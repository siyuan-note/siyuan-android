package org.b3log.siyuan.markdown;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import org.junit.Test;
import org.mockito.MockedStatic;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real SAF adapter logic with a bounded platform boundary. No alternate tree implementation. */
public final class MarkdownTreeFilesTest {
    private Context context(ContentResolver resolver) {
        Context context = mock(Context.class); when(context.getApplicationContext()).thenReturn(context); when(context.getContentResolver()).thenReturn(resolver); return context;
    }
    private Uri uri(String name) {
        Uri uri = mock(Uri.class); when(uri.getScheme()).thenReturn("content"); when(uri.getAuthority()).thenReturn("provider"); when(uri.toString()).thenReturn("content://provider/" + name); return uri;
    }
    @Test public void singleFileGrantNeverEnumeratesOrInventsParent() throws Exception {
        ContentResolver resolver = mock(ContentResolver.class); Context context = context(resolver); Uri leaf = uri("leaf");
        UriPermission grant = mock(UriPermission.class); when(grant.getUri()).thenReturn(leaf); when(grant.isReadPermission()).thenReturn(true);
        when(resolver.getPersistedUriPermissions()).thenReturn(Collections.singletonList(grant));
        try (MockedStatic<DocumentsContract> contract = mockStatic(DocumentsContract.class); MockedStatic<android.os.Process> process = mockStatic(android.os.Process.class)) {
            contract.when(() -> DocumentsContract.isDocumentUri(context, leaf)).thenReturn(true);
            contract.when(() -> DocumentsContract.isTreeUri(leaf)).thenReturn(false);
            MarkdownFiles.Target target = new MarkdownFiles.Target(leaf, "id", "A.md", true, true, MarkdownCodec.decode(new byte[0]));
            assertNull(new MarkdownTreeFiles(context).parent(target));
            verify(resolver, never()).query(any(), any(), any(), any(), any(), any(android.os.CancellationSignal.class));
            contract.verify(() -> DocumentsContract.findDocumentPath(any(), any()), never());
        }
    }
    @Test public void childOpenedThroughPersistedTreeReportsPersistentPermission() throws Exception {
        ContentResolver resolver = mock(ContentResolver.class); Context context = context(resolver); Uri tree = uri("tree"), child = uri("tree/document");
        UriPermission grant = mock(UriPermission.class); when(grant.getUri()).thenReturn(tree); when(grant.isReadPermission()).thenReturn(true); when(grant.isWritePermission()).thenReturn(true);
        when(resolver.getPersistedUriPermissions()).thenReturn(Collections.singletonList(grant));
        Cursor metadata = mock(Cursor.class); when(metadata.moveToFirst()).thenReturn(true); when(metadata.getColumnIndex(OpenableColumns.DISPLAY_NAME)).thenReturn(0); when(metadata.getColumnIndex(OpenableColumns.SIZE)).thenReturn(1); when(metadata.getString(0)).thenReturn("A.md"); when(metadata.getLong(1)).thenReturn(1L);
        Cursor flags = mock(Cursor.class); when(flags.moveToFirst()).thenReturn(true); when(flags.getLong(0)).thenReturn((long) DocumentsContract.Document.FLAG_SUPPORTS_WRITE);
        when(resolver.query(eq(child), any(String[].class), isNull(), isNull(), isNull())).thenAnswer(call -> ((String[]) call.getArgument(1)).length == 1 ? flags : metadata);
        when(resolver.openInputStream(child)).thenAnswer(call -> new ByteArrayInputStream("a".getBytes(StandardCharsets.UTF_8)));
        try (MockedStatic<DocumentsContract> contract = mockStatic(DocumentsContract.class); MockedStatic<android.os.Process> process = mockStatic(android.os.Process.class)) {
            contract.when(() -> DocumentsContract.isDocumentUri(context, child)).thenReturn(true);
            contract.when(() -> DocumentsContract.getDocumentId(child)).thenReturn("opaque-child");
            contract.when(() -> DocumentsContract.isTreeUri(tree)).thenReturn(true); contract.when(() -> DocumentsContract.isTreeUri(child)).thenReturn(true);
            contract.when(() -> DocumentsContract.getTreeDocumentId(tree)).thenReturn("opaque-root"); contract.when(() -> DocumentsContract.getTreeDocumentId(child)).thenReturn("opaque-root");
            MarkdownFiles.Target opened = new MarkdownFiles(context).open(child, 0);
            assertTrue(opened.persistent); assertTrue(opened.writable);
            verify(resolver, never()).takePersistableUriPermission(any(), anyInt());
        }
    }
    @Test public void directChildQueryWorksWithoutApi29AndRejectsAmbiguousNames() throws Exception {
        ContentResolver resolver = mock(ContentResolver.class); Context context = context(resolver); Uri tree = uri("tree"), listing = uri("children"), child = uri("child");
        Cursor cursor = mock(Cursor.class); when(cursor.moveToNext()).thenReturn(true, false); when(cursor.getString(0)).thenReturn("child-id"); when(cursor.getString(1)).thenReturn("A.md");
        when(resolver.query(eq(listing), any(String[].class), isNull(), isNull(), isNull())).thenReturn(cursor);
        try (MockedStatic<DocumentsContract> contract = mockStatic(DocumentsContract.class)) {
            contract.when(() -> DocumentsContract.buildChildDocumentsUriUsingTree(tree, "root-id")).thenReturn(listing);
            contract.when(() -> DocumentsContract.buildDocumentUriUsingTree(tree, "child-id")).thenReturn(child);
            assertSame(child, new MarkdownFiles(context).child(tree, "root-id", "A.md"));
            contract.verify(() -> DocumentsContract.isChildDocument(any(), any(), any()), never());
            when(cursor.moveToNext()).thenReturn(true, true, false);
            try { new MarkdownFiles(context).child(tree, "root-id", "A.md"); fail(); } catch (java.io.IOException expected) { assertEquals("protocol", expected.getMessage()); }
        }
    }
}
