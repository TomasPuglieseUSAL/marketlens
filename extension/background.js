// MarketLens background coordinator (Manifest V3).
// Requests are made by the extension, never from the marketplace's page.
const ext = globalThis.browser ?? globalThis.chrome;
const API_BASE = 'http://localhost:8080/api';
const AUTH_KEY = 'marketlensAuth';
const activeAnalyses = new Map();
const activeResolutions = new Map();
const REFRESH_SKEW_MS = 60_000;
let pendingRefresh = null;
let logoutPending = false;

// El coordinador es el único que rota y persiste credenciales de la extensión.
function validAuthResponse(data) {
    return typeof data?.token === 'string' && data.token.length > 0
        && Number.isFinite(data.expiresIn) && data.expiresIn > 0
        && typeof data.refreshToken === 'string' && data.refreshToken.length > 45;
}
async function loginPersistently(username, password) {
    if (logoutPending) throw new Error('Se está cerrando la sesión.');
    const response = await fetch(`${API_BASE}/auth/extension-login`, {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        credentials: 'omit',
        body: JSON.stringify({username, password})
    });
    if (!response.ok) throw new Error('No se pudo iniciar sesión. Revisá las credenciales.');
    const result = await response.json();
    if (!validAuthResponse(result)) throw new Error('Respuesta de autenticación inválida.');
    const previous = (await ext.storage.local.get(AUTH_KEY))[AUTH_KEY];
    // Una sesión antigua solo se revoca después de guardar la nueva.
    await ext.storage.local.set({
        [AUTH_KEY]: {
            token: result.token,
            expiresAt: Date.now() + result.expiresIn * 1000,
            refreshToken: result.refreshToken
        },
        'marketlensLastUsername': username
    });
    if (previous?.refreshToken && previous.refreshToken !== result.refreshToken) {
        void revokeRemote(previous.refreshToken);
    }
    return {ok: true};
}
async function revokeRemote(refreshToken) {
    try {
        await fetch(`${API_BASE}/auth/logout`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            credentials: 'omit',
            body: JSON.stringify({refreshToken})
        });
    } catch {
        // La credencial local se borra igualmente; el token remoto caducará.
    }
}
async function logoutPersistently() {
    logoutPending = true;
    try {
        const current = (await ext.storage.local.get(AUTH_KEY))[AUTH_KEY];
        await ext.storage.local.remove(AUTH_KEY);
        if (current?.refreshToken) await revokeRemote(current.refreshToken);
        return {ok: true};
    } finally {
        logoutPending = false;
    }
}
async function renewSession(force = false) {
    if (pendingRefresh) return pendingRefresh;
    const job = (async () => {
        if (logoutPending) throw new Error('Se está cerrando la sesión.');
        const snapshot = (await ext.storage.local.get(AUTH_KEY))[AUTH_KEY];
        if (!snapshot?.refreshToken) throw new Error('Iniciá sesión nuevamente.');
        if (!force && snapshot.token && snapshot.expiresAt > Date.now() + REFRESH_SKEW_MS) {
            return snapshot;
        }

        let response;
        try {
            response = await fetch(`${API_BASE}/auth/refresh`, {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                credentials: 'omit',
                body: JSON.stringify({refreshToken: snapshot.refreshToken})
            });
        } catch {
            throw new Error('No se pudo conectar al servidor. Reintentá cuando vuelva a estar disponible.');
        }
        if (response.status === 401 || response.status === 400) {
            // No borrar una sesión nueva que pudo haberse escrito mientras tanto.
            const current = (await ext.storage.local.get(AUTH_KEY))[AUTH_KEY];
            if (current?.refreshToken === snapshot.refreshToken) {
                await ext.storage.local.remove(AUTH_KEY);
            }
            throw new Error('La sesión persistente dejó de ser válida. Iniciá sesión nuevamente.');
        }
        if (!response.ok) throw new Error('No se pudo renovar la sesión por un error del servidor.');
        const result = await response.json().catch(() => null);
        if (!validAuthResponse(result)) throw new Error('Respuesta de renovación inválida.');
        const current = (await ext.storage.local.get(AUTH_KEY))[AUTH_KEY];
        if (logoutPending || current?.refreshToken !== snapshot.refreshToken) {
            await revokeRemote(result.refreshToken);
            throw new Error('La sesión cambió durante la renovación.');
        }
        const next = {
            token: result.token,
            expiresAt: Date.now() + result.expiresIn * 1000,
            refreshToken: result.refreshToken
        };
        await ext.storage.local.set({[AUTH_KEY]: next});
        return next;
    })();
    pendingRefresh = job;
    try {
        return await job;
    } finally {
        if (pendingRefresh === job) pendingRefresh = null;
    }
}
async function accessSession(force = false) {
    const current = (await ext.storage.local.get(AUTH_KEY))[AUTH_KEY];
    if (logoutPending || !current) throw new Error('Iniciá sesión nuevamente.');
    if (!force && current.token && current.expiresAt > Date.now() + REFRESH_SKEW_MS) {
        return current;
    }
    return renewSession(force);
}


