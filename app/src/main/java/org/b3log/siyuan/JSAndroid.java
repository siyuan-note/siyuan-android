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
import android.util.Log;
import android.webkit.JavascriptInterface;

import com.zackratos.ultimatebarx.ultimatebarx.java.UltimateBarX;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

/**
 * JavaScript 接口.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 1.0.1.1, Dec 23, 2021
 * @since 1.0.0
 */
public final class JSAndroid {
    private MainActivity activity;

    public JSAndroid(final MainActivity activity) {
        this.activity = activity;
    }

    private static boolean syncing;

    @JavascriptInterface
    public void returnDesktop() {
        new Thread(this::syncByHand).start();
        activity.moveTaskToBack(true);
    }

    @JavascriptInterface
    public void openExternal(String url) {
        if (url.startsWith("assets/")) {
            url = "http://127.0.0.1:6806/" + url;
        }

        final Uri uri = Uri.parse(url);
        final Intent browserIntent = new Intent(Intent.ACTION_VIEW, uri);
        activity.startActivity(browserIntent);
    }

    @JavascriptInterface
    public void changeStatusBarColor(final String color, final int appearanceMode) {
        activity.runOnUiThread(() -> {
            UltimateBarX.statusBar(activity)
                    .transparent()
                    .light(appearanceMode == 0)
                    .color(Color.parseColor(color.trim()))
                    .apply();

            UltimateBarX.navigationBar(activity)
                    .color(Color.parseColor(color.trim()))
                    .apply();
        });
    }

    private void syncByHand() {
        try {
            if (syncing) {
                return;
            }
            syncing = true;
            final OkHttpClient client = new OkHttpClient();
            final RequestBody body = RequestBody.create(null, new byte[0]);
            final Request request = new Request.Builder().url("http://127.0.0.1:6806/api/sync/performSync").method("POST", body).build();
            client.newCall(request).execute();
        } catch (final Throwable e) {
            Log.e("sync", "sync by hand failed", e);
        } finally {
            syncing = false;
        }
    }
}
