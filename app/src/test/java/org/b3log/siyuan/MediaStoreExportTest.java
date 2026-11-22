package org.b3log.siyuan;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.provider.MediaStore;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.InOrder;
import org.mockito.MockedConstruction;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MediaStoreExportTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void publishesOriginalBytesAfterWritingImage() throws Exception {
        final byte[] original = {1, 2, 3};
        final File source = temporaryFolder.newFile();
        Files.write(source.toPath(), original);
        final JSAndroid.ExportFileLease lease = new JSAndroid.ExportFileLease("lease", source.getPath(),
                "photo.png", original.length);
        final ContentResolver resolver = mock(ContentResolver.class);
        final Uri collection = mock(Uri.class);
        final Uri destination = mock(Uri.class);
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        when(resolver.insert(eq(collection), any(ContentValues.class))).thenReturn(destination);
        when(resolver.openOutputStream(destination, "w")).thenReturn(output);
        when(resolver.update(eq(destination), any(ContentValues.class), isNull(), isNull())).thenAnswer(call -> {
            assertArrayEquals(original, output.toByteArray());
            return 1;
        });

        try (MockedConstruction<ContentValues> values = mockConstruction(ContentValues.class)) {
            assertSame(destination, JSAndroid.saveExportFileToMediaStore(resolver, lease, "image/png", "Pictures",
                    collection));
            final ContentValues pending = values.constructed().get(0);
            final ContentValues published = values.constructed().get(1);
            verify(pending).put(MediaStore.MediaColumns.DISPLAY_NAME, "photo.png");
            verify(pending).put(MediaStore.MediaColumns.MIME_TYPE, "image/png");
            verify(pending).put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures");
            verify(pending).put(MediaStore.MediaColumns.IS_PENDING, 1);
            verify(published).put(MediaStore.MediaColumns.IS_PENDING, 0);
            final InOrder order = inOrder(resolver);
            order.verify(resolver).insert(collection, pending);
            order.verify(resolver).openOutputStream(destination, "w");
            order.verify(resolver).update(destination, published, null, null);
        }
        verify(resolver, never()).delete(destination, null, null);
    }

    @Test
    public void removesPartialVideoWithoutPublishing() throws Exception {
        final File source = temporaryFolder.newFile();
        Files.write(source.toPath(), new byte[]{1});
        final JSAndroid.ExportFileLease lease = new JSAndroid.ExportFileLease("lease", source.getPath(),
                "video.mp4", 3);
        final ContentResolver resolver = mock(ContentResolver.class);
        final Uri collection = mock(Uri.class);
        final Uri destination = mock(Uri.class);
        when(resolver.insert(eq(collection), any(ContentValues.class))).thenReturn(destination);
        when(resolver.openOutputStream(destination, "w")).thenReturn(new ByteArrayOutputStream());

        try (MockedConstruction<ContentValues> ignored = mockConstruction(ContentValues.class)) {
            assertThrows(IllegalStateException.class, () -> JSAndroid.saveExportFileToMediaStore(resolver, lease,
                    "video/mp4", "Movies", collection));
        }
        verify(resolver, never()).update(eq(destination), any(ContentValues.class), isNull(), isNull());
        verify(resolver).delete(destination, null, null);
    }
}
