package com.hm.manage.service.governance;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.hm.common.exception.ServiceException;
import com.hm.manage.service.support.CredentialCryptoService;
import jakarta.annotation.PreDestroy;
import java.io.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.zip.*;
import org.springframework.stereotype.Service;
import org.w3c.dom.*;

/** Owner-scoped encrypted native definitions and immutable, recoverable submission records. */
@Service
public class DataGovernanceKettleService
{
    public record DefinitionInput(String name, Long revision, String xmlBase64) { }
    public record RunInput(Long revision, String mode, String previewStep, Integer rowLimit, String requestId) { }
    public record Summary(String id, String name, String kind, long revision, int nodeCount, String createdAt, String updatedAt)
    { @com.fasterxml.jackson.annotation.JsonProperty(access=com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
      public String runtimeFilename() { return id + (kind.equals("job") ? ".kjb" : ".ktr"); } }
    public record FileInfo(String id, String name, long bytes, String sha256, String uploadedAt) { }
    public static class Definition
    { public long owner; public Summary summary; public String encryptedXml; }
    public static class StoredFile
    { public FileInfo info; public String encryptedContent, sourceDefinitionId; public long sourceRevision; }
    public static class StoredRun
    {
        public long owner, revision;
        public String id, definitionId, kind, xmlSha256, encryptedXml, state, submissionState, createdAt, updatedAt;
        public String mode, previewStep, requestId, requestFingerprint, fingerprint, inputsHash, message;
        public int rowLimit;
        public List<StoredFile> inputs = new ArrayList<>();
        public JsonNode workerSnapshot;
    }
    static class FrozenDefinition
    {
        public long owner, definitionRevision;
        public String definitionId, kind, encryptedXml, xmlSha256, inputsHash;
        public List<StoredFile> inputs = new ArrayList<>();
    }
    static class EncryptedSnapshot { public String payload; }
    private static final long FILE_LIMIT = 8L * 1024 * 1024, TOTAL_FILES = 16L * 1024 * 1024;
    private final DataGovernanceKettleProperties properties;
    private final CredentialCryptoService crypto;
    private final DataGovernanceKettleClient worker;
    private final ObjectMapper mapper = new ObjectMapper();
    private Path root;
    private FileChannel channel;
    private FileLock lock;
    private boolean closed;

    public DataGovernanceKettleService(DataGovernanceKettleProperties properties, CredentialCryptoService crypto, DataGovernanceKettleClient worker)
    { this.properties = properties; this.crypto = crypto; this.worker = worker; }

