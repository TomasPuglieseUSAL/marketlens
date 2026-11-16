const ext = globalThis.browser ?? globalThis.chrome;
const API_BASE = 'http://localhost:8080/api';
const AUTH_KEY = 'marketlensAuth';
const $ = (id) => document.getElementById(id);

// Esta preferencia es independiente de la sesion de MarketLens.
// Se almacena en la extension, no en localStorage del sitio web.
const THEME_STORAGE_KEY = 'marketlens.extension.theme';
let currentTheme = 'light';

function applyTheme(value) {
    currentTheme = value === 'dark' ? 'dark' : 'light';
    document.documentElement.dataset.theme = currentTheme;
    const nextMode = currentTheme === 'light' ? 'oscuro' : 'claro';
    const button = $('theme-toggle');
    $('theme-toggle-icon').textContent = currentTheme === 'light' ? '\u263E' : '\u2600';
    button.title = `Cambiar a modo ${nextMode}`;
    button.setAttribute('aria-label', `Activar modo ${nextMode}`);
    button.setAttribute('aria-pressed', String(currentTheme === 'dark'));
}

async function restoreTheme() {
    try {
        const stored = await ext.storage.local.get(THEME_STORAGE_KEY);
        applyTheme(stored[THEME_STORAGE_KEY]);
    } catch {
        applyTheme('light');
    }
}

async function toggleTheme() {
    const next = currentTheme === 'light' ? 'dark' : 'light';
    applyTheme(next);
    try {
        await ext.storage.local.set({[THEME_STORAGE_KEY]: next});
    } catch {
        // Si el almacenamiento no esta disponible, el tema sigue funcionando
        // hasta que se cierre esta ventana del popup.
    }
}

let activeTab = null;
let pageKey = null;
let currentMarketplace = 'OTHER';
let authenticated = false;
let pageSupported = false;
let pendingAction = null;
let extractedData = null;
let lastPublicationId = null;
let submittingReport = false;
let preparing = false;
let lastRenderedPanelStatus = null;

// La carga exitosa permanece perceptible incluso cuando el backend responde rápido.
// Los errores y la preferencia de movimiento reducido no esperan artificialmente.
const MIN_LOADING_VISIBLE_MS = 1500;
const LOGOUT_FADE_MS = 180;
let loadingStartedAt = null;
let scheduledReveal = null;
let deferredSuccessfulResult = null;
let logoutInProgress = false;
const REPORT_CLOSE_SCROLL_MS = 420;
const REPORT_COLLAPSE_MS = 300;
let reportCloseTimer = null;
let reportCollapseTimer = null;

// Los navegadores redimensionan externamente los popups de forma discreta.
// Mantener una altura de ventana fija con CSS y animar solo el contenido.
function updatePopupContent(update) {
    return update();
}

function prefersReducedMotion() {
    return typeof matchMedia === 'function'
        && matchMedia('(prefers-reduced-motion: reduce)').matches;
}
function cancelScheduledReveal() {
    if (scheduledReveal !== null) clearTimeout(scheduledReveal);
    scheduledReveal = null;
    deferredSuccessfulResult = null;
}
function resetLoadingTransition() {
    cancelScheduledReveal();
    loadingStartedAt = null;
}

const marketplaceLabels = {
    MERCADO_LIBRE: 'Mercado Libre',
    FACEBOOK_MARKETPLACE: 'Facebook Marketplace',
    TEMU: 'Temu',
    OTHER: 'No compatible'
};

