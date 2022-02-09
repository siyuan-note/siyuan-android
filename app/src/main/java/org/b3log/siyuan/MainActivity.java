/*
 * SiYuan - 源于思考，饮水思源
 * Copyright (c) 2020-present, ld246.com
 *
 * 本文件属于思源笔记源码的一部分，云南链滴科技有限公司版权所有。
 */
package org.b3log.siyuan;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
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
import android.text.Html;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
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

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
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
        webView = findViewById(R.id.webView);
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
            @Override
            public void handleMessage(final Message msg) {
                if ("startKernel".equals(msg.getData().getString("cmd"))) {
                    bootKernel();
                } else {
                    showBootIndex();
                }
            }
        };

        init();
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
        waitFotKernelHttpServing();
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

    /**
     * 等待内核 HTTP 服务伺服。
     */
    private void waitFotKernelHttpServing() {
        for (int i = 0; i < 500; i++) {
            sleep(10);
            if (Mobile.isHttpServing()) {
                break;
            }
        }
    }

    private void init() {
        if (needUnzipAssets()) {
            bootLogo.setVisibility(View.VISIBLE);
            bootProgressBar.setVisibility(View.VISIBLE);
            bootDetailsText.setVisibility(View.VISIBLE);

            new Thread(() -> {
                final String dataDir = getFilesDir().getAbsolutePath();
                final String appDir = dataDir + "/app";
                final File appVerFile = new File(appDir, "VERSION");

                setBootProgress("Clearing appearance...", 20);
                try {
                    FileUtils.deleteDirectory(new File(appDir));
                } catch (final Exception e) {
                    Log.wtf("", "Delete dir [" + appDir + "] failed, exit application", e);
                    System.exit(-1);
                }

                setBootProgress("Initializing appearance...", 60);
                Utils.unzipAsset(getAssets(), "app.zip", appDir + "/app");

                try {
                    FileUtils.writeStringToFile(appVerFile, version, StandardCharsets.UTF_8);
                } catch (final Exception e) {
                    Log.w("", "Write version failed", e);
                }

                setBootProgress("Booting kernel...", 80);
                final Bundle b = new Bundle();
                b.putString("cmd", "startKernel");
                final Message msg = new Message();
                msg.setData(b);
                handler.sendMessage(msg);
            }).start();
        } else {
            final Bundle b = new Bundle();
            b.putString("cmd", "startKernel");
            final Message msg = new Message();
            msg.setData(b);
            handler.sendMessage(msg);
        }
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

    private boolean needUnzipAssets() {
        final String dataDir = getFilesDir().getAbsolutePath();
        final String appDir = dataDir + "/app";
        final File appDirFile = new File(appDir);
        if (!appDirFile.exists()) {
            // 首次运行弹窗提示用户隐私条款和使用授权
            showAgreements();
        }

        appDirFile.mkdirs();

        boolean ret = true;
        final File appVerFile = new File(appDir, "VERSION");
        if (appVerFile.exists()) {
            try {
                final String ver = FileUtils.readFileToString(appVerFile, StandardCharsets.UTF_8);
                ret = !ver.equals(version);
            } catch (final Exception e) {
                Log.w("", "Check version failed", e);
            }
        }
        return ret;
    }

    private void showAgreements() {
        final TextView msg = new TextView(this);
        msg.setPadding(32, 32, 32, 32);
        msg.setMovementMethod(new ScrollingMovementMethod());
        msg.setText(Html.fromHtml("<div class=\"protyle-wysiwyg protyle-wysiwyg--attr\" style=\"max-width: 800px;margin: 0 auto;\" id=\"preview\"><h2 id=\"隐私条款\">隐私条款</h2>\n" +
                "<p>思源笔记（SiYuan）隐私条款文件，以此声明对用户隐私保护的许诺。最近更新时间为 2022 年 2 月 9 日。</p>\n" +
                "<h3 id=\"是否会收集个人信息或数据-\">是否会收集个人信息或数据？</h3>\n" +
                "<ul>\n" +
                "<li id=\"20220209214329-p8tqr2v\">\n" +
                "<p>不会收集用户个人信息和使用数据（笔记、资源文件、操作记录和程序日志等任何形式的数据）</p>\n" +
                "</li>\n" +
                "<li id=\"20220209214329-6mypire\">\n" +
                "<p>所有数据都保持在用户自己完全控制的设备上</p>\n" +
                "</li>\n" +
                "</ul>\n" +
                "<h3 id=\"开发者信息和联系方式\">开发者信息和联系方式</h3>\n" +
                "<ul>\n" +
                "<li id=\"20220209214329-87gzj6p\">\n" +
                "<p>思源笔记由云南链滴科技有限公司设计并开发</p>\n" +
                "</li>\n" +
                "<li id=\"20220209214329-bi7xhc5\">\n" +
                "<p>可通过 845765@qq.com 进行联系</p>\n" +
                "</li>\n" +
                "</ul>\n" +
                "<h3 id=\"查看隐私条款方式\">查看隐私条款方式</h3>\n" +
                "<ul>\n" +
                "<li id=\"20220209214329-px6s2k2\">\n" +
                "<p>通过访问网页 <a href=\"https://b3log.org/siyuan/privacy.html\">https://b3log.org/siyuan/privacy.html</a></p>\n" +
                "</li>\n" +
                "<li id=\"20220209214329-55rc81s\">\n" +
                "<p>在软件中点击“帮助”，在打开的《思源笔记用户指南》中打开《隐私条款和使用授权》文档</p>\n" +
                "</li>\n" +
                "</ul>\n" +
                "<h2 id=\"用户授权协议\">用户授权协议</h2>\n" +
                "<p>本授权协议适用且仅适用于思源笔记（以下简称为“本软件”），云南链滴科技有限公司对本授权协议拥有最终解释权。</p>\n" +
                "<p>你一旦确认本协议并开始使用本软件，即被视为完全理解并接受本协议的各项条款。电子文本形式的授权协议如同双方书面签署的协议一样，具有完全的和等同的法律效力。</p>\n" +
                "<p>在享有条款授予的权力的同时，受到相关的约束和限制。协议许可范围以外的行为，将直接违反本授权协议并构成侵权，我们有权随时终止授权，责令停止损害，并保留追究相关责任的权力。</p>\n" +
                "<h3 id=\"协议许可的权利\">协议许可的权利</h3>\n" +
                "<ol>\n" +
                "<li id=\"20220209214329-gotjmxk\">\n" +
                "<p>你可以在协议规定的约束和限制范围内使用本软件</p>\n" +
                "</li>\n" +
                "<li id=\"20220209214329-goez9a7\">\n" +
                "<p>你拥有使用本软件所撰写的内容的所有权，并独立承担与这些内容的相关法律义务</p>\n" +
                "</li>\n" +
                "</ol>\n" +
                "<h3 id=\"协议规定的约束和限制\">协议规定的约束和限制</h3>\n" +
                "<ol>\n" +
                "<li id=\"20220209214329-3ytkx0m\">\n" +
                "<p>未经官方许可，禁止在本软件的整体或任何部分基础上发展派生版本、修改版本或第三方版本用于重新分发</p>\n" +
                "</li>\n" +
                "<li id=\"20220209214329-5869ps9\">\n" +
                "<p>如果你未能遵守本协议的条款，你的授权将被终止，所被许可的权利将被收回，并承担相应法律责任</p>\n" +
                "</li>\n" +
                "</ol>\n" +
                "<h3 id=\"有限担保和免责声明\">有限担保和免责声明</h3>\n" +
                "<p>本软件及所附带的文件是作为不提供任何明确的或隐含的赔偿或担保的形式提供的。</p>\n" +
                "</div>"));

        AlertDialog.Builder ab = new AlertDialog.Builder(this);
        ab.setTitle("使用须知 / Notice");
        ab.setView(msg);
        ab.setCancelable(true);
        ab.setPositiveButton("同意 / Agree", null);
        ab.show();
    }
}