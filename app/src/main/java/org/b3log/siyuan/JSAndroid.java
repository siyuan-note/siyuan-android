package org.b3log.siyuan;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.StrictMode;
import android.webkit.JavascriptInterface;

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

        final Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        activity.startActivity(i);
    }

    @JavascriptInterface
    public void setNavigationBarColor(final String color) {
        activity.getWindow().setNavigationBarColor(Color.parseColor(color));
    }
}
