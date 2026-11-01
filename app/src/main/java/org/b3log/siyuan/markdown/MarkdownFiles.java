package org.b3log.siyuan.markdown;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.system.Os;
import android.system.OsConstants;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** SAF operations never turn a document URI into a filesystem path. */
public class MarkdownFiles {
    private final Context context;
    private final ContentResolver resolver;

    public static final class Target {
        public final String handle = UUID.randomUUID().toString();
        public final Uri uri;
        public final String identity;
        public final String name;
        public final boolean writable;
        public final boolean persistent;
        public final String initialContent;
        public boolean bom;
        public String newline;
        public String baseline;

        Target(Uri uri, String identity, String name, boolean writable, boolean persistent, MarkdownCodec.Decoded decoded) {
            this.uri = uri; this.identity = identity; this.name = name; this.writable = writable;
            this.persistent = persistent; this.bom = decoded.bom; this.newline = decoded.newline; this.baseline = decoded.baseline;
            this.initialContent = decoded.content;
        }

        public JSONObject json() throws Exception {
            return new JSONObject().put("handle", handle).put("identity", identity).put("displayName", name)
                    .put("baseline", baseline).put("writable", writable)
                    .put("format", new JSONObject().put("bom", bom).put("newline", newline))
                    .put("capabilities", new JSONObject().put("persistentPermission", persistent).put("conditionalWrite", false)
                            .put("atomicReplace", false).put("coordinatedAccess", false));
        }
    }

    public MarkdownFiles(Context context) { this.context = context.getApplicationContext(); resolver = context.getContentResolver(); }

