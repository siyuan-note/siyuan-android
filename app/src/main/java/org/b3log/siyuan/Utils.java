package org.b3log.siyuan;

import android.app.Activity;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public final class Utils {

    public static String getSiYuanDir(final Activity activity) {
        return activity.getExternalFilesDir("siyuan").getAbsolutePath();
    }

    public static boolean copyAssetFolder(final AssetManager assetManager, final String fromAssetPath, final String toPath) {
        try {
            final String[] files = assetManager.list(fromAssetPath);
            new File(toPath).mkdirs();
            boolean res = true;
            for (final String file : files) {
                final String[] subFiles = assetManager.list(fromAssetPath + "/" + file);
                if (1 > subFiles.length) {
                    res &= copyAsset(assetManager, fromAssetPath + "/" + file, toPath + "/" + file);
                } else {
                    res &= copyAssetFolder(assetManager, fromAssetPath + "/" + file, toPath + "/" + file);
                }
            }
            return res;
        } catch (final Exception e) {
            Log.e("", "copy asset folder [from=" + fromAssetPath + ", to=" + toPath + "] failed", e);
            return false;
        }
    }

    private static boolean copyAsset(final AssetManager assetManager, final String fromAssetPath, final String toPath) {
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
        } catch (final Exception e) {
            Log.e("", "copy asset [from=" + fromAssetPath + ", to=" + toPath + "] failed", e);
            return false;
        }
    }

    private static void copyFile(final InputStream in, final OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    public static String getIpAddressString() {
        try {
            for (final Enumeration<NetworkInterface> enNetI = NetworkInterface.getNetworkInterfaces(); enNetI.hasMoreElements(); ) {
                final NetworkInterface netI = enNetI.nextElement();
                for (final Enumeration<InetAddress> enumIpAddr = netI.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    final InetAddress inetAddress = enumIpAddr.nextElement();
                    if (inetAddress instanceof Inet4Address && !inetAddress.isLoopbackAddress()) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (final SocketException e) {
            Log.e("", "Get IP failed, returns 127.0.0.1", e);
        }
        return "127.0.0.1";
    }
}
