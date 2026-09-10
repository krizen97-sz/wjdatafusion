package com.hm.manage.service.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hm.common.exception.ServiceException;
import com.hm.manage.service.governance.DataGovernanceModels.StoredRun;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Immutable complete artifacts; publication is independent from, and never derived from, UI previews. */
@Component
public class DataGovernanceArtifactStore
{
    public static final int MAX_ARTIFACTS = 100;
    public static final long MAX_ARTIFACT_BYTES = 8L * 1024 * 1024;
    public static final long MAX_TOTAL_BYTES = 32L * 1024 * 1024;
    private static final int MAX_MANIFEST_BYTES = 256 * 1024;
    private final DataGovernanceProperties properties;
    private final ObjectMapper mapper = new ObjectMapper().enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public record Artifact(String id, int order, String filename, String contentType, long byteSize, String sha256) { }
    public record Manifest(String runId, long submitterId, String definitionHash, String publishedAt, List<Artifact> artifacts)
    { public Manifest { artifacts = List.copyOf(artifacts); } }
    private record Owner(String runId, long submitterId, String definitionHash) { }
    @FunctionalInterface public interface ContentWriter { void writeTo(OutputStream output) throws IOException; }

    public static final class Stage
    {
        private final DataGovernanceArtifactStore store;
        private final Path directory;
        private final Owner owner;
        private final List<Artifact> artifacts = new ArrayList<>();
        private long byteSize;
        private boolean failed;
        private boolean published;
        private Stage(DataGovernanceArtifactStore store, Path directory, Owner owner)
        { this.store = store; this.directory = directory; this.owner = owner; }
    }

    public DataGovernanceArtifactStore(DataGovernanceProperties properties) { this.properties = properties; }

    public Stage begin(StoredRun stored)
    {
        if (stored == null || stored.run == null || stored.ownerId <= 0) throw new ServiceException("产物所属运行无效");
        String id = uuid(stored.run.id); digest(stored.run.definitionHash);
        try
        {
            Path run = root().resolve(id);
            if (Files.exists(run, LinkOption.NOFOLLOW_LINKS)) throw new ServiceException("运行产物目录已存在，禁止覆盖");
            createDirectory(run); createDirectory(run.resolve("staging"));
            Owner owner = new Owner(id, stored.ownerId, stored.run.definitionHash);
            writeAtomic(run.resolve("owner.json"), owner);
            return new Stage(this, run, owner);
        }
        catch (ServiceException e) { throw e; }
        catch (IOException e) { throw new ServiceException("无法创建运行产物暂存目录"); }
    }

