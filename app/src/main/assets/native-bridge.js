(function () {
    "use strict";
    if (window !== window.top || location.origin !== "http://127.0.0.1:6806" ||
        !["/", "/check-auth", "/appearance/boot/index.html", "/stage/build/app/", "/stage/build/app/index.html",
          "/stage/build/mobile/", "/stage/build/mobile/index.html", "/stage/build/desktop/", "/stage/build/desktop/index.html"].includes(location.pathname)) return;
    const transport = window.SiYuanNativeTransport;
    if (!transport || typeof transport.postMessage !== "function") return;
    let alive = true, nextID = 0;
    const pending = new Map();
    function revoke() {
        alive = false;
        session = null;
        for (const item of pending.values()) {
            clearTimeout(item.timer);
            item.reject(new Error("Native document is no longer active"));
        }
        pending.clear();
    }
    window.addEventListener("pagehide", revoke, {once: true});
    window.addEventListener("pageshow", function (event) {
        // A BFCache restore must receive a fresh document/session rather than revive
        // promises or authority retained by the previous document.
        if (event.persisted) location.reload();
    });
    Object.defineProperty(window, "__revokeSiYuanNativeBridge", {value: revoke});
    transport.onmessage = function (event) {
        let reply;
        try { reply = JSON.parse(event.data); } catch (_) { return; }
        const item = pending.get(reply.id);
        if (!item || !alive) return;
        pending.delete(reply.id);
        clearTimeout(item.timer);
        if (reply.error) item.reject(new Error(reply.error));
        else item.resolve(reply.value);
    };
    function request(method, args, session) {
        return new Promise(function (resolve, reject) {
            if (!alive) { reject(new Error("Native document is no longer active")); return; }
            const id = ++nextID;
            const timer = setTimeout(function () {
                pending.delete(id);
                reject(new Error("Native bridge timed out"));
            }, 10000);
            pending.set(id, {resolve, reject, timer});
            try { transport.postMessage(JSON.stringify({id, method, args, session})); }
            catch (error) { clearTimeout(timer); pending.delete(id); reject(error); }
        });
    }
    // Only the real main-frame listener can issue this document's secret. Neither
    // JSAndroid.ready nor any global object exposes its value. The all-frame raw
    // Java object requires it for every call and cannot issue or renew authority.
    let session = null;
    const syncTransport = window.SiYuanNativeSync;
    const ready = request("hello", [], "").then(function (token) {
        if (!alive || typeof token !== "string" || !token) throw new Error("Native authorization failed");
        session = token;
    });
    ready.catch(function () {});
    function invoke(method, args) {
        if (!alive || !session) throw new Error("Native bridge is not ready or document expired");
        const reply = JSON.parse(syncTransport.invoke(session, method, JSON.stringify(args)));
        if (reply.error) throw new Error(reply.error);
        return reply.value;
    }
    const bridge = {ready};
    ["sendNotification", "getWordSelection", "prepareWordSelection", "getBlockURL", "readClipboard", "readHTMLClipboard", "readSiYuanHTMLClipboard", "getScreenWidthPx", "getOIDCCallback"].forEach(function (name) { bridge[name] = (...args) => invoke(name, args); });
    ["logInputEvent", "cancelNotification", "exit", "hideKeyboard", "showKeyboard", "setWebViewFocusable", "setWebViewDebuggingEnabled", "writeImageClipboard", "writeClipboard", "writeHTMLClipboard", "writeSiYuanHTMLClipboard", "returnDesktop", "exportByDefault", "saveExportFile", "saveExportFileV2", "print", "openExternal", "openAuthURL", "changeStatusBarColor"].forEach(function (name) {
        bridge[name] = (...args) => {
            if (session && alive) { invoke(name, args); return; }
            ready.then(() => invoke(name, args)).catch(function () {});
        };
    });
    bridge.getAVMapNativeBoundary = () => ready.then(() => invoke("getAVMapNativeBoundary", []));
    Object.defineProperty(window, "JSAndroid", {value: Object.freeze(bridge)});
    Object.defineProperty(window, "getAVMapNativeBoundary", {value: bridge.getAVMapNativeBoundary});
})();
