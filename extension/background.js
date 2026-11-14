// MarketLens background coordinator (Manifest V3).
// Requests are made by the extension, never from the marketplace's page.
const ext = globalThis.browser ?? globalThis.chrome;
const API_BASE = 'http://localhost:8080/api';
const AUTH_KEY = 'marketlensAuth';
const activeAnalyses = new Map();
const activeResolutions = new Map();

function taskStorageKey(pageKey) {
    return `marketlens:analysis:${pageKey}`;
}

function normalizeError(error) {
    return error instanceof Error ? error.message : String(error);
}

async function authenticatedRequest(path, options = {}) {
    const stored = await ext.storage.local.get(AUTH_KEY);
    const auth = stored[AUTH_KEY];
    if (!auth?.token || !Number.isFinite(auth.expiresAt) || auth.expiresAt <= Date.now()) {
        throw new Error('Iniciá sesión nuevamente para continuar.');
    }

    let response;
    try {
        response = await fetch(`${API_BASE}${path}`, {
            method: options.method ?? 'GET',
            headers: {
                Authorization: `Bearer ${auth.token}`,
                ...(options.body === undefined ? {} : {'Content-Type': 'application/json'})
            },
            credentials: 'omit',
            ...(options.body === undefined ? {} : {body: JSON.stringify(options.body)})
        });
    } catch {
        throw new Error('No se pudo conectar con el backend de MarketLens.');
    }

    if (response.status === 401) {
        await ext.storage.local.remove(AUTH_KEY);
        throw new Error('Tu sesión expiró. Iniciá sesión nuevamente.');
    }

    const result = await response.json().catch(() => null);
    if (!response.ok) {
        const raw = result?.message || result?.detail || result?.error;
        const message = typeof raw === 'string' ? raw.slice(0, 350) : `Error HTTP ${response.status}`;
        throw new Error(message);
    }
    return result;
}

function requireExtractedData(data) {
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
        description: data.description,
        url: data.url,
        sellerProfileUrl: data.sellerProfileUrl ?? null,
        sellerBadge: data.sellerBadge ?? null,
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
    if (data.marketplace === 'TEMU') return null; // El precio podría haber cambiado.
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
            await writeAnalysisState(pageKey, {
                status: 'success', publicationId, reused, sellerRefreshWarning,
                evaluation: evaluation?.evaluation ?? evaluation,
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
    return {publicationId, reportId: report?.id ?? null};
}

ext.runtime.onMessage.addListener((message, _sender, sendResponse) => {
    if (!message || typeof message !== 'object') return false;
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
