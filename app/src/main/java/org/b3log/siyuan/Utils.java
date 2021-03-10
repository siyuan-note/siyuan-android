/*
 * SiYuan - 源于思考，饮水思源
 * Copyright (c) 2020-present, ld246.com
 *
 * 本文件属于思源笔记源码的一部分，云南链滴科技有限公司版权所有。
 */
package org.b3log.siyuan;

import android.app.Activity;
import android.content.res.AssetManager;
import android.util.Log;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.StringTokenizer;

/**
 * 工具类.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 1.0.0.0, Feb 19, 2020
 * @since 1.0.0
 */
public final class Utils {

    /**
     * Executes the specified command.
     *
     * @param cmd the specified command
     * @return execution output, returns {@code null} if execution failed
     */
    public static String exec(final String cmd) {
        final StringTokenizer st = new StringTokenizer(cmd);
        final String[] cmds = new String[st.countTokens()];
        for (int i = 0; st.hasMoreTokens(); i++) {
            cmds[i] = st.nextToken();
        }
        return exec(cmds);
    }

    /**
     * Executes the specified commands.
     *
     * @param cmds the specified commands
     * @return execution output, returns {@code null} if execution failed
     */
    public static String exec(final String[] cmds) {
        try {
            final Process process = new ProcessBuilder(cmds).redirectErrorStream(true).start();
            final StringWriter writer = new StringWriter();
            new Thread(() -> {
                try {
                    IOUtils.copy(process.getInputStream(), writer, "UTF-8");
                } catch (final Exception e) {
                    Log.e("", "Reads input stream failed: " + e.getMessage());
                }
            }).start();

            process.waitFor();
            return writer.toString();
        } catch (final Exception e) {
            Log.e("", "Executes commands [" + Arrays.toString(cmds) + "] failed", e);
            return null;
        }
    }

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
        byte[] buffer = new byte[4096];
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
