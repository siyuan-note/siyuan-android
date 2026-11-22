package org.b3log.siyuan;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class ClipboardImagePolicyTest {
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 13};

    @Test
    public void acceptsOnlyTheLocalEditorPageAndExactOrigin() {
        final String origin = ClipboardImagePolicy.ORIGIN;
        final String mobile = "/stage/build/mobile/";
        assertTrue(ClipboardImagePolicy.isLocalOrigin(origin));
        assertTrue(ClipboardImagePolicy.isLocalOrigin(origin + "/"));
        assertFalse(ClipboardImagePolicy.isLocalOrigin(origin + mobile));
        assertFalse(ClipboardImagePolicy.isLocalOrigin(origin + "?unexpected=1"));
        assertTrue(ClipboardImagePolicy.isEditorPage(origin + mobile));
        assertTrue(ClipboardImagePolicy.isEditorPage(origin + mobile + "index.html?id=123#editor"));
        assertTrue(ClipboardImagePolicy.isEditorPage(origin + "/stage/build/desktop/?r=abc"));
        for (final String url : new String[]{null, "", "https://127.0.0.1:6806" + mobile,
                "http://localhost:6806" + mobile, "http://127.0.0.1:6807" + mobile,
                "http://127.0.0.1.example:6806" + mobile, "http://untrusted@127.0.0.1:6806" + mobile,
                "https://example.org" + mobile, origin, origin + "/check-auth",
                origin + "/stage/build/mobile/%2e%2e/desktop/", origin + mobile + "other.html"}) {
            assertFalse(ClipboardImagePolicy.isEditorPage(url));
        }
    }

    @Test
    public void requiresAnAllowedRasterMimeAndMatchingSignature() throws Exception {
        assertArrayEquals(PNG, read(PNG, "image/png"));
        final byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        assertArrayEquals(jpeg, read(jpeg, "image/jpeg"));
        final byte[] gif = "GIF89a123456".getBytes(StandardCharsets.US_ASCII);
        assertArrayEquals(gif, read(gif, "image/gif"));
        final byte[] webp = "RIFF1234WEBP".getBytes(StandardCharsets.US_ASCII);
        assertArrayEquals(webp, read(webp, "image/webp"));
        assertNull(read(PNG, "image/jpeg"));
        assertNull(read(PNG, "image/*"));
        assertNull(read(PNG, "image/svg+xml"));
        assertNull(read(Arrays.copyOf(PNG, 7), "image/png"));
        assertNull(read(new byte[0], "image/png"));
        assertNull(ClipboardImagePolicy.readBoundedImage(null, "image/png"));
    }

    @Test
    public void enforcesTheByteLimitEvenWhenLengthMetadataIsUnavailable() throws Exception {
        final byte[] exact = Arrays.copyOf(PNG, ClipboardImagePolicy.MAX_BYTES);
        assertArrayEquals(exact, read(exact, "image/png"));
        assertNull(read(Arrays.copyOf(exact, exact.length + 1), "image/png"));
    }

    private static byte[] read(final byte[] bytes, final String mimeType) throws Exception {
        return ClipboardImagePolicy.readBoundedImage(new ByteArrayInputStream(bytes), mimeType);
    }
}
