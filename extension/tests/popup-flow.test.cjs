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
        const classes = new Set();
        return {
            id, tagName, hidden: false, disabled: false, value: '',
            textContent: '', dataset: {}, children: [], scrollCalls: [],
            classList: {
                add(...names) { for (const name of names) classes.add(name); },
                remove(...names) { for (const name of names) classes.delete(name); },
                contains(name) { return classes.has(name); }
            },
            scrollIntoView(options) { this.scrollCalls.push(options); },
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
            local: {
                get: async () => ({}), set: async () => undefined,
                remove: async () => undefined
            },
            session: {remove: async () => undefined}
        },
        runtime: {sendMessage: async () => ({ok: true})}
    };
    let reducedMotion = false;
    let clock = 1000;
    let nextTimerId = 0;
    const timers = new Map();
    class MockDate extends Date {
        static now() { return clock; }
    }
    function setFakeTimeout(callback, delay) {
        const id = ++nextTimerId;
        timers.set(id, {callback, at: clock + delay});
        return id;
    }
    function advance(ms) {
        clock += ms;
        // Ejecutar en orden temporal los timers vencidos sin dormir realmente.
        for (;;) {
            const due = [...timers].filter(([, timer]) => timer.at <= clock)
                .sort((a, b) => a[1].at - b[1].at)[0];
            if (!due) break;
            timers.delete(due[0]);
            due[1].callback();
        }
    }
    const context = vm.createContext({
        document, browser, URL, console, Date: MockDate,
        requestAnimationFrame: callback => callback(),
        matchMedia: () => ({matches: reducedMotion}),
        setTimeout: setFakeTimeout,
        clearTimeout: id => timers.delete(id)
    });
    vm.runInContext(source, context, {filename: 'popup.js'});
    function run(js) { return vm.runInContext(js, context); }
    return {
        element, run, advance,
        pendingTimers: () => timers.size,
        setReducedMotion: value => { reducedMotion = value; }
    };
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
    p.advance(600);
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
    p.advance(600);
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


test('al finalizar el análisis desplaza al resultado una sola vez', () => {
    const p = popup();
    p.run("authenticated=true; pageSupported=true; setPanelState('initial')");
    const result = p.element('result-section');
    const success = "renderAnalysis({status:'success',publicationId:13,evaluation:{level:'LOW',score:0,availableFactors:3,totalFactors:3,coverageStatus:'COMPLETE',factors:[]}})";
    p.run("renderAnalysis({status:'loading',message:'Analizando'})");
    assert.equal(result.scrollCalls.length, 0);
    p.run(success);
    assert.equal(result.scrollCalls.length, 0, 'El spinner debe permanecer visible brevemente');
    p.advance(600);
    assert.equal(result.scrollCalls.length, 1);
    assert.equal(result.scrollCalls[0].behavior, 'smooth');
    assert.equal(result.scrollCalls[0].block, 'start');
    p.run(success);
    assert.equal(result.scrollCalls.length, 1, 'No repetir scroll por eventos de almacenamiento');
    p.run("renderAnalysis({status:'loading',message:'Nuevo análisis'})");
    p.run(success);
    p.advance(600);
    assert.equal(result.scrollCalls.length, 2, 'El segundo análisis debe enfocar su resultado');
});

test('el scroll automático respeta la preferencia de movimiento reducido', () => {
    const p = popup();
    p.setReducedMotion(true);
    p.run("authenticated=true; pageSupported=true; setPanelState('initial')");
    p.run("renderAnalysis({status:'success',publicationId:13,evaluation:{level:'LOW',score:0,availableFactors:3,totalFactors:3,coverageStatus:'COMPLETE',factors:[]}})");
    assert.equal(p.element('result-section').scrollCalls[0].behavior, 'auto');
    assert.equal(p.element('result-section').scrollCalls.length, 1);
});

test('el encabezado es sticky y el resultado tiene desplazamiento compensado', () => {
    const css = fs.readFileSync(path.join(__dirname, '../popup/popup.css'), 'utf8');
    const html = fs.readFileSync(path.join(__dirname, '../popup/popup.html'), 'utf8');
    assert.match(css, /\.brand\s*\{[^}]*position:\s*sticky;[^}]*top:\s*0;/s);
    assert.match(css, /\.result-card\s*\{[^}]*scroll-margin-top:\s*86px;/s);
    assert.match(html, /<svg\s+id="report-chevron"[^>]*>/);
    assert.match(css, /\.report-chevron\s*\{[^}]*transform-origin:\s*center;/s);
    assert.equal(html.includes('aria-hidden="true">⌄</span>'), false);
});


test('la cuenta se oculta durante selección de precio, corrección y procesamiento', () => {
    const p = popup();
    p.run("authenticated = true");
    for (const state of ['price', 'correction', 'loading']) {
        p.run("setPanelState('" + state + "')");
        assert.equal(p.element('auth-card').hidden, true, 'La cuenta debería ocultarse en ' + state);
        assert.equal(p.element('report-section').hidden, true, 'El reporte debería ocultarse en ' + state);
    }
    for (const state of ['success', 'error', 'initial']) {
        p.run("setPanelState('" + state + "')");
        assert.equal(p.element('auth-card').hidden, false, 'La cuenta debería volver en ' + state);
        assert.equal(p.element('report-section').hidden, false, 'El reporte debería volver en ' + state);
    }
});

