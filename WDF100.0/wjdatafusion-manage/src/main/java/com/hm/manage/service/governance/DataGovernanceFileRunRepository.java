package com.hm.manage.service.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hm.common.exception.ServiceException;
import com.hm.manage.service.governance.DataGovernanceModels.StoredRun;
import jakarta.annotation.PreDestroy;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class DataGovernanceFileRunRepository implements DataGovernanceRunRepository
{
    private final DataGovernanceProperties properties;
    private final ObjectMapper mapper = new ObjectMapper();
    private Path root;
    private FileChannel channel;
    private FileLock lock;
    private boolean closed;
    public DataGovernanceFileRunRepository(DataGovernanceProperties properties) { this.properties = properties; }

    private void initialize() throws Exception
    {
        if (closed) throw new ServiceException("测试记录存储已关闭");
        if (root != null) return;
        Path directory = Path.of(properties.getStorageDir()).toAbsolutePath().normalize();
        Files.createDirectories(directory);
        permissions(directory, "rwx------");
        channel = FileChannel.open(directory.resolve(".single-instance.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        lock = channel.tryLock();
        if (lock == null) throw new ServiceException("测试记录目录已被其他实例占用，文件存储仅支持单实例");
        root = directory;
    }

    private Path path(String id)
    {
        try { if (!UUID.fromString(id).toString().equals(id)) throw new IllegalArgumentException(); }
        catch (Exception e) { throw new ServiceException("测试记录标识无效"); }
        return root.resolve(id + ".json");
    }

    @Override public synchronized void save(StoredRun run)
    {
        Path temporary = null;
        try
        {
            initialize();
            Path destination = path(run.run.id);
            temporary = Files.createTempFile(root, ".run-", ".tmp");
            permissions(temporary, "rw-------");
            byte[] bytes = mapper.writeValueAsBytes(run);
            try (FileChannel out = FileChannel.open(temporary, StandardOpenOption.WRITE))
            {
                var buffer = java.nio.ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) out.write(buffer);
                out.force(true);
            }
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (ServiceException e) { throw e; }
        catch (Exception e) { throw new ServiceException("测试记录原子保存失败"); }
        finally { if (temporary != null) try { Files.deleteIfExists(temporary); } catch (Exception ignored) { } }
    }

    @Override public synchronized StoredRun find(String id)
    {
        try
        {
            initialize(); Path file = path(id);
            return Files.exists(file) ? read(file) : null;
        }
        catch (ServiceException e) { throw e; }
        catch (Exception e) { throw new ServiceException("测试记录读取失败"); }
    }

    @Override public synchronized List<StoredRun> list()
    {
        try
        {
            initialize(); List<StoredRun> runs = new ArrayList<>();
            try (var files = Files.newDirectoryStream(root, "*.json"))
            {
                for (Path file : files) runs.add(read(file));
            }
            runs.sort((a, b) -> b.run.createdAt.compareTo(a.run.createdAt));
            return runs;
        }
        catch (ServiceException e) { throw e; }
        catch (Exception e) { throw new ServiceException("测试记录读取失败"); }
    }

    private StoredRun read(Path file) throws Exception
    {
        if (Files.isSymbolicLink(file) || Files.size(file) > 4 * 1024 * 1024) throw new ServiceException("测试记录文件无效");
        return mapper.readValue(Files.readAllBytes(file), StoredRun.class);
    }
    private static void permissions(Path file, String permissions) throws Exception
    {
        if (file.getFileSystem().supportedFileAttributeViews().contains("posix"))
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString(permissions));
    }
    @PreDestroy public synchronized void close()
    {
        closed = true;
        try { if (lock != null) lock.release(); } catch (Exception ignored) { }
        try { if (channel != null) channel.close(); } catch (Exception ignored) { }
        root = null;
    }
}
