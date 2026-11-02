package org.b3log.siyuan.markdown;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.FileAlreadyExistsException;
import java.util.function.Supplier;

/** Ordinary SiYuan-style identifiers are separate from unguessable capability tokens. */
public final class MarkdownIds {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.ROOT);
    private MarkdownIds() { }

    public static String newId() {
        StringBuilder id = new StringBuilder(LocalDateTime.now().format(TIME)).append('-');
        for (int index = 0; index < 7; index++) id.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        return id.toString();
    }

    public static String token() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        StringBuilder token = new StringBuilder(64);
        for (byte value : bytes) {
            token.append(Character.forDigit((value & 255) >>> 4, 16));
            token.append(Character.forDigit(value & 15, 16));
        }
        return token.toString();
    }

    public static File temporary(File directory, String prefix, String suffix) throws IOException {
        return temporary(directory, prefix, suffix, MarkdownIds::newId);
    }

    static File temporary(File directory, String prefix, String suffix, Supplier<String> ids) throws IOException {
        for (int attempt = 0; attempt < 32; attempt++) {
            File candidate = new File(directory, prefix + ids.get() + suffix);
            try { Files.createFile(candidate.toPath()); return candidate; }
            catch (FileAlreadyExistsException collision) { /* Not ours: never truncate or remove it. */ }
        }
        throw new IOException("identifier collision");
    }
}
