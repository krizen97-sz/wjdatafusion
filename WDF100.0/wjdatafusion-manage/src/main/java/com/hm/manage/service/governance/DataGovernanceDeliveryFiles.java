package com.hm.manage.service.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hm.common.exception.ServiceException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;

/** Private, process-locked delivery metadata. Payloads live in the separate artifact store. */
final class DataGovernanceDeliveryFiles implements AutoCloseable
{
    private final ObjectMapper mapper = new ObjectMapper();
    private final Path root;
    private final FileChannel channel;
    private final FileLock lock;
    private boolean closed;
    DataGovernanceDeliveryFiles(DataGovernanceProperties properties)
    { this(properties, "deliveries"); }
    DataGovernanceDeliveryFiles(DataGovernanceProperties properties, String namespace)
    {
        FileChannel candidate = null;
        try
        {
            if (properties.getNifi().getBaseUrl().isBlank()) fail("请先配置独立治理环境");
            Path configured = Path.of(properties.getStorageDir()).toAbsolutePath().normalize();
            if (Files.isSymbolicLink(configured)) fail("交付存储不能使用符号链接");
            Files.createDirectories(configured); permissions(configured, "rwx------");
            // Resolve administrator-selected OS aliases, then prohibit links inside the owned tree.
            if (!Set.of("deliveries", "kafka-executions").contains(namespace)) fail("执行存储类型无效");
            root = configured.toRealPath().resolve(namespace);
            if (Files.isSymbolicLink(root)) fail("交付存储不能使用符号链接");
            Files.createDirectories(root); permissions(root, "rwx------");
            Path lockPath = root.resolve(".lock");
            if (Files.isSymbolicLink(lockPath)) fail("交付锁文件无效");
            candidate = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
            permissions(lockPath, "rw-------"); lock = candidate.tryLock();
            if (lock == null) fail("交付存储已被其他实例占用");
            channel = candidate;
        }
        catch (Exception e)
        {
            if (candidate != null) try { candidate.close(); } catch (Exception ignored) { }
            if (e instanceof ServiceException service) throw service;
            throw new ServiceException("交付存储初始化失败");
        }
    }
    synchronized <T> T read(String kind, String id, Class<T> type)
    {
        Path p = path(kind, id);
        try
        {
            if (!Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS) || Files.size(p) > 512 * 1024) fail("交付记录不存在或无效");
            return mapper.readValue(Files.readAllBytes(p), type);
        }
        catch (ServiceException e) { throw e; }
        catch (Exception e) { throw new ServiceException("交付记录读取失败"); }
    }
    synchronized <T> List<T> list(String kind, Class<T> type)
    {
        check(); List<T> result = new ArrayList<>();
        try (var files = Files.newDirectoryStream(root, kind + "-*.json"))
        {
            for (Path p : files)
            {
                if (result.size() >= 2000) fail("交付存储记录达到读取上限，请归档后继续");
                String name = p.getFileName().toString();
                result.add(read(kind, name.substring(kind.length() + 1, name.length() - 5), type));
            }
        }
        catch (ServiceException e) { throw e; }
        catch (Exception e) { throw new ServiceException("交付记录列表读取失败"); }
        return result;
    }
    synchronized void write(String kind, String id, Object value)
    {
        Path target = path(kind, id), temporary = null;
        try
        {
            if (Files.isSymbolicLink(target)) fail("交付存储文件无效");
            byte[] bytes = mapper.writeValueAsBytes(value);
            if (bytes.length > 512 * 1024) fail("交付记录超出限制");
            temporary = Files.createTempFile(root, ".delivery-", ".tmp"); permissions(temporary, "rw-------");
            try (var output = FileChannel.open(temporary, StandardOpenOption.WRITE))
            { ByteBuffer buffer = ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) output.write(buffer); output.force(true); }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            try (var directory = FileChannel.open(root, StandardOpenOption.READ)) { directory.force(true); }
        }
        catch (ServiceException e) { throw e; }
        catch (Exception e) { throw new ServiceException("交付记录原子保存失败"); }
        finally { if (temporary != null) try { Files.deleteIfExists(temporary); } catch (Exception ignored) { } }
    }
    private Path path(String kind, String id)
    {
        check(); if (!Set.of("profile", "job").contains(kind)) fail("交付记录类型无效");
        try { if (!UUID.fromString(id).toString().equals(id)) fail("交付标识无效"); }
        catch (Exception e) { throw new ServiceException("交付标识无效"); }
        return root.resolve(kind + "-" + id + ".json");
    }
    private void check() { if (closed) fail("交付存储已关闭"); }
    private static void permissions(Path path, String mode) throws Exception
    { if (path.getFileSystem().supportedFileAttributeViews().contains("posix")) Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(mode)); }
    private static void fail(String message) { throw new ServiceException(message); }
    @Override public synchronized void close()
    { closed = true; try { lock.release(); } catch (Exception ignored) { } try { channel.close(); } catch (Exception ignored) { } }
}
