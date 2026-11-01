package org.b3log.siyuan.markdown;

import android.content.Context;
import android.net.Uri;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Real Host contract calls with a controllable SAF boundary, not an alternate host implementation. */
public final class MarkdownHostTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private final Map<String, Long> sequences = new HashMap<>();

    private String operationId(MarkdownHost host) {
        long sequence = sequences.getOrDefault(host.sessionId, 0L) + 1;
        sequences.put(host.sessionId, sequence);
        return host.sessionId + ":fixture:" + sequence;
    }

    private static final class Provider extends MarkdownFiles {
        final Map<String, byte[]> contents = new HashMap<>();
        final Map<String, Uri> uris = new HashMap<>();
        int writes;
        boolean failAfterWrite;
        Provider() { super(mock(Context.class)); }
        Uri file(String name, String content) {
            Uri uri = mock(Uri.class); when(uri.toString()).thenReturn("content://fixture/" + name);
            uris.put(uri.toString(), uri); contents.put(uri.toString(), content.getBytes(StandardCharsets.UTF_8)); return uri;
        }
        @Override public Target open(Uri uri, int flags) throws Exception {
            byte[] data = contents.get(uri.toString());
            if (data == null) throw new IOException("unavailable");
            return new Target(uri, uri.toString(), "fixture.md", true, true, MarkdownCodec.decode(data));
        }
        @Override public Target reopen(String uri) throws Exception { return open(uris.get(uri), 0); }
        @Override public MarkdownCodec.Decoded read(Target target) throws Exception { return MarkdownCodec.decode(contents.get(target.uri.toString())); }
        @Override public void write(Target target, byte[] data) throws Exception {
            writes++; contents.put(target.uri.toString(), data.clone()); if (failAfterWrite) throw new IOException("io");
        }
        String text(Uri uri) { return new String(contents.get(uri.toString()), StandardCharsets.UTF_8); }
    }

    private static final class Store extends MarkdownStore {
        boolean failBinding;
        boolean failJournal;
        Store(File directory) throws IOException { super(directory); }
        @Override public synchronized void put(String key, long generation, long revision, String value) throws IOException {
            if (failBinding && key.startsWith("binding-")) throw new IOException("space");
            if (failJournal && key.startsWith("write-")) throw new IOException("space");
            super.put(key, generation, revision, value);
        }
    }

    private JSONObject operation(MarkdownHost host, MarkdownFiles.Target target, long generation, long revision, String text, String id) throws Exception {
        if (!id.startsWith(host.sessionId + ":")) id = operationId(host);
        return new JSONObject().put("sessionId", host.sessionId).put("operationId", id).put("targetGeneration", generation)
                .put("revision", revision).put("content", text).put("target", target.json()).put("expectedBaseline", target.baseline);
    }

    private JSONObject reserve(MarkdownHost host, Uri uri, long revision, String content, String id) throws Exception {
        id = operationId(host);
        MarkdownFiles.Target candidate = host.candidate(uri, 0, new JSONObject().put("bom", false).put("newline", "\n"));
        JSONObject reservation = (JSONObject) host.handle("reserveSaveAs", new JSONObject().put("sessionId", host.sessionId)
                .put("operationId", id).put("target", candidate.json()).put("format", candidate.json().getJSONObject("format")));
        assertEquals("reserved", reservation.getString("kind"));
        JSONObject request = operation(host, candidate, host.generation + 1, revision, content, id)
                .put("reservationId", reservation.getJSONObject("reservation").getString("id"));
        host.handle("prepareSaveAs", new JSONObject().put("operation", request).put("previousTarget", host.target == null ? JSONObject.NULL : host.target.json()));
        return request;
    }

    private JSONObject commit(MarkdownHost host, JSONObject request, JSONObject receipt) throws Exception {
        return new JSONObject().put("operation", request).put("receipt", receipt)
                .put("previousTarget", host.target == null ? JSONObject.NULL : host.target.json()).put("nextTargetGeneration", request.getLong("targetGeneration"));
    }

    @Test public void sharedSaveAsGenerationAndLateDraftContract() throws Exception {
        Provider provider = new Provider(); Uri original = provider.file("original.md", "original"), destination = provider.file("new.md", "");
        Store store = new Store(temporary.newFolder()); MarkdownRegistry registry = new MarkdownRegistry();
        MarkdownHost host = new MarkdownHost(provider, store, registry); host.open(original, 0);
        JSONObject oldRead = new JSONObject().put("sessionId", host.sessionId).put("operationId", operationId(host)).put("targetGeneration", 0).put("target", host.target.json());
        JSONObject request = reserve(host, destination, 1, "new content", "save-as");
        assertEquals(0, host.generation);
        JSONObject receipt = (JSONObject) host.handle("write", request); assertEquals("committed", receipt.getString("kind"));
        assertEquals("original", provider.text(original)); assertEquals("new content", provider.text(destination));
        try { registry.release(destination.toString(), host.sessionId); fail("Written candidate must remain reserved until binding is durable"); } catch (IOException expected) { }
        try { registry.detach(host.sessionId); fail("Closing cannot abandon an unbound written candidate"); } catch (IOException expected) { }
        JSONObject commit = commit(host, request, receipt);
        assertEquals("committed", ((JSONObject) host.handle("commitSaveAs", commit)).getString("kind"));
        assertEquals(1, host.generation);
        assertEquals("committed", ((JSONObject) host.handle("commitSaveAs", commit)).getString("kind"));
        assertEquals("blocked", ((JSONObject) host.handle("read", oldRead)).getString("kind"));
        JSONObject lateDraft = new JSONObject(request.toString()).put("targetGeneration", 0).put("revision", 2).put("content", "typed during Save As");
        host.handle("putDraft", lateDraft);
        host.handle("clearDraft", new JSONObject().put("sessionId", host.sessionId).put("targetGeneration", 0).put("revision", 1));
        MarkdownStore.Record draft = store.read("draft-" + host.sessionId);
        assertEquals(1, draft.generation); assertEquals(2, draft.revision); assertTrue(draft.payload.contains("typed during Save As"));
    }

    @Test public void bindingFailureRetainsReservationAndCanRetryWithoutWritingAgain() throws Exception {
        Provider provider = new Provider(); Uri original = provider.file("original.md", "original"), destination = provider.file("new.md", "");
        Store store = new Store(temporary.newFolder()); MarkdownRegistry registry = new MarkdownRegistry();
        MarkdownHost host = new MarkdownHost(provider, store, registry); host.open(original, 0);
        JSONObject request = reserve(host, destination, 1, "saved", "save-as");
        JSONObject receipt = (JSONObject) host.handle("write", request), commit = commit(host, request, receipt);
        store.failBinding = true;
        assertEquals("indeterminate", ((JSONObject) host.handle("commitSaveAs", commit)).getString("kind"));
        assertEquals(0, host.generation); assertEquals(original, host.target.uri);
        try { registry.claim(destination.toString(), "other", false); fail("Other sessions cannot take a written reservation"); } catch (IOException expected) { }
        host.handle("putDraft", new JSONObject().put("sessionId", host.sessionId).put("targetGeneration", 0).put("revision", 2).put("content", "typed while binding failed"));
        store.failBinding = false;
        assertEquals("committed", ((JSONObject) host.handle("commitSaveAs", commit)).getString("kind"));
        assertEquals(1, provider.writes);
        MarkdownStore.Record draft = store.read("draft-" + host.sessionId);
        assertEquals(1, draft.generation); assertEquals(2, draft.revision); assertTrue(draft.payload.contains("typed while binding failed"));
        assertEquals("typed while binding failed", host.content);
    }

    @Test public void uncertainProviderWriteReconcilesWithoutRewriting() throws Exception {
        Provider provider = new Provider(); Uri original = provider.file("original.md", "long original text");
        MarkdownHost host = new MarkdownHost(provider, new Store(temporary.newFolder()), new MarkdownRegistry()); host.open(original, 0);
        JSONObject request = operation(host, host.target, 0, 1, "short", "write"); provider.failAfterWrite = true;
        assertEquals("indeterminate", ((JSONObject) host.handle("write", request)).getString("kind"));
        assertEquals("short", provider.text(original));
        assertEquals("committed", ((JSONObject) host.handle("reconcileWrite", request)).getString("kind"));
        assertEquals(1, provider.writes);
        JSONObject forged = new JSONObject(request.toString()).put("content", "different snapshot");
        assertEquals("indeterminate", ((JSONObject) host.handle("reconcileWrite", forged)).getString("kind"));
    }

    @Test public void activeRecoveryCannotBeListedRestoredOrDiscarded() throws Exception {
        Provider provider = new Provider(); Store store = new Store(temporary.newFolder()); MarkdownRegistry registry = new MarkdownRegistry();
        MarkdownHost first = new MarkdownHost(provider, store, registry), second = new MarkdownHost(provider, store, registry);
        first.handle("putDraft", new JSONObject().put("sessionId", first.sessionId).put("targetGeneration", 0).put("revision", 1).put("content", "only copy"));
        assertEquals(0, second.recoveries().length());
        try { second.restore("draft-" + first.sessionId); fail("Live recovery must not be stolen"); } catch (IOException expected) { }
        try { second.discard("draft-" + first.sessionId); fail("Live recovery must not be deleted"); } catch (IOException expected) { }
        assertTrue(store.read("draft-" + first.sessionId).payload.contains("only copy"));
        first.handle("releaseSession", new JSONObject().put("sessionId", first.sessionId).put("targetGeneration", 0));
        assertEquals(1, second.recoveries().length());
    }

    @Test public void restartedHostProtectsAndRecoversWrittenButUnboundSaveAs() throws Exception {
        Provider provider = new Provider(); Uri original = provider.file("original.md", "original"), destination = provider.file("new.md", "");
        Store store = new Store(temporary.newFolder());
        MarkdownHost first = new MarkdownHost(provider, store, new MarkdownRegistry()); first.open(original, 0);
        JSONObject request = reserve(first, destination, 1, "recovered content", "save-as");
        assertEquals("committed", ((JSONObject) first.handle("write", request)).getString("kind"));
        MarkdownRegistry restartedRegistry = new MarkdownRegistry();
        MarkdownHost restarted = new MarkdownHost(provider, store, restartedRegistry);
        try { restartedRegistry.claim(destination.toString(), "third", false); fail("Crash must retain the written reservation"); } catch (IOException expected) { }
        restarted.restore("draft-" + first.sessionId);
        assertEquals("recovered content", restarted.content); assertEquals(destination, restarted.target.uri);
        assertTrue(restarted.recovered); assertEquals(1, provider.writes);
    }

    @Test public void boundedReceiptsRejectEvictedReplaysAndReleaseTombstones() throws Exception {
        Provider provider = new Provider(); Uri uri = provider.file("original.md", "original");
        Store store = new Store(temporary.newFolder()); MarkdownHost host = new MarkdownHost(provider, store, new MarkdownRegistry()); host.open(uri, 0);
        JSONObject first = null, latest = null;
        for (int i = 1; i <= 40; i++) {
            JSONObject request = operation(host, host.target, 0, i, "version " + i, "autosave");
            assertEquals("committed", ((JSONObject) host.handle("write", request)).getString("kind"));
            if (first == null) first = request;
            latest = request;
        }
        assertEquals(16, store.keys("write-" + host.sessionId).size());
        for (String key : store.keys("write-" + host.sessionId)) assertFalse(new JSONObject(store.read(key).payload).has("request"));
        assertEquals("committed", ((JSONObject) host.handle("write", latest)).getString("kind"));
        assertEquals("blocked", ((JSONObject) host.handle("write", first)).getString("kind"));
        JSONObject sameRevisionReplay = new JSONObject(first.toString()).put("revision", 40).put("expectedBaseline", host.target.baseline);
        assertEquals("blocked", ((JSONObject) host.handle("write", sameRevisionReplay)).getString("kind"));
        assertEquals(40, provider.writes); assertEquals("version 40", provider.text(uri));
        host.handle("clearDraft", new JSONObject().put("sessionId", host.sessionId).put("targetGeneration", 0).put("revision", 40));
        host.handle("releaseSession", new JSONObject().put("sessionId", host.sessionId).put("targetGeneration", 0));
        assertTrue(store.keys("write-" + host.sessionId).isEmpty());
        assertNull(store.read("draft-" + host.sessionId)); assertNull(store.read("watermark-" + host.sessionId));
    }

    @Test public void journalFailureAndExternalConflictNeverReachProviderWrite() throws Exception {
        Provider provider = new Provider(); Uri uri = provider.file("original.md", "original");
        Store store = new Store(temporary.newFolder()); MarkdownHost host = new MarkdownHost(provider, store, new MarkdownRegistry()); host.open(uri, 0);
        JSONObject request = operation(host, host.target, 0, 1, "new content", "write");
        store.failJournal = true;
        assertEquals("blocked", ((JSONObject) host.handle("write", request)).getString("kind"));
        assertEquals(0, provider.writes); assertEquals("original", provider.text(uri));
        store.failJournal = false;
        provider.contents.put(uri.toString(), "external edit".getBytes(StandardCharsets.UTF_8));
        assertEquals("conflict", ((JSONObject) host.handle("write", request)).getString("kind"));
        assertEquals(0, provider.writes); assertEquals("external edit", provider.text(uri));
    }

    @Test public void pastSaveAsBindingsDoNotReserveOldTargetsAfterRestart() throws Exception {
        Provider provider = new Provider(); Uri first = provider.file("first.md", "first"), second = provider.file("second.md", ""), third = provider.file("third.md", "");
        Store store = new Store(temporary.newFolder()); MarkdownHost host = new MarkdownHost(provider, store, new MarkdownRegistry()); host.open(first, 0);
        for (Uri destination : new Uri[]{second, third}) {
            JSONObject request = reserve(host, destination, host.generation + 1, "saved " + host.generation, "save-as");
            JSONObject receipt = (JSONObject) host.handle("write", request);
            assertEquals("committed", ((JSONObject) host.handle("commitSaveAs", commit(host, request, receipt))).getString("kind"));
        }
        MarkdownRegistry restarted = new MarkdownRegistry();
        MarkdownHost restored = new MarkdownHost(provider, store, restarted);
        assertNull(restarted.owner(first.toString())); assertNull(restarted.owner(second.toString()));
        restored.restore("draft-" + host.sessionId);
        assertEquals(third, restored.target.uri); assertEquals("saved 1", restored.content);
    }

    @Test public void cancelledCandidateDoesNotConsumeFuturePickerCapacity() throws Exception {
        Provider provider = new Provider(); Uri uri = provider.file("candidate.md", "");
        MarkdownHost host = new MarkdownHost(provider, new Store(temporary.newFolder()), new MarkdownRegistry());
        JSONObject format = new JSONObject().put("bom", false).put("newline", "\n");
        for (int i = 0; i < 20; i++) {
            MarkdownFiles.Target candidate = host.candidate(uri, 0, format);
            host.cancelCandidate(candidate.handle);
        }
        assertEquals(0, provider.writes);
    }

    @Test public void recoveringAbandonedContainerReleasesItsOldSourceClaim() throws Exception {
        Provider provider = new Provider(); Uri original = provider.file("source.md", "source"), destination = provider.file("destination.md", "");
        Store store = new Store(temporary.newFolder()); MarkdownRegistry registry = new MarkdownRegistry();
        MarkdownHost first = new MarkdownHost(provider, store, registry); first.open(original, 0);
        JSONObject request = reserve(first, destination, 1, "recovered", "save-as");
        assertEquals("committed", ((JSONObject) first.handle("write", request)).getString("kind"));
        registry.abandonCrashed(first.sessionId);
        MarkdownHost next = new MarkdownHost(provider, store, registry);
        next.restore("draft-" + first.sessionId);
        assertEquals(destination, next.target.uri); assertNull(registry.owner(original.toString()));
        assertEquals(next.sessionId, registry.owner(destination.toString()));
    }

    @Test public void contentBeyondFileLimitRemainsDurableAndRecoverableWithoutWritingOriginal() throws Exception {
        Provider provider = new Provider(); Uri original = provider.file("source.md", "original");
        Store store = new Store(temporary.newFolder()); MarkdownRegistry registry = new MarkdownRegistry();
        MarkdownHost host = new MarkdownHost(provider, store, registry); host.open(original, 0);
        String text = "x".repeat(MarkdownCodec.MAX_BYTES + 1);
        JSONObject request = operation(host, host.target, 0, 1, text, "draft");
        request.put("format", new JSONObject().put("bom", false).put("newline", "\n"));
        host.handle("putDraft", request);
        assertEquals(text, new JSONObject(store.read("draft-" + host.sessionId).payload).getJSONObject("draft").getString("content"));
        JSONObject result = (JSONObject) host.handle("write", request);
        assertEquals("blocked", result.getString("kind")); assertEquals("size", result.getJSONObject("error").getString("code"));
        assertEquals("original", provider.text(original)); assertEquals(0, provider.writes);
        host.handle("releaseSession", new JSONObject().put("sessionId", host.sessionId).put("targetGeneration", 0));
        MarkdownHost recovered = new MarkdownHost(provider, store, registry);
        recovered.restore("draft-" + host.sessionId);
        assertEquals(text, recovered.content); assertTrue(recovered.recovered);
        assertNotNull(store.read("draft-" + host.sessionId));
        assertEquals(text, new JSONObject(store.read("draft-" + recovered.sessionId).payload).getJSONObject("draft").getString("content"));
    }
}
