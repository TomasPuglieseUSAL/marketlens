const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const entirePopup = fs.readFileSync(path.join(__dirname, '../popup/popup.js'), 'utf8');
// Ejecutar las funciones sin arrancar los listeners de la extensión real.
const bootstrap = entirePopup.indexOf("$('login-form').addEventListener('submit'");
assert.ok(bootstrap > 0, 'No se encontró el límite del bootstrap');
const source = entirePopup.slice(0, bootstrap);

function popup() {
    const elements = new Map();
    function makeElement(id = null, tagName = 'div') {
        const attributes = {};
        const listeners = {};
        return {
            id, tagName, hidden: false, disabled: false, value: '',
            textContent: '', dataset: {}, children: [],
            setAttribute(name, value) { attributes[name] = String(value); },
            getAttribute(name) { return attributes[name] ?? null; },
            addEventListener(name, fn) { listeners[name] = fn; },
            click() { return listeners.click?.({preventDefault() {}}); },
            querySelectorAll(selector) {
                return selector === 'button' ? this.children.filter(child => child.tagName === 'button') : [];
            },
            replaceChildren() { this.children = []; },
            appendChild(child) { this.children.push(child); }
        };
    }
    function element(id) {
        if (!elements.has(id)) elements.set(id, makeElement(id));
        return elements.get(id);
    }
    const document = {
        getElementById: element,
        createElement: tagName => makeElement(null, tagName),
        documentElement: {dataset: {}}
    };
    const browser = {
        storage: {
            local: {get: async () => ({}), set: async () => undefined}
        },
        runtime: {sendMessage: async () => ({ok: true})}
    };
    const context = vm.createContext({document, browser, URL, console, Date});
    vm.runInContext(source, context, {filename: 'popup.js'});
    function run(js) { return vm.runInContext(js, context); }
    return {element, run};
}

function enterFacebookForm(p) {
    p.run("authenticated=true; pageSupported=true; pageKey='tab:facebook'; currentMarketplace='FACEBOOK_MARKETPLACE'");
    p.run("extractedData={marketplace:'FACEBOOK_MARKETPLACE',externalId:'123456789',title:'Producto',price:'9507',seller:null,description:'Prueba'}");
    return p.run("prepareData('analyze')");
}

test('el formulario sustituye la sección inicial y no deja el botón visible', async () => {
    const p = popup();
    p.run("setPanelState('initial')");
    assert.equal(p.element('analyze-button').hidden, false);
    const data = await enterFacebookForm(p);
    assert.equal(data, null);
    assert.equal(p.element('analysis-overview').hidden, true);
    assert.equal(p.element('analyze-button').hidden, true);
    assert.equal(p.element('loading-section').hidden, true);
    assert.equal(p.element('result-section').hidden, true);
    assert.equal(p.element('facebook-correction').hidden, false);
});

test('validación incompleta conserva el formulario y muestra allí el error', async () => {
    const p = popup();
    await enterFacebookForm(p);
    await p.run("continueFacebook()");
    assert.equal(p.element('facebook-correction').hidden, false);
    assert.equal(p.element('analysis-overview').hidden, true);
    assert.match(p.element('correction-status').textContent, /nombre del vendedor/);
    assert.equal(p.element('facebook-continue-button').disabled, false);
});

test('continuar pasa a procesamiento y después recupera el panel de resultado', async () => {
    const p = popup();
    await enterFacebookForm(p);
    p.element('fb-seller').value = 'Tienda de prueba';
    p.run("startAnalysis = async () => { if (!$('analysis-overview').hidden || $('loading-section').hidden) throw new Error('No se mostró el procesamiento'); renderAnalysis({status:'success',publicationId:13,evaluation:{level:'LOW',score:0,availableFactors:3,totalFactors:3,coverageStatus:'COMPLETE',factors:[]}}); }");
    await p.run("continueFacebook()");
    assert.equal(p.element('facebook-correction').hidden, true);
    assert.equal(p.element('analysis-overview').hidden, false);
    assert.equal(p.element('analyze-button').hidden, true);
    assert.equal(p.element('result-section').hidden, false);
    assert.equal(p.element('result-level').textContent, 'Riesgo bajo');
});

