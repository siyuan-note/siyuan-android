package org.b3log.siyuan;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.apache.commons.io.FileUtils;

import java.io.File;

import androidk.Androidk;

public class MainActivity extends AppCompatActivity {
    WebView webView;

    private int requestCode;
    private int grantResults[];

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().hide();
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);
        AndroidBug5497Workaround.assistActivity(this);

        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, requestCode);
        onRequestPermissionsResult(requestCode, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, grantResults);

        final String siyuan = Utils.getSiYuanDir();
        new File(siyuan).mkdirs();
        new File(siyuan + "/data").mkdir();

        try {
            FileUtils.deleteDirectory(new File(siyuan + "/app"));
        } catch (final Exception e) {
            Log.wtf("", "Delete dir [" + siyuan + "/app] failed, exit application", e);
            System.exit(-1);
        }

        Utils.copyAssetFolder(getAssets(), "app", siyuan + "/app");

        Androidk.startKernel(siyuan);

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
        final Link link = new Link(this);
        webView.addJavascriptInterface(link, "Link");
        final WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAppCacheEnabled(false);
        ws.setCacheMode(WebSettings.LOAD_NO_CACHE);
        webView.loadUrl("http://127.0.0.1:6806");
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode, final String permissions[], final int[] grantResults) {
        switch (requestCode) {
            case 1: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // granted
                } else {
                    Log.wtf("", "Request permission failed, exit application");
                    System.exit(-1);
                }
            }
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
}