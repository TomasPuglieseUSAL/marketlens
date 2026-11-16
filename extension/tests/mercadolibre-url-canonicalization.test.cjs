const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

// Ejecutar las funciones reales del background, sin llamadas de red.
const background = fs.readFileSync(path.join(__dirname, '../background.js'), 'utf8');
const context = vm.createContext({
    URL,
    browser: {runtime: {onMessage: {addListener() {}}}}
});
vm.runInContext(background, context, {filename: 'background.js'});

function payload(data) {
    return vm.runInContext('importPayload(' + JSON.stringify(data) + ')', context);
}

const fullUrl = 'https://www.mercadolibre.com.ar/disco-oem-solido-ssd-512gb-nvme-micron-2242/up/MLAU5694773445' +
    '?pdp_filters=item_id:MLA2246621499|item_id:MLA2246621499' +
    '#is_advertising=true&searchVariation=MLAU5694773445&position=7&tracking_id=test';
const cleanUrl = 'https://www.mercadolibre.com.ar/disco-oem-solido-ssd-512gb-nvme-micron-2242/up/MLAU5694773445';

test('un enlace publicitario de Mercado Libre se envía sin query ni fragmento', () => {
    const data = payload({
        marketplace: 'MERCADO_LIBRE', url: fullUrl,
        title: 'SSD Micron 512GB', seller: 'Tienda', price: '55000',
        externalId: 'MLAU5694773445'
    });
    assert.equal(data.url, cleanUrl);
    assert.equal(data.externalId, 'MLAU5694773445');
});

test('las variantes de tracking de la misma ficha tienen URL estable', () => {
    const before = payload({
        marketplace: 'MERCADO_LIBRE',
        url: cleanUrl + '?matt_tool=123&searchVariation=MLAU5694773445#top',
        externalId: 'MLAU5694773445'
    });
    const after = payload({
        marketplace: 'MERCADO_LIBRE', url: cleanUrl,
        externalId: 'MLAU5694773445'
    });
    assert.equal(before.url, after.url);
});

test('catálogo conserva su ruta y la oferta identificada por separado', () => {
    const catalog = 'https://www.mercadolibre.com.ar/ssd-sandisk/p/MLA2085086111';
    const data = payload({
        marketplace: 'MERCADO_LIBRE',
        url: catalog + '?searchVariation=MLA2085086111#foo',
        selectedOfferId: 'MLA4003237102', externalId: 'MLA4003237102'
    });
    assert.equal(data.url, catalog);
    assert.equal(data.externalId, 'MLA4003237102');
    assert.equal(data.selectedOfferId, 'MLA4003237102');
});

test('los parámetros funcionales de Temu y Facebook no se alteran', () => {
    const temu = 'https://www.temu.com/goods.html?goods_id=606892013277333&language=es';
    const facebook = 'https://www.facebook.com/marketplace/item/123456789?ref=search';
    assert.equal(payload({marketplace: 'TEMU', url: temu}).url, temu);
    assert.equal(payload({marketplace: 'FACEBOOK_MARKETPLACE', url: facebook}).url,
        facebook);
});

test('no modifica URLs de terceros ni rutas sin una publicación ML reconocible', () => {
    const fake = 'https://mercadolibre.com.ar.ejemplo.com/up/MLAU5694773445?q=1';
    const search = 'https://www.mercadolibre.com.ar/jm/search?tracking_id=123';
    assert.equal(payload({marketplace: 'MERCADO_LIBRE', url: fake}).url, fake);
    assert.equal(payload({marketplace: 'MERCADO_LIBRE', url: search}).url, search);
});
