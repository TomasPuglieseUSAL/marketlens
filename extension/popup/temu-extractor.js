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
    if (/\bARS\s*\d/i.test(content)) {
      nearbyText = content;
      break;
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
  return {found:true,title:title || null,price,currency:detectedCurrency,
    priceCandidates:prices,seller:null,externalId:id,
    description,url:canonical.toString(),sellerProfileUrl:null,sellerBadge:null,
    sellerSalesText:null,sellerReputationLevel:null,sellerId:null};
}