function taskStorageKey(pageKey) {
    return `marketlens:analysis:${pageKey}`;
}

function normalizeError(error) {
    return error instanceof Error ? error.message : String(error);
}

async function authenticatedRequest(path, options = {}) {
    let auth = await accessSession();
    const send = async (session) => {
        try {
            return await fetch(`${API_BASE}${path}`, {
                method: options.method ?? 'GET',
                headers: {
                    Authorization: `Bearer ${session.token}`,
                    ...(options.body === undefined ? {} : {'Content-Type': 'application/json'})
                },
                credentials: 'omit',
                ...(options.body === undefined ? {} : {body: JSON.stringify(options.body)})
            });
        } catch {
            throw new Error('No se pudo conectar con el backend de MarketLens.');
        }
    };
    let response = await send(auth);
    if (response.status === 401) {
        // Una sola renovación y un único reintento después de 401.
        // Si otra tarea ya renovó, aprovechar su token.
        const now = (await ext.storage.local.get(AUTH_KEY))[AUTH_KEY];
        auth = now?.token && now.token !== auth.token ? now : await accessSession(true);
        response = await send(auth);
    }
    if (response.status === 401) {
        throw new Error('El servidor rechazó la sesión. Iniciá sesión nuevamente.');
    }

    const result = await response.json().catch(() => null);
    if (!response.ok) {
        const raw = result?.message || result?.detail || result?.error;
        const detail = typeof raw === 'string' ? raw.slice(0, 350) : 'Error de respuesta';
        const method = options.method ?? 'GET';
        throw new Error(`${method} ${path} → HTTP ${response.status}: ${detail}`);
    }
    return result;
}

function requireExtractedData(data) {
    // En el catálogo el MLA de la URL identifica al producto. Solo aceptar
    // el item_id individual si el formulario de compra lo corroboró.
    if (data?.marketplace === 'MERCADO_LIBRE' && typeof data.url === 'string') {
        let catalog = false;
        try {
            catalog = /\/(?:[^/]+\/)?p\/MLA\d{6,}\/?$/i
                .test(new URL(data.url).pathname);
        } catch {
            // La validación de URL del backend emitirá el error pertinente.
        }
        if (catalog && (!/^MLA[1-9]\d{5,}$/i.test(data.selectedOfferId ?? '')
                || data.selectedOfferId !== data.externalId || !data.seller)) {
            throw new Error('No se pudo confirmar la oferta elegida. Seleccioná una oferta con precio y vendedor identificados.');
        }
    }
    if (!data || !['MERCADO_LIBRE', 'FACEBOOK_MARKETPLACE', 'TEMU'].includes(data.marketplace) ||
        !data.externalId || !data.title || !data.price ||
        (data.marketplace !== 'TEMU' && !data.seller)) {
        throw new Error('Faltan datos esenciales del anuncio (título, precio, vendedor o ID).');
    }
    return data;
}

function importPayload(data) {
    return {
        marketplace: data.marketplace,
        title: data.title,
        price: data.price,
        currency: data.currency,
        seller: data.seller,
        externalId: data.externalId,
        selectedOfferId: data.selectedOfferId ?? null,
        description: data.description,
        url: data.url,
        sellerProfileUrl: data.sellerProfileUrl ?? null,
        sellerBadge: data.sellerBadge ?? null,
        temuSellerRating: data.temuSellerRating ?? null,
        temuSellerReviewCount: data.temuSellerReviewCount ?? null,
        temuProductRating: data.temuProductRating ?? null,
        temuProductReviewCount: data.temuProductReviewCount ?? null,
        mlProductRating: data.mlProductRating ?? null,
        mlProductReviewCount: data.mlProductReviewCount ?? null,
        temuStoreTenureText: data.temuStoreTenureText ?? null,
        temuProductSoldText: data.temuProductSoldText ?? null,
        temuStoreSoldText: data.temuStoreSoldText ?? null,
        temuStoreId: data.temuStoreId ?? null,
        temuSellerSectionObserved: data.temuSellerSectionObserved ?? null,
        sellerSalesText: data.sellerSalesText ?? null,
        sellerReputationLevel: data.sellerReputationLevel ?? null,
        sellerId: data.sellerId ?? null,
        facebookJoinYear: data.facebookJoinYear ?? null
    };
}

