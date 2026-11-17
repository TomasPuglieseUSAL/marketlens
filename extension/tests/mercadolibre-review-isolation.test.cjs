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
                  advertisedReview = true, legitimateReview = null,
                  currentUrl = publicationUrl, headingTitle = publicationTitle,
                  currencyMeta = true} = {}) {
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
    const heading = {textContent: headingTitle};
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
                return currencyMeta ? {getAttribute: () => 'ARS'} : null;
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
    const window = {location: new URL(currentUrl)};
    return compile(window, document, URL)();
}

test('no atribuye las 4,9 estrellas del Toshiba recomendado al Kingston', () => {
    const data = extract();
    assert.equal(data.found, true);
    assert.equal(data.mlProductRating, null);
    assert.equal(data.mlProductReviewCount, null);
    assert.equal(data.mlProductRatingUnavailable, true);
});

test('JSON-LD propio confirma rating aunque la puntuación no esté visible', () => {
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

test('el producto principal admite otro slug SEO cuando conserva el mismo MLAU', () => {
    const sameIdDifferentSlug =
        'https://mercadolibre.com.ar/otro-titulo/up/MLAU2874874181';
    const data = extract({mainProduct: {
        '@type': 'Product',
        name: publicationTitle,
        url: sameIdDifferentSlug,
        aggregateRating: {ratingValue: '4.6', reviewCount: '21'}
    }});
    assert.equal(data.mlProductRating, 4.6);
    assert.equal(data.mlProductReviewCount, 21);
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

test('el Kingston conserva 4,9 y 1552 reseñas aunque estén solo en JSON-LD', () => {
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
    assert.equal(data.mlProductRating, 4.9);
    assert.equal(data.mlProductReviewCount, 1552);
    assert.equal(data.mlProductRatingUnavailable, null);
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

test('Micron desde resultado patrocinado conserva el MLAU pero quita los trackers', () => {
    const canonical = 'https://www.mercadolibre.com.ar/disco-oem-solido-ssd-512gb-nvme-micron-2242/up/MLAU5694773445';
    const currentUrl = canonical +
        '?pdp_filters=item_id:MLA2246621499|item_id:MLA2246621499' +
        '#is_advertising=true&searchVariation=MLAU5694773445&tracking_id=test';
    const result = extract({currentUrl});
    assert.equal(result.url, canonical);
    assert.equal(result.externalId, 'MLAU5694773445');
});

const refurbUrl = 'https://www.mercadolibre.com.ar/samsung-galaxy-a33-5g-128-gb-awesome-black-6-gb-ram-excelente-reacondicionado/p/MLA2000136014';

// Estructura específica del HTML del Galaxy A33: el precio principal está
// fuera del formulario, y las otras dos calidades son otras fichas de catálogo.
function extractRefurbishedCatalog({
    selectedCatalogId = 'MLA2000136014',
    selectedGradeCount = 1,
    checkoutIds = ['MLA2270730739'],
    mainPriceCount = 1,
    parentUrl = '/p/MLA2000136014'
} = {}) {
    const price = {
        getAttribute: name => name === 'aria-label' ? '319999 pesos' : null,
        querySelector(selector) {
            if (selector.includes('meta[itemprop="price"]')) {
                return {getAttribute: () => '319999'};
            }
            if (selector.includes('andes-money-amount__fraction')) {
                return {textContent: '319.999'};
            }
            return null;
        },
        closest: () => null
    };
    const secondaryPrice = {
        querySelector: () => null,
        closest: () => null
    };
    const grades = [
        ...Array.from({length: selectedGradeCount}, () => ({
            classList: {contains: value => value.endsWith('--SELECTED')},
            getAttribute: name => name === 'href'
                ? '/samsung-galaxy-a33-5g-128-gb-awesome-black-6-gb-ram-excelente-reacondicionado/p/' +
                    selectedCatalogId + '?picker=true'
                : null
        })),
        {
            classList: {contains: () => false},
            getAttribute: () => '/samsung-galaxy-a33-bueno/p/MLA2000136012'
        },
        {
            classList: {contains: () => false},
            getAttribute: () => '/samsung-galaxy-a33-aceptable/p/MLA2000136016'
        }
    ];
    const form = {
        querySelector(selector) {
            if (selector.includes('name="parent_url"')) return {value: parentUrl};
            return null;
        },
        querySelectorAll(selector) {
            if (selector.includes('offer-list-item')) return [];
            if (selector.includes('name="item_id"')) {
                return checkoutIds.map(value => ({value}));
            }
            if (selector.includes('.ui-pdp-seller-summary__link')) {
                return [{textContent: 'OUTLETIT383'}];
            }
            return [];
        }
    };
    const heading = {textContent:
        'Samsung Galaxy A33 5g 128 Gb Awesome Black 6 Gb Ram - Excelente (Reacondicionado)'};
    const document = {
        readyState: 'complete',
        body: {innerText: 'Vendido por OUTLETIT383'},
        querySelector(selector) {
            if (selector === '#buybox-form') return form;
            if (selector === 'h1.ui-pdp-title') return heading;
            if (selector.includes('.ui-pdp-price__second-line')) return price;
            return null;
        },
        querySelectorAll(selector) {
            if (selector === '.ui-pdp-outside_variations__thumbnails__item') {
                return grades;
            }
            if (selector === '.ui-pdp-price__second-line .andes-money-amount') {
                return [
                    ...Array.from({length: mainPriceCount}, () => price),
                    secondaryPrice
                ];
            }
            if (selector === 'script[type="application/ld+json"]') {
                return [{textContent: JSON.stringify({
                    '@type': 'Product',
                    name: heading.textContent,
                    url: refurbUrl,
                    productID: 'MLA2000136014',
                    itemCondition: 'https://schema.org/RefurbishedCondition',
                    offers: {price: 319999, priceCurrency: 'ARS'},
                    aggregateRating: {ratingValue: 4.4, reviewCount: 36}
                })}];
            }
            return [];
        }
    };
    return compile({location: new URL(refurbUrl)}, document, URL)();
}

test('Galaxy A33 Excelente: importa única oferta aunque precio esté fuera del buybox', () => {
    const result = extractRefurbishedCatalog();
    assert.equal(result.found, true);
    assert.equal(result.externalId, 'MLA2270730739');
    assert.equal(result.selectedOfferId, 'MLA2270730739');
    assert.equal(result.seller, 'OUTLETIT383');
    assert.equal(result.price, '319.999');
    assert.equal(result.currency, 'ARS');
    assert.equal(result.url, refurbUrl);
    assert.equal(result.mlProductRating, 4.4);
    assert.equal(result.mlProductReviewCount, 36);
});

test('Galaxy A33: no importa si está seleccionada una variante distinta', () => {
    const wrongVariant = extractRefurbishedCatalog({selectedCatalogId: 'MLA2000136012'});
    assert.equal(wrongVariant.found, false);
});

test('Galaxy A33: selector de estado sin elección única es ambiguo', () => {
    assert.equal(extractRefurbishedCatalog({selectedGradeCount: 0}).found, false);
    assert.equal(extractRefurbishedCatalog({selectedGradeCount: 2}).found, false);
});

test('Galaxy A33: dos precios principales o dos item_id no son corroboración', () => {
    assert.equal(extractRefurbishedCatalog({mainPriceCount: 2}).found, false);
    assert.equal(extractRefurbishedCatalog({
        checkoutIds: ['MLA2270730739', 'MLA2270730740']
    }).found, false);
});

test('Galaxy A33: se rechaza parent_url de otra ficha de catálogo', () => {
    assert.equal(extractRefurbishedCatalog({
        parentUrl: '/p/MLA2000136016'
    }).found, false);
});


const galaxyA13Url = 'https://www.mercadolibre.com.ar/celular-samsung-galaxy-a13-negro-128gb-color-black-excelente-reacondicionado/p/MLA2000090922';

// Estructura real observada: hay DOS grupos independientes de variaciones,
// uno para Black/Blanco/Light blue y otro para Excelente/Bueno/Aceptable.
// Ambos eligen la misma ficha de catálogo y la única oferta del checkout.
function extractGalaxyA13({
    colorId = 'MLA2000090922',
    conditionId = 'MLA2000090922',
    selectedColorCount = 1,
    selectedConditionCount = 1,
    checkoutIds = ['MLA4004994290'],
    parentUrl = '/p/MLA2000090922',
    selectedPriceCount = 1
} = {}) {
    const selected = id => ({
        classList: {contains: cls => cls.endsWith('--SELECTED')},
        getAttribute: name => name === 'href'
            ? '/celular-samsung-galaxy-a13-negro-128gb-color-black-excelente-reacondicionado/p/' +
                id + '?product_trigger_id=MLA2000090922&picker=true'
            : null
    });
    const unselected = id => ({
        classList: {contains: () => false},
        getAttribute: () => '/samsung-galaxy-a13-bueno/p/' + id
    });
    const colorOptions = [
        ...Array.from({length: selectedColorCount}, () => selected(colorId)),
        unselected('MLA2000013001')
    ];
    const conditionOptions = [
        ...Array.from({length: selectedConditionCount}, () => selected(conditionId)),
        unselected('MLA2000102466')
    ];
    const group = items => ({
        querySelectorAll: selector => selector.includes('__thumbnails__item')
            ? items : []
    });
    const groups = [group(colorOptions), group(conditionOptions)];
    const displayedPrice = {
        getAttribute: key => key === 'aria-label' ? '349999 pesos' : null,
        closest: () => null,
        querySelector(selector) {
            if (selector.includes('meta[itemprop="price"]'))
                return {getAttribute: () => '349999'};
            if (selector.includes('andes-money-amount__fraction'))
                return {textContent: '349.999'};
            return null;
        }
    };
    // Precio de la miniatura "Excelente", no es otro precio principal.
    const thumbnailPrice = {
        closest: () => null,
        querySelector: () => null
    };
    const form = {
        querySelector(selector) {
            return selector.includes('name="parent_url"')
                ? {value: parentUrl} : null;
        },
        querySelectorAll(selector) {
            if (selector.includes('name="item_id"')) {
                return checkoutIds.map(value => ({value}));
            }
            if (selector.includes('.ui-pdp-seller-summary__link')) {
                return [{textContent: 'DDOM INSUMOS'}];
            }
            return [];
        }
    };
    const heading = {textContent:
        'Celular Samsung Galaxy A13 Negro 128gb Color Black - Excelente (Reacondicionado)'};
    const document = {
        readyState: 'complete',
        body: {innerText: 'Vendido por DDOM INSUMOS'},
        querySelector(selector) {
            if (selector === '#buybox-form') return form;
            if (selector === 'h1.ui-pdp-title') return heading;
            if (selector.includes('.ui-pdp-price__second-line')) return displayedPrice;
            return null;
        },
        querySelectorAll(selector) {
            if (selector === '.ui-pdp-outside_variations__thumbnails') return groups;
            if (selector === '.ui-pdp-outside_variations__thumbnails__item')
                return [...colorOptions, ...conditionOptions];
            if (selector === '.ui-pdp-price__second-line .andes-money-amount')
                return [...Array.from({length: selectedPriceCount},
                    () => displayedPrice), thumbnailPrice];
            if (selector === 'script[type="application/ld+json"]') {
                return [{textContent: JSON.stringify({
                    '@type': 'Product',
                    name: heading.textContent,
                    url: galaxyA13Url,
                    productID: 'MLA2000090922',
                    offers: {price: 349999, priceCurrency: 'ARS'},
                    aggregateRating: {
                        ratingValue: 4.5, ratingCount: 128, reviewCount: 59
                    }
                })}];
            }
            return [];
        }
    };
    return compile({location: new URL(galaxyA13Url)}, document, URL)();
}

test('Galaxy A13: selecciona Black y Excelente simultáneamente en DOS grupos', () => {
    const result = extractGalaxyA13();
    assert.equal(result.found, true);
    assert.equal(result.externalId, 'MLA4004994290');
    assert.equal(result.selectedOfferId, 'MLA4004994290');
    assert.equal(result.seller, 'DDOM INSUMOS');
    assert.equal(result.price, '349.999');
    assert.equal(result.currency, 'ARS');
    assert.equal(result.url, galaxyA13Url);
    assert.equal(result.mlProductRating, 4.5);
    assert.equal(result.mlProductReviewCount, 59);
});

test('Galaxy A13: una variante seleccionada de otra ficha no se puede importar', () => {
    assert.equal(extractGalaxyA13({conditionId: 'MLA2000013001'}).found, false);
});

test('Galaxy A13: dos selecciones dentro de un grupo o ninguna se rechazan', () => {
    assert.equal(extractGalaxyA13({selectedConditionCount: 2}).found, false);
    assert.equal(extractGalaxyA13({selectedColorCount: 0}).found, false);
});

test('Galaxy A13: no aceptar precios ni item_id ambiguos', () => {
    assert.equal(extractGalaxyA13({selectedPriceCount: 2}).found, false);
    assert.equal(extractGalaxyA13({
        checkoutIds: ['MLA4004994290', 'MLA4004994291']
    }).found, false);
});

test('Galaxy A13: el formulario debe apuntar al catálogo abierto', () => {
    assert.equal(extractGalaxyA13({
        parentUrl: '/p/MLA2000013001'
    }).found, false);
});


const galaxyA13LightBlueUrl =
    'https://www.mercadolibre.com.ar/samsung-galaxy-a13-64gb-celeste-4gb-ram-aceptable-reacondicionado/p/MLA2000102466';

// Fixture fiel al HTML del Galaxy A13 Celeste: hay tres selectores
// independientes, pero el formulario conserva parent_url de la variante
// negra. El vínculo "Vender uno igual" confirma itemId/productId actuales.
function extractGalaxyA13LightBlue({
    confirmation = true,
    confirmationCatalog = 'MLA2000102466',
    confirmationItem = 'MLA2282119419',
    selectedStorageId = 'MLA2000102466',
    duplicateConfirmation = false,
    parentUrl = '/p/MLA2000090922',
    checkoutIds = ['MLA2282119419']
} = {}) {
    const catalog = 'MLA2000102466';
    const chosen = id => ({
        classList: {contains: name => name.endsWith('--SELECTED')},
        getAttribute: field => field === 'href'
            ? '/samsung-galaxy-a13-64gb-celeste-4gb-ram-aceptable-reacondicionado/p/' +
                id + '?product_trigger_id=MLA2000102466&picker=true'
            : null
    });
    const other = id => ({
        classList: {contains: () => false},
        getAttribute: () => '/celular-samsung-galaxy-a13-negro-128gb/p/' + id
    });
    const groups = [
        [chosen(selectedStorageId), other('MLA2000090922')],
        [other('MLA2000090922'), other('MLA2000013001'), chosen(catalog)],
        [other('MLA2000090922'), other('MLA2000013001'), chosen(catalog)]
    ];
    const gradeGroup = elements => ({
        querySelectorAll: selector =>
            selector.includes('__thumbnails__item') ? elements : []
    });
    const price = {
        getAttribute: key => key === 'aria-label' ? '239995 pesos' : null,
        closest: () => null,
        querySelector(selector) {
            if (selector.includes('meta[itemprop="price"]'))
                return {getAttribute: () => '239995'};
            if (selector.includes('__fraction'))
                return {textContent: '239.995'};
            return null;
        }
    };
    const withoutMeta = {querySelector: () => null, closest: () => null};
    const form = {
        querySelector(selector) {
            return selector.includes('name="parent_url"')
                ? {value: parentUrl} : null;
        },
        querySelectorAll(selector) {
            if (selector.includes('name="item_id"')) {
                return checkoutIds.map(value => ({value}));
            }
            if (selector.includes('.ui-pdp-seller-summary__link')) {
                return [{textContent: 'SANTIJULIANN_'}];
            }
            return [];
        }
    };
    const confirmationHref =
        'https://www.mercadolibre.com.ar/syi/core/list/equals?itemId=' +
        confirmationItem + '&productId=' + confirmationCatalog;
    const proof = {
        getAttribute: key => key === 'href' ? confirmationHref : null,
        closest: () => null
    };
    const heading = {textContent:
        'Samsung Galaxy A13 64GB Celeste 4GB RAM - Aceptable (Reacondicionado)'};
    const document = {
        readyState: 'complete',
        body: {innerText: 'Vendido por SANTIJULIANN_'},
        querySelector(selector) {
            if (selector === '#buybox-form') return form;
            if (selector === 'h1.ui-pdp-title') return heading;
            if (selector.includes('.ui-pdp-price__second-line')) return price;
            return null;
        },
        querySelectorAll(selector) {
            if (selector === '.ui-pdp-outside_variations__thumbnails') {
                return groups.map(gradeGroup);
            }
            if (selector === '.ui-pdp-outside_variations__thumbnails__item') {
                return groups.flat();
            }
            if (selector === 'a.ui-pdp-syi__link[href]') {
                return !confirmation ? [] : duplicateConfirmation
                    ? [proof, proof] : [proof];
            }
            if (selector === '.ui-pdp-price__second-line .andes-money-amount') {
                return [price, withoutMeta];
            }
            if (selector === 'script[type="application/ld+json"]') {
                // Reproduce metadata que sigue describiendo la variante
                // negra, aunque el DOM principal es el celeste.
                return [{textContent: JSON.stringify({
                    '@type': 'Product',
                    name: 'Celular Samsung Galaxy A13 Negro 128gb - Excelente (Reacondicionado)',
                    productID: 'MLA2000090922',
                    offers: {
                        price: 349999,
                        url: 'https://www.mercadolibre.com.ar/p/MLA2000090922',
                        priceCurrency: 'ARS'
                    },
                    aggregateRating: {ratingValue: 4.5, reviewCount: 59}
                })}];
            }
            return [];
        }
    };
    return compile({location: new URL(galaxyA13LightBlueUrl)}, document, URL)();
}

test('Galaxy A13 Celeste: importa oferta con tres selectores y parent_url desactualizado', () => {
    const result = extractGalaxyA13LightBlue();
    assert.equal(result.found, true);
    assert.equal(result.externalId, 'MLA2282119419');
    assert.equal(result.selectedOfferId, 'MLA2282119419');
    assert.equal(result.url, galaxyA13LightBlueUrl);
    assert.equal(result.seller, 'SANTIJULIANN_');
    assert.equal(result.price, '239.995');
    assert.equal(result.currency, 'ARS');
    // No tomar las reseñas de la otra variante que está en JSON-LD.
    assert.equal(result.mlProductRating, null);
    assert.equal(result.mlProductReviewCount, null);
});

test('Galaxy A13 Celeste: sin corroboración independiente, se rechaza parent_url viejo', () => {
    assert.equal(extractGalaxyA13LightBlue({confirmation: false}).found, false);
});

test('Galaxy A13 Celeste: enlace con otro productId o itemId no valida la oferta', () => {
    assert.equal(extractGalaxyA13LightBlue({
        confirmationCatalog: 'MLA2000090922'
    }).found, false);
    assert.equal(extractGalaxyA13LightBlue({
        confirmationItem: 'MLA2282119420'
    }).found, false);
});

test('Galaxy A13 Celeste: dos enlaces de corroboración o dos item_id son ambiguos', () => {
    assert.equal(extractGalaxyA13LightBlue({
        duplicateConfirmation: true
    }).found, false);
    assert.equal(extractGalaxyA13LightBlue({
        checkoutIds: ['MLA2282119419', 'MLA2282119420']
    }).found, false);
});

test('Galaxy A13 Celeste: almacenamiento seleccionado de otro catálogo se rechaza', () => {
    assert.equal(extractGalaxyA13LightBlue({
        selectedStorageId: 'MLA2000090922'
    }).found, false);
});


const openBoxUrl = 'https://www.mercadolibre.com.ar/samsung-galaxy-a13-64gb-negro-4gb-ram-nuevo-con-caja-abierta/p/MLA2015816941';

// Replica la estructura del HTML adjunto: la oferta está en #buybox-form,
// pero el vendedor LOTARINO es un span dentro de un botón, no un enlace.
function extractOpenBoxCatalog({
    sellerMode = 'button',
    checkoutIds = ['MLA2629481668'],
    parentUrl = '/p/MLA2015816941',
    structuredPrice = '250000'
} = {}) {
    const form = {
        querySelector(selector) {
            if (selector.includes('name="parent_url"')) {
                return {value: parentUrl};
            }
            return null;
        },
        querySelectorAll(selector) {
            if (selector.includes('name="item_id"')) {
                return checkoutIds.map(value => ({value}));
            }
            if (selector.includes('.ui-pdp-seller-summary__header__title')) {
                const nodes = [];
                if ((sellerMode === 'link' || sellerMode === 'both') &&
                        selector.includes('.ui-pdp-seller-summary__link')) {
                    nodes.push({textContent: 'LOTARINO'});
                }
                if ((sellerMode === 'button' || sellerMode === 'both') &&
                        selector.includes('.ui-pdp-seller-summary__label-text-no-action')) {
                    nodes.push({textContent: 'LOTARINO'});
                }
                return nodes;
            }
            return [];
        }
    };
    const mainPrice = {
        getAttribute: name => name === 'aria-label' ? '250000 pesos' : null,
        closest: () => null,
        querySelector(selector) {
            if (selector.includes('meta[itemprop="price"]')) {
                return {getAttribute: () => structuredPrice};
            }
            if (selector.includes('andes-money-amount__fraction')) {
                return {textContent: '250.000'};
            }
            return null;
        }
    };
    const heading = {textContent:
        'Samsung Galaxy A13 64GB Negro 4GB RAM (Nuevo con caja abierta)'};
    const doc = {
        readyState: 'complete',
        body: {innerText: 'Vendido por LOTARINO. 4 ventas'},
        querySelector(selector) {
            if (selector === '#buybox-form') return form;
            if (selector === 'h1.ui-pdp-title') return heading;
            if (selector.includes('.ui-pdp-price__second-line')) return mainPrice;
            return null;
        },
        querySelectorAll(selector) {
            if (selector === '.ui-pdp-price__second-line .andes-money-amount') {
                return [mainPrice];
            }
            if (selector === 'script[type="application/ld+json"]') {
                return [{textContent: JSON.stringify({
                    '@type': 'Product',
                    name: heading.textContent,
                    productID: 'MLA2015816941',
                    sku: 'MLA2015816941',
                    offers: {
                        price: 250000,
                        priceCurrency: 'ARS',
                        url: openBoxUrl
                    },
                    aggregateRating: {
                        ratingValue: 4.7,
                        ratingCount: 2867,
                        reviewCount: 977
                    }
                })}];
            }
            return [];
        }
    };
    return compile({location: new URL(openBoxUrl)}, doc, URL)();
}

test('Galaxy A13 caja abierta: importa oferta única con vendedor en botón', () => {
    const data = extractOpenBoxCatalog();
    assert.equal(data.found, true);
    assert.equal(data.externalId, 'MLA2629481668');
    assert.equal(data.selectedOfferId, 'MLA2629481668');
    assert.equal(data.seller, 'LOTARINO');
    assert.equal(data.url, openBoxUrl);
    assert.equal(data.price, '250.000');
    assert.equal(data.currency, 'ARS');
    assert.equal(data.mlProductRating, 4.7);
    assert.equal(data.mlProductReviewCount, 977);
});

test('Galaxy A13 caja abierta: sigue funcionando el vendedor como enlace', () => {
    const data = extractOpenBoxCatalog({sellerMode: 'link'});
    assert.equal(data.found, true);
    assert.equal(data.seller, 'LOTARINO');
});

test('Galaxy A13 caja abierta: vendedores ausentes o duplicados se rechazan', () => {
    assert.equal(extractOpenBoxCatalog({sellerMode: 'none'}).found, false);
    assert.equal(extractOpenBoxCatalog({sellerMode: 'both'}).found, false);
});

test('Galaxy A13 caja abierta: oferta y catálogo deben tener IDs inequívocos', () => {
    assert.equal(extractOpenBoxCatalog({
        checkoutIds: ['MLA2629481668', 'MLA2629481669']
    }).found, false);
    assert.equal(extractOpenBoxCatalog({
        parentUrl: '/p/MLA2000090922'
    }).found, false);
});

test('Galaxy A13 caja abierta: precio estructurado contradictorio se rechaza', () => {
    assert.equal(extractOpenBoxCatalog({
        structuredPrice: '149000'
    }).found, false);
});


const samsungA13UpUrl =
    'https://www.mercadolibre.com.ar/samsung-a13/up/MLAU3344351864';

test('Samsung A13 usado: reconoce JSON-LD con título abreviado y moneda solamente en Offer', () => {
    // HTML aportado: h1 "Samsung A13 (Usado)", Product.name "Samsung A13".
    // No hay meta[property="product:price:currency"].
    const data = extract({
        currentUrl: samsungA13UpUrl,
        headingTitle: 'Samsung A13 (Usado)',
        currencyMeta: false,
        mainProduct: {
            '@type': 'Product',
            name: 'Samsung A13',
            sku: 'MLAU3344351864',
            productID: 'MLAU3344351864',
            offers: {
                price: 111552, priceCurrency: 'ARS',
                url: samsungA13UpUrl
            }
        }
    });
    assert.equal(data.found, true);
    assert.equal(data.externalId, 'MLAU3344351864');
    assert.equal(data.currency, 'ARS');
    assert.equal(data.price, '140.000');
});

test('Samsung A13 usado: admite SKU inequívoco aun sin URLs JSON-LD', () => {
    const data = extract({
        currentUrl: samsungA13UpUrl,
        headingTitle: 'Samsung A13 (Usado)',
        currencyMeta: false,
        mainProduct: {
            '@type': 'Product',
            name: 'Samsung A13',
            sku: 'MLAU3344351864',
            productID: 'MLAU3344351864',
            offers: {price: 111552, priceCurrency: 'ARS'}
        }
    });
    assert.equal(data.currency, 'ARS');
});

test('No admitir reseñas ni moneda de otro User Product con el mismo nombre', () => {
    const data = extract({
        currentUrl: samsungA13UpUrl,
        headingTitle: 'Samsung A13 (Usado)',
        currencyMeta: false,
        mainProduct: {
            '@type': 'Product',
            name: 'Samsung A13',
            sku: 'MLAU1111111111',
            productID: 'MLAU1111111111',
            offers: {priceCurrency: 'ARS',
                     aggregateRating: {ratingValue: 4.8, reviewCount: 900}}
        }
    });
    assert.equal(data.currency, null);
    assert.equal(data.mlProductRating, null);
});

test('Si SKU contradice la URL de oferta, se rechaza el Product estructurado', () => {
    const data = extract({
        currentUrl: samsungA13UpUrl,
        headingTitle: 'Samsung A13 (Usado)',
        currencyMeta: false,
        mainProduct: {
            '@type': 'Product',
            name: 'Samsung A13',
            sku: 'MLAU1111111111',
            productID: 'MLAU1111111111',
            offers: {url: samsungA13UpUrl, priceCurrency: 'ARS'}
        }
    });
    assert.equal(data.currency, null);
});

test('Una URL Offer que identifica la ficha puede corroborar un Product sin SKU', () => {
    const data = extract({
        currentUrl: samsungA13UpUrl,
        headingTitle: 'Samsung A13 (Usado)',
        currencyMeta: false,
        mainProduct: {
            '@type': 'Product',
            name: 'Samsung A13',
            offers: {url: samsungA13UpUrl, priceCurrency: 'ARS'}
        }
    });
    assert.equal(data.currency, 'ARS');
});
