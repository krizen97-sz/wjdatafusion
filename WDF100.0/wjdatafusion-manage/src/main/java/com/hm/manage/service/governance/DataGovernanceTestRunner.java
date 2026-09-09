package com.hm.manage.service.governance;

import com.fasterxml.jackson.databind.JsonNode;
import com.hm.common.exception.ServiceException;
import com.hm.manage.service.governance.DataGovernanceModels.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;
import static com.hm.manage.service.governance.DataGovernanceEngine.*;

/** Real NiFi execution. Only a stopped, validated snapshot is rebuilt inside an owned disposable group. */
@Component
public class DataGovernanceTestRunner
{
    private final DataGovernanceEngine engine;
    private final DataGovernanceNifiClient client;
    public DataGovernanceTestRunner(DataGovernanceEngine engine) { this.engine = engine; this.client = engine.client; }

    public void execute(StoredRun stored, AtomicBoolean cancelled, Consumer<StoredRun> save)
    {
        TestRun run = stored.run;
        boolean createAttempted = false;
        boolean emptyObserved = false;
        long deadline = System.nanoTime() + engine.properties.getTestTimeoutSeconds() * 1_000_000_000L;
        try
        {
            check(cancelled, deadline);
            engine.flow(run.flowId); // Revalidate ownership immediately before taking the saved definition snapshot.
            DataGovernanceSafeFlow flow = new DataGovernanceSafeFlow(engine.groupContents(run.flowId));
            check(cancelled, deadline);
            createAttempted = true;
            JsonNode group = engine.createGroup(engine.root(), "样本测试 " + run.id.substring(0, 8), TEST + run.id);
            run.engineTestGroupId = group.path("component").path("id").asText(); save.accept(stored);
            Map<String, String> ids = new HashMap<>();
            for (String oldId : flow.order)
            {
                check(cancelled, deadline);
                JsonNode p = flow.processors.get(oldId);
                Map<String, Object> props = engine.mapper.convertValue(p.path("config").path("properties"), Map.class);
                if (oldId.equals(flow.source))
                {
                    String encoded = Base64.getEncoder().encodeToString(stored.inputJson.getBytes(StandardCharsets.UTF_8));
                    // A literal Base64 expression prevents sample text such as ${ENV_SECRET} from being evaluated.
                    props = map("Custom Text", "${literal('" + encoded + "'):base64Decode()}", "Batch Size", "1",
                        "Data Format", "Text", "Unique FlowFiles", "false");
                }
                if (p.path("type").asText().equals(STANDARD + "EvaluateJsonPath") && stored.parameters.containsKey("jsonPath"))
                    props.put("sample.value", stored.parameters.get("jsonPath"));
                if (p.path("type").asText().equals(STANDARD + "RouteOnAttribute") && stored.parameters.containsKey("requiredValue"))
                {
                    String required = (String) stored.parameters.get("requiredValue");
                    props.put("accepted", required.isEmpty() ? "${sample.value:isEmpty():not()}"
                        : "${sample.value:equals('" + required.replace("\\", "\\\\").replace("'", "\\'") + "')}");
                }
                List<String> terminated = new ArrayList<>();
                p.path("config").path("autoTerminatedRelationships").forEach(r -> terminated.add(r.asText()));
                String newId = engine.createProcessor(run.engineTestGroupId, p.path("name").asText(), p.path("type").asText(),
                    processorComment(p), props, terminated, ids.size() * 220).path("component").path("id").asText();
                ids.put(oldId, newId);
            }
            List<Edge> edges = new ArrayList<>();
            for (JsonNode connection : flow.connections)
            {
                check(cancelled, deadline);
                String from = connection.path("source").path("id").asText(), to = connection.path("destination").path("id").asText();
                String relation = connection.path("selectedRelationships").get(0).asText();
                String id = engine.connect(run.engineTestGroupId, ids.get(from), ids.get(to), List.of(relation))
                    .path("component").path("id").asText();
                edges.add(new Edge(id, from, to, relation));
            }
            for (String oldId : flow.order)
            {
                check(cancelled, deadline);
                List<Edge> inputs = edges.stream().filter(e -> e.to.equals(oldId)).toList();
                List<Edge> outputs = edges.stream().filter(e -> e.from.equals(oldId)).toList();
                long inputCount = count(inputs);
                List<String> inputSamples = samples(inputs, cancelled, deadline);
                JsonNode definition = flow.processors.get(oldId);
                if (oldId.equals(flow.capture))
                {
                    run.output.addAll(samples(inputs.stream().filter(edge -> !Set.of("empty", "failure").contains(edge.relation)).toList(), cancelled, deadline));
                    run.steps.add(new StepResult(oldId, definition.path("name").asText(), definition.path("type").asText(),
                        "SUCCEEDED", inputCount, inputCount, List.of("已读取真实 NiFi 捕获队列；观察节点未执行"),
                        new Samples(inputSamples, inputSamples)));
                    save.accept(stored); continue;
                }
                if (oldId.equals(flow.source) || inputCount > 0)
                {
                    do
                    {
                        long beforeInput = count(inputs);
                        long beforeOutput = count(outputs);
                        state(ids.get(oldId), "RUN_ONCE");
                        while (true)
                        {
                            check(cancelled, deadline);
                            JsonNode status = client.json("GET", "/processors/" + ids.get(oldId), null);
                            boolean idle = status.path("status").path("aggregateSnapshot").path("activeThreadCount").asInt(-1) == 0;
                            // One onTrigger may consume only one FlowFile. Return to the outer loop after
                            // observable progress, rather than waiting for that single invocation to drain everything.
                            boolean consumed = oldId.equals(flow.source) ? count(outputs) > beforeOutput : count(inputs) < beforeInput;
                            if (idle && consumed && "STOPPED".equals(status.path("component").path("state").asText())) break;
                            pause();
                        }
                    } while (!oldId.equals(flow.source) && count(inputs) > 0);
                }
                long outputCount = count(outputs);
                List<Map<String, String>> attributes = new ArrayList<>();
                List<String> outputSamples = samples(outputs, cancelled, deadline, attributes);
                boolean failure = false;
                List<String> messages = new ArrayList<>();
                for (Edge edge : outputs)
                {
                    long queued = queued(edge.id);
                    messages.add("NiFi 关系 " + edge.relation + "：" + queued + " 个 FlowFile");
                    if (edge.relation.equals("failure") && queued > 0) failure = true;
                    if (edge.relation.equals("empty") && queued > 0) emptyObserved = true;
                }
                run.steps.add(new StepResult(oldId, definition.path("name").asText(), definition.path("type").asText(),
                    failure ? "FAILED" : inputCount == 0 && !oldId.equals(flow.source) ? "SKIPPED" : "SUCCEEDED",
                    inputCount, outputCount, messages, new Samples(inputSamples, outputSamples, attributes)));
                save.accept(stored);
                if (failure) throw new ServiceException("NiFi 将样本路由到失败关系，请检查对应步骤");
            }
            run.status = emptyObserved && run.output.isEmpty() ? "EMPTY" : "SUCCEEDED";
        }
        catch (Cancelled e) { run.status = "CANCELLED"; run.error = "测试已取消"; }
        catch (Expired e) { run.status = "TIMED_OUT"; run.error = "测试超过执行时限"; }
        catch (DataGovernanceSafeFlow.UnsupportedFlow e) { run.status = "UNSUPPORTED"; run.error = e.getMessage(); }
        catch (ServiceException e) { run.status = "FAILED"; run.error = e.getMessage(); }
        catch (Exception e) { run.status = "FAILED"; run.error = "样本测试执行失败，请检查独立引擎状态"; }
        finally
        {
            run.cleanupConfirmed = !createAttempted || cleanup(run);
            if (!run.cleanupConfirmed)
            {
                run.status = "CLEANUP_REQUIRED";
                run.error = "尚未确认测试引擎资源已停止并清理；请由管理员检查该测试组";
            }
            run.updatedAt = Instant.now().toString(); save.accept(stored);
        }
    }

