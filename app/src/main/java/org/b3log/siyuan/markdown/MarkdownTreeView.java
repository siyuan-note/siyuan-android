package org.b3log.siyuan.markdown;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** Independent, bounded UI state. Invalid originals are never overwritten. */
public final class MarkdownTreeView {
    private final File file;
    private final MarkdownStore.DirectorySync sync;
    private JSONObject value;
    private IOException failure;
    private final java.util.Map<String, java.util.Map<String, String>> nodeIds = new java.util.HashMap<>();

    private static boolean validId(String id) { return id.matches("[0-9]{14}-[a-z0-9]{7}"); }

    /** Path fingerprints are lookup keys; view identities use SiYuan IDs. */
    public synchronized String root(String key) throws Exception {
        if (failure != null) throw failure;
        JSONArray roots = value.getJSONArray("roots");
        for (int i = 0; i < roots.length(); i++) {
            JSONObject root = roots.getJSONObject(i);
            if (key.equals(root.optString("key"))) return root.getString("id");
        }
        java.util.Set<String> used = new java.util.HashSet<>();
        for (int i = 0; i < roots.length(); i++) used.add(roots.getJSONObject(i).getString("id"));
        String id = fresh(used);
        JSONArray next = new JSONArray().put(new JSONObject().put("id", id).put("key", key).put("expanded", new JSONArray()));
        for (int i = 0; i < roots.length() && next.length() < 16; i++) next.put(roots.getJSONObject(i));
        value.put("roots", next);
        return id;
    }

    private static String fresh(java.util.Set<String> used) throws IOException {
        for (int attempt = 0; attempt < 32; attempt++) { String id = MarkdownIds.newId(); if (!used.contains(id)) return id; }
        throw new IOException("io");
    }

    public synchronized String node(String rootId, String key) throws Exception {
        if (failure != null) throw failure;
        java.util.Map<String, String> ids = nodeIds.computeIfAbsent(rootId, ignored -> new java.util.HashMap<>());
        if (ids.containsKey(key)) return ids.get(key);
        JSONArray roots = value.getJSONArray("roots");
        java.util.Set<String> used = new java.util.HashSet<>(ids.values()); used.add(rootId);
        for (int i = 0; i < roots.length(); i++) {
            JSONObject root = roots.getJSONObject(i);
            if (!rootId.equals(root.getString("id"))) continue;
            JSONObject saved = root.optJSONObject("nodeIds");
            if (saved != null) { java.util.Iterator<String> keys = saved.keys(); while (keys.hasNext()) used.add(saved.getString(keys.next())); }
            if (saved != null && saved.has(key)) { String id = saved.getString(key); ids.put(key, id); return id; }
            JSONArray expanded = root.getJSONArray("expanded");
            for (int j = 0; j < expanded.length(); j++) { used.add(expanded.getString(j)); }
        }
        if (ids.size() >= 10000) throw new IOException("size");
        String id = fresh(used); ids.put(key, id); return id;
    }

    public MarkdownTreeView(File file) { this(file, MarkdownDirectorySync::sync); }

    public MarkdownTreeView(File file, MarkdownStore.DirectorySync sync) {
        this.file = file; this.sync = sync;
        try {
            if (file.exists()) {
                if (file.length() > 512 * 1024) throw new IOException("size");
                value = new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
                validate(value);
            } else value = new JSONObject().put("schema", 1).put("width", 240).put("visible", true).put("roots", new JSONArray());
        } catch (Exception error) { failure = new IOException("io", error); }
    }

