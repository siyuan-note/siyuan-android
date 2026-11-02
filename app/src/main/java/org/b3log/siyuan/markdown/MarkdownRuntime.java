package org.b3log.siyuan.markdown;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** A versioned page root independent of MainActivity's workspace resource boot. */
public final class MarkdownRuntime {
    private MarkdownRuntime() { }
    public static synchronized File prepare(Context context) throws Exception {
        android.content.pm.PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        long version = android.os.Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
        File root = new File(context.getFilesDir(), "markdown-runtime/" + version + "-" + info.lastUpdateTime);
        File ready = new File(root, ".ready");
        File parser = new File(root, "stage/protyle/js/lute/lute.min.js");
        File legacyParser = new File(root, "stage/build/markdown/vditor/dist/js/lute/lute.min.js");
        if (!ready.isFile() || !parser.isFile() || legacyParser.exists()) {
            // A failed refresh must never leave a previous completion marker valid.
            if (ready.exists() && !ready.delete()) throw new IOException("unavailable");
            if (!root.isDirectory() && !root.mkdirs()) throw new IOException("space");
            String prefix = root.getCanonicalPath() + File.separator;
            long extracted = 0;
            try (InputStream asset = context.getAssets().open("app.zip"); ZipInputStream zip = new ZipInputStream(asset)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (entry.isDirectory() || !(name.startsWith("stage/build/markdown/") || name.startsWith("appearance/langs/") ||
                            name.equals("stage/protyle/js/lute/lute.min.js"))) continue;
                    File destination = new File(root, name);
                    if (!destination.getCanonicalPath().startsWith(prefix)) throw new IOException("protocol");
                    if (!destination.getParentFile().isDirectory() && !destination.getParentFile().mkdirs()) throw new IOException("space");
                    try (FileOutputStream output = new FileOutputStream(destination)) {
                        byte[] bytes = new byte[65536]; int count;
                        while ((count = zip.read(bytes)) != -1) {
                            extracted += count;
                            if (extracted > 64L * 1024 * 1024) throw new IOException("size");
                            output.write(bytes, 0, count);
                        }
                    }
                }
            }
            for (String name : new String[]{"stage/build/markdown/index.html", "stage/protyle/js/lute/lute.min.js", "appearance/langs/en.json"}) {
                if (!new File(root, name).isFile()) throw new IOException("unavailable");
            }
            if (legacyParser.exists() && !legacyParser.delete()) throw new IOException("unavailable");
            try (FileOutputStream output = new FileOutputStream(ready)) { output.write(1); output.getFD().sync(); }
        }
        return root;
    }
}
