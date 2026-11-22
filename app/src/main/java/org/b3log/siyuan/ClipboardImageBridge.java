package org.b3log.siyuan;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.net.Uri;
import android.util.Base64;
import android.webkit.WebView;

import androidx.webkit.JavaScriptReplyProxy;
import androidx.webkit.WebMessageCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

final class ClipboardImageBridge {
    private static final String NAME = "siyuanClipboardImageNative";
    private static final Pattern REQUEST_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private final Activity activity;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private WebView registered;
    private int navigationGeneration;
    private boolean reading;

    ClipboardImageBridge(final Activity activity) {
        this.activity = activity;
    }

    void register(final WebView view) {
        if (registered == view || !WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            return;
        }
        registered = view;
        navigationGeneration++;
        WebViewCompat.addWebMessageListener(view, NAME, Collections.singleton(ClipboardImagePolicy.ORIGIN),
                this::onMessage);
    }

    void navigating(final WebView view) {
        if (registered == view) {
            navigationGeneration++;
        }
    }

    void close() {
        registered = null;
        navigationGeneration++;
        worker.shutdownNow();
    }

    private boolean isTrustedEditor(final WebView view) {
        return view == registered && !activity.isFinishing() && !activity.isDestroyed()
                && activity.hasWindowFocus() && view.isShown()
                && ClipboardImagePolicy.isEditorPage(view.getUrl());
    }

    private void onMessage(final WebView view, final WebMessageCompat message, final Uri sourceOrigin,
                           final boolean isMainFrame, final JavaScriptReplyProxy reply) {
        if (!isMainFrame || !ClipboardImagePolicy.isLocalOrigin(sourceOrigin.toString()) || !isTrustedEditor(view)
                || message.getType() != WebMessageCompat.TYPE_STRING) {
            return;
        }
        final String input = message.getData();
        if (input == null || input.length() > 128) {
            return;
        }
        final String id;
        try {
            final JSONObject request = new JSONObject(input);
            final Object inputID = request.opt("id");
            if (request.length() != 2 || !"readClipboardImage".equals(request.opt("action"))
                    || !(inputID instanceof String) || !REQUEST_ID.matcher((String) inputID).matches()) {
                return;
            }
            id = (String) inputID;
        } catch (final JSONException ignored) {
            return;
        }

        if (reading) {
            reply.postMessage(response(id, null, null));
            return;
        }
        final ClipboardManager clipboard = activity.getSystemService(ClipboardManager.class);
        final ClipData clip;
        try {
            clip = clipboard == null ? null : clipboard.getPrimaryClip();
        } catch (final RuntimeException ignored) {
            reply.postMessage(response(id, null, null));
            return;
        }
        final Uri uri = findImageUri(clip);
        if (uri == null) {
            reply.postMessage(response(id, null, null));
            return;
        }

        final int navigation = navigationGeneration;
        reading = true;
        // Clipboard providers can block: do not read on the UI thread or queue repeated requests.
        worker.execute(() -> {
            final String result = readImage(id, activity.getContentResolver(), clip.getDescription(), uri);
            activity.runOnUiThread(() -> {
                reading = false;
                if (navigation == navigationGeneration && isTrustedEditor(view)) {
                    reply.postMessage(result);
                }
            });
        });
    }

    private static Uri findImageUri(final ClipData clip) {
        if (clip == null || clip.getItemCount() != 1 || !hasSupportedMimeType(clip.getDescription())) {
            return null;
        }
        final ClipData.Item item = clip.getItemAt(0);
        if (item.getText() != null || item.getHtmlText() != null || item.getIntent() != null) {
            return null;
        }
        final Uri uri = item.getUri();
        return uri != null && ContentResolver.SCHEME_CONTENT.equals(uri.getScheme()) && uri.isHierarchical()
                && uri.getAuthority() != null && !uri.getAuthority().isEmpty() ? uri : null;
    }

    private static boolean hasSupportedMimeType(final ClipDescription description) {
        for (int i = 0; i < description.getMimeTypeCount(); i++) {
            if (ClipboardImagePolicy.isSupportedMimeType(description.getMimeType(i))) {
                return true;
            }
        }
        return false;
    }

    private static String readImage(final String id, final ContentResolver resolver,
                                    final ClipDescription description, final Uri uri) {
        try {
            final String mimeType = resolver.getType(uri);
            if (!ClipboardImagePolicy.isSupportedMimeType(mimeType)) {
                return response(id, null, null);
            }
            for (int i = 0; i < description.getMimeTypeCount(); i++) {
                if (mimeType.equals(description.getMimeType(i))) {
                    try (final InputStream input = resolver.openInputStream(uri)) {
                        final byte[] image = ClipboardImagePolicy.readBoundedImage(input, mimeType);
                        return image == null ? response(id, null, null)
                                : response(id, mimeType, Base64.encodeToString(image, Base64.NO_WRAP));
                    }
                }
            }
        } catch (final IOException | RuntimeException ignored) {
            // Don't log clipboard contents, provider URIs, or provider exception messages.
        }
        return response(id, null, null);
    }

    private static String response(final String id, final String mimeType, final String data) {
        // The request ID, approved MIME type, and standard base64 are constrained before interpolation.
        return data == null ? "{\"id\":\"" + id + "\"}"
                : "{\"id\":\"" + id + "\",\"mimeType\":\"" + mimeType + "\",\"data\":\"" + data + "\"}";
    }
}
