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

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import androidx.core.app.ActivityCompat;
import androidx.core.app.AlarmManagerCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
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
 * @version 1.6.0.1, Mar 2, 2026
 * @since 1.0.0
 */
public final class JSAndroid {
    private MainActivity activity;

    public JSAndroid(final MainActivity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public void sendNotification(final String title, final String body, final int delayInSeconds) {
        if (ActivityCompat.checkSelfPermission(this.activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Utils.showToast(this.activity, "请允许通知权限以接收通知 / Please allow notification permission to receive notifications");
            final Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.setData(Uri.parse("package:" + this.activity.getPackageName()));
            this.activity.startActivity(intent);
            return;
        }

        if (0 < delayInSeconds) {
            final AlarmManager alarmManager = (AlarmManager) this.activity.getSystemService(Context.ALARM_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                Utils.showToast(this.activity, "请允许精确闹钟权限以接收定时通知（同时需要允许自启动） / Please allow exact alarm permission to receive scheduled notifications (also need to allow auto-start)");
                final Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                intent.setData(Uri.parse("package:" + this.activity.getPackageName()));
                this.activity.startActivity(intent);
                return;
            }

            final Intent intent = new Intent(this.activity, NotificationReceiver.class);
            intent.putExtra("title", title);
            intent.putExtra("body", body);
            final PendingIntent pendingIntent = PendingIntent.getBroadcast(this.activity, (int) System.currentTimeMillis(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            AlarmManagerCompat.setExactAndAllowWhileIdle((AlarmManager) this.activity.getSystemService(Context.ALARM_SERVICE), AlarmManager.ELAPSED_REALTIME_WAKEUP, delayInSeconds * 1000L, pendingIntent);
            return;
        }

        if (!NotificationReceiver.createNotificationChannel(activity)) {
            return;
        }

        final int notifyId = (int) System.currentTimeMillis();
        final PendingIntent resultPendingIntent = NotificationReceiver.createNotificationPendingIntent(this.activity);
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(activity, NotificationReceiver.NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.icon)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(resultPendingIntent);
        NotificationManagerCompat.from(this.activity).notify(notifyId, builder.build());
    }

    @JavascriptInterface
    public void exit() {
        this.activity.exit();
    }

    @JavascriptInterface
    public void hideKeyboard() {
        activity.runOnUiThread(() -> {
            final WebView webView = activity.findViewById(R.id.webView);
            Utils.hideKeyboardAndToolbar(webView);
            KeyboardUtils.hideSoftInput(activity);
        });
    }

    @JavascriptInterface
    public void showKeyboard() {
        activity.runOnUiThread(() -> {
            final WebView webView = activity.findViewById(R.id.webView);
            Utils.showKeyboardAndToolbar(webView);
            KeyboardUtils.showSoftInput(activity);
        });
    }

    @JavascriptInterface
    public void setWebViewFocusable(final boolean focusable) {
        activity.runOnUiThread(() -> {
            final WebView webView = activity.findViewById(R.id.webView);
            Utils.setWebViewFocusable(webView, focusable);
        });
    }

    @JavascriptInterface
    public String getBlockURL() {
        String blockURL = activity.getIntent().getStringExtra("blockURL");
        if (StringUtils.isEmpty(blockURL)) {
            blockURL = "";
        }
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
    public String readSiYuanHTMLClipboard() {
        final ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        final ClipData clipData = clipboard.getPrimaryClip();
        if (null == clipData) {
            return "";
        }

        if (clipData.getDescription().hasMimeType("text/siyuan") && 2 == clipData.getItemCount()) {
            final ClipData.Item item = clipData.getItemAt(1);
            final CharSequence text = item.getText();
            if (null != text) {
                return text.toString();
            }
        }
        return "";
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
    public void writeSiYuanHTMLClipboard(final String text, final String html, final String siyuanHTML) {
        final ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        final String[] mimeTypes = new String[]{ClipDescription.MIMETYPE_TEXT_PLAIN, ClipDescription.MIMETYPE_TEXT_HTML, "text/siyuan"};
        final ClipData.Item standardItem = new ClipData.Item(text, html, null, null);
        final ClipData.Item siyuanItem = new ClipData.Item(siyuanHTML);
        ClipData clipData = new ClipData("Copied html from SiYuan", mimeTypes, standardItem);
        clipData.addItem(siyuanItem);
        clipboard.setPrimaryClip(clipData);
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
    public void openAuthURL(final String url) {
        if (StringUtils.isEmpty(url) || url.startsWith("#")) {
            Utils.logError("JSAndroid", "openAuthURL failed: invalid url");
            return;
        }

        final Uri uri = Uri.parse(url);
        final String scheme = uri.getScheme();
        if ((!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            Utils.logError("JSAndroid", "openAuthURL failed: only support http/https protocol, not " + scheme);
            return;
        }

        Utils.tryOpenCustomTabs(uri, activity);
    }

    @JavascriptInterface
    public void changeStatusBarColor(final String color, final int appearanceMode) {
        if (Utils.isTablet(activity)) {
            return;
        }

        activity.runOnUiThread(() -> {
            final int colorVal = parseColor(color);
            UltimateBarX.statusBarOnly(activity).transparent().light(appearanceMode == 0).color(colorVal).apply();
            BarUtils.setNavBarVisibility(activity, false);
            activity.webView.getRootView().setBackgroundColor(colorVal);
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
