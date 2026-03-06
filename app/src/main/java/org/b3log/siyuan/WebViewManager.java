/*
 * SiYuan - 源于思考,饮水思源
 * Copyright (c) 2020-present, b3log.org
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.b3log.siyuan;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;

import java.lang.ref.WeakReference;

/**
 * WebView lifecycle and configuration manager.
 * WebView 生命周期和配置管理器
 *
 * @version 1.0.0.0, Mar 6, 2026
 */
public class WebViewManager {

    private final WeakReference<Activity> activityRef;
    private WebView webView;
    private String userAgent;

    /**
     * Constructor.
     *
     * @param activity Parent activity
     * @param webView  WebView instance
     */
    public WebViewManager(Activity activity, WebView webView) {
        this.activityRef = new WeakReference<>(activity);
        this.webView = webView;
    }

    /**
     * Initialize WebView with basic settings.
     * 使用基本设置初始化 WebView
     *
     * @return User agent string
     */
    @SuppressLint("SetJavaScriptEnabled")
    public String initialize() {
        if (webView == null) {
            return null;
        }

        final WebSettings ws = webView.getSettings();

        // Store original user agent
        userAgent = ws.getUserAgentString();
        Log.i("webview", "User agent: " + userAgent);

        // Configure basic settings
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setCacheMode(WebSettings.LOAD_NO_CACHE);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        ws.setTextZoom(100);
        ws.setUseWideViewPort(true);
        ws.setLoadWithOverviewMode(true);

        // Set custom user agent
        ws.setUserAgentString("SiYuan/" + Utils.version +
                " https://b3log.org/siyuan Android " + userAgent);

        // Enable third-party cookies
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        Log.i("webview", "WebView initialized successfully");
        return userAgent;
    }

    /**
     * Register JavaScript interface.
     * 注册 JavaScript 接口
     *
     * @param jsAndroid JSAndroid instance
     */
    public void registerJavaScriptInterface(JSAndroid jsAndroid) {
        if (webView != null) {
            webView.addJavascriptInterface(jsAndroid, AppConfig.JS_INTERFACE_NAME);
            Log.i("webview", "JavaScript interface registered");
        }
    }

    /**
     * Load the boot URL.
     * 加载启动 URL
     */
    public void loadBootUrl() {
        if (webView != null) {
            webView.loadUrl(AppConfig.KERNEL_BOOT_URL);
            webView.setVisibility(View.VISIBLE);
            Log.i("webview", "Loading boot URL: " + AppConfig.KERNEL_BOOT_URL);
        }
    }

    /**
     * Evaluate JavaScript code.
     * 执行 JavaScript 代码
     *
     * @param script JavaScript code
     */
    public void evaluateJavaScript(String script) {
        if (webView != null) {
            webView.evaluateJavascript(script, null);
        }
    }

    /**
     * Execute back navigation.
     * 执行返回导航
     */
    public void goBack() {
        evaluateJavaScript("javascript:window.goBack ? window.goBack() : window.history.back()");
    }

    /**
     * Open a block by URL.
     * 通过 URL 打开块
     *
     * @param blockURL Block URL
     */
    public void openBlockByURL(String blockURL) {
        if (blockURL != null && webView != null) {
            final Activity activity = activityRef.get();
            if (activity != null) {
                activity.runOnUiThread(() ->
                    evaluateJavaScript("javascript:window.openFileByURL('" +
                        blockURL.replace("'", "\\'") + "')"));
            }
        }
    }

    /**
     * Handle OIDC callback link.
     * 处理 OIDC 回调链接
     *
     * @param callbackUrl OIDC callback URL
     */
    public void handleOidcCallback(String callbackUrl) {
        if (callbackUrl != null && webView != null) {
            final Activity activity = activityRef.get();
            if (activity != null) {
                activity.runOnUiThread(() ->
                    evaluateJavaScript("javascript:window.handleOidcCallbackLink('" +
                        callbackUrl.replace("'", "\\'") + "')"));
            }
        }
    }

    /**
     * Reconnect WebSocket.
     * 重新连接 WebSocket
     */
    public void reconnectWebSocket() {
        evaluateJavaScript("javascript:window.reconnectWebSocket()");
    }

    /**
     * Pause WebView.
     * 暂停 WebView
     */
    public void pause() {
        if (webView != null) {
            webView.onPause();
            webView.pauseTimers();
            Log.i("webview", "WebView paused");
        }
    }

    /**
     * Resume WebView.
     * 恢复 WebView
     */
    public void resume() {
        if (webView != null) {
            webView.onResume();
            webView.resumeTimers();
            Log.i("webview", "WebView resumed");
        }
    }

    /**
     * Clean up and destroy WebView.
     * 清理并销毁 WebView
     */
    public void destroy() {
        if (webView == null) {
            return;
        }

        final Activity activity = activityRef.get();
        if (activity != null) {
            activity.runOnUiThread(() -> {
                try {
                    ((ViewGroup) webView.getParent()).removeView(webView);
                    webView.removeAllViews();
                    webView.destroy();
                    webView = null;
                    Log.i("webview", "WebView destroyed");
                } catch (final Exception e) {
                    Utils.logError("webview", "Failed to destroy WebView", e);
                }
            });
        }
    }

    /**
     * Get the WebView instance.
     * 获取 WebView 实例
     *
     * @return WebView instance
     */
    public WebView getWebView() {
        return webView;
    }

    /**
     * Get the user agent string.
     * 获取用户代理字符串
     *
     * @return User agent
     */
    public String getUserAgent() {
        return userAgent;
    }
}
