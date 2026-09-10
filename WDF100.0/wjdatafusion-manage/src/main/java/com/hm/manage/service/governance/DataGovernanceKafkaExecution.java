package com.hm.manage.service.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hm.common.exception.ServiceException;
import com.hm.manage.service.governance.DataGovernanceModels.*;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Owner-scoped receipt execution. Polling HTTP requests never initiate processing or delivery. */
@Service
public class DataGovernanceKafkaExecution
{
    public record Request(String releaseId, String deliveryConnectionId) { }
    public record View(String receiptId, String runId, String releaseId, String releaseName, String releaseHash, String executionHash,
                       String inputSha256, String status, String runStatus, String deliveryId, String deliveryStatus, String error, String createdAt, String updatedAt) { }
    public static class Intent
    {
        public long owner;
        public String receiptId, runId, releaseId, releaseName, releaseHash, executionHash, inputSha256;
        public String status, runStatus, deliveryId, deliveryStatus, deliveryIntentAt, error, createdAt, updatedAt;
        public DataGovernanceScheduleDelivery.Binding delivery;
    }
    private final DataGovernanceProperties properties;
    private final DataGovernanceKafkaProperties kafkaProperties;
    private final DataGovernanceKafkaService kafka;
    private final DataGovernanceScheduleStore releases;
    private final DataGovernanceService service;
    private final DataGovernanceRunRepository runs;
    private final DataGovernanceArtifactStore artifacts;
    private final Supplier<DataGovernanceScheduleDelivery> delivery;
    private final ObjectMapper mapper = new ObjectMapper();
    private DataGovernanceDeliveryFiles files;
    private boolean closed, watching;
    private final ThreadPoolExecutor submissions = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(4), r -> daemon(r, "kafka-flow-submit"), new ThreadPoolExecutor.AbortPolicy());
    private final ScheduledExecutorService observer = Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "kafka-flow-observe"));
    @Autowired
    public DataGovernanceKafkaExecution(DataGovernanceProperties properties, DataGovernanceKafkaProperties kafkaProperties,
        DataGovernanceKafkaService kafka, DataGovernanceScheduleStore releases, DataGovernanceService service,
        DataGovernanceRunRepository runs, DataGovernanceArtifactStore artifacts, ObjectProvider<DataGovernanceScheduleDelivery> delivery)
    { this(properties, kafkaProperties, kafka, releases, service, runs, artifacts, delivery::getIfAvailable); }
    DataGovernanceKafkaExecution(DataGovernanceProperties properties, DataGovernanceKafkaProperties kafkaProperties,
        DataGovernanceKafkaService kafka, DataGovernanceScheduleStore releases, DataGovernanceService service,
        DataGovernanceRunRepository runs, DataGovernanceArtifactStore artifacts, Supplier<DataGovernanceScheduleDelivery> delivery)
    { this.properties = properties; this.kafkaProperties = kafkaProperties; this.kafka = kafka; this.releases = releases; this.service = service; this.runs = runs; this.artifacts = artifacts; this.delivery = delivery; }
    private static Thread daemon(Runnable work, String name) { Thread thread = new Thread(work, name); thread.setDaemon(true); return thread; }
    private void initialize()
    {
        if (closed || !kafkaProperties.isEnabled()) fail("Kafka批次执行未启用或已关闭");
        if (files != null) return;
        files = new DataGovernanceDeliveryFiles(properties, "kafka-executions");
        try
        {
            for (Intent intent : files.list("job", Intent.class))
                if (!Set.of("READY_TO_ACK", "FAILED", "RECOVERY_REQUIRED").contains(intent.status))
                    update(intent, "RECOVERY_REQUIRED", "服务曾在执行期间中断；仅核查已有运行与交付，不自动重跑");
        }
        catch (RuntimeException e) { files.close(); files = null; throw e; }
    }
    private void watch()
    { if (!watching) { watching = true; observer.scheduleWithFixedDelay(this::tick, 1, 1, TimeUnit.SECONDS); } }
    public synchronized View view(String receiptId, long owner)
    {
        initialize(); kafka.read(receiptId, owner);
        Intent intent = find(receiptId, owner); return intent == null ? null : view(intent);
    }
    public synchronized View execute(String receiptId, Request request, long owner)
    {
        initialize(); var receipt = kafka.snapshot(receiptId, owner);
        Intent existing = find(receiptId, owner); if (existing != null) return view(existing);
        if (request == null || receipt.runId != null || receipt.executionIntentAt != null || !receipt.leaseHeld || !Set.of("RECEIVED", "EMPTY").contains(receipt.status)) fail("该Kafka批次不能创建新的执行");
        if (files.list("job", Intent.class).stream().filter(i -> i.owner == owner).count() >= 500) fail("执行记录已达500条，请先归档");
        var release = releases.release(request.releaseId());
        if (release == null || release.ownerId != owner || release.snapshot == null || release.snapshot.ownerId != owner) fail("发布版本不存在或无权执行");
        String releaseHash = DataGovernanceSafeFlow.hash(release.snapshot.definition);
        if (!releaseHash.equals(release.snapshot.run.definitionHash)) fail("原发布版本定义摘要不一致");
        StoredRun snapshot = substitute(release.snapshot, receipt.inputJson);
        DataGovernanceScheduleDelivery.Binding target = null;
        if (request.deliveryConnectionId() != null && !request.deliveryConnectionId().isBlank()) target = bridge().target(request.deliveryConnectionId(), owner);
        Intent intent = new Intent(); intent.owner = owner; intent.receiptId = receiptId; intent.runId = UUID.randomUUID().toString();
        intent.releaseId = release.id; intent.releaseName = release.name; intent.releaseHash = releaseHash; intent.executionHash = snapshot.run.definitionHash;
        intent.inputSha256 = receipt.inputSha256; intent.delivery = target; intent.status = "RUN_PLANNED"; intent.createdAt = intent.updatedAt = Instant.now().toString();
        save(intent); // Intent exists before either a source reservation or an engine submission can happen.
        try
        {
            kafka.reserveExecution(receiptId, intent.runId, intent.inputSha256, target, owner);
            submissions.execute(() -> start(intent, snapshot)); watch();
        }
        catch (Exception e) { update(intent, "RECOVERY_REQUIRED", "未确认执行提交是否完成；源租约保留，请核查已有运行"); }
        return view(intent);
    }
    static StoredRun substitute(StoredRun original, String input)
    {
        var mapper = new ObjectMapper(); StoredRun result = mapper.convertValue(original, StoredRun.class);
        result.inputJson = input;
        result.definition = new DataGovernanceSafeFlow(original.definition).freeze(input, original.parameters == null ? Map.of() : original.parameters);
        result.run.definitionHash = DataGovernanceSafeFlow.hash(result.definition); result.run.definitionCapturedAt = Instant.now().toString();
        return result;
    }
    private void start(Intent intent, StoredRun snapshot)
    {
        try
        {
            service.submitFrozen(snapshot, intent.owner, intent.runId);
            synchronized (this) {
                kafka.attachRun(intent.receiptId, intent.runId, intent.delivery, intent.owner);
                update(intent, "RUNNING", null);
            }
        }
        catch (Exception e) { synchronized (this) { update(intent, "RECOVERY_REQUIRED", "运行提交或关联未能确认，请核查计划中的运行编号；不会重新取批"); } }
    }
    private synchronized void tick()
    {
        if (closed || files == null) return;
        try
        {
            for (Intent intent : files.list("job", Intent.class))
                if (Set.of("RUNNING", "DELIVERING").contains(intent.status))
                    try { advance(intent); } catch (Exception e) { update(intent, "RECOVERY_REQUIRED", e instanceof ServiceException ? e.getMessage() : "执行状态未能确认，请核查"); }
        }
        catch (RuntimeException ignored) { /* A failed journal read never permits another submission or offset acknowledgement. */ }
    }
    private StoredRun checkedRun(Intent intent)
    {
        StoredRun stored = runs.find(intent.runId);
        if (stored == null || stored.ownerId != intent.owner || stored.run == null || !intent.executionHash.equals(stored.run.definitionHash)
            || !intent.inputSha256.equals(DataGovernanceKafkaService.hash(stored.inputJson))) fail("运行归属、输入或执行摘要未确认");
        return stored;
    }
    private void advance(Intent intent)
    {
        StoredRun stored = checkedRun(intent); TestRun run = stored.run; intent.runStatus = run.status;
        if (Set.of("QUEUED", "RUNNING").contains(run.status)) { save(intent); return; }
        if (!run.cleanupConfirmed || !Set.of("SUCCEEDED", "EMPTY").contains(run.status)) { update(intent, "FAILED", "转换未成功并确认清理；未交付、未确认Kafka位点"); return; }
        if (run.status.equals("EMPTY")) {
            if (run.artifactsManifestAvailable || run.artifactCount != 0 || run.output == null || !run.output.isEmpty()) fail("空批次存在矛盾的产物状态");
            intent.deliveryStatus = "SKIPPED_EMPTY"; update(intent, "READY_TO_ACK", null); return;
        }
        if (!run.artifactsManifestAvailable || run.artifactCount < 1) fail("转换缺少完整产物清单");
        var manifest = artifacts.manifest(run.id, intent.owner);
        if (!manifest.definitionHash().equals(intent.executionHash) || manifest.artifacts().size() != run.artifactCount) fail("产物清单与执行快照不一致");
        if (intent.delivery == null) { intent.deliveryStatus = "NOT_REQUESTED"; update(intent, "READY_TO_ACK", null); return; }
        DataGovernanceScheduleDelivery.Delivery result;
        if (intent.deliveryId != null) result = bridge().status(intent.deliveryId, intent.owner);
        else {
            if (intent.deliveryIntentAt != null) fail("已计划的交付编号缺失，请核查已有交付，禁止盲目补传");
            intent.deliveryIntentAt = Instant.now().toString(); update(intent, "DELIVERING", null);
            result = bridge().submit(intent.runId, intent.delivery, intent.owner);
        }
        acceptDelivery(intent, result);
    }
    private void acceptDelivery(Intent intent, DataGovernanceScheduleDelivery.Delivery result)
    {
        if (result == null || result.id() == null || !intent.delivery.fingerprint().equals(result.targetFingerprint())) fail("交付目标或状态未确认");
        intent.deliveryId = result.id(); intent.deliveryStatus = result.status();
        if ("DELIVERED".equals(result.status())) update(intent, "READY_TO_ACK", null);
        else if (Set.of("QUEUED", "RUNNING").contains(result.status())) update(intent, "DELIVERING", null);
        else update(intent, "RECOVERY_REQUIRED", result.error() == null ? "交付未完成，请先在文件交付页核查" : result.error());
    }
    public synchronized View recover(String receiptId, long owner)
    {
        initialize(); Intent intent = owned(receiptId, owner); kafka.read(receiptId, owner);
        if (!Set.of("RECOVERY_REQUIRED", "FAILED").contains(intent.status)) return view(intent);
        try
        {
            StoredRun stored = checkedRun(intent); intent.runStatus = stored.run.status;
            kafka.attachRun(intent.receiptId, intent.runId, intent.delivery, owner);
            if (intent.deliveryIntentAt != null) {
                var found = intent.deliveryId == null ? bridge().find(intent.runId, intent.delivery.id(), owner) : bridge().status(intent.deliveryId, owner);
                if (found == null) fail("未找到已计划的交付记录；保留源租约，不能猜测或重发");
                acceptDelivery(intent, found);
            } else update(intent, "RUNNING", null); // Resume observation of an existing run; never submit another run.
            watch();
        }
        catch (Exception e) { update(intent, "RECOVERY_REQUIRED", e instanceof ServiceException ? e.getMessage() : "恢复核查未完成"); }
        return view(intent);
    }
    private DataGovernanceScheduleDelivery bridge() { var result = delivery.get(); if (result == null) fail("交付桥未配置"); return result; }
    private Intent find(String id, long owner) { DataGovernanceEngine.id(id); return files.list("job", Intent.class).stream().filter(i -> id.equals(i.receiptId) && i.owner == owner).findFirst().orElse(null); }
    private Intent owned(String id, long owner) { Intent result = find(id, owner); if (result == null) fail("批次执行记录不存在或无权访问"); return result; }
    private void update(Intent intent, String status, String error) { intent.status = status; intent.error = error; save(intent); }
    private void save(Intent intent) { intent.updatedAt = Instant.now().toString(); files.write("job", intent.receiptId, intent); }
    private static View view(Intent i) { return new View(i.receiptId, i.runId, i.releaseId, i.releaseName, i.releaseHash, i.executionHash, i.inputSha256, i.status, i.runStatus, i.deliveryId, i.deliveryStatus, i.error, i.createdAt, i.updatedAt); }
    private static void fail(String message) { throw new ServiceException(message); }
    @PreDestroy public void close()
    {
        synchronized (this) { closed = true; observer.shutdownNow(); submissions.shutdownNow(); }
        try { boolean done = observer.awaitTermination(5, TimeUnit.SECONDS) && submissions.awaitTermination(10, TimeUnit.SECONDS); if (done) synchronized (this) { if (files != null) files.close(); } }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
