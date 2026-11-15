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
        const styles = new Map();
        return {
            id, tagName, hidden: false, disabled: false, value: '',
            style: {
                setProperty(name, value) { styles.set(name, value); },
                removeProperty(name) { styles.delete(name); },
                getPropertyValue(name) { return styles.get(name) || ''; }
            },
            getBoundingClientRect() { return {height: this.hidden ? 0 : 320}; },
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
    let simulateLayout = false;
    let animatedHeight = null;
    const heightAnimations = [];
    const popupStyle = {height: ''};
    const popupClasses = new Set();
    const popupSurface = {
        style: popupStyle,
        classList: {
            add(...names) { for (const name of names) popupClasses.add(name); },
            remove(...names) { for (const name of names) popupClasses.delete(name); },
            contains(name) { return popupClasses.has(name); }
        },
        getBoundingClientRect() {
            if (animatedHeight !== null) return {height: animatedHeight};
            if (popupStyle.height) return {height: parseFloat(popupStyle.height)};
            const visible = id => !element(id).hidden;
            if (!visible('authenticated-content')) return {height: 225};
            if (visible('loading-section')) return {height: 285};
            if (visible('result-section')) return {height: 565};
            if (visible('temu-price-panel')) return {height: 540};
            if (visible('facebook-correction')) return {height: 590};
            return {height: visible('report-details') ? 620 : 430};
        },
        animate(frames, options) {
            const listeners = {finish: [], cancel: []};
            animatedHeight = parseFloat(frames[0].height);
            const anim = {
                frames, options,
                addEventListener(name, cb) { listeners[name].push(cb); },
                cancel() {
                    animatedHeight = null;
                    for (const cb of listeners.cancel) cb();
                },
                finish() {
                    animatedHeight = null;
                    for (const cb of listeners.finish) cb();
                }
            };
            heightAnimations.push(anim);
            return anim;
        }
    };
    const document = {
        getElementById: element,
        createElement: tagName => makeElement(null, tagName),
        querySelector: selector => simulateLayout && selector === '.popup'
            ? popupSurface : null,
        documentElement: {dataset: {}, scrollTop: 0, scrollCalls: [],
            scrollTo(options) { this.scrollCalls.push(options); this.scrollTop = 0; }},
        body: {scrollTop: 0, scrollCalls: [],
            scrollTo(options) { this.scrollCalls.push(options); this.scrollTop = 0; }}
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
        enableHeightAnimations() { simulateLayout = true; },
        heightAnimations,
        scrollBody: document.body,
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
    p.advance(1500);
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
    assert.equal(p.element('report-details').hidden, false, 'El reporte sigue visible durante la salida');
    assert.equal(p.element('report-details').classList.contains('is-closing'), true);
    assert.equal(p.element('report-toggle').getAttribute('aria-expanded'), 'false');
    p.advance(300);
    assert.equal(p.element('report-details').hidden, true);
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
    p.advance(1500);
    assert.equal(result.scrollCalls.length, 1);
    assert.equal(result.scrollCalls[0].behavior, 'smooth');
    assert.equal(result.scrollCalls[0].block, 'start');
    p.run(success);
    assert.equal(result.scrollCalls.length, 1, 'No repetir scroll por eventos de almacenamiento');
    p.run("renderAnalysis({status:'loading',message:'Nuevo análisis'})");
    p.run(success);
    p.advance(1500);
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


test('una respuesta inmediata mantiene la carga al menos 1,5 segundos', () => {
    const p = popup();
    p.run("authenticated=true; pageSupported=true; pageKey='tab:fb'; setPanelState('initial')");
    const success = "renderAnalysis({status:'success',publicationId:12,evaluation:{level:'LOW',score:0,availableFactors:2,totalFactors:2,coverageStatus:'COMPLETE',factors:[]}})";
    p.run("renderAnalysis({status:'loading',message:'Procesando'})");
    p.run(success);
    assert.equal(p.element('loading-section').hidden, false);
    assert.equal(p.element('result-section').hidden, true);
    p.advance(1499);
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
    p.advance(1600);
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


test('el marco del popup mantiene 540px en todas las pantallas', () => {
    const css = fs.readFileSync(path.join(__dirname, '../popup/popup.css'), 'utf8');
    assert.match(css, /body\s*\{[^}]*height:\s*540px;[^}]*max-height:\s*540px;/s);
    assert.match(css, /body\s*\{[^}]*overflow-y:\s*scroll;/s);
    assert.match(css, /\.popup\s*\{[^}]*min-height:\s*540px;/s);
    assert.match(css, /\.brand\s*\{[^}]*position:\s*sticky;/s);
    assert.equal(/\.popup\s*\{[^}]*overflow:\s*clip;/s.test(css), false);
});

test('pasar de inicio a carga y resultado no anima el marco exterior', () => {
    const p = popup();
    p.run("authenticated=true; pageSupported=true; pageKey='tab:a'; setPanelState('initial'); setReportExpanded(false)");
    p.enableHeightAnimations();
    p.run("renderAnalysis({status:'loading',message:'Procesando'})");
    assert.equal(p.heightAnimations.length, 0);
    assert.equal(p.element('loading-section').hidden, false);
    p.advance(1500);
    p.run("renderAnalysis({status:'success',publicationId:1,evaluation:{level:'LOW',score:0,availableFactors:2,totalFactors:2,factors:[]}})");
    assert.equal(p.heightAnimations.length, 0);
    assert.equal(p.element('result-section').hidden, false);
    assert.equal(p.element('result-section').scrollCalls.length, 1);
});

test('abrir el reporte no redimensiona el marco nativo', () => {
    const p = popup();
    p.run("authenticated=true; pageSupported=true; setPanelState('initial'); setReportExpanded(false)");
    p.enableHeightAnimations();
    p.run("setReportExpanded(true)");
    assert.equal(p.element('report-details').hidden, false);
    p.run("setReportExpanded(false)");
    assert.equal(p.element('report-details').hidden, true);
    assert.equal(p.heightAnimations.length, 0);
});

test('el login y logout conservan transiciones del contenido sin cambiar el marco', () => {
    const p = popup();
    p.enableHeightAnimations();
    p.run("showLoggedIn('Usuario')");
    p.run("showLoggedOut('Sesión cerrada')");
    assert.equal(p.heightAnimations.length, 0);
    assert.equal(p.element('authenticated-content').hidden, true);
    assert.equal(p.element('login-form').hidden, false);
});

test('el contenido todavía anima su aparición y respeta reducir movimiento', () => {
    const css = fs.readFileSync(path.join(__dirname, '../popup/popup.css'), 'utf8');
    assert.match(css, /#loading-section:not\(\[hidden\]\)[\s\S]*animation:\s*ml-panel-enter/);
    assert.match(css, /@media\s*\(prefers-reduced-motion:\s*reduce\)/);
    const p = popup();
    p.setReducedMotion(true);
    p.enableHeightAnimations();
    p.run("authenticated=true; pageSupported=true; setPanelState('loading')");
    assert.equal(p.heightAnimations.length, 0);
    assert.equal(p.element('loading-section').hidden, false);
});


test('las notas de Temu se resumen sin perder calificación y cantidad de opiniones', () => {
    const p = popup();
    const sourceText = 'El producto muestra 4.7/5 con 286 opiniones en Temu. Son valoraciones observadas, no ventas verificadas ni garantía de autenticidad.';
    const label = p.run("conciseFactorLabel({code:'TEMU_PRODUCT_REVIEWS_OBSERVED',description:" + JSON.stringify(sourceText) + "})");
    assert.equal(label, 'Calificación: 4,7/5 (286 opiniones).');
    assert.ok(label.length < sourceText.length / 2);
    assert.equal(p.run("conciseFactorLabel({code:'TEMU_LOW_PRODUCT_RATING',description:'El producto muestra 2.5/5 con 33 opiniones en Temu. Son valoraciones observadas.'})"),
        'Calificación baja: 2,5/5 (33 opiniones).');
    assert.equal(p.run("conciseFactorLabel({code:'TEMU_STAR_SELLER_OBSERVED',description:'La página de Temu mostraba la etiqueta Star Seller para la tienda. MarketLens no ha verificado sus criterios.'})"),
        'Temu muestra el distintivo Star Seller.');
});

test('el resumen presenta las notas cortas y conserva el detalle original en tooltip', () => {
    const p = popup();
    const description = 'El producto muestra 4.7/5 con 286 opiniones en Temu. Son valoraciones observadas, no ventas verificadas ni garantía de autenticidad.';
    p.run("renderRisk({publicationId:13,evaluation:{level:'LOW',score:0,availableFactors:3,totalFactors:3,factors:[{code:'TEMU_PRODUCT_REVIEWS_OBSERVED',description:" + JSON.stringify(description) + "}]}})");
    const list = p.element('result-factors');
    assert.equal(list.children.length, 1);
    assert.equal(list.children[0].textContent, 'Calificación: 4,7/5 (286 opiniones).');
    assert.equal(list.children[0].title, description);
});

test('al desplegar el reporte se enfoca el formulario y no se repite al cerrarlo', () => {
    const p = popup();
    p.run("authenticated=true; pageSupported=true; setPanelState('initial'); setReportExpanded(false); updateActions()");
    const panel = p.element('report-details');
    p.run("toggleReport()");
    assert.equal(panel.hidden, false);
    assert.equal(panel.scrollCalls.length, 1);
    assert.equal(panel.scrollCalls[0].behavior, 'smooth');
    assert.equal(panel.scrollCalls[0].block, 'start');
    p.run("toggleReport()");
    assert.equal(panel.hidden, false);
    assert.equal(panel.classList.contains('is-closing'), true);
    p.advance(300);
    assert.equal(panel.hidden, true);
    assert.equal(panel.scrollCalls.length, 1);
});

test('el desplazamiento del reporte respeta movimiento reducido y no se activa oculto', () => {
    const p = popup();
    p.setReducedMotion(true);
    p.run("authenticated=true; pageSupported=true; setPanelState('initial'); setReportExpanded(false); updateActions()");
    p.run("setReportExpanded(true)");
    assert.equal(p.element('report-details').scrollCalls[0].behavior, 'auto');
    p.run("setPanelState('loading'); setReportExpanded(false)");
    p.run("setReportExpanded(true)");
    assert.equal(p.element('report-details').scrollCalls.length, 1);
});

test('durante la carga se activa el diseño centrado sin cambiar la altura exterior', () => {
    const p = popup();
    p.run("authenticated=true; pageSupported=true; setPanelState('initial')");
    p.run("setPanelState('loading')");
    // El mock carece de querySelector('.popup'), por lo que se comprueba
    // la regla visual y los demás estados con CSS.
    const css = fs.readFileSync(path.join(__dirname, '../popup/popup.css'), 'utf8');
    assert.match(css, /\.popup\.is-analyzing #authenticated-content\s*\{[^}]*display:\s*flex;/s);
    assert.match(css, /\.popup\.is-analyzing \.analysis-card\s*\{[^}]*flex:\s*1 1 auto;/s);
    assert.match(css, /\.popup\.is-analyzing #loading-section:not\(\[hidden\]\)\s*\{[^}]*justify-content:\s*center;/s);
    assert.match(css, /body\s*\{[^}]*height:\s*540px;/s);
});

test('la carga centra solo su estado y restaura el diseño de resultados', () => {
    const p = popup();
    p.enableHeightAnimations();
    p.run("authenticated=true; pageSupported=true; setPanelState('loading')");
    assert.equal(p.run("document.querySelector('.popup').classList.contains('is-analyzing')"), true);
    p.run("setPanelState('success')");
    assert.equal(p.run("document.querySelector('.popup').classList.contains('is-analyzing')"), false);
});


test('las ventas de Temu muestran solo el valor, sin sold ni estimadas', () => {
    const p = popup();
    const product = p.run("conciseFactorLabel({code:'TEMU_PRODUCT_SALES_OBSERVED',description:'Temu muestra \"16K+ sold\" para el producto. Es una cifra aproximada.'})");
    const store = p.run("conciseFactorLabel({code:'TEMU_STORE_SALES_OBSERVED',description:'Temu muestra \"300K+ Sold\" para la tienda. No son ventas verificadas.'})");
    const exact = p.run("conciseFactorLabel({code:'TEMU_PRODUCT_SALES_OBSERVED',description:'Temu muestra \"3 sold\" para el producto.'})");
    assert.equal(product, 'Ventas del producto: 16K+.');
    assert.equal(store, 'Ventas de tienda: 300K+.');
    assert.equal(exact, 'Ventas del producto: 3.');
});

test('se conservan los matices de calificaciones bajas y moderadas', () => {
    const p = popup();
    const rated = p.run("conciseFactorLabel({code:'TEMU_PRODUCT_REVIEWS_OBSERVED',description:'El producto muestra 4.7/5 con 286 opiniones en Temu.'})");
    const moderate = p.run("conciseFactorLabel({code:'TEMU_MODERATE_PRODUCT_RATING',description:'El producto muestra 3.6/5 con 45 opiniones en Temu.'})");
    assert.equal(rated, 'Calificación: 4,7/5 (286 opiniones).');
    assert.equal(moderate, 'Calificación moderada: 3,6/5 (45 opiniones).');
});

test('al cerrar el reporte se vuelve arriba suavemente antes de ocultar el formulario', () => {
    const p = popup();
    p.run("authenticated=true; pageSupported=true; setPanelState('initial'); setReportExpanded(false); updateActions()");
    p.run("setReportExpanded(true)");
    p.scrollBody.scrollTop = 310;
    p.run("toggleReport()");
    assert.equal(p.scrollBody.scrollCalls.length, 1);
    assert.equal(p.scrollBody.scrollCalls[0].behavior, 'smooth');
    assert.equal(p.scrollBody.scrollCalls[0].top, 0);
    assert.equal(p.element('report-details').hidden, false, 'Mantener el reporte abierto durante el regreso');
    assert.equal(p.pendingTimers(), 1);
    p.advance(419);
    assert.equal(p.element('report-details').hidden, false);
    p.advance(1);
    assert.equal(p.element('report-details').hidden, false, 'Tras el scroll comienza la animación de salida');
    assert.equal(p.element('report-details').classList.contains('is-closing'), true);
    assert.equal(p.element('report-toggle').getAttribute('aria-expanded'), 'false');
    p.advance(299);
    assert.equal(p.element('report-details').hidden, false);
    p.advance(1);
    assert.equal(p.element('report-details').hidden, true);
    assert.equal(p.element('report-details').classList.contains('is-closing'), false);
});

test('un segundo clic interrumpe el cierre diferido del reporte', () => {
    const p = popup();
    p.run("authenticated=true; pageSupported=true; setPanelState('initial'); setReportExpanded(true); updateActions()");
    p.scrollBody.scrollTop = 230;
    p.run("toggleReport()");
    assert.equal(p.pendingTimers(), 1);
    p.run("toggleReport()");
    assert.equal(p.pendingTimers(), 0);
    p.advance(500);
    assert.equal(p.element('report-details').hidden, false);
});

test('el cierre también se anima sin scroll, salvo con movimiento reducido', () => {
    const p = popup();
    p.run("authenticated=true; pageSupported=true; setPanelState('initial'); setReportExpanded(true); updateActions()");
    p.run("toggleReport()");
    assert.equal(p.element('report-details').hidden, false);
    assert.equal(p.element('report-details').classList.contains('is-closing'), true);
    assert.equal(p.pendingTimers(), 1);
    p.advance(300);
    assert.equal(p.element('report-details').hidden, true);
    assert.equal(p.pendingTimers(), 0);
    p.setReducedMotion(true);
    p.run("setReportExpanded(true)");
    p.scrollBody.scrollTop = 180;
    p.run("toggleReport()");
    assert.equal(p.element('report-details').hidden, true);
    assert.equal(p.element('report-details').classList.contains('is-closing'), false);
    assert.equal(p.scrollBody.scrollCalls.length, 0);
    assert.equal(p.pendingTimers(), 0);
});

test('si cambia la pantalla se cancela el cierre pendiente', () => {
    const p = popup();
    p.run("authenticated=true; pageSupported=true; setPanelState('initial'); setReportExpanded(true); updateActions()");
    p.scrollBody.scrollTop = 200;
    p.run("toggleReport()");
    assert.equal(p.pendingTimers(), 1);
    p.run("setReportExpanded(false); setPanelState('loading')");
    assert.equal(p.pendingTimers(), 0);
    assert.equal(p.element('report-details').hidden, true);
    p.advance(800);
    assert.equal(p.element('report-details').hidden, true);
});


test('un nuevo análisis o cierre de sesión cancela el colapso diferido', () => {
    const p = popup();
    p.run("authenticated=true; pageSupported=true; setPanelState('initial'); setReportExpanded(true); updateActions()");
    p.scrollBody.scrollTop = 160;
    p.run("toggleReport()");
    assert.equal(p.pendingTimers(), 1);
    p.run("setPanelState('loading')");
    assert.equal(p.pendingTimers(), 0);
    assert.equal(p.element('report-details').hidden, true);

    p.run("setPanelState('initial'); setReportExpanded(true)");
    p.scrollBody.scrollTop = 190;
    p.run("toggleReport()");
    assert.equal(p.pendingTimers(), 1);
    p.run("showLoggedOut('Sesión cerrada')");
    assert.equal(p.pendingTimers(), 0);
    assert.equal(p.element('login-form').hidden, false);
    p.advance(500);
    assert.equal(p.element('authenticated-content').hidden, true);
});


test('otro clic durante la animación de salida devuelve el formulario a abierto', () => {
    const p = popup();
    p.run("authenticated=true; pageSupported=true; setPanelState('initial'); setReportExpanded(true); updateActions()");
    p.run("toggleReport()");
    const details = p.element('report-details');
    assert.equal(details.classList.contains('is-closing'), true);
    assert.equal(details.style.getPropertyValue('--report-open-height'), '320px');
    assert.equal(p.pendingTimers(), 1);
    p.run("toggleReport()");
    assert.equal(details.hidden, false);
    assert.equal(details.classList.contains('is-closing'), false);
    assert.equal(details.style.getPropertyValue('--report-open-height'), '');
    assert.equal(p.element('report-toggle').getAttribute('aria-expanded'), 'true');
    assert.equal(p.pendingTimers(), 0);
    p.advance(400);
    assert.equal(details.hidden, false);
});

test('el cambio de estado durante la salida cancela el temporizador y oculta reporte', () => {
    const p = popup();
    p.run("authenticated=true; pageSupported=true; setPanelState('initial'); setReportExpanded(true); updateActions()");
    p.run("toggleReport()");
    assert.equal(p.pendingTimers(), 1);
    p.run("setPanelState('loading')");
    assert.equal(p.pendingTimers(), 0);
    assert.equal(p.element('report-details').hidden, true);
    assert.equal(p.element('report-details').classList.contains('is-closing'), false);
});

test('CSS anima opacidad y altura al cerrar y respeta movimiento reducido', () => {
    const css = fs.readFileSync(path.join(__dirname, '../popup/popup.css'), 'utf8');
    assert.match(css, /@keyframes ml-report-leave\s*\{[\s\S]*?opacity:\s*0;[\s\S]*?max-height:\s*0;/);
    assert.match(css, /#report-details\.is-closing\s*\{[^}]*animation:\s*ml-report-leave\s*\.3s/s);
    assert.match(css, /#report-details\.is-closing\s*\{\s*animation:\s*none\s*!important;/);
});

test('scrollbar ocupa ancho permanente y el encabezado sigue fijo', () => {
    const css = fs.readFileSync(path.join(__dirname, '../popup/popup.css'), 'utf8');
    assert.match(css, /body\s*\{[^}]*overflow-y:\s*scroll;[^}]*scrollbar-gutter:\s*stable;/s);
    assert.match(css, /body\s*\{[^}]*height:\s*540px;/s);
    assert.match(css, /\.brand\s*\{[^}]*position:\s*sticky;[^}]*top:\s*0;/s);
});


test('Mercado Libre: permite inspeccionar catálogo pero exige una oferta individual', () => {
    const p = popup();
    const catalog = 'https://www.mercadolibre.com.ar/disco-solido-ssd-kingston-kc600-512gb-color-gris-oscuro/p/MLA24356923';
    const simple = 'https://www.mercadolibre.com.ar/p/MLA24356923';
    const offer = 'https://articulo.mercadolibre.com.ar/MLA-123456789-ssd-kingston';
    const userProduct = 'https://www.mercadolibre.com.ar/ssd-kingston/up/MLAU123456789';
    assert.equal(p.run("isMercadoLibreCatalogUrl(" + JSON.stringify(catalog) + ")"), true);
    assert.equal(p.run("isMercadoLibreCatalogUrl(" + JSON.stringify(simple) + ")"), true);
    assert.equal(p.run("isSupportedPage(" + JSON.stringify(catalog) + ",'MERCADO_LIBRE')"), true);
    assert.equal(p.run("isSupportedPage(" + JSON.stringify(simple) + ",'MERCADO_LIBRE')"), true);
    assert.equal(p.run("isSupportedPage(" + JSON.stringify(offer) + ",'MERCADO_LIBRE')"), true);
    assert.equal(p.run("isSupportedPage(" + JSON.stringify(userProduct) + ",'MERCADO_LIBRE')"), true);
});

test('la extracción nunca asigna el ID de catálogo a una publicación', () => {
    const p = popup();
    p.run("globalThis.window = {location:{pathname:'/disco-solido-ssd-kingston-kc600-512gb-color-gris-oscuro/p/MLA24356923',href:'https://www.mercadolibre.com.ar/ssd/p/MLA24356923'}}");
    p.run("document.querySelectorAll = () => []");
    const response = p.run('extractMercadoLibre()');
    assert.equal(response.found, false);
    assert.match(response.message, /ficha de catálogo/);
});

test('la protección del background rechaza el catálogo antes del POST', () => {
    const file = fs.readFileSync(path.join(__dirname, '../background.js'), 'utf8');
    const context = vm.createContext({
        browser: {runtime:{onMessage:{addListener(){}}}},
        URL
    });
    vm.runInContext(file, context, {filename: 'background.js'});
    const data = {
        marketplace:'MERCADO_LIBRE', title:'Kingston KC600 512 GB',
        price:'320000', seller:'Vendedor de prueba', externalId:'MLA24356923',
        url:'https://www.mercadolibre.com.ar/disco-solido-ssd-kingston-kc600-512gb-color-gris-oscuro/p/MLA24356923'
    };
    assert.throws(
        () => vm.runInContext("requireExtractedData(" + JSON.stringify(data) + ")", context),
        /ficha de catálogo/
    );
    data.url = 'https://articulo.mercadolibre.com.ar/MLA-123456789-ssd-kingston';
    data.externalId = 'MLA123456789';
    assert.equal(vm.runInContext("requireExtractedData(" + JSON.stringify(data) + ").externalId", context),
        'MLA123456789');
});
