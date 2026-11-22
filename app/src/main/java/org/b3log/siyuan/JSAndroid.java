package org.b3log.siyuan;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;
import android.webkit.WebView;

import androidx.core.app.ActivityCompat;
import androidx.activity.result.ActivityResult;
import androidx.core.app.AlarmManagerCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.ShareCompat;
import androidx.core.content.FileProvider;

import com.blankj.utilcode.util.KeyboardUtils;
import com.blankj.utilcode.util.StringUtils;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Locale;

import mobile.Mobile;

/**
 * JavaScript 接口.
 *
 * @author <a href="https://88250.b3log.org">Liang Ding</a>
 * @author <a href="https://github.com/Soltus">绛亽</a>
 * @version 1.6.0.9, Aug 28, 2026
 * @since 1.0.0
 */
public final class JSAndroid {
    private MainActivity activity;
    private final WordSelectionController wordSelection;
    private final Object exportFileLock = new Object();
    private PendingExportFile pendingExportFile;
    private String pendingGalleryAsset;

    private static final class PendingExportFile {
        private final String url;
        private final String requestID;
        private String suggestedName;
        private ExportFileLease lease;

        private PendingExportFile(final String url, final String requestID, final String suggestedName) {
            this.url = url;
            this.requestID = requestID;
            this.suggestedName = suggestedName;
        }
    }

    static final class ExportFileLease {
        private final String id;
        private final String path;
        private final String name;
        private final long size;

        ExportFileLease(final String id, final String path, final String name, final long size) {
            this.id = id;
            this.path = path;
            this.name = name;
            this.size = size;
        }
    }

    public JSAndroid(final MainActivity activity) {
        this.activity = activity;
        this.wordSelection = WordSelectionController.install(activity.webView);
    }

    @JavascriptInterface
    public void logInputEvent(final String details) {
        if (StringUtils.isEmpty(details)) {
            return;
        }
        Utils.logInfo("input", "DOM input event [" + details.substring(0, Math.min(details.length(), 2048)) + "]");
    }

    @JavascriptInterface
    public void cancelNotification(final int id) {
        final Intent intent = new Intent(this.activity, NotificationReceiver.class);
        final PendingIntent pendingIntent = PendingIntent.getBroadcast(this.activity, id, intent, PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pendingIntent != null) {
            final AlarmManager alarmManager = (AlarmManager) this.activity.getSystemService(Context.ALARM_SERVICE);
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
        }

        NotificationManagerCompat.from(this.activity).cancel(id);
    }

