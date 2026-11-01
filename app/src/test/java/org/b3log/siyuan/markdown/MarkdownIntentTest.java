package org.b3log.siyuan.markdown;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import org.b3log.siyuan.MarkdownActivity;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public final class MarkdownIntentTest {
    @Test public void genericFileRejectionOnlyAppliesToExplicitContentViewRequests() {
        assertTrue(MarkdownRouting.genericDocument("android.intent.action.VIEW", "content", "text/plain"));
        assertTrue(MarkdownRouting.genericDocument("android.intent.action.VIEW", "content", "application/octet-stream"));
        assertFalse(MarkdownRouting.genericDocument("android.intent.action.SEND", "content", "text/plain"));
        assertFalse(MarkdownRouting.genericDocument("android.intent.action.VIEW", "https", "text/plain"));
        assertFalse(MarkdownRouting.genericDocument("android.intent.action.VIEW", "content", "text/markdown"));
    }
    @Test public void opaqueContentUriUsesProviderNameWithoutCapturingOtherAttachments() {
        Context context = mock(Context.class); ContentResolver resolver = mock(ContentResolver.class);
        when(context.getContentResolver()).thenReturn(resolver);
        Intent intent = mock(Intent.class); Uri uri = mock(Uri.class); Cursor cursor = mock(Cursor.class);
        when(intent.getAction()).thenReturn(Intent.ACTION_VIEW); when(intent.getData()).thenReturn(uri);
        when(uri.getScheme()).thenReturn("content"); when(intent.getType()).thenReturn("application/octet-stream");
        when(resolver.query(eq(uri), any(String[].class), isNull(), isNull(), isNull())).thenReturn(cursor);
        when(cursor.moveToFirst()).thenReturn(true); when(cursor.getString(0)).thenReturn("README.MARKDOWN");
        assertTrue(MarkdownActivity.isMarkdownIntent(context, intent));
        when(cursor.getString(0)).thenReturn("document.pdf"); when(intent.getType()).thenReturn("application/pdf");
        assertFalse(MarkdownActivity.isMarkdownIntent(context, intent));
        when(cursor.getString(0)).thenReturn("notes.txt"); when(intent.getType()).thenReturn("text/plain");
        assertFalse(MarkdownActivity.isMarkdownIntent(context, intent));
        when(intent.getType()).thenReturn("text/markdown");
        assertTrue(MarkdownActivity.isMarkdownIntent(context, intent));
        when(intent.getAction()).thenReturn(Intent.ACTION_SEND);
        assertFalse(MarkdownActivity.isMarkdownIntent(context, intent));
        when(intent.getAction()).thenReturn(Intent.ACTION_VIEW); when(uri.getScheme()).thenReturn("https");
        assertFalse(MarkdownActivity.isMarkdownIntent(context, intent));
    }

    @Test public void declaredMarkdownMimeReachesPermissionUiWhenMetadataIsDenied() {
        Context context = mock(Context.class); ContentResolver resolver = mock(ContentResolver.class);
        when(context.getContentResolver()).thenReturn(resolver);
        Intent intent = mock(Intent.class); Uri uri = mock(Uri.class);
        when(intent.getAction()).thenReturn(Intent.ACTION_VIEW); when(intent.getData()).thenReturn(uri);
        when(uri.getScheme()).thenReturn("content"); when(intent.getType()).thenReturn("text/markdown");
        when(resolver.query(eq(uri), any(String[].class), isNull(), isNull(), isNull())).thenThrow(new SecurityException());
        assertTrue(MarkdownActivity.isMarkdownIntent(context, intent));
        when(intent.getType()).thenReturn("application/pdf");
        assertFalse(MarkdownActivity.isMarkdownIntent(context, intent));
    }
}
