import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;
import org.pentaho.di.core.Result;
import org.pentaho.di.job.Job;
import org.pentaho.di.job.entries.ftpput.JobEntryFTPPUT;

/** Bounded local staging around the original FTP_PUT, which sends at most 80 files per invocation. */
public final class NativeFtpBatchDelivery {
    public static final int MAX_FILES_PER_PASS = 80;
    private NativeFtpBatchDelivery() { }

    private static final class Item {
        final String name, sha256;
        final long bytes;
        final Object fileKey;
        String state = "PENDING", sourceState = "PRESERVED";
        int pass;
        Item(String name, String sha256, long bytes, Object fileKey) {
            this.name = name; this.sha256 = sha256; this.bytes = bytes; this.fileKey = fileKey;
        }
        Map<String, Object> json() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("name", name); value.put("sha256", sha256); value.put("bytes", bytes);
            value.put("state", state); value.put("sourceState", sourceState); value.put("pass", pass);
            return value;
        }
    }

    public static final class Context implements AutoCloseable {
        final Path root, source, spool;
        final JobEntryFTPPUT entry;
        final Job job;
        final BooleanSupplier stopped;
        final String originalDirectory, configurationSha;
        final List<Item> items = new ArrayList<>();
        final List<Map<String, Object>> passes = new ArrayList<>();
        final boolean remove, onlyNew, rename, binary;
        DirectoryStream<Path> sourceListing;
        SecureDirectoryStream<Path> sourceHandle;
        Object sourceDirectoryKey;
        List<Item> current = List.of();
        Path currentDirectory;
        String state = "PREPARING";
        int cursor, processed;
        boolean closed, completed;
        Context(Path root, Path source, Path spool, JobEntryFTPPUT entry, Job job, BooleanSupplier stopped) throws Exception {
            this.root = root; this.source = source; this.spool = spool; this.entry = entry; this.job = job; this.stopped = stopped;
            originalDirectory = entry.getLocalDirectory();
            configurationSha = hash(entry.getXML().getBytes(StandardCharsets.UTF_8));
            remove = entry.getRemove(); onlyNew = entry.isOnlyPuttingNewFiles(); rename = entry.getRename(); binary = entry.isBinaryMode();
        }
        public Path manifestPath() { return spool.resolve("manifest.json"); }
        public Map<String, Object> summary() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("deliveryId", spool.getFileName().toString()); value.put("state", state);
            value.put("node", entry.getName()); value.put("candidateFiles", items.size());
            value.put("nativeProcessedFiles", processed); value.put("passes", passes.size());
            value.put("pendingFiles", items.stream().filter(item -> item.state.equals("PENDING")).count());
            value.put("uncertainFiles", items.stream().filter(item -> item.state.equals("UNCERTAIN")).count());
            value.put("sourceDeletedFiles", items.stream().filter(item -> item.sourceState.equals("DELETED")).count());
            value.put("manifest", root.relativize(manifestPath()).toString());
            value.put("onlyNewRequested", onlyNew);
            value.put("nativeOnlyNewSemantics", "ORIGINAL_PLUGIN_DECISION_CUSTOM_ARCHIVE_MAY_REPLACE_EXISTING");
            value.put("remoteHashVerified", false); // The helper never pretends to perform RETR verification.
            return value;
        }
        void persist() throws IOException {
            Map<String, Object> value = new LinkedHashMap<>(summary());
            value.put("schemaVersion", 1); value.put("automaticReplay", false); value.put("maxFilesPerPass", MAX_FILES_PER_PASS);
            value.put("sourceDirectory", root.relativize(source).toString()); value.put("configurationSha256", configurationSha);
            value.put("options", Map.of("remove", remove, "rename", rename, "binary", binary, "onlyNew", onlyNew));
            value.put("batches", passes); value.put("files", items.stream().map(Item::json).toList());
            Path temporary = spool.resolve("manifest-" + UUID.randomUUID() + ".tmp");
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                Files.setPosixFilePermissions(temporary, PosixFilePermissions.fromString("rw-------"));
                ByteBuffer buffer = StandardCharsets.UTF_8.encode(KettleWorker.json(value));
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            Files.move(temporary, manifestPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            try (FileChannel directory = FileChannel.open(spool, StandardOpenOption.READ)) { directory.force(true); }
        }
        void event(String phase) { Map<String, Object> value = new LinkedHashMap<>(summary()); value.put("phase", phase); KettleWorker.event("ftp-batch", value); }
        boolean isStopped() { return stopped.getAsBoolean() || job.isStopped() || Thread.currentThread().isInterrupted(); }
        @Override public void close() throws IOException {
            entry.setLocalDirectory(originalDirectory);
            if (!closed) { closed = true; if (sourceListing != null) sourceListing.close(); }
        }
    }

    public static Context prepare(Path operationRoot, Path approvedOutputDirectory, JobEntryFTPPUT entry,
                                  Job job, BooleanSupplier stopped) throws Exception {
        Objects.requireNonNull(entry); Objects.requireNonNull(job); Objects.requireNonNull(stopped);
        Path root = operationRoot.toAbsolutePath().normalize(), output = root.resolve("output");
        Path source = approvedOutputDirectory.toAbsolutePath().normalize();
        if (!source.startsWith(output)) throw new IOException("FTP batch source must be inside this run's output directory");
        directoryTree(root, source);
        Path delivery = root.resolve("delivery");
        if (!Files.exists(delivery, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(delivery, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        directoryTree(root, delivery);
        Path spool = Files.createDirectory(delivery.resolve(UUID.randomUUID().toString()), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        Context context = new Context(root, source, spool, entry, job, stopped);
        try {
            context.sourceDirectoryKey = Files.readAttributes(source, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).fileKey();
            context.sourceListing = Files.newDirectoryStream(source);
            if (context.sourceListing instanceof SecureDirectoryStream<?>) {
                @SuppressWarnings("unchecked") SecureDirectoryStream<Path> secure = (SecureDirectoryStream<Path>) context.sourceListing;
                context.sourceHandle = secure;
            }
            // macOS JDK 17 does not implement SecureDirectoryStream. Revalidate every directory
            // component and its file key and use O_NOFOLLOW file channels on that platform.
            verifySourceDirectory(context);
            String wildcard = entry.environmentSubstitute(entry.getWildcard());
            Pattern pattern = wildcard == null || wildcard.isEmpty() ? null : Pattern.compile(wildcard);
            List<String> names = new ArrayList<>();
            for (Path path : context.sourceListing) {
                String name = path.getFileName().toString();
                BasicFileAttributes attributes = attributes(context, name);
                if (attributes.isSymbolicLink() || !attributes.isDirectory() && !attributes.isRegularFile()) throw new IOException("FTP batch rejects links and special files");
                if (attributes.isRegularFile()) { regular(context, name); if (pattern == null || pattern.matcher(name).matches()) names.add(name); }
            }
            Collections.sort(names); // Stable pass membership prevents remove=N from repeatedly choosing the first 80.
            for (String name : names) {
                BasicFileAttributes before = regular(context, name);
                String digest = hash(context, name);
                BasicFileAttributes after = regular(context, name);
                requireSame(before, after);
                context.items.add(new Item(name, digest, before.size(), before.fileKey()));
            }
            context.persist();
            stage(context);
            return context;
        } catch (Exception error) {
            context.state = "PREPARATION_FAILED";
            try { context.persist(); } catch (IOException ignored) { }
            context.close();
            throw error;
        }
    }

    /** Call after the Job engine executes the staged first pass, before it chooses the outgoing hop. */
    public static Result complete(Context context, Result firstPassResult, int entryNr) {
        Objects.requireNonNull(context);
        if (context.completed) throw new IllegalStateException("FTP batch context cannot be replayed");
        context.completed = true;
        Result result = firstPassResult == null ? new Result() : firstPassResult;
        long filesBefore = result.getNrFilesRetrieved();
        try {
            if (firstPassResult == null) { result.setNrErrors(Math.max(1, result.getNrErrors())); result.setResult(false); }
            while (true) {
                boolean stopped = context.isStopped() || result.isStopped();
                boolean success = result.getResult() && result.getNrErrors() == 0 && !stopped;
                finishPass(context, result, success, stopped);
                if (!success) {
                    context.state = stopped ? "STOPPED" : "FAILED";
                    result.setResult(false); result.setStopped(stopped);
                    if (!stopped) result.setNrErrors(Math.max(1, result.getNrErrors()));
                    break;
                }
                if (context.cursor >= context.items.size()) { context.state = "COMPLETED"; break; }
                stage(context);
                if (context.isStopped()) {
                    context.state = "STOPPED"; result.setResult(false); result.setStopped(true); break;
                }
                Result next = context.entry.execute(result, entryNr); // Same original instance and options, never a substitute uploader.
                if (next != result) throw new IOException("Original FTP entry returned an unexpected Result object");
            }
        } catch (Exception error) {
            context.state = context.isStopped() ? "STOPPED" : "FAILED";
            result.setResult(false); result.setStopped(context.isStopped());
            if (!context.isStopped()) result.setNrErrors(Math.max(1, result.getNrErrors()));
            for (Item item : context.current) if (item.state.equals("STAGED")) item.state = "UNCERTAIN";
            Map<String, Object> fields = new LinkedHashMap<>(context.summary()); fields.put("errorClass", error.getClass().getName());
            fields.put("message", "FTP batch could not confirm delivery or source consistency; inspect the private manifest");
            KettleWorker.event("ftp-batch-error", fields);
        } finally {
            // This counter means files successfully considered by a native pass, including any native only-new decision.
            result.setNrFilesRetrieved(filesBefore + context.processed);
            try { context.close(); context.persist(); context.event("FINAL"); }
            catch (IOException error) { result.setResult(false); result.setNrErrors(Math.max(1, result.getNrErrors())); KettleWorker.event("ftp-batch-error", Map.of("errorClass", error.getClass().getName(), "message", "FTP batch manifest finalization failed")); }
        }
        return result;
    }

    private static void stage(Context context) throws Exception {
        if (context.isStopped()) throw new IOException("FTP batch stopped before staging the next pass");
        int number = context.passes.size() + 1;
        context.currentDirectory = Files.createDirectory(context.spool.resolve(String.format(Locale.ROOT, "pass-%04d", number)), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        context.current = new ArrayList<>(context.items.subList(context.cursor, Math.min(context.items.size(), context.cursor + MAX_FILES_PER_PASS)));
        for (Item item : context.current) {
            BasicFileAttributes before = regular(context, item.name);
            if (!Objects.equals(item.fileKey, before.fileKey()) || item.bytes != before.size()) throw new IOException("FTP source changed after its delivery snapshot");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            Path staged = context.currentDirectory.resolve(item.name);
            try (SeekableByteChannel input = openSource(context, item.name);
                 FileChannel output = FileChannel.open(staged, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                Files.setPosixFilePermissions(staged, PosixFilePermissions.fromString("rw-------"));
                ByteBuffer buffer = ByteBuffer.allocate(65536);
                while (input.read(buffer) != -1) { buffer.flip(); digest.update(buffer.asReadOnlyBuffer()); while (buffer.hasRemaining()) output.write(buffer); buffer.clear(); }
                output.force(true);
            }
            requireSame(before, regular(context, item.name));
            if (!item.sha256.equals(HexFormat.of().formatHex(digest.digest()))) throw new IOException("FTP staged bytes differ from the immutable delivery snapshot");
            if (!item.sha256.equals(hash(staged))) throw new IOException("FTP staged copy hash mismatch");
            item.pass = number; item.state = "STAGED";
        }
        context.cursor += context.current.size();
        context.passes.add(new LinkedHashMap<>(Map.of("pass", number, "state", "PREPARED", "files", context.current.size(), "nativeResultBoolean", false, "nativeErrors", 0)));
        context.state = "DELIVERING";
        context.persist(); // Intent precedes the native call, including the first call performed by the Job engine.
        context.entry.setLocalDirectory(context.currentDirectory.toString());
        context.event("PREPARED");
    }

    private static void finishPass(Context context, Result result, boolean success, boolean stopped) throws Exception {
        Map<String, Object> pass = context.passes.get(context.passes.size() - 1);
        pass.put("nativeResultBoolean", result.getResult()); pass.put("nativeErrors", result.getNrErrors());
        pass.put("state", success ? "NATIVE_COMPLETED" : stopped ? "STOPPED" : "FAILED");
        for (Item item : context.current) {
            Path staged = context.currentDirectory.resolve(item.name);
            boolean stagedExists = Files.exists(staged, LinkOption.NOFOLLOW_LINKS);
            if (stagedExists && (Files.isSymbolicLink(staged) || !Files.isRegularFile(staged, LinkOption.NOFOLLOW_LINKS))) throw new IOException("FTP staged file was replaced by a link or special file");
            item.state = success ? "NATIVE_COMPLETED" : "UNCERTAIN";
            if (context.remove && !stagedExists) {
                BasicFileAttributes original = regular(context, item.name);
                if (!Objects.equals(item.fileKey, original.fileKey()) || !item.sha256.equals(hash(context, item.name))) { item.sourceState = "CHANGED_PRESERVED"; throw new IOException("FTP source changed; refusing to delete it"); }
                requireSame(original, regular(context, item.name));
                verifySourceDirectory(context);
                if (context.sourceHandle != null) context.sourceHandle.deleteFile(Path.of(item.name));
                else Files.delete(context.source.resolve(item.name));
                item.sourceState = "DELETED";
                item.state = "NATIVE_DELETED_STAGED";
            }
        }
        if (success) context.processed += context.current.size();
        context.persist(); context.event("PASS_FINISHED");
    }

    private static void directoryTree(Path root, Path directory) throws IOException {
        if (!directory.startsWith(root)) throw new IOException("FTP directory is outside the run");
        Path cursor = root;
        if (Files.isSymbolicLink(cursor) || !Files.isDirectory(cursor, LinkOption.NOFOLLOW_LINKS)) throw new IOException("FTP run directory is not a normal directory");
        for (Path part : root.relativize(directory)) { cursor = cursor.resolve(part); if (Files.isSymbolicLink(cursor) || !Files.isDirectory(cursor, LinkOption.NOFOLLOW_LINKS)) throw new IOException("FTP directory cannot traverse links"); }
    }
    private static void verifySourceDirectory(Context context) throws IOException {
        directoryTree(context.root, context.source);
        if (!Objects.equals(context.sourceDirectoryKey, Files.readAttributes(context.source, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).fileKey())) throw new IOException("FTP source directory was replaced");
    }
    private static BasicFileAttributes attributes(Context context, String name) throws IOException {
        verifySourceDirectory(context);
        if (context.sourceHandle != null) return context.sourceHandle.getFileAttributeView(Path.of(name), java.nio.file.attribute.BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS).readAttributes();
        return Files.readAttributes(context.source.resolve(name), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    }
    private static SeekableByteChannel openSource(Context context, String name) throws IOException {
        verifySourceDirectory(context);
        return context.sourceHandle != null ? context.sourceHandle.newByteChannel(Path.of(name), Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) : FileChannel.open(context.source.resolve(name), StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
    }
    private static BasicFileAttributes regular(Context context, String name) throws IOException {
        directoryTree(context.root, context.source);
        BasicFileAttributes value = attributes(context, name);
        if (!value.isRegularFile() || value.isSymbolicLink() || ((Number) Files.getAttribute(context.source.resolve(name), "unix:nlink", LinkOption.NOFOLLOW_LINKS)).longValue() != 1) throw new IOException("FTP batch requires ordinary files without hard links");
        return value;
    }
    private static void requireSame(BasicFileAttributes first, BasicFileAttributes second) throws IOException {
        if (!Objects.equals(first.fileKey(), second.fileKey()) || first.size() != second.size() || !first.lastModifiedTime().equals(second.lastModifiedTime())) throw new IOException("FTP source changed during snapshot verification");
    }
    private static String hash(Context context, String name) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (SeekableByteChannel channel = openSource(context, name)) {
            ByteBuffer buffer = ByteBuffer.allocate(65536);
            while (channel.read(buffer) != -1) { buffer.flip(); digest.update(buffer); buffer.clear(); }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    private static String hash(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            ByteBuffer buffer = ByteBuffer.allocate(65536);
            while (channel.read(buffer) != -1) { buffer.flip(); digest.update(buffer); buffer.clear(); }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    private static String hash(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
}
