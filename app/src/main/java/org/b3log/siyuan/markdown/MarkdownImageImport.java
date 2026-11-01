package org.b3log.siyuan.markdown;

import java.io.IOException;
import java.util.Arrays;

/** New-file import only. A failed or uncertain copy never publishes a Markdown reference. */
public final class MarkdownImageImport {
    private MarkdownImageImport() { }
    public static final int MAX_BYTES = 32 * 1024 * 1024;

    public interface Destination<T> {
        T find(String name) throws Exception;
        T create(String name, String mime) throws Exception;
        String name(T file) throws Exception;
        byte[] read(T file) throws Exception;
        void write(T file, byte[] bytes) throws Exception;
        boolean same(T first, T second) throws Exception;
    }

    public static boolean validName(String name) {
        if (name == null || name.isEmpty() || name.length() > 255 || name.equals(".") || name.equals("..")) return false;
        for (int i = 0; i < name.length(); i++) if (name.charAt(i) < 32 || name.charAt(i) == 127 || "/\\:".indexOf(name.charAt(i)) >= 0) return false;
        return MarkdownImages.mime(name) != null;
    }

    public static <T> T copy(byte[] bytes, String name, Destination<T> destination) throws Exception {
        if (!validName(name)) throw new IOException("protocol");
        String mime = MarkdownImages.mime(name);
        if (bytes.length > MAX_BYTES || !MarkdownImages.matches(bytes, mime)) throw new IOException("protocol");
        if (destination.find(name) != null) throw new IOException("occupied");
        T created = destination.create(name, mime);
        if (created == null || !name.equals(destination.name(created)) || destination.read(created).length != 0) throw new IOException("unavailable");
        T located = destination.find(name);
        if (located == null || !destination.same(created, located)) throw new IOException("identity-changed");
        destination.write(created, bytes);
        if (!Arrays.equals(bytes, destination.read(created)) || !name.equals(destination.name(created))) throw new IOException("io");
        located = destination.find(name);
        if (located == null || !destination.same(created, located)) throw new IOException("identity-changed");
        return created;
    }
}