    public Artifact capture(Stage stage, String filename, String contentType, long expectedBytes, ContentWriter writer)
    {
        synchronized (stage)
        {
            Path temporary = null;
            try
            {
                checkStage(stage);
                validateFilename(filename); validateContentType(contentType);
                if (stage.artifacts.size() >= MAX_ARTIFACTS || expectedBytes < 0 || expectedBytes > MAX_ARTIFACT_BYTES
                    || expectedBytes > MAX_TOTAL_BYTES - stage.byteSize) throw new ServiceException("运行产物超过数量或字节上限");
                String id = UUID.randomUUID().toString();
                Path staging = stage.directory.resolve("staging"); directory(staging);
                temporary = staging.resolve(id + ".part");
                MessageDigest hash = sha256();
                long[] written = {0};
                try (FileChannel file = newFile(temporary))
                {
                    OutputStream output = new OutputStream()
                    {
                        @Override public void write(int value) throws IOException { write(new byte[]{(byte) value}, 0, 1); }
                        @Override public void write(byte[] bytes, int offset, int length) throws IOException
                        {
                            java.util.Objects.checkFromIndexSize(offset, length, bytes.length);
                            if (length > expectedBytes - written[0]) throw new ServiceException("完整产物超出引擎声明长度");
                            ByteBuffer buffer = ByteBuffer.wrap(bytes, offset, length);
                            while (buffer.hasRemaining()) file.write(buffer);
                            hash.update(bytes, offset, length); written[0] += length;
                        }
                    };
                    writer.writeTo(output);
                    if (written[0] != expectedBytes) throw new ServiceException("完整产物长度不符，禁止发布");
                    file.force(true);
                }
                Path destination = staging.resolve(id + ".bin");
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE); temporary = null;
                forceDirectory(staging);
                Artifact artifact = new Artifact(id, stage.artifacts.size() + 1, filename, contentType, written[0], HexFormat.of().formatHex(hash.digest()));
                stage.artifacts.add(artifact); stage.byteSize += written[0];
                return artifact;
            }
            catch (RuntimeException e) { stage.failed = true; throw e; }
            catch (IOException e) { stage.failed = true; throw new ServiceException("完整产物读取或落盘中断，未发布清单"); }
            finally
            {
                // Only this operation's incomplete temporary file is removed; complete artifacts are retained.
                if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
            }
        }
    }

    public Manifest publish(Stage stage, StoredRun stored)
    {
        synchronized (stage)
        {
            checkStage(stage);
            if (stored == null || stored.run == null || !stage.owner.runId.equals(stored.run.id) || stage.owner.submitterId != stored.ownerId
                || !stage.owner.definitionHash.equals(stored.run.definitionHash) || !"SUCCEEDED".equals(stored.run.status) || !stored.run.cleanupConfirmed
                || stage.artifacts.isEmpty()) throw new ServiceException("只有成功且清理确认的非空完整产物可以发布");
            try
            {
                for (Artifact artifact : stage.artifacts)
                    try (FileChannel ignored = verifiedFile(stage.directory.resolve("staging"), artifact)) { }
                Path content = stage.directory.resolve("content");
                if (Files.exists(content, LinkOption.NOFOLLOW_LINKS)) throw new ServiceException("产物内容目录已存在，禁止覆盖");
                Files.move(stage.directory.resolve("staging"), content, StandardCopyOption.ATOMIC_MOVE);
                forceDirectory(stage.directory);
                Manifest manifest = new Manifest(stage.owner.runId, stage.owner.submitterId, stage.owner.definitionHash,
                    Instant.now().toString(), stage.artifacts);
                writeAtomic(stage.directory.resolve("manifest.json"), manifest);
                stage.published = true;
                stored.run.artifactsManifestAvailable = true; stored.run.artifactCount = stage.artifacts.size();
                return manifest;
            }
            catch (RuntimeException e) { stage.failed = true; throw e; }
            catch (IOException e) { stage.failed = true; throw new ServiceException("产物清单原子发布失败，内容保留待核查"); }
        }
    }

    public Manifest manifest(String runId, long submitterId)
    {
        if (submitterId <= 0) throw new ServiceException("产物清单不存在或无权访问");
        try
        {
            Path run = runDirectory(runId); Owner owner = readOwner(run);
            if (owner.submitterId != submitterId || !owner.runId.equals(runId)) throw new ServiceException("产物清单不存在或无权访问");
            Path file = run.resolve("manifest.json"); regularFile(file);
            if (Files.size(file) > MAX_MANIFEST_BYTES) throw new ServiceException("产物清单无效");
            Manifest result;
            try (InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) { result = mapper.readValue(input, Manifest.class); }
            if (!result.runId.equals(runId) || result.submitterId != submitterId || !result.definitionHash.equals(owner.definitionHash))
                throw new ServiceException("产物清单归属校验失败");
            digest(result.definitionHash); Instant.parse(result.publishedAt);
            long total = 0; int order = 0; var ids = new HashSet<String>();
            if (result.artifacts.isEmpty() || result.artifacts.size() > MAX_ARTIFACTS) throw new ServiceException("产物清单数量无效");
            for (Artifact artifact : result.artifacts)
            {
                uuid(artifact.id); digest(artifact.sha256); validateFilename(artifact.filename); validateContentType(artifact.contentType);
                if (!ids.add(artifact.id) || artifact.order != ++order || artifact.byteSize < 0 || artifact.byteSize > MAX_ARTIFACT_BYTES
                    || artifact.byteSize > MAX_TOTAL_BYTES - total) throw new ServiceException("产物清单条目无效");
                total += artifact.byteSize;
            }
            directory(run.resolve("content"));
            return result;
        }
        catch (ServiceException e) { throw e; }
        catch (Exception e) { throw new ServiceException("产物清单尚未发布或读取失败"); }
    }

    /** Verify the same opened descriptor before exposing bytes; also verify the bytes consumed to EOF. */
    public InputStream read(String runId, String artifactId, long submitterId)
    {
        uuid(artifactId);
        Artifact artifact = manifest(runId, submitterId).artifacts.stream().filter(item -> item.id.equals(artifactId)).findFirst()
            .orElseThrow(() -> new ServiceException("产物不存在或无权访问"));
        try { return new VerifiedInput(Channels.newInputStream(verifiedFile(runDirectory(runId).resolve("content"), artifact)), artifact); }
        catch (ServiceException e) { throw e; }
        catch (IOException e) { throw new ServiceException("完整产物读取或校验失败"); }
    }

    private FileChannel verifiedFile(Path directory, Artifact artifact) throws IOException
    {
        directory(directory); Path file = directory.resolve(uuid(artifact.id) + ".bin"); regularFile(file);
        FileChannel channel = FileChannel.open(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
        try
        {
            if (channel.size() != artifact.byteSize) throw new ServiceException("完整产物长度校验失败");
            MessageDigest digest = sha256(); ByteBuffer buffer = ByteBuffer.allocate(8192); long size = 0;
            while (channel.read(buffer) != -1)
            {
                buffer.flip();
                if (buffer.remaining() > artifact.byteSize - size) throw new ServiceException("完整产物长度校验失败");
                size += buffer.remaining(); digest.update(buffer); buffer.clear();
            }
            if (size != artifact.byteSize || !HexFormat.of().formatHex(digest.digest()).equals(artifact.sha256))
                throw new ServiceException("完整产物摘要校验失败");
            channel.position(0);
            return channel;
        }
        catch (Exception e) { channel.close(); throw e; }
    }

    private static final class VerifiedInput extends InputStream
    {
        private final InputStream input; private final Artifact artifact; private final MessageDigest hash = sha256(); private long size;
        private boolean ended; private boolean closed;
        private VerifiedInput(InputStream input, Artifact artifact) { this.input = input; this.artifact = artifact; }
        @Override public int read() throws IOException { byte[] bytes = new byte[1]; int count = read(bytes, 0, 1); return count == -1 ? -1 : bytes[0] & 0xff; }
        @Override public int read(byte[] bytes, int offset, int length) throws IOException
        {
            java.util.Objects.checkFromIndexSize(offset, length, bytes.length);
            if (closed) throw new IOException("产物流已关闭");
            if (length == 0) return 0;
            if (ended) return -1;
            int count = input.read(bytes, offset, length);
            if (count > 0)
            {
                if (count > artifact.byteSize - size) throw new IOException("完整产物在读取期间发生变化");
                hash.update(bytes, offset, count); size += count;
            }
            else if (count == -1) verifyEnd();
            return count;
        }
        private void verifyEnd() throws IOException
        {
            if (ended) return;
            if (size != artifact.byteSize || !HexFormat.of().formatHex(hash.digest()).equals(artifact.sha256))
                throw new IOException("完整产物读取未完成或在读取期间发生变化");
            ended = true;
        }
        @Override public void close() throws IOException
        {
            if (closed) return;
            try { verifyEnd(); } finally { closed = true; input.close(); }
        }
    }

    /** Remove only unpublished bytes created by this Stage; existing published artifacts are never removed. */
    public void abandon(Stage stage)
    {
        if (stage == null) return;
        synchronized (stage)
        {
            if (stage.store != this || stage.published) return;
            try
            {
                directory(stage.directory);
                if (!stage.owner.equals(readOwner(stage.directory)) || Files.exists(stage.directory.resolve("manifest.json"), LinkOption.NOFOLLOW_LINKS)) return;
                for (String folder : List.of("staging", "content"))
                {
                    Path path = stage.directory.resolve(folder);
                    if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) continue;
                    directory(path);
                    try (var files = Files.newDirectoryStream(path))
                    {
                        for (Path file : files)
                        {
                            String name = file.getFileName().toString();
                            if (name.matches("[0-9a-f-]{36}\\.(bin|part)")) { regularFile(file); Files.delete(file); }
                        }
                    }
                    Files.delete(path);
                }
            }
            catch (IOException | ServiceException ignored) { /* A failed cleanup remains unpublished and cannot be read by delivery APIs. */ }
            finally { stage.failed = true; }
        }
    }

    private void checkStage(Stage stage)
    {
        if (stage.store != this || stage.failed || stage.published) throw new ServiceException("产物暂存状态不可写或发布");
        try
        {
            directory(stage.directory);
            if (!stage.owner.equals(readOwner(stage.directory)) || Files.exists(stage.directory.resolve("manifest.json"), LinkOption.NOFOLLOW_LINKS))
                throw new ServiceException("产物暂存归属或发布状态无效");
        }
        catch (IOException e) { throw new ServiceException("产物暂存目录校验失败"); }
    }
    private Owner readOwner(Path run) throws IOException
    {
        Path file = run.resolve("owner.json"); regularFile(file);
        if (Files.size(file) > 4096) throw new ServiceException("产物归属记录无效");
        try (InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) { return mapper.readValue(input, Owner.class); }
    }
    private Path runDirectory(String id) throws IOException { Path run = root().resolve(uuid(id)); directory(run); return run; }
    private Path root() throws IOException
    {
        Path configured = Path.of(properties.getStorageDir()).toAbsolutePath().normalize();
        if (Files.isSymbolicLink(configured)) throw new ServiceException("产物存储不允许符号链接");
        Files.createDirectories(configured, permissions(configured, true)); directory(configured);
        // Administrator path may traverse OS aliases such as macOS /var; the owned tree is anchored to its real path.
        Path root = configured.toRealPath().resolve("artifacts");
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS))
            try { createDirectory(root); } catch (java.nio.file.FileAlreadyExistsException ignored) { }
        directory(root); return root;
    }
    private static void directory(Path path) throws IOException
    { if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) throw new ServiceException("产物目录无效或包含符号链接"); }
    private static void regularFile(Path path) throws IOException
    { if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new ServiceException("产物文件未发布或包含符号链接"); }
    private static FileAttribute<?>[] permissions(Path path, boolean directory)
    { return path.getFileSystem().supportedFileAttributeViews().contains("posix") ? new FileAttribute<?>[]{PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------"))} : new FileAttribute<?>[0]; }
    private static void createDirectory(Path path) throws IOException { Files.createDirectory(path, permissions(path, true)); }
    private static FileChannel newFile(Path path) throws IOException
    { return FileChannel.open(path, java.util.Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS), permissions(path, false)); }
    private void writeAtomic(Path file, Object value) throws IOException
    {
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) throw new ServiceException("产物元数据已存在，禁止覆盖");
        Path temporary = file.resolveSibling(UUID.randomUUID() + ".tmp"); boolean moved = false;
        try
        {
            byte[] bytes = mapper.writeValueAsBytes(value);
            if (bytes.length > MAX_MANIFEST_BYTES) throw new ServiceException("产物清单超过大小限制");
            try (FileChannel channel = newFile(temporary))
            { ByteBuffer buffer = ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) channel.write(buffer); channel.force(true); }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE); moved = true;
            try { forceDirectory(file.getParent()); }
            catch (IOException e) { Files.deleteIfExists(file); throw e; }
        }
        finally { if (!moved) Files.deleteIfExists(temporary); }
    }
    private static void forceDirectory(Path directory) throws IOException
    { try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) { channel.force(true); } }
    private static String uuid(String value)
    { try { if (!UUID.fromString(value).toString().equals(value)) throw new IllegalArgumentException(); return value; } catch (Exception e) { throw new ServiceException("产物标识无效"); } }
    private static void digest(String value) { if (value == null || !value.matches("[0-9a-f]{64}")) throw new ServiceException("产物摘要格式无效"); }
    private static MessageDigest sha256() { try { return MessageDigest.getInstance("SHA-256"); } catch (Exception e) { throw new IllegalStateException(e); } }
    private static void validateFilename(String value)
    { if (value == null || value.isBlank() || value.length() > 255 || value.equals(".") || value.equals("..") || value.matches("(?s).*[\\\\/\\p{Cntrl}].*")) throw new ServiceException("产物文件名必须是有效的独立名称"); }
    private static void validateContentType(String value)
    { if (value == null || value.isBlank() || value.length() > 255 || value.matches("(?s).*\\p{Cntrl}.*")) throw new ServiceException("产物内容类型无效"); }
}
