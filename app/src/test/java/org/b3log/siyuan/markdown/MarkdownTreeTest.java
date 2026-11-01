package org.b3log.siyuan.markdown;

import android.content.Context;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public final class MarkdownTreeTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static final class Provider implements MarkdownTree.Provider {
        final Map<String, MarkdownTree.Entry> entries = new HashMap<>();
        final Map<String, List<MarkdownTree.Entry>> children = new HashMap<>();
        final Map<String, String> parents = new HashMap<>();
        int queries;
        boolean denied;
        MarkdownTree.Entry add(String parent, String key, String name, boolean directory) {
            MarkdownTree.Entry entry = new MarkdownTree.Entry(key, name, directory); entries.put(key, entry);
            children.putIfAbsent(key, new ArrayList<>());
            if (parent != null) { children.get(parent).add(entry); parents.put(key, parent); }
            return entry;
        }
        MarkdownTree.Root root(String key) { return new MarkdownTree.Root("opaque-grant", "provider:" + key, entries.get(key)); }
        @Override public List<MarkdownTree.Entry> children(MarkdownTree.Root root, String key) throws Exception {
            queries++; if (denied) throw new SecurityException(); return new ArrayList<>(children.get(key));
        }
        @Override public MarkdownTree.Entry inspect(MarkdownTree.Root root, String key) throws Exception {
            if (denied) throw new SecurityException(); if (!entries.containsKey(key)) throw new IOException("unavailable"); return entries.get(key);
        }
        @Override public List<MarkdownTree.Entry> path(MarkdownTree.Root root, String key) throws Exception {
            List<MarkdownTree.Entry> result = new ArrayList<>(); String at = key;
            while (at != null && result.size() < 100) { result.add(0, entries.get(at)); if (at.equals(root.entry.key)) return result; at = parents.get(at); }
            throw new IOException("permission");
        }
    }
    private MarkdownTree tree(Provider provider) throws Exception {
        return new MarkdownTree(provider, new MarkdownTreeView(new File(temporary.newFolder(), "tree-view.json"), ignored -> { }));
    }
    private JSONObject rootLocation(MarkdownTree tree) throws Exception {
        JSONObject state = tree.state();
        return new JSONObject().put("rootId", state.getString("rootId")).put("generation", state.getLong("generation")).put("nodeId", state.getString("rootId"));
    }
    private JSONObject childLocation(JSONObject parent, String id) throws Exception { return new JSONObject(parent.toString()).put("nodeId", id); }

    @Test public void lazyDirectoriesFirstNumericSortAndMarkdownOnly() throws Exception {
        Provider p = new Provider(); p.add(null, "root", "Notes", true); p.add("root", "sub", "zFolder", true);
        p.add("root", "ten", "Note10.md", false); p.add("root", "two", "note2.MARKDOWN", false); p.add("root", "png", "image.png", false);
        p.add("sub", "secret", "Nested.md", false);
        MarkdownTree tree = tree(p); tree.select(p.root("root"));
        JSONObject result = tree.list(rootLocation(tree)); JSONArray entries = result.getJSONArray("entries");
        assertEquals(1, p.queries); assertEquals(3, entries.length());
        assertEquals("zFolder", entries.getJSONObject(0).getString("name")); assertEquals("note2.MARKDOWN", entries.getJSONObject(1).getString("name")); assertEquals("Note10.md", entries.getJSONObject(2).getString("name"));
        assertFalse(result.toString().contains("opaque-grant")); assertFalse(result.toString().contains("Nested.md"));
        assertEquals("two", tree.openKey(childLocation(rootLocation(tree), entries.getJSONObject(1).getString("id"))));
    }

    @Test public void staleRootForgedNodeRefreshAndMovedFileCannotOpen() throws Exception {
        Provider p = new Provider(); p.add(null, "root", "Notes", true); p.add("root", "file", "File.md", false);
        MarkdownTree tree = tree(p); tree.select(p.root("root")); JSONObject location = rootLocation(tree);
        String id = tree.list(location).getJSONArray("entries").getJSONObject(0).getString("id");
        assertEquals("blocked", tree.list(childLocation(location, "forged")).getString("kind"));
        p.children.get("root").clear();
        try { tree.openKey(childLocation(location, id)); fail(); } catch (IOException expected) { assertEquals("permission", expected.getMessage()); }
        assertEquals(0, tree.list(location).getJSONArray("entries").length());
        try { tree.openKey(childLocation(location, id)); fail(); } catch (IOException expected) { }
        tree.select(p.root("root")); assertEquals("blocked", tree.list(location).getString("kind"));
        assertEquals(location.getString("rootId"), tree.state().getString("rootId"));
    }

    @Test public void permissionRevocationInvalidatesRootAndViewPersistsAcrossSessions() throws Exception {
        Provider p = new Provider(); p.add(null, "root", "Notes", true);
        File viewFile = new File(temporary.newFolder(), "tree-view.json");
        MarkdownTree first = new MarkdownTree(p, new MarkdownTreeView(viewFile, ignored -> { })); first.select(p.root("root"));
        JSONObject location = rootLocation(first);
        first.saveView(new JSONObject(location.toString()).put("width", 310).put("visible", false).put("expanded", new JSONArray().put(location.getString("rootId"))));
        MarkdownTree next = new MarkdownTree(p, new MarkdownTreeView(viewFile, ignored -> { })); next.select(p.root("root"));
        assertEquals(310, next.state().getInt("width")); assertFalse(next.state().getBoolean("visible")); assertEquals(1, next.state().getJSONArray("expanded").length());
        p.denied = true; JSONObject denied = first.state(); assertEquals("permission-required", denied.getString("kind")); assertTrue(denied.getLong("generation") > location.getLong("generation"));
        assertEquals("blocked", first.list(location).getString("kind"));
    }

    @Test public void limitsRejectWholeDirectoryAndCycles() throws Exception {
        Provider p = new Provider(); p.add(null, "root", "Notes", true);
        for (int i = 0; i <= MarkdownTree.MAX_NODES; i++) p.add("root", "file" + i, i + ".md", false);
        MarkdownTree tree = tree(p); tree.select(p.root("root"));
        assertEquals("size", tree.list(rootLocation(tree)).getJSONObject("error").getString("code"));
        p.children.get("root").clear(); p.children.get("root").add(p.entries.get("root"));
        assertEquals("protocol", tree.list(rootLocation(tree)).getJSONObject("error").getString("code"));
    }

    @Test public void depthAndTotalNodeBudgetAreBounded() throws Exception {
        Provider p = new Provider(); p.add(null, "root", "Notes", true);
        String previous = "root"; for (int i = 1; i <= 65; i++) { p.add(previous, "dir" + i, "D" + i, true); previous = "dir" + i; }
        MarkdownTree tree = tree(p); tree.select(p.root("root")); JSONObject location = rootLocation(tree);
        for (int i = 0; i < 64; i++) location = childLocation(location, tree.list(location).getJSONArray("entries").getJSONObject(0).getString("id"));
        assertEquals("size", tree.list(location).getJSONObject("error").getString("code"));
    }

    @Test public void currentPathUsesOpaqueAncestorsAndDoesNotExpandOtherDirectories() throws Exception {
        Provider p = new Provider(); p.add(null, "root", "Notes", true); p.add("root", "dir", "Sub", true); p.add("dir", "file", "A.md", false);
        MarkdownTree tree = tree(p); tree.select(p.root("root")); tree.current("file");
        JSONObject state = tree.state(); assertEquals(2, state.getJSONArray("currentPath").length()); assertEquals(0, p.queries);
        assertEquals("file", tree.openKey(new JSONObject().put("rootId", state.getString("rootId")).put("generation", state.getLong("generation")).put("nodeId", state.getString("currentNodeId"))));
    }

    @Test public void deletedCurrentDocumentDoesNotDisableItsStillReadableRoot() throws Exception {
        Provider p = new Provider(); p.add(null, "root", "Notes", true); p.add("root", "a", "A.md", false); p.add("root", "b", "B.md", false);
        MarkdownTree tree = tree(p); tree.select(p.root("root")); tree.current("a");
        JSONObject before = tree.state(); assertTrue(before.has("currentNodeId"));
        p.entries.remove("a"); p.children.get("root").removeIf(entry -> entry.key.equals("a"));
        JSONObject after = tree.state(); assertEquals("ready", after.getString("kind")); assertFalse(after.has("currentNodeId")); assertFalse(after.has("currentPath"));
        assertEquals(before.getString("rootId"), after.getString("rootId")); assertEquals(before.getLong("generation"), after.getLong("generation"));
        JSONObject listing = tree.list(rootLocation(tree)); assertEquals("B.md", listing.getJSONArray("entries").getJSONObject(0).getString("name"));
        assertEquals("b", tree.openKey(childLocation(rootLocation(tree), listing.getJSONArray("entries").getJSONObject(0).getString("id"))));
    }

    @Test public void unknownOrCorruptViewIsPreservedAndLimitsCannotBeBypassed() throws Exception {
        File file = new File(temporary.newFolder(), "tree-view.json"); byte[] unknown = "{\"schema\":999}".getBytes(StandardCharsets.UTF_8); Files.write(file.toPath(), unknown);
        MarkdownTreeView view = new MarkdownTreeView(file, ignored -> { });
        try { view.save(null, new JSONObject().put("width", 250)); fail(); } catch (IOException expected) { }
        assertArrayEquals(unknown, Files.readAllBytes(file.toPath()));
        File valid = new File(temporary.newFolder(), "tree-view.json"); view = new MarkdownTreeView(valid, ignored -> { });
        try { view.save(null, new JSONObject().put("width", 481)); fail(); } catch (IOException expected) { }
        assertFalse(valid.exists());
        for (int i = 0; i < 17; i++) { String id = MarkdownCodec.digest(("root" + i).getBytes(StandardCharsets.UTF_8)); view.save(id, new JSONObject().put("expanded", new JSONArray().put(id))); }
        assertEquals(16, new JSONObject(new String(Files.readAllBytes(valid.toPath()), StandardCharsets.UTF_8)).getJSONArray("roots").length());
        JSONArray tooMany = new JSONArray(); for (int i = 0; i < 257; i++) tooMany.put("a".repeat(64));
        try { view.save("b".repeat(64), new JSONObject().put("expanded", tooMany)); fail(); } catch (IOException expected) { }
    }

    @Test public void acceptedViewSnapshotStaysWithOldRootAfterRootOrDocumentSwitch() throws Exception {
        Provider p = new Provider(); p.add(null, "a", "A", true); p.add(null, "b", "B", true);
        MarkdownTree tree = tree(p); tree.select(p.root("a")); JSONObject a = rootLocation(tree);
        JSONObject request = new JSONObject(a.toString()).put("expanded", new JSONArray().put(a.getString("rootId"))).put("width", 300);
        MarkdownTree.CapturedView captured = tree.captureView(request);
        request.put("expanded", new JSONArray());
        tree.select(p.root("b")); tree.saveCapturedView(captured);
        assertEquals(0, tree.state().getJSONArray("expanded").length()); assertEquals(300, tree.state().getInt("width"));
        try { tree.captureView(request); fail(); } catch (IOException expected) { }
        tree.select(p.root("a")); assertEquals(1, tree.state().getJSONArray("expanded").length());
    }

    private static final class FilesProvider extends MarkdownFiles {
        final Map<Uri, String> content = new HashMap<>(); int opens; boolean fail;
        FilesProvider() { super(mock(Context.class)); }
        Uri file(String name) { Uri uri = mock(Uri.class); when(uri.toString()).thenReturn("content://fixture/" + name); content.put(uri, name); return uri; }
        @Override public Target open(Uri uri, int flags) throws Exception { opens++; if (fail) throw new IOException("permission"); return new Target(uri, uri.toString(), content.get(uri), true, true, MarkdownCodec.decode(content.get(uri).getBytes(StandardCharsets.UTF_8))); }
    }
    @Test public void prepareCancelAndIdempotentCommitUseRealHostLeasesWithoutRereading() throws Exception {
        FilesProvider files = new FilesProvider(); Uri first = files.file("a.md"), second = files.file("b.md");
        MarkdownRegistry registry = new MarkdownRegistry(); MarkdownStore store = new MarkdownStore(temporary.newFolder());
        MarkdownHost current = new MarkdownHost(files, store, registry); current.open(first, 0);
        MarkdownTreeSwitch switching = new MarkdownTreeSwitch(() -> new MarkdownHost(files, store, registry), registry);
        JSONObject ready = switching.prepare(current, second, new JSONObject(), "en", false); String ticket = ready.getString("ticket");
        assertFalse(current.closed); assertEquals(current.sessionId, registry.owner(first.toString())); assertEquals(ready.getString("sessionId"), registry.owner(second.toString()));
        try { switching.commit(ticket, current); fail(); } catch (IOException expected) { }
        switching.cancel(ticket); assertNull(registry.owner(second.toString())); assertFalse(current.closed);
        ready = switching.prepare(current, second, new JSONObject(), "en", false); ticket = ready.getString("ticket");
        current.handle("releaseSession", new JSONObject().put("sessionId", current.sessionId).put("targetGeneration", 0));
        files.fail = true; int opens = files.opens;
        MarkdownHost next = switching.commit(ticket, current); assertEquals("b.md", next.content); assertEquals(opens, files.opens);
        assertSame(next, switching.commit(ticket, next)); assertEquals(next.sessionId, switching.committedBootstrap(ticket, next).getString("sessionId"));
        switching.cancel(ticket); assertFalse(next.closed); assertEquals(next.sessionId, registry.owner(second.toString()));
    }

    @Test public void failedOrOccupiedPreparationPreservesCurrentAndDestroyDrainsCandidate() throws Exception {
        FilesProvider files = new FilesProvider(); Uri first = files.file("a.md"), second = files.file("b.md");
        MarkdownRegistry registry = new MarkdownRegistry(); MarkdownStore store = new MarkdownStore(temporary.newFolder());
        MarkdownHost current = new MarkdownHost(files, store, registry); current.open(first, 0);
        MarkdownTreeSwitch switching = new MarkdownTreeSwitch(() -> new MarkdownHost(files, store, registry), registry);
        assertEquals("current", switching.prepare(current, first, new JSONObject(), "en", false).getString("kind"));
        registry.claim(second.toString(), "other", false);
        assertEquals("occupied", switching.prepare(current, second, new JSONObject(), "en", false).getString("kind")); registry.detach("other");
        files.fail = true; try { switching.prepare(current, second, new JSONObject(), "en", false); fail(); } catch (IOException expected) { }
        assertFalse(current.closed); assertEquals(current.sessionId, registry.owner(first.toString()));
        files.fail = false; switching.prepare(current, second, new JSONObject(), "en", false); switching.drain();
        assertNull(registry.owner(second.toString())); assertFalse(current.closed);
    }
}
