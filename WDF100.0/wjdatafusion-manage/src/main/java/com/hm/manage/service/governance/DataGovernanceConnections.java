package com.hm.manage.service.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hm.common.exception.ServiceException;
import com.hm.manage.service.support.CredentialCryptoService;
import jakarta.annotation.PreDestroy;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Semaphore;
import org.springframework.stereotype.Service;

/** Explicit connection profiles; snapshot reads never accept SQL, URLs or driver properties from the browser. */
@Service
public class DataGovernanceConnections
{
    public record ProfileInput(Long revision, String name, String host, int port, String database,
                               String username, String password, String sslMode) { }
    public record Profile(String id, long revision, String type, String name, String host, int port,
                          String database, String username, boolean passwordConfigured, String sslMode, String updatedAt) { }
    public record SnapshotRequest(String schema, String table, List<String> columns, List<String> orderBy) { }
    public record Snapshot(String connectionId, long connectionRevision, String capturedAt, String sha256,
                           int rowCount, List<String> columns, String rowsJson, String mode) { }
    public static class StoredProfile
    {
        public long owner;
        public Profile profile;
        public String encryptedPassword;
    }
    private final DataGovernanceProperties properties;
    private final CredentialCryptoService crypto;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Semaphore permits = new Semaphore(4);
    private Path root;
    private FileChannel channel;
    private FileLock lock;
    private boolean closed;
    public DataGovernanceConnections(DataGovernanceProperties properties, CredentialCryptoService crypto)
    { this.properties = properties; this.crypto = crypto; }