function identityKey(data) {
    return `${data.marketplace}:${data.externalId}`;
}

async function lookupSavedPublication(data) {
    // Reimportar observaciones recientes de ML y Temu aunque exista un
    // mapeo local: la reputación y las opiniones pueden haber cambiado.
    if (data.marketplace === 'TEMU' || data.marketplace === 'MERCADO_LIBRE') return null;
    const key = `marketlens:publication:${identityKey(data)}`;
    const cachedId = (await ext.storage.local.get(key))[key];
    if (!Number.isSafeInteger(cachedId) || cachedId <= 0) return null;

    // A local map is not proof that the backend still has the publication.
    try {
        await authenticatedRequest(`/publications/${cachedId}`);
        return cachedId;
    } catch (error) {
        // Missing on the server: forget the stale local mapping and recreate.
        // Other errors must be surfaced, not treated as "not found".
        if (!/404/.test(normalizeError(error))) throw error;
        await ext.storage.local.remove(key);
        return null;
    }
}

async function savePublicationIdentity(data, publicationId) {
    const key = `marketlens:publication:${identityKey(data)}`;
    await ext.storage.local.set({[key]: publicationId});
}

async function ensurePublication(data, update) {
    requireExtractedData(data);
    const key = identityKey(data);
    if (activeResolutions.has(key)) return activeResolutions.get(key);

    const job = (async () => {
        await update?.('Buscando una publicación previamente vinculada…');
        const cachedId = await lookupSavedPublication(data);
        if (cachedId) return {publicationId: cachedId, reused: true};

        // Existing extension import endpoints are reused in order.
        await update?.('Guardando los datos del anuncio…');
        const imported = await authenticatedRequest('/extension/imports', {
            method: 'POST', body: importPayload(data)
        });
        if (imported?.status !== 'STORED' || !Number.isSafeInteger(imported.id)) {
            throw new Error('La API de importación devolvió una respuesta inesperada.');
        }

        await update?.('Registrando la referencia externa…');
        const reference = await authenticatedRequest(
            `/extension/imports/${imported.id}/reference`, {method: 'POST'}
        );
        if (!['LISTING', 'USER_PRODUCT', 'CATALOG_PRODUCT', 'PRODUCT'].includes(reference?.referenceType)) {
            throw new Error('La API devolvió una referencia externa no reconocida.');
        }
        if (reference.referenceType === 'CATALOG_PRODUCT') {
            throw new Error('Esta página representa un producto de catálogo, no una oferta individual identificable. Abrí una oferta concreta del vendedor.');
        }

        await update?.('Creando o vinculando la publicación…');
        const processed = await authenticatedRequest(
            `/extension/imports/${imported.id}/process`, {method: 'POST'}
        );
        const id = processed?.publicationId;
        if (!Number.isSafeInteger(id) || id <= 0) {
            throw new Error('El backend no devolvió un ID de publicación válido.');
        }
        await savePublicationIdentity(data, id);
        return {publicationId: id, reused: processed?.created === false};
    })().finally(() => activeResolutions.delete(key));

    activeResolutions.set(key, job);
    return job;
}

async function writeAnalysisState(pageKey, state) {
    await ext.storage.local.set({
        [taskStorageKey(pageKey)]: {...state, updatedAt: Date.now()}
    });
}