    public Map<String, Object> status()
    {
        Map<String, Object> result = new LinkedHashMap<>(); result.put("enabled", properties.isEnabled());
        try { synchronized (this) { initialize(); } result.put("draftStorageAvailable", true); }
        catch (ServiceException e) { result.put("draftStorageAvailable", false); }
        result.put("executionAvailable", false);
        result.put("inputFileLimitBytes", FILE_LIMIT); result.put("inputTotalLimitBytes", TOTAL_FILES);
        result.put("xmlLimitBytes", DataGovernanceKettleXml.MAX_XML_BYTES); result.put("previewRowLimit", 200);
        if (properties.isEnabled())
        {
            try { JsonNode health = worker.request("GET", "/health", null); result.put("executionAvailable", "UP".equals(health.path("status").asText()));
                for (String key : List.of("engine", "sandbox", "protocolVersion")) if (health.has(key)) result.put(key, health.get(key)); }
            catch (DataGovernanceKettleClient.Failure e) { result.put("message", "原生 worker 暂不可用，草稿仍可读写"); }
        }
        else result.put("message", "原生 worker 未启用，草稿仍可读写");
        return result;
    }
    public Map<String, Object> catalog()
    {
        JsonNode capabilities = mapper.createObjectNode(); boolean available = false;
        if (properties.isEnabled()) try { capabilities = worker.request("GET", "/capabilities", null); available = true; }
            catch (DataGovernanceKettleClient.Failure ignored) { }
        Map<String, JsonNode> runtime = new LinkedHashMap<>();
        for (String kind : List.of("steps", "jobs")) for (JsonNode item : capabilities.path(kind))
            runtime.put(kind + ":" + item.path("id").asText() + ":" + item.path("className").asText(), item);
        List<Map<String, Object>> steps = new ArrayList<>(), jobs = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        JsonNode inventory = mapper.createObjectNode();
        try
        {
            if (!properties.getCatalogFile().isBlank())
            {
                Path file = Path.of(properties.getCatalogFile());
                if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && Files.size(file) <= 32 * 1024 * 1024) inventory = mapper.readTree(file.toFile());
            }
        }
        catch (Exception ignored) { }
        for (JsonNode item : inventory.path("plugins"))
        {
            String kind = item.path("kind").asText().equals("job-entry") ? "jobs" : "steps";
            List<String> aliases = Arrays.stream(item.path("id").asText().split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
            if (aliases.isEmpty()) continue;
            JsonNode live = null;
            for (String alias : aliases)
            { String key = kind + ":" + alias + ":" + item.path("registeredClass").asText();
              if (live == null) live = runtime.get(key); seen.add(key); }
            Map<String, Object> value = catalogEntry(kind, aliases.get(0), item.path("registeredClass").asText(),
                item.path("name").path("zhCN").path("text").asText(), item.path("category").path("zhCN").path("text").asText(), live, available);
            value.put("aliases", aliases);
            value.put("catalogKey", item.path("catalogKey").asText()); value.put("nameSource", item.path("name").path("zhCN").path("evidence").asText());
            value.put("classVariantCount", item.path("registeredClassVariants").size());
            (kind.equals("jobs") ? jobs : steps).add(value);
        }
        for (var entry : runtime.entrySet()) if (!seen.contains(entry.getKey()))
        {
            JsonNode item = entry.getValue(); String kind = entry.getKey().startsWith("jobs:") ? "jobs" : "steps";
            (kind.equals("jobs") ? jobs : steps).add(catalogEntry(kind, item.path("id").asText(), item.path("className").asText(),
                item.path("name").asText(), item.path("category").asText(), item, available));
        }
        return Map.of("steps", steps, "jobs", jobs, "workerAvailable", available,
            "staticInventoryAvailable", inventory.has("plugins"), "staticDiscoveredCount", inventory.path("plugins").size());
    }
    private Map<String, Object> catalogEntry(String kind, String id, String className, String name, String category, JsonNode live, boolean available)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id); result.put("kind", kind.equals("jobs") ? "job-entry" : "step"); result.put("name", name);
        result.put("category", category); result.put("className", className); result.put("discovered", true);
        boolean loadable = live != null && live.path("loadable").asBoolean(false);
        boolean supported = live != null && (live.has("executionSupported")
            ? live.path("executionSupported").asBoolean(false) : !kind.equals("jobs"));
        result.put("loadable", loadable); result.put("executionSupported", supported);
        result.put("executable", available && loadable && supported); result.put("executionValidated", false);
        result.put("status", loadable ? "LOADABLE" : live == null ? "DISCOVERED" : "LOAD_FAILED");
        if (loadable && live.hasNonNull("defaultXml"))
            try { result.put("defaultXmlBase64", DataGovernanceKettleXml.encode(DataGovernanceKettleXml.template(live.path("defaultXml").asText()))); }
            catch (ServiceException ignored) { result.put("defaultXmlUnavailable", true); }
        if (loadable && live.hasNonNull("configurationTemplateXml"))
            try { result.put("configurationTemplateXmlBase64", DataGovernanceKettleXml.encode(DataGovernanceKettleXml.template(live.path("configurationTemplateXml").asText()))); }
            catch (ServiceException ignored) { result.put("configurationTemplateXmlUnavailable", true); }
        if (live != null) for (String key : List.of("allocationApplied", "allocationMethod", "allocationArguments", "allocationReason", "configurationTemplateReadback"))
            if (live.has(key)) result.put(key, mapper.convertValue(live.get(key), Object.class));
        return result;
    }
    public synchronized List<Summary> definitions(long owner)
    {
        initialize(); List<Summary> result = new ArrayList<>();
        for (Path file : list(root.resolve("definitions"))) { Definition d = read(file, Definition.class); if (d.owner == owner) result.add(d.summary); }
        result.sort(Comparator.comparing(Summary::updatedAt).reversed()); return result;
    }
    public synchronized Map<String, Object> definition(String id, long owner)
    {
        Definition d = ownedDefinition(id, owner); Map<String, Object> result = summaryMap(d.summary);
        String xml = DataGovernanceKettleXml.withExecutionTimeZone(crypto.decrypt(d.encryptedXml)); result.put("executionTimeZone", DataGovernanceKettleXml.executionTimeZone(xml)); result.put("xmlBase64", DataGovernanceKettleXml.encode(DataGovernanceKettleXml.masked(xml)));
        result.put("references", references(xml)); return result;
    }
    public synchronized Map<String, Object> save(String id, DefinitionInput input, long owner)
    {
        initialize(); if (input == null) reject("请填写原生流程");
        Definition old = id == null ? null : ownedDefinition(id, owner);
        if (old != null && (input.revision() == null || input.revision() != old.summary.revision())) conflict();
        if (old == null && definitions(owner).size() >= 200) reject("当前用户最多保存 200 个原生流程");
        var prepared = DataGovernanceKettleXml.prepare(DataGovernanceKettleXml.decode(input.xmlBase64()), old == null ? null : crypto.decrypt(old.encryptedXml));
        if (old != null && !old.summary.kind().equals(prepared.kind())) reject("不能改变已有流程的原生类型，请另存为新流程");
        String now = Instant.now().toString(); Definition value = new Definition(); value.owner = owner;
        value.summary = new Summary(old == null ? UUID.randomUUID().toString() : id, name(input.name()), prepared.kind(),
            old == null ? 1 : old.summary.revision() + 1, prepared.nodeCount(), old == null ? now : old.summary.createdAt(), now);
        value.encryptedXml = crypto.encrypt(prepared.xml()); write(definitionPath(value.summary.id()), value);
        return definition(value.summary.id(), owner);
    }
    public synchronized List<Summary> imports(String filename, InputStream stream, long owner)
    {
        initialize(); if (filename == null) reject("缺少导入文件名");
        String lower = filename.toLowerCase(Locale.ROOT); List<DefinitionInput> candidates = new ArrayList<>();
        try
        {
            if (lower.endsWith(".zip"))
            {
                byte[] archive = stream.readNBytes(20 * 1024 * 1024 + 1);
                if (archive.length > 20 * 1024 * 1024) reject("ZIP 文件超过 20 MiB");
                try { candidates = zipCandidates(archive, StandardCharsets.UTF_8); }
                catch (IllegalArgumentException malformedName)
                {
                    // Actual legacy Hikvision ZIPs use unflagged GBK names. EFS-marked names remain UTF-8 in the JDK.
                    try { candidates = zipCandidates(archive, java.nio.charset.Charset.forName("GB18030")); }
                    catch (IllegalArgumentException notChineseName) { candidates = zipCandidates(archive, java.nio.charset.Charset.forName("IBM437")); }
                }
            }
            else if (lower.endsWith(".ktr") || lower.endsWith(".kjb")) candidates.add(importCandidate(filename, stream.readNBytes(DataGovernanceKettleXml.MAX_XML_BYTES + 1)));
            else reject("仅接受 .ktr、.kjb 或 .zip 文件");
        }
        catch (ServiceException e) { throw e; }
        catch (Exception e) { throw new ServiceException("导入文件无法解析；请提供有效原生 XML 或 ZIP，未替换其他附件"); }
        if (definitions(owner).size() + candidates.size() > 200) reject("导入后将超过 200 个流程上限");
        List<Summary> result = new ArrayList<>();
        for (DefinitionInput input : candidates) { String id = (String)save(null, input, owner).get("id"); result.add(ownedDefinition(id, owner).summary); }
        return result;
    }
    private List<DefinitionInput> zipCandidates(byte[] archive, java.nio.charset.Charset charset) throws IOException
    {
        List<DefinitionInput> result = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive), charset))
        {
            ZipEntry entry; long total = 0; int entries = 0;
            while ((entry = zip.getNextEntry()) != null)
            {
                if (++entries > 200) reject("ZIP 成员超过 200 个");
                String member = entry.getName().replace('\\', '/');
                if (member.startsWith("/") || member.matches("^[A-Za-z]:.*") || Arrays.asList(member.split("/")).contains("..")) reject("ZIP 含目录穿越路径");
                if (entry.isDirectory()) continue;
                byte[] bytes = zip.readNBytes(20 * 1024 * 1024 + 1); total += bytes.length;
                if (total > 20 * 1024 * 1024) reject("ZIP 解压内容总额超过 20 MiB");
                if (member.toLowerCase(Locale.ROOT).endsWith(".ktr") || member.toLowerCase(Locale.ROOT).endsWith(".kjb"))
                { if (result.size() >= 20) reject("ZIP 最多包含 20 个原生 XML 流程"); result.add(importCandidate(member, bytes)); }
            }
        }
        if (result.isEmpty()) reject("ZIP 未包含可解析的 KTR/KJB 原生 XML 文件");
        return result;
    }
    private DefinitionInput importCandidate(String member, byte[] bytes)
    {
        try
        {
            if (!xmlSignature(bytes)) throw new ServiceException("非 XML 成员");
            String xml = DataGovernanceKettleXml.decode(Base64.getEncoder().encodeToString(bytes));
            DataGovernanceKettleXml.prepare(xml, null); Document document = DataGovernanceKettleXml.parse(xml);
            String title = DataGovernanceKettleXml.child(document.getDocumentElement(), "name");
            if (title.isBlank() && document.getElementsByTagName("info").getLength() > 0)
                title = DataGovernanceKettleXml.child((Element)document.getElementsByTagName("info").item(0), "name");
            if (title.isBlank()) title = member.substring(member.lastIndexOf('/') + 1).replaceFirst("(?i)\\.(ktr|kjb)$", "");
            return new DefinitionInput(title.substring(0, Math.min(80, title.length())), null, DataGovernanceKettleXml.encode(xml));
        }
        catch (ServiceException e) { throw new ServiceException("导入成员不是支持的 UTF-8 原生 XML（可能为二进制、加密内容或不允许的结构），未替换其他附件：" + member.substring(Math.max(0, member.lastIndexOf('/') + 1))); }
    }
    private static boolean xmlSignature(byte[] bytes)
    {
        int offset = bytes.length >= 3 && (bytes[0] & 255) == 239 && (bytes[1] & 255) == 187 && (bytes[2] & 255) == 191 ? 3 : 0;
        while (offset < bytes.length && (bytes[offset] == ' ' || bytes[offset] == '\r' || bytes[offset] == '\n' || bytes[offset] == '\t')) offset++;
        return offset < bytes.length && bytes[offset] == '<';
    }
    public synchronized List<FileInfo> files(String id, long owner)
    { ownedDefinition(id, owner); return storedFiles(id).stream().map(f -> f.info).toList(); }
    public synchronized FileInfo upload(String id, String filename, InputStream stream, long owner)
    {
        ownedDefinition(id, owner); uploadFilename(filename); List<StoredFile> existing = storedFiles(id);
        if (existing.size() >= 20) reject("每个流程最多 20 个输入文件");
        if (existing.stream().anyMatch(f -> fileKey(f.info.name()).equals(fileKey(filename)))) reject("输入文件名重复（含 Unicode/大小写等价名称），请先删除旧文件");
        try
        {
            byte[] bytes = stream.readNBytes((int) FILE_LIMIT + 1);
            if (bytes.length > FILE_LIMIT || existing.stream().mapToLong(f -> f.info.bytes()).sum() + bytes.length > TOTAL_FILES) reject("输入文件单个最多 8 MiB、总额最多 16 MiB");
            StoredFile file = new StoredFile(); file.info = new FileInfo(UUID.randomUUID().toString(), filename, bytes.length, hash(bytes), Instant.now().toString());
            file.encryptedContent = crypto.encrypt(Base64.getEncoder().encodeToString(bytes));
            Path directory = directory(root.resolve("files").resolve(id)); write(directory.resolve(file.info.id() + ".json"), file); return file.info;
        }
        catch (ServiceException e) { throw e; }
        catch (Exception e) { throw new ServiceException("输入文件保存失败"); }
    }
    public synchronized void deleteFile(String id, String fileId, long owner)
    {
        ownedDefinition(id, owner); identifier(fileId); Path path = root.resolve("files").resolve(id).resolve(fileId + ".json");
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) reject("文件不存在或无权访问");
        try { Files.delete(path); } catch (IOException e) { throw new ServiceException("输入文件删除失败"); }
    }
    public JsonNode validate(String id, long owner)
    {
        String xml, kind; List<StoredFile> inputs;
        synchronized (this) { Definition d = ownedDefinition(id, owner); requireWorker(); xml = DataGovernanceKettleXml.withExecutionTimeZone(crypto.decrypt(d.encryptedXml)); kind = d.summary.kind(); inputs = snapshotInputs(d, xml, owner); }
        try { return scrub(worker.request("POST", kind.equals("job") ? "/jobs/validate" : "/transformations/validate", Map.of("xml", xml, "inputFiles", workerInputs(inputs))), xml, inputs); }
        catch (DataGovernanceKettleClient.Failure e) { throw new ServiceException("原生校验未完成，请检查 worker 状态"); }
    }
    public Map<String, Object> submit(String id, RunInput input, long owner)
    {
        StoredRun run; String xml;
        synchronized (this)
        {
            Definition definition = ownedDefinition(id, owner); requireWorker();
            if (input == null || input.revision() == null) conflict();
            String mode = input.mode() == null ? "run" : input.mode();
            if (!Set.of("run", "preview").contains(mode)) reject("运行模式必须为 run 或 preview");
            if (definition.summary.kind().equals("job") && mode.equals("preview")) reject("作业不支持步骤预览，请运行作业或预览其子转换");
            String target = input.previewStep() == null ? "" : input.previewStep();
            if (mode.equals("preview") && (target.isBlank() || target.length() > 200)) reject("预览必须指定步骤名");
            int limit = input.rowLimit() == null ? 20 : input.rowLimit(); if (limit < 1 || limit > 200) reject("预览采样行数须为 1 至 200");
            String requestId = input.requestId() == null ? UUID.randomUUID().toString() : input.requestId();
            if (!requestId.matches("[A-Za-z0-9_-]{1,80}")) reject("requestId 无效");
            String fingerprint = hash((id + ":" + input.revision() + ":" + mode + ":" + target + ":" + limit).getBytes(StandardCharsets.UTF_8));
            for (Path path : list(root.resolve("runs")))
            {
                StoredRun previous = read(path, StoredRun.class);
                if (previous.owner == owner && id.equals(previous.definitionId) && requestId.equals(previous.requestId))
                { if (!fingerprint.equals(previous.requestFingerprint == null ? previous.fingerprint : previous.requestFingerprint)) conflict(); return runView(previous); }
            }
            if (input.revision() != definition.summary.revision()) conflict();
            run = new StoredRun(); run.owner = owner; run.id = UUID.randomUUID().toString(); run.definitionId = id;
            run.kind = definition.summary.kind(); run.revision = definition.summary.revision(); run.mode = mode;
            run.previewStep = target; run.rowLimit = limit; run.requestId = requestId; run.requestFingerprint = fingerprint;
            xml = DataGovernanceKettleXml.withExecutionTimeZone(crypto.decrypt(definition.encryptedXml)); run.encryptedXml = crypto.encrypt(xml); run.xmlSha256 = hash(xml.getBytes(StandardCharsets.UTF_8));
            run.inputs = snapshotInputs(definition, xml, owner); run.inputsHash = inputsHash(run.inputs);
            run.fingerprint = hash((fingerprint + ":" + run.xmlSha256 + ":" + run.inputsHash).getBytes(StandardCharsets.UTF_8));
            run.state = "PREPARING"; run.submissionState = "PREPARING";
            run.createdAt = Instant.now().toString(); persistRun(run);
        }
        return dispatch(run, xml);
    }
    private Map<String,Object> dispatch(StoredRun run, String xml)
    {
        boolean submitting = false;
        try
        {
            Map<String, Object> stored = Map.of("xml", xml, "inputFiles", workerInputs(run.inputs));
            JsonNode prepared = worker.request("PUT", (run.kind.equals("job") ? "/jobs/" : "/transformations/") + run.id, stored);
            if (prepared.has("valid") && !prepared.path("valid").asBoolean() || prepared.path("validation").has("valid") && !prepared.path("validation").path("valid").asBoolean())
            { synchronized (this) { run.state = "VALIDATION_FAILED"; run.submissionState = "NOT_SUBMITTED"; run.workerSnapshot = scrub(prepared, xml, run.inputs); persistRun(run); return runView(run); } }
            synchronized (this) { run.state = "SUBMITTING"; run.submissionState = "SUBMITTING"; persistRun(run); }
            submitting = true;
            Map<String, Object> body = new LinkedHashMap<>(); body.put(run.kind.equals("job") ? "jobId" : "transformationId", run.id);
            body.put("runId", run.id); body.put("mode", run.mode); body.put("previewStep", run.previewStep); body.put("rowLimit", run.rowLimit); body.put("inputFiles", workerInputs(run.inputs));
            JsonNode response = worker.request("POST", "/runs", body);
            synchronized (this) { applySnapshot(run, response, xml); run.submissionState = "ACCEPTED"; persistRun(run); return runView(run); }
        }
        catch (DataGovernanceKettleClient.Failure e)
        {
            synchronized (this)
            {
                run.state = submitting ? (e.uncertain() ? "SUBMISSION_UNKNOWN" : "SUBMISSION_REJECTED") : "PREPARATION_FAILED";
                run.submissionState = submitting && e.uncertain() ? "UNKNOWN" : "NOT_SUBMITTED";
                run.message = submitting && e.uncertain() ? "提交结果未知，已保留冻结快照；请核查此运行，系统不会自动重发" : "原生 worker 未接收本次运行，请检查校验或 worker 状态";
                persistRun(run); return runView(run);
            }
        }
    }
    synchronized Summary definitionSummary(String id, long owner) { return ownedDefinition(id, owner).summary; }
    synchronized Path scheduleDirectory() { initialize(); return directory(root.resolve("schedules")); }
    synchronized FrozenDefinition captureFrozen(String id, long revision, long owner)
    {
        Definition definition = ownedDefinition(id, owner); if (revision != definition.summary.revision()) conflict();
        FrozenDefinition snapshot = new FrozenDefinition(); snapshot.owner = owner; snapshot.definitionId = id;
        snapshot.definitionRevision = revision; snapshot.kind = definition.summary.kind();
        String xml = DataGovernanceKettleXml.withExecutionTimeZone(crypto.decrypt(definition.encryptedXml));
        snapshot.encryptedXml = crypto.encrypt(xml); snapshot.xmlSha256 = hash(xml.getBytes(StandardCharsets.UTF_8));
        snapshot.inputs = snapshotInputs(definition, xml, owner); snapshot.inputsHash = inputsHash(snapshot.inputs); return snapshot;
    }
    synchronized void writeFrozen(String id, FrozenDefinition snapshot)
    {
        initialize(); identifier(id); Path path = directory(root.resolve("schedule-snapshots")).resolve(id + ".json");
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) reject("计划快照不可覆盖");
        try { EncryptedSnapshot envelope = new EncryptedSnapshot(); envelope.payload = crypto.encrypt(mapper.writeValueAsString(snapshot)); write(path, envelope); }
        catch (ServiceException e) { throw e; } catch (Exception e) { throw new ServiceException("原生计划快照保存失败"); }
    }
    synchronized FrozenDefinition readFrozen(String id, long owner)
    {
        initialize(); identifier(id); EncryptedSnapshot envelope = read(root.resolve("schedule-snapshots").resolve(id + ".json"), EncryptedSnapshot.class);
        try { FrozenDefinition snapshot = mapper.readValue(crypto.decrypt(envelope.payload), FrozenDefinition.class);
            if (snapshot.owner != owner) unavailable(); return snapshot; }
        catch (ServiceException e) { throw e; } catch (Exception e) { throw new ServiceException("原生计划快照读取失败"); }
    }
    Map<String,Object> submitFrozen(FrozenDefinition snapshot, String runId, long owner)
    {
        StoredRun run; String xml;
        synchronized (this)
        {
            initialize(); identifier(runId); if (snapshot.owner != owner) unavailable(); ownedDefinition(snapshot.definitionId, owner); requireWorker();
            // Validate the frozen copy itself; never rebuild it from today's definition or assets.
            xml = crypto.decrypt(snapshot.encryptedXml);
            DataGovernanceKettleXml.executionTimeZone(xml);
            for (Map<String,Object> reference : references(xml))
                if (!((String)reference.get("definitionId")).isBlank()) inputReferenceFilename(reference);
            if (Files.exists(root.resolve("runs").resolve(runId + ".json"), LinkOption.NOFOLLOW_LINKS))
            { StoredRun prior = ownedRun(runId, owner); if (!snapshot.xmlSha256.equals(prior.xmlSha256) || !snapshot.inputsHash.equals(prior.inputsHash)) conflict(); return runView(prior); }
            run = new StoredRun(); run.id = runId; run.owner = owner; run.definitionId = snapshot.definitionId; run.revision = snapshot.definitionRevision;
            run.kind = snapshot.kind; run.mode = "run"; run.previewStep = ""; run.rowLimit = 20; run.requestId = "schedule-" + runId;
            run.requestFingerprint = hash((run.definitionId + ":" + run.revision + ":run::20").getBytes(StandardCharsets.UTF_8));
            run.encryptedXml = snapshot.encryptedXml; run.xmlSha256 = snapshot.xmlSha256; run.inputs = snapshot.inputs; run.inputsHash = snapshot.inputsHash;
            run.fingerprint = hash((run.requestFingerprint + ":" + run.xmlSha256 + ":" + run.inputsHash).getBytes(StandardCharsets.UTF_8));
            run.state = "PREPARING"; run.submissionState = "PREPARING"; run.createdAt = Instant.now().toString();
            persistRun(run); xml = crypto.decrypt(run.encryptedXml);
        }
        return dispatch(run, xml);
    }
    public Map<String, Object> run(String id, long owner)
    {
        StoredRun run; synchronized (this) { run = ownedRun(id, owner); }
        boolean workerStateAvailable = false;
        // The native engine also reports PREPARING after acceptance. Submission state owns this gate.
        if (properties.isEnabled() && !Set.of("PREPARING", "NOT_SUBMITTED").contains(run.submissionState))
        {
            try
            {
                JsonNode snapshot = worker.request("GET", "/runs/" + id, null);
                synchronized (this) { run = ownedRun(id, owner); applySnapshot(run, snapshot, crypto.decrypt(run.encryptedXml)); run.submissionState = "ACCEPTED"; persistRun(run); }
                workerStateAvailable = true;
            }
            catch (DataGovernanceKettleClient.Failure e) { /* Read-only reconciliation never submits work. */ }
        }
        Map<String,Object> result = runView(run); result.put("workerStateAvailable", workerStateAvailable);
        if (!workerStateAvailable && Set.of("ACCEPTED", "UNKNOWN", "SUBMITTING").contains(run.submissionState))
            result.put("reconciliationMessage", "worker 当前未返回此运行状态，展示已保存快照；不会自动重复提交");
        return result;
    }
    public synchronized List<Map<String,Object>> runs(String definitionId, long owner)
    {
        ownedDefinition(definitionId, owner);
        List<Map<String,Object>> result = new ArrayList<>();
        for (Path path : list(root.resolve("runs")))
        {
            // Skip encrypted payloads and row snapshots without materializing them for a history query.
            Map<String,Object> summary = readRunSummary(path);
            if (((Number)summary.getOrDefault("owner", -1L)).longValue() != owner || !definitionId.equals(summary.get("definitionId"))) continue;
            summary.remove("owner");
            if (summary.get("inputsHash") != null) summary.put("snapshotFingerprint", summary.get("fingerprint"));
            summary.remove("fingerprint");
            if ("SUBMITTING".equals(summary.get("state"))) summary.put("state", "SUBMISSION_UNKNOWN");
            result.add(summary);
        }
        result.sort(Comparator.comparing((Map<String,Object> r) -> Instant.parse((String)r.get("createdAt"))).reversed());
        return result;
    }
    private Map<String,Object> readRunSummary(Path path)
    {
        Set<String> allowed = Set.of("owner", "id", "definitionId", "kind", "revision", "xmlSha256", "state", "submissionState",
            "createdAt", "updatedAt", "mode", "previewStep", "rowLimit", "requestId", "message", "inputsHash", "fingerprint");
        Map<String,Object> result = new LinkedHashMap<>();
        try (var parser = mapper.getFactory().createParser(path.toFile()))
        {
            if (parser.nextToken() != com.fasterxml.jackson.core.JsonToken.START_OBJECT) reject("运行历史记录无效");
            com.fasterxml.jackson.core.JsonToken token;
            while ((token = parser.nextToken()) != com.fasterxml.jackson.core.JsonToken.END_OBJECT)
            {
                if (token != com.fasterxml.jackson.core.JsonToken.FIELD_NAME) reject("运行历史记录无效");
                String field = parser.currentName(); parser.nextToken();
                if (allowed.contains(field)) result.put(field, mapper.readValue(parser, Object.class)); else parser.skipChildren();
            }
            return result;
        }
        catch (ServiceException e) { throw e; } catch (Exception e) { throw new ServiceException("运行历史读取失败"); }
    }
    public Map<String, Object> events(String id, long after, long owner)
    {
        StoredRun run; synchronized (this) { run = ownedRun(id, owner); }
        if (after < 0) reject("事件游标不能为负数");
        if (!properties.isEnabled()) return Map.of("events", List.of(), "nextCursor", after, "state", run.state, "workerAvailable", false);
        try
        {
            JsonNode value = scrub(worker.request("GET", "/runs/" + id + "/events?after=" + after, null), crypto.decrypt(run.encryptedXml), run.inputs);
            return Map.of("events", value.path("events"), "nextCursor", value.path("nextCursor").asLong(after), "state", value.path("state").asText(run.state), "workerAvailable", true);
        }
        catch (DataGovernanceKettleClient.Failure e) { return Map.of("events", List.of(), "nextCursor", after, "state", run.state, "workerAvailable", false); }
    }
    public Map<String, Object> stop(String id, long owner)
    {
        StoredRun run; synchronized (this) { run = ownedRun(id, owner); requireWorker(); }
        try
        {
            JsonNode response = worker.request("POST", "/runs/" + id + "/stop", Map.of());
            synchronized (this) { run = ownedRun(id, owner); applySnapshot(run, response, crypto.decrypt(run.encryptedXml)); persistRun(run); return runView(run); }
        }
        catch (DataGovernanceKettleClient.Failure e) { throw new ServiceException("停止请求未确认，请继续核查运行状态"); }
    }
    public DataGovernanceKettleClient.Download download(String id, String filename, long owner)
    {
        synchronized (this) { ownedRun(id, owner); requireWorker(); }
        if (unsafeBasename(filename) || filename.getBytes(StandardCharsets.UTF_8).length > 255) reject("产物名称必须是文件名");
        try { return worker.download(id, filename); } catch (DataGovernanceKettleClient.Failure e) { throw new ServiceException("产物不存在或 worker 不可用"); }
    }
    private synchronized List<StoredFile> snapshotInputs(Definition definition, String xml, long owner)
    {
        Map<String,StoredFile> merged = new LinkedHashMap<>();
        for (StoredFile file : storedFiles(definition.summary.id()))
        { file.sourceDefinitionId = definition.summary.id(); file.sourceRevision = definition.summary.revision(); mergeInput(merged, file); }
        for (Map<String, Object> reference : references(xml))
        {
            String bound = (String)reference.get("definitionId"); if (bound.isBlank()) continue;
            Definition target = ownedDefinition(bound, owner);
            if (!target.summary.kind().equals("transformation")) reject("TRANS 只能关联当前用户的转换定义");
            for (StoredFile asset : storedFiles(target.summary.id()))
            { asset.sourceDefinitionId = target.summary.id(); asset.sourceRevision = target.summary.revision(); mergeInput(merged, asset); }
            String filename = inputReferenceFilename(reference);
            String contents = DataGovernanceKettleXml.withExecutionTimeZone(crypto.decrypt(target.encryptedXml)); byte[] bytes = contents.getBytes(StandardCharsets.UTF_8);
            StoredFile file = new StoredFile(); file.info = new FileInfo(target.summary.id(), filename, bytes.length, hash(bytes), target.summary.updatedAt());
            file.encryptedContent = crypto.encrypt(Base64.getEncoder().encodeToString(bytes));
            file.sourceDefinitionId = target.summary.id(); file.sourceRevision = target.summary.revision(); mergeInput(merged, file);
        }
        List<StoredFile> result = new ArrayList<>(merged.values());
        if (result.size() > 20 || result.stream().mapToLong(f -> f.info.bytes()).sum() > TOTAL_FILES) reject("运行输入及关联转换总额超过 20 文件或 16 MiB");
        return result;
    }
    private static String inputReferenceFilename(Map<String,Object> reference)
    {
        String id = (String)reference.get("definitionId"); identifier(id);
        String filename = id + ".ktr";
        if (!("${INPUT_DIR}/" + filename).equals(reference.get("filename")))
            reject("关联转换文件名须为 ${INPUT_DIR}/关联定义UUID.ktr；WORK_DIR 仅用于输出");
        return filename;
    }
    private static void mergeInput(Map<String,StoredFile> merged, StoredFile file)
    {
        StoredFile previous = merged.putIfAbsent(fileKey(file.info.name()), file);
        if (previous != null && !previous.info.sha256().equals(file.info.sha256()))
            reject("输入文件名冲突且内容不同，禁止自动覆盖：" + file.info.name());
    }
    private String inputsHash(List<StoredFile> inputs)
    {
        var metadata = inputs.stream().sorted(Comparator.comparing(f -> fileKey(f.info.name())))
            .map(f -> Map.of("name", f.info.name(), "bytes", f.info.bytes(), "sha256", f.info.sha256())).toList();
        try { return hash(mapper.writer().with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsBytes(metadata)); }
        catch (Exception e) { throw new ServiceException("输入快照指纹生成失败"); }
    }
    private static String fileKey(String name)
    {
        // Upper/lower expansion includes sharp-s and final-sigma equivalence; preserve dotless i.
        String normalized = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFC);
        StringBuilder folded = new StringBuilder();
        normalized.codePoints().forEach(c -> folded.append(c == 0x0131 ? "\u0131" : new String(Character.toChars(c)).toUpperCase(Locale.ROOT).toLowerCase(Locale.ROOT)));
        return java.text.Normalizer.normalize(folded, java.text.Normalizer.Form.NFC);
    }
    private List<Map<String, Object>> references(String xml)
    {
        List<Map<String, Object>> result = new ArrayList<>(); NodeList entries = DataGovernanceKettleXml.parse(xml).getElementsByTagName("entry");
        for (int i = 0; i < entries.getLength(); i++)
        {
            Element entry = (Element)entries.item(i);
            if (DataGovernanceKettleXml.child(entry, "type").equals("TRANS")) result.add(Map.of("elementId", entry.getAttribute(DataGovernanceKettleXml.ID),
                "name", DataGovernanceKettleXml.child(entry, "name"), "filename", DataGovernanceKettleXml.child(entry, "filename"),
                "definitionId", entry.getAttribute("data-rynew-definition-id"), "bound", !entry.getAttribute("data-rynew-definition-id").isBlank()));
        }
        return result;
    }
    private List<Map<String, Object>> workerInputs(List<StoredFile> files)
    { return files.stream().map(f -> Map.<String,Object>of("name", f.info.name(), "contentBase64", crypto.decrypt(f.encryptedContent))).toList(); }
    private void applySnapshot(StoredRun run, JsonNode snapshot, String xml)
    {
        run.workerSnapshot = scrub(snapshot, xml, run.inputs); run.state = snapshot.path("state").asText(run.state); run.message = null;
    }
    private JsonNode scrub(JsonNode value, String xml, List<StoredFile> inputs)
    {
        List<String> secrets = new ArrayList<>(DataGovernanceKettleXml.secretValues(xml));
        for (StoredFile file : inputs) if (file.info.name().endsWith(".ktr") || file.info.name().endsWith(".kjb"))
            try { secrets.addAll(DataGovernanceKettleXml.secretValues(DataGovernanceKettleXml.decode(crypto.decrypt(file.encryptedContent)))); }
            catch (ServiceException ignored) { }
        secrets.sort(Comparator.comparingInt(String::length).reversed()); return scrubNode(value, secrets);
    }
    private JsonNode scrubNode(JsonNode value, List<String> secrets)
    {
        if (value.isObject())
        {
            ObjectNode out = mapper.createObjectNode(); value.fields().forEachRemaining(entry -> {
                if (Set.of("xml", "defaultXml", "encryptedXml", "directory", "classpath", "token", "fingerprint").contains(entry.getKey())) return;
                out.set(entry.getKey(), DataGovernanceKettleXml.sensitiveName(entry.getKey()) ? TextNode.valueOf("[redacted]") : scrubNode(entry.getValue(), secrets)); }); return out;
        }
        if (value.isArray()) { ArrayNode out = mapper.createArrayNode(); value.forEach(v -> out.add(scrubNode(v, secrets))); return out; }
        if (value.isTextual())
        { String text = value.asText(); for (String secret : secrets) text = text.replace(secret, "[redacted]");
          return TextNode.valueOf(text.replaceAll("(?i)((?:password|passwd|secret|token)\\s*[=:]\\s*)[^\\s,;]+", "$1[redacted]")); }
        return value;
    }
    private Map<String, Object> runView(StoredRun run)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        if (run.workerSnapshot != null && run.workerSnapshot.isObject()) run.workerSnapshot.fields().forEachRemaining(e -> result.put(e.getKey(), e.getValue()));
        result.put("id", run.id); result.put("definitionId", run.definitionId); result.put("kind", run.kind); result.put("revision", run.revision);
        result.put("xmlSha256", run.xmlSha256); result.put("state", run.state.equals("SUBMITTING") ? "SUBMISSION_UNKNOWN" : run.state);
        if (run.inputsHash != null) { result.put("inputsHash", run.inputsHash); result.put("snapshotFingerprint", run.fingerprint); }
        result.put("submissionState", run.submissionState); result.put("mode", run.mode); result.put("createdAt", run.createdAt); result.put("updatedAt", run.updatedAt);
        result.put("previewStep", run.previewStep); result.put("rowLimit", run.rowLimit);
        result.put("executionTimeZone", DataGovernanceKettleXml.executionTimeZone(crypto.decrypt(run.encryptedXml)));
        result.put("requestId", run.requestId); result.put("inputFiles", run.inputs.stream().map(f -> f.info).toList());
        result.putIfAbsent("nodes", List.of()); result.putIfAbsent("files", List.of()); if (run.message != null) result.put("message", run.message);
        return result;
    }
    private Map<String, Object> summaryMap(Summary value)
    { return mapper.convertValue(value, new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String,Object>>() { }); }
    private List<StoredFile> storedFiles(String id)
    { return list(root.resolve("files").resolve(id)).stream().map(p -> read(p, StoredFile.class)).toList(); }
    private Definition ownedDefinition(String id, long owner)
    {
        initialize(); identifier(id); Path path = definitionPath(id); if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) unavailable();
        Definition value = read(path, Definition.class); if (value.owner != owner) unavailable(); return value;
    }
    private StoredRun ownedRun(String id, long owner)
    {
        initialize(); identifier(id); Path path = root.resolve("runs").resolve(id + ".json");
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) unavailable(); StoredRun value = read(path, StoredRun.class);
        if (value.owner != owner) unavailable(); return value;
    }
    private Path definitionPath(String id) { return root.resolve("definitions").resolve(id + ".json"); }
    private void persistRun(StoredRun run) { run.updatedAt = Instant.now().toString(); write(root.resolve("runs").resolve(run.id + ".json"), run); }
    private synchronized void initialize()
    {
        if (closed) reject("原生流程存储已关闭"); if (root != null) return;
        try
        {
            Path candidate = Path.of(properties.getStorageDir()).toAbsolutePath().normalize(); directory(candidate);
            Path lockPath = candidate.resolve(".store.lock"); if (Files.isSymbolicLink(lockPath)) reject("存储锁不能为符号链接");
            channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
            lock = channel.tryLock(); if (lock == null) reject("原生流程目录已由其他进程使用");
            directory(candidate.resolve("definitions")); directory(candidate.resolve("runs")); directory(candidate.resolve("files")); root = candidate;
        }
        catch (Exception e) { try { if (channel != null) channel.close(); } catch (IOException ignored) { } throw new ServiceException("原生流程存储初始化失败或已被占用"); }
    }
    private Path directory(Path path)
    {
        try { if (Files.isSymbolicLink(path)) reject("存储目录不能为符号链接"); Files.createDirectories(path);
            try { Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwx------")); } catch (UnsupportedOperationException ignored) { } return path; }
        catch (IOException e) { throw new ServiceException("无法建立原生流程目录"); }
    }
    private List<Path> list(Path folder)
    {
        if (!Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS)) return List.of();
        try (var stream = Files.newDirectoryStream(folder, "*.json"))
        { List<Path> paths = new ArrayList<>(); for (Path p : stream) if (Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) paths.add(p); return paths; }
        catch (IOException e) { throw new ServiceException("原生流程目录读取失败"); }
    }
    private <T> T read(Path path, Class<T> type)
    {
        try { if (Files.isSymbolicLink(path) || Files.size(path) > 64L * 1024 * 1024) reject("存储记录无效"); return mapper.readValue(Files.readAllBytes(path), type); }
        catch (ServiceException e) { throw e; } catch (Exception e) { throw new ServiceException("原生流程记录读取失败"); }
    }
    private void write(Path target, Object value)
    {
        if (closed) reject("原生流程存储已关闭");
        Path temporary = target.resolveSibling(target.getFileName() + "." + UUID.randomUUID() + ".tmp");
        try
        {
            if (Files.isSymbolicLink(target)) reject("存储记录不能为符号链接");
            byte[] bytes = mapper.writeValueAsBytes(value);
            try (FileChannel file = FileChannel.open(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))
            { try { Files.setPosixFilePermissions(temporary, PosixFilePermissions.fromString("rw-------")); } catch (UnsupportedOperationException ignored) { }
              java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) file.write(buffer); file.force(true); }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (ServiceException e) { throw e; } catch (Exception e) { throw new ServiceException("原生流程记录保存失败"); }
        finally { try { Files.deleteIfExists(temporary); } catch (IOException ignored) { } }
    }
    private static String hash(byte[] bytes)
    { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); } catch (Exception e) { throw new IllegalStateException(e); } }
    private void requireWorker() { if (!properties.isEnabled()) reject("原生 worker 未启用，当前只能编辑和保存草稿"); }
    private static String name(String value) { if (value == null || value.isBlank() || value.length() > 80 || controls(value)) reject("流程名称须为 1 至 80 个有效字符"); return value.trim(); }
    private static void identifier(String value) { if (value == null || !value.matches("[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}")) unavailable(); }
    private static void uploadFilename(String value)
    { if (unsafeBasename(value) || value.getBytes(StandardCharsets.UTF_8).length > 240) reject("文件名须为安全 UTF-8 名称，最多 240 字节，不能含路径或控制字符"); }
    private static boolean unsafeBasename(String value)
    { return value == null || value.isBlank() || value.equals(".") || value.equals("..") || value.indexOf('/') >= 0 || value.indexOf('\\') >= 0 || controls(value); }
    private static boolean controls(String value)
    { return value.codePoints().anyMatch(c -> Character.isISOControl(c) || Character.getType(c) == Character.FORMAT || Character.getType(c) == Character.SURROGATE); }
    private static void conflict() { throw new ServiceException("流程修订已变化或请求标识冲突，请刷新后重新编辑", 409); }
    private static void unavailable() { throw new ServiceException("资源不存在或无权访问", 404); }
    private static void reject(String text) { throw new ServiceException(text); }
    @PreDestroy public synchronized void close()
    { closed = true; try { if (lock != null) lock.release(); } catch (IOException ignored) { } try { if (channel != null) channel.close(); } catch (IOException ignored) { } }
}