test('si la sesión se cierra, la cuenta y el formulario de acceso permanecen visibles', () => {
    const p = popup();
    p.run("authenticated = true; setPanelState('loading')");
    assert.equal(p.element('auth-card').hidden, true);
    p.run("showLoggedOut('Sesión expirada')");
    assert.equal(p.element('auth-card').hidden, false);
    assert.equal(p.element('login-form').hidden, false);
    assert.equal(p.element('authenticated-content').hidden, true);
    // Incluso si hay un estado de análisis pendiente, no debe esconder el login.
    p.run("setPanelState('loading')");
    assert.equal(p.element('auth-card').hidden, false);
});


test('una respuesta inmediata mantiene la carga al menos medio segundo', () => {
    const p = popup();
    p.run("authenticated=true; pageSupported=true; pageKey='tab:fb'; setPanelState('initial')");
    const success = "renderAnalysis({status:'success',publicationId:12,evaluation:{level:'LOW',score:0,availableFactors:2,totalFactors:2,coverageStatus:'COMPLETE',factors:[]}})";
    p.run("renderAnalysis({status:'loading',message:'Procesando'})");
    p.run(success);
    assert.equal(p.element('loading-section').hidden, false);
    assert.equal(p.element('result-section').hidden, true);
    p.advance(549);
    assert.equal(p.element('result-section').hidden, true);
    p.advance(1);
    assert.equal(p.element('result-section').hidden, false);
    assert.equal(p.pendingTimers(), 0);
});

test('los errores no sufren demora artificial y anulan resultados diferidos', () => {
    const p = popup();
    p.run("authenticated=true; pageSupported=true; pageKey='tab:fb'");
    p.run("renderAnalysis({status:'loading',message:'Procesando'})");
    p.run("renderAnalysis({status:'success',publicationId:1,evaluation:{level:'LOW',score:0,availableFactors:2,totalFactors:2,factors:[]}})");
    assert.equal(p.pendingTimers(), 1);
    p.run("renderAnalysis({status:'error',message:'Error al consultar la publicación'})");
    assert.equal(p.element('error-section').hidden, false);
    assert.equal(p.pendingTimers(), 0);
    p.advance(1000);
    assert.equal(p.element('result-section').hidden, true);
});

test('una respuesta de otra publicación no revela el resultado de la pestaña anterior', () => {
    const p = popup();
    p.run("authenticated=true; pageSupported=true; pageKey='tab:a'");
    p.run("renderAnalysis({status:'loading',message:'Procesando'})");
    p.run("renderAnalysis({status:'success',publicationId:1,evaluation:{level:'LOW',score:0,availableFactors:2,totalFactors:2,factors:[]}})");
    p.run("pageKey='tab:b'; resetLoadingTransition(); renderAnalysis({status:'initial'})");
    p.advance(600);
    assert.equal(p.element('result-section').hidden, true);
});

test('reducir movimiento elimina la espera y las animaciones opcionales', () => {
    const p = popup();
    p.setReducedMotion(true);
    p.run("authenticated=true; pageSupported=true");
    p.run("renderAnalysis({status:'loading',message:'Procesando'})");
    p.run("renderAnalysis({status:'success',publicationId:1,evaluation:{level:'LOW',score:0,availableFactors:2,totalFactors:2,factors:[]}})");
    assert.equal(p.element('result-section').hidden, false);
    assert.equal(p.pendingTimers(), 0);
});

test('cerrar sesión elimina credenciales sin esperar la animación y luego muestra login', async () => {
    const p = popup();
    p.run("authenticated=true; pageSupported=true; showLoggedIn('Usuario de prueba'); setPanelState('initial')");
    const logoutPromise = p.run("handleLogout()");
    assert.equal(p.run("authenticated"), false);
    assert.equal(p.element('authenticated-content').classList.contains('auth-exiting'), true);
    assert.equal(p.element('authenticated-content').hidden, false);
    p.advance(180);
    await logoutPromise;
    assert.equal(p.element('authenticated-content').hidden, true);
    assert.equal(p.element('auth-card').hidden, false);
    assert.equal(p.element('login-form').hidden, false);
    assert.equal(p.element('auth-status').textContent, 'Sesión cerrada.');
    assert.equal(p.element('authenticated-content').classList.contains('auth-exiting'), false);
});

test('cerrar sesión con movimiento reducido es inmediato', async () => {
    const p = popup();
    p.setReducedMotion(true);
    p.run("authenticated=true; pageSupported=true; showLoggedIn('Usuario de prueba'); setPanelState('initial')");
    await p.run("handleLogout()");
    assert.equal(p.pendingTimers(), 0);
    assert.equal(p.element('login-form').hidden, false);
    assert.equal(p.element('authenticated-content').hidden, true);
});
