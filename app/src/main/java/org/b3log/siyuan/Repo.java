package org.b3log.siyuan;

import android.app.Activity;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

public class Repo {
    private Activity activity;

    public Repo(Activity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public void sync() {
        Toast.makeText(activity, "Syncing...", Toast.LENGTH_SHORT).show();
    }
}