function matchesDomain(host, domain) {
    return host === domain || host.endsWith(`.${domain}`);
}
function detectMarketplace(url) {
    try {
        const host = new URL(url).hostname.toLowerCase();
        if (matchesDomain(host, 'mercadolibre.com.ar') ||
            matchesDomain(host, 'mercadolibre.com') ||
            matchesDomain(host, 'mercadolivre.com.br')) return 'MERCADO_LIBRE';
        if (matchesDomain(host, 'facebook.com')) return 'FACEBOOK_MARKETPLACE';
        if (matchesDomain(host, 'temu.com')) return 'TEMU';
        return 'OTHER';
    } catch { return 'OTHER'; }
}
function isMercadoLibreCatalogUrl(url) {
    try {
        return detectMarketplace(url) === 'MERCADO_LIBRE'
            && /\/(?:[^/]+\/)?p\/MLA\d{6,}\/?$/i.test(new URL(url).pathname);
    } catch {
        return false;
    }
}
function isSupportedPage(url, marketplace) {
    try {
        const path = new URL(url).pathname;
        // Una ficha de catálogo puede tener una oferta elegida y mostrada.
        // La identidad MLA/MLAU se resuelve dentro del extractor; la ficha
        // genérica por sí sola nunca es una referencia importable.
        if (marketplace === 'MERCADO_LIBRE' && isMercadoLibreCatalogUrl(url))
            return true;
        if (marketplace === 'FACEBOOK_MARKETPLACE')
            return /^\/marketplace\/item\/\d{6,30}\/?$/.test(path);
        if (marketplace === 'TEMU') {
            const address = new URL(url);
            const id = address.searchParams.get('goods_id');
            return (/^\/goods\.html\/?$/i.test(path) && /^\d{6,30}$/.test(id ?? '')) ||
                /-g-\d{6,30}\.html\/?$/i.test(path);
        }
        if (marketplace === 'MERCADO_LIBRE')
            return /\/up\/MLAU\d{6,}/i.test(path) ||
                /(?:^|\/)MLA-?\d{6,}/i.test(path) ||
                isMercadoLibreCatalogUrl(url);
        return false;
    } catch {return false;}
}
function displayError(error) {
    const value = error instanceof Error ? error.message : String(error);
    $('status').textContent = value;
    return value;
}
function validSession(auth) {
    return typeof auth?.token === 'string' && auth.token.length > 0 &&
        typeof auth.expiresAt === 'number' && auth.expiresAt > Date.now();
}
async function getSession() {
    const stored = await ext.storage.local.get(AUTH_KEY);
    return validSession(stored[AUTH_KEY]) ? stored[AUTH_KEY] : null;
}
function showLoggedOut(message = 'Iniciá sesión para usar MarketLens.') {
    cancelReportClose();
    updatePopupContent(() => {
        authenticated = false;
        resetLoadingTransition();
        // Nunca ocultar el formulario de acceso por el estado del análisis.
        $('auth-card').hidden = false;
        $('auth-status').textContent = message;
        $('login-form').hidden = false;
        $('logout-button').hidden = true;
        $('authenticated-content').hidden = true;
    });
}
function showLoggedIn(username) {
    updatePopupContent(() => {
        authenticated = true;
        $('auth-status').textContent = username;
        $('login-form').hidden = true;
        $('logout-button').hidden = false;
        $('authenticated-content').hidden = false;
        updateActions();
    });
}
async function checkAuthentication() {
    const session = await getSession();
    if (!session) { showLoggedOut(); return; }
    let response;
    try {
        response = await fetch(`${API_BASE}/users/me`, {
            headers: {Authorization: `Bearer ${session.token}`},
            credentials: 'omit'
        });
    } catch {
        showLoggedOut('No se pudo conectar con el backend de MarketLens.');
        return;
    }
    if (response.status === 401) {
        await ext.storage.local.remove(AUTH_KEY);
        showLoggedOut('Tu sesión expiró. Iniciá sesión nuevamente.');
        return;
    }
    if (!response.ok) {
        showLoggedOut(`Error de autenticación HTTP ${response.status}.`);
        return;
    }
    const me = await response.json().catch(() => ({}));
    showLoggedIn(me.username || me.email || 'Usuario autenticado');
}
async function handleLogin(event) {
    event.preventDefault();
    $('login-button').disabled = true;
    $('auth-status').textContent = 'Iniciando sesión…';
    try {
        const response = await fetch(`${API_BASE}/auth/login`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            credentials: 'omit',
            body: JSON.stringify({
                username: $('login-username').value.trim(),
                password: $('login-password').value
            })
        });
        const result = await response.json().catch(() => null);
        if (!response.ok) throw new Error(result?.message || `HTTP ${response.status}`);
        if (!result?.token || !Number.isFinite(result?.expiresIn) || result.expiresIn <= 0)
            throw new Error('Respuesta de inicio de sesión inválida.');
        await ext.storage.local.set({
            [AUTH_KEY]: {token: result.token, expiresAt: Date.now() + result.expiresIn * 1000}
        });
        $('login-password').value = '';
        await checkAuthentication();
        await loadCurrentPage();
    } catch (error) {
        $('auth-status').textContent = `No se pudo iniciar sesión: ${displayError(error)}`;
    } finally { $('login-button').disabled = false; }
}
async function handleLogout() {
    if (logoutInProgress) return;
    logoutInProgress = true;
    $('logout-button').disabled = true;
    // Inhabilitar interacciones del análisis inmediatamente. La credencial
    // se elimina sin esperar a la transición visual.
    authenticated = false;
    updateActions();
    resetLoadingTransition();
    const animate = !prefersReducedMotion();
    if (animate) {
        $('auth-card').classList.add('auth-exiting');
        $('authenticated-content').classList.add('auth-exiting');
    }
    const visualExit = animate
        ? new Promise(resolve => setTimeout(resolve, LOGOUT_FADE_MS))
        : Promise.resolve();
    try {
        await ext.storage.local.remove(AUTH_KEY);
        await ext.storage.session.remove(AUTH_KEY).catch(() => {});
        await visualExit;
        $('login-password').value = '';
        showLoggedOut('Sesión cerrada.');
    } catch (error) {
        // No afirmar que se cerró la sesión cuando falló la revocación.
        await visualExit;
        authenticated = true;
        $('auth-status').textContent = 'No se pudo cerrar sesión. Reintentá.';
        updateActions();
    } finally {
        $('auth-card').classList.remove('auth-exiting');
        $('authenticated-content').classList.remove('auth-exiting');
        $('logout-button').disabled = false;
        logoutInProgress = false;
    }
}
function updateActions() {
    $('analyze-button').disabled = !authenticated || !pageSupported || preparing;
    $('report-toggle').disabled = !authenticated || !pageSupported || submittingReport
        || preparing || pendingAction !== null || !$('loading-section').hidden;
    $('report-submit').disabled = !authenticated || !pageSupported || submittingReport || preparing
        || pendingAction !== null;
}
function scrollToManualReport() {
    if (typeof requestAnimationFrame !== 'function') return;
    requestAnimationFrame(() => {
        const details = $('report-details');
        if (details.hidden || $('report-section').hidden) return;
        details.scrollIntoView?.({
            behavior: prefersReducedMotion() ? 'auto' : 'smooth',
            block: 'start'
        });
    });
}
function cancelReportClose() {
    if (reportCloseTimer !== null) clearTimeout(reportCloseTimer);
    if (reportCollapseTimer !== null) clearTimeout(reportCollapseTimer);
    reportCloseTimer = null;
    reportCollapseTimer = null;
    const details = $('report-details');
    details.classList?.remove('is-closing');
    details.style?.removeProperty('--report-open-height');
    details.inert = false;
}
function setReportExpanded(expanded) {
    // Para cambios de publicación, logout y navegación se aplica el estado
    // directamente. El cierre animado se reserva al clic del usuario.
    cancelReportClose();
    const wasHidden = $('report-details').hidden;
    updatePopupContent(() => {
        $('report-details').hidden = !expanded;
        $('report-toggle').setAttribute('aria-expanded', String(expanded));
    });
    if (expanded && wasHidden) scrollToManualReport();
}
function animateReportCollapse() {
    const details = $('report-details');
    if (details.hidden) return;
    if (prefersReducedMotion()) {
        setReportExpanded(false);
        return;
    }

    // Mantener el formulario en el DOM mientras disminuyen su altura y
    // opacidad; ocultarlo solamente al terminar la animación.
    const openHeight = details.getBoundingClientRect?.().height;
    if (Number.isFinite(openHeight) && openHeight > 0) {
        details.style?.setProperty('--report-open-height', openHeight + 'px');
    }
    details.inert = true;
    details.classList?.add('is-closing');
    $('report-toggle').setAttribute('aria-expanded', 'false');
    reportCollapseTimer = setTimeout(() => {
        reportCollapseTimer = null;
        setReportExpanded(false);
    }, REPORT_COLLAPSE_MS);
}
function toggleReport() {
    if ($('report-toggle').disabled) return;
    // Otro clic durante el retorno o la desaparición cancela el cierre.
    if (reportCloseTimer !== null || reportCollapseTimer !== null) {
        cancelReportClose();
        $('report-toggle').setAttribute('aria-expanded', 'true');
        return;
    }
    if ($('report-details').hidden) {
        setReportExpanded(true);
        return;
    }

    // Regresar arriba antes de contraer un formulario largo. Mientras tanto
    // sigue visible; a continuación una segunda animación lo desvanece y
    // reduce su altura para evitar que desaparezca bruscamente.
    const body = document.body;
    const root = document.scrollingElement || document.documentElement;
    const scroller = body?.scrollTop > 0 ? body : root;
    if (prefersReducedMotion()) {
        setReportExpanded(false);
        return;
    }
    if (scroller?.scrollTo && scroller.scrollTop > 4) {
        scroller.scrollTo({top: 0, behavior: 'smooth'});
        reportCloseTimer = setTimeout(() => {
            reportCloseTimer = null;
            animateReportCollapse();
        }, REPORT_CLOSE_SCROLL_MS);
    } else {
        animateReportCollapse();
    }
}
function setPanelState(state) {
    updatePopupContent(() => applyPanelState(state));
}
function applyPanelState(state) {
    // Los pasos son excluyentes: elección de precio -> corrección -> resultado.
    // Los paneles entran con una transición CSS respetando reduced-motion.
    const busy = state === 'price' || state === 'correction' || state === 'loading';
    if (busy && (reportCloseTimer !== null || reportCollapseTimer !== null)) {
        // El reporte no puede terminar su cierre diferido después de
        // haber iniciado un análisis o cambiado a otro formulario.
        cancelReportClose();
        $('report-details').hidden = true;
        $('report-toggle').setAttribute('aria-expanded', 'false');
    }
    $('analysis-title').textContent = ({
        price: 'Confirmar precio',
        correction: 'Completar datos',
        loading: 'Analizando publicación',
        success: 'Resultado del análisis',
        error: 'Análisis incompleto'
    })[state] || 'Analizar publicación';
    $('analysis-overview').hidden = busy;
    $('analyze-button').hidden = state !== 'initial';
    $('temu-price-panel').hidden = state !== 'price';
    $('facebook-correction').hidden = state !== 'correction';
    $('loading-section').hidden = state !== 'loading';
    $('result-section').hidden = state !== 'success';
    $('error-section').hidden = state !== 'error';
    $('report-section').hidden = busy;
    // Una clase del contenedor permite centrar verticalmente la carga
    // dentro de la ventana fija, sin alterar el resto de las pantallas.
    const popup = document.querySelector?.('.popup');
    if (popup?.classList) {
        if (state === 'loading') popup.classList.add('is-analyzing');
        else popup.classList.remove('is-analyzing');
    }
    // Durante un análisis se prioriza la información de la publicación.
    // La sección de usuario reaparece al mostrar el resultado o un error.
    $('auth-card').hidden = authenticated && busy;
}
function scrollToAnalysisResult() {
    // El contenedor mantiene su altura; solo se desplaza su contenido.
    if (typeof requestAnimationFrame !== 'function') return;
    requestAnimationFrame(() => {
        const result = $('result-section');
        if (result.hidden) return;
        result.scrollIntoView?.({
            behavior: prefersReducedMotion() ? 'auto' : 'smooth',
            block: 'start'
        });
    });
}
function renderAnalysis(value) {
    if (!authenticated) return;
    // Evitar que un cambio de almacenamiento en segundo plano interrumpa
    // la edición del formulario antes de pulsar Continuar o Cancelar.
    if (pendingAction && (!$('facebook-correction').hidden || !$('temu-price-panel').hidden)) return;
    const status = value?.status || 'initial';

    if (status === 'loading') {
        if (lastRenderedPanelStatus !== 'loading') {
            resetLoadingTransition();
            loadingStartedAt = Date.now();
        }
    } else if (status === 'success' && loadingStartedAt !== null
            && !prefersReducedMotion()) {
        const remaining = MIN_LOADING_VISIBLE_MS - (Date.now() - loadingStartedAt);
        if (remaining > 0) {
            deferredSuccessfulResult = value;
            if (scheduledReveal === null) {
                const expectedPageKey = pageKey;
                scheduledReveal = setTimeout(() => {
                    scheduledReveal = null;
                    const result = deferredSuccessfulResult;
                    deferredSuccessfulResult = null;
                    if (result && authenticated && pageKey === expectedPageKey) {
                        renderAnalysis(result);
                    }
                }, remaining);
            }
            return;
        }
        resetLoadingTransition();
    } else {
        resetLoadingTransition();
    }

    const previousStatus = lastRenderedPanelStatus;
    updatePopupContent(() => applyRenderedAnalysis(value, status));
    if (status === 'success' && previousStatus !== 'success') scrollToAnalysisResult();
}
function applyRenderedAnalysis(value, status) {
    lastRenderedPanelStatus = status;
    setPanelState(status);
    if (status === 'loading') {
        $('loading-message').textContent = value.message || 'Procesando…';
        $('status').textContent = 'El análisis continúa aunque cierres este popup.';
    } else if (status === 'error') {
        $('status').textContent = 'Podés reintentar el análisis.';
        $('error-message').textContent = value.message || 'Error inesperado.';
    } else if (status === 'success') {
        renderRisk(value);
        $('status').textContent = (value.reused
            ? 'Publicación existente evaluada nuevamente.'
            : 'Análisis completado.') +
            (value.sellerRefreshWarning ? ` ${value.sellerRefreshWarning}` : '');
        // El foco de scroll se aplica fuera de la mutación, después de
        // iniciar (o terminar) la interpolación de altura.
    } else if (pageSupported) {
        $('status').textContent = 'Publicación compatible detectada.';
    }
    updateActions();
}
// Las explicaciones completas se conservan en la evaluación del backend.
 // El popup muestra frases breves; los detalles permanecen en MarketLens.
