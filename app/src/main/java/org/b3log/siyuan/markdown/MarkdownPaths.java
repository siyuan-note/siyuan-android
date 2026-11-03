package org.b3log.siyuan.markdown;

import android.content.Context;
import java.io.File;
import java.io.IOException;

public final class MarkdownPaths {
    private MarkdownPaths() { }

    public static File directory(Context context) throws IOException {
        File base = context.getExternalFilesDir(null);
        if (base == null) throw new IOException("unavailable");
        return new File(base, "home/.config/siyuan/markdown");
    }
}
