package org.b3log.siyuan;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;

import org.apache.commons.io.FileUtils;

import java.io.File;

import androidk.Androidk;

public class MainActivity extends AppCompatActivity {
    WebView webView;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().hide();
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
//        getWindow().setNavigationBarContrastEnforced(false);
//        getWindow().setNavigationBarColor(Color.TRANSPARENT);
//        getWindow().setNavigationBarDividerColor(Color.TRANSPARENT);

        setContentView(R.layout.activity_main);
        AndroidBug5497Workaround.assistActivity(this);

        final String siyuan = Utils.getSiYuanDir(this);
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