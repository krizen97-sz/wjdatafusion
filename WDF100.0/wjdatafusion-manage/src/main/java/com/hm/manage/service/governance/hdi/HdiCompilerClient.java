package com.hm.manage.service.governance.hdi;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

/** One-shot transport to the original configuration compiler; never accepts executable arguments from a task. */
@Component
public class HdiCompilerClient {
    private static final int MAX_REQUEST = 8 * 1024 * 1024;
    private static final int MAX_STDOUT = 32 * 1024 * 1024;
    private static final int MAX_STDERR = 64 * 1024;
    private static final int MAX_LOG_FILES = 32;
    private static final Set<String> CONTEXT_KEYS = Set.of("businessTaskId", "revision", "ownerId", "workspaceId", "projectCode");
    private final ObjectMapper json = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final HdiCoreProperties properties;
    private final Duration timeout;
    private final int maxStdout;
    private final Semaphore slots;

    @Autowired
    public HdiCompilerClient(HdiCoreProperties properties) { this(properties, Duration.ofSeconds(45), MAX_STDOUT, 2); }
    HdiCompilerClient(HdiCoreProperties properties, Duration timeout, int maxStdout, int concurrency) {
        this.properties = properties; this.timeout = timeout; this.maxStdout = maxStdout; slots = new Semaphore(concurrency);
    }

    public JsonNode prepare(Map<String,Object> trustedContext, JsonNode visual) {
        JsonNode context = context(trustedContext);
        if (visual == null || !visual.isObject()) throw new HdiTaskFault(422, "流程定义必须是原画布对象");
        ObjectNode request = json.createObjectNode().put("operation", "prepare");
        request.set("context", context); request.set("visual", visual);
        JsonNode response = invoke(request);
        require("prepare".equals(response.path("operation").asText()), "operation");
        JsonNode snapshot = checkedSnapshot(response.path("snapshotJson"), response.path("snapshotSha256"));
        require(context.equals(snapshot.path("context")), "context");
        require(response.path("canonicalVisual").isObject() && snapshot.path("visual").equals(response.path("canonicalVisual")), "visual");
        require(identity(snapshot.path("visual").path("flowId"), context.path("businessTaskId").asText()), "flowId");
        JsonNode flow = snapshot.path("visual").path("flowVo");
        for (String[] binding : new String[][]{{"id", "businessTaskId"}, {"groupId", "workspaceId"}, {"userId", "ownerId"}})
            require(identity(flow.path(binding[0]), context.path(binding[1]).asText()), "visual identity");
        require(context.path("projectCode").equals(flow.path("projectCode")), "project");
        return response;
    }

    /** Original ScheduleConfigVo-to-Cron conversion in the same isolated compiler, not an ETL algorithm. */
    public JsonNode schedule(JsonNode scheduleConfig, String zoneId) {
        require(scheduleConfig != null && scheduleConfig.isObject(), "schedule config");
        try { java.time.ZoneId.of(zoneId); } catch (RuntimeException invalid) { throw new HdiTaskFault(503, "平台调度时区配置无效"); }
        ObjectNode request = json.createObjectNode().put("operation", "schedule").put("zoneId", zoneId); request.set("scheduleConfig", scheduleConfig);
        JsonNode result = invoke(request);
        require("schedule".equals(result.path("operation").asText()) && zoneId.equals(result.path("zoneId").asText()) && scheduleConfig.equals(result.path("scheduleConfig")), "schedule binding");
        require(result.path("cronExpressions").isArray() && !result.path("cronExpressions").isEmpty() && result.path("cronExpressions").size() <= 32, "schedule expressions");
        for (JsonNode expression : result.path("cronExpressions")) require(expression.isTextual() && expression.asText().length() <= 256 && org.quartz.CronExpression.isValidExpression(expression.asText()), "schedule cron");
        return result;
    }

