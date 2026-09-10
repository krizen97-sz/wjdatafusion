package com.hm.manage.service.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hm.common.exception.ServiceException;
import com.hm.manage.service.governance.DataGovernanceKafkaModels.*;
import jakarta.annotation.PreDestroy;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.stereotype.Repository;

/** Receipt and its lease are one atomic file, so a crash cannot lose an independently maintained lease. */
@Repository
public class DataGovernanceKafkaStore
{
    private final DataGovernanceKafkaProperties properties;
    private final ObjectMapper mapper = new ObjectMapper();
    private Path root;
    private FileChannel channel;
    private FileLock lock;
    private boolean closed;
    public DataGovernanceKafkaStore(DataGovernanceKafkaProperties properties) { this.properties = properties; }
    private void initialize() throws Exception
    {
        if (closed) throw new ServiceException("Kafka 记录存储已关闭");
        if (!properties.isEnabled()) throw new ServiceException("Kafka 批次功能未启用");
        if (root != null) return;
        Path directory = Path.of(properties.getStorageDir()).toAbsolutePath().normalize();
        if (Files.isSymbolicLink(directory)) throw new ServiceException("Kafka 存储目录不能是符号链接");
        Files.createDirectories(directory); mode(directory, "rwx------");
        Path lockPath = directory.resolve(".single-instance.lock");
        if (Files.isSymbolicLink(lockPath)) throw new ServiceException("Kafka 存储锁无效");
        FileChannel candidate = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
        boolean acquired = false;
        try
        {
            mode(lockPath, "rw-------"); FileLock candidateLock = candidate.tryLock();
            if (candidateLock == null) throw new ServiceException("Kafka 存储仅允许单实例");
            channel = candidate; lock = candidateLock; root = directory; acquired = true;
        }
        finally { if (!acquired) candidate.close(); }
    }
    private Path path(String prefix, String id)
    {
        try { if (!UUID.fromString(id).toString().equals(id)) throw new IllegalArgumentException(); }
        catch (Exception e) { throw new ServiceException("Kafka 记录标识无效"); }
        return root.resolve(prefix + "-" + id + ".json");
    }
    public synchronized Profile profile(String id) { return readOne("p", id, Profile.class); }
    public synchronized Receipt receipt(String id) { return readOne("r", id, Receipt.class); }
    public synchronized List<Profile> profiles() { List<Profile> values = new ArrayList<>(); visit("p", Profile.class, values::add); return values; }
    public synchronized void receipts(Consumer<Receipt> visitor) { visit("r", Receipt.class, visitor); }
    public synchronized void saveProfile(Profile profile) { write("p", profile.id, profile); }
    public synchronized void saveReceipt(Receipt receipt) { write("r", receipt.id, receipt); }
    public synchronized boolean leased(String leaseKey)
    {
        boolean[] held = {false};
        receipts(receipt -> { if (receipt.leaseHeld && receipt.binding.leaseKey().equals(leaseKey)) held[0] = true; });
        return held[0];
    }
    public synchronized void claim(Receipt receipt)
    {
        if (leased(receipt.binding.leaseKey())) throw new ServiceException("同一 Kafka 源和消费组已有未完成批次，请先处理原批次");
        if (receipt(receipt.id) != null) throw new ServiceException("Kafka 批次标识已存在");
        write("r", receipt.id, receipt);
    }
    private void write(String prefix, String id, Object value)
    {
        Path temporary = null;
        try
        {
            initialize(); Path target = path(prefix, id);
            if (Files.isSymbolicLink(target)) throw new ServiceException("Kafka 记录文件无效");
            byte[] bytes = mapper.writeValueAsBytes(value);
            if (bytes.length > 2 * 1024 * 1024) throw new ServiceException("Kafka 记录文件超过限制");
            temporary = Files.createTempFile(root, ".pending-", ".tmp"); mode(temporary, "rw-------");
            try (FileChannel output = FileChannel.open(temporary, StandardOpenOption.WRITE))
            {
                ByteBuffer data = ByteBuffer.wrap(bytes); while (data.hasRemaining()) output.write(data); output.force(true);
            }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            try (FileChannel directory = FileChannel.open(root, StandardOpenOption.READ)) { directory.force(true); }
        }
        catch (ServiceException e) { throw e; }
        catch (Exception e) { throw new ServiceException("Kafka 记录原子保存失败"); }
        finally { if (temporary != null) try { Files.deleteIfExists(temporary); } catch (Exception ignored) { } }
    }
    private <T> T readOne(String prefix, String id, Class<T> type)
    {
        try { initialize(); Path target = path(prefix, id); return Files.exists(target, LinkOption.NOFOLLOW_LINKS) ? read(target, type) : null; }
        catch (ServiceException e) { throw e; }
        catch (Exception e) { throw new ServiceException("Kafka 记录读取失败"); }
    }
    private <T> void visit(String prefix, Class<T> type, Consumer<T> visitor)
    {
        try
        {
            initialize(); int count = 0;
            try (DirectoryStream<Path> files = Files.newDirectoryStream(root, prefix + "-*.json"))
            {
                for (Path file : files)
                { if (++count > 10000) throw new ServiceException("Kafka 记录过多，请管理员归档"); visitor.accept(read(file, type)); }
            }
        }
        catch (ServiceException e) { throw e; }
        catch (Exception e) { throw new ServiceException("Kafka 记录读取失败"); }
    }
    private <T> T read(Path path, Class<T> type) throws Exception
    {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > 2 * 1024 * 1024)
            throw new ServiceException("Kafka 记录文件无效");
        return mapper.readValue(Files.readAllBytes(path), type);
    }
    private static void mode(Path path, String value) throws Exception
    {
        if (path.getFileSystem().supportedFileAttributeViews().contains("posix"))
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(value));
    }
    @PreDestroy public synchronized void close()
    {
        closed = true;
        try { if (lock != null) lock.release(); } catch (Exception ignored) { }
        try { if (channel != null) channel.close(); } catch (Exception ignored) { }
        root = null;
    }
}
