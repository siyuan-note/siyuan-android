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

import android.view.Display;
import android.view.WindowManager;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
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
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.blankj.utilcode.util.AppUtils;
import com.blankj.utilcode.util.KeyboardUtils;
import com.blankj.utilcode.util.StringUtils;
import com.zackratos.ultimatebarx.ultimatebarx.java.UltimateBarX;

import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.TimeZone;

import mobile.Mobile;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 主程序.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 1.0.4.12, Mar 31, 2023
 * @since 1.0.0
 */
public class MainActivity extends AppCompatActivity implements com.blankj.utilcode.util.Utils.OnAppStatusChangedListener {

    private WebView webView;
    private ImageView bootLogo;
    private ProgressBar bootProgressBar;
    private TextView bootDetailsText;
    private final String version = BuildConfig.VERSION_NAME;
    private String webViewVer;
    private ValueCallback<Uri[]> uploadMessage;
    private static final int REQUEST_SELECT_FILE = 100;

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
        Log.i("boot", "create main activity");
        super.onCreate(savedInstanceState);
        AppUtils.registerAppStatusChangedListener(this);

        setContentView(R.layout.activity_main);
        
        Display display = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display = this.getDisplay(); // 等效于 getApplicationContext().getDisplay() 因为Activity已经实现了Context接口，所以用 this 替换
        } else {
            display = getWindowManager().getDefaultDisplay(); // deprecated as of API 30: Android 11.0 (R)
        }
        if (display != null) {
            Display.Mode[] modes = display.getSupportedModes();
            Display.Mode preferredMode = modes[0];
            for (Display.Mode mode : modes) {
                Log.d("MainActivity Display", "supported mode: " + mode.toString());
                if (mode.getRefreshRate() > preferredMode.getRefreshRate() && mode.getPhysicalWidth() >= preferredMode.getPhysicalWidth()) {
                    preferredMode = mode;
                }
            }
            Log.d("MainActivity Display", "preferredMode mode: " + preferredMode.toString());
            WindowManager.LayoutParams params = getWindow().getAttributes();
            params.preferredDisplayModeId = preferredMode.getModeId();
            getWindow().setAttributes(params);
        }

        // 初始化 UI 元素
        initUIElements();

        // 注册软键盘顶部跟随工具栏
        Utils.registerSoftKeyboardToolbar(this, webView);

        // 沉浸式状态栏设置
        UltimateBarX.statusBarOnly(this).
                transparent().
                light(false).
                color(Color.parseColor("#212224")).
                apply();
        ((ViewGroup) webView.getParent()).setPadding(0, UltimateBarX.getStatusBarHeight(), 0, 0);

        KeyboardUtils.fixAndroidBug5497(this);

        boot();
    }

    private void initUIElements() {
        bootLogo = findViewById(R.id.bootLogo);
        bootProgressBar = findViewById(R.id.progressBar);
        bootDetailsText = findViewById(R.id.bootDetails);
        webView = findViewById(R.id.webView);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(final WebView mWebView, final ValueCallback<Uri[]> filePathCallback, final FileChooserParams fileChooserParams) {
                if (uploadMessage != null) {
                    uploadMessage.onReceiveValue(null);
                }
                uploadMessage = filePathCallback;
                final Intent intent = fileChooserParams.createIntent();
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                try {
                    startActivityForResult(intent, REQUEST_SELECT_FILE);
                } catch (final Exception e) {
                    uploadMessage = null;
                    Toast.makeText(getApplicationContext(), "Cannot open file chooser", Toast.LENGTH_LONG).show();
                    return false;
                }
                return true;
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                request.grant(request.getResources());
            }

        });

        webView.setOnDragListener((v, event) -> {
            // 禁用拖拽 https://github.com/siyuan-note/siyuan/issues/6436
            return DragEvent.ACTION_DRAG_ENDED != event.getAction();
        });

        final WebSettings ws = webView.getSettings();
        checkWebViewVer(ws);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void showBootIndex() {
        webView.setVisibility(View.VISIBLE);
        bootLogo.setVisibility(View.GONE);
        bootProgressBar.setVisibility(View.GONE);
        bootDetailsText.setVisibility(View.GONE);
        final ImageView bootLogo = findViewById(R.id.bootLogo);
        bootLogo.setVisibility(View.GONE);

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
        });

        final JSAndroid JSAndroid = new JSAndroid(this);
        webView.addJavascriptInterface(JSAndroid, "JSAndroid");
        final WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setCacheMode(WebSettings.LOAD_NO_CACHE);
        ws.setTextZoom(100);
        ws.setUseWideViewPort(true);
        ws.setLoadWithOverviewMode(true);
        ws.setUserAgentString("SiYuan/" + version + " https://b3log.org/siyuan " + ws.getUserAgentString());
        waitFotKernelHttpServing();
        webView.loadUrl("http://127.0.0.1:6806/appearance/boot/index.html");

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

    private void bootKernel() {
        if (Mobile.isHttpServing()) {
            Log.i("boot", "kernel HTTP server is running");
            showBootIndex();
            return;
        }

        final String appDir = getFilesDir().getAbsolutePath() + "/app";
        final Locale locale = getResources().getConfiguration().locale;
        final String workspaceBaseDir = getExternalFilesDir(null).getAbsolutePath();
        final String timezone = TimeZone.getDefault().getID();
        new Thread(() -> {
            final String localIPs = Utils.getIPAddressList();
            String lang = locale.getLanguage() + "_" + locale.getCountry();
            if (lang.toLowerCase().contains("cn")) {
                lang = "zh_CN";
            } else {
                lang = "en_US";
            }
            Mobile.startKernel("android", appDir, workspaceBaseDir, timezone, localIPs, lang, Build.VERSION.RELEASE + "/SDK " + Build.VERSION.SDK_INT + "/WebView " + webViewVer);
        }).start();

        final Handler h = new Handler();
        h.postDelayed(() -> {
            final Bundle b = new Bundle();
            b.putString("cmd", "bootIndex");
            final Message msg = new Message();
            msg.setData(b);
            bootHandler.sendMessage(msg);
        }, 100);
    }

    /**
     * 通知栏保活。
     */
    private void keepLive() {
        while (true) {
            try {
                final Intent intent = new Intent(MainActivity.this, KeepLiveService.class);
                ContextCompat.startForegroundService(this, intent);
                sleep(45 * 1000);
                stopService(intent);
            } catch (final Throwable t) {
            }
        }
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

    private void boot() {
        if (needUnzipAssets()) {
            bootLogo.setVisibility(View.VISIBLE);
            bootProgressBar.setVisibility(View.VISIBLE);
            bootDetailsText.setVisibility(View.VISIBLE);

            final String dataDir = getFilesDir().getAbsolutePath();
            final String appDir = dataDir + "/app";
            final File appVerFile = new File(appDir, "VERSION");

            setBootProgress("Clearing appearance...", 20);
            try {
                FileUtils.deleteDirectory(new File(appDir));
            } catch (final Exception e) {
                Log.wtf("boot", "delete dir [" + appDir + "] failed, exit application", e);
                exit();
                return;
            }

            setBootProgress("Initializing appearance...", 60);
            Utils.unzipAsset(getAssets(), "app.zip", appDir + "/app");

            try {
                FileUtils.writeStringToFile(appVerFile, version, StandardCharsets.UTF_8);
            } catch (final Exception e) {
                Log.w("boot", "write version failed", e);
            }

            setBootProgress("Booting kernel...", 80);
        }

        final Bundle b = new Bundle();
        b.putString("cmd", "startKernel");
        final Message msg = new Message();
        msg.setData(b);
        bootHandler.sendMessage(msg);
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
            Log.e("runtime", e.getMessage());
        }
    }

    @Override
    public void onBackPressed() {
        webView.evaluateJavascript("javascript:window.goBack()", null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (null == uploadMessage) {
            super.onActivityResult(requestCode, resultCode, intent);
            return;
        }

        // 以下代码参考自 https://github.com/mgks/os-fileup/blob/master/app/src/main/java/mgks/os/fileup/MainActivity.java MIT license
        if (requestCode == REQUEST_SELECT_FILE) {
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
            uploadMessage = null;
        }

        super.onActivityResult(requestCode, resultCode, intent);
    }

    private boolean needUnzipAssets() {
        final String dataDir = getFilesDir().getAbsolutePath();
        final String appDir = dataDir + "/app";
        final File appDirFile = new File(appDir);
        appDirFile.mkdirs();

        boolean ret = true;
        final File appVerFile = new File(appDir, "VERSION");
        if (appVerFile.exists()) {
            try {
                final String ver = FileUtils.readFileToString(appVerFile, StandardCharsets.UTF_8);
                ret = !ver.equals(version);
            } catch (final Exception e) {
                Log.w("boot", "check version failed", e);
            }
        }
        return ret;
    }


    @Override
    protected void onDestroy() {
        Log.i("boot", "destroy main activity");
        super.onDestroy();
        KeyboardUtils.unregisterSoftInputChangedListener(getWindow());
        AppUtils.unregisterAppStatusChangedListener(this);
        if (null != webView) {
            webView.removeAllViews();
            webView.destroy();
        }
    }

    @Override
    public void onForeground(Activity activity) {
        startSyncData();
    }

    @Override
    public void onBackground(Activity activity) {
        startSyncData();
    }

    private void exit() {
        finishAffinity();
        finishAndRemoveTask();
    }

    private void checkWebViewVer(final WebSettings ws) {
        // Android check WebView version 75+ https://github.com/siyuan-note/siyuan/issues/7840
        final String ua = ws.getUserAgentString();
        if (ua.contains("Chrome/")) {
            final int minVer = 75;
            try {
                final String chromeVersion = ua.split("Chrome/")[1].split(" ")[0];
                if (chromeVersion.contains(".")) {
                    final String[] chromeVersionParts = chromeVersion.split("\\.");
                    webViewVer = chromeVersionParts[0];
                    if (Integer.parseInt(webViewVer) < minVer) {
                        Toast.makeText(this, "WebView version [" + chromeVersion + "] is too low, please upgrade to " + minVer + "+", Toast.LENGTH_LONG).show();
                    }
                }
            } catch (final Exception e) {
                Log.e("boot", "check webview version failed", e);
                Toast.makeText(this, "Check WebView version failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private static boolean syncing;

    public static void startSyncData() {
        new Thread(MainActivity::syncData).start();
    }

    public static void syncData() {
        try {
            if (syncing) {
                Log.i("sync", "data is syncing...");
                return;
            }
            syncing = true;
            final OkHttpClient client = new OkHttpClient();
            final RequestBody body = RequestBody.create(null, new JSONObject().
                    put("mobileSwitch", true).toString());
            final Request request = new Request.Builder().url("http://127.0.0.1:6806/api/sync/performSync").method("POST", body).build();
            final Response response = client.newCall(request).execute();
            response.close();
        } catch (final Throwable e) {
            Log.e("sync", "data sync failed", e);
        } finally {
            syncing = false;
        }
    }
}
