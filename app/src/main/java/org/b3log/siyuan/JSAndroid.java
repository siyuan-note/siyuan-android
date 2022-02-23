/*
 * SiYuan - 源于思考，饮水思源
 * Copyright (c) 2020-present, b3log.org
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
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
            UltimateBarX.statusBar(activity).
                    transparent().
                    light(appearanceMode == 0).
                    color(Color.parseColor(color.trim())).
                    apply();

            UltimateBarX.navigationBar(activity).
                    light(appearanceMode == 0).
                    color(Color.parseColor(color.trim())).
                    apply();
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