async function analyze(pageKey, data) {
    if (activeAnalyses.has(pageKey)) return activeAnalyses.get(pageKey);
    const job = (async () => {
        const progress = async (message) => {
            await writeAnalysisState(pageKey, {status: 'loading', message});
        };
        try {
            await progress('Comprobando los datos de la publicación…');
            const {publicationId, reused} = await ensurePublication(data, progress);
            // Siempre enviamos la observación VISIBLE más reciente, incluso si
            // la publicación ya existía. Este endpoint NO utiliza OAuth.
            let sellerRefreshWarning = null;
            if (data?.marketplace === 'MERCADO_LIBRE') {
                await progress('Registrando la actividad comercial visible del vendedor…');
                try {
                    const observation = await authenticatedRequest(
                        `/publications/${publicationId}/seller-observation`, {
                            method: 'POST',
                            body: {
                                marketplace: data.marketplace,
                                externalId: data.externalId,
                                sellerName: data.seller,
                                sellerSalesText: data.sellerSalesText ?? null,
                                sellerListedProductsText: data.sellerListedProductsText ?? null,
                                sellerInsufficientSalesText: data.sellerInsufficientSalesText ?? null
                            }
                        }
                    );
                    if (observation?.status === 'NO_EXACT_SALES') {
                        sellerRefreshWarning = 'No se encontró una cantidad exacta de ventas; no se inventan valores.';
                    } else if (observation?.status === 'APPROXIMATE_SALES') {
                        sellerRefreshWarning = 'El indicador de ventas es aproximado; no se asignan puntos por esa cifra.';
                    } else if (observation?.status === 'SELLER_MISMATCH') {
                        sellerRefreshWarning = 'El nombre del vendedor no coincide con el registrado; no se actualizaron sus ventas.';
                    }
                } catch (error) {
                    // La ausencia de ventas no debe bloquear el resto del análisis.
                    sellerRefreshWarning = `No se pudieron registrar las ventas visibles: ${normalizeError(error)}`;
                }
            }
            await progress('Calculando el riesgo de la publicación…');
            const evaluation = await authenticatedRequest(
                `/publications/${publicationId}/evaluations`, {method: 'POST'}
            );
            // La ausencia del resumen no invalida la evaluación ya completada.
            let communityReports = null;
            try {
                communityReports = await authenticatedRequest(
                    `/publications/${publicationId}/reports/summary`
                );
            } catch {
                // API del resumen no disponible: omitir el módulo de reportes.
            }
            await writeAnalysisState(pageKey, {
                status: 'success', publicationId, reused, sellerRefreshWarning,
                evaluation: evaluation?.evaluation ?? evaluation,
                communityReports,
                message: 'Evaluación completada.'
            });
            return {ok: true, publicationId};
        } catch (error) {
            await writeAnalysisState(pageKey, {status: 'error', message: normalizeError(error)});
            return {ok: false, error: normalizeError(error)};
        }
    })().finally(() => activeAnalyses.delete(pageKey));
    activeAnalyses.set(pageKey, job);
    return job;
}

const REASONS = new Set([
    'SUSPICIOUS_PRICE', 'SUSPICIOUS_SELLER', 'SUSPICIOUS_DESCRIPTION',
    'PAYMENT_OUTSIDE_PLATFORM', 'POSSIBLE_IMPERSONATION', 'OTHER'
]);

async function submitReport(data, reason, description) {
    if (!REASONS.has(reason)) throw new Error('Seleccioná un motivo de reporte válido.');
    if (typeof description !== 'string' || description.length > 2000) {
        throw new Error('La descripción puede tener hasta 2000 caracteres.');
    }
    const {publicationId} = await ensurePublication(data);
    const report = await authenticatedRequest(`/publications/${publicationId}/reports`, {
        method: 'POST', body: {reason, description}
    });
    let communityReports = null;
    try {
        communityReports = await authenticatedRequest(
            `/publications/${publicationId}/reports/summary`
        );
    } catch {
        // Reporte registrado. Un fallo de la consulta agregada no lo invalida.
    }
    return {publicationId, reportId: report?.id ?? null, communityReports};
}

ext.runtime.onMessage.addListener((message, _sender, sendResponse) => {
    if (!message || typeof message !== 'object') return false;
    if (message.type === 'AUTH_LOGIN') {
        loginPersistently(message.username, message.password)
            .then(sendResponse)
            .catch(error => sendResponse({ok: false, error: normalizeError(error)}));
        return true;
    }
    if (message.type === 'AUTH_ACCESS') {
        accessSession(message.force === true)
            .then(session => sendResponse({ok: true, token: session.token}))
            .catch(error => sendResponse({ok: false, error: normalizeError(error)}));
        return true;
    }
    if (message.type === 'AUTH_LOGOUT') {
        logoutPersistently()
            .then(sendResponse)
            .catch(error => sendResponse({ok: false, error: normalizeError(error)}));
        return true;
    }
    if (message.type === 'ANALYZE_PUBLICATION') {
        // Acknowledge immediately; work continues outside the popup lifetime.
        if (typeof message.pageKey !== 'string' || message.pageKey.length > 5000) {
            sendResponse({ok: false, error: 'Identidad de página inválida.'});
            return false;
        }
        void analyze(message.pageKey, message.data);
        sendResponse({ok: true});
        return false;
    }
    if (message.type === 'SUBMIT_REPORT') {
        submitReport(message.data, message.reason, message.description)
            .then(result => sendResponse({ok: true, ...result}))
            .catch(error => sendResponse({ok: false, error: normalizeError(error)}));
        return true;
    }
    return false;
});
