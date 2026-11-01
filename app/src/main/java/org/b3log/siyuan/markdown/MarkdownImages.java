package org.b3log.siyuan.markdown;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Only inert raster image formats are served by the document resource origin. */
public final class MarkdownImages {
    private MarkdownImages() { }

    public static String mime(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".avif")) return "image/avif";
        if (lower.endsWith(".bmp")) return "image/bmp";
        return null;
    }

    private static boolean ascii(byte[] bytes, int offset, String text) {
        byte[] signature = text.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length < offset + signature.length) return false;
        for (int i = 0; i < signature.length; i++) if (bytes[offset + i] != signature[i]) return false;
        return true;
    }

    public static boolean matches(byte[] bytes, String mime) {
        if (bytes == null || mime == null) return false;
        switch (mime) {
            case "image/png": return bytes.length >= 8 && (bytes[0] & 255) == 137 && ascii(bytes, 1, "PNG\r\n\u001a\n");
            case "image/jpeg": return bytes.length >= 3 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216 && (bytes[2] & 255) == 255;
            case "image/gif": return ascii(bytes, 0, "GIF87a") || ascii(bytes, 0, "GIF89a");
            case "image/webp": return ascii(bytes, 0, "RIFF") && ascii(bytes, 8, "WEBP");
            case "image/bmp": return bytes.length >= 14 && ascii(bytes, 0, "BM");
            case "image/avif":
                if (bytes.length < 16 || !ascii(bytes, 4, "ftyp")) return false;
                long size = ((long) (bytes[0] & 255) << 24) | ((long) (bytes[1] & 255) << 16) | ((long) (bytes[2] & 255) << 8) | (bytes[3] & 255);
                if (size < 16 || size > Math.min(bytes.length, 4096)) return false;
                if (ascii(bytes, 8, "avif") || ascii(bytes, 8, "avis")) return true;
                for (int i = 16; i + 4 <= size; i += 4) if (ascii(bytes, i, "avif") || ascii(bytes, i, "avis")) return true;
                return false;
            default: return false;
        }
    }
}
