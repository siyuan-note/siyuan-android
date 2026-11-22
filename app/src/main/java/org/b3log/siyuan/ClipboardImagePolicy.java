package org.b3log.siyuan;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

final class ClipboardImagePolicy {
    static final String ORIGIN = "http://127.0.0.1:6806";
    static final int MAX_BYTES = 8 * 1024 * 1024;

    private ClipboardImagePolicy() {
    }

    static boolean isLocalOrigin(final String url) {
        final URI uri = localUri(url);
        return uri != null && (uri.getRawPath().isEmpty() || "/".equals(uri.getRawPath()))
                && uri.getRawQuery() == null && uri.getRawFragment() == null;
    }

    static boolean isEditorPage(final String url) {
        final URI uri = localUri(url);
        return uri != null && ("/stage/build/mobile/".equals(uri.getRawPath())
                || "/stage/build/mobile/index.html".equals(uri.getRawPath())
                || "/stage/build/desktop/".equals(uri.getRawPath())
                || "/stage/build/desktop/index.html".equals(uri.getRawPath()));
    }

    private static URI localUri(final String url) {
        if (url == null) {
            return null;
        }
        try {
            final URI uri = URI.create(url);
            return "http".equals(uri.getScheme()) && "127.0.0.1".equals(uri.getHost())
                    && uri.getPort() == 6806 && uri.getRawUserInfo() == null ? uri : null;
        } catch (final IllegalArgumentException ignored) {
            return null;
        }
    }

    static boolean isSupportedMimeType(final String mimeType) {
        return "image/png".equals(mimeType) || "image/jpeg".equals(mimeType)
                || "image/gif".equals(mimeType) || "image/webp".equals(mimeType);
    }

    static byte[] readBoundedImage(final InputStream input, final String mimeType) throws IOException {
        if (input == null || !isSupportedMimeType(mimeType)) {
            return null;
        }
        final byte[] prefix = new byte[12];
        for (int count = 0; count < prefix.length; ) {
            final int read = input.read(prefix, count, prefix.length - count);
            if (read <= 0) {
                return null;
            }
            count += read;
        }
        final boolean matches = switch (mimeType) {
            case "image/png" -> startsWith(prefix, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A);
            case "image/jpeg" -> startsWith(prefix, 0, 0xFF, 0xD8, 0xFF);
            case "image/gif" -> startsWith(prefix, 0, 'G', 'I', 'F', '8', '7', 'a')
                    || startsWith(prefix, 0, 'G', 'I', 'F', '8', '9', 'a');
            case "image/webp" -> startsWith(prefix, 0, 'R', 'I', 'F', 'F')
                    && startsWith(prefix, 8, 'W', 'E', 'B', 'P');
            default -> false;
        };
        if (!matches) {
            return null;
        }
        final ByteArrayOutputStream output = new ByteArrayOutputStream(8192);
        output.write(prefix, 0, prefix.length);
        final byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer, 0, Math.min(buffer.length, MAX_BYTES - output.size() + 1))) > 0) {
            if (read > MAX_BYTES - output.size()) {
                return null;
            }
            output.write(buffer, 0, read);
        }
        return read == -1 ? output.toByteArray() : null;
    }

    private static boolean startsWith(final byte[] bytes, final int offset, final int... signature) {
        if (bytes.length < offset + signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((bytes[offset + i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
