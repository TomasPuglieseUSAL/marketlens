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
  // Temu puede incluir información útil en #goodsDetail aunque no publique
  // Product.description en JSON-LD. Limitarse a atributos y texto de aviso:
  // imágenes, ALT, botones y paneles de tienda no describen el producto.
  function extractProductDetails() {
    const root = document.querySelector('#goodsDetail');
    if (!root) return null;
    const clean = value => typeof value === 'string'
      ? value.replace(/\s+/g, ' ').trim() : '';
    const lines = [];
    const seen = new Set();
    function addField(rawLabel, rawValue) {
      const label = clean(rawLabel).replace(/:\s*$/, '');
      const value = clean(rawValue);
      if (!label || !value || label.length > 80 || value.length > 1500
          || /^(?:item id|id del artículo)$/i.test(label)) return;
      const key = (label + ':' + value).toLowerCase();
      if (seen.has(key)) return;
      seen.add(key);
      lines.push(label + ': ' + value);
    }

    // Pares clave/valor en la sección superior (Feature, Popular Elements...).
    for (const entry of root.querySelectorAll('._3zjMlOk4')) {
      addField(entry.querySelector('._5pgrvPWK')?.textContent,
        entry.querySelector('._2N4zowM4')?.textContent);
    }
    // Especificaciones inferiores (Main Material, Origin...).
    for (const entry of root.querySelectorAll('._1YBVObhm')) {
      const labelNode = entry.querySelector('._3c5b9cNv');
      const label = clean(labelNode?.firstChild?.textContent
        || labelNode?.textContent);
      const separator = label.indexOf(':');
      if (separator > 0 && separator < 80) {
        addField(label.slice(0, separator),
          entry.querySelector('._2bgv8UEs > span')?.textContent);
      }
    }

    // Advertencia sobre las características o presentación del artículo.
    const note = clean(root.querySelector('._2lHZTmSd')?.textContent);
    if (note.length >= 40 && note.length <= 2000 && !seen.has(note.toLowerCase())) {
      lines.push(note);
    }

    const details = lines.join('\n');
    return details.length >= 25 ? details.slice(0, 5000) : null;
  }
  const structuredDescription = typeof product?.description === 'string'
    ? product.description.trim() : '';
  const visibleDetails = extractProductDetails();
  const description = [structuredDescription,
    visibleDetails && !structuredDescription.includes(visibleDetails)
      ? visibleDetails : null]
    .filter(Boolean).join('\n\n').slice(0, 5000) || null;

  // Opiniones del PRODUCTO, distintas de las valoraciones de la tienda.
  // Priorizar JSON-LD Product.aggregateRating; si no está presente, buscar
  // etiquetas explícitas de estrellas y reviews junto al título principal.
  // Nunca usar aggregateRating de otro objeto ni buscar en recomendaciones.
  function readProductReviews() {
    const toRating = value => {
      if (value == null || !/^[0-5](?:[.,]\d{1,3})?$/.test(String(value).trim())) return null;
      const n = Number(String(value).trim().replace(',', '.'));
      return Number.isFinite(n) && n > 0 && n <= 5
        ? Math.round(n * 10) / 10 : null;
    };
    const toExactCount = value => {
      if (value == null) return null;
      const str = String(value).trim();
      if (!/^(?:\d{1,8}|\d{1,3}(?:[.,]\d{3})+)$/.test(str)) return null;
      const n = Number(str.replace(/[.,]/g, ''));
      return Number.isSafeInteger(n) && n > 0 && n <= 10000000 ? n : null;
    };
    const aggregate = product?.aggregateRating;
    const structuredRating = toRating(aggregate?.ratingValue);
    // ratingCount puede incluir estrellas sin reseña escrita: no es reviewCount.
    const structuredCount = toExactCount(aggregate?.reviewCount);
    if (structuredRating != null && structuredCount != null)
      return {rating:structuredRating, count:structuredCount};

    let parent = document.querySelector('h1')?.parentElement ?? null;
    for (let depth = 0; parent && depth < 8; depth++, parent = parent.parentElement) {
      const text = (parent.innerText || parent.textContent || '').replace(/\u00a0/g, ' ').trim();
      // Revisar sólo el panel próximo al título, no las recomendaciones.
      if (text.length > 4500) break;
      const labels = Array.from(parent.querySelectorAll?.('[aria-label], [title]') || [])
        .slice(0, 250)
        .map(el => [el.getAttribute?.('aria-label'), el.getAttribute?.('title')]
          .filter(Boolean).join(' '))
        .filter(Boolean).join(' ');
      const combined = text + ' ' + labels;
      const ratingMatches = [...combined.matchAll(
        /\b([0-5](?:[.,]\d{1,3})?)\s*(?:out of (?:five|5)(?: stars?)?|\/\s*5(?: stars?)?)\b/gi
      )].map(x => toRating(x[1])).filter(x => x != null);
      const reviewMatches = [...combined.matchAll(
        /\b(\d{1,3}(?:[.,]\d{3})*|\d{1,8})\s+(?:product\s+)?reviews?\b/gi
      )].map(x => toExactCount(x[1])).filter(x => x != null);
      const ratings = [...new Set(ratingMatches)];
      const counts = [...new Set(reviewMatches)];
      // No relacionar ciegamente una calificación etiquetada de tienda
      // con un contador de opiniones del producto del mismo contenedor.
      const explicitProductPair = ratings.length === 1 && counts.length === 1
        && !/\b(?:seller|store|shop|tienda|vendedor)\b/i.test(combined);

      // En algunos diseños Temu muestra "4.7" junto a "286 reviews"
      // sin las palabras "out of five stars". Sólo aceptar la pareja
      // numérica inmediatamente anterior a un contador explícito.
      const pairs = [];
      for (const match of text.matchAll(
        /\b(\d{1,3}(?:[.,]\d{3})*|\d{1,8})\s+(?:product\s+)?reviews?\b/gi
      )) {
        const count = toExactCount(match[1]);
        if (count == null) continue;
        const before = text.slice(Math.max(0, match.index - 85), match.index);
        // Evitar "store reviews", referencias a tiendas y reseñas recomendadas.
        if (/\b(?:seller|store|shop|tienda|vendedor)\b/i.test(before)) continue;
        const localRatings = [...before.matchAll(/\b([0-5][.,]\d{1,2})\b/g)];
        if (!localRatings.length) continue;
        const nearest = localRatings[localRatings.length - 1];
        const between = before.slice(nearest.index + nearest[0].length);
        if (between.length > 65 || /\b(?:seller|store|shop|tienda|vendedor)\b/i.test(between)) continue;
        const rating = toRating(nearest[1]);
        if (rating != null) pairs.push({rating,count});
      }
      const uniquePairs = [...new Map(pairs.map(p => [p.rating + ':' + p.count, p])).values()];
      if (uniquePairs.length === 1) return uniquePairs[0];
      if (explicitProductPair) return {rating:ratings[0],count:counts[0]};
    }
    // No combinar un rating del JSON-LD con una cantidad vista en otro panel.
    return {rating:null,count:null};
  }
  const productReviews = readProductReviews();

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
        || /\bARS\s*\d/i.test(name)
        // El contador "286 store reviews" no es un nombre de tienda.
        // Si lo aceptamos como nombre, compite con "Tokki Shop" y
        // la extracción de reseñas termina sin vendedor identificado.
        || /^\d[\d.,]*\s+(?:seller|store|shop|tienda|vendedor)\s+(?:reviews?|ratings?|valoraciones|reseñas)\b/i.test(name)
        || /\b(?:stars?|rating|review score|out of (?:five|5))\b/i.test(name)
        || /\b[0-5][.,]\d\b/.test(name)
        || /^[0-5](?:[.,]\d)?\s*(?:\/\s*5)?$/i.test(name)
        || /^\d+(?:[.,]\d+)?\s+out\s+of\s+(?:five|5)\s+stars?\b/i.test(name)) return null;
    return name;
  };

  function validStoreLink(element) {
    const href = element?.getAttribute?.('href');
    if (!href) return false;
    try {
      const target = new URL(href, url.href);
      const shopHost = target.hostname.toLowerCase();
      return (shopHost === 'temu.com' || shopHost.endsWith('.temu.com'))
        && (/^(?:\/mall\.html|\/store\.html|\/shop\.html)\/?$/i.test(target.pathname)
            || /-m-\d{6,30}\.html\/?$/i.test(target.pathname));
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
  let storeEvidenceRoot = null;
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
        if (index >= 0) {
          // La línea inmediatamente superior puede ser "4.8" (rating) o
          // "4.7 out of five stars" (opiniones del PRODUCTO). Buscar nombre
          // de tienda entre líneas anteriores sin confundir estos valores.
          const around = [...lines.slice(Math.max(0, index - 5), index).reverse(),
              ...lines.slice(index + 1, index + 3)];
          const storeLike = around
            .map(cleanStoreName)
            .filter(name => name && /(?:shop|store|tienda|official)/i.test(name));
          if (storeLike.length === 1) candidate = storeLike[0];
        }
      }
      if (candidate) {
        observedStore = candidate;
        starSellerForStore = true;
        storeEvidenceRoot = container;
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

  // Buscar la tienda por etiquetas independientes del distintivo. Las
  // tiendas normales pueden no tener ningún sello de vendedor destacado.
  if (!observedStore) {
    const root = document.querySelector('main') || document.body;
    const candidateNames = new Set();
    const elements = Array.from(
      root?.querySelectorAll?.('section, div, p, span') || []
    ).slice(0, 1500);
    for (const element of elements) {
      const content = (element.innerText || element.textContent || '')
        .replace(/\u00a0/g, ' ').trim();
      if (content.length < 5 || content.length > 180
          || /\bARS\s*\d/i.test(content)) continue;
      const lines = content.split(/\r?\n/).map(line => line.trim()).filter(Boolean);
      for (let i = 0; i < lines.length; i++) {
        const prefixed = lines[i].match(
          /^(?:sold by|sourced from|vendido por|tienda|store)\s*:\s*(.{2,100})$/i
        );
        const direct = lines[i].match(
          /^(?:sold by|sourced from|vendido por)\s+(.{2,100})$/i
        );
        const next = /^(?:seller|vendedor|store|tienda)$/i.test(lines[i])
          ? lines[i + 1] : null;
        const name = cleanStoreName(prefixed?.[1] || direct?.[1] || next);
        if (name) candidateNames.add(name);
      }
    }
    if (candidateNames.size === 1) {
      observedStore = [...candidateNames][0];
      storeEvidenceRoot = elements.find(element =>
        (element.innerText || element.textContent || '').includes(observedStore)
      ) || null;
    }
  }

  // Una única tienda enlazada en el área principal constituye otra
  // fuente de nombre; varias tiendas posibles se consideran ambiguas.
  if (!observedStore) {
    const root = document.querySelector('main') || document.body;
    const names = Array.from(root?.querySelectorAll?.('a[href]') || [])
      .map(storeLinkName).filter(Boolean);
    const unique = [...new Set(names)];
    if (unique.length === 1) {
      observedStore = unique[0];
      const link = Array.from(root?.querySelectorAll?.('a[href]') || [])
        .find(element => storeLinkName(element) === observedStore);
      storeEvidenceRoot = link?.parentElement || link || null;
    }
  }


  // Las estrellas del PRODUCTO no son reputación de la TIENDA.
  // Solo intentar atribuir un número a la tienda si ya hay un nombre
  // fiable y una región DOM cercana a esa identidad.
  let temuSellerRating = null;
  if (observedStore) {
    const candidates = [];
    const storeRoots = [];
    if (storeEvidenceRoot) storeRoots.push(storeEvidenceRoot);
    const main = document.querySelector('main') || document.body;
    for (const element of Array.from(
        main?.querySelectorAll?.('a[href], section, div, p, span') || []
      ).slice(0, 1500)) {
      const t = (element.innerText || element.textContent || '').trim();
      if (t.length <= 180 && t.includes(observedStore)) storeRoots.push(element);
    }
    const seenNodes = new Set();
    for (const root of storeRoots) {
      let current = root;
      for (let depth = 0; current && depth < 4; depth++, current = current.parentElement) {
        if (seenNodes.has(current)) continue;
        seenNodes.add(current);
        const raw = (current.innerText || current.textContent || '').replace(/\u00a0/g, ' ');
        if (raw.length < observedStore.length || raw.length > 380
            || !raw.includes(observedStore)) continue;
        // Eliminar descripciones de estrellas del producto, e.g.
        // "4.7 out of five stars". No parsear aggregateRating de JSON-LD.
        const cleaned = raw.replace(
          /\b[0-5](?:[.,]\d)?\s+out\s+of\s+(?:five|5)\s+stars?\b/gi, ' '
        );
        const ratings = [...cleaned.matchAll(/\b([0-5][.,]\d)\b/g)]
          .map(match => Number(match[1].replace(',', '.')))
          .filter(value => value > 0 && value <= 5);
        const uniqueRatings = [...new Set(ratings)];
        if (uniqueRatings.length === 1) {
          candidates.push({value: uniqueRatings[0], size: raw.length});
        }
      }
    }
    // Elegir el área más pequeña que vincula el nombre con un único
    // puntaje; no usar un número aislado del encabezado del producto.
    candidates.sort((a, b) => a.size - b.size);
    if (candidates.length) temuSellerRating = candidates[0].value;
  }


  // Identificador de tienda: solo proviene de un enlace de tienda
  // asociado al nombre encontrado, jamás del goods_id del producto.
  let temuStoreId = null;
  let temuStoreProfile = null;
  let temuSellerReviewCount = null;
  const storeSectionObserved = Boolean(observedStore && storeEvidenceRoot);
  if (observedStore) {
    const scope = storeEvidenceRoot || document.querySelector('main') || document.body;
    const storeLinks = Array.from(scope?.querySelectorAll?.('a[href]') || []);
    if (scope?.matches?.('a[href]')) storeLinks.unshift(scope);
    const links = storeLinks.filter(element => storeLinkName(element) === observedStore);
    const unique = new Map();
    for (const element of links) {
      try {
        const destination = new URL(element.getAttribute('href'), url.href);
        const mall = destination.searchParams.get('mall_id');
        const slug = destination.pathname.match(/-m-(\d{6,30})\.html\/?$/i);
        const mallId = slug?.[1] || (
          /^\/mall\.html\/?$/i.test(destination.pathname) &&
          /^\d{6,30}$/.test(mall ?? '') ? mall : null
        );
        if (!mallId) continue;
        const canonicalStore = slug
          ? new URL(destination.pathname, destination.origin)
          : new URL('/mall.html?mall_id=' + mallId, destination.origin);
        unique.set(mallId, canonicalStore.toString());
      } catch { /* ignorar links inválidos */ }
    }
    if (unique.size === 1) {
      [temuStoreId, temuStoreProfile] = [...unique.entries()][0];
    }

    // Recuento EXPLÍCITO de reseñas de la tienda: nunca tomar el
    // contador de reseñas del producto ni redondeos como "1.2K+".
    let element = storeEvidenceRoot;
    for (let depth = 0; element && depth < 3; depth++, element = element.parentElement) {
      const content = (element.innerText || element.textContent || '')
        .replace(/\u00a0/g, ' ').trim();
      if (content.length > 380 || !content.includes(observedStore)) continue;
      // Separadores de miles exactos sí se admiten; "1.2K+" no es exacto.
      const exactNumber = '(?:\\d{1,3}(?:[.,]\\d{3})+|\\d{1,8})';
      const matches = [
        ...content.matchAll(new RegExp('\\b(' + exactNumber + ')\\s+(?:seller|store|shop|tienda|vendedor)\\s+(?:reviews?|ratings?|valoraciones|reseñas)\\b', 'gi')),
        ...content.matchAll(new RegExp('\\b(?:seller|store|shop|tienda|vendedor)\\s+(?:reviews?|ratings?|valoraciones|reseñas)\\s*:?\\s*(' + exactNumber + ')\\b', 'gi')),
      ].map(m => Number(m[1].replace(/[.,]/g, ''))).filter(n => n <= 10000000);
      const distinct = [...new Set(matches)];
      if (distinct.length === 1) { temuSellerReviewCount = distinct[0]; break; }
    }
  }

  return {found:true,title:title || null,price,currency:detectedCurrency,
    priceCandidates:prices,seller:observedStore,
    temuSellerRating,temuSellerReviewCount,temuSellerSectionObserved:storeSectionObserved,
    temuProductRating:productReviews.rating,temuProductReviewCount:productReviews.count,
    temuStoreId,externalId:id,
    description,url:canonical.toString(),sellerProfileUrl:temuStoreProfile,
    sellerBadge:starSellerForStore ? 'TEMU_STAR_SELLER' : null,
    sellerSalesText:null,sellerReputationLevel:null,sellerId:null};
}
