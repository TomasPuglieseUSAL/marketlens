// Autocontenida para scripting.executeScript({func: extractTemu}).
function extractTemu() {
  const url = new URL(window.location.href);
  const host = url.hostname.toLowerCase();
  if (host !== 'temu.com' && !host.endsWith('.temu.com'))
    return {found:false,message:'Dominio no compatible con Temu.'};
  const match = url.pathname.match(/-g-(\d{6,30})\.html\/?$/i);
  const query = url.searchParams.get('goods_id');
  const id = match?.[1] ?? (/^\/goods\.html\/?$/i.test(url.pathname)
    && /^\d{6,30}$/.test(query ?? '') ? query : null);
  if (!id) return {found:false,message:'Abrí un producto individual de Temu.'};
  const canonical = new URL(url.pathname, url.origin);
  if (!match) canonical.searchParams.set('goods_id', id);
  function productNode(value) {
    if (!value || typeof value !== 'object') return null;
    if (Array.isArray(value)) {
      for (const item of value) {
        const found = productNode(item);
        if (found) return found;
      }
      return null;
    }
    const types = Array.isArray(value['@type']) ? value['@type'] : [value['@type']];
    if (types.some(t => typeof t === 'string' && /(?:^|\/)Product$/i.test(t)))
      return value;
    return productNode(value['@graph']);
  }
  let product = null;
  for (const script of document.querySelectorAll('script[type="application/ld+json"]')) {
    try {
      product = productNode(JSON.parse(script.textContent || ''));
      if (product) break;
    } catch { /* Metadatos inválidos. */ }
  }
  const meta = s => document.querySelector(s)?.getAttribute('content')?.trim() || null;
  const offers = Array.isArray(product?.offers)
    ? (product.offers.length === 1 ? product.offers[0] : null) : product?.offers;
  const currency = typeof offers?.priceCurrency === 'string'
    ? offers.priceCurrency.toUpperCase()
    : (meta('meta[property="product:price:currency"]')?.toUpperCase() ?? null);
  const ambiguous = offers?.lowPrice != null || offers?.highPrice != null;
  const structuredPrice = currency === 'ARS' && !ambiguous
    ? (offers?.price == null ? meta('meta[property="product:price:amount"]') : String(offers.price))
    : null;

  // Fallback prudente: buscar importes marcados ARS cerca del título.
  // El DOM puede contener texto que no permite selección con el mouse.
  // No elegimos automáticamente el menor precio si hay promociones,
  // precios tachados o distintas variantes.
  const heading = document.querySelector('h1');
  let nearbyText = '';
  let node = heading?.parentElement ?? null;
  for (let depth = 0; node && depth < 9; depth++, node = node.parentElement) {
    const content = (node.innerText || node.textContent || '').replace(/\u00a0/g, ' ');
    if (content.length > 6000) break; // No recorrer todas las recomendaciones.
    // Tomar el mayor contexto acotado alrededor del título: puede separar
    // el importe promocional del precio publicado y del tachado.
    if (/\bARS\s*\d/i.test(content) && content.length > nearbyText.length) {
      nearbyText = content;
    }
  }

  const prices = [];
  const seen = new Set();
  const pattern = /\bARS\s*(\d{1,3}(?:[.,]\d{3})+(?:[.,]\d{1,2})?|\d+(?:[.,]\d{1,2})?)/gi;
  for (const found of nearbyText.matchAll(pattern)) {
    const amount = found[1];
    if (!seen.has(amount)) {
      seen.add(amount);
      prices.push(amount);
    }
    if (prices.length >= 8) break;
  }
  const promotionContext = /after applying promos?|promotional|promo(?:s|tion)?|coupon|cr[eé]dito|despu[eé]s de aplicar|from|desde/i.test(nearbyText);
  const visiblePrice = prices.length === 1 && !promotionContext ? prices[0] : null;
  const detectedCurrency = currency || (prices.length ? 'ARS' : null);
  const price = structuredPrice || visiblePrice;
  const title = document.querySelector('h1')?.textContent?.trim()
    || (typeof product?.name === 'string' ? product.name.trim() : null)
    || meta('meta[property="og:title"]');
  const description = typeof product?.description === 'string'
    ? product.description.trim().slice(0, 5000) || null : null;

  // La tienda se busca en el producto actual, sin tomar nombres de
  // recomendaciones ni confundir "Star Seller" con la identidad de la tienda.
  const cleanStoreName = value => {
    if (typeof value !== 'string') return null;
    const name = value.replace(/\s+/g, ' ').trim()
      .replace(/^(?:sold by|sourced from|vendido por|tienda)\s*:?\s*/i, '')
      .replace(/\s+star\s+seller$/i, '').trim();
    if (name.length < 2 || name.length > 100
        || !/[\p{L}]/u.test(name)
        || /^(?:star seller|star store|seller|store|shop|visit store|store information|seller information|product details)$/i.test(name)
        || /\bARS\s*\d/i.test(name)) return null;
    return name;
  };

  function validStoreLink(element) {
    const href = element?.getAttribute?.('href');
    if (!href) return false;
    try {
      const target = new URL(href, url.href);
      const shopHost = target.hostname.toLowerCase();
      return (shopHost === 'temu.com' || shopHost.endsWith('.temu.com'))
        && /(?:^|\/)(?:store|shop|mall)(?:\/|\.html|$)/i.test(target.pathname);
    } catch { return false; }
  }
  const storeLinkName = element =>
    validStoreLink(element)
      ? cleanStoreName(element.innerText || element.textContent
        || element.getAttribute?.('aria-label') || element.getAttribute?.('title'))
      : null;

  const badgeNodes = Array.from(document.querySelectorAll('span, strong, b, a, div')).filter(
    element => /^\s*star\s+seller\s*$/i.test(element.textContent || '')
      && (!element.children || element.children.length === 0)
  ).slice(0, 15);

  let observedStore = null;
  let starSellerForStore = false;
  for (const badge of badgeNodes) {
    let container = badge.parentElement;
    for (let depth = 0; container && depth < 6; depth++, container = container.parentElement) {
      const text = (container.innerText || container.textContent || '').replace(/\u00a0/g, ' ');
      if (text.length > 650) break; // Evitar toda la página y productos sugeridos.
      const linkedNames = Array.from(container.querySelectorAll?.('a[href]') || [])
        .map(storeLinkName).filter(Boolean);
      let candidate = linkedNames.length === 1 ? linkedNames[0] : null;
      if (!candidate) {
        const lines = text.split(/\r?\n/).map(line => line.trim()).filter(Boolean);
        const index = lines.findIndex(line => /^star\s+seller$/i.test(line));
        if (index > 0) candidate = cleanStoreName(lines[index - 1]);
        if (!candidate && index >= 0 && index + 1 < lines.length)
          candidate = cleanStoreName(lines[index + 1]);
        if (!candidate) {
          const combined = text.trim().match(/^([^\n]{2,100}?)\s+star\s+seller(?:\s|$)/i);
          if (combined) candidate = cleanStoreName(combined[1]);
        }
      }
      if (candidate) {
        observedStore = candidate;
        starSellerForStore = true;
        break;
      }
    }
    if (observedStore) break;
  }

  // Alternativa: vendedor explícito en JSON-LD, sin asumir que su
  // presencia confirma la etiqueta visual.
  const structuredSeller = offers?.seller || product?.seller;
  const structuredName = cleanStoreName(
    typeof structuredSeller === 'string'
      ? structuredSeller : structuredSeller?.name
  );
  if (!observedStore && structuredName) observedStore = structuredName;

  // Una única tienda enlazada en el área principal constituye otra
  // fuente de nombre; varias tiendas posibles se consideran ambiguas.
  if (!observedStore) {
    const root = document.querySelector('main') || document.body;
    const names = Array.from(root?.querySelectorAll?.('a[href]') || [])
      .map(storeLinkName).filter(Boolean);
    const unique = [...new Set(names)];
    if (unique.length === 1) observedStore = unique[0];
  }

  return {found:true,title:title || null,price,currency:detectedCurrency,
    priceCandidates:prices,seller:observedStore,externalId:id,
    description,url:canonical.toString(),sellerProfileUrl:null,
    sellerBadge:starSellerForStore ? 'TEMU_STAR_SELLER' : null,
    sellerSalesText:null,sellerReputationLevel:null,sellerId:null};
}