    public synchronized List<Profile> list(long owner)
    {
        initialize(); List<Profile> result = new ArrayList<>();
        try (var files = Files.newDirectoryStream(root, "*.json"))
        {
            for (Path file : files)
            {
                StoredProfile value = read(file);
                if (value.owner == owner) result.add(value.profile);
            }
        }
        catch (ServiceException e) { throw e; }
        catch (Exception e) { throw new ServiceException("连接配置读取失败"); }
        result.sort(Comparator.comparing(Profile::updatedAt).reversed());
        return result;
    }
    public synchronized Profile save(String id, ProfileInput input, long owner)
    {
        initialize();
        if (input == null) reject("请填写连接配置");
        StoredProfile old = id == null ? null : owned(id, owner);
        if (old != null && (input.revision() == null || input.revision() != old.profile.revision())) reject("连接配置已变化，请刷新后重新编辑");
        if (old == null && list(owner).size() >= 100) reject("当前最多保存 100 个连接配置");
        String host = validateEndpoint(input.host(), input.port());
        String database = identifier(input.database());
        String username = text(input.username(), 128, "数据库账号");
        String ssl = input.sslMode() == null ? "verify-full" : input.sslMode();
        if (!ssl.equals("verify-full") && !(ssl.equals("disable") && isLoopback(host))) reject("远程数据库必须校验证书；仅独立本机连接允许关闭SSL");
        String password = input.password();
        if (password != null && password.length() > 4096) reject("密码长度超出限制");
        if ((password == null || password.isEmpty()) && old == null) reject("首次保存需要提供数据库密码");
        StoredProfile stored = new StoredProfile(); stored.owner = owner;
        stored.encryptedPassword = password == null || password.isEmpty() ? old.encryptedPassword : crypto.encrypt(password);
        stored.profile = new Profile(id == null ? UUID.randomUUID().toString() : id, old == null ? 1 : old.profile.revision() + 1,
            "POSTGRESQL", text(input.name(), 80, "连接名称"), host, input.port(), database, username, true, ssl, Instant.now().toString());
        write(stored);
        return stored.profile;
    }
    public Map<String, Object> test(String id, long owner)
    {
        StoredProfile stored = profile(id, owner);
        acquire();
        long start = System.nanoTime();
        try (Connection connection = open(stored); PreparedStatement query = connection.prepareStatement("SELECT 1, current_setting('transaction_read_only')"))
        {
            query.setQueryTimeout(5);
            try (ResultSet rows = query.executeQuery()) { if (!rows.next() || rows.getInt(1) != 1 || !"on".equals(rows.getString(2))) reject("数据库未确认只读事务"); }
            connection.rollback();
            return Map.of("success", true, "readOnly", true, "elapsedMillis", (System.nanoTime() - start) / 1_000_000);
        }
        catch (ServiceException e) { throw e; }
        catch (Exception e) { throw new ServiceException("数据库连接测试失败，请检查地址、证书、账号及权限"); }
        finally { permits.release(); }
    }
    public Snapshot snapshot(String id, SnapshotRequest request, long owner)
    {
        StoredProfile stored = profile(id, owner);
        String sql = snapshotSql(request);
        acquire();
        try (Connection connection = open(stored); PreparedStatement query = connection.prepareStatement(sql))
        {
            query.setQueryTimeout(5); query.setFetchSize(16); query.setMaxRows(1001);
            List<Map<String, Object>> result = new ArrayList<>();
            int bytes = 2;
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
            try (ResultSet rows = query.executeQuery())
            {
                while (rows.next())
                {
                    if (System.nanoTime() > deadline) reject("字典读取超时，未保存部分快照");
                    if (rows.getBoolean(request.columns().size() + 1)) reject("字典包含超大行，未读取或截断该行内容");
                    if (result.size() >= 1000) reject("字典超过 1000 行，请使用更小的字典表或业务视图；未截断保存");
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 0; i < request.columns().size(); i++) row.put(request.columns().get(i), scalar(rows.getObject(i + 1)));
                    bytes += mapper.writeValueAsBytes(row).length + 1;
                    if (bytes > 65536) reject("字典快照超过 64 KiB，请减少字段或使用业务视图；未截断保存");
                    result.add(row);
                }
            }
            connection.rollback();
            String json = mapper.writeValueAsString(result);
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8)));
            return new Snapshot(id, stored.profile.revision(), Instant.now().toString(), hash, result.size(), List.copyOf(request.columns()), json, "BATCH_SNAPSHOT");
        }
        catch (ServiceException e) { throw e; }
        catch (Exception e) { throw new ServiceException("字典读取失败，请核对表、字段、证书和只读权限"); }
        finally { permits.release(); }
    }
    static Object scalar(Object value)
    {
        if (value instanceof Double d && !Double.isFinite(d) || value instanceof Float f && !Float.isFinite(f)) reject("字典包含非有限数值");
        if (value == null || value instanceof Boolean || value instanceof Number || value instanceof String) return value;
        if (value instanceof byte[] || value instanceof Blob || value instanceof Clob || value instanceof java.sql.Array) reject("快照仅支持标量字典列，不支持二进制或数组列");
        return value.toString();
    }
    static String snapshotSql(SnapshotRequest request)
    {
        if (request == null || request.columns() == null || request.columns().isEmpty() || request.columns().size() > 30) reject("请选择 1 至 30 个字典字段");
        String schema = identifier(request.schema() == null || request.schema().isBlank() ? "public" : request.schema());
        if (schema.toLowerCase(Locale.ROOT).startsWith("pg_") || schema.equalsIgnoreCase("information_schema")) reject("不允许读取系统目录");
        if (new HashSet<>(request.columns()).size() != request.columns().size()) reject("字典字段不能重复");
        List<String> columns = request.columns().stream().map(column -> "snapshot_source." + quoted(column)).toList();
        List<String> order = request.orderBy() == null ? List.of() : request.orderBy();
        if (order.size() > 8 || !request.columns().containsAll(order)) reject("排序字段必须来自所选字典字段，最多 8 个");
        String rowBytes = columns.stream().map(column -> "COALESCE(octet_length(" + column + "::text)::bigint,0)").collect(java.util.stream.Collectors.joining("+"));
        String tooLarge = "(" + rowBytes + ")>65536";
        // Oversized values are never sent to the JDBC client. The flag makes this an explicit rejection, never a truncated snapshot.
        String projection = columns.stream().map(column -> "CASE WHEN " + tooLarge + " THEN NULL ELSE " + column + " END").collect(java.util.stream.Collectors.joining(","));
        return "SELECT " + projection + ",(" + tooLarge + ") AS __governance_oversize FROM " + quoted(schema) + "." + quoted(request.table()) + " AS snapshot_source"
            + (order.isEmpty() ? "" : " ORDER BY " + String.join(",", order.stream().map(column -> "snapshot_source." + quoted(column)).toList())) + " LIMIT 1001";
    }
    private Connection open(StoredProfile stored) throws Exception
    {
        Profile p = stored.profile;
        String host = validateEndpoint(p.host(), p.port()); // Revalidate after policy changes; no DNS or arbitrary JDBC options.
        Properties config = new Properties();
        config.setProperty("user", p.username()); config.setProperty("password", crypto.decrypt(stored.encryptedPassword));
        config.setProperty("sslmode", p.sslMode()); config.setProperty("readOnly", "true"); config.setProperty("readOnlyMode", "always");
        config.setProperty("connectTimeout", "3"); config.setProperty("socketTimeout", "8"); config.setProperty("cancelSignalTimeout", "3");
        config.setProperty("ApplicationName", "RYNEW governance snapshot");
        Connection connection = DriverManager.getConnection("jdbc:postgresql://" + (host.contains(":") ? "[" + host + "]" : host) + ":" + p.port() + "/" + p.database(), config);
        try { connection.setReadOnly(true); connection.setAutoCommit(false); return connection; }
        catch (Exception e) { connection.close(); throw e; }
    }
    String validateEndpoint(String supplied, int port)
    {
        String host = supplied == null ? "" : supplied.trim();
        if (host.equals("localhost")) host = "127.0.0.1";
        if (!host.equals("::1"))
        {
            String[] parts = host.split("\\.", -1);
            if (parts.length != 4) reject("连接地址只接受明确的IP地址或localhost");
            for (String part : parts)
                if (!part.matches("0|[1-9][0-9]{0,2}") || Integer.parseInt(part) > 255) reject("IP地址格式无效");
        }
        if (port < 1 || port > 65535 || !properties.getConnectionAllowedEndpoints().contains(host + ":" + port)) reject("此地址和端口未列入服务端允许的治理连接范围");
        return host;
    }
    private static boolean isLoopback(String host) { return host.equals("127.0.0.1") || host.equals("::1"); }
    static String identifier(String value)
    {
        if (value == null || !value.matches("[\\p{L}_][\\p{L}\\p{N}_$]{0,62}") || value.getBytes(StandardCharsets.UTF_8).length > 63) reject("库、表或字段名称格式无效，UTF-8长度不能超过63字节");
        return value;
    }
    private static String quoted(String value) { return "\"" + identifier(value) + "\""; }
    private static String text(String value, int max, String label)
    { if (value == null || value.isBlank() || value.length() > max || value.chars().anyMatch(c -> c < 32)) reject(label + "格式无效"); return value.trim(); }
    private synchronized StoredProfile profile(String id, long owner) { initialize(); return owned(id, owner); }
    private StoredProfile owned(String id, long owner)
    {
        Path path = file(id);
        if (!Files.exists(path)) reject("连接不存在或无权访问");
        StoredProfile stored = read(path);
        if (stored.owner != owner) reject("连接不存在或无权访问");
        return stored;
    }
    private StoredProfile read(Path path)
    {
        try
        {
            if (Files.isSymbolicLink(path) || !Files.isRegularFile(path) || Files.size(path) > 65536) reject("连接存储文件无效");
            StoredProfile stored = mapper.readValue(Files.readAllBytes(path), StoredProfile.class);
            if (stored.profile == null || stored.encryptedPassword == null || !file(stored.profile.id()).equals(path)) reject("连接存储记录无效");
            return stored;
        }
        catch (ServiceException e) { throw e; }
        catch (Exception e) { throw new ServiceException("连接配置读取失败"); }
    }
    private void write(StoredProfile profile)
    {
        Path temporary = null;
        try
        {
            temporary = Files.createTempFile(root, ".connection-", ".tmp"); permissions(temporary, "rw-------");
            try (var output = FileChannel.open(temporary, StandardOpenOption.WRITE))
            {
                var buffer = java.nio.ByteBuffer.wrap(mapper.writeValueAsBytes(profile));
                while (buffer.hasRemaining()) output.write(buffer);
                output.force(true);
            }
            Files.move(temporary, file(profile.profile.id()), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (Exception e) { throw new ServiceException("连接配置原子保存失败"); }
        finally { if (temporary != null) try { Files.deleteIfExists(temporary); } catch (Exception ignored) { } }
    }
    private Path file(String id)
    {
        try { if (!UUID.fromString(id).toString().equals(id)) throw new IllegalArgumentException(); }
        catch (Exception e) { throw new ServiceException("连接标识无效"); }
        return root.resolve(id + ".json");
    }
    private void initialize()
    {
        if (closed) reject("连接存储已关闭");
        if (properties.getNifi().getBaseUrl().isBlank()) reject("请先配置独立数据治理环境");
        if (root != null) return;
        FileChannel candidate = null;
        try
        {
            Path directory = Path.of(properties.getStorageDir()).toAbsolutePath().normalize().resolve("connections");
            if (Files.isSymbolicLink(directory)) reject("连接存储目录不能是符号链接");
            Files.createDirectories(directory); permissions(directory, "rwx------");
            candidate = FileChannel.open(directory.resolve(".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock acquired = candidate.tryLock();
            if (acquired == null) reject("连接目录已被其他实例占用");
            channel = candidate; lock = acquired; root = directory;
        }
        catch (Exception e)
        {
            if (candidate != null) try { candidate.close(); } catch (Exception ignored) { }
            if (e instanceof ServiceException service) throw service;
            throw new ServiceException("连接存储初始化失败");
        }
    }
    private void acquire() { if (!permits.tryAcquire()) reject("连接检查繁忙，请稍后再试"); }
    private static void permissions(Path path, String value) throws Exception
    { if (path.getFileSystem().supportedFileAttributeViews().contains("posix")) Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(value)); }
    static void reject(String message) { throw new ServiceException(message); }
    @PreDestroy public synchronized void close()
    {
        closed = true;
        try { if (lock != null) lock.release(); } catch (Exception ignored) { }
        try { if (channel != null) channel.close(); } catch (Exception ignored) { }
    }
}
