package org.b3log.siyuan;

import android.net.Uri;
import android.webkit.WebView;
import android.webkit.JavascriptInterface;

import androidx.webkit.JavaScriptReplyProxy;
import androidx.webkit.ScriptHandler;
import androidx.webkit.WebMessageCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.UUID;

/** Origin-aware replacement for the all-frame JavascriptInterface on supported WebViews. */
final class NativeBridgeBoundary implements AutoCloseable {
    private static final String TRANSPORT = "SiYuanNativeTransport";
    private static final String SYNC_TRANSPORT = "SiYuanNativeSync";
    private final WebView webView;
    private final JSAndroid delegate;
    private ScriptHandler script;
    private boolean listenerInstalled;
    private volatile boolean installed;
    private volatile boolean closed;
    private volatile boolean trustedDocument;
    private volatile long generation;
    private volatile String session;

    NativeBridgeBoundary(final WebView webView, final JSAndroid delegate) {
        this.webView = webView;
        this.delegate = delegate;
    }

    boolean install() {
        webView.removeJavascriptInterface("JSAndroid");
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
                || !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            return false;
        }
        try {
            final String source;
            try (InputStream input = webView.getContext().getAssets().open("native-bridge.js");
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int length;
                while ((length = input.read(buffer)) != -1) {
                    output.write(buffer, 0, length);
                }
                source = new String(output.toByteArray(), StandardCharsets.UTF_8);
            }
            webView.addJavascriptInterface(new SyncBridge(), SYNC_TRANSPORT);
            WebViewCompat.addWebMessageListener(webView, TRANSPORT,
                    Collections.singleton(NativeBridgePolicy.ORIGIN), this::onMessage);
            listenerInstalled = true;
            script = WebViewCompat.addDocumentStartJavaScript(webView, source,
                    Collections.singleton(NativeBridgePolicy.ORIGIN));
            installed = true;
            return true;
        } catch (Exception e) {
            // Fail closed for maps, while the caller retains the legacy app bridge.
            close();
            Utils.logError("webview", "Install origin-aware bridge failed", e);
            return false;
        }
    }

    void onNavigation(final String url) {
        generation++;
        session = null;
        trustedDocument = NativeBridgePolicy.isTrustedDocument(url);
    }

    private void onMessage(final WebView sourceView, final WebMessageCompat message,
                           final Uri sourceOrigin, final boolean isMainFrame,
                           final JavaScriptReplyProxy reply) {
        // sourceOrigin/isMainFrame are supplied by WebView, never by page JS or getUrl().
        if (!installed || closed || sourceView != webView || !isMainFrame
                || !trustedDocument || !NativeBridgePolicy.isTrustedOrigin(sourceOrigin.toString())) {
            return;
        }
        long id = -1;
        final long epoch = generation;
        try {
            if (message.getType() != WebMessageCompat.TYPE_STRING || message.getData() == null
                    || message.getData().length() > 8 * 1024 * 1024) {
                return;
            }
            final JSONObject request = new JSONObject(message.getData());
            id = request.getLong("id");
            final String method = request.getString("method");
            final JSONArray args = request.getJSONArray("args");
            if ("hello".equals(method)) {
                arity(args, 0);
                if (session == null) {
                    session = UUID.randomUUID().toString();
                }
                respond(reply, id, session, null, epoch);
                return;
            }
            respond(reply, id, null, "Unknown authorization request", epoch);
        } catch (Exception e) {
            respond(reply, id, null, "Invalid native bridge request", epoch);
        }
    }

    private void respond(final JavaScriptReplyProxy reply, final long id, final Object value,
                         final String error, final long epoch) {
        if (closed || !installed || generation != epoch || !trustedDocument) {
            return;
        }
        try {
            final JSONObject response = new JSONObject();
            response.put("id", id);
            response.put("value", value == null ? JSONObject.NULL : value);
            if (error != null) {
                response.put("error", error);
            }
            reply.postMessage(response.toString());
        } catch (Exception ignored) {
            // Navigation can dispose the reply proxy. The JS timeout settles the request.
        }
    }

    /** The raw all-frame object has no authority without the main-document secret. */
    public final class SyncBridge {
        @JavascriptInterface
        public String invoke(final String token, final String method, final String arguments) {
            final long epoch = generation;
            try {
                if (!installed || closed || !trustedDocument || session == null || !session.equals(token)) {
                    throw new IllegalStateException("Unauthorized native document");
                }
                if (arguments == null || arguments.length() > 8 * 1024 * 1024) {
                    throw new IllegalArgumentException("Invalid native arguments");
                }
                final JSONArray args = new JSONArray(arguments);
                final Object value;
                if ("getAVMapNativeBoundary".equals(method)) {
                    arity(args, 0);
                    final JSONObject capability = new JSONObject();
                    capability.put("version", 1);
                    capability.put("enabled", true);
                    value = capability;
                } else {
                    value = dispatch(method, args);
                }
                if (closed || !installed || generation != epoch || !token.equals(session)) {
                    throw new IllegalStateException("Native document changed");
                }
                final JSONObject response = new JSONObject();
                response.put("value", value == null ? JSONObject.NULL : value);
                return response.toString();
            } catch (Exception e) {
                // Never return secrets, argument contents, paths or exception details.
                return "{\"error\":\"Native bridge call failed or document expired\"}";
            }
        }
    }

    private Object dispatch(final String method, final JSONArray args) throws Exception {
        // Explicit allowlist: never reflect arbitrary methods from JSAndroid or Activity.
        switch (method) {
            case "logInputEvent":
                arity(args, 1);
                delegate.logInputEvent(string(args, 0));
                return null;
            case "cancelNotification":
                arity(args, 1);
                delegate.cancelNotification(integer(args, 0));
                return null;
            case "sendNotification":
                arity(args, 4);
                return delegate.sendNotification(string(args, 0), string(args, 1), string(args, 2), integer(args, 3));
            case "exit":
                arity(args, 0);
                delegate.exit();
                return null;
            case "hideKeyboard":
                arity(args, 0);
                delegate.hideKeyboard();
                return null;
            case "showKeyboard":
                arity(args, 0);
                delegate.showKeyboard();
                return null;
            case "setWebViewFocusable":
                arity(args, 1);
                delegate.setWebViewFocusable(bool(args, 0));
                return null;
            case "getWordSelection":
                arity(args, 3);
                return delegate.getWordSelection(string(args, 0), integer(args, 1), integer(args, 2));
            case "prepareWordSelection":
                arity(args, 3);
                return delegate.prepareWordSelection(string(args, 0), integer(args, 1), integer(args, 2));
            case "getBlockURL":
                arity(args, 0);
                return delegate.getBlockURL();
            case "setWebViewDebuggingEnabled":
                arity(args, 1);
                delegate.setWebViewDebuggingEnabled(bool(args, 0));
                return null;
            case "readClipboard":
                arity(args, 0);
                return delegate.readClipboard();
            case "readHTMLClipboard":
                arity(args, 0);
                return delegate.readHTMLClipboard();
            case "readSiYuanHTMLClipboard":
                arity(args, 0);
                return delegate.readSiYuanHTMLClipboard();
            case "writeImageClipboard":
                arity(args, 1);
                delegate.writeImageClipboard(string(args, 0));
                return null;
            case "writeClipboard":
                arity(args, 1);
                delegate.writeClipboard(string(args, 0));
                return null;
            case "writeHTMLClipboard":
                arity(args, 2);
                delegate.writeHTMLClipboard(string(args, 0), string(args, 1));
                return null;
            case "writeSiYuanHTMLClipboard":
                arity(args, 3);
                delegate.writeSiYuanHTMLClipboard(string(args, 0), string(args, 1), string(args, 2));
                return null;
            case "returnDesktop":
                arity(args, 0);
                delegate.returnDesktop();
                return null;
            case "exportByDefault":
                arity(args, 1);
                delegate.exportByDefault(string(args, 0));
                return null;
            case "saveExportFile":
                arity(args, 1);
                delegate.saveExportFile(string(args, 0));
                return null;
            case "saveExportFileV2":
                arity(args, 2);
                delegate.saveExportFileV2(string(args, 0), string(args, 1));
                return null;
            case "print":
                arity(args, 2);
                delegate.print(string(args, 0), string(args, 1));
                return null;
            case "getScreenWidthPx":
                arity(args, 0);
                return delegate.getScreenWidthPx();
            case "openExternal":
                arity(args, 1);
                delegate.openExternal(string(args, 0));
                return null;
            case "openAuthURL":
                arity(args, 1);
                delegate.openAuthURL(string(args, 0));
                return null;
            case "getOIDCCallback":
                arity(args, 0);
                return delegate.getOIDCCallback();
            case "changeStatusBarColor":
                arity(args, 2);
                delegate.changeStatusBarColor(string(args, 0), integer(args, 1));
                return null;            default:
                throw new IllegalArgumentException("Unknown native method");
        }
    }

    private static void arity(final JSONArray args, final int expected) {
        if (args.length() != expected) {
            throw new IllegalArgumentException("Invalid argument count");
        }
    }

    private static String string(final JSONArray args, final int index) throws Exception {
        final Object value = args.get(index);
        if (!(value instanceof String)) {
            throw new IllegalArgumentException("Expected string");
        }
        return (String) value;
    }

    private static boolean bool(final JSONArray args, final int index) throws Exception {
        final Object value = args.get(index);
        if (!(value instanceof Boolean)) {
            throw new IllegalArgumentException("Expected boolean");
        }
        return (Boolean) value;
    }

    private static int integer(final JSONArray args, final int index) throws Exception {
        final Object value = args.get(index);
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException("Expected integer");
        }
        final double number = ((Number) value).doubleValue();
        if (!Double.isFinite(number) || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE
                || number != Math.rint(number)) {
            throw new IllegalArgumentException("Expected integer");
        }
        return (int) number;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        installed = false;
        trustedDocument = false;
        generation++;
        session = null;
        try {
            webView.evaluateJavascript("window.__revokeSiYuanNativeBridge && window.__revokeSiYuanNativeBridge()", null);
        } catch (RuntimeException ignored) {
            // A crashed renderer cannot receive additional callbacks.
        }
        if (script != null) {
            try {
                script.remove();
            } catch (RuntimeException ignored) { }
            script = null;
        }
        if (listenerInstalled) {
            try {
                WebViewCompat.removeWebMessageListener(webView, TRANSPORT);
            } catch (RuntimeException ignored) { }
            listenerInstalled = false;
        }
        try {
            webView.removeJavascriptInterface("JSAndroid");
            webView.removeJavascriptInterface(SYNC_TRANSPORT);
        } catch (RuntimeException ignored) {
            // State and document capability were revoked before touching the renderer.
        }
    }
}
