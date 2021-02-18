package org.b3log.siyuan;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.StrictMode;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;

import org.apache.commons.io.FilenameUtils;

import java.lang.reflect.Method;

public final class JSAndroid {
    private Activity activity;

    public JSAndroid(final Activity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public void openExternal(final String url) {
        try {
            final Method m = StrictMode.class.getMethod("disableDeathOnFileUriExposure");
            m.invoke(null);
        } catch (Exception e) {
            e.printStackTrace();
        }

        final MimeTypeMap map = MimeTypeMap.getSingleton();
        final String ext = FilenameUtils.getExtension(url);
        String type = map.getMimeTypeFromExtension(ext);
        if (type == null) {
            type = "application/pdf";
        }
        final Uri uri = Uri.parse(url);
        final Intent intent = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, type).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        activity.startActivity(intent);
    }
}
