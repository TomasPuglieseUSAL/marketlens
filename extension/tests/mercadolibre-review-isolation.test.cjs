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

test('sí conserva el rating de JSON-LD cuando la URL corresponde a la ficha', () => {
    const data = extract({mainProduct: {
        '@type': 'Product',
        name: publicationTitle,
        url: publicationUrl,
        aggregateRating: {ratingValue: '4.7', reviewCount: '286'}
    }});
    assert.equal(data.mlProductRating, 4.7);
    assert.equal(data.mlProductReviewCount, 286);
    assert.equal(data.mlProductRatingUnavailable, null);
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
