package com.hm.manage.service.governance.hdi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hm.manage.service.governance.DataGovernanceArtifactStore;
import com.hm.manage.service.governance.DataGovernanceModels;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

/** Platform-owned run orchestration. Native observations are authoritative; unknown submissions are never replayed. */
@Service
public class HdiCoreRuns {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(HdiCoreRuns.class);
    private static final Set<String> TERMINAL = Set.of("SUCCEEDED", "FAILED", "CANCELLED");
    private static final Set<String> NATIVE_TERMINAL = Set.of("SUCCEEDED", "FAILED", "CANCELLED", "INTERRUPTED", "TIMED_OUT");
    private final HdiCoreProperties properties;
    private final HdiTaskStore store;
    private final HdiCompilerClient compiler;
    private final HdiWorkerClient worker;
    private final DataGovernanceArtifactStore artifactStore;
    private HdiCoreConnections connections;
    @org.springframework.beans.factory.annotation.Autowired
    public void setConnections(HdiCoreConnections value) { this.connections = value; }
    private final ConcurrentHashMap<String,Active> active = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor starters = new ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS, new SynchronousQueue<>(),
        runnable -> { Thread thread = new Thread(runnable, "hdi-run-start"); thread.setDaemon(true); return thread; });
    private final ScheduledExecutorService poller = Executors.newSingleThreadScheduledExecutor(runnable -> { Thread thread = new Thread(runnable, "hdi-run-observation"); thread.setDaemon(true); return thread; });
    private volatile boolean closing;
    private static final class Active {
        final HdiTaskStore.Run run; volatile boolean cancelled, reserved, submitting, submitted;
        Active(HdiTaskStore.Run run) { this.run = run; }
    }
    public record ArtifactChunk(String artifactId, String filename, String contentType, long offset, long nextOffset,
                                long totalBytes, String sha256, boolean eof, byte[] bytes) { }

    public HdiCoreRuns(HdiCoreProperties properties, HdiTaskStore store, HdiCompilerClient compiler,
                       HdiWorkerClient worker, DataGovernanceArtifactStore artifactStore) {
        this.properties = properties; this.store = store; this.compiler = compiler; this.worker = worker; this.artifactStore = artifactStore;
    }
    @PostConstruct void initialize() {
        if (!properties.isEnabled()) return;
        // A database row is not permission to replay a task after restart.
        for (HdiTaskStore.Run run : store.unfinished()) store.transition(run, "UNKNOWN", run.workerSnapshotJson(), "平台已重启，原运行需要核查；未自动重新提交");
        poller.scheduleWithFixedDelay(this::poll, 1, 1, TimeUnit.SECONDS);
    }
    public JsonNode resources() {
        try {
            JsonNode hello = worker.hello();
            ObjectNode binding = HdiProtocol.JSON.createObjectNode().put("id", hello.path("binding").path("id").asText())
                .put("name", hello.path("binding").path("name").asText()).put("type", "original-detl").put("state", "CONFIGURED")
                .put("nativeInstanceAllocatedPerRun", true);
            ObjectNode resource = HdiProtocol.JSON.createObjectNode().put("available", true).put("workerState", "READY").put("metricsAvailable", false);
            resource.set("bindings", HdiProtocol.JSON.createArrayNode().add(binding));
            if (hello.path("outputPolicy").isObject()) resource.set("outputPolicy", hello.path("outputPolicy"));
            if (hello.path("capacity").isObject()) resource.set("capacity", hello.path("capacity"));
            return resource;
        } catch (HdiWorkerClient.Failure failure) {
            return HdiProtocol.JSON.createObjectNode().put("available", false).put("workerState", failure.uncertain() ? "DISCONNECTED" : "UNAVAILABLE")
                .put("metricsAvailable", false).set("bindings", HdiProtocol.JSON.createArrayNode());
        }
    }
    public HdiTaskStore.Run startDebug(long owner, long task, long revision) { return start(owner, task, revision, "DEBUG", null); }
    public HdiTaskStore.Run startRun(long owner, long task, long revision) { return start(owner, task, revision, "RUN", null); }
    public HdiTaskStore.Run startScheduled(long owner, long task, long revision, String triggerKey) {
        if (triggerKey == null || !triggerKey.matches("[A-Za-z0-9_.:-]{1,128}")) throw new HdiTaskFault(400, "定时触发标识无效");
        return start(owner, task, revision, "SCHEDULE", triggerKey);
    }
    private HdiTaskStore.Run start(long owner, long task, long revision, String mode, String trigger) {
        enabled(); store.get(owner, task); store.revision(owner, task, revision);
        HdiTaskStore.Run run = store.reserveRun(owner, task, revision, mode, trigger);
        Active current = new Active(run); active.put(run.id(), current);
        try { starters.execute(() -> start(current)); }
        catch (java.util.concurrent.RejectedExecutionException busy) {
            active.remove(run.id()); store.transition(run, "FAILED", null, "本机运行准备容量已满，任务尚未提交");
        }
        return store.run(owner, run.id());
    }
    /** Only the persistent scheduler may pass a run already reserved by its atomic claim transaction. */
    void dispatchReservedSchedule(HdiTaskStore.Run reserved) {
        enabled();
        HdiTaskStore.Run run = store.run(reserved.ownerId(), reserved.id());
        if (!"SCHEDULE".equals(run.mode()) || !"QUEUED".equals(run.state()) || !run.id().equals(reserved.id())
            || run.taskId() != reserved.taskId() || run.revision() != reserved.revision() || run.nativeFlowId() != reserved.nativeFlowId()) throw new HdiTaskFault(409, "定时运行预留状态已经变化，未重新提交");
        Active current = new Active(run);
        if (active.putIfAbsent(run.id(), current) != null) return;
        try { starters.execute(() -> start(current)); }
        catch (java.util.concurrent.RejectedExecutionException busy) {
            active.remove(run.id()); store.transition(run, "FAILED", null, "本机运行准备容量已满，定时任务尚未提交");
        }
    }
    private void start(Active current) {
        HdiTaskStore.Run run = current.run;
        try {
            if (current.cancelled || closing) { finishUnsubmitted(current); return; }
            store.transition(run, "PREPARING", null, null);
            HdiTaskStore.Revision frozen = store.revision(run.ownerId(), run.taskId(), run.revision());
            HdiCoreConnections.Resolved resolved = null;
            if (connections != null) {
                JsonNode snapshot;
                try { snapshot = HdiProtocol.JSON.readTree(frozen.snapshotJson()); }
                catch (java.io.IOException invalid) { throw new HdiTaskFault(422, "任务冻结版本无法读取"); }
                if (snapshot == null || !snapshot.isObject()) throw new HdiTaskFault(422, "任务冻结版本无法读取");
                JsonNode refs = snapshot.has("connectionRefs") ? snapshot.path("connectionRefs") : HdiProtocol.JSON.createObjectNode();
                resolved = connections.resolveForRun(run.ownerId(), snapshot.path("visual"), refs);
            }
            JsonNode reserved = resolved == null || resolved.connectionBindings().isEmpty() ? worker.reserve(run)
                : worker.reserve(run, resolved.connectionBindings(), resolved.redactionValues()); current.reserved = true;
            if (!reserved.path("outputsDir").isTextual() || !reserved.path("authorizedOutputRoot").isTextual()) throw new HdiTaskFault(502, "原执行器没有返回受控输出目录");
            store.transition(run, "COMPILING", serialized(reserved), null);
            if (current.cancelled || closing) { cancelReserved(current); return; }
            Map<String,Object> runContext = new java.util.LinkedHashMap<>(Map.of(
                "businessTaskId", Long.toString(run.taskId()), "revision", Long.toString(run.revision()),
                "nativeFlowId", Long.toString(run.nativeFlowId()), "nativeInstanceId", Long.toString(run.nativeInstanceId()),
                "metadataIdBase", Long.toString(run.metadataIdBase()), "execId", run.execId(),
                "outputsDir", reserved.path("outputsDir").asText(), "authorizedOutputRoot", reserved.path("authorizedOutputRoot").asText(),
                "outputRootSource", "worker-reserve",
                "mode", run.mode().equals("DEBUG") ? "test" : "run"));
            if (resolved != null && !resolved.connectionBindings().isEmpty()) {
                runContext.put("connections", resolved.connections()); runContext.put("connectionBindings", resolved.connectionBindings());
            }
            JsonNode compiled = compiler.compile(frozen.snapshotJson(), frozen.snapshotSha256(), runContext);
            if (current.cancelled || closing) { cancelReserved(current); return; }
            // Persist submission intent before the transport call. A missing reply becomes UNKNOWN, never a retry.
            store.transition(run, "STARTING", serialized(reserved), null); current.submitting = true;
            JsonNode observed = worker.submit(run, compiled); current.submitted = true;
            observe(run, observed);
            if (current.cancelled || closing) observe(run, worker.cancel(run));
        } catch (HdiWorkerClient.Failure failure) {
            LOG.error("HDI run {} worker failure: {} (submission intent={})", run.id(), failure.code(), current.submitting);
            if (!failure.uncertain() && current.submitting && confirmUnsubmittedReservation(current)) {
                current.submitting = false;
                failBeforeSubmit(current, "LOCAL_OUTPUT_REQUIRED".equals(failure.code())
                    ? "文件输出的传输协议或上传设置与节点表单冲突，请核对高级参数；任务尚未开始"
                    : "执行器拒绝了该配置，任务尚未开始；请核查节点设置或联系管理员");
            } else if (failure.uncertain() || current.submitting) unknown(current, "原执行通道中断或提交结果未知；未重新提交");
            else failBeforeSubmit(current, "原执行环境当前不可用，任务尚未开始");
        } catch (HdiTaskFault failure) {
            LOG.warn("HDI run {} preparation rejected with status {} (submission intent={})", run.id(), failure.status(), current.submitting);
            failBeforeSubmit(current, failure.getMessage());
        }
        catch (RuntimeException failure) {
            // Keep useful diagnostics without logging a source definition, data value or exception message.
            LOG.error("HDI run {} preparation failure: {} at {}", run.id(), failure.getClass().getName(),
                java.util.Arrays.stream(failure.getStackTrace()).limit(12).map(StackTraceElement::toString).collect(java.util.stream.Collectors.joining(" <- ")));
            if (current.submitting) unknown(current, "平台运行协调异常，请核查原执行状态");
            else failBeforeSubmit(current, "平台配置准备异常，任务尚未提交；请联系管理员核查运行编号");
        }
    }
    private boolean confirmUnsubmittedReservation(Active current) {
        try {
            // Worker persists PREPARING before constructing/starting a Core thread. An owned RESERVED
            // observation after a definitive rejection proves no execution was admitted; transport failure does not.
            JsonNode observed = worker.status(current.run);
            return "RESERVED".equals(observed.path("state").asText()) && !observed.path("finalized").asBoolean();
        } catch (RuntimeException unavailable) { return false; }
    }
    private void failBeforeSubmit(Active current, String message) {
        if (current.submitting) { unknown(current, "原运行结果待核查，未重新提交"); return; }
        if (current.reserved) {
            try {
                JsonNode result = worker.cancel(current.run);
                if (!result.path("finalized").asBoolean(false)) { unknown(current, "准备失败，原执行器清理尚未确认"); return; }
                store.transition(current.run, current.cancelled ? "CANCELLED" : "FAILED", serialized(result), message);
            } catch (RuntimeException failure) { unknown(current, "准备失败，原执行器清理结果待核查"); return; }
        } else store.transition(current.run, current.cancelled ? "CANCELLED" : "FAILED", null, message);
        active.remove(current.run.id());
    }
    private void finishUnsubmitted(Active current) {
        store.transition(current.run, "CANCELLED", null, "运行在提交原引擎前已取消"); active.remove(current.run.id());
    }
    private void cancelReserved(Active current) {
        observe(current.run, worker.cancel(current.run));
    }
    private void unknown(Active current, String message) {
        HdiTaskStore.Run latest = store.run(current.run.ownerId(), current.run.id());
        store.transition(current.run, "UNKNOWN", latest.workerSnapshotJson(), message); active.remove(current.run.id());
    }
    public HdiTaskStore.Run stop(long owner, String runId) {
        HdiTaskStore.Run run = store.run(owner, runId);
        if (TERMINAL.contains(run.state())) return run;
        Active current = active.get(runId);
        if (current != null) {
            current.cancelled = true;
            if (!current.reserved) return run; // The start thread owns the pre-reservation cancellation handshake.
        }
        try { observe(run, worker.cancel(run)); }
        catch (HdiWorkerClient.Failure failure) { store.transition(run, "UNKNOWN", run.workerSnapshotJson(), "停止请求结果待核查，未报告停止成功"); }
        return store.run(owner, runId);
    }
    public JsonNode status(long owner, String runId) {
        HdiTaskStore.Run run = store.run(owner, runId);
        Active current = active.get(runId);
        if (!TERMINAL.contains(run.state()) && (current == null || current.submitted || current.reserved && current.cancelled)) {
            try { observe(run, worker.status(run)); }
            catch (HdiWorkerClient.Failure failure) { store.transition(run, "UNKNOWN", run.workerSnapshotJson(), "原执行状态暂不可确认，未重新提交"); }
            run = store.run(owner, runId);
        }
        JsonNode evidence = saved(run);
        ObjectNode view = HdiProtocol.JSON.createObjectNode().put("runId", run.id()).put("taskId", Long.toString(run.taskId()))
            .put("revision", Long.toString(run.revision())).put("state", run.state()).put("engine", "original-detl")
            .put("finalized", evidence.path("finalized").asBoolean(false)).put("engineState", evidence.path("state").asText("UNKNOWN"))
            .put("observed", !evidence.isEmpty()).put("metricsAvailable", false).put("updatedAt", run.updatedAt().toString());
        view.put("nativeState", evidence.path("nativeState").asText(null));
        view.put("artifactCompleteness", run.state().equals("SUCCEEDED") ? "COMPLETE" : confirmedPartialStop(evidence) ? "PARTIAL" : "NONE");
        view.set("outputWarningCodes", HdiProtocol.JSON.valueToTree(outputWarnings(evidence)));
        view.put("error", run.errorMessage()); view.putNull("inputRows"); view.putNull("outputRows");
        return view;
    }
    public JsonNode moduleMetrics(long owner, String runId) {
        store.run(owner, runId);
        return HdiProtocol.JSON.createObjectNode().put("available", false).put("reason", "NOT_COLLECTED")
            .put("message", "原模块指标尚未采集，不能将空值视为零行").set("modules", HdiProtocol.JSON.createArrayNode());
    }
    public JsonNode logs(long owner, String runId, long cursor, int limit) {
        HdiTaskStore.Run run = store.run(owner, runId);
        if (cursor < 0 || limit < 4 || limit > 65536) throw new HdiTaskFault(400, "日志游标或读取大小无效");
        try { return worker.logs(run, cursor, limit); }
        catch (HdiWorkerClient.Failure failure) { throw new HdiTaskFault(503, "原运行日志暂不可读取"); }
    }
    private void poll() {
        if (closing) return;
        for (Active current : active.values()) {
            if (!current.submitted && !(current.reserved && current.cancelled)) continue;
            try { observe(current.run, worker.status(current.run)); }
            catch (HdiWorkerClient.Failure failure) { try { unknown(current, "原执行通道中断，运行需要核查"); } catch (RuntimeException ignored) { } }
            catch (RuntimeException failure) { /* A persistence failure is retried as observation, never as execution. */ }
        }
    }
    private synchronized void observe(HdiTaskStore.Run run, JsonNode observation) {
        HdiTaskStore.Run latest = store.run(run.ownerId(), run.id());
        if (TERMINAL.contains(latest.state())) { active.remove(run.id()); return; }
        String nativeState = observation.path("state").asText(); boolean finalized = observation.path("finalized").asBoolean(false);
        // A reservation proves no execution. It cannot resolve a prior unknown outcome or erase its diagnostic.
        if (latest.state().equals("UNKNOWN") && nativeState.equals("RESERVED") && !finalized) return;
        String state;
        if (NATIVE_TERMINAL.contains(nativeState)) {
            if (!finalized) state = "STOPPING";
            else state = switch (nativeState) { case "SUCCEEDED" -> "SUCCEEDED"; case "CANCELLED" -> "CANCELLED"; default -> "FAILED"; };
        } else state = switch (nativeState) {
            case "RESERVED", "PREPARING" -> "PREPARING";
            case "STARTING", "READY", "SUBMITTING" -> "STARTING";
            case "RUNNING" -> "RUNNING";
            case "STOPPING" -> "STOPPING";
            default -> "UNKNOWN";
        };
        ObjectNode persisted = observation.deepCopy(); String error = null;
        boolean partial = state.equals("CANCELLED") && confirmedPartialStop(observation);
        if (state.equals("SUCCEEDED") || partial) {
            persisted.put("artifactCompleteness", partial ? "PARTIAL" : "COMPLETE");
            try { captureArtifacts(run, observation, partial); persisted.put("platformArtifactsAvailable", true); }
            catch (RuntimeException failure) {
                persisted.put("platformArtifactsAvailable", false);
                error = partial ? "原任务已停止，但停止时生成的部分结果尚未通过平台归档校验" : "原引擎运行成功，但完整产物尚未通过平台收编校验";
            }
            String warning = outputWarningMessage(observation);
            if (warning != null) error = error == null ? warning : warning + "；" + error;
        } else if (state.equals("FAILED")) error = "原引擎执行失败或超时，详情请查看该运行日志";
        else if (state.equals("UNKNOWN")) error = "原执行状态待核查，未重新提交";
        store.transition(run, state, serialized(persisted), error);
        if (TERMINAL.contains(state) || state.equals("UNKNOWN")) active.remove(run.id());
    }

    private static boolean confirmedPartialStop(JsonNode evidence) {
        return "CANCELLED".equals(evidence.path("state").asText()) && "stop".equals(evidence.path("nativeState").asText())
            && evidence.path("stopRequestedByUser").isBoolean() && evidence.path("stopRequestedByUser").booleanValue()
            && evidence.path("finalized").isBoolean() && evidence.path("finalized").booleanValue()
            && evidence.path("recoveryRequired").isBoolean() && !evidence.path("recoveryRequired").booleanValue()
            && evidence.path("errorCode").asText("").isEmpty() && "PARTIAL".equals(evidence.path("artifactCompleteness").asText());
    }
    private static boolean publishedArtifacts(HdiTaskStore.Run run, JsonNode evidence) {
        return evidence.path("platformArtifactsAvailable").asBoolean(false)
            && (run.state().equals("SUCCEEDED") || run.state().equals("CANCELLED") && confirmedPartialStop(evidence));
    }
    private void captureArtifacts(HdiTaskStore.Run run, JsonNode observation, boolean partial) {
        JsonNode artifacts = observation.path("artifacts");
        if (!artifacts.isArray() || artifacts.size() > DataGovernanceArtifactStore.MAX_ARTIFACTS) throw new HdiTaskFault(502, "原运行未提供完整产物清单");
        if (artifacts.isEmpty()) return; // Database/message outputs need not create a downloadable local file.
        DataGovernanceArtifactStore.Manifest existing = null;
        try { existing = artifactStore.manifest(run.id(), run.ownerId()); } catch (RuntimeException absent) { /* Begin immutable capture only when no published manifest exists. */ }
        if (existing != null) {
            if (existing.partial() != partial) throw new HdiTaskFault(502, "产物完整性类型不一致");
            return;
        }
        DataGovernanceModels.StoredRun stored = new DataGovernanceModels.StoredRun(); stored.ownerId = run.ownerId(); stored.run = new DataGovernanceModels.TestRun();
        stored.run.id = run.id(); stored.run.definitionHash = run.definitionSha256(); stored.run.status = partial ? "CANCELLED" : "SUCCEEDED"; stored.run.cleanupConfirmed = true;
        var stage = artifactStore.begin(stored); Set<String> ids = new HashSet<>(); long total = 0;
        try {
            for (JsonNode artifact : artifacts) {
                String id = artifact.path("artifactId").asText(), name = artifact.path("name").asText(), sha = artifact.path("sha256").asText();
                long bytes = artifact.path("bytes").asLong(-1);
                if (!id.matches("[A-Za-z0-9_-]{1,128}") || !ids.add(id) || !sha.matches("[0-9a-f]{64}") || bytes < 0 || bytes > DataGovernanceArtifactStore.MAX_ARTIFACT_BYTES
                    || bytes > DataGovernanceArtifactStore.MAX_TOTAL_BYTES-total || name.isBlank() || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) throw new HdiTaskFault(502, "原产物清单无效");
                total += bytes;
                var captured = artifactStore.capture(stage, name, contentType(name), bytes, output -> {
                    long offset = 0;
                    while (offset < bytes) {
                        int limit = (int)Math.min(65536, bytes-offset); JsonNode chunk = worker.artifact(run, id, offset, limit);
                        byte[] data = decodedChunk(chunk, id, sha, bytes, offset, limit);
                        output.write(data); offset += data.length;
                    }
                });
                if (!captured.sha256().equals(sha)) throw new HdiTaskFault(502, "完整产物摘要不一致");
            }
            if (partial) artifactStore.publishStoppedPartial(stage, stored); else artifactStore.publish(stage, stored);
        } catch (RuntimeException failure) { artifactStore.abandon(stage); throw failure; }
    }
    private static byte[] decodedChunk(JsonNode chunk, String id, String sha, long bytes, long offset, int maximum) {
        try {
            byte[] value = Base64.getDecoder().decode(chunk.path("base64").asText());
            if (!id.equals(chunk.path("artifactId").asText()) || !sha.equals(chunk.path("sha256").asText()) || chunk.path("bytes").asLong(-1) != bytes
                || chunk.path("offset").asLong(-1) != offset || value.length < 1 || value.length > maximum
                || chunk.path("nextOffset").asLong(-1) != offset+value.length || chunk.path("eof").asBoolean() != (offset+value.length == bytes)) throw new IllegalArgumentException();
            return value;
        } catch (IllegalArgumentException failure) { throw new HdiTaskFault(502, "原产物分块校验失败"); }
    }
    public JsonNode artifacts(long owner, String runId) {
        HdiTaskStore.Run run = store.run(owner, runId);
        JsonNode evidence = saved(run); boolean partial = confirmedPartialStop(evidence);
        if (!publishedArtifacts(run, evidence))
            return HdiProtocol.JSON.createObjectNode().put("available", false).put("reason", "NOT_PUBLISHED").set("artifacts", HdiProtocol.JSON.createArrayNode());
        if (saved(run).path("artifacts").isArray() && saved(run).path("artifacts").isEmpty())
            return HdiProtocol.JSON.createObjectNode().put("available", true).put("artifactCompleteness", partial ? "PARTIAL" : "COMPLETE").put("localArtifactsGenerated", false).set("artifacts", HdiProtocol.JSON.createArrayNode());
        try {
            var manifest = artifactStore.manifest(runId, owner); var items = HdiProtocol.JSON.createArrayNode();
            if (manifest.partial() != partial) throw new HdiTaskFault(502, "产物完整性类型不一致");
            for (var item : manifest.artifacts()) items.add(HdiProtocol.JSON.createObjectNode().put("artifactId", item.id()).put("name", item.filename())
                .put("bytes", item.byteSize()).put("sha256", item.sha256()).put("contentType", item.contentType()));
            return HdiProtocol.JSON.createObjectNode().put("available", true).put("artifactCompleteness", partial ? "PARTIAL" : "COMPLETE").set("artifacts", items);
        } catch (RuntimeException failure) { throw new HdiTaskFault(503, "平台完整产物暂不可读取"); }
    }
    public ArtifactChunk artifact(long owner, String runId, String artifactId, long offset, int limit) {
        HdiTaskStore.Run run = store.run(owner, runId);
        JsonNode evidence = saved(run);
        if (!publishedArtifacts(run, evidence)) throw new HdiTaskFault(409, "产物尚未通过归档确认");
        if (offset < 0 || limit < 1 || limit > 1024 * 1024) throw new HdiTaskFault(400, "产物读取范围无效");
        try {
            var manifest = artifactStore.manifest(runId, owner);
            if (manifest.partial() != confirmedPartialStop(evidence)) throw new HdiTaskFault(502, "产物完整性类型不一致");
            var item = manifest.artifacts().stream().filter(value -> value.id().equals(artifactId)).findFirst()
                .orElseThrow(() -> new HdiTaskFault(404, "产物不存在或无权访问"));
            if (offset > item.byteSize()) throw new HdiTaskFault(416, "产物读取范围超出文件");
            byte[] all;
            try (InputStream input = artifactStore.read(runId, artifactId, owner)) { all = input.readAllBytes(); }
            int end = (int)Math.min(item.byteSize(), offset+limit); byte[] selected = java.util.Arrays.copyOfRange(all, (int)offset, end);
            return new ArtifactChunk(artifactId, item.filename(), item.contentType(), offset, end, item.byteSize(), item.sha256(), end == item.byteSize(), selected);
        } catch (HdiTaskFault failure) { throw failure; }
        catch (Exception failure) { throw new HdiTaskFault(503, "完整产物读取或校验失败"); }
    }
    private static String contentType(String name) {
        if (name.toLowerCase().endsWith(".csv")) return "text/csv"; // Encoding is configured by the original node; do not mislabel GBK output as UTF-8.
        return "application/octet-stream";
    }
    private static String serialized(JsonNode value) { return value == null ? null : value.toString(); }
    private static List<String> outputWarnings(JsonNode evidence) {
        JsonNode codes = evidence.path("outputWarningCodes"); if (!codes.isArray()) return List.of();
        var result = new java.util.LinkedHashSet<String>();
        for (JsonNode code : codes) if (code.isTextual() && Set.of("JDBC_OUTPUT_ERROR_OBSERVED", "JDBC_OUTPUT_ROWS_SKIPPED").contains(code.asText())) result.add(code.asText());
        return List.copyOf(result);
    }
    private static String outputWarningMessage(JsonNode evidence) {
        List<String> codes = outputWarnings(evidence);
        if (codes.contains("JDBC_OUTPUT_ERROR_OBSERVED")) return "原引擎已结束，但观察到数据库输出错误；请核对目标表和运行日志";
        return codes.isEmpty() ? null : "原引擎记录了跳过数据；请结合目标端和运行日志核对结果";
    }
    private static JsonNode saved(HdiTaskStore.Run run) {
        if (run.workerSnapshotJson() == null || run.workerSnapshotJson().isBlank()) return HdiProtocol.JSON.createObjectNode();
        try { return HdiProtocol.JSON.readTree(run.workerSnapshotJson()); }
        catch (Exception error) { throw new HdiTaskFault(502, "持久化运行观测无效"); }
    }
    private void enabled() { if (!properties.isEnabled() || closing) throw new HdiTaskFault(503, "原数据流程运行服务尚未就绪"); }
    @PreDestroy public void close() {
        closing = true; poller.shutdownNow();
        for (Active current : active.values()) {
            current.cancelled = true;
            try { if (current.reserved) observe(current.run, worker.cancel(current.run)); }
            catch (RuntimeException ignored) { }
        }
        starters.shutdown();
        try { starters.awaitTermination(2, TimeUnit.SECONDS); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
        for (Active current : active.values()) {
            try { unknown(current, "平台正在停止，原运行最终状态需要核查"); } catch (RuntimeException ignored) { }
        }
        worker.close();
    }
}
