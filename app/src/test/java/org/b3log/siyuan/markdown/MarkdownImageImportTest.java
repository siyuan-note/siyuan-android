package org.b3log.siyuan.markdown;

import org.junit.Test;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

public final class MarkdownImageImportTest {
    private static final byte[] PNG = new byte[]{(byte)137,80,78,71,13,10,26,10};
    private static final class Files implements MarkdownImageImport.Destination<String> {
        final Map<String, byte[]> bytes = new HashMap<>();
        int creates, writes, reads;
        boolean rename, race, loseAcknowledgement, corrupt;
        @Override public String find(String name) { return bytes.containsKey(name) ? name : null; }
        @Override public String create(String name, String mime) {
            creates++;
            String actual = rename ? "renamed.png" : name;
            bytes.put(actual, race ? new byte[]{9,9,9} : new byte[0]);
            return actual;
        }
        @Override public String name(String file) { return file; }
        @Override public byte[] read(String file) { reads++; return bytes.get(file); }
        @Override public void write(String file, byte[] body) throws IOException {
            writes++; bytes.put(file, corrupt ? new byte[]{1,2,3} : body.clone());
            if (loseAcknowledgement) throw new IOException("io");
        }
        @Override public boolean same(String first, String second) { return first.equals(second); }
    }
    private interface Attempt { void run() throws Exception; }
    private void rejects(Attempt action) throws Exception {
        try { action.run(); fail("Import must not publish a reference"); } catch (IOException expected) { }
    }
    @Test public void importsOnlyASelectedNewFilenameAfterVerifiedWrite() throws Exception {
        Files files = new Files();
        assertEquals("my image.png", MarkdownImageImport.copy(PNG, "my image.png", files));
        assertArrayEquals(PNG, files.bytes.get("my image.png")); assertEquals(1, files.writes); assertEquals(2, files.reads);
    }
    @Test public void existingDestinationRemainsUntouched() throws Exception {
        Files files = new Files(); files.bytes.put("photo.png", new byte[]{5,6,7});
        rejects(() -> MarkdownImageImport.copy(PNG, "photo.png", files));
        assertArrayEquals(new byte[]{5,6,7}, files.bytes.get("photo.png")); assertEquals(0, files.creates); assertEquals(0, files.writes);
    }
    @Test public void returnedFileWithUnexpectedNameOrExistingContentIsNeverTruncated() throws Exception {
        Files renamed = new Files(); renamed.rename = true;
        rejects(() -> MarkdownImageImport.copy(PNG, "photo.png", renamed)); assertEquals(0, renamed.writes);
        Files raced = new Files(); raced.race = true;
        rejects(() -> MarkdownImageImport.copy(PNG, "photo.png", raced)); assertEquals(0, raced.writes);
        assertArrayEquals(new byte[]{9,9,9}, raced.bytes.get("photo.png"));
    }
    @Test public void failedAcknowledgementOrVerificationKeepsFileWithoutPublishingReference() throws Exception {
        Files lost = new Files(); lost.loseAcknowledgement = true;
        rejects(() -> MarkdownImageImport.copy(PNG, "photo.png", lost)); assertArrayEquals(PNG, lost.bytes.get("photo.png"));
        Files corrupt = new Files(); corrupt.corrupt = true;
        rejects(() -> MarkdownImageImport.copy(PNG, "photo.png", corrupt)); assertNotNull(corrupt.bytes.get("photo.png"));
    }
    @Test public void imageTypeAndDestinationValidationPrecedeCreation() throws Exception {
        Files files = new Files();
        rejects(() -> MarkdownImageImport.copy(PNG, "../photo.png", files));
        rejects(() -> MarkdownImageImport.copy(PNG, "photo.jpg", files));
        rejects(() -> MarkdownImageImport.copy(new byte[]{1,2,3}, "photo.png", files));
        assertEquals(0, files.creates);
    }
}
