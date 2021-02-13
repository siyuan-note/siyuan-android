package org.b3log.siyuan;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.StrictMode;
import android.webkit.JavascriptInterface;

import java.lang.reflect.Method;

public final class Link {
    private Activity activity;

    public Link(final Activity activity) {
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

        final Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        activity.startActivity(i);
    }
}
