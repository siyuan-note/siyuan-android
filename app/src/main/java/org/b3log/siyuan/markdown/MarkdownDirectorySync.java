package org.b3log.siyuan.markdown;

import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;

/** A file fsync alone does not confirm the directory entry of an atomic rename. */
final class MarkdownDirectorySync {
    private MarkdownDirectorySync() { }

    static void sync(File directory) throws IOException {
        FileDescriptor descriptor = null;
        try {
            descriptor = Os.open(directory.getAbsolutePath(), OsConstants.O_RDONLY, 0);
            if (!OsConstants.S_ISDIR(Os.fstat(descriptor).st_mode)) throw new IOException("protocol");
            Os.fsync(descriptor);
        } catch (ErrnoException failure) {
            throw new IOException(failure.errno == OsConstants.ENOSPC ? "space" : "io", failure);
        } finally {
            if (descriptor != null) {
                try { Os.close(descriptor); } catch (ErrnoException failure) { throw new IOException("io", failure); }
            }
        }
    }
}
