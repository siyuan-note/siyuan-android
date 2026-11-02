package org.b3log.siyuan.markdown;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

/** Checksummed, atomic private records. Tombstones retain the draft revision watermark. */
public class MarkdownStore {
    private static final int MAX_RECORD = 64 * 1024 * 1024;
    private static final long MAX_TOTAL = 256L * 1024 * 1024;
    private static final int MAX_RECORDS = 1024;
    private final File directory;
    private final DirectorySync directorySync;
    private final Set<String> unconfirmed = new HashSet<>();

    @FunctionalInterface public interface DirectorySync { void sync(File directory) throws IOException; }

    public static final class Record {
        public final long generation;
        public final long revision;
        public final String payload;
        Record(long generation, long revision, String payload) {
            this.generation = generation; this.revision = revision; this.payload = payload;
        }
    }

    public MarkdownStore(File directory) throws IOException {
        this(directory, ignored -> { });
    }

    public MarkdownStore(File directory, DirectorySync directorySync) throws IOException {
        this.directory = directory.getCanonicalFile();
        this.directorySync = directorySync;
        if (!directory.isDirectory()) {
            if (!directory.mkdirs()) throw new IOException("space");
            directorySync.sync(this.directory.getParentFile());
        }
        recoverPending();
    }

    private File file(String key) throws IOException {
        if (!key.matches("[a-zA-Z0-9_-]{1,160}")) throw new IOException("protocol");
        return new File(directory, key + ".record");
    }

    private static String checksum(String key, long generation, long revision, byte[] data) {
        return MarkdownCodec.digest((key + ":" + generation + ":" + revision + ":" + MarkdownCodec.digest(data)).getBytes(StandardCharsets.UTF_8));
    }

    public synchronized Record read(String key) throws IOException {
        if (unconfirmed.contains(key)) confirmDirectory(key);
        File source = file(key);
        if (!source.exists()) return null;
        return readFile(source, key);
    }

    private void confirmDirectory(String key) throws IOException {
        unconfirmed.add(key);
        directorySync.sync(directory);
        unconfirmed.remove(key);
    }

    private Record readFile(File source, String key) throws IOException {
        if (source.length() > MAX_RECORD + 1024) throw new IOException("size");
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(Files.readAllBytes(source.toPath())))) {
            if (input.readInt() != 0x4d440001) throw new IOException("protocol");
            long generation = input.readLong(), revision = input.readLong();
            String checksum = input.readUTF();
            int length = input.readInt();
            if (generation < 0 || revision < 0 || length < 0 || length > MAX_RECORD) throw new IOException("protocol");
            byte[] bytes = new byte[length];
            input.readFully(bytes);
            if (input.read() != -1 || !checksum(key, generation, revision, bytes).equals(checksum)) throw new IOException("io");
            return new Record(generation, revision, new String(bytes, StandardCharsets.UTF_8));
        }
    }

    private void recoverPending() throws IOException {
        File[] files = directory.listFiles();
        if (files == null) throw new IOException("io");
        for (File pending : files) {
            String name = pending.getName();
            java.util.regex.Matcher match = java.util.regex.Pattern.compile(
                    "^([a-zA-Z0-9_-]{1,160})-[0-9]{14}-[a-z0-9]{7}\\.pending$").matcher(name);
            if (!match.matches()) continue;
            String key = match.group(1);
            try {
                Record candidate = readFile(pending, key), current = read(key);
                if (current == null || candidate.generation > current.generation || candidate.generation == current.generation && candidate.revision > current.revision) {
                    Files.move(pending.toPath(), file(key).toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                    confirmDirectory(key);
                } else if (candidate.generation < current.generation || candidate.revision < current.revision
                        || candidate.payload.equals(current.payload) || current.payload.isEmpty()) {
                    // A verified newer snapshot/tombstone already supersedes this complete temporary record.
                    Files.delete(pending.toPath());
                    confirmDirectory(key);
                }
            } catch (Exception ignored) { /* Never replace a corrupt original or discard incomplete bytes. */ }
        }
    }

    public synchronized boolean hasUnrecoveredPending() throws IOException {
        File[] files = directory.listFiles();
        if (files == null) throw new IOException("io");
        for (File entry : files) if (entry.getName().endsWith(".pending")) return true;
        return false;
    }

    public synchronized void put(String key, long generation, long revision, String payload) throws IOException {
        if (generation < 0 || revision < 0) throw new IOException("protocol");
        File destination = file(key);
        Record previous = read(key);
        if (previous != null && (previous.generation > generation || previous.generation == generation && previous.revision > revision)) return;
        // Clearing and a delayed put of the same revision cannot resurrect a draft.
        if (key.startsWith("draft-") && previous != null && previous.generation == generation && previous.revision == revision && previous.payload.isEmpty()) return;
        byte[] data = payload.getBytes(StandardCharsets.UTF_8);
        if (data.length > MAX_RECORD) throw new IOException("size");
        File[] files = directory.listFiles();
        if (files == null) throw new IOException("io");
        long total = data.length + 128;
        int count = 0;
        for (File entry : files) {
            if (!entry.equals(destination)) total += entry.length();
            if (entry.getName().endsWith(".record")) count++;
        }
        if (total > MAX_TOTAL || !destination.exists() && count >= MAX_RECORDS) throw new IOException("space");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(0x4d440001); output.writeLong(generation); output.writeLong(revision);
            output.writeUTF(checksum(key, generation, revision, data)); output.writeInt(data.length); output.write(data);
        }
        File pending = MarkdownIds.temporary(directory, key + "-", ".pending");
        try (FileOutputStream output = new FileOutputStream(pending)) { output.write(bytes.toByteArray()); output.getFD().sync(); }
        // If promotion fails, leave the checksummed pending bytes for diagnosis
        // and recovery rather than deleting the only new durable snapshot.
        Files.move(pending.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        confirmDirectory(key);
    }

    public synchronized List<String> keys(String prefix) throws IOException {
        File[] files = directory.listFiles();
        if (files == null) throw new IOException("io");
        List<String> keys = new ArrayList<>();
        for (File file : files) if (file.getName().startsWith(prefix) && file.getName().endsWith(".record")) keys.add(file.getName().substring(0, file.getName().length() - 7));
        return keys;
    }

    public synchronized void remove(String key) throws IOException {
        if (Files.deleteIfExists(file(key).toPath())) confirmDirectory(key);
        else if (unconfirmed.contains(key)) confirmDirectory(key);
    }
}
