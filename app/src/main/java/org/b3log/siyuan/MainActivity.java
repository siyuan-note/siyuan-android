/*
 * SiYuan - 源于思考，饮水思源
 * Copyright (c) 2020-present, ld246.com
 *
 * 本文件属于思源笔记源码的一部分，云南链滴科技有限公司版权所有。
 */
package org.b3log.siyuan;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import androidk.Androidk;

/**
 * 程序入口.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 1.0.0.0, Feb 19, 2020
 * @since 1.0.0
 */
public class MainActivity extends AppCompatActivity {
    WebView webView;

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
        AndroidBug5497Workaround.assistActivity(this);

        final String siyuan = Utils.getSiYuanDir(this);
        new File(siyuan).mkdirs();
        new File(siyuan + "/data").mkdir();

        try {
            FileUtils.deleteDirectory(new File(siyuan + "/app"));
            FileUtils.deleteDirectory(new File(getFilesDir().getAbsolutePath() + "/lib"));
        } catch (final Exception e) {
            Log.wtf("", "Delete dir [" + siyuan + "/app] failed, exit application", e);
            System.exit(-1);
        }

        Utils.copyAssetFolder(getAssets(), "app", siyuan + "/app");

        final File dataDir = getFilesDir();
        final File libDir = new File(dataDir.getAbsolutePath() + "/lib");
        if (libDir.exists()) {
            FileUtils.deleteQuietly(libDir);
        }
        libDir.mkdir();
        FileUtils.deleteQuietly(new File(dataDir.getAbsolutePath() + "/.unison"));

        Utils.copyAssetFolder(getAssets(), "lib", libDir.getAbsolutePath());
        Androidk.startKernel(siyuan, getApplicationInfo().nativeLibraryDir, dataDir.getAbsolutePath());

        try {
            final File keyDir = new File(dataDir.getAbsolutePath() + "/temp");
            if (keyDir.exists()) {
                FileUtils.deleteQuietly(keyDir);
            }
            keyDir.mkdir();

            final File key = new File(keyDir + "/siyuan.key");
            FileUtils.copyFile(new File(siyuan + "/app/siyuan.key"), key);

            final String unison = getApplicationInfo().nativeLibraryDir + "/libunison.so";
            final String ssh = getApplicationInfo().nativeLibraryDir + "/libssh.so";

            Utils.copyAssetFolder(getAssets(), "lib", libDir.getAbsolutePath());

            final Map<String, String> envs = new HashMap<>();
            envs.put("LD_LIBRARY_PATH", libDir.getAbsolutePath());
            new File(dataDir.getAbsolutePath() + "/home").mkdir();
            envs.put("HOME", dataDir.getAbsolutePath() + "/home");

            new File(siyuan + "/clone").mkdir();

            final String[] cmds = new String[]{
                    unison,
                    "-servercmd", "/home/git/unison-2.48.4/unison user=1602224134353",
                    "-sshcmd", ssh,
                    "-sshargs", "-i " + key.getAbsolutePath() + " -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null",
                    "-ignore", "Name .git",
                    "-batch",
                    "-debug", "all",
                    "-prefer", siyuan + "/clone/",
                    "-clientHostName", "Android-1602224134353",
                    siyuan + "/clone/",
                    "ssh://git@siyuan.b3logfile.com//siyuan/1602224134353/测试笔记本/"
            };


            while (true) {
                String output = Utils.exec(cmds, envs);
                Log.i("", output);
            }
        } catch (final Exception e) {
            e.printStackTrace();
        }

        webView = findViewById(R.id.wv);
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
        final Repo repo = new Repo(this);
        webView.addJavascriptInterface(repo, "Repo");
        final JSAndroid JSAndroid = new JSAndroid(this);
        webView.addJavascriptInterface(JSAndroid, "JSAndroid");
        final WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAppCacheEnabled(false);
        ws.setCacheMode(WebSettings.LOAD_NO_CACHE);
        webView.loadUrl("http://127.0.0.1:6806");
    }

    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_BACK) && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}