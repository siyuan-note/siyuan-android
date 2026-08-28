/*
 * SiYuan - From thought to insight, with agents
 * Copyright (c) 2020-present, b3log.org
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.b3log.siyuan;

import android.content.Context;
import android.util.AtomicFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 闪念速记草稿持久化。
 */
final class ShorthandDraftStore {

    private static final long SAVE_DELAY_MILLIS = 250;
    private static final Object LOCK = new Object();
    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "shorthand-draft");
        thread.setDaemon(true);
        return thread;
    });

    private static ScheduledFuture<?> pendingSave;
    private static long revision;
    private static boolean available = true;

    private ShorthandDraftStore() {
    }

    static String load(final Context context) {
        synchronized (LOCK) {
            try {
                final AtomicFile file = getDraftFile(context.getApplicationContext());
                if (!hasDraftArtifacts(file)) {
                    available = true;
                    return "";
                }
                final String content = new String(file.readFully(), StandardCharsets.UTF_8);
                available = true;
                return content;
            } catch (final Exception e) {
                markUnavailableLocked();
                Utils.logError("shortcut", "Load draft failed", e);
                return null;
            }
        }
    }

    static void saveAsync(final Context context, final String content) {
        final Context appContext = context.getApplicationContext();
        final String snapshot = content;
        synchronized (LOCK) {
            if (!available) {
                return;
            }
            final long taskRevision = ++revision;
            cancelPendingLocked();
            pendingSave = EXECUTOR.schedule(() -> {
                synchronized (LOCK) {
                    if (!available || taskRevision != revision) {
                        return;
                    }
                    writeLocked(appContext, snapshot);
                    if (taskRevision == revision) {
                        pendingSave = null;
                    }
                }
            }, SAVE_DELAY_MILLIS, TimeUnit.MILLISECONDS);
        }
    }

    static boolean saveNow(final Context context, final String content) {
        final Context appContext = context.getApplicationContext();
        final String snapshot = content;
        final Future<Boolean> future;
        synchronized (LOCK) {
            if (!available) {
                return false;
            }
            final long taskRevision = ++revision;
            cancelPendingLocked();
            future = EXECUTOR.submit(() -> {
                synchronized (LOCK) {
                    if (!available) {
                        return false;
                    }
                    if (taskRevision != revision) {
                        return true;
                    }
                    return writeLocked(appContext, snapshot);
                }
            });
        }
        return await(future);
    }

    static boolean clearNow(final Context context) {
        return saveNow(context, "");
    }

    private static AtomicFile getDraftFile(final Context context) throws IOException {
        final File dir = new File(context.getNoBackupFilesDir(), "shorthand");
        if ((!dir.exists() && !dir.mkdirs()) || !dir.isDirectory()) {
            throw new IOException("Create shorthand draft directory failed [" + dir.getAbsolutePath() + "]");
        }
        return new AtomicFile(new File(dir, "draft.md"));
    }

    private static boolean writeLocked(final Context context, final String content) {
        AtomicFile file = null;
        FileOutputStream output = null;
        try {
            file = getDraftFile(context);
            output = file.startWrite();
            output.write(content.getBytes(StandardCharsets.UTF_8));
            file.finishWrite(output);
            output = null;
            if (content.isEmpty()) {
                file.delete();
                return !hasDraftArtifacts(file);
            }
            return true;
        } catch (final Exception e) {
            if (null != file && null != output) {
                try {
                    file.failWrite(output);
                } catch (final Exception failWriteError) {
                    Utils.logError("shortcut", "Rollback draft write failed", failWriteError);
                }
            }
            Utils.logError("shortcut", "Save draft failed", e);
            if (content.isEmpty() && null != file) {
                file.delete();
                return !hasDraftArtifacts(file);
            }
            return false;
        }
    }

    private static boolean await(final Future<Boolean> future) {
        try {
            return future.get();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            Utils.logError("shortcut", "Wait for draft write interrupted", e);
            return false;
        } catch (final ExecutionException e) {
            Utils.logError("shortcut", "Wait for draft write failed", e);
            return false;
        }
    }

    private static boolean hasDraftArtifacts(final AtomicFile file) {
        final File baseFile = file.getBaseFile();
        final String path = baseFile.getAbsolutePath();
        return baseFile.exists() || new File(path + ".bak").exists() || new File(path + ".new").exists();
    }

    private static void markUnavailableLocked() {
        available = false;
        revision++;
        cancelPendingLocked();
    }

    private static void cancelPendingLocked() {
        if (null != pendingSave) {
            pendingSave.cancel(false);
            pendingSave = null;
        }
    }
}
