package org.b3log.siyuan;

import android.content.res.AssetManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

import androidk.Androidk;

public class MainActivity extends AppCompatActivity {
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        String siyuan = Environment.getExternalStorageDirectory() + "/siyuan";
        new File(siyuan).mkdirs();

        try {
            FileUtils.deleteDirectory(new File(siyuan + "/app"));
        } catch (final Exception e) {
            e.printStackTrace();
        }

        copyAssetFolder(getAssets(), "app", siyuan + "/app");

        Androidk.startKernel(siyuan);

        webView = findViewById(R.id.wv);
        webView.setWebViewClient(new WebViewClient());
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setAllowFileAccess(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR) {
            try {
                Method m1 = WebSettings.class.getMethod("setDomStorageEnabled", Boolean.TYPE);
                m1.invoke(ws, Boolean.TRUE);
                Method m2 = WebSettings.class.getMethod("setDatabaseEnabled", Boolean.TYPE);
                m2.invoke(ws, Boolean.TRUE);
                Method m3 = WebSettings.class.getMethod("setDatabasePath", String.class);
                m3.invoke(ws, "/data/data/" + getPackageName() + "/databases/");
                Method m4 = WebSettings.class.getMethod("setAppCacheMaxSize", Long.TYPE);
                m4.invoke(ws, 1024 * 1024 * 8);
                Method m5 = WebSettings.class.getMethod("setAppCachePath", String.class);
                m5.invoke(ws, "/data/data/" + getPackageName() + "/cache/");
                Method m6 = WebSettings.class.getMethod("setAppCacheEnabled", Boolean.TYPE);
                m6.invoke(ws, Boolean.TRUE);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        webView.loadUrl("http://127.0.0.1:6806");
    }

    private static boolean copyAssetFolder(AssetManager assetManager,
                                           String fromAssetPath, String toPath) {
        try {
            String[] files = assetManager.list(fromAssetPath);
            new File(toPath).mkdirs();
            boolean res = true;
            for (String file : files)
                if (file.contains("."))
                    res &= copyAsset(assetManager, fromAssetPath + "/" + file, toPath + "/" + file);
                else
                    res &= copyAssetFolder(assetManager, fromAssetPath + "/" + file, toPath + "/" + file);
            return res;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static boolean copyAsset(AssetManager assetManager,
                                     String fromAssetPath, String toPath) {
        InputStream in;
        OutputStream out;
        try {
            in = assetManager.open(fromAssetPath);
            new File(toPath).createNewFile();
            out = new FileOutputStream(toPath);
            copyFile(in, out);
            in.close();
            out.flush();
            out.close();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    public static String getIpAddressString() {
        try {
            for (Enumeration<NetworkInterface> enNetI = NetworkInterface
                    .getNetworkInterfaces(); enNetI.hasMoreElements(); ) {
                NetworkInterface netI = enNetI.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = netI
                        .getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (inetAddress instanceof Inet4Address && !inetAddress.isLoopbackAddress()) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return "127.0.0.1";
    }
}