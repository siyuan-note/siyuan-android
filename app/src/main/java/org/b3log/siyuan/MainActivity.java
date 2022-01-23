/*
 * SiYuan - 源于思考，饮水思源
 * Copyright (c) 2020-present, ld246.com
 *
 * 本文件属于思源笔记源码的一部分，云南链滴科技有限公司版权所有。
 */
package org.b3log.siyuan;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.TimeZone;

import mobile.Mobile;

/**
 * 程序入口.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 1.0.2.1, Jan 22, 2022
 * @since 1.0.0
 */
public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ImageView bootLogo;
    private ProgressBar bootProgressBar;
    private TextView bootDetailsText;
    private Handler handler;
    private String version;

    public ValueCallback<Uri[]> uploadMessage;
    public static final int REQUEST_SELECT_FILE = 100;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final ActionBar actionBar = getSupportActionBar();
        if (null != actionBar) {
            actionBar.hide();
        }
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        setWindowFlag(this, WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS, false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER;
        }
        setContentView(R.layout.activity_main);
        initVersion();

//        final Uri startUri = getIntent().getData();
//        if (null != startUri) {
//            Toast.makeText(getApplicationContext(), startUri.toString(), Toast.LENGTH_LONG).show();
//        }

        bootLogo = findViewById(R.id.bootLogo);
        bootProgressBar = findViewById(R.id.progressBar);
        bootDetailsText = findViewById(R.id.bootDetails);

        new Thread(this::init).start();

        webView = findViewById(R.id.webView);
        webView.setVisibility(View.GONE);
        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public boolean onShowFileChooser(WebView mWebView, ValueCallback<Uri[]> filePathCallback, WebChromeClient.FileChooserParams fileChooserParams) {
                if (uploadMessage != null) {
                    uploadMessage.onReceiveValue(null);
                    uploadMessage = null;
                }
                uploadMessage = filePathCallback;
                final Intent intent = fileChooserParams.createIntent();
                try {
                    startActivityForResult(intent, REQUEST_SELECT_FILE);
                } catch (final Exception e) {
                    uploadMessage = null;
                    Toast.makeText(getApplicationContext(), "Cannot open file chooser", Toast.LENGTH_LONG).show();
                    return false;
                }
                return true;
            }
        });

        handler = new Handler(Looper.getMainLooper()) {
            public void handleMessage(final Message msg) {
                if ("startKernel".equals(msg.getData().getString("cmd"))) {
                    bootKernel();
                } else {
                    showBootIndex();
                }
            }
        };
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void showBootIndex() {
        webView.setVisibility(View.VISIBLE);
        bootLogo.setVisibility(View.GONE);
        bootProgressBar.setVisibility(View.GONE);
        bootDetailsText.setVisibility(View.GONE);
        final ImageView bootLogo = findViewById(R.id.bootLogo);
        bootLogo.setVisibility(View.GONE);

        AndroidBug5497Workaround.assistActivity(this);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(final WebView view, final String url) {
                if (url.contains("127.0.0.1")) {
                    view.loadUrl(url);
                } else if (url.contains("siyuan://api/system/exit")) {
                    finishAndRemoveTask();
                    System.exit(0);
                } else {
                    final Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    final ActivityInfo info = i.resolveActivityInfo(getPackageManager(), PackageManager.MATCH_ALL);
                    if (null == info || !info.exported) {
//                        Toast.makeText(getApplicationContext(), "No application that can handle this link [" + url + "]", Toast.LENGTH_LONG).show();
                    } else {
                        startActivity(i);
                    }
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

        for (int i = 0; i < 200; i++) { // 等待内核 HTTP 服务伺服
            sleep(50);
            if (Mobile.isHttpServing()) {
                break;
            }
        }

        webView.loadUrl("http://127.0.0.1:6806/appearance/boot/index.html");
        new Thread(this::keepLive).start();
    }

    private void bootKernel() {
        final String dataDir = getFilesDir().getAbsolutePath();
        final String appDir = dataDir + "/app";

        final Locale locale = getResources().getConfiguration().locale;
        final String lang = locale.getLanguage() + "_" + locale.getCountry();
        Mobile.setDefaultLang(lang);
        final String localIP = Utils.getIpAddressString();
        final String workspaceDir = getWorkspacePath();
        final String timezone = TimeZone.getDefault().getID();
        new Thread(() -> {
            Mobile.startKernel("android", appDir, workspaceDir, getApplicationInfo().nativeLibraryDir, dataDir, localIP, timezone);
        }).start();
        sleep(100);
        final Bundle b = new Bundle();
        b.putString("cmd", "bootIndex");
        final Message msg = new Message();
        msg.setData(b);
        handler.sendMessage(msg);
    }

    private void init() {
        final String dataDir = getFilesDir().getAbsolutePath();
        final String appDir = dataDir + "/app";
        new File(appDir).mkdirs();

        boolean needUnzipAssets = true;
        final File appVerFile = new File(appDir, "VERSION");
        if (appVerFile.exists()) {
            try {
                final String ver = FileUtils.readFileToString(appVerFile, StandardCharsets.UTF_8);
                needUnzipAssets = !ver.equals(version);
            } catch (final Exception e) {
                Log.w("", "Check version failed", e);
            }
        }

        if (needUnzipAssets) {
            bootLogo.setVisibility(View.VISIBLE);
            bootProgressBar.setVisibility(View.VISIBLE);
            bootDetailsText.setVisibility(View.VISIBLE);
            setBootProgress("Initializing appearance...", 20);
            try {
                FileUtils.deleteDirectory(new File(appDir));
            } catch (final Exception e) {
                Log.wtf("", "Delete dir [" + appDir + "] failed, exit application", e);
                System.exit(-1);
            }
            Utils.unzipAsset(getAssets(), "app.zip", appDir + "/app");
            setBootProgress("Initializing libraries...", 50);
            final String libDir = dataDir + "/lib";
            try {
                FileUtils.deleteDirectory(new File(libDir));
            } catch (final Exception e) {
                Log.wtf("", "Delete dir [" + libDir + "] failed, exit application", e);
                System.exit(-1);
            }
            Utils.unzipAsset(getAssets(), "lib.zip", libDir);

            try {
                FileUtils.writeStringToFile(appVerFile, version, StandardCharsets.UTF_8);
            } catch (final Exception e) {
                Log.w("", "Write version failed", e);
            }

            setBootProgress("Booting kernel...", 80);
        }

        final Bundle b = new Bundle();
        b.putString("cmd", "startKernel");
        final Message msg = new Message();
        msg.setData(b);
        handler.sendMessage(msg);
    }

    /**
     * 通知栏保活。
     */
    private void keepLive() {
        while (true) {
            try {
                final Intent intent = new Intent(MainActivity.this, WhiteService.class);
                ContextCompat.startForegroundService(this, intent);
                sleep(45 * 1000);
                stopService(intent);
            } catch (final Throwable t) {
            }
        }
    }

    private void setBootProgress(final String text, final int progressPercent) {
        runOnUiThread(() -> {
            bootDetailsText.setText(text);
            bootProgressBar.setProgress(progressPercent);
        });
    }

    private String getWorkspacePath() {
        return getExternalFilesDir("siyuan").getAbsolutePath();
    }

    private void initVersion() {
        try {
            final PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            version = pInfo.versionName;
        } catch (final PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void sleep(final long time) {
        try {
            Thread.sleep(time);
        } catch (final Exception e) {
            Log.e("", e.getMessage());
        }
    }

    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            webView.evaluateJavascript("javascript:window.goBack()", null);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        webView.evaluateJavascript("javascript:window.goBack()", null);
    }

    @Override
    public void onConfigurationChanged(final Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        AndroidBug5497Workaround.assistActivity(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == REQUEST_SELECT_FILE) {
            if (uploadMessage == null) {
                return;
            }
            uploadMessage.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, intent));
            uploadMessage = null;
        }
        super.onActivityResult(requestCode, resultCode, intent);
    }

    public static void setWindowFlag(final Activity activity, final int bits, boolean on) {
        final Window win = activity.getWindow();
        WindowManager.LayoutParams winParams = win.getAttributes();
        if (on) {
            winParams.flags |= bits;
        } else {
            winParams.flags &= ~bits;
        }
        win.setAttributes(winParams);
    }
}