package org.b3log.siyuan.markdown;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/** Independent, bounded UI state. Invalid originals are never overwritten. */
public final class MarkdownTreeView {
    private final File file;
    private final MarkdownStore.DirectorySync sync;
    private JSONObject value;
    private IOException failure;

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
            if (!id.matches("[a-f0-9]{64}") || !ids.add(id)) throw new IOException("protocol");
            JSONArray expanded = root.getJSONArray("expanded");
            if (expanded.length() > 256) throw new IOException("size");
            for (int j = 0; j < expanded.length(); j++) if (!expanded.getString(j).matches("[a-f0-9]{64}")) throw new IOException("protocol");
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
            roots.put(new JSONObject().put("id", rootId).put("expanded", patch.getJSONArray("expanded")));
            JSONArray old = next.getJSONArray("roots");
            for (int i = 0; i < old.length() && roots.length() < 16; i++) if (!old.getJSONObject(i).getString("id").equals(rootId)) roots.put(old.getJSONObject(i));
            next.put("roots", roots);
        }
        validate(next);
        File directory = file.getParentFile();
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("space");
        File pending = new File(directory, "tree-view-" + UUID.randomUUID() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(pending)) { out.write(next.toString().getBytes(StandardCharsets.UTF_8)); out.getFD().sync(); }
        try { Files.move(pending.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (IOException error) { Files.deleteIfExists(pending.toPath()); throw error; }
        sync.sync(directory);
        value = next;
    }
}
