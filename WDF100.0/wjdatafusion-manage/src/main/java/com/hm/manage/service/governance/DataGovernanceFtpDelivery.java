package com.hm.manage.service.governance;

import com.hm.common.exception.ServiceException;
import com.hm.manage.service.support.CredentialCryptoService;
import jakarta.annotation.PreDestroy;
import java.io.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.apache.commons.net.ftp.*;
import org.apache.commons.net.io.CopyStreamAdapter;
import org.springframework.stereotype.Service;

/** Explicit asynchronous delivery of a complete, successful run; previews can never enter this path. */
@Service
public class DataGovernanceFtpDelivery
{
    public record ProfileInput(Long revision, String name, String host, Integer port, String username, String password,
                               String directory, String transferMode, String controlEncoding) { }
    public record Profile(String id, long revision, String name, String host, int port, String username, String directory,
                          String transferMode, String controlEncoding, boolean passwordConfigured, String updatedAt) { }
    public record Submit(String runId, String connectionId) { }
    public record Retry(Long revision, Boolean refreshCredentials) { public Retry(Long revision) { this(revision, false); } }
    public record Entry(String artifactId, int order, String filename, long byteSize, String sha256, String status) { }
    public record Job(String id, long revision, String runId, String connectionId, String connectionName, long connectionRevision, long credentialRevision,
                      String status, String remoteDirectory, String transferMode, List<Entry> entries, String error,
                      int attempts, String createdAt, String updatedAt) { }
    public static class StoredProfile { public long owner; public Profile profile; public String encryptedPassword; }
    public static class StoredJob { public long owner; public Job job; public Profile target; public String encryptedPassword; }
    private final DataGovernanceProperties properties;
    private final CredentialCryptoService crypto;
    private final DataGovernanceArtifactStore artifacts;
    private final DataGovernanceRunRepository runs;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(4), r -> {
        Thread thread = new Thread(r, "governance-delivery"); thread.setDaemon(true); return thread;
    }, new ThreadPoolExecutor.AbortPolicy());
    private DataGovernanceDeliveryFiles files;
    private boolean closed;
    public DataGovernanceFtpDelivery(DataGovernanceProperties properties, CredentialCryptoService crypto, DataGovernanceArtifactStore artifacts, DataGovernanceRunRepository runs)
    { this.properties = properties; this.crypto = crypto; this.artifacts = artifacts; this.runs = runs; }

    public synchronized List<Profile> profiles(long owner)
    { initialize(); return files.list("profile", StoredProfile.class).stream().filter(p -> p.owner == owner).map(p -> p.profile).sorted(Comparator.comparing(Profile::updatedAt).reversed()).toList(); }
    public synchronized Profile saveProfile(String id, ProfileInput input, long owner)
    {
        initialize(); if (input == null) fail("请填写FTP连接");
        StoredProfile old = id == null ? null : profile(id, owner);
        if (old != null && (input.revision() == null || input.revision() != old.profile.revision())) fail("FTP连接已修改，请刷新");
        if (old == null && profiles(owner).size() >= 100) fail("最多保存100个FTP连接");
        String host = endpoint(input.host(), input.port());
        String mode = input.transferMode() == null ? "BINARY" : input.transferMode();
        if (!Set.of("BINARY", "ASCII").contains(mode)) fail("传输模式无效");
        String encoding = input.controlEncoding() == null ? "UTF-8" : input.controlEncoding();
        if (!Set.of("UTF-8", "ISO-8859-1").contains(encoding)) fail("控制编码无效");
        String password = input.password();
        if (password != null && (password.length() > 4096 || password.indexOf('\r') >= 0 || password.indexOf('\n') >= 0)) fail("FTP密码格式无效");
        if ((password == null || password.isEmpty()) && old == null) fail("首次保存需要FTP密码");
        StoredProfile saved = new StoredProfile(); saved.owner = owner;
        saved.encryptedPassword = password == null || password.isEmpty() ? old.encryptedPassword : crypto.encrypt(password);
        saved.profile = new Profile(old == null ? UUID.randomUUID().toString() : id, old == null ? 1 : old.profile.revision() + 1,
            text(input.name(), 80), host, input.port(), text(input.username(), 128), directory(input.directory()), mode, encoding, true, Instant.now().toString());
        files.write("profile", saved.profile.id(), saved); return saved.profile;
    }
    public Map<String, Object> test(String id, long owner)
    {
        StoredProfile stored; synchronized (this) { initialize(); stored = profile(id, owner); }
        long start = System.nanoTime();
        try (Remote remote = connect(stored.profile, crypto.decrypt(stored.encryptedPassword), System.nanoTime() + TimeUnit.SECONDS.toNanos(15)))
        { remote.directory(stored.profile.directory()); return Map.of("success", true, "elapsedMillis", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)); }
        catch (Exception e) { throw new ServiceException("FTP连接检查失败，请核对目标、账号及已有目录"); }
    }
    public synchronized List<Job> jobs(String runId, long owner)
    {
        initialize(); if (runId != null) uuid(runId);
        return files.list("job", StoredJob.class).stream().filter(j -> j.owner == owner && (runId == null || runId.equals(j.job.runId())))
            .map(j -> j.job).sorted(Comparator.comparing(Job::createdAt).reversed()).toList();
    }
    public synchronized Job job(String id, long owner) { initialize(); return owned(id, owner).job; }
    public synchronized String deliveryTargetFingerprint(String id, long owner) { initialize(); return targetFingerprint(owned(id, owner).target); }
    public synchronized Profile destination(String id, long owner)
    { initialize(); Profile target = profile(id, owner).profile; endpoint(target.host(), target.port()); return target; }
    public static String targetFingerprint(Profile profile)
    {
        try
        {
            var values = new TreeMap<String, Object>(); values.put("host", profile.host()); values.put("port", profile.port());
            values.put("username", profile.username()); values.put("directory", profile.directory()); values.put("transferMode", profile.transferMode()); values.put("controlEncoding", profile.controlEncoding());
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(values)));
        }
        catch (Exception e) { throw new ServiceException("交付目标摘要计算失败"); }
    }
    public synchronized Job submit(Submit input, long owner)
    { return submit(input, owner, null); }
    public synchronized Job submit(Submit input, long owner, String expectedFingerprint)
    {
        initialize(); if (input == null) fail("请选择运行和FTP连接");
        StoredProfile connection = profile(input.connectionId(), owner);
        endpoint(connection.profile.host(), connection.profile.port());
        if (expectedFingerprint != null && !expectedFingerprint.equals(targetFingerprint(connection.profile))) fail("定时任务绑定的FTP目标已改变，未发起交付");
        var manifest = verifiedManifest(input.runId(), owner);
        if (manifest.artifacts().isEmpty()) fail("本次运行没有可交付的完整产物");
        List<Job> existing = jobs(null, owner);
        for (Job job : existing)
            if (job.runId().equals(input.runId()) && job.connectionId().equals(input.connectionId()))
            {
                if (expectedFingerprint != null && !expectedFingerprint.equals(targetFingerprint(owned(job.id(), owner).target))) fail("已有交付记录的目标与定时任务不一致");
                return job;
            }
        if (existing.size() >= 500) fail("当前最多500条交付记录，请归档后继续");
        String id = UUID.randomUUID().toString(), now = Instant.now().toString();
        List<Entry> entries = new ArrayList<>();
        Set<String> filenames = new HashSet<>();
        for (var artifact : manifest.artifacts())
        {
            String filename = artifactName(artifact.filename());
            if (!filenames.add(filename)) fail("产物文件名重复，请在输出节点配置不同前缀后重新执行");
            entries.add(new Entry(artifact.id(), artifact.order(), filename,
                artifact.byteSize(), artifact.sha256(), "PENDING"));
        }
        StoredJob stored = new StoredJob(); stored.owner = owner; stored.target = connection.profile; stored.encryptedPassword = connection.encryptedPassword;
        String folder = connection.profile.directory().replaceAll("/$", "") + "/batch-" + input.runId() + "-" + id.substring(0, 8);
        stored.job = new Job(id, 1, input.runId(), input.connectionId(), connection.profile.name(), connection.profile.revision(), connection.profile.revision(), "QUEUED", folder,
            connection.profile.transferMode(), List.copyOf(entries), null, 1, now, now);
        files.write("job", id, stored); enqueue(stored); return stored.job;
    }
    public synchronized Job retry(String id, Retry request, long owner)
    {
        initialize(); StoredJob stored = owned(id, owner);
        if (request == null || request.revision() == null || request.revision() != stored.job.revision()) fail("交付记录已变化，请刷新");
        if (!Set.of("FAILED", "RECOVERY_REQUIRED").contains(stored.job.status())) fail("只有失败或待核实交付可重试");
        verifiedManifest(stored.job.runId(), owner); endpoint(stored.target.host(), stored.target.port());
        Job j = stored.job;
        long credentialRevision = j.credentialRevision();
        if (Boolean.TRUE.equals(request.refreshCredentials()))
        {
            StoredProfile latest = profile(j.connectionId(), owner); Profile target = stored.target, current = latest.profile;
            if (!target.host().equals(current.host()) || target.port() != current.port() || !target.directory().equals(current.directory())
                || !target.transferMode().equals(current.transferMode()) || !target.controlEncoding().equals(current.controlEncoding())) fail("当前连接的目标或传输设置已改变，不能用它重试原批次");
            stored.target = current; stored.encryptedPassword = latest.encryptedPassword; credentialRevision = current.revision();
        }
        stored.job = new Job(j.id(), j.revision() + 1, j.runId(), j.connectionId(), j.connectionName(), j.connectionRevision(), credentialRevision, "QUEUED", j.remoteDirectory(),
            j.transferMode(), j.entries(), null, j.attempts() + 1, j.createdAt(), Instant.now().toString());
        files.write("job", id, stored); enqueue(stored); return stored.job;
    }
    private void enqueue(StoredJob stored)
    {
        try { executor.execute(() -> deliver(stored)); }
        catch (RejectedExecutionException e) { update(stored, "FAILED", stored.job.entries(), "交付队列已满，请稍后重试"); }
    }
    private void deliver(StoredJob stored)
    {
        List<Entry> completed = new ArrayList<>(stored.job.entries());
        try
        {
            update(stored, "RUNNING", completed, null);
            validateManifest(stored);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(180);
            try (Remote remote = connect(stored.target, crypto.decrypt(stored.encryptedPassword), deadline))
            {
                remote.directory(stored.target.directory());
                String folder = stored.job.remoteDirectory().substring(stored.job.remoteDirectory().lastIndexOf('/') + 1);
                remote.batchDirectory(folder);
                byte[] marker = completionMarker(stored.job);
                String markerHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(marker));
                if (remote.exists("_SUCCESS.json"))
                {
                    try
                    {
                        remote.verify("_SUCCESS.json", marker.length, markerHash);
                        for (int i = 0; i < completed.size(); i++)
                        {
                            Entry entry = completed.get(i);
                            remote.verify(entry.filename(), entry.byteSize(), entry.sha256());
                            completed.set(i, new Entry(entry.artifactId(), entry.order(), entry.filename(), entry.byteSize(), entry.sha256(), "VERIFIED"));
                        }
                    }
                    catch (Exception e) { throw new PublishedBatchUnconfirmed(); }
                    update(stored, "DELIVERED", completed, null);
                    return; // A published batch is immutable. Never refill files already consumed by its receiver.
                }
                for (int i = 0; i < completed.size(); i++)
                {
                    if (Thread.currentThread().isInterrupted() || System.nanoTime() > deadline) throw new IOException("delivery deadline");
                    Entry entry = completed.get(i);
                    // Revalidate every existing final file after retry or restart; a recorded success alone is insufficient.
                    if (remote.exists(entry.filename())) remote.verify(entry.filename(), entry.byteSize(), entry.sha256());
                    else
                    {
                        String temporary = entry.filename() + ".temp";
                        try (InputStream input = artifacts.read(stored.job.runId(), entry.artifactId(), stored.owner))
                        { remote.upload(temporary, input, stored.target.transferMode()); }
                        remote.verify(temporary, entry.byteSize(), entry.sha256());
                        if (remote.exists(entry.filename())) remote.verify(entry.filename(), entry.byteSize(), entry.sha256());
                        else remote.rename(temporary, entry.filename());
                        remote.verify(entry.filename(), entry.byteSize(), entry.sha256());
                    }
                    completed.set(i, new Entry(entry.artifactId(), entry.order(), entry.filename(), entry.byteSize(), entry.sha256(), "VERIFIED"));
                    update(stored, "RUNNING", completed, null);
                }
                // This marker is published last. Receivers must use it as the batch completion gate.
                if (remote.exists("_SUCCESS.json")) remote.verify("_SUCCESS.json", marker.length, markerHash);
                else
                {
                    remote.upload("_SUCCESS.json.temp", new ByteArrayInputStream(marker), "BINARY");
                    remote.verify("_SUCCESS.json.temp", marker.length, markerHash);
                    remote.rename("_SUCCESS.json.temp", "_SUCCESS.json");
                    remote.verify("_SUCCESS.json", marker.length, markerHash);
                }
            }
            update(stored, "DELIVERED", completed, null);
        }
        catch (PublishedBatchUnconfirmed e)
        { update(stored, "RECOVERY_REQUIRED", completed, "远端已有完成标记，但无法确认完整批次；文件可能已被接收方取走，禁止自动补传以免重复消费"); }
        catch (Exception e)
        { update(stored, "FAILED", completed, "交付未确认完成；可能存在远端临时文件或已上传部分。本地产物已保留，重试前请核对连接与远端内容。"); }
    }
    private static final class PublishedBatchUnconfirmed extends Exception { }
    static byte[] completionMarker(Job job) throws Exception
    {
        Map<String, Object> marker = new LinkedHashMap<>();
        marker.put("version", 1); marker.put("runId", job.runId()); marker.put("deliveryId", job.id());
        marker.put("files", job.entries().stream().map(e -> {
            Map<String, Object> file = new LinkedHashMap<>(); file.put("name", e.filename()); file.put("bytes", e.byteSize()); file.put("sha256", e.sha256()); return file;
        }).toList());
        return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(marker);
    }
    private void validateManifest(StoredJob stored)
    {
        var manifest = verifiedManifest(stored.job.runId(), stored.owner);
        if (manifest.artifacts().isEmpty() || manifest.artifacts().size() != stored.job.entries().size()) fail("交付产物清单已变化");
        for (int i = 0; i < manifest.artifacts().size(); i++)
        {
            var artifact = manifest.artifacts().get(i); Entry entry = stored.job.entries().get(i);
            String expectedName = artifactName(artifact.filename());
            if (!artifact.id().equals(entry.artifactId()) || artifact.order() != entry.order() || artifact.byteSize() != entry.byteSize()
                || !artifact.sha256().equals(entry.sha256()) || !expectedName.equals(entry.filename())) fail("交付产物清单校验失败");
        }
        directory(stored.target.directory());
        String expectedDirectory = stored.target.directory().replaceAll("/$", "") + "/batch-" + stored.job.runId() + "-" + stored.job.id().substring(0, 8);
        if (!expectedDirectory.equals(stored.job.remoteDirectory())) fail("交付目录校验失败");
    }
    private DataGovernanceArtifactStore.Manifest verifiedManifest(String runId, long owner)
    {
        uuid(runId);
        var run = runs.find(runId);
        if (run == null || run.ownerId != owner || run.run == null || !"SUCCEEDED".equals(run.run.status) || !run.run.cleanupConfirmed
            || !run.run.artifactsManifestAvailable) fail("原运行未持久确认成功与清理，不能交付");
        var manifest = artifacts.manifest(runId, owner);
        if (!manifest.definitionHash().equals(run.run.definitionHash) || manifest.artifacts().size() != run.run.artifactCount) fail("运行与完整产物清单不一致");
        return manifest;
    }
    private synchronized void update(StoredJob stored, String status, List<Entry> entries, String error)
    {
        Job j = stored.job;
        stored.job = new Job(j.id(), j.revision() + 1, j.runId(), j.connectionId(), j.connectionName(), j.connectionRevision(), j.credentialRevision(), status,
            j.remoteDirectory(), j.transferMode(), List.copyOf(entries), error, j.attempts(), j.createdAt(), Instant.now().toString());
        files.write("job", j.id(), stored);
    }
    private StoredProfile profile(String id, long owner)
    { StoredProfile p = files.read("profile", id, StoredProfile.class); if (p.owner != owner || p.profile == null || !id.equals(p.profile.id())) fail("FTP连接不存在或无权访问"); return p; }
    private StoredJob owned(String id, long owner)
    { StoredJob j = files.read("job", id, StoredJob.class); if (j.owner != owner || j.job == null || !id.equals(j.job.id())) fail("交付记录不存在或无权访问"); return j; }
    private void initialize()
    {
        if (closed) fail("交付服务已关闭"); if (files != null) return;
        files = new DataGovernanceDeliveryFiles(properties);
        try
        {
            for (StoredJob stored : files.list("job", StoredJob.class))
                if (Set.of("QUEUED", "RUNNING").contains(stored.job.status()))
                    update(stored, "RECOVERY_REQUIRED", stored.job.entries(), "进程曾在交付期间中断；请显式重试，系统将回读核对已上传文件");
        }
        catch (Exception e) { files.close(); files = null; throw e; }
    }
    String endpoint(String host, Integer port)
    {
        host = host == null ? "" : host.trim(); if (host.equals("localhost")) host = "127.0.0.1";
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) fail("FTP连接只接受明确IPv4地址");
        for (String part : parts) if (!part.matches("0|[1-9][0-9]{0,2}") || Integer.parseInt(part) > 255) fail("FTP地址格式无效");
        if (port == null || port < 1 || port > 65535 || !properties.getFtpAllowedEndpoints().contains(host + ":" + port)) fail("FTP地址与端口未登记在服务端允许范围");
        return host;
    }
    static String directory(String value)
    {
        if (value == null || !value.startsWith("/") || value.length() > 240 || value.contains("//")) fail("FTP目录必须为明确的绝对路径");
        for (String segment : value.substring(1).split("/")) if (!segment.isEmpty() && (!segment.matches("[A-Za-z0-9_.-]{1,64}") || segment.equals(".") || segment.equals(".."))) fail("FTP目录格式无效");
        return value;
    }
    private static String artifactName(String name)
    {
        if (name == null || name.isBlank() || name.length() > 240 || name.contains("/") || name.contains("\\") || name.equals(".") || name.equals("..")
            || name.chars().anyMatch(c -> c < 32 || c == 127) || name.equals("_SUCCESS.json") || name.endsWith(".temp")) fail("产物文件名不适用于FTP批次交付");
        return name;
    }
    private static String text(String value, int max)
    { if (value == null || value.isBlank() || value.length() > max || value.chars().anyMatch(c -> c < 32 || c == 127)) fail("FTP配置文本格式无效"); return value.trim(); }
    private static void uuid(String id) { try { if (!UUID.fromString(id).toString().equals(id)) fail("运行标识无效"); } catch (Exception e) { throw new ServiceException("运行标识无效"); } }
    private static void fail(String message) { throw new ServiceException(message); }
    Remote connect(Profile profile, String password, long deadline) throws Exception
    { endpoint(profile.host(), profile.port()); return new FtpRemote(profile, password, deadline); }
    interface Remote extends AutoCloseable
    {
        void directory(String path) throws Exception;
        void batchDirectory(String name) throws Exception;
        boolean exists(String filename) throws Exception;
        void upload(String name, InputStream input, String mode) throws Exception;
        void verify(String name, long bytes, String sha256) throws Exception;
        void rename(String from, String to) throws Exception;
        void close() throws Exception;
    }
    static final class FtpRemote implements Remote
    {
        final BoundedFtpClient ftp = new BoundedFtpClient();
        final long deadline;
        FtpRemote(Profile profile, String password, long deadline) throws Exception
        {
            this.deadline = deadline;
            try
            {
                ftp.setConnectTimeout(3000); ftp.setDefaultTimeout(5000); ftp.setDataTimeout(Duration.ofSeconds(5));
                ftp.setControlEncoding(profile.controlEncoding()); ftp.setUseEPSVwithIPv4(true); ftp.setIpAddressFromPasvResponse(false); ftp.setRemoteVerificationEnabled(true);
                ftp.setCopyStreamListener(new CopyStreamAdapter() { @Override public void bytesTransferred(long total, int bytes, long size) { check(); } });
                ftp.connect(profile.host(), profile.port()); ftp.setSoTimeout(5000);
                if (!FTPReply.isPositiveCompletion(ftp.getReplyCode()) || !ftp.login(profile.username(), password)) throw new IOException("FTP login rejected");
                ftp.enterLocalPassiveMode();
            }
            catch (Exception e) { try { ftp.disconnect(); } catch (Exception ignored) { } throw e; }
        }
        private void check() { if (System.nanoTime() > deadline || Thread.currentThread().isInterrupted()) throw new IllegalStateException("FTP deadline"); }
        public void directory(String path) throws Exception { check(); if (!ftp.changeWorkingDirectory(path)) throw new IOException("FTP directory rejected"); }
        public void batchDirectory(String name) throws Exception { check(); if (!ftp.changeWorkingDirectory(name)) { if (!ftp.makeDirectory(name) || !ftp.changeWorkingDirectory(name)) throw new IOException("FTP batch directory rejected"); } }
        public boolean exists(String name) throws Exception
        {
            check(); List<FTPFile> found = ftp.boundedList(deadline);
            List<FTPFile> matches = found.stream().filter(file -> name.equals(file.getName())).toList();
            if (matches.size() > 1 || matches.size() == 1 && !matches.get(0).isFile()) throw new IOException("FTP target conflict");
            return matches.size() == 1;
        }
        public void upload(String name, InputStream input, String mode) throws Exception
        { check(); if (!ftp.setFileType(mode.equals("ASCII") ? FTP.ASCII_FILE_TYPE : FTP.BINARY_FILE_TYPE) || !ftp.storeFile(name, input)) throw new IOException("FTP upload unconfirmed"); }
        public void verify(String name, long bytes, String sha256) throws Exception
        {
            check(); if (!ftp.setFileType(FTP.BINARY_FILE_TYPE)) throw new IOException("FTP binary verification unavailable");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long[] count = {0};
            OutputStream sink = new OutputStream() {
                public void write(int value) throws IOException { write(new byte[]{(byte)value}, 0, 1); }
                public void write(byte[] value, int offset, int length) throws IOException {
                    check(); count[0] += length; if (count[0] > bytes) throw new IOException("FTP remote size mismatch"); digest.update(value, offset, length);
                }
            };
            if (!ftp.retrieveFile(name, sink) || count[0] != bytes || !HexFormat.of().formatHex(digest.digest()).equals(sha256)) throw new IOException("FTP content mismatch");
        }
        public void rename(String from, String to) throws Exception { check(); if (!ftp.rename(from, to)) throw new IOException("FTP rename unconfirmed"); }
        public void close() throws Exception { if (ftp.isConnected()) ftp.disconnect(); }
    }
    static final class BoundedFtpClient extends FTPClient
    {
        List<FTPFile> boundedList(long deadline) throws IOException
        {
            List<FTPFile> result;
            try (var socket = _openDataConnection_(FTPCmd.MLSD, (String) null))
            {
                if (socket == null) throw new IOException("FTP server must support bounded MLSD listings");
                socket.setSoTimeout(5000);
                result = readListing(socket.getInputStream(), getControlEncoding(), deadline);
            }
            if (!completePendingCommand()) throw new IOException("FTP listing completion unconfirmed");
            return result;
        }
        static List<FTPFile> readListing(InputStream input, String encoding, long deadline) throws IOException
        {
            InputStream limited = new FilterInputStream(input) {
                long count;
                private void check() throws IOException { if (System.nanoTime() > deadline || Thread.currentThread().isInterrupted()) throw new IOException("FTP listing deadline"); }
                @Override public int read() throws IOException { byte[] one = new byte[1]; int n = read(one, 0, 1); return n < 0 ? -1 : one[0] & 255; }
                @Override public int read(byte[] b, int off, int len) throws IOException {
                    check(); int n = in.read(b, off, len); check(); if (n > 0 && (count += n) > 65536) throw new IOException("FTP listing byte limit"); return n;
                }
            };
            List<FTPFile> result = new ArrayList<>();
            try (var reader = new BufferedReader(new InputStreamReader(limited, encoding)))
            {
                String line;
                while ((line = reader.readLine()) != null)
                {
                    if (line.length() > 1024 || result.size() >= 210 || System.nanoTime() > deadline) throw new IOException("FTP listing limit");
                    FTPFile file = org.apache.commons.net.ftp.parser.MLSxEntryParser.parseEntry(line);
                    if (file == null) throw new IOException("FTP listing entry invalid");
                    result.add(file);
                }
            }
            return result;
        }
    }
    @PreDestroy public void close()
    {
        synchronized (this) { closed = true; executor.shutdownNow(); }
        try { if (executor.awaitTermination(15, TimeUnit.SECONDS)) synchronized (this) { if (files != null) files.close(); } }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
