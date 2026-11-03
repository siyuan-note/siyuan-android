package org.b3log.siyuan;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

final class FileChooserMimeTypes {
    static String[] normalize(final String[] acceptTypes, final Function<String, String> resolveExtension) {
        final Set<String> mimeTypes = new LinkedHashSet<>();
        if (acceptTypes != null) {
            for (final String group : acceptTypes) {
                if (group == null) continue;
                for (final String entry : group.split(",")) {
                    String type = entry.trim().toLowerCase(Locale.ROOT);
                    if (type.isEmpty()) continue;
                    if (type.startsWith(".")) {
                        type = resolveExtension.apply(type.substring(type.lastIndexOf('.') + 1));
                    }
                    // 未知扩展名需要允许系统浏览文件，避免把扩展名作为 MIME 类型交给 Intent。
                    if (type == null || type.indexOf('/') < 1 || type.endsWith("/") || "*/*".equals(type)) {
                        return new String[]{"*/*"};
                    }
                    mimeTypes.add(type);
                }
            }
        }
        return mimeTypes.isEmpty() ? new String[]{"*/*"} : mimeTypes.toArray(new String[0]);
    }

    private FileChooserMimeTypes() {}
}
