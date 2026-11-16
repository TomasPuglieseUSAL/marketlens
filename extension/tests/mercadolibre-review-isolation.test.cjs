const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

// El extractor se ejecuta dentro de la pestaña como función independiente.
// Esta prueba utiliza esa misma función, sin iniciar el popup.
const popupCode = fs.readFileSync(path.join(__dirname, '../popup/popup.js'), 'utf8');
const begin = popupCode.indexOf('function extractMercadoLibre() {');
const end = popupCode.indexOf('function extractFacebookMarketplace() {', begin);
assert.ok(begin >= 0 && end > begin);
const compile = new Function(
    'window', 'document', 'URL',
    popupCode.slice(begin, end) + '\nreturn extractMercadoLibre;'
);
const publicationUrl = 'https://www.mercadolibre.com.ar/disco-solido-ssd-kingston-nv3-1tb-m2-2280-nvme-pcie-40/up/MLAU2874874181';
const publicationTitle = 'Disco Sólido Ssd Kingston Nv3 1tb M.2 2280 Nvme Pcie 4.0';

// Reproduce el dato aportado en el HTML del usuario: un carrusel publicitario
// de "Con 4 estrellas o más" tiene un Toshiba 4,9, no el Kingston analizado.
function extract({mainProduct = null, questions = true, loaded = true,
                  advertisedReview = true, legitimateReview = null} = {}) {
    const scripts = [
        {
            '@type': 'Product',
            name: 'Disco Duro Externo Toshiba Hdtp340xk3ca 2.5in 4tb Usb 3',
            url: 'https://www.mercadolibre.com.ar/p/MLA21767091',
            aggregateRating: {ratingValue: '4.9', reviewCount: '226'}
        }
    ];
    if (mainProduct) scripts.push(mainProduct);

    const adReview = {
        textContent: 'Calificación 4,9 de 5 226 opiniones',
        innerText: 'Calificación 4,9 de 5 226 opiniones',
        closest: () => ({className: 'poly-card'}),
        querySelector: () => ({textContent: '4,9'})
    };
    const ownReview = legitimateReview ? {
        textContent: legitimateReview.text,
        innerText: legitimateReview.text,
        closest: () => null,
        querySelector: () => ({textContent: legitimateReview.rating})
    } : null;
    const heading = {textContent: publicationTitle};
    const amount = {querySelector: selector =>
        selector.includes('fraction') ? {textContent: '140.000'} : null};
    const document = {
        readyState: loaded ? 'complete' : 'loading',
        body: {innerText: 'Vendido por TIENDA DE PRUEBA'},
        querySelector(selector) {
            if (selector === 'h1.ui-pdp-title') return heading;
            if (selector.includes('.ui-pdp-price__second-line')) return amount;
            if (questions && selector.includes('.ui-pdp-questions')) {
                return {textContent: 'Preguntas y respuestas'};
            }
            if (selector.includes('meta[property="product:price:currency"]')) {
                return {getAttribute: () => 'ARS'};
            }
            return null;
        },
        querySelectorAll(selector) {
            if (selector === 'script[type="application/ld+json"]') {
                return [{textContent: JSON.stringify({'@graph': scripts})}];
            }
            if (selector === 'script') return [];
            if (selector.includes('.ui-pdp-review__rating')) {
                return [
                    ...(advertisedReview ? [adReview] : []),
                    ...(ownReview ? [ownReview] : [])
                ];
            }
            return [];
        }
    };
    const window = {location: new URL(publicationUrl)};
    return compile(window, document, URL)();
}

test('no atribuye las 4,9 estrellas del Toshiba recomendado al Kingston', () => {
    const data = extract();
    assert.equal(data.found, true);
    assert.equal(data.mlProductRating, null);
    assert.equal(data.mlProductReviewCount, null);
    assert.equal(data.mlProductRatingUnavailable, true);
});

test('un JSON-LD de la ficha sin módulo de opiniones NO confirma cobertura', () => {
    const data = extract({mainProduct: {
        '@type': 'Product',
        name: publicationTitle,
        url: publicationUrl,
        aggregateRating: {ratingValue: '4.7', reviewCount: '286'}
    }});
    assert.equal(data.mlProductRating, null);
    assert.equal(data.mlProductReviewCount, null);
    assert.equal(data.mlProductRatingUnavailable, true);
});

test('el producto principal admite otro slug SEO cuando conserva el mismo MLAU', () => {
    const sameIdDifferentSlug =
        'https://mercadolibre.com.ar/otro-titulo/up/MLAU2874874181';
    const data = extract({mainProduct: {
        '@type': 'Product',
        name: publicationTitle,
        url: sameIdDifferentSlug,
        aggregateRating: {ratingValue: '4.6', reviewCount: '21'}
    }});
    assert.equal(data.mlProductRating, null);
    assert.equal(data.mlProductReviewCount, null);
    assert.equal(data.mlProductRatingUnavailable, true);
});

test('el fallback DOM ignora el carrusel pero puede usar una reseña principal', () => {
    const data = extract({legitimateReview: {
        text: '4,6 de 5 · 12 opiniones', rating: '4,6'
    }});
    assert.equal(data.mlProductRating, 4.6);
    assert.equal(data.mlProductReviewCount, 12);
    assert.equal(data.mlProductRatingUnavailable, null);
});

