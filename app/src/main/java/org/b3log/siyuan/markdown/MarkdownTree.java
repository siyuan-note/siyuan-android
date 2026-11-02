package org.b3log.siyuan.markdown;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Opaque directory authority. Directory I/O runs separately from document writes. */
public final class MarkdownTree {
    public static final int MAX_NODES = 10000, MAX_DEPTH = 64;
    public static final class Entry {
        public final String key, name;
        public final boolean directory;
        public Entry(String key, String name, boolean directory) { this.key = key; this.name = name; this.directory = directory; }
    }
    public static final class Root {
        public final String grant, identity;
        public final Entry entry;
        public Root(String grant, String identity, Entry entry) { this.grant = grant; this.identity = identity; this.entry = entry; }
    }
    public interface Provider {
        List<Entry> children(Root root, String key) throws Exception;
        Entry inspect(Root root, String key) throws Exception;
        List<Entry> path(Root root, String key) throws Exception;
    }
    private static final class Node {
        final Entry entry;
        final String parent;
        final int depth;
        Node(Entry entry, String parent, int depth) { this.entry = entry; this.parent = parent; this.depth = depth; }
    }
    private final Provider provider;
    private final MarkdownTreeView view;
    private final Map<String, Node> nodes = new HashMap<>();
    private Root root;
    private String rootId, currentKey;
    private long generation;
    private String kind = "empty";
    private JSONObject failure;
    private static final class ViewAuthority {
        final String rootId; final long generation;
        ViewAuthority(String rootId, long generation) { this.rootId = rootId; this.generation = generation; }
    }
    public static final class CapturedView {
        private final String rootId;
        private final JSONObject patch;
        CapturedView(String rootId, JSONObject patch) { this.rootId = rootId; this.patch = patch; }
    }
    private volatile ViewAuthority viewAuthority = new ViewAuthority(null, 0);

    public MarkdownTree(Provider provider, MarkdownTreeView view) { this.provider = provider; this.view = view; }
    private static String digest(String value) { return MarkdownCodec.digest(value.getBytes(StandardCharsets.UTF_8)); }
    private String id(Entry entry) throws Exception { return entry.key.equals(root.entry.key) ? rootId : view.node(rootId, digest(root.identity + "\0" + entry.key)); }
    public synchronized Root root() { return root; }
    public synchronized long generation() { return generation; }
    public synchronized void current(String key) { currentKey = key; }

    public synchronized void select(Root selected) throws Exception {
        if (selected == null || !selected.entry.directory) throw new IOException("permission");
        root = selected; rootId = view.root(digest(selected.identity)); generation++; kind = "ready"; failure = null; nodes.clear();
        nodes.put(rootId, new Node(selected.entry, null, 0));
        viewAuthority = new ViewAuthority(rootId, generation);
    }
    public synchronized void permissionRequired() {
        root = null; rootId = null; nodes.clear(); generation++; kind = "permission-required"; failure = null;
        viewAuthority = new ViewAuthority(null, generation);
    }
    public static JSONObject blocked(Exception error) {
        String code = error instanceof SecurityException ? "permission" : error.getMessage();
        if (code == null || !code.matches("permission|unavailable|size|io|protocol|offline|deleted|identity-changed")) code = "unavailable";
        try { return new JSONObject().put("kind", "blocked").put("error", new JSONObject().put("code", code).put("recoverable", !"protocol".equals(code))); }
        catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }

    public synchronized JSONObject state() throws Exception {
        if (root != null && "ready".equals(kind)) {
            try { if (!provider.inspect(root, root.entry.key).directory) throw new IOException("permission"); }
            catch (Exception error) { permissionRequired(); failure = blocked(error).getJSONObject("error"); }
        }
        JSONObject result; boolean viewFailure = false;
        try { result = view.read(rootId); }
        catch (Exception error) { viewFailure = true; result = new JSONObject().put("width", 240).put("visible", true).put("expanded", new JSONArray()); result.put("error", blocked(error).get("error")); }
        result.put("kind", viewFailure ? "blocked" : kind).put("generation", generation);
        if (failure != null) result.put("error", failure);
        if (root != null) {
            result.put("rootId", rootId).put("rootName", root.entry.name);
            if (currentKey != null) {
                boolean currentAvailable = false;
                try {
                    currentAvailable = !provider.inspect(root, currentKey).directory;
                    if (!currentAvailable) throw new IOException("unavailable");
                    List<Entry> path = provider.path(root, currentKey);
                    if (path.size() > MAX_DEPTH + 1) throw new IOException("size");
                    if (!path.isEmpty() && path.get(0).key.equals(root.entry.key) && path.get(path.size() - 1).key.equals(currentKey)) {
                        JSONArray parents = new JSONArray();
                        Set<String> seen = new HashSet<>();
                        String parent = null;
                        for (int i = 0; i < path.size(); i++) {
                            Entry entry = path.get(i);
                            if (!seen.add(entry.key) || i < path.size() - 1 && !entry.directory) throw new IOException("protocol");
                            String nodeId = id(entry);
                            if (nodes.size() >= MAX_NODES && !nodes.containsKey(nodeId)) throw new IOException("size");
                            nodes.put(nodeId, new Node(entry, parent, i));
                            if (entry.directory) parents.put(nodeId); else result.put("currentNodeId", nodeId);
                            parent = nodeId;
                        }
                        result.put("currentPath", parents);
                    }
                } catch (Exception ignored) {
                    // Some providers cannot return paths. Issued direct-child
                    // capabilities still let a root-internal switch be selected.
                    for (Map.Entry<String, Node> entry : nodes.entrySet()) if (currentAvailable && entry.getValue().entry.key.equals(currentKey) && !entry.getValue().entry.directory) {
                        try { validateNode(entry.getValue()); } catch (Exception outsideOrChanged) { break; }
                        List<String> path = new ArrayList<>(); Node at = entry.getValue();
                        while (at.parent != null) { path.add(0, at.parent); at = nodes.get(at.parent); if (at == null || path.size() > MAX_DEPTH) break; }
                        if (at != null && at.entry.key.equals(root.entry.key)) result.put("currentNodeId", entry.getKey()).put("currentPath", new JSONArray(path));
                        break;
                    }
                }
            }
        }
        return result;
    }

    private Node require(JSONObject location, boolean directory) throws Exception {
        if (!"ready".equals(kind) || root == null || !rootId.equals(location.getString("rootId")) || generation != location.getLong("generation")) throw new IOException("permission");
        Node node = nodes.get(location.getString("nodeId"));
        if (node == null || node.entry.directory != directory) throw new IOException("permission");
        return node;
    }
    private void validateNode(Node node) throws Exception {
        Entry actual = provider.inspect(root, node.entry.key);
        if (actual.directory != node.entry.directory || !actual.name.equals(node.entry.name)) throw new IOException("identity-changed");
        // Validate the issued ancestry with direct provider queries. No ID prefix
        // comparison or filesystem path conversion can confer authority.
        Node at = node;
        int depth = 0;
        while (at.parent != null) {
            if (++depth > MAX_DEPTH) throw new IOException("size");
            Node parent = nodes.get(at.parent);
            if (parent == null) throw new IOException("permission");
            boolean found = false;
            List<Entry> children = provider.children(root, parent.entry.key);
            if (children.size() > MAX_NODES) throw new IOException("size");
            for (Entry child : children) if (child.key.equals(at.entry.key) && child.directory == at.entry.directory) { found = true; break; }
            if (!found) throw new IOException("permission");
            at = parent;
        }
        if (!at.entry.key.equals(root.entry.key)) throw new IOException("permission");
    }

    public synchronized String openKey(JSONObject location) throws Exception {
        Node node = require(location, false); validateNode(node); return node.entry.key;
    }