    @JavascriptInterface
    public int sendNotification(final String channel, final String title, final String body, final int delayInSeconds) {
        if (ActivityCompat.checkSelfPermission(this.activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Utils.showToast(this.activity, "请允许通知权限以接收通知 / Please allow notification permission to receive notifications");
            openNotificationSettings(this.activity);
            return -1;
        }

        if (!NotificationReceiver.createNotificationChannel(activity, channel)) {
            return -1;
        }

        final int ret = NotificationReceiver.getNextNotificationId();
        if (0 < delayInSeconds) {
            final AlarmManager alarmManager = (AlarmManager) this.activity.getSystemService(Context.ALARM_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                Utils.showToast(this.activity, "请允许精确闹钟权限以接收定时通知（同时需要允许自启动） / Please allow exact alarm permission to receive scheduled notifications (also need to allow auto-start)");
                final Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                intent.setData(Uri.parse("package:" + this.activity.getPackageName()));
                this.activity.startActivity(intent);
                return -1;
            }

            final Intent intent = new Intent(this.activity, NotificationReceiver.class);
            intent.putExtra("channel", channel);
            intent.putExtra("id", ret);
            intent.putExtra("title", title);
            intent.putExtra("body", body);
            final long triggerTime = SystemClock.elapsedRealtime() + (delayInSeconds * 1000L);
            final PendingIntent pendingIntent = PendingIntent.getBroadcast(this.activity, ret, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            AlarmManagerCompat.setExactAndAllowWhileIdle((AlarmManager) this.activity.getSystemService(Context.ALARM_SERVICE), AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime, pendingIntent);
            return ret;
        }

        final PendingIntent resultPendingIntent = NotificationReceiver.createNotificationPendingIntent(this.activity);
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(activity, channel).
                setVisibility(NotificationCompat.VISIBILITY_PRIVATE).
                setPriority(NotificationCompat.PRIORITY_HIGH).
                setSmallIcon(R.drawable.icon).
                setContentTitle(title).
                setContentText(body).
                setAutoCancel(true).
                setContentIntent(resultPendingIntent).
                setCategory(Notification.CATEGORY_REMINDER);
        NotificationManagerCompat.from(this.activity).notify(ret, builder.build());
        return ret;
    }

    static void openNotificationSettings(final Activity activity) {
        activity.runOnUiThread(() -> {
            final Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, activity.getPackageName());
            try {
                activity.startActivity(intent);
            } catch (final ActivityNotFoundException | SecurityException e) {
                // 部分系统未提供通知设置入口，使用应用详情页供用户管理权限。
                final Intent fallback = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                fallback.setData(Uri.parse("package:" + activity.getPackageName()));
                try {
                    activity.startActivity(fallback);
                } catch (final ActivityNotFoundException | SecurityException fallbackError) {
                    Utils.logError("notification", "open notification settings failed", fallbackError);
                }
            }
        });
    }

    @JavascriptInterface
    public void exit() {
        this.activity.exit();
    }

    @JavascriptInterface
    public void openMarkdownEditor() {
        this.activity.runOnUiThread(() -> {
            Intent intent = new Intent(this.activity, MarkdownActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            this.activity.startActivity(intent);
        });
    }

    @JavascriptInterface
    public void hideKeyboard() {
        if (wordSelection != null) {
            wordSelection.cancel();
        }
        activity.runOnUiThread(() -> {
            final WebView webView = activity.findViewById(R.id.webView);
            Utils.hideKeyboardAndToolbar(activity, webView, false);
            KeyboardUtils.hideSoftInput(activity);
        });
    }

    @JavascriptInterface
    public void showKeyboard() {
        activity.runOnUiThread(() -> {
            final WebView webView = activity.findViewById(R.id.webView);
            Utils.showSoftKeyboard(activity, webView);
        });
    }

    @JavascriptInterface
    public void setWebViewFocusable(final boolean focusable) {
        if (wordSelection != null) {
            wordSelection.cancel();
        }
        activity.runOnUiThread(() -> {
            final WebView webView = activity.findViewById(R.id.webView);
            Utils.setWebViewFocusable(webView, focusable);
        });
    }

    @JavascriptInterface
    public String getWordSelection(final String text, final int start, final int end) {
        try {
            return WordSelection.expand(text, start, end);
        } catch (final RuntimeException e) {
            // 系统词典不可用时保留当前选区，不记录正文内容。
            return "";
        }
    }

    @JavascriptInterface
    public boolean prepareWordSelection(final String word, final int startAdjust, final int endAdjust) {
        return wordSelection != null && wordSelection.prepare(word, startAdjust, endAdjust);
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
        activity.runOnUiThread(() -> activity.setWebViewDebuggable(debuggable));
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
        HttpURLConnection connection = null;
        try {
            final InputStream inputStream;
            if (uri.startsWith("assets/")) {
                final String workspacePath = Mobile.getCurrentWorkspacePath();
                final String assetAbsPath = Mobile.getAssetAbsPath(uri);
                final File asset;
                if (assetAbsPath.contains(workspacePath)) {
                    asset = new File(workspacePath, assetAbsPath.substring(workspacePath.length() + 1));
                } else {
                    asset = new File(workspacePath, "data/" + URLDecoder.decode(uri, "UTF-8"));
                }
                inputStream = new FileInputStream(asset);
            } else {
                final String imageURL = uri.startsWith("http://") || uri.startsWith("https://")
                        ? uri : "http://127.0.0.1:6806/" + uri.replaceFirst("^/", "");
                connection = (HttpURLConnection) new URL(imageURL).openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(30000);
                connection.connect();
                inputStream = connection.getInputStream();
            }

            final File clipboardDir = new File(activity.getCacheDir(), "clipboard");
            if (!clipboardDir.exists() && !clipboardDir.mkdirs()) {
                throw new IllegalStateException("create clipboard directory failed");
            }
            final File imageFile = new File(clipboardDir, "image.png");
            try (final InputStream input = inputStream;
                 final FileOutputStream output = new FileOutputStream(imageFile)) {
                final Bitmap bitmap = BitmapFactory.decodeStream(input);
                if (null == bitmap) {
                    throw new IllegalArgumentException("decode clipboard image failed");
                }
                try {
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                        throw new IllegalStateException("encode clipboard image failed");
                    }
                } finally {
                    bitmap.recycle();
                }
            }

            final Uri contentUri = FileProvider.getUriForFile(activity, BuildConfig.APPLICATION_ID, imageFile);
            final ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            final ClipData clip = ClipData.newUri(activity.getContentResolver(), "Copied img from SiYuan", contentUri);
            clipboard.setPrimaryClip(clip);
            cleanupLegacyImageClipboard();
        } catch (final Exception e) {
            Utils.logError("JSAndroid", "write image clipboard failed", e);
        } finally {
            if (null != connection) {
                connection.disconnect();
            }
        }
    }

    private void cleanupLegacyImageClipboard() {
        final File externalFilesDir = activity.getExternalFilesDir(null);
        if (null == externalFilesDir) {
            return;
        }

        final File legacyClipboardDir = new File(externalFilesDir, "clipboard");
        final File[] entries = legacyClipboardDir.listFiles();
        if (null == entries) {
            return;
        }
        if (0 == entries.length) {
            if (!legacyClipboardDir.delete()) {
                Log.w("JSAndroid", "delete legacy clipboard directory failed");
            }
            return;
        }
        if (1 != entries.length || !entries[0].isFile() || !"image.png".equals(entries[0].getName())) {
            return;
        }
        if (!entries[0].delete()) {
            Log.w("JSAndroid", "delete legacy clipboard image failed");
            return;
        }
        if (!legacyClipboardDir.delete()) {
            Log.w("JSAndroid", "delete legacy clipboard directory failed");
        }
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
    public void shareAsset(final String url) {
        if (url == null || !url.startsWith("assets/")) {
            Mobile.showMsg(Mobile.language(475), 5000);
            return;
        }
        new Thread(() -> {
            try {
                final String path = Mobile.getAssetSharePath(url);
                if (StringUtils.isEmpty(path)) {
                    Mobile.showMsg(Mobile.language(475), 5000);
                    return;
                }
                final Uri uri = FileProvider.getUriForFile(activity, BuildConfig.APPLICATION_ID, new File(path));
                final String type = Mobile.getMimeTypeByExt(path);
                final Intent intent = new Intent(Intent.ACTION_SEND)
                        .setTypeAndNormalize(StringUtils.isEmpty(type) ? "application/octet-stream" : type)
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.setClipData(ClipData.newRawUri(null, uri));
                activity.runOnUiThread(() -> {
                    try {
                        activity.startActivity(Intent.createChooser(intent, null));
                    } catch (final Exception e) {
                        Utils.logError("JSAndroid", "share asset failed", e);
                        Mobile.showMsg(Mobile.language(475), 5000);
                    }
                });
            } catch (final Exception e) {
                Utils.logError("JSAndroid", "prepare asset for sharing failed", e);
                Mobile.showMsg(Mobile.language(475), 5000);
            }
        }).start();
    }

    @JavascriptInterface
    public void saveAssetToGallery(final String url) {
        if (url == null || !url.startsWith("assets/")) {
            Mobile.showMsg(Mobile.language(477), 5000);
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || ActivityCompat.checkSelfPermission(
                activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            saveGalleryAsset(url);
            return;
        }
        activity.runOnUiThread(() -> {
            if (null != pendingGalleryAsset) {
                return;
            }
            pendingGalleryAsset = url;
            try {
                activity.launchGalleryPermissionRequest();
            } catch (final Exception e) {
                Utils.logError("JSAndroid", "request gallery permission failed", e);
                onGalleryPermissionResult(false);
            }
        });
    }

    void onGalleryPermissionResult(final boolean granted) {
        final String url = pendingGalleryAsset;
        pendingGalleryAsset = null;
        if (null == url) {
            return;
        }
        if (granted) {
            saveGalleryAsset(url);
        } else {
            Mobile.showMsg(Mobile.language(477), 5000);
        }
    }

    private void saveGalleryAsset(final String url) {
        new Thread(() -> {
            ExportFileLease lease = null;
            try {
                lease = acquireExportFileLease(url, "");
                final int extensionIndex = lease.name.lastIndexOf('.');
                final String mappedType = extensionIndex < 0 ? null : MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                        lease.name.substring(extensionIndex + 1).toLowerCase(Locale.ROOT));
                final String mimeType = null == mappedType ? Mobile.getMimeTypeByExt(lease.path) : mappedType;
                final boolean isVideo = mimeType.startsWith("video/");
                if ((!isVideo && !mimeType.startsWith("image/")) || lease.size == 0) {
                    throw new IllegalStateException("Asset is not a nonempty image or video");
                }
                final String directory = isVideo ? Environment.DIRECTORY_MOVIES : Environment.DIRECTORY_PICTURES;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    final Uri collection = isVideo ? MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                            : MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                    saveExportFileToMediaStore(activity.getContentResolver(), lease, mimeType, directory, collection);
                    Mobile.showMsg(Mobile.language(476), 5000);
                } else {
                    saveLegacyGalleryMedia(lease, mimeType, directory);
                }
            } catch (final Exception e) {
                Utils.logError("JSAndroid", "save gallery asset failed", e);
                Mobile.showMsg(Mobile.language(477), 5000);
            } finally {
                releaseExportFileLease(lease);
            }
        }).start();
    }

    static Uri saveExportFileToMediaStore(final ContentResolver resolver, final ExportFileLease lease,
                                         final String mimeType, final String directory, final Uri collection)
            throws Exception {
        final ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, lease.name);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, directory);
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        final Uri destination = resolver.insert(collection, values);
        if (null == destination) {
            throw new IllegalStateException("Cannot create media destination");
        }
        try {
            final java.io.OutputStream output = resolver.openOutputStream(destination, "w");
            if (null == output) {
                throw new IllegalStateException("Cannot open media destination");
            }
            copyExportFile(lease, output);
            final ContentValues published = new ContentValues();
            published.put(MediaStore.MediaColumns.IS_PENDING, 0);
            if (resolver.update(destination, published, null, null) < 1) {
                throw new IllegalStateException("Cannot publish media destination");
            }
            return destination;
        } catch (final Exception e) {
            try {
                resolver.delete(destination, null, null);
            } catch (final Exception cleanupError) {
                Utils.logError("JSAndroid", "remove incomplete media export failed", cleanupError);
            }
            throw e;
        }
    }

    private void saveLegacyGalleryMedia(final ExportFileLease lease, final String mimeType, final String directory)
            throws Exception {
        final File folder = Environment.getExternalStoragePublicDirectory(directory);
        if (!folder.mkdirs() && !folder.isDirectory()) {
            throw new IllegalStateException("Cannot create gallery directory");
        }
        final File destination = createUniqueExportFile(folder, lease.name);
        try {
            copyExportFile(lease, new FileOutputStream(destination));
            MediaScannerConnection.scanFile(activity, new String[]{destination.getAbsolutePath()},
                    new String[]{mimeType}, (path, uri) -> {
                        if (null == uri) {
                            destination.delete();
                        }
                        Mobile.showMsg(Mobile.language(null == uri ? 477 : 476), 5000);
                    });
        } catch (final Exception e) {
            destination.delete();
            throw e;
        }
    }

    @JavascriptInterface
    public void saveExportFile(final String url) {
        saveExportFileV2(url, "");
    }

    @JavascriptInterface
    public void saveExportFileV2(final String url, final String requestID) {
        if (StringUtils.isEmpty(url)) {
            notifyExportFileResult(requestID, "error", "");
            return;
        }

        String fileName = url.substring(url.lastIndexOf('/') + 1);
        final int queryIdx = fileName.indexOf('?');
        if (-1 != queryIdx) {
            fileName = fileName.substring(0, queryIdx);
        }
        try {
            fileName = URLDecoder.decode(fileName, "UTF-8");
        } catch (final Exception e) {
            Utils.logError("JSAndroid", "decode fileName failed", e);
        }
        if (StringUtils.isEmpty(fileName)) {
            fileName = "export";
        }

        final PendingExportFile request = new PendingExportFile(url, requestID, fileName);
        synchronized (exportFileLock) {
            if (null != pendingExportFile) {
                notifyExportFileResult(requestID, "error", "");
                Mobile.showMsg(Mobile.language(290), 5000);
                return;
            }
            pendingExportFile = request;
        }

        new Thread(() -> {
            try {
                request.lease = acquireExportFileLease(request.url, request.suggestedName);
                request.suggestedName = request.lease.name;
                activity.runOnUiThread(() -> {
                    try {
                        final Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        final String mimeType = Mobile.getMimeTypeByExt(request.suggestedName);
                        intent.setType(StringUtils.isEmpty(mimeType) ? "application/octet-stream" : mimeType);
                        intent.putExtra(Intent.EXTRA_TITLE, request.suggestedName);
                        activity.launchSaveExportFile(intent);
                    } catch (final ActivityNotFoundException e) {
                        clearPendingExportFile(request);
                        saveExportFileToDownloads(request);
                    } catch (final Exception e) {
                        clearPendingExportFile(request);
                        releaseExportFileLease(request);
                        Utils.logError("JSAndroid", "open export file picker failed", e);
                        notifyExportFileResult(request.requestID, "error", "");
                        Mobile.showMsg(Mobile.language(290), 5000);
                    }
                });
            } catch (final Exception e) {
                clearPendingExportFile(request);
                releaseExportFileLease(request);
                Utils.logError("JSAndroid", "prepare export file failed", e);
                notifyExportFileResult(request.requestID, "error", "");
                Mobile.showMsg(Mobile.language(290), 5000);
            }
        }).start();
    }

    void onSaveExportFileResult(final ActivityResult result) {
        final PendingExportFile request;
        synchronized (exportFileLock) {
            request = pendingExportFile;
            pendingExportFile = null;
        }
        if (null == request) {
            return;
        }
        if (Activity.RESULT_OK != result.getResultCode() || null == result.getData()
                || null == result.getData().getData()) {
            releaseExportFileLease(request);
            notifyExportFileResult(request.requestID, "canceled", "");
            return;
        }

        saveExportFileToURI(request, result.getData().getData());
    }

    private void clearPendingExportFile(final PendingExportFile request) {
        synchronized (exportFileLock) {
            if (pendingExportFile == request) {
                pendingExportFile = null;
            }
        }
    }

    private ExportFileLease acquireExportFileLease(final String url, final String suggestedName) throws Exception {
        final String leaseJSON = Mobile.acquireExportFile(url);
        if (StringUtils.isEmpty(leaseJSON)) {
            throw new IllegalStateException("Export file lease is unavailable");
        }
        final JSONObject lease = new JSONObject(leaseJSON);
        final String leaseID = lease.optString("leaseID");
        final String srcPath = lease.optString("path");
        String exportFileName = lease.optString("name", suggestedName);
        if (StringUtils.isEmpty(exportFileName)) {
            exportFileName = suggestedName;
        }
        final long expectedSize = lease.optLong("size", -1);
        if (StringUtils.isEmpty(srcPath) || StringUtils.isEmpty(leaseID) || expectedSize < 0) {
            if (!StringUtils.isEmpty(leaseID)) {
                Mobile.releaseExportFile(leaseID);
            }
            throw new IllegalStateException("Export file lease is invalid");
        }
        return new ExportFileLease(leaseID, srcPath, exportFileName, expectedSize);
    }

    private void releaseExportFileLease(final PendingExportFile request) {
        final ExportFileLease lease = request.lease;
        request.lease = null;
        releaseExportFileLease(lease);
    }

    private void releaseExportFileLease(final ExportFileLease lease) {
        if (null != lease) {
            try {
                Mobile.releaseExportFile(lease.id);
            } catch (final Exception e) {
                Utils.logError("JSAndroid", "release export file failed", e);
            }
        }
    }

    private static long copyExportFile(final ExportFileLease lease, final java.io.OutputStream outputStream) throws Exception {
        long writtenSize = 0;
        try (java.io.OutputStream destination = outputStream;
             java.io.FileInputStream inputStream = new java.io.FileInputStream(lease.path)) {
            final byte[] buffer = new byte[65536];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                destination.write(buffer, 0, bytesRead);
                writtenSize += bytesRead;
            }
            destination.flush();
        }
        if (writtenSize != lease.size) {
            throw new IllegalStateException("Export file size does not match its lease");
        }
        return writtenSize;
    }

    private void saveExportFileToURI(final PendingExportFile request, final Uri destinationURI) {
        new Thread(() -> {
            boolean succeeded = false;
            String savedName = request.suggestedName;
            try {
                final ExportFileLease lease = request.lease;
                if (null == lease) {
                    throw new IllegalStateException("Export file lease is unavailable");
                }
                final java.io.OutputStream outputStream = activity.getContentResolver().openOutputStream(destinationURI, "w");
                if (null == outputStream) {
                    throw new IllegalStateException("Cannot open export destination");
                }
                copyExportFile(lease, outputStream);
                savedName = queryExportFileName(destinationURI, lease.name);
                succeeded = true;
            } catch (final Exception e) {
                Utils.logError("JSAndroid", "saveExportFile failed", e);
                try {
                    activity.getContentResolver().delete(destinationURI, null, null);
                } catch (final Exception ignored) {
                }
            } finally {
                releaseExportFileLease(request);
            }
            if (succeeded) {
                notifyExportFileResult(request.requestID, "success", savedName);
            } else {
                notifyExportFileResult(request.requestID, "error", "");
                Mobile.showMsg(Mobile.language(290), 5000);
            }
        }).start();
    }

    private void saveExportFileToDownloads(final PendingExportFile request) {
        new Thread(() -> {
            File destinationFile = null;
            boolean succeeded = false;
            String savedName = request.suggestedName;
            try {
                final ExportFileLease lease = request.lease;
                if (null == lease) {
                    throw new IllegalStateException("Export file lease is unavailable");
                }
                savedName = lease.name;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    final Uri destinationURI = saveExportFileToMediaStore(activity.getContentResolver(), lease,
                            Mobile.getMimeTypeByExt(lease.name), Environment.DIRECTORY_DOWNLOADS,
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI);
                    savedName = queryExportFileName(destinationURI, lease.name);
                } else {
                    final File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    if (!downloadsDir.exists() && !downloadsDir.mkdirs()) {
                        throw new IllegalStateException("Cannot create downloads directory");
                    }
                    destinationFile = createUniqueExportFile(downloadsDir, lease.name);
                    savedName = destinationFile.getName();
                    copyExportFile(lease, new java.io.FileOutputStream(destinationFile));
                }
                succeeded = true;
            } catch (final Exception e) {
                Utils.logError("JSAndroid", "saveExportFile fallback failed", e);
                try {
                    if (null != destinationFile) {
                        destinationFile.delete();
                    }
                } catch (final Exception ignored) {
                }
            } finally {
                releaseExportFileLease(request);
            }
            if (succeeded) {
                notifyExportFileResult(request.requestID, "success", savedName);
            } else {
                notifyExportFileResult(request.requestID, "error", "");
                Mobile.showMsg(Mobile.language(290), 5000);
            }
        }).start();
    }

    private File createUniqueExportFile(final File directory, final String requestedName) throws Exception {
        String safeName = new File(requestedName).getName();
        if (StringUtils.isEmpty(safeName)) {
            safeName = "export";
        }
        final int extensionIndex = safeName.lastIndexOf('.');
        final String baseName = extensionIndex > 0 ? safeName.substring(0, extensionIndex) : safeName;
        final String extension = extensionIndex > 0 ? safeName.substring(extensionIndex) : "";
        for (int index = 0; index < 10000; index++) {
            final String candidateName = 0 == index ? safeName : baseName + " (" + index + ")" + extension;
            final File candidate = new File(directory, candidateName);
            if (candidate.createNewFile()) {
                return candidate;
            }
        }
        throw new IllegalStateException("Cannot create a unique export destination");
    }

    private String queryExportFileName(final Uri uri, final String fallback) {
        try (Cursor cursor = activity.getContentResolver().query(
                uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (null != cursor && cursor.moveToFirst()) {
                final int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    final String name = cursor.getString(index);
                    if (!StringUtils.isEmpty(name)) {
                        return name;
                    }
                }
            }
        } catch (final Exception e) {
            Utils.logError("JSAndroid", "query export file name failed", e);
        }
        return fallback;
    }

    private void notifyExportFileResult(final String requestID, final String status, final String name) {
        if (StringUtils.isEmpty(requestID)) {
            return;
        }
        try {
            final JSONObject result = new JSONObject();
            result.put("status", status);
            if (!StringUtils.isEmpty(name)) {
                result.put("name", name);
            }
            final String script = "window.handleSaveExportFileResult && window.handleSaveExportFileResult("
                    + JSONObject.quote(requestID) + "," + JSONObject.quote(result.toString()) + ");";
            activity.runOnUiThread(() -> activity.webView.evaluateJavascript(script, null));
        } catch (final Exception e) {
            Utils.logError("JSAndroid", "notify export file result failed", e);
        }
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
    public String getOIDCCallback() {
        final String callback = activity.getIntent().getStringExtra("oidcCallback");
        activity.getIntent().removeExtra("oidcCallback");
        return StringUtils.isEmpty(callback) ? "" : callback;
    }

    @JavascriptInterface
    public void changeStatusBarColor(final String color, final int appearanceMode) {
        activity.runOnUiThread(() -> {
            final int colorVal = parseColor(color);
            SystemBars.setAppearance(activity, appearanceMode == 0);
            // 窗口缩放和 WebView 重绘期间，各层背景均与页面主题保持一致。
            activity.getWindow().setBackgroundDrawable(new ColorDrawable(colorVal));
            activity.findViewById(android.R.id.content).setBackgroundColor(colorVal);
            ((View) activity.webView.getParent()).setBackgroundColor(colorVal);
            activity.webView.setBackgroundColor(colorVal);
            if (activity.keyboardWorkaround != null) activity.keyboardWorkaround.enableNavigationOverlay(activity.webView);
        });
    }

    private int parseColor(String str) {
        try {
            str = str.trim();
            if (9 != str.length() || '#' != str.charAt(0)) {
                throw new IllegalArgumentException("invalid color format");
            }
            // 将 #RRGGBBAA 转换为 #AARRGGBB
            str = "#" + str.substring(7, 9) + str.substring(1, 7);
            return Color.parseColor(str);
        } catch (final Exception e) {
            Utils.logError("js", "parse color [" + str + "] failed", e);
            return Color.parseColor("#212224");
        }
    }
}
