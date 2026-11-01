package org.b3log.siyuan.markdown;

import java.util.Locale;

/** The launcher must preserve non-Markdown external file routes. */
public final class MarkdownRouting {
    private MarkdownRouting() { }
    public static boolean matches(String action, String scheme, String mime, String displayName) {
        if (!"android.intent.action.VIEW".equals(action) || !"content".equals(scheme)) return false;
        if ("text/markdown".equals(mime) || "text/x-markdown".equals(mime)) return true;
        return displayName != null && displayName.toLowerCase(Locale.ROOT).matches(".*\\.(md|markdown)$");
    }
}