    private static void validate(JSONObject data) throws Exception {
        if (!Integer.valueOf(1).equals(data.get("schema")) || !(data.get("width") instanceof Number) || ((Number) data.get("width")).doubleValue() != data.getInt("width") || data.getInt("width") < 180 || data.getInt("width") > 480 || !(data.get("visible") instanceof Boolean)) throw new IOException("protocol");
        JSONArray roots = data.getJSONArray("roots");
        if (roots.length() > 16) throw new IOException("size");
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (int i = 0; i < roots.length(); i++) {
            JSONObject root = roots.getJSONObject(i);
            String id = root.getString("id");
            if (!validId(id) || !ids.add(id)) throw new IOException("protocol");
            JSONArray expanded = root.getJSONArray("expanded");
            if (expanded.length() > 256) throw new IOException("size");
            for (int j = 0; j < expanded.length(); j++) if (!validId(expanded.getString(j))) throw new IOException("protocol");
            if (root.has("key") && !root.getString("key").matches("[a-f0-9]{64}")) throw new IOException("protocol");
            JSONObject mappings = root.optJSONObject("nodeIds");
            if (root.has("nodeIds") && mappings == null) throw new IOException("protocol");
            if (mappings != null) {
                if (mappings.length() > 256) throw new IOException("size");
                java.util.Set<String> mapped = new java.util.HashSet<>(); mapped.add(id);
                java.util.Iterator<String> keys = mappings.keys();
                while (keys.hasNext()) { String key = keys.next(); if (!key.matches("[a-f0-9]{64}") || !validId(mappings.getString(key)) || !mapped.add(mappings.getString(key))) throw new IOException("protocol"); }
            }
        }
    }

    public synchronized JSONObject read(String rootId) throws Exception {
        if (failure != null) throw failure;
        JSONArray expanded = new JSONArray();
        JSONArray roots = value.getJSONArray("roots");
        for (int i = 0; i < roots.length(); i++) if (roots.getJSONObject(i).getString("id").equals(rootId)) expanded = roots.getJSONObject(i).getJSONArray("expanded");
        return new JSONObject().put("width", value.getInt("width")).put("visible", value.getBoolean("visible"))
                .put("expanded", new JSONArray(expanded.toString()));
    }

    public synchronized void save(String rootId, JSONObject patch) throws Exception {
        if (failure != null) throw failure;
        JSONObject next = new JSONObject(value.toString());
        if (patch.has("width")) next.put("width", patch.getInt("width"));
        if (patch.has("visible")) next.put("visible", patch.getBoolean("visible"));
        if (patch.has("expanded")) {
            if (rootId == null) throw new IOException("protocol");
            JSONArray roots = new JSONArray();
            JSONArray old = next.getJSONArray("roots");
            JSONObject selected = new JSONObject().put("id", rootId);
            for (int i = 0; i < old.length(); i++) if (rootId.equals(old.getJSONObject(i).getString("id"))) selected = old.getJSONObject(i);
            JSONArray expanded = patch.getJSONArray("expanded");
            java.util.Set<String> retained = new java.util.HashSet<>();
            for (int i = 0; i < expanded.length(); i++) retained.add(expanded.getString(i));
            JSONObject mappings = selected.optJSONObject("nodeIds");
            JSONObject kept = new JSONObject();
            if (mappings != null) { java.util.Iterator<String> keys = mappings.keys(); while (keys.hasNext()) { String key = keys.next(); if (retained.contains(mappings.getString(key))) kept.put(key, mappings.getString(key)); } }
            for (java.util.Map.Entry<String, String> entry : nodeIds.getOrDefault(rootId, java.util.Collections.emptyMap()).entrySet()) if (retained.contains(entry.getValue())) kept.put(entry.getKey(), entry.getValue());
            roots.put(selected.put("expanded", expanded).put("nodeIds", kept));
            for (int i = 0; i < old.length() && roots.length() < 16; i++) if (!old.getJSONObject(i).getString("id").equals(rootId)) roots.put(old.getJSONObject(i));
            next.put("roots", roots);
        }
        validate(next);
        File directory = file.getParentFile();
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("space");
        File pending = MarkdownIds.temporary(directory, "tree-view-", ".tmp");
        try (FileOutputStream out = new FileOutputStream(pending)) { out.write(next.toString().getBytes(StandardCharsets.UTF_8)); out.getFD().sync(); }
        try { Files.move(pending.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (IOException error) { Files.deleteIfExists(pending.toPath()); throw error; }
        sync.sync(directory);
        value = next;
    }
}
