const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '../background.js'), 'utf8');
const rawToken = n => '1.' + n.repeat(43);

function environment(initial = {}) {
    const state = {...initial};
    const requests = [];
    let listener;
    let online = true;
    let now = 1_000_000;
    let refreshCalls = 0;
    let rejectProtectedOnce = false;
    let rejectRefresh = false;
    const storage = {
        async get(key) { return {[key]: state[key]}; },
        async set(values) { Object.assign(state, values); },
        async remove(key) { delete state[key]; }
    };
    const browser = {
        storage: {local: storage},
        runtime: {onMessage: {addListener(callback) { listener = callback; }}}
    };
    const FakeDate = class extends Date { static now() { return now; } };
    async function fetch(url, options) {
        requests.push({url, options});
        if (!online) throw new Error('Backend offline');
        if (url.endsWith('/auth/extension-login')) {
            return {
                ok: true, status: 200,
                json: async () => ({
                    token: 'jwt-initial',
                    expiresIn: 3600,
                    refreshToken: rawToken('a')
                })
            };
        }
        if (url.endsWith('/auth/refresh')) {
            refreshCalls++;
            if (rejectRefresh) return {ok: false, status: 401};
            return {
                ok: true, status: 200,
                json: async () => ({
                    token: 'jwt-renewed',
                    expiresIn: 3600,
                    refreshToken: rawToken('b')
                })
            };
        }
        if (url.endsWith('/auth/logout')) {
            return {ok: true, status: 204};
        }
        if (rejectProtectedOnce) {
            rejectProtectedOnce = false;
            return {ok: false, status: 401, json: async () => ({})};
        }
        return {ok: true, status: 200, json: async () => ({ok: true})};
    }
    const context = vm.createContext({browser, fetch, Date: FakeDate, URL, console});
    vm.runInContext(source, context, {filename: 'background.js'});
    const send = message => new Promise(resolve => {
        assert.equal(listener(message, {}, resolve), true);
    });
    return {
        state, requests, send,
        offline() { online = false; },
        rejectNextProtected() { rejectProtectedOnce = true; },
        rejectRenewal() { rejectRefresh = true; },
        expireAccess() { state.marketlensAuth.expiresAt = now - 1; },
        refreshCalls() { return refreshCalls; },
        run(sourceCode) { return vm.runInContext(sourceCode, context); }
    };
}

test('el primer login guarda tokens y nombre, nunca la contraseña', async () => {
    const env = environment();
    const reply = await env.send({
        type: 'AUTH_LOGIN', username: 'demo', password: 'contraseña temporal'
    });
    assert.equal(reply.ok, true);
    assert.equal(env.state.marketlensLastUsername, 'demo');
    assert.equal(env.state.marketlensAuth.token, 'jwt-initial');
    assert.equal(env.state.marketlensAuth.refreshToken, rawToken('a'));
    assert.equal(JSON.stringify(env.state).includes('contraseña temporal'), false);
});

test('dos peticiones simultáneas comparten una sola renovación', async () => {
    const env = environment({
        marketlensAuth: {token: 'expired', expiresAt: 1, refreshToken: rawToken('a')}
    });
    const [first, second] = await Promise.all([
        env.send({type: 'AUTH_ACCESS'}),
        env.send({type: 'AUTH_ACCESS'})
    ]);
    assert.equal(first.ok, true);
    assert.equal(second.ok, true);
    assert.equal(first.token, 'jwt-renewed');
    assert.equal(second.token, 'jwt-renewed');
    assert.equal(env.refreshCalls(), 1);
    assert.equal(env.state.marketlensAuth.refreshToken, rawToken('b'));
});

test('el logout elimina la credencial local y revoca la remota', async () => {
    const env = environment({
        marketlensAuth: {token: 'jwt', expiresAt: 9999999, refreshToken: rawToken('a')}
    });
    const reply = await env.send({type: 'AUTH_LOGOUT'});
    assert.equal(reply.ok, true);
    assert.equal(env.state.marketlensAuth, undefined);
    assert.ok(env.requests.some(r => r.url.endsWith('/auth/logout')));
    assert.equal((await env.send({type: 'AUTH_ACCESS'})).ok, false);
});

test('si el backend está desconectado se conserva el refresh token', async () => {
    const env = environment({
        marketlensAuth: {token: 'expired', expiresAt: 1, refreshToken: rawToken('a')}
    });
    env.offline();
    const reply = await env.send({type: 'AUTH_ACCESS'});
    assert.equal(reply.ok, false);
    assert.equal(env.state.marketlensAuth.refreshToken, rawToken('a'));
});

test('un JWT previo sin refresh requiere una sola autenticación nueva', async () => {
    const env = environment({
        marketlensAuth: {token: 'expired', expiresAt: 1}
    });
    assert.equal((await env.send({type: 'AUTH_ACCESS'})).ok, false);
});

test('un 401 anticipado reintenta con un JWT renovado sin perder la sesión', async () => {
    const env = environment({
        marketlensAuth: {
            token: 'jwt-previous', expiresAt: 9_999_999,
            refreshToken: rawToken('a')
        }
    });
    env.rejectNextProtected();
    const result = await env.run("authenticatedRequest('/publications/1')");
    assert.equal(result.ok, true);
    assert.equal(env.refreshCalls(), 1);
    assert.equal(env.state.marketlensAuth.token, 'jwt-renewed');
});

test('una renovación revocada elimina solo la sesión inválida', async () => {
    const env = environment({
        marketlensAuth: {token: 'expired', expiresAt: 1, refreshToken: rawToken('a')}
    });
    env.rejectRenewal();
    const reply = await env.send({type: 'AUTH_ACCESS'});
    assert.equal(reply.ok, false);
    assert.equal(env.state.marketlensAuth, undefined);
});
