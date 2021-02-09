package org.b3log.siyuan;

import android.app.Activity;
import android.os.Environment;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

import androidk.Androidk;

public final class Repo {
    private Activity activity;

    public Repo(Activity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public void sync() {
        Toast.makeText(activity, Androidk.language(22), Toast.LENGTH_SHORT).show();
        try {
            Androidk.prepareSync();

            final String siyuan = Environment.getExternalStorageDirectory() + "/siyuan";
            final String confStr = FileUtils.readFileToString(new File(siyuan + "/conf/conf.json"));
            final JSONObject conf = new JSONObject(confStr);
            final JSONArray boxes = conf.optJSONArray("boxes");

            final String keyFile = Androidk.genTempKeyFile();
            for (int i = 0; i < boxes.length(); i++) {
                final JSONObject box = boxes.getJSONObject(i);
                if (box.optBoolean("isRemote")) {
                    continue;
                }

                final String localPath = box.optString("path");


                // pull

                Androidk.reloadBox(localPath);

                // push

                Log.i("", "synced box [" + box.optString("name") + "]");
            }

            Androidk.reloadRecentBlocks();
            FileUtils.deleteQuietly(new File(keyFile));
        } catch (final Exception e) {
            Toast.makeText(activity, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

}