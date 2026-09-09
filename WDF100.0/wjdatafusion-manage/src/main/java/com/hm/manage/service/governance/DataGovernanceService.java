package com.hm.manage.service.governance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hm.common.exception.ServiceException;
import com.hm.manage.service.governance.DataGovernanceModels.*;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Service;
import static com.hm.manage.service.governance.DataGovernanceEngine.*;

@Service
public class DataGovernanceService
{
    private final DataGovernanceEngine engine;
    private final DataGovernanceTestRunner runner;
    private final DataGovernanceRunRepository repository;
    private final ObjectMapper mapper = new ObjectMapper().enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final Map<String, AtomicBoolean> active = new ConcurrentHashMap<>();
    private boolean recovered;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(8), task -> { Thread thread = new Thread(task, "data-governance-test"); thread.setDaemon(true); return thread; });

    public DataGovernanceService(DataGovernanceEngine engine, DataGovernanceTestRunner runner, DataGovernanceRunRepository repository)
    { this.engine = engine; this.runner = runner; this.repository = repository; }

    public List<Template> templates()
    {
        Map<String, Object> schema = map("type", "object", "additionalProperties", false, "properties", map(
            "jsonPath", map("type", "string", "title", "字段路径（可选覆盖画布参数）", "default", "$.message", "maxLength", 256),
            "requiredValue", map("type", "string", "title", "匹配值（可选）", "default", "", "maxLength", 256)));
        return List.of(new Template("blank", "空白流程", "在平台画布添加组件；测试前需要建立安全样本输入和结果输出节点", "AVAILABLE", map()),
            new Template(SAMPLE, "安全 JSON 样本流程", "真实 NiFi：样本输入、JSONPath、条件路由、结果队列；不连接业务源目标", "AVAILABLE", schema),
            new Template(DELIMITED, "协议文本样本流程", "真实 NiFi：JSON 数组转字段有序文本，兼容表头计数与分片", engine.supports(WRITER) ? "AVAILABLE" : "ENGINE_REQUIRED",
                map("type", "object", "additionalProperties", false, "properties", map(), "inputExample", "[{\"message\":\"示例\",\"picture\":\"\"}]")),
            new Template(LOOKUP_TEMPLATE, "快照查表样本流程", "合成双键字典与非空条件；可在节点中显式加载数据库快照，未命中保留默认字符串0", engine.supports(LOOKUP) ? "AVAILABLE" : "ENGINE_REQUIRED",
                map("type", "object", "additionalProperties", false, "properties", map(), "inputExample", "[{\"camera\":\"CAM-001\",\"platform\":\"DEMO\"},{\"camera\":\"UNKNOWN\",\"platform\":\"DEMO\"}]")),
            new Template("vehicle-pass", "普通过车业务流程", "需要查表快照、文件协议与 FTP 交付适配后才能执行", "ADAPTER_REQUIRED", map()),
            new Template("vehicle-violation", "违法告警业务流程", "需要业务规则、Kafka 确认边界与文件交付适配后才能执行", "ADAPTER_REQUIRED", map()));
    }

    public List<CatalogItem> catalog()
    {
        List<CatalogItem> items = new ArrayList<>();
        String[][] entries = {
            {"sample-input", "样本输入", "输入", "AVAILABLE", "GenerateFlowFile：只注入有界测试 JSON"},
            {"json-path", "JSON 字段提取", "转换", "AVAILABLE", "EvaluateJsonPath：提取到 sample.* 属性"},
            {"json-jolt", "JSON 映射", "转换", "AVAILABLE", "Jolt Chain：内联 shift/default/remove/cardinality/sort，使用 FlowFile 内容"},
            {"json-jolt-advanced", "Jolt 高级转换", "转换", "ADAPTER_REQUIRED", "Custom/Modify、属性来源、模块目录及文件规范未纳入安全样本执行"},
            {"route", "条件路由", "控制", "AVAILABLE", "RouteOnAttribute：有限 sample.* 表达式"},
            {"attributes", "常量与属性", "转换", "AVAILABLE", "UpdateAttribute：仅 sample.* 字段"},
            {"capture", "结果观察", "输出", "AVAILABLE", "停止的观察节点前队列，读取真实引擎输出"},
            {"postgres-lookup", "PostgreSQL 实时查表", "查询", "ADAPTER_REQUIRED", "逐批实时查询与长表缓存尚未接入；当前可用显式数据库快照与多条件快照查表"},
            {"kafka-consumer", "Kafka 消费", "输入", "ADAPTER_REQUIRED", "业务源需要独立连接与位点迁移，安全样本测试禁止连接"},
            {"kafka-producer", "Kafka 发送", "输出", "ADAPTER_REQUIRED", "业务目标需要确认与幂等策略，安全样本测试禁止发送"},
            {"protocol-file", "协议文件落盘与交付", "输出", "ADAPTER_REQUIRED", "文本编码已支持；实际文件清单、交付台账与整批成功后上传仍待接入"},
            {"ftp-delivery", "FTP 交付", "输出", "ADAPTER_REQUIRED", "需交付确认台账和协议适配，测试禁止访问实际目标"},
            {"business-script", "海康消息转换", "转换", "ADAPTER_REQUIRED", "需要迁移原 JavaScript 和数组、日期、空值语义"}
        };
        for (String[] entry : entries) items.add(new CatalogItem(entry[0], entry[1], entry[2],
            entry[0].equals("json-jolt") ? engine.supports(JOLT) ? "AVAILABLE" : "ENGINE_REQUIRED" : entry[3], entry[4],
            entry[0].equals("sample-input") ? List.of() : List.of("JSON", "FLOWFILE"), List.of("FLOWFILE")));
        items.add(new CatalogItem("delimited-text-writer", "协议文本样本输出", "输出", engine.supports(WRITER) ? "AVAILABLE" : "ENGINE_REQUIRED",
            "只转换封闭 JSON 数组；无文件系统、网络或业务源目标访问", List.of("JSON_ARRAY"), List.of("TEXT")));
        items.add(new CatalogItem("snapshot-lookup", "批次快照查表", "查询", engine.supports(LOOKUP) ? "AVAILABLE" : "ENGINE_REQUIRED",
            "多键等值、非空条件、默认值与重复键策略；显式加载字典快照并随发布版本冻结，不自动实时刷新数据库", List.of("JSON", "JSON_ARRAY"), List.of("JSON", "JSON_ARRAY")));
        return items;
    }

    public TestRun submit(String flowId, TestInput input, long userId)
    {
        recover(); validateInput(input);
        Flow flow = engine.flow(flowId);
        StoredRun stored = new StoredRun(); stored.ownerId = userId; stored.inputJson = input.inputJson();
        stored.parameters = input.parameters() == null ? map() : new java.util.LinkedHashMap<>(input.parameters());
        TestRun run = new TestRun(); stored.run = run;
        run.id = UUID.randomUUID().toString(); run.flowId = flow.id(); run.projectId = flow.projectId();
        run.status = "QUEUED"; run.createdAt = run.updatedAt = Instant.now().toString();
        try
        {
            stored.definition = new DataGovernanceSafeFlow(engine.groupContents(flow.id())).freeze(stored.inputJson, stored.parameters);
            run.definitionHash = DataGovernanceSafeFlow.hash(stored.definition);
            run.definitionCapturedAt = Instant.now().toString();
        }
        catch (DataGovernanceSafeFlow.UnsupportedFlow e)
        {
            // No unvalidated graph or properties are retained, even for rejected runs.
            stored.definition = null; run.status = "UNSUPPORTED"; run.error = e.getMessage(); run.cleanupConfirmed = true;
            repository.save(stored); return copy(run);
        }
        return enqueue(stored);
    }

    /** Internal publication boundary: validate and freeze once; never expose this envelope as an API DTO. */
    public StoredRun prepareSnapshot(String flowId, TestInput input, long userId)
    {
        validateInput(input);
        Flow flow = engine.flow(flowId);
        StoredRun snapshot = new StoredRun(); snapshot.ownerId = userId; snapshot.inputJson = input.inputJson();
        snapshot.parameters = input.parameters() == null ? map() : new java.util.LinkedHashMap<>(input.parameters());
        snapshot.run = new TestRun(); snapshot.run.flowId = flow.id(); snapshot.run.projectId = flow.projectId();
        snapshot.definition = new DataGovernanceSafeFlow(engine.groupContents(flow.id())).freeze(snapshot.inputJson, snapshot.parameters);
        snapshot.run.definitionHash = DataGovernanceSafeFlow.hash(snapshot.definition);
        snapshot.run.definitionCapturedAt = Instant.now().toString();
        return snapshot;
    }

    /** Reuses only the published definition, with a new durable run id allocated before any engine work. */
    public TestRun submitFrozen(StoredRun snapshot, long userId)
    { return submitFrozen(snapshot, userId, UUID.randomUUID().toString()); }

    TestRun submitFrozen(StoredRun snapshot, long userId, String runId)
    {
        recover(); id(runId);
        if (snapshot == null || snapshot.run == null || snapshot.ownerId != userId)
            throw new ServiceException("已发布版本不存在或无权执行");
        validateInput(new TestInput(snapshot.inputJson, snapshot.parameters));
        if (snapshot.definition == null || snapshot.run.definitionHash == null
            || !snapshot.run.definitionHash.equals(DataGovernanceSafeFlow.hash(snapshot.definition)))
            throw new ServiceException("已发布版本快照校验失败");
        new DataGovernanceSafeFlow(snapshot.definition);
        engine.flow(snapshot.run.flowId); // Scope is current; the mutable canvas is intentionally not read.
        if (repository.find(runId) != null) throw new ServiceException("执行记录标识已存在");
        StoredRun stored = mapper.convertValue(snapshot, StoredRun.class);
        TestRun run = new TestRun(); stored.run = run;
        run.id = runId; run.flowId = snapshot.run.flowId; run.projectId = snapshot.run.projectId;
        run.definitionHash = snapshot.run.definitionHash; run.definitionCapturedAt = snapshot.run.definitionCapturedAt;
        run.status = "QUEUED"; run.createdAt = run.updatedAt = Instant.now().toString();
        return enqueue(stored);
    }

    private TestRun enqueue(StoredRun stored)
    {
        TestRun run = stored.run;
        repository.save(stored);
        AtomicBoolean cancelled = new AtomicBoolean(); active.put(run.id, cancelled);
        try
        {
            executor.execute(() -> {
                try
                {
                    run.status = "RUNNING"; persist(stored);
                    runner.execute(stored, cancelled, this::persist);
                }
                finally { active.remove(run.id); }
            });
        }
        catch (RejectedExecutionException e)
        {
            active.remove(run.id); run.status = "FAILED"; run.error = "样本测试队列已满，请稍后重试";
            run.cleanupConfirmed = true; persist(stored);
        }
        return copy(repository.find(run.id).run);
    }

    void validateInput(TestInput input)
    {
        if (input == null || input.inputJson() == null || input.inputJson().getBytes(StandardCharsets.UTF_8).length > 256 * 1024)
            throw new ServiceException("样本 JSON 不能为空且不得超过 256 KiB");
        try
        {
            JsonNode json = mapper.readTree(input.inputJson());
            if (json == null || !(json.isObject() || json.isArray()) || json.isArray() && json.size() > 100)
                throw new ServiceException("样本必须为 JSON 对象或最多 100 条记录的数组");
        }
        catch (ServiceException e) { throw e; }
        catch (Exception e) { throw new ServiceException("样本 JSON 语法无效"); }
        if (input.parameters() != null)
        {
            for (Map.Entry<String, Object> entry : input.parameters().entrySet())
            {
                if (!Set.of("jsonPath", "requiredValue").contains(entry.getKey()) || !(entry.getValue() instanceof String value)
                    || value.length() > 256) throw new ServiceException("测试参数只支持有限 jsonPath 与 requiredValue 字符串");
                if (entry.getKey().equals("jsonPath") && (!value.startsWith("$") || value.contains("${") || value.contains("#{")))
                    throw new ServiceException("jsonPath 必须为不含环境引用的 JSONPath");
            }
        }
    }

    public List<TestRun> runs(String flowId, long owner)
    {
        recover(); if (flowId != null) id(flowId);
        return repository.summaries().stream().filter(r -> r.ownerId == owner && (flowId == null || flowId.equals(r.run.flowId)))
            .limit(100).map(r -> copy(r.run)).toList();
    }
    public TestRun run(String id, long owner) { recover(); return copy(owned(id, owner).run); }
    public TestRun cancel(String id, long owner)
    {
        recover(); StoredRun stored = owned(id, owner);
        AtomicBoolean cancelled = active.get(id);
        if (cancelled != null) { cancelled.set(true); return copy(stored.run); }
        if (stored.run.status.equals("CLEANUP_REQUIRED"))
        {
            stored.run.cleanupConfirmed = runner.cleanup(stored.run);
            if (stored.run.cleanupConfirmed) { stored.run.status = "CANCELLED"; stored.run.error = "残留测试组已确认清理"; }
            persist(stored);
        }
        return copy(stored.run);
    }
    private StoredRun owned(String id, long owner)
    {
        StoredRun result = repository.find(id);
        if (result == null || result.ownerId != owner) throw new ServiceException("测试记录不存在或无权访问");
        return result;
    }
    private synchronized void recover()
    {
        if (recovered) return;
        for (StoredRun summary : repository.summaries())
            if (Set.of("QUEUED", "RUNNING").contains(summary.run.status))
            {
                // Load only interrupted records in full; never persist a payload-free summary over a snapshot.
                StoredRun stored = repository.find(summary.run.id);
                if (stored == null || !Set.of("QUEUED", "RUNNING").contains(stored.run.status)) continue;
                boolean queued = stored.run.status.equals("QUEUED");
                stored.run.status = queued ? "FAILED" : "CLEANUP_REQUIRED";
                stored.run.error = "服务重启中断了测试，请检查并清理残留测试组后重新运行";
                stored.run.cleanupConfirmed = queued; persist(stored);
            }
        recovered = true;
    }
    private void persist(StoredRun stored) { stored.run.updatedAt = Instant.now().toString(); repository.save(stored); }
    private TestRun copy(TestRun run) { return mapper.convertValue(run, TestRun.class); }
    @PreDestroy public void close()
    {
        active.values().forEach(flag -> flag.set(true)); executor.shutdown();
        try { executor.awaitTermination(20, TimeUnit.SECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