function conciseFactorLabel(factor) {
    const full = String(factor?.description ?? factor?.reason ?? factor?.name ?? '').trim();
    if (!full) return '';
    const code = String(factor?.code ?? '').toUpperCase();
    const rating = full.match(/\b([0-5](?:[.,]\d+)?)\s*\/\s*5\b/);
    const reviews = full.match(/\b(\d[\d.,]*)\s+opiniones\b/i);
    const quoted = full.match(/[“"]([^"”]{1,60})[”"]/);
    // Temu muestra "16K+ sold" o "300K+ Sold"; "sold" es un texto de UI,
    // no parte del valor que interesa al usuario.
    const displayedSales = quoted?.[1].replace(/\s+sold\s*$/i, '').trim();
    const ratingLabel = rating ? rating[1].replace('.', ',') + '/5' : '';

    if (code === 'ML_PRODUCT_REVIEWS_OBSERVED'
            || code === 'TEMU_PRODUCT_REVIEWS_OBSERVED' || code === 'TEMU_LOW_PRODUCT_RATING'
            || code === 'TEMU_MODERATE_PRODUCT_RATING') {
        if (ratingLabel && reviews) {
            const prefix = code === 'TEMU_LOW_PRODUCT_RATING' ? 'Calificación baja: '
                : code === 'TEMU_MODERATE_PRODUCT_RATING' ? 'Calificación moderada: '
                : 'Calificación: ';
            return prefix + ratingLabel + ' (' + reviews[1] + ' opiniones).';
        }
    }
    if (code === 'ML_SELLER_REPUTATION_OBSERVED' && ratingLabel)
        return 'Reputación del vendedor: ' + ratingLabel + ' (observada).';
    if (code === 'MODERATE_SELLER_REPUTATION')
        return 'Reputación intermedia del vendedor: 3/5.';
    if (code === 'TEMU_SELLER_RATING_OBSERVED' && ratingLabel)
        return 'Tienda: ' + ratingLabel + ' (valoración observada).';
    if (code === 'TEMU_STAR_SELLER_OBSERVED')
        return 'Temu muestra el distintivo Star Seller.';
    if (code === 'TEMU_STORE_TENURE_OBSERVED' && quoted)
        return 'Antigüedad de tienda: ' + quoted[1] + ' (aproximada).';
    if (code === 'TEMU_STORE_SALES_OBSERVED' && displayedSales)
        return 'Ventas de tienda: ' + displayedSales + '.';
    if (code === 'TEMU_PRODUCT_SALES_OBSERVED' && displayedSales)
        return 'Ventas del producto: ' + displayedSales + '.';

    // Para otros factores, usar la primera frase solo cuando existe
    // una explicación secundaria. Nunca cortar números a mitad de texto.
    const firstSentence = full.match(/^(.+?[.!?])(?:\s|$)/)?.[1];
    return full.length > 115 && firstSentence ? firstSentence : full;
}
function renderRisk(value) {
    const evaluation = value.evaluation || {};
    lastPublicationId = value.publicationId || null;
    const level = String(evaluation.level || 'INDETERMINATE').toUpperCase();
    $('result-section').dataset.level = level;
    const labels = {
        LOW: ['Riesgo bajo', 'No se detectaron señales relevantes en los datos analizados. No garantiza autenticidad ni una compra segura.', '✓'],
        MEDIUM: ['Riesgo medio', 'Se detectaron factores que conviene revisar.', '!'],
        HIGH: ['Riesgo alto', 'Se detectaron múltiples señales de riesgo.', '!'],
        INDETERMINATE: ['Riesgo indeterminado', 'Los datos disponibles no tienen respaldo suficiente para clasificar el riesgo.', '?']
    };
    const descriptor = labels[level] ?? ['Riesgo: ' + level, 'Resultado del análisis.', '?'];
    $('result-level').textContent = descriptor[0];
    $('result-explanation').textContent = descriptor[1];
    $('result-icon').textContent = descriptor[2];
    $('result-score').textContent = Number.isFinite(evaluation.score) ? String(evaluation.score) : '—';
    const available = evaluation.availableFactors;
    const total = evaluation.totalFactors;
    $('result-coverage').textContent = Number.isInteger(available) && Number.isInteger(total)
        ? `${available}/${total}` : '—';
    const coverage = String(evaluation.coverageStatus ?? '').toUpperCase();
    const factorDetails = Array.isArray(evaluation.coverageFactors)
        ? evaluation.coverageFactors : [];
    const missing = factorDetails.filter(item => item && item.available === false)
        .map(item => item.name).filter(Boolean);
    const detail = missing.length
        ? 'Falta: ' + missing.join(', ') + '. '
        : factorDetails.length ? 'Todos los factores disponibles fueron observados. ' : '';
    $('result-coverage-note').textContent = detail + (
        level === 'INDETERMINATE'
            ? 'La cobertura mide datos disponibles, no su fiabilidad.'
            : coverage === 'PARTIAL'
                ? 'La información disponible es parcial; interpretá el resultado con precaución.'
                : 'La clasificación no garantiza una compra segura.');
    const factors = Array.isArray(evaluation.factors) ? evaluation.factors : [];
    $('result-factors').replaceChildren();
    for (const factor of factors.slice(0, 8)) {
        const label = factor?.description ?? factor?.reason ?? factor?.name;
        if (typeof label !== 'string' || !label.trim()) continue;
        const li = document.createElement('li');
        li.textContent = conciseFactorLabel(factor);
        // Tooltip con texto completo para quien necesite profundizar, sin
        // inundar visualmente el resumen.
        if (li.textContent !== label) li.title = label;
        $('result-factors').appendChild(li);
    }
    $('result-factors').hidden = $('result-factors').children.length === 0;
    $('open-publication-button').disabled = !Number.isSafeInteger(lastPublicationId);
}
function pageStateKey(key = pageKey) { return `marketlens:analysis:${key}`; }
async function restoreAnalysis(key = pageKey) {
    if (!key) return;
    const stored = await ext.storage.local.get(pageStateKey(key));
    if (key !== pageKey) return;
    const state = stored[pageStateKey(key)];
    if (state?.status === 'loading' && Date.now() - state.updatedAt > 3 * 60 * 1000) {
        renderAnalysis({status:'error', message:'El proceso quedó sin confirmación. Podés reintentar.'});
        return;
    }
    renderAnalysis(state ?? {status:'initial'});
}
async function loadCurrentPage() {
    try {
        const tabs = await ext.tabs.query({active: true, currentWindow: true});
        const tab = tabs[0];
        if (!tab?.url || !Number.isInteger(tab.id)) throw new Error('No se pudo consultar la pestaña activa.');
        const nextKey = `${tab.id}:${tab.url.split('#')[0]}`;
        const changed = nextKey !== pageKey;
        activeTab = tab;
        pageKey = nextKey;
        currentMarketplace = detectMarketplace(tab.url);
        pageSupported = isSupportedPage(tab.url, currentMarketplace);
        $('marketplace').textContent = marketplaceLabels[currentMarketplace] ?? 'Otro';
        $('page-title').textContent = tab.title || 'Publicación sin título';
        if (changed) {
            resetLoadingTransition();
            extractedData = null;
            pendingAction = null;
            lastPublicationId = null;
            setPanelState('initial');
            setReportExpanded(false);
            $('correction-status').textContent = '';
            $('temu-price-status').textContent = '';
            $('report-status').textContent = '';
            renderAnalysis({status:'initial'});
        }
        if (!pageSupported) {
            setPanelState('initial');
            $('status').textContent = isMercadoLibreCatalogUrl(tab.url)
                ? 'Esta es una ficha de catálogo. Abrí una oferta de un vendedor para analizarla.'
                : currentMarketplace === 'OTHER'
                    ? 'Abrí una publicación de Mercado Libre, Facebook Marketplace o Temu.'
                    : 'Abrí una publicación individual antes de analizar.';
        } else if (isMercadoLibreCatalogUrl(tab.url)) {
            // El enlace del catálogo no identifica qué oferta eligió el usuario.
            // No restaurar un resultado de otra oferta sólo por compartir URL.
            setPanelState('initial');
            $('status').textContent =
                'Ficha de catálogo: elegí una oferta con vendedor y precio antes de analizar.';
        } else {
            await restoreAnalysis(nextKey);
        }
        updateActions();
    } catch (error) {
        pageSupported = false;
        displayError(error);
        updateActions();
    }
}

function extractMercadoLibre() {
    // Una página de catálogo no representa una publicación identificable,
    // pero el panel de compra puede mostrar una oferta individual seleccionada.
    // Exigir que en un mismo panel VISIBLE haya enlace MLA/MLAU, vendedor y
    // precio: no elegir una oferta al azar entre recomendaciones o variantes.
    const isCatalog = /\/(?:[^/]+\/)?p\/MLA\d{6,}\/?$/i.test(window.location.pathname);
    function selectedCatalogOffer() {
        if (!isCatalog) return null;

        // Buybox nuevo: el radiobutton con "checked" puede pertenecer
        // a una alternativa NO seleccionada (atributo HTML inicial).
        // La selección efectiva está indicada por --SELECTED y debe
        // coincidir con el item_id que el formulario enviaría al checkout.
        const form = document.querySelector('#buybox-form');
        if (form) {
            const rows = [...form.querySelectorAll(
                'li.ui-pdp-buy-box-offers__offer-list-item'
            )];
            if (rows.length) {
                const active = rows.filter(row =>
                    row.classList?.contains(
                        'ui-pdp-buy-box-offers__offer-list-item--SELECTED')
                );
                if (active.length !== 1) return null;

                const selected = active[0];
                const radio = selected.querySelector(
                    'input.andes-radio__input[id]'
                );
                const inputId = radio?.id || radio?.getAttribute('value');
                const checkoutId = form.querySelector(
                    'input[type="hidden"][name="item_id"]'
                )?.value;
                if (!/^MLA[1-9]\d{5,}$/i.test(inputId || '')
                        || checkoutId !== inputId) return null;

                // Precio principal de la oferta; nunca tomar cuotas,
                // precio sin impuestos ni el monto de otra alternativa.
                const amount = selected.querySelector(
                    '.ui-pdp-buy-box-offers__offer-price ' +
                    '.ui-pdp-price__second-line .andes-money-amount'
                );
                const fraction = amount?.querySelector(
                    '.andes-money-amount__fraction'
                )?.textContent?.trim();
                const cents = amount?.querySelector(
                    '.andes-money-amount__cents'
                )?.textContent?.trim();

                // El nombre de tienda debe provenir de LA MISMA oferta.
                const seller = selected.querySelector(
                    '.ui-pdp-seller-summary__header__title ' +
                    '.ui-pdp-seller-summary__link'
                )?.textContent ||
                    selected.querySelector('#seller')?.textContent ||
                    selected.querySelector(
                        '.ui-pdp-seller-summary__header__image-container__image'
                    )?.getAttribute('alt');
                const sellerName = seller?.replace(/\s+/g, ' ').trim()
                    .replace(/^vendido\s+por\s*:?[ ]*/i, '')
                    .replace(/^tienda\s+oficial\s*/i, '').trim();
                if (!fraction || !sellerName) return null;

                return {
                    externalId: inputId.toUpperCase(),
                    selectedOfferId: inputId.toUpperCase(),
                    // Guardar la URL REAL del catálogo como procedencia,
                    // no inventar una permalink de publicación individual.
                    url: window.location.href.split('#')[0],
                    seller: sellerName,
                    price: cents ? fraction + ',' + cents : fraction,
                    currency: /\bpesos\b/i.test(
                        amount?.getAttribute('aria-label') || '') ? 'ARS' : null,
                    source: selected
                };
            }
        }

        // Estructuras alternativas anteriores: únicamente identificamos
        // enlaces de oferta y jamás usamos el ID global /p/MLA... .
        const visible = node => Boolean(node)
            && !node.hidden && node.getAttribute?.('aria-hidden') !== 'true'
            && (!node.getClientRects || node.getClientRects().length > 0);
        const scopes = Array.from(document.querySelectorAll(
            '[role="dialog"], [aria-modal="true"], .andes-modal, .ui-pdp-buybox'
        )).filter(visible);
        const candidates = new Map();

        for (const scope of scopes) {
            const selected = Array.from(scope.querySelectorAll(
                '[aria-selected="true"], [aria-checked="true"], [data-selected="true"]'
            )).filter(visible);
            // Ante una selección explícita, no mezclarla con las otras
            // tarjetas que siguen presentes en el mismo diálogo.
            const groups = selected.length
                ? selected.map(node =>
                    node.closest?.('[data-testid*="offer"], li, .ui-pdp-buybox') || node.parentElement || node)
                : [scope];
            for (const group of groups) {
                const sellerText = group.innerText || group.textContent || '';
                const sellerMatch = sellerText.match(
                    /(?:vendido\s+por|tienda)\s*:?\s*([^\r\n]+)/i
                );
                const sellerName = sellerMatch?.[1]?.replace(/\s+/g, ' ').trim();
                const priceContainer = group.querySelector(
                    '.ui-pdp-price__second-line .andes-money-amount'
                ) || group.querySelector('.andes-money-amount');
                const amount = priceContainer?.querySelector(
                    '.andes-money-amount__fraction'
                )?.textContent?.trim();
                const cents = priceContainer?.querySelector(
                    '.andes-money-amount__cents'
                )?.textContent?.trim();
                if (!sellerName || !amount) continue;

                for (const link of group.querySelectorAll('a[href]')) {
                    const href = link.getAttribute('href');
                    let address;
                    try { address = new URL(href, window.location.href); }
                    catch { continue; }
                    if (address.protocol !== 'https:' ||
                            !/(^|\.)mercadolibre\.com(\.ar)?$/.test(address.hostname)) continue;
                    const user = address.pathname.match(/\/up\/(MLAU\d{6,})(?:\/|$)/i);
                    const item = address.pathname.match(/(?:^|\/)MLA-?(\d{6,})(?=[\/-]|$)/i);
                    if (!user && !item) continue;
                    const id = user ? user[1].toUpperCase() : 'MLA' + item[1];
                    candidates.set(id, {
                        externalId: id,
                        url: address.href.split('#')[0],
                        seller: sellerName,
                        price: cents ? amount + ',' + cents : amount,
                        source: group
                    });
                }
            }
        }
        return candidates.size === 1 ? [...candidates.values()][0] : null;
    }
    const catalogOffer = selectedCatalogOffer();
    if (isCatalog && !catalogOffer) {
        return {
            found: false,
            message: 'La ficha muestra un producto de catálogo, pero no pude identificar una oferta con vendedor, precio e ID individual. Abrí el enlace de la oferta elegida y analizala allí.'
        };
    }
    function getText(selector) {
        return document.querySelector(selector)?.textContent?.trim() || null;
    }

    function getMeta(selector) {
        return document.querySelector(selector)?.getAttribute('content') || null;
    }

    function normalizeMercadoLibreSellerName(value) {
        if (!value || typeof value !== 'string') {
            return null;
        }

        const cleaned = value
            .replace(/\s+/g, ' ')
            .trim()
            .replace(/^vendido\s+por\s*:?\s*/i, '')
            .replace(/\s+\d+\s+ventas?(?:\s+realizadas?)?.*$/i, '')
            // "Vendido por SEBLONDIERIOS, 3 productos" no forma parte del nombre.
            .replace(/(?:\s*[,·|—–-]\s*|\s+)\d{1,3}(?:[.,]\d{3})*\s+productos?(?:\s+publicados?)?.*$/i, '')
            .trim();

        return cleaned || null;
    }

    function extractSellerNameFromText() {

        // Priorizar el panel principal del vendedor.
        const sellerPanel = document.querySelector(
            '.ui-pdp-seller-summary'
        );

        const panelText = sellerPanel?.innerText?.trim();

        // Utilizar el documento completo únicamente
        // cuando no exista un panel identificable.
        const text = panelText || document.body.innerText;

        const lines = text
            .split(/\r?\n/)
            .map(line => line.trim())
            .filter(Boolean);

        for (let i = 0; i < lines.length; i++) {

            const line = lines[i];

            const match = line.match(
                /^vendido\s+por\s*:?\s*(.*)$/i
            );

            if (!match) {
                continue;
            }

            const rawName = match[1] || lines[i + 1];

            const name = normalizeMercadoLibreSellerName(
                rawName
            );

            if (name) {
                return name;
            }
        }

        return null;
    }

    function findProduct(data) {
        if (!data || typeof data !== 'object') return null;

        if (Array.isArray(data)) {
            for (const item of data) {
                const found = findProduct(item);
                if (found) return found;
            }
            return null;
        }

        const types = Array.isArray(data['@type'])
            ? data['@type']
            : [data['@type']];

        if (types.some((type) =>
            typeof type === 'string' &&
            (type === 'Product' || type.endsWith('/Product'))
        )) {
            return data;
        }

        return findProduct(data['@graph']);
    }

    // Intentar recuperar los datos estructurados JSON-LD.
    let product = null;

    for (const script of document.querySelectorAll(
        'script[type="application/ld+json"]'
    )) {
        try {
            product = findProduct(JSON.parse(script.textContent));

            if (product) break;
        } catch {
            // Ignorar bloques que no contengan JSON válido.
        }
    }

    // URL sin fragmento de navegación.
    const pageUrl = catalogOffer?.url || window.location.href.split('#')[0];

    // Analizar solamente la ruta, sin parámetros.
    const pathname = window.location.pathname;

    // Identificar User Products.
    const userProductMatch = pathname.match(
        /\/up\/(MLAU\d{6,})(?=\/|$)/i
    );

    // Identificar publicaciones individuales.
    const itemMatch = pathname.match(
        /(?:^|\/)MLA-?(\d{6,})(?=\/|$)/i
    );

    // Determinar el identificador principal.
    const externalId = catalogOffer?.externalId || (userProductMatch
        ? userProductMatch[1].toUpperCase()
        : itemMatch
            ? `MLA${itemMatch[1]}`
            : null);

    // Evitar interpretar páginas de búsqueda como productos.
    const isProductPage =
        Boolean(document.querySelector('h1.ui-pdp-title')) ||
        Boolean(userProductMatch) ||
        Boolean(itemMatch);

    if (!isProductPage) {
        return {
            found: false,
            message: 'No parece ser una página individual de producto.'
        };
    }

    const title =
        getText('h1.ui-pdp-title') ||
        product?.name ||
        getMeta('meta[property="og:title"]');

    // Priorizar el precio principal mostrado en la página.
    const priceContainer =
        document.querySelector(
            '.ui-pdp-price__second-line .andes-money-amount'
        ) ||
        document.querySelector(
            '.ui-pdp-price .andes-money-amount'
        );

    let price = null;

    if (priceContainer) {
        const fraction = priceContainer.querySelector(
            '.andes-money-amount__fraction'
        )?.textContent?.trim();

        const cents = priceContainer.querySelector(
            '.andes-money-amount__cents'
        )?.textContent?.trim();

        if (fraction) {
            price = cents ? `${fraction},${cents}` : fraction;
        }
    }

    // Opiniones del producto: JSON-LD Product.aggregateRating. La cifra
    // reviewCount corresponde a opiniones, no a ventas ni al vendedor.
    // No inferir cantidades exactas desde "1.2K+" ni sustituir ratingCount.
    function parseMlReviewCount(raw) {
        if (raw == null) return null;
        const text = String(raw).trim();
        if (!/^(?:\d+|\d{1,3}(?:[.,]\d{3})+)$/.test(text)) return null;
        const count = Number(text.replace(/[.,]/g, ''));
        return Number.isSafeInteger(count) && count > 0 && count <= 10000000
            ? count : null;
    }
    function parseMlRating(raw) {
        if (raw == null) return null;
        const value = String(raw).trim().replace(',', '.');
        if (!/^[0-5](?:\.\d)?$/.test(value)) return null;
        const rating = Number(value);
        return rating > 0 && rating <= 5 ? rating : null;
    }
    const productRating = Array.isArray(product?.aggregateRating)
        ? product.aggregateRating[0] : product?.aggregateRating;
    let mlProductRating = parseMlRating(productRating?.ratingValue);
    let mlProductReviewCount = parseMlReviewCount(productRating?.reviewCount);
    if (mlProductRating === null || mlProductReviewCount === null) {
        // Alternativa conservadora si falta JSON-LD: exigir estrellas y
        // recuento de opiniones en un MISMO bloque de reseñas de producto,
        // nunca en el panel de reputación del vendedor.
        const reviewBlocks = document.querySelectorAll(
            '.ui-pdp-review__rating, .ui-pdp-reviews__rating, ' +
            '.ui-pdp-review__rating__summary, [data-testid="product-reviews"]'
        );
        for (const block of reviewBlocks) {
            const text = block.innerText || block.textContent || '';
            const countMatch = text.match(/(\d{1,3}(?:[.,]\d{3})+|\d+)\s+(?:opiniones|reseñas)\b/i);
            const averageText = block.querySelector?.(
                '.ui-pdp-review__rating__average, .ui-pdp-reviews__rating__average'
            )?.textContent?.trim() || text;
            const scoreMatch = averageText.match(/(?:^|[^\d])([1-5](?:[.,]\d)?)(?:\s*\/\s*5)?(?:\b|$)/);
            const rating = parseMlRating(scoreMatch?.[1]);
            const count = parseMlReviewCount(countMatch?.[1]);
            if (rating !== null && count !== null) {
                mlProductRating = rating;
                mlProductReviewCount = count;
                break;
            }
        }
    }
    if (mlProductRating === null || mlProductReviewCount === null) {
        // Estructura actual de Mercado Libre (reviews-capability-v3).
        // La media y el número de OPINIONES deben proceder de un mismo
        // módulo de reseñas del producto, no de la reputación del vendedor.
        // "2 calificaciones" no se interpreta automáticamente como
        // "2 opiniones": el texto accesible o los comentarios lo confirman.
        const reviewSections = document.querySelectorAll(
            '[data-testid="reviews-desktop"], [data-testid="reviews-mobile"], ' +
            '#reviews_capability_v3'
        );
        for (const section of reviewSections) {
            const average = parseMlRating(section.querySelector?.(
                '.ui-review-capability__rating__average'
            )?.textContent?.trim());
            if (average === null) continue;

            const accessible = section.querySelector?.(
                '.ui-review-capability__rating__rating .andes-visually-hidden'
            )?.textContent?.trim() || '';
            const accessibleMatch = accessible.match(
                /calificaci[oó]n\s+([1-5](?:[.,]\d)?)\s+de\s+5\s*[.,;:]?\s*(\d{1,3}(?:[.,]\d{3})+|\d+)\s+opiniones?\b/i
            );
            let count = accessibleMatch &&
                    parseMlRating(accessibleMatch[1]) === average
                ? parseMlReviewCount(accessibleMatch[2]) : null;

            if (count === null) {
                // Si falta el texto accesible, usar la cantidad explícita
                // de opiniones/comentarios del mismo módulo. Evitar contar
                // estrellas individuales y la etiqueta "calificaciones".
                const labels = section.querySelectorAll?.(
                    '.total-opinion, .ui-review-capability__rating__label'
                ) || [];
                for (const label of labels) {
                    const match = (label.textContent || '').trim().match(
                        /^(\d{1,3}(?:[.,]\d{3})+|\d+)\s+(?:opiniones|reseñas|comentarios)\b/i
                    );
                    if (match) {
                        count = parseMlReviewCount(match[1]);
                        if (count !== null) break;
                    }
                }
            }
            if (count !== null) {
                mlProductRating = average;
                mlProductReviewCount = count;
                break;
            }
        }
    }
    if (mlProductRating === null || mlProductReviewCount === null) {
        mlProductRating = null;
        mlProductReviewCount = null;
    }

    const offers = Array.isArray(product?.offers)
        ? product.offers[0]
        : product?.offers;

    price =
        catalogOffer?.price ||
        price ||
        getMeta('meta[property="product:price:amount"]') ||
        getMeta('meta[itemprop="price"]') ||
        (offers?.price != null ? String(offers.price) : null);

    const currency =
        catalogOffer?.currency ||
        getMeta('meta[property="product:price:currency"]') ||
        offers?.priceCurrency ||
        null;

    // Intentar obtener el vendedor desde la sección principal.
    const sellerSelectors = [
        // Estructura actual de Mercado Libre
        '.ui-pdp-seller-summary__header__title .ui-pdp-seller-summary__link',
        '.ui-pdp-seller-summary__link-trigger-button a',
        '.ui-pdp-seller-summary__header__title',

        // Estructuras alternativas
        '.ui-pdp-seller__header__title',
        '.ui-pdp-seller__link-trigger-button',
        '.ui-pdp-seller__header a',
        '.ui-pdp-seller-info a[href*="/perfil/"]',
        '.ui-pdp-seller a[href*="/perfil/"]',
        '.ui-pdp-seller a[href*="/_CustId_"]',
        '.ui-seller-data-header__title'
    ];

    let seller = null;

    for (const selector of sellerSelectors) {
        const value = getText(selector);

        if (value) {
            seller = value;
            break;
        }
    }

    // Alternativa: datos estructurados JSON-LD
    if (!seller && offers?.seller) {
        if (typeof offers.seller === 'string') {
            seller = offers.seller;
        } else if (typeof offers.seller.name === 'string') {
            seller = offers.seller.name;
        }
    }

    const sellerName = catalogOffer
        ? normalizeMercadoLibreSellerName(catalogOffer.seller)
        : normalizeMercadoLibreSellerName(seller) || extractSellerNameFromText();

    const description =
        getText('.ui-pdp-description__content') ||
        getText('.ui-pdp-description') ||
        (typeof product?.description === 'string'
            ? product.description
            : null);

    // -------------------------------------
    // Información pública del vendedor
    // -------------------------------------

    // En catálogos, leer el resumen comercial únicamente dentro de la
    // fila de la oferta elegida, nunca desde otra tarjeta de vendedor.
    const sellerPanel = catalogOffer
        ? catalogOffer.source?.querySelector?.('.ui-pdp-seller-summary') ?? null
        : document.querySelector('.ui-pdp-seller-summary');

    const sellerLink = catalogOffer
        ? sellerPanel?.querySelector?.(
            '.ui-pdp-seller-summary__header__title .ui-pdp-seller-summary__link'
          ) ?? null
        : document.querySelector(
            '.ui-pdp-seller-summary__header__title .ui-pdp-seller-summary__link'
          );

    // Enlace al perfil comercial del vendedor.
    let sellerProfileUrl = null;

    const sellerHref = sellerLink?.getAttribute('href');

    if (sellerHref) {
        try {
            const profileUrl = new URL(
                sellerHref,
                window.location.href
            );

            if (
                profileUrl.protocol === 'https:' &&
                [
                    'mercadolibre.com.ar',
                    'www.mercadolibre.com.ar'
                ].includes(profileUrl.hostname.toLowerCase()) &&
                profileUrl.pathname.startsWith('/pagina/')
            ) {
                sellerProfileUrl = profileUrl.href;
            }
        } catch {
            sellerProfileUrl = null;
        }
    }

    // -------------------------------------
    // Identificador del vendedor
    // -------------------------------------

    function extractSellerId(sellerName) {
        const candidates = new Map();

        const pattern =
            /["'](seller_id|sellerId|seller_user_id|sellerUserId)["']\s*:\s*["']?(\d{5,})/g;

        for (const script of document.querySelectorAll('script')) {
            const content = script.textContent || '';

            if (content.length > 5_000_000) {
                continue;
            }

            for (const match of content.matchAll(pattern)) {
                const id = match[2];

                const nearbyText = content.slice(
                    Math.max(0, match.index - 500),
                    match.index + 500
                );

                const matchesSeller = sellerName != null &&
                    nearbyText.toLowerCase()
                        .includes(sellerName.toLowerCase());

                const candidate = candidates.get(id) || {
                    occurrences: 0,
                    matchesSeller: false
                };

                candidate.occurrences++;
                candidate.matchesSeller ||= matchesSeller;

                candidates.set(id, candidate);
            }
        }

        // No asignar un ID cuando hay varios candidatos.
        if (candidates.size !== 1) {
            return null;
        }

        const [id, evidence] = [...candidates.entries()][0];

        // Exigir una asociación textual con el vendedor.
        if (!evidence.matchesSeller) {
            return null;
        }

        // Conservar el identificador como String para evitar
        // pérdida de precisión con posibles IDs de 64 bits.
        return id;
    }

    const sellerId = extractSellerId(sellerName);

    // Mercado Libre también usa un panel de vendedor independiente
    // de .ui-pdp-seller-summary. Asociarlo al MISMO vendedor antes
    // de tomar indicadores comerciales (evita leer otro vendedor).
    // La reputación está debajo del formulario, como bloque hermano
    // .ui-seller-data dentro del sidebar. En catálogos exigir además un
    // enlace del propio panel con item_id de la oferta elegida: el nombre
    // del vendedor por sí solo no basta para vincular una reputación.
    const sellerScope = catalogOffer?.source?.closest?.(
        '.ui-pdp--sticky-wrapper-right'
    );
    const sellerDetailsCandidates = Array.from(
        sellerScope?.querySelectorAll?.('.ui-seller-data')
        ?? document.querySelectorAll('.ui-seller-data')
    );
    const sellerDetailsPanel = sellerDetailsCandidates.find(panel => {
        const heading = panel.querySelector(
            '.ui-seller-data-header__title'
        )?.textContent?.trim();
        const sameSeller = Boolean(
            sellerName && heading &&
            normalizeMercadoLibreSellerName(heading)?.toLowerCase() ===
                sellerName.toLowerCase()
        );
        if (!sameSeller) return false;
        if (!catalogOffer) return true;

        const references = panel.querySelectorAll('a[href*="item_id="]');
        return Array.from(references).some(link => {
            try {
                const href = link.getAttribute('href');
                const address = new URL(href, window.location.href);
                return address.searchParams.get('item_id')?.toUpperCase()
                    === catalogOffer.externalId;
            } catch {
                return false;
            }
        });
    }) ?? null;

    // Solo se usan los paneles atribuidos a este vendedor;
    // nunca el texto completo de la página para ventas o catálogo.
    const sellerPanelText = [
        sellerPanel?.innerText,
        sellerDetailsPanel?.innerText
    ].filter(Boolean).join('\n').trim();

    // Distinción comercial.
    const sellerBadge =
        /mercado\s*l[ií]der/i.test(sellerPanelText)
            ? 'MERCADOLIDER'
            : null;

    // Ventas visibles: aceptamos formatos como "4 ventas", "4 ventas totales"
    // y preservamos expresiones aproximadas ("+50 ventas", "más de 50 ventas")
    // para que el backend NO las interprete como cantidades exactas.
    const salesSections = [
        sellerDetailsPanel,
        sellerPanel,
        document.querySelector('.ui-pdp-seller-summary__reputation'),
        document.querySelector('.ui-pdp-seller__reputation-info'),
        document.querySelector('.ui-pdp-seller__reputation')
    ].filter(Boolean);

    let sellerSalesText = null;
    for (const section of salesSections) {
        const visibleText = section.innerText || '';
        const salesMatch = visibleText.match(
            /(?:(?:más de|mas de|al menos|aproximadamente|aprox\.?)\s+|\+\s*)?(?:\d{1,3}(?:[.,]\d{3})+|\d+)\s+ventas?\b(?:\s+(?:totales|realizadas|completadas|concretadas))?(?:\s+(?:en|durante)\s+(?:los?\s+)?(?:últimos?|últimas?)\s+\d+\s+(?:días?|meses?|años?))?/i
        );
        if (salesMatch) {
            sellerSalesText = salesMatch[0].replace(/\s+/g, ' ').trim();
            break;
        }
    }

    // Señales de actividad comercial del panel del vendedor.
    // No confundir "3 productos" con "3 ventas".
    // Registrar datos SOLO de la sección identificada del vendedor,
    // nunca de todo el documento o de sugerencias de productos.
    let sellerListedProductsText = null;
    // Estructura observada: <p class="ui-seller-data-header__products">
    // con el número y "Productos" en spans independientes.
    const structuredProductsText = sellerDetailsPanel?.querySelector(
        '.ui-seller-data-header__products'
    )?.textContent?.replace(/\s+/g, ' ').trim();

    const commercialProductsText = structuredProductsText || sellerPanelText;
    if ((sellerDetailsPanel || sellerPanel) && commercialProductsText) {
        const productsMatch = commercialProductsText.match(
            /(?:^|[^\d])(\d{1,3}(?:[.,]\d{3})+|\d+)\s+productos?\b/i
        );
        if (productsMatch) {
            const numberStart = commercialProductsText.indexOf(
                productsMatch[1], productsMatch.index
            );
            const precedingText = commercialProductsText.slice(0, numberStart).trim();
            // Excluir cantidades aproximadas: "más de 3 productos", "+3 productos".
            const approximate = /(?:m[aá]s de|al menos|aproximadamente|aprox\.?)\s*$/i
                .test(precedingText) || /\+\s*$/.test(precedingText);
            if (!approximate) {
                sellerListedProductsText = productsMatch[1] + ' productos';
            }
        }
    }

    // Solo registrar el mensaje cuando aparece explícitamente en el panel.
    // Que no aparezca una cifra NO demuestra que haya cero ventas.
    let sellerInsufficientSalesText = null;
    // Estructura observada: <p class="ui-seller-data-status__default-info">
    const structuredInsufficientText = sellerDetailsPanel?.querySelector(
        '.ui-seller-data-status__default-info'
    )?.textContent?.replace(/\s+/g, ' ').trim();
    const commercialInsufficientText = structuredInsufficientText || sellerPanelText;
    if ((sellerDetailsPanel || sellerPanel) && commercialInsufficientText) {
        const insufficientMatch = commercialInsufficientText.match(
            /(?:no\s+(?:tiene|cuenta\s+con))\s+(?:las?\s+)?(?:suficientes?\s+ventas?|ventas?\s+suficientes?)\b[^\n.]{0,110}/i
        );
        if (insufficientMatch) {
            sellerInsufficientSalesText = insufficientMatch[0].trim();
        }
    }

    // -------------------------------------
    // Reputación pública del vendedor
    // -------------------------------------

    // Nunca combinar termómetros de vendedores diferentes. En una
    // selección de catálogo solo leer el panel correlacionado por
    // nombre e item_id. En ofertas normales se conserva el fallback.
    const reputationThermometers = Array.from(
        catalogOffer
            ? sellerDetailsPanel?.querySelectorAll(
                'ul.ui-seller-data-status__thermometer[value]'
              ) ?? []
            : sellerDetailsPanel?.querySelectorAll(
                'ul.ui-seller-data-status__thermometer[value]'
              ) ?? document.querySelectorAll(
                'ul.ui-seller-data-status__thermometer[value]'
              )
    );

    let sellerReputationLevel = null;

    // Si aparecen múltiples termómetros, no elegimos uno
    // arbitrariamente.
    if (reputationThermometers.length === 1) {
        const rawLevel =
            reputationThermometers[0].getAttribute('value');

        if (/^[1-5]$/.test(rawLevel ?? '')) {
            sellerReputationLevel = Number(rawLevel);
        }
    }

    return {
        found: Boolean(title || price),
        title: title || null,
        price,
        currency,
        seller: sellerName,
        externalId,
        description,
        url: pageUrl,
        // Usar señales comerciales en el catálogo únicamente si el panel
        // del vendedor está corroborado para la oferta seleccionada.
        sellerProfileUrl: catalogOffer && !sellerDetailsPanel ? null : sellerProfileUrl,
        sellerBadge: catalogOffer && !sellerDetailsPanel ? null : sellerBadge,
        sellerSalesText: catalogOffer && !sellerDetailsPanel ? null : sellerSalesText,
        sellerListedProductsText: catalogOffer && !sellerDetailsPanel
            ? null : sellerListedProductsText,
        sellerInsufficientSalesText: catalogOffer && !sellerDetailsPanel
            ? null : sellerInsufficientSalesText,
        sellerReputationLevel: catalogOffer && !sellerDetailsPanel
            ? null : sellerReputationLevel,
        sellerId: catalogOffer ? null : sellerId,
        selectedOfferId: catalogOffer?.selectedOfferId ?? null,
        mlProductRating,
        mlProductReviewCount
    };
}

function extractFacebookMarketplace() {

    const url = new URL(window.location.href);

    const match = url.pathname.match(
        /^\/marketplace\/item\/(\d{6,30})\/?$/
    );

    if (!match) {
        return {
            found: false,
            message: 'No es una publicación individual de Facebook Marketplace.'
        };
    }

    // Solamente utilizar texto de elementos visibles.
    function visibleText(element) {

        if (!element || element.getClientRects().length === 0) {
            return null;
        }

        return element.innerText?.trim() || null;
    }

    // Título principal.
    const heading = document.querySelector('h1');
    const title = visibleText(heading);

    // Panel que contiene título, precio, detalles y vendedor.
    const informationContainer =
        heading?.parentElement
            ?.parentElement
            ?.parentElement
            ?.parentElement;

    const informationText =
        visibleText(informationContainer) || '';

    const informationLines = informationText
        .split('\n')
        .map(line => line.trim())
        .filter(Boolean);

    // Buscar el precio cerca del título, sin recorrer
    // indiscriminadamente toda la página.
    let price = null;

    const detailContainer =
        heading?.parentElement?.parentElement?.parentElement;

    if (detailContainer) {

        const candidates = [
            ...detailContainer.querySelectorAll('span')
        ];

        for (const candidate of candidates) {

            const text = visibleText(candidate);

            if (text && /^\$\s*[\d.,]+$/.test(text)) {
                price = text.replace('$', '').trim();
                break;
            }
        }
    }

    // -------------------------------------
    // Vendedor de Facebook Marketplace
    // -------------------------------------

    // -------------------------------------
    // Nombre del vendedor
    // -------------------------------------

    let seller = null;

    const sellerDetailsIndex = informationLines.findIndex(
        line => line.toLowerCase() === 'detalles del vendedor'
    );

    if (sellerDetailsIndex !== -1) {

        const candidate =
            informationLines[sellerDetailsIndex + 1];

        const followingLine =
            informationLines[sellerDetailsIndex + 2];

        // Comprobar que después del nombre aparezca
        // el indicador de incorporación a Facebook.
        const hasJoinDate = followingLine &&
            /^se unió a facebook en \d{4}$/i.test(followingLine);

        if (
            candidate &&
            candidate.length <= 100 &&
            hasJoinDate
        ) {
            seller = candidate;
        }
    }



    // -------------------------------------
    // Descripción / especificaciones
    // -------------------------------------

    let description = null;

    const specificationsIndex = informationLines.findIndex(
        line => line.toLowerCase() === 'especificaciones'
    );

    if (specificationsIndex !== -1) {

        const descriptionLines = [];

        for (
            let i = specificationsIndex;
            i < informationLines.length;
            i++
        ) {
            const line = informationLines[i];
            const normalized = line.toLowerCase();

            if (
                normalized === 'información del vendedor' ||
                normalized === 'detalles del vendedor' ||
                normalized.includes('la ubicación es aproximada')
            ) {
                break;
            }

            descriptionLines.push(line);
        }

        if (descriptionLines.length > 0) {
            description = descriptionLines
                .join('\n')
                .trim()
                .slice(0, 5000);
        }
    }

    // -------------------------------------
    // Año de incorporación a Facebook
    // -------------------------------------

    let facebookJoinYear = null;

    const joinMatch = informationText.match(
        /se\s+unió\s+a\s+facebook\s+en\s+(\d{4})/i
    );

    if (joinMatch) {
        const year = Number(joinMatch[1]);
        const currentYear = new Date().getFullYear();

        if (
            Number.isInteger(year) &&
            year >= 2004 &&
            year <= currentYear
        ) {
            facebookJoinYear = year;
        }
    }

    return {
        found: true,
        marketplace: 'FACEBOOK_MARKETPLACE',
        title,
        price,
        currency: 'ARS',
        seller,
        externalId: match[1],
        description,
        facebookJoinYear,

        url: `${url.origin}/marketplace/item/${match[1]}/`,

        sellerProfileUrl: null,
        sellerBadge: null,
        sellerSalesText: null,
        sellerReputationLevel: null,
        sellerId: null
    };
}


async function extractCurrentListing() {
    if (!activeTab?.id || !pageSupported) throw new Error('Abrí una publicación compatible.');
    const extractor = currentMarketplace === 'MERCADO_LIBRE'
        ? extractMercadoLibre : currentMarketplace === 'TEMU'
            ? extractTemu : extractFacebookMarketplace;
    const results = await ext.scripting.executeScript({
        target: {tabId: activeTab.id}, func: extractor
    });
    const execution = results?.[0];
    if (execution?.error) throw new Error(String(execution.error.message || execution.error));
    if (!execution?.result?.found) throw new Error(execution?.result?.message || 'No se encontraron datos del anuncio.');
    const data = execution.result;
    return {...data, marketplace: currentMarketplace};
}
function fillFacebookForm(data) {
    $('fb-title').value = data.title ?? '';
    $('fb-price').value = data.price ?? '';
    $('fb-seller').value = data.seller ?? '';
    $('fb-join-year').value = data.facebookJoinYear ?? '';
    $('fb-description').value = data.description ?? '';
}
function readFacebookForm(data) {
    const chosenSeller = $('fb-seller').value.trim();
    const sameObservedSeller = chosenSeller.toLocaleLowerCase() ===
        String(data.seller ?? '').trim().toLocaleLowerCase();
    const year = $('fb-join-year').value.trim();
    const joinYear = year ? Number(year) : null;
    if (joinYear !== null && (!Number.isInteger(joinYear) || joinYear < 2004 || joinYear > new Date().getFullYear())) {
        throw new Error('El año de incorporación a Facebook no es válido.');
    }
    return {...data,
        title: $('fb-title').value.trim(),
        price: $('fb-price').value.trim(),
        seller: chosenSeller || null,
        sellerBadge: data.marketplace === 'TEMU' && !sameObservedSeller
            ? null : data.sellerBadge,
        temuSellerRating: data.marketplace === 'TEMU' && !sameObservedSeller
            ? null : data.temuSellerRating,
        temuSellerReviewCount: data.marketplace === 'TEMU' && !sameObservedSeller
            ? null : data.temuSellerReviewCount,
        // Las opiniones del producto son independientes de la identidad de la tienda.
        temuProductRating: data.temuProductRating ?? null,
        temuProductReviewCount: data.temuProductReviewCount ?? null,
        temuProductSoldText: data.temuProductSoldText ?? null,
        temuStoreSoldText: data.marketplace === 'TEMU' && !sameObservedSeller
            ? null : data.temuStoreSoldText,
        temuStoreTenureText: data.marketplace === 'TEMU' && !sameObservedSeller
            ? null : data.temuStoreTenureText,
        temuStoreId: data.marketplace === 'TEMU' && !sameObservedSeller
            ? null : data.temuStoreId,
        temuSellerSectionObserved: data.marketplace === 'TEMU' && !sameObservedSeller
            ? false : data.temuSellerSectionObserved,
        sellerProfileUrl: data.marketplace === 'TEMU' && !sameObservedSeller
            ? null : data.sellerProfileUrl,
        facebookJoinYear: joinYear,
        description: $('fb-description').value.trim() || null
    };
}
function validTemuPrice(value) {
    const text = typeof value === 'string' ? value.trim()
        : typeof value === 'number' && Number.isFinite(value) ? String(value) : '';
    // Admitir importes como 9.507 y decimales de dos dígitos, sin monedas ni símbolos.
    return /^(?:\d{1,3}(?:[.,]\d{3})+(?:[.,]\d{1,2})?|\d+(?:[.,]\d{1,2})?)$/.test(text)
        && /[1-9]/.test(text);
}
function temuPriceCandidates(data) {
    return Array.isArray(data.priceCandidates)
        ? [...new Set(data.priceCandidates.filter(validTemuPrice).map(String))].slice(0, 8)
        : [];
}
function requiresTemuPriceConfirmation(data) {
    if (data?.marketplace !== 'TEMU') return false;
    const candidates = temuPriceCandidates(data);
    return !validTemuPrice(data.price) || candidates.length > 1
        || (candidates.length === 1 && candidates[0] !== String(data.price).trim());
}
function updateTemuPriceSelection() {
    const chosen = $('temu-confirm-price').value.trim();
    for (const button of $('temu-price-options').querySelectorAll('button')) {
        button.setAttribute('aria-pressed', String(button.dataset.price === chosen));
    }
}
function showTemuPrices(data) {
    const prices = temuPriceCandidates(data);
    const options = $('temu-price-options');
    options.replaceChildren();
    const initial = validTemuPrice(data.price) ? String(data.price).trim()
        : prices.length === 1 ? prices[0] : '';
    $('temu-confirm-price').value = initial;
    $('temu-price-status').textContent = '';

    for (const value of prices) {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'temu-price-option';
        button.dataset.price = value;
        button.setAttribute('aria-label', 'Seleccionar ARS ' + value + ' como precio del artículo');
        button.textContent = 'ARS ' + value;
        button.addEventListener('click', () => {
            $('temu-confirm-price').value = value;
            $('temu-price-status').textContent = '';
            updateTemuPriceSelection();
        });
        options.appendChild(button);
    }
    options.hidden = options.children.length === 0;
    $('temu-price-hint').textContent = prices.length > 1
        ? 'Comprobá cuál es el precio real de tu variante. No uses un importe tachado ni descuentos que no te correspondan.'
        : prices.length === 1
            ? 'Verificá que el precio mostrado corresponda a tu variante y a las promociones aplicables.'
            : 'No se pudo identificar un precio confiable. Escribí el importe en ARS tal como aparece en tu publicación.';
    updateTemuPriceSelection();
}
function showCorrectionForm(data) {
    fillFacebookForm(data);
    const sellerHint = $('temu-seller-hint');
    sellerHint.hidden = data.marketplace !== 'TEMU';
    if (data.marketplace === 'TEMU') {
        const rating = data.temuSellerRating;
        const ratingText = typeof rating === 'number' && rating > 0
            ? 'Calificación de tienda: ' + rating.toLocaleString('es-AR') + ' / 5. '
            : '';
        const count = data.temuSellerReviewCount;
        const reviewText = Number.isInteger(count) && count >= 0
            ? 'Reseñas de tienda: ' + count.toLocaleString('es-AR') + '. ' : '';
        sellerHint.textContent = ratingText + reviewText +
            (data.sellerBadge === 'TEMU_STAR_SELLER'
                ? 'Star Seller aparece junto a la tienda. Ninguna señal implica seguridad garantizada.'
                : 'Podés completar o corregir el nombre de la tienda si aparece en la publicación.');
    }
    $('fb-join-row').hidden = data.marketplace !== 'FACEBOOK_MARKETPLACE';
    $('fb-seller').disabled = false;
    $('correction-status').textContent = '';
    setPanelState('correction');
}
function missingEssential(data) {
    return !data?.externalId || !data.title || !data.price ||
        (data.marketplace !== 'TEMU' && !data.seller);
}
async function prepareData(action) {
    const keyAtStart = pageKey;
    if (!extractedData || currentMarketplace === 'MERCADO_LIBRE' || currentMarketplace === 'TEMU') {
        extractedData = await extractCurrentListing();
    }
    if (keyAtStart !== pageKey) throw new Error('La publicación cambió durante la extracción.');
    if (currentMarketplace === 'MERCADO_LIBRE'
            && isMercadoLibreCatalogUrl(activeTab?.url)
            && extractedData?.externalId) {
        // Dos ofertas del mismo catálogo no deben compartir estado de análisis.
        pageKey = `${activeTab.id}:${activeTab.url.split('#')[0]}:offer:${extractedData.externalId}`;
    }
    if (currentMarketplace === 'TEMU' && extractedData.currency !== 'ARS') {
        throw new Error('No se encontró información suficiente para confirmar que el precio de Temu está en ARS.');
    }
    if (!extractedData.externalId) {
        throw new Error('No se encontró el ID individual de la publicación. Revisá el anuncio.');
    }

    // En Temu se confirma el precio antes de pedir otras correcciones.
    // Si hay alternativas, se exige confirmación incluso con precio extraído.
    if (requiresTemuPriceConfirmation(extractedData)) {
        pendingAction = action;
        showTemuPrices(extractedData);
        setPanelState('price');
        updateActions();
        return null;
    }
    if (missingEssential(extractedData)) {
        pendingAction = action;
        showCorrectionForm(extractedData);
        updateActions();
        return null;
    }
    return extractedData;
}
async function finishPreparedAction(action, data) {
    if (action === 'report') {
        await restoreAnalysis();
        await sendReport(data);
    } else {
        setPanelState('loading');
        await startAnalysis(data);
    }
}
async function confirmTemuPrice() {
    if (!pendingAction || !extractedData || preparing || currentMarketplace !== 'TEMU'
        || $('temu-price-panel').hidden) return;
    const chosen = $('temu-confirm-price').value.trim();
    if (!validTemuPrice(chosen)) {
        $('temu-price-status').textContent = 'Seleccioná o ingresá un precio válido en ARS antes de continuar.';
        return;
    }
    preparing = true;
    $('temu-price-confirm').disabled = true;
    $('temu-price-cancel').disabled = true;
    updateActions();
    const action = pendingAction;
    try {
        extractedData = {...extractedData, price: chosen};
        $('temu-price-status').textContent = '';
        if (missingEssential(extractedData)) {
            showCorrectionForm(extractedData);
        } else {
            pendingAction = null;
            await finishPreparedAction(action, extractedData);
        }
    } catch (error) {
        const message = String(error.message || error);
        if (pendingAction) {
            $('temu-price-status').textContent = message;
            setPanelState('price');
        } else if (action === 'report') {
            await restoreAnalysis();
            $('report-status').textContent = 'No se pudo continuar: ' + message;
        } else {
            renderAnalysis({status: 'error', message});
        }
    } finally {
        preparing = false;
        $('temu-price-confirm').disabled = false;
        $('temu-price-cancel').disabled = false;
        updateActions();
    }
}
async function cancelTemuPrice() {
    if (preparing) return;
    pendingAction = null;
    $('temu-price-status').textContent = '';
    setPanelState('initial');
    await restoreAnalysis();
}
async function startAnalysis(data) {
    if (!pageKey) return;
    const key = pageKey;
    renderAnalysis({status: 'loading', message: 'Comenzando…'});
    const reply = await ext.runtime.sendMessage({
        type: 'ANALYZE_PUBLICATION', pageKey: key, data
    });
    if (!reply?.ok) throw new Error(reply?.error || 'No se pudo iniciar la evaluación.');
    await restoreAnalysis(key);
}
async function analyzeClick() {
    if (preparing || !authenticated || !pageSupported) return;
    preparing = true;
    updateActions();
    try {
        const data = await prepareData('analyze');
        if (data) await startAnalysis(data);
    } catch (error) {
        renderAnalysis({status: 'error', message: String(error.message || error)});
    } finally { preparing = false; updateActions(); }
}
async function sendReport(data) {
    if (submittingReport) return;
    submittingReport = true;
    $('report-submit').textContent = 'Enviando reporte…';
    $('report-status').textContent = 'Identificando publicación y registrando reporte…';
    updateActions();
    try {
        const reply = await ext.runtime.sendMessage({
            type: 'SUBMIT_REPORT',
            data,
            reason: $('report-reason').value,
            description: $('report-description').value.trim()
        });
        if (!reply?.ok) throw new Error(reply?.error || 'No se pudo registrar el reporte.');
        lastPublicationId = reply.publicationId;
        $('report-status').textContent = `Reporte registrado correctamente para la publicación #${reply.publicationId}.`;
        $('report-description').value = '';
        $('report-reason').value = '';
    } catch (error) {
        $('report-status').textContent = `No se pudo enviar: ${error.message || error}`;
    } finally {
        submittingReport = false;
        $('report-submit').textContent = 'Enviar reporte';
        updateActions();
    }
}
async function reportSubmit(event) {
    event.preventDefault();
    if (!authenticated || !pageSupported || submittingReport || preparing) return;
    preparing = true;
    updateActions();
    try {
        const data = await prepareData('report');
        if (data) await sendReport(data);
    } catch (error) {
        $('report-status').textContent = `No se pudo continuar: ${error.message || error}`;
    } finally {preparing = false; updateActions();}
}
async function continueFacebook() {
    if (!pendingAction || !extractedData || preparing) return;
    let action = pendingAction;
    preparing = true;
    $('facebook-continue-button').disabled = true;
    $('facebook-cancel-button').disabled = true;
    updateActions();
    try {
        const data = readFacebookForm(extractedData);
        if (missingEssential(data)) throw new Error(data.marketplace === 'TEMU'
            ? 'Completá título y precio en ARS.' : 'Completá título, precio y nombre del vendedor.');
        extractedData = data;
        pendingAction = null;
        $('correction-status').textContent = '';
        await finishPreparedAction(action, data);
    } catch (error) {
        const message = String(error.message || error);
        if (pendingAction) {
            // Error de validación: permanecer en el formulario.
            $('correction-status').textContent = message;
        } else if (action === 'report') {
            await restoreAnalysis();
            $('report-status').textContent = `No se pudo continuar: ${message}`;
        } else {
            renderAnalysis({status: 'error', message});
        }
    } finally {
        preparing = false;
        $('facebook-continue-button').disabled = false;
        $('facebook-cancel-button').disabled = false;
        updateActions();
    }
}
async function cancelFacebookCorrection() {
    if (preparing) return;
    pendingAction = null;
    $('correction-status').textContent = '';
    setPanelState('initial');
    await restoreAnalysis();
}

$('login-form').addEventListener('submit', (event) => void handleLogin(event));
$('logout-button').addEventListener('click', () => void handleLogout());
$('theme-toggle').addEventListener('click', () => void toggleTheme());
$('analyze-button').addEventListener('click', () => void analyzeClick());
$('reanalyze-button').addEventListener('click', () => void analyzeClick());
$('retry-button').addEventListener('click', () => void analyzeClick());
$('report-toggle').addEventListener('click', () => toggleReport());
$('report-form').addEventListener('submit', (event) => void reportSubmit(event));
$('facebook-continue-button').addEventListener('click', () => void continueFacebook());
$('facebook-cancel-button').addEventListener('click', () => void cancelFacebookCorrection());
$('temu-price-confirm').addEventListener('click', () => void confirmTemuPrice());
$('temu-price-cancel').addEventListener('click', () => void cancelTemuPrice());
$('temu-confirm-price').addEventListener('input', () => {
    $('temu-price-status').textContent = '';
    updateTemuPriceSelection();
});
// Un vínculo temporal permite abrir el frontend sin enviar el JWT en la URL.
async function openPublicationWithSession() {
    const id = lastPublicationId;
    if (!Number.isSafeInteger(id) || id <= 0) return;

    const button = $('open-publication-button');
    button.disabled = true;

    try {
        const session = await getSession();
        if (!session) throw new Error('Iniciá sesión en la extensión nuevamente.');

        const response = await fetch(`${API_BASE}/auth/extension-link`, {
            method: 'POST',
            headers: { Authorization: `Bearer ${session.token}` },
            credentials: 'omit',
            cache: 'no-store'
        });

        const data = await response.json().catch(() => null);
        if (!response.ok) throw new Error(data?.detail || `HTTP ${response.status}`);
        if (typeof data?.code !== 'string' || !/^[A-Za-z0-9_-]{43}$/.test(data.code)) {
            throw new Error('El backend no devolvió un código temporal válido.');
        }

        // El fragmento (#) no viaja al servidor como parte de la URL HTTP.
        const fragment = new URLSearchParams({
            code: data.code,
            publication: String(id)
        });
        await ext.tabs.create({
            url: `http://localhost:5173/auth/extension#${fragment.toString()}`
        });
    } catch (error) {
        // Si falla el acceso delegado, mantenemos la navegación normal:
        // el frontend mostrará login y conservará la publicación de destino.
        $('status').textContent = `Acceso automático no disponible: ${error.message || error}`;
        await ext.tabs.create({url: `http://localhost:5173/publications/${id}`});
    } finally {
        button.disabled = false;
    }
}

$('open-publication-button').addEventListener('click', () => {
    void openPublicationWithSession();
});

ext.storage.onChanged.addListener((changes, area) => {
    if (area !== 'local') return;
    if (changes[THEME_STORAGE_KEY]) applyTheme(changes[THEME_STORAGE_KEY].newValue);
    const taskKey = pageKey && pageStateKey();
    if (taskKey && changes[taskKey]) renderAnalysis(changes[taskKey].newValue);
    if (changes[AUTH_KEY] && !changes[AUTH_KEY].newValue && !logoutInProgress)
        showLoggedOut('La sesión expiró o se cerró.');
});
ext.tabs.onActivated.addListener(() => void loadCurrentPage());
ext.tabs.onUpdated.addListener((_id, changes, tab) => {
    if (tab.active && (changes.url || changes.status === 'loading')) void loadCurrentPage();
});

void (async () => {
    await restoreTheme();
    await checkAuthentication();
    await loadCurrentPage();
})().catch(error => showLoggedOut(`No se pudo iniciar la extensión: ${error.message}`));
