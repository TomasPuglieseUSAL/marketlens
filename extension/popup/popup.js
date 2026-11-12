const ext = globalThis.browser ?? globalThis.chrome;
const API_BASE = 'http://localhost:8080/api';
const AUTH_KEY = 'marketlensAuth';
const $ = (id) => document.getElementById(id);

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

const marketplaceLabels = {
    MERCADO_LIBRE: 'Mercado Libre',
    FACEBOOK_MARKETPLACE: 'Facebook Marketplace',
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
        return 'OTHER';
    } catch { return 'OTHER'; }
}
function isSupportedPage(url, marketplace) {
    try {
        const path = new URL(url).pathname;
        if (marketplace === 'FACEBOOK_MARKETPLACE')
            return /^\/marketplace\/item\/\d{6,30}\/?$/.test(path);
        if (marketplace === 'MERCADO_LIBRE')
            return /\/up\/MLAU\d{6,}/i.test(path) ||
                /(?:^|\/)MLA-?\d{6,}/i.test(path) ||
                /\/p\/MLA\d{6,}/i.test(path);
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
    authenticated = false;
    $('auth-status').textContent = message;
    $('login-form').hidden = false;
    $('logout-button').hidden = true;
    $('authenticated-content').hidden = true;
}
function showLoggedIn(username) {
    authenticated = true;
    $('auth-status').textContent = username;
    $('login-form').hidden = true;
    $('logout-button').hidden = false;
    $('authenticated-content').hidden = false;
    updateActions();
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
    await ext.storage.local.remove(AUTH_KEY);
    await ext.storage.session.remove(AUTH_KEY).catch(() => {});
    $('login-password').value = '';
    showLoggedOut('Sesión cerrada.');
}
function updateActions() {
    $('analyze-button').disabled = !authenticated || !pageSupported || preparing;
    $('report-submit').disabled = !authenticated || !pageSupported || submittingReport || preparing;
}
function setPanelState(state) {
    $('analyze-button').hidden = state !== 'initial';
    $('loading-section').hidden = state !== 'loading';
    $('result-section').hidden = state !== 'success';
    $('error-section').hidden = state !== 'error';
}
function renderAnalysis(value) {
    if (!authenticated) return;
    const status = value?.status || 'initial';
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
    } else if (pageSupported) {
        $('status').textContent = 'Publicación compatible detectada.';
    }
    updateActions();
}
function renderRisk(value) {
    const evaluation = value.evaluation || {};
    lastPublicationId = value.publicationId || null;
    const level = String(evaluation.level || 'INDETERMINATE').toUpperCase();
    $('result-section').dataset.level = level;
    const labels = {
        LOW: ['Riesgo bajo', 'No se detectaron señales importantes dentro de la información disponible.', '✓'],
        MEDIUM: ['Riesgo medio', 'Se detectaron factores que conviene revisar.', '!'],
        HIGH: ['Riesgo alto', 'Se detectaron múltiples señales de riesgo.', '!'],
        INDETERMINATE: ['Riesgo indeterminado', 'Información insuficiente para clasificar el riesgo.', '?']
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
    $('result-coverage-note').textContent =
        level === 'INDETERMINATE'
            ? 'Un puntaje bajo con datos insuficientes no significa que la oferta sea segura.'
            : coverage === 'PARTIAL'
                ? 'La cobertura de información es parcial; interpretá el puntaje con precaución.'
                : 'Una clasificación de riesgo no garantiza que una operación sea segura o fraudulenta.';
    const factors = Array.isArray(evaluation.factors) ? evaluation.factors : [];
    $('result-factors').replaceChildren();
    for (const factor of factors.slice(0, 8)) {
        const label = factor?.description ?? factor?.reason ?? factor?.name;
        if (typeof label !== 'string' || !label.trim()) continue;
        const li = document.createElement('li');
        li.textContent = label;
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
            extractedData = null;
            pendingAction = null;
            lastPublicationId = null;
            $('facebook-correction').hidden = true;
            $('report-status').textContent = '';
            renderAnalysis({status:'initial'});
        }
        if (!pageSupported) {
            setPanelState('initial');
            $('status').textContent = currentMarketplace === 'OTHER'
                ? 'Abrí una publicación de Mercado Libre o Facebook Marketplace.'
                : 'Abrí una publicación individual antes de analizar.';
        } else await restoreAnalysis(nextKey);
        updateActions();
    } catch (error) {
        pageSupported = false;
        displayError(error);
        updateActions();
    }
}