    private void state(String processor, String state)
    {
        JsonNode current = client.json("GET", "/processors/" + id(processor), null);
        if (state.equals("RUN_ONCE") && !"VALID".equals(current.path("component").path("validationStatus").asText()))
            throw new ServiceException("NiFi 组件校验未通过，未执行样本");
        client.json("PUT", "/processors/" + processor + "/run-status", map("revision", current.path("revision"),
            "state", state, "disconnectedNodeAcknowledged", false));
    }
    private long queued(String connection)
    {
        JsonNode q = client.json("GET", "/connections/" + id(connection), null).path("status").path("aggregateSnapshot").path("flowFilesQueued");
        if (!q.isNumber()) throw new ServiceException("NiFi 未返回队列数量，无法确认测试结果");
        return q.asLong();
    }
    private long count(List<Edge> edges)
    { long count = 0; for (Edge edge : edges) count += queued(edge.id); return count; }

    private List<String> samples(List<Edge> edges, AtomicBoolean cancelled, long deadline)
    { return samples(edges, cancelled, deadline, null); }

    private List<String> samples(List<Edge> edges, AtomicBoolean cancelled, long deadline, List<Map<String, String>> attributes)
    {
        List<String> result = new ArrayList<>();
        for (Edge edge : edges)
        {
            if (result.size() >= 10 || queued(edge.id) == 0) continue;
            String request = client.json("POST", "/flowfile-queues/" + edge.id + "/listing-requests", null)
                .path("listingRequest").path("id").asText();
            id(request);
            try
            {
                JsonNode listing;
                do
                {
                    check(cancelled, deadline);
                    listing = client.json("GET", "/flowfile-queues/" + edge.id + "/listing-requests/" + request, null).path("listingRequest");
                    if (!listing.path("finished").asBoolean()) pause();
                } while (!listing.path("finished").asBoolean());
                for (JsonNode file : listing.path("flowFileSummaries"))
                {
                    if (result.size() == 10) break;
                    String content = client.content("/flowfile-queues/" + edge.id + "/flowfiles/" + id(file.path("uuid").asText()) + "/content");
                    result.add(content.length() > 8192 ? content.substring(0, 8192) + "\n[样本预览已截断]" : content);
                    if (attributes != null)
                    {
                        JsonNode detail = client.json("GET", "/flowfile-queues/" + edge.id + "/flowfiles/" + id(file.path("uuid").asText()), null);
                        Map<String, String> sample = new LinkedHashMap<>();
                        detail.path("flowFile").path("attributes").fields().forEachRemaining(entry -> {
                            if (entry.getKey().startsWith("sample."))
                            {
                                String value = entry.getValue().asText();
                                sample.put(entry.getKey(), value.substring(0, Math.min(value.length(), 8192)));
                            }
                        });
                        attributes.add(sample);
                    }
                }
            }
            finally { client.json("DELETE", "/flowfile-queues/" + edge.id + "/listing-requests/" + request, null); }
        }
        return result;
    }