    public JsonNode compile(String snapshotJson, String sha, Map<String,Object> trustedRunContext) {
        JsonNode snapshot = checkedSnapshot(json.getNodeFactory().textNode(snapshotJson == null ? "" : snapshotJson), json.getNodeFactory().textNode(sha == null ? "" : sha));
        JsonNode frozen = snapshot.path("context");
        JsonNode run = json.valueToTree(trustedRunContext);
        require(run != null && run.isObject(), "runContext");
        for (String key : List.of("businessTaskId", "revision")) require(frozen.path(key).equals(run.path(key)), "run revision");
        for (String key : List.of("nativeFlowId", "nativeInstanceId", "metadataIdBase", "execId")) decimal(run.path(key));
        require(run.path("outputsDir").isTextual() && run.path("authorizedOutputRoot").isTextual(), "output policy");
        ObjectNode request = json.createObjectNode().put("operation", "compile").put("snapshotJson", snapshotJson).put("snapshotSha256", sha);
        request.set("runContext", run);
        JsonNode response = invoke(request);
        require("compile".equals(response.path("operation").asText()), "operation");
        for (String key : List.of("businessTaskId", "revision", "ownerId", "workspaceId")) require(frozen.path(key).equals(response.path(key)), "compiled identity");
        for (String[] pair : new String[][]{{"flowId", "nativeFlowId"}, {"instanceId", "nativeInstanceId"}, {"execId", "execId"}})
            require(run.path(pair[1]).equals(response.path(pair[0])), "compiled run");
        require(sha.equals(response.path("snapshotSha256").asText()), "snapshot binding");
        JsonNode flow = payload(response.path("flowjson"), MAX_REQUEST);
        require(identity(flow.path("flow").path("id"), run.path("nativeFlowId").asText()), "native flow");
        require(identity(flow.path("flow").path("group_id"), frozen.path("workspaceId").asText()), "native group");
        JsonNode schedule = payload(response.path("schedule"), 1024 * 1024), stop = payload(response.path("stop"), 4096);
        for (JsonNode control : List.of(schedule, stop)) {
            require(identity(control.path("id"), run.path("nativeFlowId").asText()), "control flow");
            require(control.path("oper_type").isInt() && control.path("oper_type").intValue() == 1, "control operation");
        }
        require(schedule.path("oper_value").isInt() && schedule.path("oper_value").intValue() == 1, "schedule operation");
        require(stop.path("oper_value").isInt() && stop.path("oper_value").intValue() == 0, "stop operation");
        require(run.path("execId").equals(schedule.path("exec_id")), "execution ID");
        require(identity(schedule.path("strategy_flow").path("id"), run.path("nativeFlowId").asText()), "schedule flow");
        require(identity(schedule.path("strategy_flow").path("group_id"), frozen.path("workspaceId").asText()), "schedule group");
        require(identity(schedule.path("strategy_flow").path("etl_schedule").path("node_id"), run.path("nativeFlowId").asText()), "schedule node");
        require(response.path("metadataSnapshot").path("outputsDir").equals(run.path("outputsDir")), "output directory");
        require(response.path("metadataSnapshot").path("metadataIdBase").equals(run.path("metadataIdBase")), "metadata block");
        validateMappings(response.path("nodeMappings"), flow, snapshot.path("visual"), run);
        return response;
    }