    public synchronized JSONObject list(JSONObject location) {
        try {
            Node parent = require(location, true); validateNode(parent);
            List<Entry> children = provider.children(root, parent.entry.key);
            if (children.size() > MAX_NODES) throw new IOException("size");
            List<Entry> visible = new ArrayList<>(); Set<String> seen = new HashSet<>();
            Set<String> ancestors = new HashSet<>(); Node ancestor = parent;
            while (ancestor != null) { ancestors.add(ancestor.entry.key); ancestor = nodes.get(ancestor.parent); }
            for (Entry child : children) {
                if (!seen.add(child.key) || ancestors.contains(child.key)) throw new IOException("protocol");
                if (child.directory || child.name.toLowerCase(Locale.ROOT).matches(".*\\.(md|markdown)$")) visible.add(child);
            }
            if (!visible.isEmpty() && parent.depth + 1 > MAX_DEPTH) throw new IOException("size");
            Set<String> retained = new HashSet<>(); for (Entry child : visible) retained.add(id(child));
            // Refresh removes capabilities for deleted children and their descendants.
            Set<String> removed = new HashSet<>();
            for (Map.Entry<String, Node> entry : nodes.entrySet()) if (location.getString("nodeId").equals(entry.getValue().parent) && !retained.contains(entry.getKey())) removed.add(entry.getKey());
            boolean changed;
            do { changed = false; for (Map.Entry<String, Node> entry : nodes.entrySet()) if (removed.contains(entry.getValue().parent) && removed.add(entry.getKey())) changed = true; } while (changed);
            int added = 0; for (Entry child : visible) if (!nodes.containsKey(id(child))) added++;
            if (nodes.size() - removed.size() + added > MAX_NODES) throw new IOException("size");
            visible.sort((a, b) -> a.directory != b.directory ? a.directory ? -1 : 1 : naturalCompare(a.name, b.name));
            removed.forEach(nodes::remove);
            JSONArray entries = new JSONArray();
            for (Entry child : visible) {
                String childId = id(child);
                Node previous = nodes.get(childId);
                if (previous != null && !location.getString("nodeId").equals(previous.parent)) throw new IOException("protocol");
                nodes.put(childId, new Node(child, location.getString("nodeId"), parent.depth + 1));
                entries.put(new JSONObject().put("id", childId).put("name", child.name).put("kind", child.directory ? "directory" : "file"));
            }
            return new JSONObject().put("kind", "listed").put("rootId", rootId).put("generation", generation).put("nodeId", location.getString("nodeId")).put("entries", entries);
        } catch (Exception error) { return blocked(error); }
    }

    /** Capture on receipt, without waiting behind provider queries or a root change. */
    public CapturedView captureView(JSONObject request) throws Exception {
        JSONObject patch = new JSONObject(request.toString());
        ViewAuthority authority = viewAuthority;
        if (patch.has("rootId") && (!patch.getString("rootId").equals(authority.rootId) || patch.getLong("generation") != authority.generation)) throw new IOException("permission");
        if (patch.has("expanded") && (!patch.has("rootId") || patch.getJSONArray("expanded").length() > 256)) throw new IOException("protocol");
        return new CapturedView(authority.rootId, patch);
    }
    public void saveCapturedView(CapturedView captured) throws Exception { view.save(captured.rootId, captured.patch); }
    public void saveView(JSONObject patch) throws Exception { saveCapturedView(captureView(patch)); }

    static int naturalCompare(String left, String right) {
        String a = left.toLowerCase(Locale.ROOT), b = right.toLowerCase(Locale.ROOT);
        int i = 0, j = 0;
        while (i < a.length() && j < b.length()) {
            char x = a.charAt(i), y = b.charAt(j);
            if (x >= '0' && x <= '9' && y >= '0' && y <= '9') {
                int ai = i, bj = j;
                while (i < a.length() && Character.isDigit(a.charAt(i))) i++;
                while (j < b.length() && Character.isDigit(b.charAt(j))) j++;
                while (ai < i - 1 && a.charAt(ai) == '0') ai++;
                while (bj < j - 1 && b.charAt(bj) == '0') bj++;
                if (i - ai != j - bj) return Integer.compare(i - ai, j - bj);
                int compare = a.substring(ai, i).compareTo(b.substring(bj, j)); if (compare != 0) return compare;
            } else { if (x != y) return Character.compare(x, y); i++; j++; }
        }
        int compare = Integer.compare(a.length() - i, b.length() - j);
        return compare == 0 ? left.compareTo(right) : compare;
    }
}
