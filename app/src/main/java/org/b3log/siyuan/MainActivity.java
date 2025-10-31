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
package org.b3log.siyuan;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.Settings;
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
import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpPost;
import com.koushikdutta.async.http.body.JSONObjectBody;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.util.Charsets;
import com.zackratos.ultimatebarx.ultimatebarx.java.UltimateBarX;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.TimeZone;

import mobile.Mobile;

/**
 * 主程序.
 *
 * @author <a href="https://88250.b3log.org">Liang Ding</a>
 * @version 1.1.1.11, Sep 30, 2025
 * @since 1.0.0
 */
public class MainActivity extends AppCompatActivity implements com.blankj.utilcode.util.Utils.OnAppStatusChangedListener {

    private AsyncHttpServer server;
    private WebView webView;
    private ImageView bootLogo;
    private ProgressBar bootProgressBar;
    private TextView bootDetailsText;

    private ValueCallback<Uri[]> uploadMessage;
    private static final int REQUEST_SELECT_FILE = 100;
    private static final int REQUEST_CAMERA = 101;
    private static final int LOCAL_SYNC_FOLDER_CODE = 200;

    static int serverPort = 6906;
    static String webViewVer;
    static String userAgent;

    @Override
    public void onNewIntent(final Intent intent) {
        super.onNewIntent(intent);
        if (null != webView) {
            final String blockURL = intent.getStringExtra("blockURL");
            if (!StringUtils.isEmpty(blockURL)) {
                webView.evaluateJavascript("javascript:window.openFileByURL('" + blockURL + "')", null);
            }
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
        startHttpServer();

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

        if (Utils.isTablet(userAgent)) {
            // 平板上隐藏状态栏 Hide the status bar on tablet https://github.com/siyuan-note/siyuan/issues/12204
            BarUtils.setStatusBarVisibility(this, false);
            Log.i("boot", "Hide status bar on tablet");
        } else {
            // 沉浸式状态栏设置
            UltimateBarX.statusBarOnly(this).transparent().light(false).color(Color.parseColor("#1e1e1e")).apply();
            ((ViewGroup) webView.getParent()).setPadding(0, UltimateBarX.getStatusBarHeight(), 0, 0);
        }

        BarUtils.setNavBarVisibility(this, false);

        // Fix https://github.com/siyuan-note/siyuan/issues/9726
        // KeyboardUtils.fixAndroidBug5497(this);
        AndroidBug5497Workaround.assistActivity(this);
    }

    public String getLocalSyncPath() {
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        return prefs.getString("localSyncPath", null);
    }

    public void getStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            Uri uri = Uri.fromParts("package", getPackageName(), null);
            intent.setData(uri);
            startActivity(intent);
        }
    }

    public void pickLocalFileSystemFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(intent, LOCAL_SYNC_FOLDER_CODE);
    }

    private void saveLocalSyncPath(String path) {
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("localSyncPath", path);
        editor.apply();
    }

    private void initUIElements() {
        bootLogo = findViewById(R.id.bootLogo);
        bootProgressBar = findViewById(R.id.progressBar);
        bootDetailsText = findViewById(R.id.bootDetails);
        webView = findViewById(R.id.webView);
        webView.setBackgroundColor(Color.parseColor("#1e1e1e"));

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

        webView.setVisibility(View.VISIBLE);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(final WebView view, final WebResourceRequest request) {
                final Uri uri = request.getUrl();
                final String url = uri.toString();
                if (url.contains("127.0.0.1")) {
                    view.loadUrl(url);
                    return true;
                }

                if (url.contains("siyuan://api/system/exit")) {
                    exit();
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
                            ActivityCompat.requestPermissions(MainActivity.this, new String[]{android.Manifest.permission.CAMERA}, REQUEST_CAMERA);
                        });
                        builder.setNegativeButton("拒绝/Decline", (dialog, which) -> {
                            Utils.showToast(MainActivity.this, "权限已被拒绝 / Permission denied");
                            uploadMessage = null;
                        });
                        builder.setCancelable(false);
                        builder.create().show();
                        return true;
                    }

                    final String[] permissions = {};
                    if (ContextCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(MainActivity.this, permissions, REQUEST_CAMERA);
                        return true;
                    }

                    openCamera();
                    return true;
                }

                final Intent intent = fileChooserParams.createIntent();
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                try {
                    startActivityForResult(intent, REQUEST_SELECT_FILE);
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

        final JSAndroid JSAndroid = new JSAndroid(this);
        webView.addJavascriptInterface(JSAndroid, "JSAndroid");
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        final WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setCacheMode(WebSettings.LOAD_NO_CACHE);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        ws.setTextZoom(100);
        ws.setUseWideViewPort(true);
        ws.setLoadWithOverviewMode(true);
        ws.setUserAgentString("SiYuan/" + Utils.version + " https://b3log.org/siyuan Android " + ws.getUserAgentString());

        waitFotKernelHttpServing();
        webView.loadUrl("http://127.0.0.1:6806/appearance/boot/index.html?v=" + Utils.version);

        new Thread(this::keepLive).start();
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

    private void startHttpServer() {
        if (null != server) {
            server.stop();
        }

        try {
            // 解决乱码问题 https://github.com/koush/AndroidAsync/issues/656#issuecomment-523325452
            final Class<Charsets> charsetClass = Charsets.class;
            Field usAscii = charsetClass.getDeclaredField("US_ASCII");
            usAscii.setAccessible(true);
            usAscii.set(Charsets.class, Charsets.UTF_8);
        } catch (final Exception e) {
            Utils.logError("http", "init charset failed", e);
        }

        server = new AsyncHttpServer();
        server.post("/api/walkDir", (request, response) -> {
            try {
                final long start = System.currentTimeMillis();
                final JSONObject requestJSON = (JSONObject) request.getBody().get();
                final String dir = requestJSON.optString("dir");
                final JSONObject data = new JSONObject();
                final JSONArray files = new JSONArray();
                FileUtils.listFilesAndDirs(new File(dir), TrueFileFilter.INSTANCE, DirectoryFileFilter.DIRECTORY).forEach(file -> {
                    final String path = file.getAbsolutePath();
                    final JSONObject info = new JSONObject();
                    try {
                        info.put("path", path);
                        info.put("name", file.getName());
                        info.put("size", file.length());
                        info.put("updated", file.lastModified());
                        info.put("isDir", file.isDirectory());
                    } catch (final Exception e) {
                        Utils.logError("http", "walk dir failed", e);
                    }
                    files.put(info);
                });
                data.put("files", files);
                final JSONObject responseJSON = new JSONObject().put("code", 0).put("msg", "").put("data", data);
                response.send(responseJSON);
                Utils.logInfo("http", "Walk dir [" + dir + "] in [" + (System.currentTimeMillis() - start) + "] ms");
            } catch (final Exception e) {
                Utils.logError("http", "walk dir failed", e);
                try {
                    response.send(new JSONObject().put("code", -1).put("msg", e.getMessage()));
                } catch (final Exception e2) {
                    Utils.logError("http", "walk dir failed", e2);
                }
            }
        });

        serverPort = getAvailablePort();
        final AsyncServer s = AsyncServer.getDefault();
        if (Utils.isDebugPackageAndMode(this)) {
            // 开发环境绑定所有网卡以便调试
            s.listen(null, serverPort, server.getListenCallback());
        } else {
            // 生产环境绑定 ipv6 回环地址 [::1] 以防止被远程访问
            s.listen(InetAddress.getLoopbackAddress(), serverPort, server.getListenCallback());
        }
        Utils.logInfo("http", "HTTP server is listening on port [" + serverPort + "]");
    }

    private int getAvailablePort() {
        int ret = 6906;
        try {
            ServerSocket s = new ServerSocket(0);
            ret = s.getLocalPort();
            s.close();
        } catch (final Exception e) {
            Utils.logError("http", "get available port failed", e);
        }
        return ret;
    }

    private void bootKernel() {
        Mobile.setHttpServerPort(MainActivity.serverPort);
        if (Mobile.isHttpServing()) {
            Log.i("kernel", "Kernel HTTP server is running");
            bootIndex();
            return;
        }

        new Thread(() -> {
            if (Utils.isCnChannel(this.getPackageManager())) {
                // Apps in Chinese mainland app stores no longer provide AI access settings https://github.com/siyuan-note/siyuan/issues/13051
                Mobile.disableFeature("ai");
            }

            final String appDir = getFilesDir().getAbsolutePath() + "/app";
            final String workspaceBaseDir = getExternalFilesDir(null).getAbsolutePath();
            final String timezone = TimeZone.getDefault().getID();
            final String localIPs = Utils.getIPAddressList();
            final String langCode = Utils.getLanguage();
            Mobile.startKernel("android", appDir, workspaceBaseDir, timezone, localIPs, langCode,
                    Build.VERSION.RELEASE +
                            "/SDK " + Build.VERSION.SDK_INT +
                            "/WebView " + webViewVer +
                            "/Manufacturer " + android.os.Build.MANUFACTURER +
                            "/Brand " + android.os.Build.BRAND +
                            "/UA " + userAgent);
        }).start();

        bootIndex();
    }

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

        while (true) {
            try {
                final Intent intent = new Intent(MainActivity.this, KeepLiveService.class);
                ContextCompat.startForegroundService(this, intent);
                sleep(45 * 1000);
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
        while (true) {
            sleep(10);
            if (Mobile.isHttpServing()) {
                break;
            }
        }
    }

    private void initAppearance() {
        if (needUnzipAssets()) {
            final String appDir = getFilesDir().getAbsolutePath() + "/app";
            final File appVerFile = new File(appDir, "VERSION");

            setBootProgress("Clearing appearance...", 20);
            try {
                FileUtils.deleteDirectory(new File(appDir));
            } catch (final Exception e) {
                Utils.logError("boot", "delete dir [" + appDir + "] failed, exit application", e);
                exit();
                return;
            }

            setBootProgress("Initializing appearance...", 60);
            Utils.unzipAsset(getAssets(), "app.zip", appDir + "/app");

            try {
                FileUtils.writeStringToFile(appVerFile, Utils.versionCode + "", StandardCharsets.UTF_8);
            } catch (final Exception e) {
                Utils.logError("boot", "write version failed", e);
            }

            setBootProgress("Booting kernel...", 80);
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
        webView.evaluateJavascript("javascript:window.goBack ? window.goBack() : window.history.back()", null);
    }

    // 用于保存拍照图片的 uri
    private Uri mCameraUri;

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_CAMERA) {
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
                startActivityForResult(captureIntent, REQUEST_CAMERA);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode == LOCAL_SYNC_FOLDER_CODE && resultCode == RESULT_OK) {
            if (intent != null) {
                Uri treeUri = intent.getData();
                Uri docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri,
                        DocumentsContract.getTreeDocumentId(treeUri));
                String path = AndroidFileUtils.getPath(this, docUri);
                if (path == null || path.isEmpty())
                    Toast.makeText(this, "Error with selected directory",
                            Toast.LENGTH_LONG).show();
                else
                    saveLocalSyncPath(path);
            }
        }

        if (null == uploadMessage) {
            return;
        }

        if (requestCode == REQUEST_CAMERA) {
            if (RESULT_OK != resultCode) {
                uploadMessage.onReceiveValue(null);
                uploadMessage = null;
                return;
            }

            uploadMessage.onReceiveValue(new Uri[]{mCameraUri});
        } else if (requestCode == REQUEST_SELECT_FILE) {
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

    private boolean needUnzipAssets() {
        final String appDir = getFilesDir().getAbsolutePath() + "/app";
        final File appDirFile = new File(appDir);
        appDirFile.mkdirs();

        if (Utils.isDebugPackageAndMode(this)) {
            Log.i("boot", "Always unzip assets in debug mode");
            return true;
        }

        final File appVerFile = new File(appDir, "VERSION");
        if (!appVerFile.exists()) {
            return true;
        }

        boolean ret = true;
        try {
            String ver = FileUtils.readFileToString(appVerFile, StandardCharsets.UTF_8);
            if (StringUtils.isEmpty(ver)) {
                return true;
            }
            ver = ver.trim();
            try {
                return Integer.parseInt(ver) != Utils.versionCode;
            } catch (final NumberFormatException e) {
                return true;
            }
        } catch (final Exception e) {
            Utils.logError("boot", "check version failed", e);
        }
        return ret;
    }

    @Override
    protected void onDestroy() {
        Log.i("boot", "Destroy main activity");
        super.onDestroy();
        release();
    }

    @Override
    public void onForeground(Activity activity) {
        startSyncData();
        if (null != webView) {
            webView.evaluateJavascript("javascript:window.reconnectWebSocket()", null);
        }
    }

    @Override
    public void onBackground(Activity activity) {
        startSyncData();
    }

    @Override
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode) {
        super.onMultiWindowModeChanged(isInMultiWindowMode);
    }

    private void exit() {
        release();
        finishAffinity();
        finishAndRemoveTask();
    }

    private void release() {
        KeyboardUtils.unregisterSoftInputChangedListener(getWindow());
        AppUtils.unregisterAppStatusChangedListener(this);
        CookieManager.getInstance().removeSessionCookies(null); // The "Remember me" function on the auth page is invalid on the mobile https://github.com/siyuan-note/siyuan/issues/15216
        if (null != webView) {
            ((ViewGroup) webView.getParent()).removeView(webView);
            webView.removeAllViews();
        }
        if (null != server) {
            server.stop();
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

    private static boolean syncing;

    public static void startSyncData() {
        new Thread(MainActivity::syncData).start();
    }

    public static void syncData() {
        try {
            if (syncing) {
                Log.i("sync", "Data is syncing...");
                return;
            }
            syncing = true;

            final AsyncHttpPost req = new com.koushikdutta.async.http.AsyncHttpPost("http://127.0.0.1:6806/api/sync/performSync");
            req.setBody(new JSONObjectBody(new JSONObject().put("mobileSwitch", true)));
            AsyncHttpClient.getDefaultInstance().executeJSONObject(req,
                    new com.koushikdutta.async.http.AsyncHttpClient.JSONObjectCallback() {
                        @Override
                        public void onCompleted(Exception e, com.koushikdutta.async.http.AsyncHttpResponse source, JSONObject result) {
                            if (null != e) {
                                Utils.logError("sync", "data sync failed", e);
                            }
                        }
                    });
        } catch (final Throwable e) {
            Utils.logError("sync", "data sync failed", e);
        } finally {
            syncing = false;
        }
    }
}