function extractMercadoLibre() {
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
    const pageUrl = window.location.href.split('#')[0];

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
    const externalId = userProductMatch
        ? userProductMatch[1].toUpperCase()
        : itemMatch
            ? `MLA${itemMatch[1]}`
            : null;

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

    const offers = Array.isArray(product?.offers)
        ? product.offers[0]
        : product?.offers;

    price =
        price ||
        getMeta('meta[property="product:price:amount"]') ||
        getMeta('meta[itemprop="price"]') ||
        (offers?.price != null ? String(offers.price) : null);

    const currency =
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

    const sellerName =
        normalizeMercadoLibreSellerName(seller)
        || extractSellerNameFromText();

    const description =
        getText('.ui-pdp-description__content') ||
        getText('.ui-pdp-description') ||
        (typeof product?.description === 'string'
            ? product.description
            : null);

    // -------------------------------------
    // Información pública del vendedor
    // -------------------------------------

    const sellerPanel = document.querySelector(
        '.ui-pdp-seller-summary'
    );

    const sellerLink = document.querySelector(
        '.ui-pdp-seller-summary__header__title ' +
        '.ui-pdp-seller-summary__link'
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
    const sellerDetailsPanel = Array.from(
        document.querySelectorAll('.ui-seller-data')
    ).find(panel => {
        const heading = panel.querySelector(
            '.ui-seller-data-header__title'
        )?.textContent?.trim();
        return Boolean(
            sellerName && heading &&
            normalizeMercadoLibreSellerName(heading)?.toLowerCase() ===
                sellerName.toLowerCase()
        );
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

    const reputationThermometers = [
        ...document.querySelectorAll(
            'ul.ui-seller-data-status__thermometer[value]'
        )
    ];

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
        sellerProfileUrl,
        sellerBadge,
        sellerSalesText,
        sellerListedProductsText,
        sellerInsufficientSalesText,
        sellerReputationLevel,
        sellerId
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
        ? extractMercadoLibre : extractFacebookMarketplace;
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
    const year = $('fb-join-year').value.trim();
    const joinYear = year ? Number(year) : null;
    if (joinYear !== null && (!Number.isInteger(joinYear) || joinYear < 2004 || joinYear > new Date().getFullYear())) {
        throw new Error('El año de incorporación a Facebook no es válido.');
    }
    return {...data,
        title: $('fb-title').value.trim(),
        price: $('fb-price').value.trim(),
        seller: $('fb-seller').value.trim(),
        facebookJoinYear: joinYear,
        description: $('fb-description').value.trim() || null
    };
}
function missingEssential(data) {
    return !data?.externalId || !data.title || !data.price || !data.seller;
}
async function prepareData(action) {
    const keyAtStart = pageKey;
    if (!extractedData || currentMarketplace === 'MERCADO_LIBRE') {
        extractedData = await extractCurrentListing();
    }
    if (keyAtStart !== pageKey) throw new Error('La publicación cambió durante la extracción.');
    if (missingEssential(extractedData)) {
        if (extractedData.externalId) {
            pendingAction = action;
            fillFacebookForm(extractedData);
            $('fb-join-row').hidden = currentMarketplace !== 'FACEBOOK_MARKETPLACE';
            $('facebook-correction').hidden = false;
            $('status').textContent = 'Faltan datos. Completalos para continuar.';
            return null;
        }
        throw new Error('No se pudo obtener el título, precio, vendedor o ID individual. Revisá el anuncio.');
    }
    return extractedData;
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
    if (!pendingAction || !extractedData) return;
    try {
        const data = readFacebookForm(extractedData);
        if (missingEssential(data)) throw new Error('Completá título, precio y nombre del vendedor.');
        extractedData = data;
        $('facebook-correction').hidden = true;
        const action = pendingAction;
        pendingAction = null;
        if (action === 'report') await sendReport(data);
        else await startAnalysis(data);
    } catch (error) {
        $('status').textContent = error.message;
    }
}

$('login-form').addEventListener('submit', (event) => void handleLogin(event));
$('logout-button').addEventListener('click', () => void handleLogout());
$('analyze-button').addEventListener('click', () => void analyzeClick());
$('reanalyze-button').addEventListener('click', () => void analyzeClick());
$('retry-button').addEventListener('click', () => void analyzeClick());
$('report-form').addEventListener('submit', (event) => void reportSubmit(event));
$('facebook-continue-button').addEventListener('click', () => void continueFacebook());
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
    const taskKey = pageKey && pageStateKey();
    if (taskKey && changes[taskKey]) renderAnalysis(changes[taskKey].newValue);
    if (changes[AUTH_KEY] && !changes[AUTH_KEY].newValue) showLoggedOut('La sesión expiró o se cerró.');
});
ext.tabs.onActivated.addListener(() => void loadCurrentPage());
ext.tabs.onUpdated.addListener((_id, changes, tab) => {
    if (tab.active && (changes.url || changes.status === 'loading')) void loadCurrentPage();
});

void (async () => {
    await checkAuthentication();
    await loadCurrentPage();
})().catch(error => showLoggedOut(`No se pudo iniciar la extensión: ${error.message}`));
