package org.b3log.siyuan.markdown;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Strict file encoding, separate from editor normalization. */
public final class MarkdownCodec {
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    public static final int MAX_DRAFT_BYTES = 16 * 1024 * 1024;

    public static final class Decoded {
        public final String content;
        public final boolean bom;
        public final String newline;
        public final String baseline;

        private Decoded(String content, boolean bom, String newline, String baseline) {
            this.content = content;
            this.bom = bom;
            this.newline = newline;
            this.baseline = baseline;
        }
    }

    private MarkdownCodec() { }

    public static byte[] read(InputStream input, int maximum) throws IOException {
        if (input == null) throw new IOException("unavailable");
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[65536];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (count == 0) continue;
            if (result.size() > maximum - count) throw new IOException("size");
            result.write(buffer, 0, count);
        }
        return result.toByteArray();
    }

    public static Decoded decode(byte[] bytes) throws IOException {
        if (bytes.length > MAX_BYTES) throw new IOException("size");
        boolean bom = bytes.length >= 3 && bytes[0] == (byte) 0xef && bytes[1] == (byte) 0xbb && bytes[2] == (byte) 0xbf;
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes, bom ? 3 : 0, bytes.length - (bom ? 3 : 0))).toString();
        } catch (CharacterCodingException error) { throw new IOException("encoding", error); }
        if (text.indexOf('\0') >= 0) throw new IOException("encoding");
        int first = -1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\r' || text.charAt(i) == '\n') { first = i; break; }
        }
        String newline = first < 0 || text.charAt(first) == '\n' ? "\n" :
                first + 1 < text.length() && text.charAt(first + 1) == '\n' ? "\r\n" : "\r";
        return new Decoded(text.replace("\r\n", "\n").replace('\r', '\n'), bom, newline, digest(bytes));
    }

    public static byte[] encode(String content, boolean bom, String newline) throws IOException {
        return encode(content, bom, newline, MAX_BYTES);
    }

    public static byte[] encodeDraft(String content) throws IOException {
        return encode(content, false, "\n", MAX_DRAFT_BYTES);
    }

    private static byte[] encode(String content, boolean bom, String newline, int maximum) throws IOException {
        if (!newline.equals("\n") && !newline.equals("\r\n") && !newline.equals("\r")) throw new IOException("protocol");
        if (content.indexOf('\0') >= 0) throw new IOException("encoding");
        if (content.length() > maximum) throw new IOException("size");
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n').replace("\n", newline);
        try {
            ByteBuffer buffer = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap((bom ? "\ufeff" : "") + normalized));
            if (buffer.remaining() > maximum) throw new IOException("size");
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return bytes;
        } catch (CharacterCodingException error) { throw new IOException("encoding", error); }
    }

    public static String digest(byte[] bytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder result = new StringBuilder(64);
            for (byte value : hash) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