test('cancelar descarta la edición y recupera el botón de analizar', async () => {
    const p = popup();
    await enterFacebookForm(p);
    await p.run("cancelFacebookCorrection()");
    assert.equal(p.element('facebook-correction').hidden, true);
    assert.equal(p.element('analysis-overview').hidden, false);
    assert.equal(p.element('analyze-button').hidden, false);
    assert.equal(p.run("pendingAction"), null);
});


test('Temu abre primero el precio y no mezcla los dos formularios', async () => {
    const p = popup();
    p.run("authenticated=true; pageSupported=true; pageKey='tab:temu'; currentMarketplace='TEMU'");
    p.run("extractedData={marketplace:'TEMU',externalId:'606892013277333',title:'Funda',price:null,currency:'ARS',seller:'Tokki Shop',priceCandidates:['9.059','9.507','16.486']}");
    p.run("extractCurrentListing = async () => extractedData");
    const data = await p.run("prepareData('analyze')");
    assert.equal(data, null);
    assert.equal(p.element('analysis-overview').hidden, true);
    assert.equal(p.element('temu-price-panel').hidden, false);
    assert.equal(p.element('facebook-correction').hidden, true);
    assert.equal(p.element('temu-price-options').children.length, 3);
    assert.equal(p.element('report-section').hidden, true);
    assert.equal(p.run('pendingAction'), 'analyze');
});

test('precio confirmado pasa directamente al análisis si no faltan datos', async () => {
    const p = popup();
    p.run("authenticated=true; pageSupported=true; pageKey='tab:temu'; currentMarketplace='TEMU'");
    p.run("extractedData={marketplace:'TEMU',externalId:'606892013277333',title:'Funda',price:'9507',currency:'ARS',seller:'Tokki Shop',priceCandidates:['9059','9507','16486']}");
    p.run("extractCurrentListing = async () => extractedData");
    await p.run("prepareData('analyze')");
    assert.equal(p.element('temu-confirm-price').value, '9507');
    p.element('temu-price-options').children[0].click();
    assert.equal(p.element('temu-confirm-price').value, '9059');
    assert.equal(p.element('temu-price-options').children[0].getAttribute('aria-pressed'), 'true');
    p.run("startAnalysis = async d => { globalThis.finalChosenPrice = d.price; renderAnalysis({status:'success',publicationId:13,evaluation:{level:'LOW',score:0,availableFactors:3,totalFactors:3,coverageStatus:'COMPLETE',factors:[]}}); }");
    await p.run("confirmTemuPrice()");
    assert.equal(p.run("globalThis.finalChosenPrice"), '9059');
    assert.equal(p.element('temu-price-panel').hidden, true);
    assert.equal(p.element('facebook-correction').hidden, true);
    assert.equal(p.element('result-section').hidden, false);
    assert.equal(p.element('report-section').hidden, false);
    assert.equal(p.run('pendingAction'), null);
});

test('precio inválido deja al usuario en el paso de confirmación', async () => {
    const p = popup();
    p.run("authenticated=true; pageSupported=true; pageKey='tab:temu'; currentMarketplace='TEMU'");
    p.run("extractedData={marketplace:'TEMU',externalId:'606892013277333',title:'Funda',price:null,currency:'ARS',seller:'Tokki Shop',priceCandidates:[]}");
    p.run("extractCurrentListing = async () => extractedData");
    await p.run("prepareData('analyze')");
    p.element('temu-confirm-price').value = 'ARS sin precio';
    await p.run("confirmTemuPrice()");
    assert.equal(p.element('temu-price-panel').hidden, false);
    assert.match(p.element('temu-price-status').textContent, /precio válido/);
    assert.equal(p.run('pendingAction'), 'analyze');
});

test('Temu pasa del precio al formulario general solo si falta el título', async () => {
    const p = popup();
    p.run("authenticated=true; pageSupported=true; pageKey='tab:temu'; currentMarketplace='TEMU'");
    p.run("extractedData={marketplace:'TEMU',externalId:'606892013277333',title:null,price:null,currency:'ARS',seller:'Tokki Shop',priceCandidates:['9507']}");
    p.run("extractCurrentListing = async () => extractedData");
    await p.run("prepareData('analyze')");
    p.element('temu-price-options').children[0].click();
    await p.run("confirmTemuPrice()");
    assert.equal(p.element('temu-price-panel').hidden, true);
    assert.equal(p.element('facebook-correction').hidden, false);
    assert.equal(p.element('fb-price').value, '9507');
    assert.equal(p.element('temu-seller-hint').hidden, false);
    assert.equal(p.element('fb-join-row').hidden, true);
    assert.equal(p.run('pendingAction'), 'analyze');
});

