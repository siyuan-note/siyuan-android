const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const root = path.resolve(__dirname, '../..');
const source = fs.readFileSync(path.join(root, 'main/assets/native-bridge.js'), 'utf8');
function harness({child = false, origin = 'http://127.0.0.1:6806', pathname = '/stage/build/mobile/', transport = true} = {}) {
    const sent = [], syncCalls = [], timers = new Map(), events = {};
    let syncError = false, currentToken = "session-A";
    let timerID = 0, reloads = 0;
    const window = {addEventListener: (name, listener) => { events[name] = listener; }};
    window.top = child ? {} : window;
    if (transport) window.SiYuanNativeTransport = {postMessage: data => sent.push(JSON.parse(data))};
    window.SiYuanNativeSync = {invoke(token, method, args) {
        syncCalls.push({token, method, args: JSON.parse(args)});
        if (syncError || token !== currentToken) return JSON.stringify({error: 'Native bridge call failed or document expired'});
        const values = {readClipboard: 'plain text', getScreenWidthPx: 1080, prepareWordSelection: true,
            getWordSelection: '[0,3]', getAVMapNativeBoundary: {version: 1, enabled: true}};
        return JSON.stringify({value: values[method] ?? null});
    }};
    const context = vm.createContext({window, location: {origin, pathname, reload() { reloads++; }},
        setTimeout(callback) { const id = ++timerID; timers.set(id, callback); return id; },
        clearTimeout(id) { timers.delete(id); }});
    vm.runInContext(source, context);
    return {window, sent, syncCalls, events, timers, set syncError(value) { syncError = value; },
        set token(value) { currentToken = value; }, get reloads() { return reloads; },
        reply(request, value, error) { window.SiYuanNativeTransport.onmessage({data: JSON.stringify({id: request.id, value, error})}); }};
}
const tick = () => new Promise(resolve => setImmediate(resolve));
(async () => {
    for (const options of [{child: true}, {origin: 'https://evil.example'}, {pathname: '/assets/evil.html'}, {pathname: '/stage/map/wrapper.html'}, {pathname: '/stage/map/index.html'}, {transport: false}]) {
        const h = harness(options);
        assert.equal(h.window.JSAndroid, undefined);
        assert.equal(h.window.getAVMapNativeBoundary, undefined);
        assert.equal(h.sent.length, 0);
    }
    const h = harness();
    assert.equal(h.sent[0].method, 'hello');
    assert.throws(() => h.window.JSAndroid.readClipboard(), /not ready/);
    assert.equal(h.window.JSAndroid.showKeyboard(), undefined, 'early void calls queue');
    const capability = h.window.getAVMapNativeBoundary();
    assert.equal(h.syncCalls.length, 0);
    h.reply(h.sent[0], 'session-A');
    assert.equal(await h.window.JSAndroid.ready, undefined, 'ready must not disclose token');
    await tick();
    assert.equal(h.syncCalls[0].method, 'showKeyboard');
    assert.deepEqual(JSON.parse(JSON.stringify(await capability)), {version: 1, enabled: true});
    assert.equal(h.window.JSAndroid.readClipboard(), 'plain text');
    assert.equal(h.window.JSAndroid.getScreenWidthPx(), 1080);
    assert.equal(h.window.JSAndroid.getWordSelection('中文词', 1, 2), '[0,3]');
    assert.equal(h.window.JSAndroid.prepareWordSelection('中文词', -1, 1), true);
    assert.equal(h.window.JSAndroid.showKeyboard(), undefined, 'void shape is unchanged');
    assert.ok(!JSON.stringify(h.window.JSAndroid).includes('session-A'));
    assert.ok(!h.window.JSAndroid.readClipboard.toString().includes('session-A'));
    h.syncError = true;
    assert.throws(() => h.window.JSAndroid.getBlockURL(), /failed/);
    h.syncError = false;
    h.events.pagehide();
    assert.equal(h.timers.size, 0);
    assert.throws(() => h.window.JSAndroid.readClipboard(), /expired/);
    await assert.rejects(h.window.getAVMapNativeBoundary(), /expired/);
    h.events.pageshow({persisted: true}); assert.equal(h.reloads, 1);

    const timeout = harness();
    const rejectTimeout = assert.rejects(timeout.window.JSAndroid.ready, /timed out/);
    for (const callback of timeout.timers.values()) callback(); await rejectTimeout;
    assert.throws(() => timeout.window.JSAndroid.readClipboard(), /not ready/);
    const destroyedBeforeReady = harness();
    const rejectDestroy = assert.rejects(destroyedBeforeReady.window.JSAndroid.ready, /no longer active/);
    destroyedBeforeReady.window.__revokeSiYuanNativeBridge();
    destroyedBeforeReady.reply(destroyedBeforeReady.sent[0], 'stale-token'); await rejectDestroy;
    assert.throws(() => destroyedBeforeReady.window.JSAndroid.readClipboard(), /expired/);
    const restored = harness(); restored.token = 'session-B';
    restored.reply(restored.sent[0], 'session-B'); await restored.window.JSAndroid.ready;
    assert.equal(restored.window.JSAndroid.readClipboard(), 'plain text');
    assert.equal(restored.syncCalls.at(-1).token, 'session-B');
    restored.token = 'session-C';
    assert.throws(() => restored.window.JSAndroid.readClipboard(), /expired/);
    assert.equal(restored.window.SiYuanNativeSync.invoke('guessed-token', 'readClipboard', '[]'),
        '{"error":"Native bridge call failed or document expired"}');

    // Contract completeness and non-reflective native dispatch (not a device test).
    const native = fs.readFileSync(path.join(root, 'main/java/org/b3log/siyuan/NativeBridgeBoundary.java'), 'utf8');
    const delegate = fs.readFileSync(path.join(root, 'main/java/org/b3log/siyuan/JSAndroid.java'), 'utf8');
    for (const match of delegate.matchAll(/@JavascriptInterface\s+public \w+ (\w+)\(/g)) {
        assert.ok(native.includes(`case "${match[1]}"`), `missing explicit dispatch: ${match[1]}`);
    }
    assert.ok(native.includes('!isMainFrame'));
    assert.ok(native.includes('isTrustedOrigin(sourceOrigin.toString())'));
    assert.ok(!native.includes('getUrl()') || native.includes('never by page JS or getUrl()'));
    assert.ok(!native.includes('getMethod('));
    console.log('Native bridge facade isolation, replies, errors, timeout, navigation, BFCache and destroy passed');
})().catch(error => { console.error(error); process.exitCode = 1; });
