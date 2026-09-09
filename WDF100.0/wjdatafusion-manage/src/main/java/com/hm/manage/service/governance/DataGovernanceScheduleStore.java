package com.hm.manage.service.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hm.common.exception.ServiceException;
import com.hm.manage.service.governance.DataGovernanceScheduleModels.*;
import jakarta.annotation.PreDestroy;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Private, atomic, single-writer files. Releases are create-only; schedule updates replace atomically. */
@Repository
public class DataGovernanceScheduleStore
{
    private final DataGovernanceProperties properties;
    private final ObjectMapper mapper = new ObjectMapper();
    private Path root;
    private FileChannel channel;
    private FileLock lock;
    private boolean closed;
    public DataGovernanceScheduleStore(DataGovernanceProperties properties) { this.properties = properties; }

    private void initialize() throws Exception
    {
        if (closed) throw new ServiceException("任务存储已关闭");
        if (root != null) return;
        Path base = Path.of(properties.getStorageDir()).toAbsolutePath().normalize();
        Path directory = base.resolve("schedules");
        // Refuse symlink components rather than tightening permissions on an unrelated directory.
        if (Files.isSymbolicLink(base) || Files.isSymbolicLink(directory))
            throw new ServiceException("任务存储目录不能是符号链接");
        Files.createDirectories(directory); permissions(base, "rwx------"); permissions(directory, "rwx------");
        Path lockFile = directory.resolve(".single-instance.lock");
        if (Files.isSymbolicLink(lockFile)) throw new ServiceException("任务锁文件无效");
        FileChannel candidate = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
        boolean acquired = false;
        try
        {
            permissions(lockFile, "rw-------");
            FileLock candidateLock = candidate.tryLock();
            if (candidateLock == null) throw new ServiceException("任务目录已被其他实例占用");
            channel = candidate; lock = candidateLock; root = directory; acquired = true;
        }
        finally { if (!acquired) candidate.close(); }
    }
    private Path path(String kind, String id)
    {
        try { if (!UUID.fromString(id).toString().equals(id)) throw new IllegalArgumentException(); }
        catch (Exception e) { throw new ServiceException("任务或版本标识无效"); }
        return root.resolve(kind + "-" + id + ".json");
    }
    public synchronized void createRelease(Release release) { write("release", release.id, release, true); }
    public synchronized void saveSchedule(Schedule schedule) { write("schedule", schedule.id, schedule, false); }
    public synchronized Release release(String id) { return readOne("release", id, Release.class); }
    public synchronized Schedule schedule(String id) { return readOne("schedule", id, Schedule.class); }
    public synchronized List<Release> releases() { return list("release", Release.class); }
    public synchronized List<Schedule> schedules() { return list("schedule", Schedule.class); }
    private void write(String kind, String id, Object object, boolean createOnly)
    {
        Path temporary = null;
        try
        {
            initialize(); Path destination = path(kind, id);
            if (Files.isSymbolicLink(destination)) throw new ServiceException("任务文件无效");
            if (createOnly && Files.exists(destination)) throw new ServiceException("已发布版本不可覆盖");
            byte[] bytes = mapper.writeValueAsBytes(object);
            if (bytes.length > 4 * 1024 * 1024) throw new ServiceException("任务文件超过 4 MiB");
            temporary = Files.createTempFile(root, ".pending-", ".tmp"); permissions(temporary, "rw-------");
            try (FileChannel out = FileChannel.open(temporary, StandardOpenOption.WRITE))
            {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) out.write(buffer);
                out.force(true);
            }
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (ServiceException e) { throw e; }
        catch (Exception e) { throw new ServiceException("任务文件原子保存失败"); }
        finally { if (temporary != null) try { Files.deleteIfExists(temporary); } catch (Exception ignored) { } }
    }
    private <T> T readOne(String kind, String id, Class<T> type)
    {
        try { initialize(); Path file = path(kind, id); return Files.exists(file, LinkOption.NOFOLLOW_LINKS) ? read(file, type) : null; }
        catch (ServiceException e) { throw e; }
        catch (Exception e) { throw new ServiceException("任务文件读取失败"); }
    }
    private <T> List<T> list(String kind, Class<T> type)
    {
        try
        {
            initialize(); List<T> result = new ArrayList<>();
            try (DirectoryStream<Path> files = Files.newDirectoryStream(root, kind + "-*.json"))
            {
                for (Path file : files)
                {
                    if (result.size() >= 10000) throw new ServiceException("任务存储记录过多，请管理员归档");
                    result.add(read(file, type));
                }
            }
            return result;
        }
        catch (ServiceException e) { throw e; }
        catch (Exception e) { throw new ServiceException("任务文件读取失败"); }
    }
    private <T> T read(Path file, Class<T> type) throws Exception
    {
        if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > 4 * 1024 * 1024)
            throw new ServiceException("任务文件无效");
        return mapper.readValue(Files.readAllBytes(file), type);
    }
    private static void permissions(Path path, String mode) throws Exception
    {
        if (path.getFileSystem().supportedFileAttributeViews().contains("posix"))
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(mode));
    }
    @PreDestroy public synchronized void close()
    {
        closed = true;
        try { if (lock != null) lock.release(); } catch (Exception ignored) { }
        try { if (channel != null) channel.close(); } catch (Exception ignored) { }
        root = null;
    }
}
