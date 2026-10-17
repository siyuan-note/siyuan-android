package org.b3log.siyuan;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Stream;

public final class ShorthandDraftRecoveryTest {
    public static void main(String[] args) throws Exception {
        final Path root = Files.createTempDirectory("shorthand-draft-recovery-");
        try {
            checkMissingDraft(root.resolve("missing"));
            checkNewDraft(root.resolve("new"), "未提交的草稿\nsecond line".getBytes(StandardCharsets.UTF_8));
            checkNewDraft(root.resolve("empty"), new byte[0]);
            checkNewDraft(root.resolve("partial"), new byte[]{(byte) 0xE8, (byte) 0x8D});
            checkExistingDraft(root.resolve("base"), true, false);
            checkExistingDraft(root.resolve("backup"), false, true);
            checkExistingDraft(root.resolve("base-and-backup"), true, true);
            checkInvalidNewFile(root.resolve("invalid"));
            checkRecoveryFailure(root.resolve("failure"));
            System.out.println("Shorthand draft recovery cases passed");
        } finally {
            try (final Stream<Path> paths = Files.walk(root)) {
                for (final Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) {
                    Files.delete(path);
                }
            }
        }
    }

    private static void checkMissingDraft(final Path dir) throws IOException {
        final Path base = dir.resolve("draft.md");
        ShorthandDraftRecovery.recover(base.toFile());
        check(!Files.exists(dir), "Missing draft must not create files");
    }

    private static void checkNewDraft(final Path dir, final byte[] content) throws IOException {
        Files.createDirectories(dir);
        final Path base = dir.resolve("draft.md");
        final Path pending = dir.resolve("draft.md.new");
        Files.write(pending, content);

        ShorthandDraftRecovery.recover(base.toFile());
        checkBytes(base, content);
        check(!Files.exists(pending), "Recovered draft must not remain pending");

        // 重复打开不应改写已经恢复的草稿。
        ShorthandDraftRecovery.recover(base.toFile());
        checkBytes(base, content);
    }

    private static void checkExistingDraft(final Path dir, final boolean hasBase, final boolean hasBackup)
            throws IOException {
        Files.createDirectories(dir);
        final Path base = dir.resolve("draft.md");
        final Path backup = dir.resolve("draft.md.bak");
        final Path pending = dir.resolve("draft.md.new");
        final byte[] baseContent = "committed draft".getBytes(StandardCharsets.UTF_8);
        final byte[] backupContent = "legacy backup".getBytes(StandardCharsets.UTF_8);
        final byte[] pendingContent = "incomplete write".getBytes(StandardCharsets.UTF_8);
        if (hasBase) {
            Files.write(base, baseContent);
        }
        if (hasBackup) {
            Files.write(backup, backupContent);
        }
        Files.write(pending, pendingContent);

        ShorthandDraftRecovery.recover(base.toFile());
        check(Files.exists(base) == hasBase, "Recovery must preserve the base file state");
        if (hasBase) {
            checkBytes(base, baseContent);
        }
        if (hasBackup) {
            checkBytes(backup, backupContent);
        }
        checkBytes(pending, pendingContent);
    }

    private static void checkInvalidNewFile(final Path dir) throws IOException {
        final Path pending = Files.createDirectories(dir.resolve("draft.md.new"));
        ShorthandDraftRecovery.recover(dir.resolve("draft.md").toFile());
        check(Files.isDirectory(pending), "Invalid pending draft must remain untouched");
        check(!Files.exists(dir.resolve("draft.md")), "Directory must not become a draft");
    }

    private static void checkRecoveryFailure(final Path dir) throws IOException {
        Files.createDirectories(dir);
        final Path base = dir.resolve("draft.md");
        final Path pending = dir.resolve("draft.md.new");
        final byte[] content = "recoverable draft".getBytes(StandardCharsets.UTF_8);
        Files.write(pending, content);

        // 模拟检查后正式文件已出现，移动必须报错并保留两份内容。
        final java.io.File destination = new java.io.File(base.toString()) {
            @Override
            public boolean exists() {
                try {
                    Files.write(toPath(), "concurrent draft".getBytes(StandardCharsets.UTF_8));
                } catch (final IOException e) {
                    throw new AssertionError(e);
                }
                return false;
            }
        };
        try {
            ShorthandDraftRecovery.recover(destination);
            throw new AssertionError("Recovery must fail when the destination appears");
        } catch (final java.nio.file.FileAlreadyExistsException expected) {
            checkBytes(pending, content);
            checkBytes(base, "concurrent draft".getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void checkBytes(final Path path, final byte[] expected) throws IOException {
        check(Arrays.equals(expected, Files.readAllBytes(path)), "Draft bytes changed: " + path);
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
