package org.b3log.siyuan.markdown;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;

public final class MarkdownCoreTest {
    private static void check(boolean condition, String reason) { if (!condition) throw new AssertionError(reason); }
    private interface Operation { void run() throws Exception; }
    private static void rejects(Operation operation, String reason) throws Exception {
        try { operation.run(); throw new AssertionError(reason); } catch (IOException expected) { }
    }
    public static void main(String[] args) throws Exception {
        check(MarkdownTheme.dark("dark", false), "Explicit dark overrides light system");
        check(!MarkdownTheme.dark("light", true), "Explicit light overrides dark system");
        check(MarkdownTheme.dark("system", true) && !MarkdownTheme.dark("system", false), "System preference tracks system");
        String initialPage = "<link href=\"./appearance/themes/daylight/theme.css\"><script src=\"./index.js\"></script>";
        check(MarkdownTheme.page(initialPage, true).equals(initialPage.replace("daylight/theme.css", "midnight/theme.css")), "Dark page selects bundled stylesheet before loading");
        check(MarkdownTheme.page(initialPage, false).equals(initialPage), "Light page stays unchanged");
        check(MarkdownTheme.page("<link href=./appearance/themes/daylight/theme.css>", true).contains("midnight/theme.css"), "Minified attribute quoting does not change theme selection");
        check(MarkdownTheme.page("daylight arbitrary document text", true).equals("daylight arbitrary document text"), "Only the exact stylesheet reference is transformed");
        check(MarkdownIds.newId().matches("[0-9]{14}-[a-z0-9]{7}"), "SiYuan ID format");
        check(MarkdownIds.token().matches("[0-9a-f]{64}"), "Capability tokens retain 256 bits");
        check(!MarkdownIds.token().equals(MarkdownIds.token()), "Fresh capability tokens");
        Path idDirectory = Files.createTempDirectory("markdown-id-");
        Path collision = idDirectory.resolve("same.tmp");
        Files.write(collision, "other writer".getBytes(StandardCharsets.UTF_8));
        try {
            rejects(() -> MarkdownIds.temporary(idDirectory.toFile(), "", ".tmp", () -> "same"), "Colliding temporary files must not be acquired");
            check(new String(Files.readAllBytes(collision), StandardCharsets.UTF_8).equals("other writer"), "Collision never truncates or removes another writer's file");
        } finally { Files.delete(collision); Files.delete(idDirectory); }
        check(MarkdownRouting.matches("android.intent.action.VIEW", "content", "text/markdown", "opaque"), "Markdown MIME route");
        check(MarkdownRouting.matches("android.intent.action.VIEW", "content", "application/octet-stream", "README.MD"), "Provider filename route");
        check(!MarkdownRouting.matches("android.intent.action.VIEW", "content", "application/pdf", "document.pdf"), "PDF keeps existing route");
        check(!MarkdownRouting.matches("android.intent.action.VIEW", "content", "text/plain", "notes.txt"), "Plain text keeps existing route");
        check(!MarkdownRouting.matches("android.intent.action.SEND", "content", "text/markdown", "readme.md"), "Sharing keeps the shorthand route");
        check(MarkdownImages.mime("payload.svg") == null, "Active SVG is not a resource image");
        check(!MarkdownImages.matches("<html><script>bad</script>".getBytes(StandardCharsets.UTF_8), "image/png"), "Disguised HTML is rejected");
        check(!MarkdownImages.matches("<svg onload='bad'>".getBytes(StandardCharsets.UTF_8), "image/jpeg"), "Disguised SVG is rejected");
        check(MarkdownImages.matches(new byte[]{(byte)137,80,78,71,13,10,26,10}, "image/png"), "PNG signature");
        check(MarkdownImages.matches("GIF89a".getBytes(StandardCharsets.US_ASCII), "image/gif"), "GIF signature");
        check(MarkdownImages.matches("RIFFxxxxWEBP".getBytes(StandardCharsets.US_ASCII), "image/webp"), "WebP signature");
        check(!MarkdownImages.matches("RIFFxxxxWAVE".getBytes(StandardCharsets.US_ASCII), "image/webp"), "Unrelated RIFF is rejected");
        byte[] original = "\ufeff中文\r\nshort\r\n".getBytes(StandardCharsets.UTF_8);
        MarkdownCodec.Decoded decoded = MarkdownCodec.decode(original);
        check(decoded.bom && decoded.newline.equals("\r\n") && decoded.content.equals("中文\nshort\n"), "BOM/newline decoding");
        check(Arrays.equals(original, MarkdownCodec.encode(decoded.content, decoded.bom, decoded.newline)), "Exact encoding restoration");
        check(MarkdownCodec.decode("a\rb".getBytes(StandardCharsets.UTF_8)).newline.equals("\r"), "CR newline");
        rejects(() -> MarkdownCodec.decode(new byte[]{(byte) 0xc0, (byte) 0xaf}), "Invalid UTF-8 must not be replaced");
        rejects(() -> MarkdownCodec.encode("\ud800", false, "\n"), "Invalid UTF-16 must not be replaced");
        rejects(() -> MarkdownCodec.decode(new byte[MarkdownCodec.MAX_BYTES + 1]), "Document limit");
        byte[] boundary = new byte[8 * 1024 * 1024]; Arrays.fill(boundary, (byte) 'a');
        String full = new String(boundary, StandardCharsets.UTF_8);
        check(MarkdownCodec.decode(boundary).content.length() == 8 * 1024 * 1024, "Exactly 8 MiB is accepted");
        check(MarkdownCodec.encode(full, false, "\n").length == boundary.length, "Encoding accepts the exact byte boundary");
        rejects(() -> MarkdownCodec.encode(full + "a", false, "\n"), "A byte beyond 8 MiB is rejected");
        rejects(() -> MarkdownCodec.encode(full, true, "\n"), "BOM bytes count toward the limit");
        rejects(() -> MarkdownCodec.encode(full.substring(1) + "界", false, "\n"), "UTF-8 bytes, not character count, determine the limit");
        check(MarkdownCodec.encodeDraft(full + "a").length == 8 * 1024 * 1024 + 1, "An oversized editable draft remains recoverable");
        rejects(() -> MarkdownCodec.encodeDraft("x".repeat(MarkdownCodec.MAX_DRAFT_BYTES + 1)), "Recovery content has its independent 16 MiB limit");
        check(!MarkdownImageImport.validName("../photo.png") && !MarkdownImageImport.validName("photo.svg"), "Image destination must be an inert image leaf name");

        MarkdownRegistry registry = new MarkdownRegistry();
        registry.attach("A"); registry.attach("B");
        rejects(() -> registry.attach("A"), "Duplicate session cannot replace an active owner");
        check(registry.isSessionActive("A"), "Collision leaves original session active");
        registry.claim("original", "A", false);
        MarkdownRegistry.Claim reserved = registry.claim("candidate", "A", true);
        rejects(() -> registry.claim("candidate", "B", true), "Concurrent Save As must reserve before writing");
        rejects(() -> registry.claim("original", "B", false), "Duplicate identity must reuse its owner");
        registry.begin("candidate", "A", reserved.reservation, "op1");
        rejects(() -> registry.release("candidate", "A"), "Uncertain provider writes retain reservation");
        rejects(() -> registry.detach("A"), "Session cannot release an unresolved write");
        rejects(() -> registry.begin("candidate", "A", reserved.reservation, "op2"), "New writes cannot overtake uncertain writes");
        rejects(() -> registry.resolved("candidate", "A", "op2"), "A different receipt cannot settle the write");
        registry.resolved("candidate", "A", "op1"); registry.release("candidate", "A");
        check(registry.claim("candidate", "B", true) != null, "Reservation available after settled cancellation");
        registry.detach("A"); check(registry.hasSessions(), "Other container remains active");
        registry.detach("B"); check(!registry.hasSessions(), "Last session detaches cleanly");

        Path root = Files.createTempDirectory("siyuan-markdown-store-");
        try {
            MarkdownStore store = new MarkdownStore(root.toFile());
            store.put("draft-A", 0, 4, "new text"); store.put("draft-A", 0, 3, "old text");
            check(store.read("draft-A").payload.equals("new text"), "Late draft cannot overwrite newer revision");
            store.put("draft-A", 0, 4, ""); store.put("draft-A", 0, 4, "late text");
            check(store.read("draft-A").payload.isEmpty(), "Late draft cannot resurrect tombstone");
            store.put("draft-A", 1, 4, "new target"); store.put("draft-A", 0, 100, "old target");
            check(store.read("draft-A").payload.equals("new target"), "Old target generation cannot overwrite new binding");
            MarkdownStore reopened = new MarkdownStore(root.toFile());
            check(reopened.read("draft-A").payload.equals("new target"), "Draft survives reopening");
            byte[] bytes = Files.readAllBytes(root.resolve("draft-A.record"));
            bytes[bytes.length - 1] ^= 1; Files.write(root.resolve("draft-A.record"), bytes);
            rejects(() -> reopened.read("draft-A"), "Corruption rejected");
            rejects(() -> reopened.put("draft-A", 2, 1, "replacement"), "Corrupt original cannot be silently overwritten");
            check(Arrays.equals(bytes, Files.readAllBytes(root.resolve("draft-A.record"))), "Original corruption evidence preserved");
            rejects(() -> store.put("../escape", 0, 0, ""), "Path traversal rejected");
            store.put("draft-B", 0, 1, "untouched");
            byte[] metadata = Files.readAllBytes(root.resolve("draft-B.record")); metadata[19] ^= 1;
            Files.write(root.resolve("draft-B.record"), metadata);
            rejects(() -> store.read("draft-B"), "Metadata corruption rejected");
            store.put("draft-C", 0, 1, "durable old draft");
            byte[] prior = Files.readAllBytes(root.resolve("draft-C.record"));
            store.put("draft-C", 0, 2, "complete pending draft");
            Path pending = root.resolve("draft-C-" + MarkdownIds.newId() + ".pending");
            Files.move(root.resolve("draft-C.record"), pending);
            Files.write(root.resolve("draft-C.record"), prior);
            MarkdownStore interrupted = new MarkdownStore(root.toFile());
            check(interrupted.read("draft-C").payload.equals("complete pending draft"), "A complete interrupted atomic promotion recovers the latest draft");
            check(!Files.exists(pending), "Recovered pending becomes the authoritative record");
            Files.write(pending, new byte[]{1,2,3});
            MarkdownStore incomplete = new MarkdownStore(root.toFile());
            check(incomplete.hasUnrecoveredPending() && Files.exists(pending), "Incomplete temporary bytes remain visible and preserved");
            check(incomplete.read("draft-C").payload.equals("complete pending draft"), "Incomplete pending cannot erase the valid draft");
            boolean[] directoryFailure = {false};
            MarkdownStore durable = new MarkdownStore(root.toFile(), directory -> { if (directoryFailure[0]) throw new IOException("space"); });
            directoryFailure[0] = true;
            rejects(() -> durable.put("draft-D", 0, 1, "unconfirmed rename"), "Directory sync failure cannot acknowledge durability");
            rejects(() -> durable.read("draft-D"), "Reading a cached rename cannot bypass failed directory sync");
            directoryFailure[0] = false;
            check(durable.read("draft-D").payload.equals("unconfirmed rename"), "Directory sync can confirm retained bytes on retry");
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
                for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) Files.delete(path);
            }
        }
        System.out.println("Markdown encoding, ownership, lifecycle and recovery regression cases passed");
    }
}
