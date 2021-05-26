/*
 * SiYuan - 源于思考，饮水思源
 * Copyright (c) 2020-present, ld246.com
 *
 * 本文件属于思源笔记源码的一部分，云南链滴科技有限公司版权所有。
 */
package org.b3log.siyuan;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

import androidk.Androidk;

/**
 * 程序入口.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 1.0.0.1, May 14, 2021
 * @since 1.0.0
 */
public class MainActivity extends AppCompatActivity {
    WebView webView;
    private ProgressBar bootProgressBar;
    private TextView bootDetailsText;
    private int bootProgress;
    private String bootDetails;
    private Handler handler;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().hide();
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        final View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );

        setContentView(R.layout.activity_main);

        bootProgressBar = findViewById(R.id.progressBar);
        bootDetailsText = findViewById(R.id.bootDetails);
        bootDetailsText.setText("Booting...");
        webView = findViewById(R.id.webView);
        webView.setVisibility(View.GONE);

        handler = new Handler(Looper.getMainLooper()) {
            public void handleMessage(final Message msg) {
                showMainUI();
                Log.i("", "show");
            }
        };

        new Thread(this::boot).start();
        new Thread(this::bootProgress).start();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void showMainUI() {
        bootProgressBar.setVisibility(View.GONE);
        bootDetailsText.setVisibility(View.GONE);
        final ImageView bootLogo = findViewById(R.id.bootLogo);
        bootLogo.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        webView.setEnabled(false);
        webView.setClickable(false);
        webView.setLongClickable(false);

        AndroidBug5497Workaround.assistActivity(this);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(final WebView view, final String url) {
                if (url.contains("127.0.0.1")) {
                    view.loadUrl(url);
                } else {
                    final Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(i);
                }
                return true;
            }
        });

        final JSAndroid JSAndroid = new JSAndroid(this);
        webView.addJavascriptInterface(JSAndroid, "JSAndroid");
        final WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAppCacheEnabled(false);
        ws.setCacheMode(WebSettings.LOAD_NO_CACHE);
        ws.setTextZoom(100);
        ws.setUseWideViewPort(true);
        ws.setLoadWithOverviewMode(true);
        webView.loadUrl("http://127.0.0.1:6806");
    }

    private void boot() {
        final String dataDir = getFilesDir().getAbsolutePath();
        final String appDir = dataDir + "/app";
        new File(appDir).mkdirs();
        try {
            FileUtils.deleteDirectory(new File(appDir));
        } catch (final Exception e) {
            Log.wtf("", "Delete dir [" + appDir + "] failed, exit application", e);
            System.exit(-1);
        }

        final String libDir = dataDir + "/lib";
        try {
            FileUtils.deleteDirectory(new File(libDir));
        } catch (final Exception e) {
            Log.wtf("", "Delete dir [" + libDir + "] failed, exit application", e);
            System.exit(-1);
        }

        Utils.copyAssetFolder(getAssets(), "app", appDir + "/app");
        Utils.copyAssetFolder(getAssets(), "lib", libDir);

        final Locale locale = getResources().getConfiguration().locale;
        final String lang = locale.getLanguage() + "_" + locale.getCountry();
        Androidk.setDefaultLang(lang);
        final String localIP = Utils.getIpAddressString();
        final String workspaceDir = getWorkspacePath();
        Androidk.startKernel(appDir, workspaceDir, getApplicationInfo().nativeLibraryDir, dataDir, localIP);
    }

    private void bootProgress() {
        sleep(500);
        while (true) {
            sleep(100);

            HttpURLConnection urlConnection = null;
            try {
                final URL bootProgressURL = new URL("http://127.0.0.1:6806/api/system/boot/progress");
                urlConnection = (HttpURLConnection) bootProgressURL.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.setDefaultUseCaches(false);
                urlConnection.setConnectTimeout(500);
                urlConnection.setReadTimeout(1000);
                final InputStream inputStream = urlConnection.getInputStream();
                final String content = IOUtils.toString(inputStream);
                final JSONObject result = new JSONObject(content);
                final JSONObject data = result.optJSONObject("data");
                bootDetails = data.optString("details");
                bootProgress = data.optInt("progress");
                runOnUiThread(() -> {
                    bootDetailsText.setText(bootDetails);
                    bootProgressBar.setProgress(bootProgress);
                });
                if (100 <= bootProgress) {
                    handler.sendEmptyMessage(0);
                    return;
                }
            } catch (final Throwable e) {
                // ignored
                //e.printStackTrace();
            } finally {
                if (null != urlConnection) {
                    urlConnection.disconnect();
                }
            }
        }
    }

    public String getWorkspacePath() {
        return getExternalFilesDir("siyuan").getAbsolutePath();
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
        if ((keyCode == KeyEvent.KEYCODE_BACK) && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }
}