    private JsonNode context(Map<String,Object> trusted) {
        require(trusted != null && trusted.keySet().equals(CONTEXT_KEYS), "trusted context");
        JsonNode value = json.valueToTree(trusted);
        for (String key : List.of("businessTaskId", "revision", "ownerId", "workspaceId")) decimal(value.path(key));
        require(Long.parseLong(value.path("workspaceId").asText()) <= Integer.MAX_VALUE && value.path("projectCode").isTextual(), "workspace context");
        return value;
    }
    private JsonNode checkedSnapshot(JsonNode text, JsonNode sha) {
        require(text.isTextual() && sha.isTextual(), "snapshot text");
        byte[] bytes = text.asText().getBytes(StandardCharsets.UTF_8);
        require(bytes.length <= MAX_REQUEST && hash(bytes).equals(sha.asText()), "snapshot digest");
        JsonNode snapshot = read(bytes);
        require(snapshot.path("protocolVersion").isInt() && snapshot.path("protocolVersion").intValue() == 1 && snapshot.path("context").isObject() && snapshot.path("visual").isObject(), "snapshot schema");
        @SuppressWarnings("unchecked") Map<String,Object> values = json.convertValue(snapshot.path("context"), Map.class);
        context(values);
        return snapshot;
    }
    private JsonNode payload(JsonNode envelope, int maxBytes) {
        require(envelope.isObject() && envelope.path("base64").isTextual() && envelope.path("sha256").isTextual(), "payload envelope");
        String encoded = envelope.path("base64").asText(); require(encoded.length() <= 4L * ((maxBytes + 2L) / 3), "payload limit");
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            require(bytes.length <= maxBytes && hash(bytes).equals(envelope.path("sha256").asText()), "payload digest");
            return read(bytes);
        } catch (IllegalArgumentException badEncoding) { throw protocol(); }
    }
    private void validateMappings(JsonNode mappings, JsonNode flow, JsonNode visual, JsonNode run) {
        require(mappings.isArray() && mappings.size() >= 2 && mappings.size() <= 32 && flow.path("streams").isArray(), "node mappings");
        long base = decimal(run.path("metadataIdBase"));
        Set<String> visualIds = new HashSet<>(), moduleIds = new HashSet<>(); Map<String,JsonNode> byModule = new HashMap<>();
        for (JsonNode mapping : mappings) {
            require(mapping.path("visualNodeId").isTextual() && visual.path("nodes").has(mapping.path("visualNodeId").asText()), "mapped visual node");
            require(visualIds.add(mapping.path("visualNodeId").asText()), "duplicate mapped node");
            for (String key : List.of("nodeId", "moduleId", "stepId", "itemId", "streamId")) {
                long id = decimal(mapping.path(key)); require(id >= base && id < base + 8192, "mapping allocation");
            }
            require(byModule.put(mapping.path("moduleId").asText(), mapping) == null, "duplicate module mapping");
        }
        require(visualIds.size() == visual.path("nodes").size(), "missing visual mapping");
        for (JsonNode stream : flow.path("streams")) {
            require(stream.path("modules").isArray(), "stream modules");
            for (JsonNode module : stream.path("modules")) {
                JsonNode info = module.path("module_info"), mapping = byModule.get(info.path("id").asText());
                require(mapping != null && moduleIds.add(info.path("id").asText()), "native module mapping");
                require(identity(info.path("flow_id"), run.path("nativeFlowId").asText()) && identity(info.path("module_instance_id"), run.path("nativeInstanceId").asText()), "module binding");
                require(identity(info.path("visual_node_id"), mapping.path("nodeId").asText()) && identity(info.path("item_id"), mapping.path("itemId").asText()), "module node binding");
                require(identity(stream.path("id"), mapping.path("streamId").asText()), "stream binding");
                require(module.path("adapter").path("adapter_name").equals(mapping.path("adapterName")), "plugin mapping");
            }
        }
        require(moduleIds.equals(byModule.keySet()), "incomplete module mapping");
    }

    private JsonNode invoke(JsonNode request) {
        if (!properties.isEnabled()) throw new HdiTaskFault(503, "原任务编译功能尚未启用");
        List<String> command = properties.getCompilerCommand();
        if (command.isEmpty() || command.stream().anyMatch(value -> value == null || value.isBlank() || value.indexOf('\0') >= 0))
            throw new HdiTaskFault(503, "原任务编译器尚未配置");
        byte[] input;
        try { input = json.writeValueAsBytes(request); } catch (IOException failure) { throw protocol(); }
        if (input.length > MAX_REQUEST) throw new HdiTaskFault(413, "流程编译请求超过大小限制");
        if (!slots.tryAcquire()) throw new HdiTaskFault(503, "原任务编译器繁忙，请稍后重试");
        Process process = null; ExecutorService io = Executors.newFixedThreadPool(3, runnable -> {
            Thread thread = new Thread(runnable, "hdi-compiler-io"); thread.setDaemon(true); return thread;
        });
        Map<ProcessHandle,Instant> children = new HashMap<>();
        try {
            Path log = diagnosticFile();
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(log.getParent().toFile());
            for (String key : List.of("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "CLASSPATH", "DYLD_INSERT_LIBRARIES")) builder.environment().remove(key);
            process = builder.start(); final Process owned = process;
            Future<byte[]> stdout = io.submit(() -> readLimited(owned.getInputStream(), maxStdout));
            Future<?> stderr = io.submit(() -> { drainDiagnostic(owned.getErrorStream(), log); return null; });
            Future<?> stdin = io.submit(() -> { try (OutputStream stream = owned.getOutputStream()) { stream.write(input); } return null; });
            long deadline = System.nanoTime() + timeout.toNanos();
            while (true) {
                owned.descendants().forEach(child -> child.info().startInstant().ifPresent(start -> children.putIfAbsent(child, start)));
                if (stdout.isDone()) completed(stdout);
                if (stderr.isDone()) completed(stderr);
                if (owned.waitFor(25, TimeUnit.MILLISECONDS)) break;
                if (System.nanoTime() >= deadline) throw new HdiTaskFault(504, "原任务编译超时，请检查本机编译环境");
            }
            long remaining = Math.max(1, deadline - System.nanoTime());
            byte[] output = stdout.get(remaining, TimeUnit.NANOSECONDS);
            stderr.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            stdin.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            JsonNode response = read(output);
            require(response.path("protocolVersion").isInt() && response.path("protocolVersion").intValue() == 1 && response.path("ok").isBoolean(), "response protocol");
            if (!response.path("ok").booleanValue()) {
                if (process.exitValue() == 0) throw protocol();
                String code = response.path("code").asText();
                if (code.equals("UNSUPPORTED_GRAPH")) throw new HdiTaskFault(422, "当前原引擎接入范围不支持此流程节点、连接或参数配置");
                if (code.equals("INVALID_INPUT")) throw new HdiTaskFault(422, "流程配置不完整或参数超出当前执行范围，请检查节点配置");
                if (code.equals("COMPILER_UNAVAILABLE")) throw new HdiTaskFault(503, "原任务编译器不可用，请检查本机运行环境");
                throw new HdiTaskFault(502, "原任务配置生成失败，请查看私有诊断日志");
            }
            require(process.exitValue() == 0 && response.path("diagnostics").isArray(), "response exit status");
            return response;
        } catch (HdiTaskFault fault) { throw fault; }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new HdiTaskFault(503, "原任务编译已中断"); }
        catch (java.util.concurrent.TimeoutException timeout) { throw new HdiTaskFault(504, "原任务编译超时，请检查本机编译环境"); }
        catch (IOException failure) { throw new HdiTaskFault(503, "原任务编译器启动或通信失败"); }
        catch (ExecutionException failure) { throw protocol(); }
        finally {
            if (process != null) terminateOwned(process, children);
            io.shutdownNow(); slots.release();
        }
    }

    private static void completed(Future<?> result) throws ExecutionException, InterruptedException { result.get(); }
    private synchronized Path diagnosticFile() throws IOException {
        String configured = properties.getStateDir();
        if (configured == null || configured.isBlank()) throw new HdiTaskFault(503, "原任务私有运行目录尚未配置");
        Path root = Path.of(configured); require(root.isAbsolute() && root.equals(root.normalize()), "state path");
        for (Path path = root; path != null; path = path.getParent()) if (Files.isSymbolicLink(path)) throw new IOException("symbolic private state");
        if (!Files.exists(root)) Files.createDirectories(root, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        privateDirectory(root);
        Path logs = root.resolve("compiler-diagnostics");
        if (!Files.exists(logs, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(logs, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        privateDirectory(logs);
        try (var files = Files.list(logs)) {
            List<Path> owned = files.filter(path -> path.getFileName().toString().matches("compiler-[a-f0-9-]{36}\\.stderr") && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                .sorted(Comparator.comparingLong(path -> { try { return Files.getLastModifiedTime(path).toMillis(); } catch (IOException e) { return Long.MAX_VALUE; } })).toList();
            for (int i = 0; i <= owned.size() - MAX_LOG_FILES; i++) Files.delete(owned.get(i));
        }
        Path log = logs.resolve("compiler-" + UUID.randomUUID() + ".stderr");
        Files.createFile(log, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))); return log;
    }
    private static void privateDirectory(Path path) throws IOException {
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) throw new IOException("invalid private state directory");
        var attributes = Files.readAttributes(path, java.nio.file.attribute.PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.owner().getName().equals(System.getProperty("user.name")) || !attributes.permissions().equals(PosixFilePermissions.fromString("rwx------"))) throw new IOException("private state ownership or permissions");
    }
    private static byte[] readLimited(InputStream source, int limit) throws IOException {
        try (InputStream input = source; ByteArrayOutputStream result = new ByteArrayOutputStream()) {
            byte[] block = new byte[8192]; int n;
            while ((n = input.read(block)) != -1) { if ((long)result.size() + n > limit) throw new IOException("compiler output limit"); result.write(block, 0, n); }
            return result.toByteArray();
        }
    }
    private static void drainDiagnostic(InputStream source, Path destination) throws IOException {
        try (InputStream input = source; OutputStream output = Files.newOutputStream(destination, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            byte[] block = new byte[8192]; int n, remaining = MAX_STDERR;
            while ((n = input.read(block)) != -1) { int written = Math.min(n, remaining); if (written > 0) output.write(block, 0, written); remaining -= written; }
        }
    }
    private static void terminateOwned(Process process, Map<ProcessHandle,Instant> children) {
        process.descendants().forEach(child -> child.info().startInstant().ifPresent(start -> children.putIfAbsent(child, start)));
        for (var child : children.entrySet()) if (sameChild(child)) child.getKey().destroy();
        if (process.isAlive()) process.destroy();
        try { process.waitFor(250, TimeUnit.MILLISECONDS); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        for (var child : children.entrySet()) if (sameChild(child)) child.getKey().destroyForcibly();
        if (process.isAlive()) process.destroyForcibly();
        try { process.waitFor(250, TimeUnit.MILLISECONDS); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        try { process.getInputStream().close(); process.getErrorStream().close(); process.getOutputStream().close(); } catch (IOException ignored) { }
    }
    private static boolean sameChild(Map.Entry<ProcessHandle,Instant> child) { return child.getKey().isAlive() && child.getKey().info().startInstant().filter(child.getValue()::equals).isPresent(); }
    private JsonNode read(byte[] bytes) {
        try { JsonNode value = json.readTree(bytes); require(value != null && value.isObject(), "JSON object"); return value; }
        catch (IOException invalid) { throw protocol(); }
    }
    private static long decimal(JsonNode value) {
        require(value.isTextual() && value.asText().matches("[1-9][0-9]{0,15}"), "decimal ID");
        long id = Long.parseLong(value.asText()); require(id <= 9007199254740991L, "ID range"); return id;
    }
    private static boolean identity(JsonNode value, String expected) { return value.isIntegralNumber() && value.canConvertToLong() && value.asText().equals(expected); }
    private static String hash(byte[] value) { return HdiTaskStore.sha256(value); }
    private static void require(boolean condition, String ignoredDetail) { if (!condition) throw protocol(); }
    private static HdiTaskFault protocol() { return new HdiTaskFault(502, "原任务编译结果校验失败，请查看私有诊断日志"); }
}
