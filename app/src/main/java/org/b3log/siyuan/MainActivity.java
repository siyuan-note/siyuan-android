package org.b3log.siyuan;

import android.os.Bundle;
import android.os.Environment;
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
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().hide();
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        String siyuan = Environment.getExternalStorageDirectory() + "/siyuan";
        new File(siyuan).mkdirs();
        new File(siyuan + "/data").mkdir();

        try {
            FileUtils.deleteDirectory(new File(siyuan + "/app"));
        } catch (final Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }

        Utils.copyAssetFolder(getAssets(), "app", siyuan + "/app");

        Androidk.startKernel(siyuan);

        webView = findViewById(R.id.wv);
        webView.setWebViewClient(new WebViewClient());
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        webView.loadUrl("http://127.0.0.1:6806");
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_BACK) && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}