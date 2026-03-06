/*
 * SiYuan - 源于思考，饮水思源
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
package org.b3log.siyuan.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.view.DragEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.window.OnBackInvokedDispatcher;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.blankj.utilcode.util.AppUtils;
import com.blankj.utilcode.util.BarUtils;
import com.blankj.utilcode.util.KeyboardUtils;
import com.blankj.utilcode.util.StringUtils;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpPost;
import com.koushikdutta.async.http.body.JSONObjectBody;
import com.zackratos.ultimatebarx.ultimatebarx.java.UltimateBarX;

import org.b3log.siyuan.R;
import org.b3log.siyuan.bridge.JSAndroid;
import org.b3log.siyuan.config.AppConfig;
import org.b3log.siyuan.manager.AppAssetManager;
import org.b3log.siyuan.manager.HttpServerManager;
import org.b3log.siyuan.manager.KernelManager;
import org.b3log.siyuan.manager.SyncManager;
import org.b3log.siyuan.manager.WebViewManager;
import org.b3log.siyuan.service.KeepLiveService;
import org.b3log.siyuan.util.AndroidBug5497Workaround;
import org.b3log.siyuan.util.Utils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

/**
 * 主程序.
 *
 * @author <a href="https://88250.b3log.org">Liang Ding</a>
 * @version 1.1.3.2, Feb 15, 2026
 * @since 1.0.0
 */
public class MainActivity extends AppCompatActivity implements com.blankj.utilcode.util.Utils.OnAppStatusChangedListener {

    private AppAssetManager assetManager;
    private HttpServerManager httpServerManager;
    private KernelManager kernelManager;
    private SyncManager syncManager;
    private WebViewManager webViewManager;
    public WebView webView;
    private ImageView bootLogo;
    private ProgressBar bootProgressBar;
    private TextView bootDetailsText;

    private ValueCallback<Uri[]> uploadMessage;

    static int serverPort = AppConfig.DEFAULT_ASYNC_SERVER_PORT;
    static String webViewVer;
    static String userAgent;