    public boolean persist(Uri uri, int flags) {
        int granted = flags & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if ((flags & Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0 && granted != 0) {
            try { resolver.takePersistableUriPermission(uri, granted); } catch (SecurityException ignored) { /* Temporary grant remains usable. */ }
        }
        return persistent(uri, false);
    }

    private boolean persistent(Uri uri, boolean requireWrite) {
        for (android.content.UriPermission grant : resolver.getPersistedUriPermissions()) {
            if (grant.getUri().equals(uri) && grant.isReadPermission() && (!requireWrite || grant.isWritePermission())) return true;
            if (!grant.isReadPermission() || requireWrite && !grant.isWritePermission() || !DocumentsContract.isTreeUri(grant.getUri()) || !grant.getUri().getAuthority().equals(uri.getAuthority())) continue;
            try {
                String root = DocumentsContract.getTreeDocumentId(grant.getUri());
                if (DocumentsContract.isTreeUri(uri) && root.equals(DocumentsContract.getTreeDocumentId(uri))) return true;
                DocumentsContract.Path path = DocumentsContract.findDocumentPath(resolver, DocumentsContract.buildDocumentUriUsingTree(grant.getUri(), DocumentsContract.getDocumentId(uri)));
                if (path != null && !path.getPath().isEmpty() && root.equals(path.getPath().get(0)) && DocumentsContract.getDocumentId(uri).equals(path.getPath().get(path.getPath().size() - 1))) return true;
            } catch (Exception ignored) { /* A separate leaf grant does not confer directory authority. */ }
        }
        return false;
    }

    private String identity(Uri uri) throws IOException {
        if (!"content".equals(uri.getScheme()) || uri.getAuthority() == null) throw new IOException("permission");
        String id;
        if (DocumentsContract.isDocumentUri(context, uri)) id = uri.getAuthority() + ":" + DocumentsContract.getDocumentId(uri);
        else {
            Uri canonical = resolver.canonicalize(uri);
            id = (canonical == null ? uri : canonical).toString();
        }
        return "android:" + MarkdownCodec.digest(id.getBytes(StandardCharsets.UTF_8));
    }

    public Target open(Uri uri, int returnedFlags) throws Exception {
        String identity = identity(uri);
        persist(uri, returnedFlags);
        String name = "document.md";
        long size = -1;
        try (Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) throw new IOException("unavailable");
            int nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME), sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE);
            if (nameColumn >= 0 && !cursor.isNull(nameColumn)) name = cursor.getString(nameColumn);
            if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) size = cursor.getLong(sizeColumn);
        }
        if (!name.toLowerCase(java.util.Locale.ROOT).matches(".*\\.(md|markdown)$")) throw new IOException("protocol");
        if (size > MarkdownCodec.MAX_BYTES) throw new IOException("size");
        boolean writable = context.checkUriPermission(uri, Process.myPid(), Process.myUid(), Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == PackageManager.PERMISSION_GRANTED;
        if (writable && DocumentsContract.isDocumentUri(context, uri)) {
            try (Cursor cursor = resolver.query(uri, new String[]{DocumentsContract.Document.COLUMN_FLAGS}, null, null, null)) {
                writable = cursor != null && cursor.moveToFirst() &&
                        (cursor.getLong(0) & DocumentsContract.Document.FLAG_SUPPORTS_WRITE) != 0;
            }
        }
        MarkdownCodec.Decoded decoded = decode(uri);
        return new Target(uri, identity, name, writable, persistent(uri, writable), decoded);
    }

    public Target reopen(String uri) throws Exception { return open(Uri.parse(uri), 0); }

    private MarkdownCodec.Decoded decode(Uri uri) throws Exception {
        try (InputStream input = resolver.openInputStream(uri)) { return MarkdownCodec.decode(MarkdownCodec.read(input, MarkdownCodec.MAX_BYTES)); }
    }

    public MarkdownCodec.Decoded read(Target target) throws Exception {
        if (!target.identity.equals(identity(target.uri))) throw new IOException("identity-changed");
        return decode(target.uri);
    }

    public void write(Target target, byte[] bytes) throws Exception {
        if (!target.writable) throw new IOException("readonly");
        if (!target.identity.equals(identity(target.uri))) throw new IOException("identity-changed");
        writeUri(target.uri, bytes);
    }

    public void writeUri(Uri uri, byte[] bytes) throws Exception {
        // "w" alone need not truncate with every provider. rwt explicitly requests it.
        ParcelFileDescriptor descriptor = resolver.openFileDescriptor(uri, "rwt");
        if (descriptor == null) throw new IOException("unavailable");
        try (ParcelFileDescriptor.AutoCloseOutputStream output = new ParcelFileDescriptor.AutoCloseOutputStream(descriptor)) {
            output.write(bytes); output.flush();
            if (OsConstants.S_ISREG(Os.fstat(descriptor.getFileDescriptor()).st_mode)) descriptor.getFileDescriptor().sync();
        }
        // Provider acceptance is the limit; this says nothing about cloud sync.
        try (InputStream input = resolver.openInputStream(uri)) {
            if (!MarkdownCodec.digest(MarkdownCodec.read(input, Math.max(MarkdownCodec.MAX_BYTES, bytes.length))).equals(MarkdownCodec.digest(bytes))) throw new IOException("io");
        }
    }

    public Uri child(Uri tree, String parentId, String name) throws Exception {
        Uri child = findChild(tree, parentId, name);
        if (child == null) throw new IOException("unavailable");
        return child;
    }

    public Uri findChild(Uri tree, String parentId, String name) throws Exception {
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId);
        String found = null;
        try (Cursor cursor = resolver.query(children, new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
            if (cursor == null) throw new IOException("unavailable");
            while (cursor.moveToNext()) if (name.equals(cursor.getString(1))) {
                if (found != null) throw new IOException("protocol");
                found = cursor.getString(0);
            }
        }
        if (found == null) return null;
        Uri child = DocumentsContract.buildDocumentUriUsingTree(tree, found);
        // The exact name and ID came from this parent's direct child query.
        // isChildDocument is only public since API 29; our minimum is API 26.
        return child;
    }

    public boolean isParent(Uri tree, Target target) {
        try {
            if (!tree.getAuthority().equals(target.uri.getAuthority()) || !DocumentsContract.isDocumentUri(context, target.uri)) return false;
            return identity(child(tree, DocumentsContract.getTreeDocumentId(tree), target.name)).equals(target.identity);
        } catch (Exception error) { return false; }
    }

    public Uri resource(Uri tree, String reference) throws Exception {
        String clean = reference.split("[?#]", 2)[0];
        if (clean.startsWith("/") || clean.contains("\\") || clean.contains(":")) throw new IOException("permission");
        Uri current = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
        for (String encoded : clean.split("/", -1)) {
            String part = Uri.decode(encoded);
            if (part.isEmpty() || part.equals(".") || part.equals("..") || part.contains("/") || part.contains("\\") || part.contains("%") || part.indexOf('\0') >= 0) throw new IOException("permission");
            current = child(tree, DocumentsContract.getDocumentId(current), part);
        }
        return current;
    }

    public static String imageMime(String name) {
        return MarkdownImages.mime(name);
    }
}
