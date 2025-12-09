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

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.util.Log;
import android.webkit.JavascriptInterface;

import androidx.core.app.ShareCompat;
import androidx.core.content.FileProvider;

import com.blankj.utilcode.util.BarUtils;
import com.blankj.utilcode.util.KeyboardUtils;
import com.blankj.utilcode.util.StringUtils;
import com.zackratos.ultimatebarx.ultimatebarx.java.UltimateBarX;

import java.io.File;
import java.net.URLDecoder;

import mobile.Mobile;

/**
 * JavaScript 接口.
 *
 * @author <a href="https://88250.b3log.org">Liang Ding</a>
 * @author <a href="https://github.com/Soltus">绛亽</a>
 * @version 1.3.0.2, Dec 9, 2025
 * @since 1.0.0
 */
public final class JSAndroid {
    private MainActivity activity;

    public JSAndroid(final MainActivity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public void exit() {
        this.activity.exit();
    }

    @JavascriptInterface
    public void hideKeyboard() {
        activity.runOnUiThread(() -> {
            KeyboardUtils.hideSoftInput(activity);
            Utils.lastFrontendForceHideKeyboard = System.currentTimeMillis();
            //Utils.logInfo("keyboard", "Hide keyboard");
        });
    }

    @JavascriptInterface
    public String getBlockURL() {
        final String blockURL = activity.getIntent().getStringExtra("blockURL");
        return blockURL;
    }

    @JavascriptInterface
    public void setWebViewDebuggingEnabled(final boolean debuggable) {
        activity.setWebViewDebuggable(debuggable);
    }

    @JavascriptInterface
    public String readClipboard() {
        final ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        final ClipData clipData = clipboard.getPrimaryClip();
        if (null == clipData) {
            return "";
        }

        final ClipData.Item item = clipData.getItemAt(0);
        if (null != item.getUri()) {
            final Uri uri = item.getUri();
            final String url = uri.toString();
            if (url.startsWith("http://127.0.0.1:6806/assets/")) {
                final int idx = url.indexOf("assets/");
                final String asset = url.substring(idx);
                String name = asset.substring(asset.lastIndexOf("/") + 1);
                final int suffixIdx = name.lastIndexOf(".");
                if (0 < suffixIdx) {
                    name = name.substring(0, suffixIdx);
                }
                if (23 < StringUtils.length(name)) {
                    name = name.substring(0, name.length() - 23);
                }
                return "![" + name + "](" + asset + ")";
            }
        }

        final CharSequence text = item.getText();
        if (null == text) {
            return "";
        }
        return text.toString();
    }

    @JavascriptInterface
    public String readHTMLClipboard() {
        final ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        final ClipData clipData = clipboard.getPrimaryClip();
        if (null == clipData) {
            return "";
        }

        final ClipData.Item item = clipData.getItemAt(0);
        String ret = item.getHtmlText();
        if (null == ret) {
            ret = "";
        }
        return ret;
    }

    @JavascriptInterface
    public void writeImageClipboard(final String uri) {
        final ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        final ClipData clip = ClipData.newUri(activity.getContentResolver(), "Copied img from SiYuan", Uri.parse("http://127.0.0.1:6806/" + uri));
        clipboard.setPrimaryClip(clip);
    }

    @JavascriptInterface
    public void writeClipboard(final String content) {
        final ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        final ClipData clip = ClipData.newPlainText("Copied text from SiYuan", content);
        clipboard.setPrimaryClip(clip);
    }

    @JavascriptInterface
    public void writeHTMLClipboard(final String text, final String html) {
        final ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        final ClipData clip = ClipData.newHtmlText("Copied html from SiYuan", text, html);
        clipboard.setPrimaryClip(clip);
    }

    @JavascriptInterface
    public void returnDesktop() {
        final Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
    }

    @JavascriptInterface
    public void exportByDefault(String url) {
        Utils.openByDefaultBrowser(url, activity);
    }

    @JavascriptInterface
    public void print(final String title, final String html) {
        final String filename = title + ".pdf";
        try {
            Utils.print(html, filename, activity);
        } catch (final Exception e) {
            Utils.logError("JSAndroid", "export PDF failed", e);
        }
    }

    @JavascriptInterface
    public int getScreenWidthPx() {
        return activity.getResources().getDisplayMetrics().widthPixels;
    }

    @JavascriptInterface
    public void openExternal(String url) {
        if (!url.startsWith("assets/")) {
            Utils.openByDefaultBrowser(url, activity);
            return;
        }

        // Support opening assets through other apps on the Android https://github.com/siyuan-note/siyuan/issues/10657
        try {
            final String workspacePath = Mobile.getCurrentWorkspacePath();
            final String assetAbsPath = Mobile.getAssetAbsPath(url);
            File asset;
            if (assetAbsPath.contains(workspacePath)) {
                asset = new File(workspacePath, assetAbsPath.substring(workspacePath.length() + 1));
            } else {
                final String decodedUrl = URLDecoder.decode(url, "UTF-8");
                asset = new File(workspacePath, "data/" + decodedUrl);
            }

            if (!asset.exists()) {
                Log.e("js", "File does not exist: " + asset.getAbsolutePath());
                url = "http://127.0.0.1:6806/" + url;
                Utils.openByDefaultBrowser(url, activity);
                return;
            }

            Log.d("js", asset.getAbsolutePath());
            final Uri uri = FileProvider.getUriForFile(activity.getApplicationContext(), BuildConfig.APPLICATION_ID, asset);
            final String type = Mobile.getMimeTypeByExt(asset.getAbsolutePath());
            Intent intent = new ShareCompat.IntentBuilder(activity.getApplicationContext())
                    .setStream(uri)
                    .setType(type)
                    .getIntent()
                    .setAction(Intent.ACTION_VIEW)
                    .setDataAndType(uri, type)
                    .addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            activity.startActivity(intent);
        } catch (Exception e) {
            Utils.logError("JSAndroid", "openExternal failed", e);
        }
    }

    @JavascriptInterface
    public void changeStatusBarColor(final String color, final int appearanceMode) {
        if (Utils.isTablet(MainActivity.userAgent)) {
            return;
        }

        activity.runOnUiThread(() -> {
            UltimateBarX.statusBarOnly(activity).transparent().light(appearanceMode == 0).color(parseColor(color)).apply();
            BarUtils.setNavBarVisibility(activity, false);
        });
    }

    private int parseColor(String str) {
        try {
            str = str.trim();
            if (str.toLowerCase().contains("rgb")) {
                String splitStr = str.substring(str.indexOf('(') + 1, str.indexOf(')'));
                String[] splitString = splitStr.split(",");

                final int[] colorValues = new int[splitString.length];
                for (int i = 0; i < splitString.length; i++) {
                    colorValues[i] = Integer.parseInt(splitString[i].trim());
                }
                return Color.rgb(colorValues[0], colorValues[1], colorValues[2]);
            }
            if (7 > str.length()) {
                // https://stackoverflow.com/questions/10230331/how-to-convert-3-digit-html-hex-colors-to-6-digit-flex-hex-colors
                str = str.replaceAll("#([0-9a-fA-F])([0-9a-fA-F])([0-9a-fA-F])", "#$1$1$2$2$3$3");
            }
            if (9 == str.length() && '#' == str.charAt(0)) {
                // The status bar color on Android is incorrect https://github.com/siyuan-note/siyuan/issues/10278
                // 将 #RRGGBBAA 转换为 #AARRGGBB
                str = "#" + str.substring(7, 9) + str.substring(1, 7);
            }
            return Color.parseColor(str);
        } catch (final Exception e) {
            Utils.logError("js", "parse color [" + str + "] failed", e);
            return Color.parseColor("#212224");
        }
    }


}