test('una sección incompleta o sin preguntas observables no confirma ausencia', () => {
    const loading = extract({loaded: false});
    const noQuestions = extract({questions: false});
    assert.equal(loading.mlProductRatingUnavailable, null);
    assert.equal(noQuestions.mlProductRatingUnavailable, null);
});

test('el Kingston real declara 4,9 y 1552 reseñas en JSON-LD, pero no las muestra', () => {
    const data = extract({mainProduct: {
        '@type': 'Product',
        name: publicationTitle,
        productID: 'MLAU2874874181',
        url: publicationUrl,
        aggregateRating: {
            '@type': 'AggregateRating',
            ratingValue: 4.9,
            ratingCount: 3573,
            reviewCount: 1552
        }
    }});
    assert.equal(data.mlProductRating, null);
    assert.equal(data.mlProductReviewCount, null);
    assert.equal(data.mlProductRatingUnavailable, true);
});

test('si las reseñas son visibles, JSON-LD del producto puede corroborar rating', () => {
    const data = extract({
        mainProduct: {
            '@type': 'Product', name: publicationTitle,
            url: publicationUrl,
            aggregateRating: {ratingValue: 4.7, reviewCount: 286}
        },
        legitimateReview: {text: '4,7 de 5 · 286 opiniones', rating: '4,7'}
    });
    assert.equal(data.mlProductRating, 4.7);
    assert.equal(data.mlProductReviewCount, 286);
    assert.equal(data.mlProductRatingUnavailable, null);
});

const catalogUrl = 'https://www.mercadolibre.com.ar/ssd-nvme-m2-2280-sandisk-optimus-5100-1tb-pcie-gen4/p/MLA2085086111';

function extractSingleCatalog({
    parentUrl = '/p/MLA2085086111',
    itemIds = ['MLA4003237102'],
    sellerName = 'Youyite Trading Colimit Global',
    metadataPrice = '331245.45'
} = {}) {
    const form = {
        querySelector(selector) {
            if (selector.includes('parent_url')) return {value: parentUrl};
            // El catálogo del usuario NO incluye lista lateral de opciones.
            return null;
        },
        querySelectorAll(selector) {
            if (selector.includes('offer-list-item')) return [];
            if (selector.includes('name="item_id"')) {
                return itemIds.map(value => ({value}));
            }
            if (selector.includes('.ui-pdp-seller-summary__link')) {
                return sellerName ? [{textContent: sellerName}] : [];
            }
            return [];
        }
    };
    const mainPrice = {
        getAttribute(attribute) {
            return attribute === 'aria-label'
                ? '331245 pesos con 45 centavos' : null;
        },
        querySelector(selector) {
            if (selector.includes('andes-money-amount__fraction'))
                return {textContent: '331.245'};
            if (selector.includes('andes-money-amount__cents'))
                return {textContent: '45'};
            if (selector.includes('meta[itemprop="price"]'))
                return {getAttribute: () => metadataPrice};
            return null;
        }
    };
    const heading = {textContent:
        'Disco Ssd Sandisk Optimus 5100 1 Tb Nvme Pcie 4.0 M.2 2280 1 Tb'};
    const doc = {
        readyState: 'complete',
        body: {innerText: 'Vendido por ' + sellerName},
        querySelector(selector) {
            if (selector === '#buybox-form') return form;
            if (selector === 'h1.ui-pdp-title') return heading;
            if (selector.includes('.ui-pdp-price__second-line')) return mainPrice;
            if (selector.includes('.ui-pdp-questions')) return {};
            return null;
        },
        querySelectorAll(selector) {
            if (selector === 'script[type="application/ld+json"]') {
                return [{textContent: JSON.stringify({
                    '@type': 'Product',
                    name: heading.textContent,
                    url: catalogUrl,
                    offers: {price: 331245.45, priceCurrency: 'ARS'},
                    sku: 'MLA2085086111'
                })}];
            }
            if (selector === '.ui-pdp-price__second-line .andes-money-amount') {
                return [mainPrice];
            }
            return [];
        }
    };
    return compile({location: new URL(catalogUrl)}, doc, URL)();
}

test('catálogo SanDisk con un solo item_id importa la oferta del checkout', () => {
    const result = extractSingleCatalog();
    assert.equal(result.found, true);
    assert.equal(result.selectedOfferId, 'MLA4003237102');
    assert.equal(result.externalId, 'MLA4003237102');
    assert.equal(result.seller, 'Youyite Trading Colimit Global');
    assert.equal(result.price, '331.245,45');
    assert.equal(result.currency, 'ARS');
    assert.equal(result.url, catalogUrl);
    assert.equal(result.mlProductRatingUnavailable, true);
});

test('catálogo sin vendedor comprobado o con varios item_id no se importa', () => {
    const noSeller = extractSingleCatalog({sellerName: ''});
    const multipleIds = extractSingleCatalog({
        itemIds: ['MLA4003237102', 'MLA4003237103']
    });
    assert.equal(noSeller.found, false);
    assert.equal(multipleIds.found, false);
});

test('catálogo con parent_url o precio principal contradictorios se rechaza', () => {
    const badParent = extractSingleCatalog({parentUrl: '/p/MLA1111111111'});
    const badPrice = extractSingleCatalog({metadataPrice: '123456.00'});
    assert.equal(badParent.found, false);
    assert.equal(badPrice.found, false);
});