    /** Only delete groups carrying this run's marker and directly below the configured root. */
    public boolean cleanup(TestRun run)
    {
        try
        {
            List<String> owned = new ArrayList<>();
            for (JsonNode entity : client.json("GET", "/process-groups/" + engine.root() + "/process-groups", null).path("processGroups"))
                if ((TEST + run.id).equals(entity.path("component").path("comments").asText()))
                    owned.add(id(entity.path("component").path("id").asText()));
            if (owned.isEmpty()) return run.engineTestGroupId != null && absent(run.engineTestGroupId);
            for (String group : owned)
            {
                client.json("PUT", "/flow/process-groups/" + group, map("id", group, "state", "STOPPED", "disconnectedNodeAcknowledged", false));
                long deadline = System.nanoTime() + 15_000_000_000L;
                JsonNode contents;
                while (true)
                {
                    contents = engine.groupContents(group);
                    boolean stopped = true;
                    for (JsonNode entity : contents.path("processors"))
                        if (entity.path("status").path("aggregateSnapshot").path("activeThreadCount").asInt(-1) != 0
                            || "RUNNING".equals(entity.path("component").path("state").asText())) stopped = false;
                    if (stopped) break;
                    if (System.nanoTime() > deadline) return false;
                    pause();
                }
                for (JsonNode entity : contents.path("connections"))
                {
                    if (System.nanoTime() > deadline) return false;
                    String connection = id(entity.path("component").path("id").asText());
                    if (queued(connection) == 0) continue;
                    String drop = client.json("POST", "/flowfile-queues/" + connection + "/drop-requests", null).path("dropRequest").path("id").asText();
                    id(drop);
                    while (!client.json("GET", "/flowfile-queues/" + connection + "/drop-requests/" + drop, null).path("dropRequest").path("finished").asBoolean())
                    { if (System.nanoTime() > deadline) return false; pause(); }
                    client.json("DELETE", "/flowfile-queues/" + connection + "/drop-requests/" + drop, null);
                    if (queued(connection) != 0) return false;
                }
                JsonNode current = client.json("GET", "/process-groups/" + group, null);
                client.json("DELETE", "/process-groups/" + group + "?version=" + current.path("revision").path("version").asLong()
                    + "&disconnectedNodeAcknowledged=false", null);
                if (!absent(group)) return false;
            }
            return true;
        }
        catch (Exception e) { return false; }
    }
    private boolean absent(String group)
    {
        for (JsonNode entity : client.json("GET", "/process-groups/" + engine.root() + "/process-groups", null).path("processGroups"))
            if (group.equals(entity.path("component").path("id").asText())) return false;
        return true;
    }
    private static void check(AtomicBoolean cancelled, long deadline)
    { if (cancelled.get()) throw new Cancelled(); if (System.nanoTime() > deadline) throw new Expired(); }
    private static void pause() { try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new Cancelled(); } }
    private record Edge(String id, String from, String to, String relation) { }
    private static class Cancelled extends RuntimeException { }
    private static class Expired extends RuntimeException { }
}
