package org.b3log.siyuan.markdown;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.CancellationSignal;
import android.provider.DocumentsContract;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** SAF directory access, never derived from a filesystem path or a leaf grant. */
public final class MarkdownTreeFiles implements MarkdownTree.Provider {
    private final Context context;
    private final ContentResolver resolver;
    private final Set<CancellationSignal> queries = java.util.Collections.synchronizedSet(new HashSet<>());
    private volatile boolean cancelled;
    private static final java.util.concurrent.ScheduledExecutorService TIMEOUTS = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
    private static final String[] COLUMNS = {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE};
    public MarkdownTreeFiles(Context context) { this.context = context.getApplicationContext(); resolver = context.getContentResolver(); }
    public void cancelQueries() { synchronized (queries) { for (CancellationSignal query : queries) query.cancel(); } }
    public void cancel() { cancelled = true; cancelQueries(); }
    private Cursor query(Uri uri, CancellationSignal signal) throws Exception {
        if (cancelled) throw new IOException("unavailable");
        return resolver.query(uri, COLUMNS, null, null, null, signal);
    }
    private MarkdownTree.Entry entry(Cursor cursor) throws Exception {
        String key = cursor.getString(0), name = cursor.getString(1), mime = cursor.getString(2);
        if (key == null || key.isEmpty() || key.length() > 8192 || name == null || name.isEmpty() || name.length() > 4096 || name.indexOf('\0') >= 0) throw new IOException("protocol");
        return new MarkdownTree.Entry(key, name, DocumentsContract.Document.MIME_TYPE_DIR.equals(mime));
    }
    public Uri uri(MarkdownTree.Root root, String key) { return DocumentsContract.buildDocumentUriUsingTree(Uri.parse(root.grant), key); }
    @Override public List<MarkdownTree.Entry> children(MarkdownTree.Root root, String key) throws Exception {
        CancellationSignal signal = new CancellationSignal(); queries.add(signal);
        java.util.concurrent.ScheduledFuture<?> timeout = TIMEOUTS.schedule(signal::cancel, 15, java.util.concurrent.TimeUnit.SECONDS);
        try (Cursor cursor = query(DocumentsContract.buildChildDocumentsUriUsingTree(Uri.parse(root.grant), key), signal)) {
            if (cursor == null) throw new IOException("unavailable");
            List<MarkdownTree.Entry> result = new ArrayList<>();
            while (cursor.moveToNext()) { if (result.size() >= MarkdownTree.MAX_NODES) throw new IOException("size"); result.add(entry(cursor)); }
            return result;
        } finally { timeout.cancel(false); queries.remove(signal); }
    }
    @Override public MarkdownTree.Entry inspect(MarkdownTree.Root root, String key) throws Exception {
        CancellationSignal signal = new CancellationSignal(); queries.add(signal);
        java.util.concurrent.ScheduledFuture<?> timeout = TIMEOUTS.schedule(signal::cancel, 15, java.util.concurrent.TimeUnit.SECONDS);
        try (Cursor cursor = query(uri(root, key), signal)) {
            if (cursor == null || !cursor.moveToFirst()) throw new IOException("unavailable");
            MarkdownTree.Entry entry = entry(cursor);
            if (!entry.key.equals(key)) throw new IOException("identity-changed");
            return entry;
        } finally { timeout.cancel(false); queries.remove(signal); }
    }
    @Override public List<MarkdownTree.Entry> path(MarkdownTree.Root root, String key) throws Exception {
        if (key.equals(root.entry.key)) return java.util.Collections.singletonList(inspect(root, key));
        DocumentsContract.Path path = DocumentsContract.findDocumentPath(resolver, uri(root, key));
        if (path == null) throw new IOException("permission");
        List<String> ids = path.getPath();
        int from = ids.indexOf(root.entry.key);
        if (from < 0 || !ids.get(ids.size() - 1).equals(key) || ids.size() - from > MarkdownTree.MAX_DEPTH + 1) throw new IOException("permission");
        Set<String> seen = new HashSet<>(); List<MarkdownTree.Entry> result = new ArrayList<>();
        for (int i = from; i < ids.size(); i++) {
            if (!seen.add(ids.get(i))) throw new IOException("protocol");
            result.add(inspect(root, ids.get(i)));
        }
        return result;
    }
    public MarkdownTree.Root choose(Uri tree, int flags) throws Exception {
        if (!"content".equals(tree.getScheme()) || !DocumentsContract.isTreeUri(tree)) throw new IOException("permission");
        new MarkdownFiles(context).persist(tree, flags);
        String key = DocumentsContract.getTreeDocumentId(tree);
        MarkdownTree.Root provisional = new MarkdownTree.Root(tree.toString(), tree.getAuthority() + "\0" + key, new MarkdownTree.Entry(key, "", true));
        MarkdownTree.Entry entry = inspect(provisional, key);
        if (!entry.directory) throw new IOException("permission");
        return new MarkdownTree.Root(tree.toString(), provisional.identity, entry);
    }
    public String key(MarkdownFiles.Target target) {
        if (target == null || !DocumentsContract.isDocumentUri(context, target.uri)) return null;
        return target.uri.getAuthority() + "\0" + DocumentsContract.getDocumentId(target.uri);
    }
    public String localKey(MarkdownTree.Root root, MarkdownFiles.Target target) {
        String key = key(target);
        if (key == null || !key.startsWith(Uri.parse(root.grant).getAuthority() + "\0")) return null;
        return DocumentsContract.getDocumentId(target.uri);
    }
    public MarkdownTree.Root parent(MarkdownFiles.Target target) {
        if (target == null || !DocumentsContract.isDocumentUri(context, target.uri)) return null;
        List<Uri> trees = new ArrayList<>();
        if (DocumentsContract.isTreeUri(target.uri)) trees.add(target.uri);
        for (android.content.UriPermission grant : resolver.getPersistedUriPermissions()) if (grant.isReadPermission() && DocumentsContract.isTreeUri(grant.getUri())) trees.add(grant.getUri());
        for (Uri tree : trees) {
            if (!tree.getAuthority().equals(target.uri.getAuthority())) continue;
            try {
                String key = DocumentsContract.getDocumentId(target.uri), grantedRoot = DocumentsContract.getTreeDocumentId(tree);
                DocumentsContract.Path found = DocumentsContract.findDocumentPath(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, key));
                if (found == null) continue;
                List<String> path = found.getPath();
                if (path.size() < 2 || !grantedRoot.equals(path.get(0)) || !key.equals(path.get(path.size() - 1))) continue;
                String parentKey = path.get(path.size() - 2);
                MarkdownTree.Root provisional = new MarkdownTree.Root(tree.toString(), tree.getAuthority() + "\0" + parentKey, new MarkdownTree.Entry(parentKey, "", true));
                MarkdownTree.Entry parent = inspect(provisional, parentKey);
                if (parent.directory) return new MarkdownTree.Root(provisional.grant, provisional.identity, parent);
            } catch (Exception ignored) { /* A leaf grant or unsupported path API grants no parent authority. */ }
        }
        return null;
    }
}