test('cancelar la elección de precio restaura el panel inicial', async () => {
    const p = popup();
    p.run("authenticated=true; pageSupported=true; pageKey='tab:temu'; currentMarketplace='TEMU'");
    p.run("extractedData={marketplace:'TEMU',externalId:'606892013277333',title:'Funda',price:null,currency:'ARS',seller:'Tokki Shop',priceCandidates:['9507']}");
    p.run("extractCurrentListing = async () => extractedData");
    await p.run("prepareData('analyze')");
    await p.run("cancelTemuPrice()");
    assert.equal(p.element('temu-price-panel').hidden, true);
    assert.equal(p.element('analysis-overview').hidden, false);
    assert.equal(p.element('analyze-button').hidden, false);
    assert.equal(p.run('pendingAction'), null);
});

test('el reporte manual permanece plegado hasta tocar el botón', () => {
    const p = popup();
    p.run("authenticated=true; pageSupported=true; setPanelState('initial'); updateActions()");
    p.run("setReportExpanded(false)");
    assert.equal(p.element('report-details').hidden, true);
    p.run("toggleReport()");
    assert.equal(p.element('report-details').hidden, false);
    assert.equal(p.element('report-toggle').getAttribute('aria-expanded'), 'true');
    p.run("toggleReport()");
    assert.equal(p.element('report-details').hidden, true);
    assert.equal(p.element('report-toggle').getAttribute('aria-expanded'), 'false');
});

test('reporte de Temu continúa tras precio confirmado sin iniciar análisis automático', async () => {
    const p = popup();
    p.run("authenticated=true; pageSupported=true; pageKey='tab:temu'; currentMarketplace='TEMU'; setPanelState('initial'); setReportExpanded(true)");
    p.run("extractedData={marketplace:'TEMU',externalId:'606892013277333',title:'Funda',price:null,currency:'ARS',seller:'Tokki Shop',priceCandidates:['9507','9059']}");
    p.run("extractCurrentListing = async () => extractedData");
    await p.run("prepareData('report')");
    p.element('temu-price-options').children[0].click();
    p.run("sendReport = async d => { globalThis.reportPrice = d.price; $('report-status').textContent = 'Reporte enviado'; }");
    await p.run("confirmTemuPrice()");
    assert.equal(p.run("globalThis.reportPrice"), '9507');
    assert.equal(p.element('temu-price-panel').hidden, true);
    assert.equal(p.element('report-section').hidden, false);
    assert.equal(p.element('report-details').hidden, false);
    assert.equal(p.element('report-status').textContent, 'Reporte enviado');
});

test('Temu con precio único coincidente analiza sin pedir confirmación', async () => {
    const p = popup();
    p.run("authenticated=true; pageSupported=true; pageKey='tab:temu'; currentMarketplace='TEMU'");
    p.run("extractedData={marketplace:'TEMU',externalId:'606892013277333',title:'Funda',price:'9507',currency:'ARS',seller:'Tokki Shop',priceCandidates:['9507']}");
    p.run("extractCurrentListing = async () => extractedData");
    p.run("setPanelState('initial')");
    const data = await p.run("prepareData('analyze')");
    assert.equal(data.price, '9507');
    assert.equal(p.element('temu-price-panel').hidden, true);
    assert.equal(p.run('pendingAction'), null);
});

test('Temu con precio extraído en conflicto con la opción visible pide confirmación', async () => {
    const p = popup();
    p.run("authenticated=true; pageSupported=true; pageKey='tab:temu'; currentMarketplace='TEMU'");
    p.run("extractedData={marketplace:'TEMU',externalId:'606892013277333',title:'Funda',price:'9507',currency:'ARS',seller:'Tokki Shop',priceCandidates:['9059']}");
    p.run("extractCurrentListing = async () => extractedData");
    const data = await p.run("prepareData('analyze')");
    assert.equal(data, null);
    assert.equal(p.element('temu-price-panel').hidden, false);
    assert.equal(p.element('analysis-title').textContent, 'Confirmar precio');
});