    @Override
    public void onNewIntent(final Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        if (null == intent || null == webViewManager) {
            return;
        }

        // Handle OIDC callback
        final String oidcCallback = intent.getStringExtra("oidcCallback");
        if (!StringUtils.isEmpty(oidcCallback)) {
            webViewManager.handleOidcCallback(oidcCallback);
            return;
        }

        // Handle block URL
        final String blockURL = intent.getStringExtra("blockURL");
        if (!StringUtils.isEmpty(blockURL)) {
            webViewManager.openBlockByURL(blockURL);
        }
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        Log.i("boot", "Create main activity");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Full screen display in landscape mode on Android https://github.com/siyuan-note/siyuan/issues/14448
            getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::goBack);
        }

        // 启动 HTTP Server
        httpServerManager = new HttpServerManager(this);
        serverPort = httpServerManager.startServer();

        // 初始化同步管理器
        syncManager = new SyncManager();

        // 初始化 UI 元素
        initUIElements();

        // 拉起内核
        startKernel();

        // 初始化外观资源
        initAppearance();

        AppUtils.registerAppStatusChangedListener(this);

        // 使用 Chromium 调试 WebView
        if (Utils.isDebugPackageAndMode(this)) {
            this.setWebViewDebuggable(true);
        }

        // 注册工具栏显示/隐藏跟随软键盘状态
        // Fix https://github.com/siyuan-note/siyuan/issues/9765
        Utils.registerSoftKeyboardToolbar(this, webView);

        if (Utils.isTablet(this)) {
            // 平板上隐藏状态栏 Hide the status bar on tablet https://github.com/siyuan-note/siyuan/issues/12204
            BarUtils.setStatusBarVisibility(this, false);
            Utils.setWebViewFocusable(webView, true);
        } else {
            // 沉浸式状态栏设置
            UltimateBarX.statusBarOnly(this).transparent().light(false).color(Color.parseColor(AppConfig.DEFAULT_STATUS_BAR_COLOR)).apply();
            ((ViewGroup) webView.getParent()).setPadding(0, UltimateBarX.getStatusBarHeight(), 0, 0);
        }

        BarUtils.setNavBarVisibility(this, false);

        // Fix https://github.com/siyuan-note/siyuan/issues/9726
        AndroidBug5497Workaround.assistActivity(this);
    }

    private void initUIElements() {
        bootLogo = findViewById(R.id.bootLogo);
        bootProgressBar = findViewById(R.id.progressBar);
        bootDetailsText = findViewById(R.id.bootDetails);
        webView = findViewById(R.id.webView);
        if (!Utils.isTablet(this)) {
            Utils.setWebViewFocusable(webView, false);
        }

        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            final Uri uri = Uri.parse(url);
            final Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);
        });

        webView.setOnDragListener((v, event) -> {
            // 禁用拖拽 https://github.com/siyuan-note/siyuan/issues/6436
            return DragEvent.ACTION_DRAG_ENDED != event.getAction();
        });

        final WebSettings ws = webView.getSettings();
        checkWebViewVer(ws);
        userAgent = ws.getUserAgentString();
        Log.i("boot", "User agent [" + userAgent + "]");
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void showBootIndex() {
        if (null == webView) {
            return;
        }

        // Initialize WebViewManager
        webViewManager = new WebViewManager(this, webView);
        userAgent = webViewManager.initialize();
        MainActivity.webViewVer = userAgent;
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(final WebView view, final WebResourceRequest request) {
                final Uri uri = request.getUrl();
                final String url = uri.toString();
                if (url.contains("127.0.0.1") && !url.contains("openExternal")) {
                    view.loadUrl(url);
                    return true;
                }

                if (uri.getScheme().toLowerCase().startsWith("http")) {
                    final Intent i = new Intent(Intent.ACTION_VIEW, uri);
                    startActivity(i);
                    return true;
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                runOnUiThread(() -> {
                    bootLogo.setVisibility(View.GONE);
                    bootProgressBar.setVisibility(View.GONE);
                    bootDetailsText.setVisibility(View.GONE);
                });
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(final WebView view, final WebResourceRequest request) {
                final Map<String, String> headers = request.getRequestHeaders();
                final String lowerCaseURL = request.getUrl().toString().toLowerCase();
                if (lowerCaseURL.contains("youtube")) {
                    // YouTube 设置 Referer https://github.com/siyuan-note/siyuan/issues/16319
                    headers.put("Referer", "https://b3log.org/siyuan/");
                } else if (lowerCaseURL.contains("qpic")) {
                    // 改进公众号图片加载 https://github.com/siyuan-note/siyuan/issues/16326
                    return handleRequest(request.getUrl().toString(), headers);
                }
                return super.shouldInterceptRequest(view, request);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            private View mCustomView;
            private WebChromeClient.CustomViewCallback mCustomViewCallback;
            private int mOriginalSystemUiVisibility;

            @Override
            public void onShowCustomView(final View view, final WebChromeClient.CustomViewCallback callback) {
                if (mCustomView != null) {
                    callback.onCustomViewHidden();
                    return;
                }

                mCustomView = view;
                mOriginalSystemUiVisibility = getWindow().getDecorView().getSystemUiVisibility();
                mCustomViewCallback = callback;

                final FrameLayout decor = (FrameLayout) getWindow().getDecorView();
                decor.addView(mCustomView, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_FULLSCREEN |
                                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
            }

            @Override
            public void onHideCustomView() {
                final FrameLayout decor = (FrameLayout) getWindow().getDecorView();
                decor.removeView(mCustomView);
                mCustomView = null;
                getWindow().getDecorView().setSystemUiVisibility(mOriginalSystemUiVisibility);
                mCustomViewCallback.onCustomViewHidden();
                mCustomViewCallback = null;
            }

            @Override
            public boolean onShowFileChooser(final WebView mWebView, final ValueCallback<Uri[]> filePathCallback, final FileChooserParams fileChooserParams) {
                if (uploadMessage != null) {
                    uploadMessage.onReceiveValue(null);
                }

                uploadMessage = filePathCallback;

                if (fileChooserParams.isCaptureEnabled()) {
                    if (Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
                        // 不支持 Android 10 以下
                        Utils.showToast(MainActivity.this, "Capture is not supported on your device (Android 10+ required)");
                        uploadMessage = null;
                        return false;
                    }

                    if (ContextCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                        builder.setTitle("权限申请 / Permission Request");
                        builder.setMessage("需要相机权限以拍摄照片并插入到当前文档中 / Camera permission is required to take photos and insert them into the current document");
                        builder.setPositiveButton("同意/Agree", (dialog, which) -> {
                            ActivityCompat.requestPermissions(MainActivity.this, new String[]{android.Manifest.permission.CAMERA}, AppConfig.REQUEST_CAMERA);
                        });
                        builder.setNegativeButton("拒绝/Decline", (dialog, which) -> {
                            Utils.showToast(MainActivity.this, "权限已被拒绝 / Permission denied");
                            uploadMessage = null;
                        });
                        builder.setCancelable(false);
                        builder.create().show();
                        return true;
                    }

                    openCamera();
                    return true;
                }

                final Intent intent = fileChooserParams.createIntent();
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                try {
                    startActivityForResult(intent, AppConfig.REQUEST_SELECT_FILE);
                } catch (final Exception e) {
                    uploadMessage = null;
                    Utils.showToast(MainActivity.this, "Cannot open file chooser");
                    return false;
                }
                return true;
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                request.grant(request.getResources());
            }
        });

        // Register JavaScript interface
        final JSAndroid JSAndroid = new JSAndroid(this);
        webViewManager.registerJavaScriptInterface(JSAndroid);

        // Wait for kernel and load boot URL
        waitFotKernelHttpServing();
        webViewManager.loadBootUrl();

        keepLiveActive = true;
        keepLiveThread = new Thread(this::keepLive, "KeepLiveThread");
        keepLiveThread.start();
    }

    private Handler bootHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(final Message msg) {
            final String cmd = msg.getData().getString("cmd");
            if ("startKernel".equals(cmd)) {
                bootKernel();
            } else {
                showBootIndex();
            }
        }
    };


    private void bootKernel() {
        // Initialize KernelManager
        final String appDir = assetManager.getAppDir();
        final String workspaceBaseDir = getExternalFilesDir(null).getAbsolutePath();
        kernelManager = new KernelManager(this, appDir, workspaceBaseDir,
                                         webViewVer, userAgent, serverPort);

        // Start kernel
        kernelManager.startKernel();

        // Check if already running
        if (kernelManager.isKernelServing()) {
            Log.i("kernel", "Kernel HTTP server is already running");
        }

        bootIndex();
    }

    private WebResourceResponse handleRequest(String urlString, Map<String, String> headers) {
        try {
            final URL url = new URL(urlString);
            final HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if ("referer".equalsIgnoreCase(entry.getKey())) {
                    continue;
                }
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            }
            connection.setRequestProperty("User-Agent", userAgent);

            final String contentType = connection.getContentType();
            final String mimeType = (contentType != null && contentType.contains(";")) ? contentType.split(";")[0] : contentType;
            final String encoding = (contentType != null && contentType.contains("charset=")) ? contentType.split("charset=")[1] : "UTF-8";
            final InputStream is = connection.getInputStream();
            return new WebResourceResponse(mimeType, encoding, is);

        } catch (final Exception e) {
            Utils.logError("webview", "handle request failed for url [" + urlString + "]", e);
            return null; // 返回空后 WebView 会尝试自己加载原始 URL
        }
    }

    private volatile boolean keepLiveActive = true;
    private Thread keepLiveThread;

    /**
     * 通知栏保活。
     */
    private void keepLive() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        while (keepLiveActive) {
            try {
                final Intent intent = new Intent(MainActivity.this, KeepLiveService.class);
                ContextCompat.startForegroundService(this, intent);
                sleep(AppConfig.KEEP_ALIVE_DURATION_MS);
                stopService(intent);
            } catch (final Throwable t) {
                Utils.logError("keeplive", "keep live failed", t);
                break;
            }
        }
    }

    private void startKernel() {
        final Bundle b = new Bundle();
        b.putString("cmd", "startKernel");
        final Message msg = new Message();
        msg.setData(b);
        bootHandler.sendMessage(msg);
    }

    private void bootIndex() {
        final Bundle b = new Bundle();
        b.putString("cmd", "bootIndex");
        final Message msg = new Message();
        msg.setData(b);
        bootHandler.sendMessage(msg);
    }

    /**
     * 等待内核 HTTP 服务伺服。
     */
    private void waitFotKernelHttpServing() {
        if (kernelManager != null) {
            kernelManager.waitForKernelReady();
        }
    }

    private void initAppearance() {
        assetManager = new AppAssetManager(this);
        boolean success = assetManager.initializeIfNeeded((message, percent) -> {
            setBootProgress(message, percent);
        });

        if (!success) {
            Utils.logError("boot", "Failed to initialize appearance assets, exit application", null);
            exit();
        }
    }

    private void setBootProgress(final String text, final int progressPercent) {
        runOnUiThread(() -> {
            bootDetailsText.setText(text);
            bootProgressBar.setProgress(progressPercent);
        });
    }

    private void sleep(final long time) {
        try {
            Thread.sleep(time);
        } catch (final Exception e) {
            Utils.logError("runtime", "sleep failed", e);
        }
    }

    @Override
    public void onBackPressed() {
        goBack();
    }

    private void goBack() {
        if (webViewManager != null) {
            webViewManager.goBack();
        }
    }

    // 用于保存拍照图片的 uri
    private Uri mCameraUri;

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == AppConfig.REQUEST_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
                return;
            }

            Utils.showToast(this, "权限已被拒绝 / Permission denied");
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void openCamera() {
        final Intent captureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (captureIntent.resolveActivity(getPackageManager()) != null) {
            final Uri photoUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, new ContentValues());
            mCameraUri = photoUri;
            if (photoUri != null) {
                captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                captureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                startActivityForResult(captureIntent, AppConfig.REQUEST_CAMERA);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (null == uploadMessage) {
            super.onActivityResult(requestCode, resultCode, intent);
            return;
        }

        if (requestCode == AppConfig.REQUEST_CAMERA) {
            if (RESULT_OK != resultCode) {
                uploadMessage.onReceiveValue(null);
                uploadMessage = null;
                return;
            }

            uploadMessage.onReceiveValue(new Uri[]{mCameraUri});
        } else if (requestCode == AppConfig.REQUEST_SELECT_FILE) {
            // 以下代码参考自 https://github.com/mgks/os-fileup/blob/master/app/src/main/java/mgks/os/fileup/MainActivity.java MIT license

            Uri[] results = null;
            ClipData clipData;
            String stringData;
            try {
                clipData = intent.getClipData();
                stringData = intent.getDataString();
            } catch (Exception e) {
                clipData = null;
                stringData = null;
            }

            if (clipData != null) {
                final int numSelectedFiles = clipData.getItemCount();
                results = new Uri[numSelectedFiles];
                for (int i = 0; i < clipData.getItemCount(); i++) {
                    results[i] = clipData.getItemAt(i).getUri();
                }
            } else {
                try {
                    Bitmap cam_photo = (Bitmap) intent.getExtras().get("data");
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    cam_photo.compress(Bitmap.CompressFormat.JPEG, 100, bytes);
                    stringData = MediaStore.Images.Media.insertImage(this.getContentResolver(), cam_photo, null, null);
                } catch (Exception ignored) {
                }

                if (!StringUtils.isEmpty(stringData)) {
                    results = new Uri[]{Uri.parse(stringData)};
                }
            }

            uploadMessage.onReceiveValue(results);
        }

        uploadMessage = null;
        super.onActivityResult(requestCode, resultCode, intent);
    }


    @Override
    protected void onPause() {
        super.onPause();
        if (webViewManager != null) {
            webViewManager.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webViewManager != null) {
            webViewManager.resume();
        }
    }

    @Override
    protected void onDestroy() {
        Log.i("boot", "Destroy main activity");
        super.onDestroy();
        exit();
    }

    @Override
    public void onForeground(Activity activity) {
        if (syncManager != null) {
            syncManager.startSync();
        }
        if (webViewManager != null) {
            webViewManager.reconnectWebSocket();
        }
    }

    @Override
    public void onBackground(Activity activity) {
        if (syncManager != null) {
            syncManager.startSync();
        }
    }

    @Override
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode) {
        super.onMultiWindowModeChanged(isInMultiWindowMode);
    }

    public void exit() {
        release();

        finishAffinity();
        finishAndRemoveTask();

        try {
            if (kernelManager != null) {
                kernelManager.shutdown();
            }
        } catch (Exception e) {
            Utils.logError("runtime", "exit kernel failed", e);
        }
    }

    private void release() {
        try {
            KeyboardUtils.unregisterSoftInputChangedListener(getWindow());
        } catch (final Exception e) {
            Utils.logError("runtime", "unregister keyboard listener failed", e);
        }

        try {
            AppUtils.unregisterAppStatusChangedListener(this);
        } catch (final Exception e) {
            Utils.logError("runtime", "unregister app status listener failed", e);
        }

        try {
            // The "Remember me" function on the auth page is invalid on the mobile https://github.com/siyuan-note/siyuan/issues/15216
            CookieManager.getInstance().removeSessionCookies(null);
        } catch (final Exception e) {
            Utils.logError("runtime", "clear cookies failed", e);
        }

        try {
            if (webViewManager != null) {
                webViewManager.destroy();
            }
        } catch (final Exception e) {
            Utils.logError("runtime", "destroy webview failed", e);
        }

        try {
            if (null != httpServerManager) {
                httpServerManager.stopServer();
            }
        } catch (final Exception e) {
            Utils.logError("runtime", "stop http server failed", e);
        }

        try {
            keepLiveActive = false;
            if (keepLiveThread != null) {
                keepLiveThread.interrupt();
                keepLiveThread = null;
            }
        } catch (final Exception e) {
            Utils.logError("runtime", "stop keep live thread failed", e);
        }
    }

    private void checkWebViewVer(final WebSettings ws) {
        // Android check WebView version 95+ https://github.com/siyuan-note/siyuan/issues/15147
        final String ua = ws.getUserAgentString();
        if (ua.contains("Chrome/")) {
            final int minVer = 95;
            try {
                final String chromeVersion = ua.split("Chrome/")[1].split(" ")[0];
                if (chromeVersion.contains(".")) {
                    final String[] chromeVersionParts = chromeVersion.split("\\.");
                    webViewVer = chromeVersionParts[0];
                    if (Integer.parseInt(webViewVer) < minVer) {
                        Utils.showToast(this, "WebView version [" + webViewVer + "] is too low, please upgrade to [" + minVer + "] or higher");
                    }
                }
            } catch (final Exception e) {
                Utils.logError("boot", "check WebView version failed", e);
                Utils.showToast(this, "Check WebView version failed: " + e.getMessage());
            }
        }
    }

    public void setWebViewDebuggable(final boolean debuggable) {
        WebView.setWebContentsDebuggingEnabled(debuggable);
    }

}
