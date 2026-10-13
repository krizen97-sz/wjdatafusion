package com.hm.manage.service.governance.hdi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hm.common.core.domain.model.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Compatibility endpoints for the untouched original components, backed by platform-owned records. */
@Component
@ConditionalOnProperty(prefix = "data-governance-hdi-core", name = "enabled", havingValue = "true")
public class HdiCoreApi {
    private final HdiCoreProperties properties;
    private final HdiTaskStore store;
    private final HdiCoreSessions sessions;
    private final HdiCoreCatalog catalog;
    private final HdiCoreAssets assets;
    private final HdiCompilerClient compiler;
    private final ObjectProvider<HdiCoreKeys> keys;
    private final ObjectProvider<HdiCoreRuns> runs;

    public HdiCoreApi(HdiCoreProperties properties, HdiTaskStore store, HdiCoreSessions sessions, HdiCoreCatalog catalog,
            HdiCoreAssets assets, HdiCompilerClient compiler, ObjectProvider<HdiCoreKeys> keys, ObjectProvider<HdiCoreRuns> runs) {
        this.properties = properties; this.store = store; this.sessions = sessions; this.catalog = catalog;
        this.assets = assets; this.compiler = compiler; this.keys = keys; this.runs = runs;
    }

    public void handle(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HdiCoreSessions.Session session = (HdiCoreSessions.Session) request.getAttribute(HdiCoreSessions.SESSION_ATTRIBUTE);
        LoginUser user = (LoginUser) request.getAttribute(HdiCoreSessions.USER_ATTRIBUTE);
        if (session == null || user == null || user.getUserId() == null || session.ownerId != user.getUserId()) throw new HdiTaskFault(401, "请从平台进入数据流程");
        HdiCoreSessions.require(user, "governance:flow:list");
        String path = path(request), method = request.getMethod();
        if ("/".equals(path)) {
            requireMethod(method, "GET");
            String html = "<!doctype html><html><head><meta content=\"" + HdiCoreSessions.CSRF_HEADER + "\" name=\"_csrf_header\"/>"
                + "<meta name=\"_csrf\" content=\"" + session.csrf + "\"/></head><body></body></html>";
            bytes(response, html.getBytes(StandardCharsets.UTF_8), "text/html;charset=UTF-8", false); return;
        }
        if ("/_platform/context".equals(path)) { requireMethod(method, "GET"); HdiProtocol.send(response, sessions.context(session, user)); return; }
        HdiCoreAssets.Asset asset = assets.read("/detl" + path);
        if (asset != null) { if (!Set.of("GET", "HEAD").contains(method)) throw new HdiTaskFault(405, "静态资源仅支持读取"); bytes(response, asset.bytes(), asset.contentType(), method.equals("HEAD")); return; }
        if ("POST".equals(method)) sessions.csrf(request, session);
        Object data = dispatch(path, method, request, session, user);
        HdiProtocol.send(response, data);
    }

    private Object dispatch(String path, String method, HttpServletRequest request, HdiCoreSessions.Session session, LoginUser user) throws IOException {
        long owner = session.ownerId;
        switch (path) {
            case "/workspace/getByProduct":
                requireMethod(method, "GET");
                if (!"iDataFusion DFP".equals(HdiProtocol.query(request, "product"))) throw new HdiTaskFault(400, "任务组件入口无效");
                return Map.of("workspaceId", session.workspace.id(), "workspaceName", session.workspace.name());
            case "/getLanguage": requireMethod(method, "GET"); return "zh_CN";
            case "/getEtlUserInfo": requireMethod(method, "GET"); return sessions.context(session, user).get("user");
            case "/getPublicKey":
                requireMethod(method, "GET"); HdiCoreKeys key = keys.getIfAvailable();
                if (key == null) throw new HdiTaskFault(503, "节点参数加密尚未就绪");
                return Map.of("publicKey", key.publicKey());
            case "/system/environment/getBase64VisualEnable": requireMethod(method, "GET"); return true;
            case "/globalConfig/groupAdapterManage/listAdapters": requireMethod(method, "GET"); return catalog.getAdapters();
            case "/globalConfig/groupAdapterManage/getAdapterTypeOptions": requireMethod(method, "GET"); return catalog.getAdapterTypeOptions();
            case "/code/codeConfig/getDataTypeList": requireMethod(method, "GET"); return catalog.getDataTypes();
            case "/globalConfig/paramManage/listParams": {
                requireMethod(method, "POST"); JsonNode query = object(body(request));
                if (!query.path("typeId").isIntegralNumber()) throw bad("参数类型无效");
                return catalog.listParams(query.path("typeId").intValue(), optionalText(query, "adapterName"), optionalText(query, "stepTypeName"));
            }
            case "/flow/getColumnDisplayName": requireMethod(method, "GET"); return parse(store.getPreference(owner, "task-columns", "[]"));
            case "/flow/saveColumnDisplayName": {
                requireMethod(method, "POST"); JsonNode columns = body(request);
                if (!columns.isArray() || columns.size() > 64) throw bad("列设置无效");
                Set<String> unique = new java.util.HashSet<>();
                for (JsonNode column : columns) if (!column.isTextual() || !column.asText().matches("[A-Za-z][A-Za-z0-9_]{0,63}") || !unique.add(column.asText())) throw bad("列设置无效");
                store.setPreference(owner, "task-columns", columns.toString()); return null;
            }
            case "/folder/findFolder": {
                requireMethod(method, "POST"); emptyBody(request); checkProject(HdiProtocol.query(request, "projectCode"), owner);
                String requestedUser = HdiProtocol.query(request, "userId");
                if (requestedUser != null && !requestedUser.isBlank() && !Long.toString(owner).equals(requestedUser)) throw new HdiTaskFault(403, "目录不属于当前用户");
                String type = HdiProtocol.query(request, "folderType");
                if (type != null && !Set.of("1", "5").contains(type)) throw unsupported();
                // This platform profile currently persists owner-root tasks only; no directory or script records exist.
                return List.of();
            }
            case "/globalConfig/tag/listTag": requireMethod(method, "GET"); return List.of();
            case "/flow/getFrequencyEnums": requireMethod(method, "GET"); return Map.of(); // No periodic schedule has been registered in this profile.
            case "/flow/findFlowPage": return list(request, method, user);
            case "/flow/pageInterruptedFlow": {
                requireMethod(method, "GET"); int page = page(request, "pageNo", 1, 100000), size = page(request, "pageSize", 20, 200);
                return Map.of("list", store.interrupted(owner, page, size).stream().map(task -> flow(task, user)).toList(), "total", store.interruptedCount(owner));
            }
            case "/flow/validateFlowDisplayName": {
                requireMethod(method, "POST"); JsonNode query = object(body(request)); checkProject(optionalText(query, "projectCode"), owner);
                Long except = optionalId(query.get("flowId"), "任务标识"); if (except != null) store.get(owner, except);
                return store.nameAvailable(owner, optionalText(query, "flowDisplayName"), except);
            }
            case "/flow/saveFlowVisual/save": return save(request, method, session, user, false);
            case "/flow/saveFlowVisual/push": return save(request, method, session, user, true);
            case "/mount/getMountFlowVisual": {
                requireMethod(method, "GET"); store.get(owner, queryId(request, "flowId")); return List.of(); // Stored profile has no mount objects.
            }
            case "/globalConfig/instanceManage/findAllInstance":
            case "/globalConfig/instanceManage/findAvailableInstances":
                requireMethod(method, "GET"); checkGroup(HdiProtocol.query(request, "groupId"), session); return resources(session);
            case "/flow/valid/getAdapterReadWritePath":
                requireMethod(method, "POST"); emptyBody(request); return List.of("/平台输出");
            case "/flow/listCrossInstanceFlow": {
                requireMethod(method, "POST"); JsonNode query = object(body(request));
                JsonNode ids = query.path("flowIds"); if (!ids.isArray() || ids.isEmpty() || ids.size() > 100) throw bad("请选择有效任务");
                Set<String> registered = new java.util.HashSet<>();
                for (Map<String, Object> binding : resources(session)) registered.add(binding.get("id").toString());
                if (registered.size() != 1) throw unsupported();
                for (JsonNode id : ids) {
                    HdiTaskStore.Task task = store.get(owner, nodeId(id, "任务标识"));
                    JsonNode visual = parse(store.revision(owner, task.id(), task.revision()).visualJson());
                    for (JsonNode node : visual.path("nodes")) for (JsonNode binding : node.path("moduleInstanceList"))
                        if (!registered.contains(binding.path("id").asText())) throw unsupported();
                }
                // Verified profile has exactly the one worker-registered execution binding; no cross-instance graph exists.
                return List.of();
            }
            default: return taskRoute(path, method, request, session, user);
        }
    }

    private Object taskRoute(String path, String method, HttpServletRequest request, HdiCoreSessions.Session session, LoginUser user) throws IOException {
        long owner = session.ownerId;
        for (String prefix : List.of("/flow/getFlowVisualById/", "/flow/getFlowById/")) if (path.startsWith(prefix)) {
            requireMethod(method, "GET"); long id = HdiProtocol.positive(path.substring(prefix.length()), "任务标识");
            HdiTaskStore.Task task = store.get(owner, id);
            return prefix.contains("Visual") ? visual(task, user) : flow(task, user);
        }
        String adapterPrefix = "/globalConfig/groupAdapterManage/getOrderedGroupAdapters/";
        if (path.startsWith(adapterPrefix)) {
            requireMethod(method, "POST"); emptyBody(request); checkGroup(path.substring(adapterPrefix.length()), session); return catalog.getAdapters();
        }
        String instancePrefix = "/globalConfig/instanceManage/findByGroupId/";
        if (path.startsWith(instancePrefix)) { requireMethod(method, "GET"); checkGroup(path.substring(instancePrefix.length()), session); return resources(session); }
        for (String operation : List.of("debugFlow", "startFlow", "startFlowV2", "stopFlow", "stopFlowV2")) {
            String prefix = "/flow/" + operation + "/";
            if (!path.startsWith(prefix)) continue;
            requireMethod(method, operation.equals("debugFlow") ? "POST" : "GET"); sessions.csrf(request, session);
            HdiCoreSessions.require(user, "governance:flow:test"); emptyBody(request);
            HdiTaskStore.Task task = store.get(owner, HdiProtocol.positive(path.substring(prefix.length()), "任务标识"));
            if (operation.startsWith("stop")) { runtime().stop(owner, lastRun(task)); return flow(store.get(owner, task.id()), user); }
            if (operation.equals("debugFlow")) { runtime().startDebug(owner, task.id(), task.revision()); return null; }
            runtime().startRun(owner, task.id(), task.revision()); return flow(store.get(owner, task.id()), user);
        }
        if (path.equals("/flow/getRunningStatus") || path.equals("/flow/getFlowVisualState")) {
            requireMethod(method, path.endsWith("getRunningStatus") ? "GET" : "POST"); emptyBody(request);
            HdiTaskStore.Task task = store.get(owner, queryId(request, "flowId"));
            JsonNode observation = runtime().status(owner, lastRun(task));
            ObjectNode result = HdiProtocol.JSON.createObjectNode().put("flowId", task.id()).put("flowDisplayName", task.name())
                .put("available", false).put("metricsAvailable", false).put("reason", "NOT_COLLECTED");
            result.set("run", observation);
            if (path.endsWith("getRunningStatus")) result.set("moduleStatusList", HdiProtocol.JSON.createArrayNode());
            else {
                result.set("instanceStateList", HdiProtocol.JSON.createArrayNode());
                result.putNull("inputNum").putNull("outputNum").putNull("startTime").putNull("endTime");
            }
            return result;
        }
        if (path.startsWith("/statistics/modulesSpeed/") || path.startsWith("/statistics/modulesSum/")) {
            requireMethod(method, "POST"); long id = HdiProtocol.positive(path.substring(path.lastIndexOf('/') + 1), "任务标识");
            HdiTaskStore.Task task = store.get(owner, id); runtime().moduleMetrics(owner, lastRun(task));
            throw new HdiTaskFault(503, "原引擎尚未提供节点吞吐量观测，不能显示推测指标");
        }
        throw unsupported();
    }

    private Object list(HttpServletRequest request, String method, LoginUser user) throws IOException {
        requireMethod(method, "POST"); JsonNode query = object(body(request)); long owner = user.getUserId();
        checkProject(optionalText(query, "projectCode"), owner);
        String keyword = optionalText(query, "keyword"); if (keyword != null && keyword.length() > 128) throw bad("搜索条件过长");
        Long folder = folder(query.get("folderId"));
        List<String> states = states(optionalText(query, "flowStatus"));
        var fields = query.fields();
        while (fields.hasNext()) {
            var field = fields.next();
            if (Set.of("projectCode", "keyword", "folderId", "flowStatus").contains(field.getKey()) || empty(field.getValue())) continue;
            if (field.getKey().equals("flowType") && field.getValue().asText().equals("0")) continue;
            throw new HdiTaskFault(422, "当前基础任务列表尚不支持此筛选条件");
        }
        int page = page(request, "pageNo", 1, 100000), size = page(request, "pageSize", 20, 200);
        return Map.of("list", store.list(owner, keyword, folder, states, page, size).stream().map(task -> flow(task, user)).toList(), "total", store.count(owner, keyword, folder, states));
    }

    private long save(HttpServletRequest request, String method, HdiCoreSessions.Session session, LoginUser user, boolean publish) throws IOException {
        requireMethod(method, "POST"); HdiCoreSessions.require(user, "governance:flow:edit");
        if (publish) HdiCoreSessions.require(user, "governance:flow:test");
        int limit = properties.getMaxVisualBytes(); if (limit < 1024 || limit > 8 * 1024 * 1024) throw new HdiTaskFault(503, "任务配置限额无效");
        ObjectNode visual = (ObjectNode)HdiProtocol.visual(HdiProtocol.body(request, limit + 4096), limit);
        ObjectNode flow = (ObjectNode)visual.path("flowVo"); long owner = session.ownerId;
        Long id = optionalId(flow.get("id"), "任务标识"), expected = optionalId(flow.get("_platformRevision"), "任务修订");
        if (id != null) store.get(owner, id); else if (expected != null) throw bad("新任务不能携带旧修订");
        if (visual.hasNonNull("flowId") && !java.util.Objects.equals(optionalId(visual.get("flowId"), "任务标识"), id)) throw bad("任务标识不一致");
        checkIdentity(flow.get("userId"), owner); checkIdentity(flow.get("groupId"), session.workspace.id());
        checkProject(optionalText(flow, "projectCode"), owner);
        if (flow.hasNonNull("flowType") && !flow.path("flowType").asText().equals("0")) throw unsupported();
        if (visual.hasNonNull("flowType") && !visual.path("flowType").asText().equals("0")) throw unsupported();
        if (flow.hasNonNull("tagNames") && !empty(flow.get("tagNames"))) throw new HdiTaskFault(422, "当前任务范围未启用标签管理");
        String name = optionalText(flow, "flowDisplayName"); long folder = folder(flow.get("folderId"));
        flow.remove("_platformRevision");
        HdiTaskStore.Task saved = store.save(owner, id, expected, name, folder, (allocatedId, revision) -> {
            JsonNode prepared = compiler.prepare(Map.of("businessTaskId", Long.toString(allocatedId), "revision", Long.toString(revision),
                "ownerId", Long.toString(owner), "workspaceId", Integer.toString(session.workspace.id()), "projectCode", HdiTaskStore.project(owner)), visual);
            return new HdiTaskStore.PreparedVisual(prepared.path("canonicalVisual").toString(), prepared.path("snapshotJson").asText(),
                prepared.path("snapshotSha256").asText(), prepared.path("diagnostics").toString());
        });
        if (publish) store.publish(owner, saved.id(), saved.revision());
        return saved.id();
    }

    private ObjectNode visual(HdiTaskStore.Task task, LoginUser user) {
        ObjectNode visual = (ObjectNode)parse(store.revision(task.ownerId(), task.id(), task.revision()).visualJson());
        visual.set("flowVo", flow(task, user)); visual.put("flowId", task.id());
        visual.put("readOnly", !HdiCoreSessions.has(user, "governance:flow:edit")); visual.putNull("historyId");
        visual.put("flowVersion", task.revision()); return visual;
    }

    private ObjectNode flow(HdiTaskStore.Task task, LoginUser user) {
        JsonNode canonical = parse(store.revision(task.ownerId(), task.id(), task.revision()).visualJson());
        ObjectNode flow = canonical.path("flowVo").isObject() ? ((ObjectNode)canonical.path("flowVo")).deepCopy() : HdiProtocol.JSON.createObjectNode();
        flow.put("id", task.id()).put("groupId", task.workspaceId()).put("userId", task.ownerId()).put("projectCode", task.projectCode())
            .put("flowDisplayName", task.name()).put("folderId", task.folderId()).put("_platformRevision", task.revision())
            .put("flowType", 0).put("flowStatus", state(task)).put("flowIncrStatus", 1).put("flowLock", 0)
            .put("nodeSize", canonical.path("nodes").size()).put("readOnly", !HdiCoreSessions.has(user, "governance:flow:edit"))
            .put("createTime", task.createdAt().toString()).put("updateTime", task.updatedAt().toString());
        if (task.scheduleJson() != null) flow.set("scheduleVo", parse(task.scheduleJson()));
        else flow.set("scheduleVo", HdiProtocol.JSON.createObjectNode().put("flowIncrStatus", 1).putNull("enablePeriod"));
        return flow;
    }

    private List<Map<String, Object>> resources(HdiCoreSessions.Session session) {
        JsonNode snapshot = runtime().resources();
        if (!snapshot.path("available").asBoolean() || !snapshot.path("bindings").isArray()) throw new HdiTaskFault(503, "平台执行目标尚未就绪");
        List<Map<String, Object>> result = new ArrayList<>();
        long taskCount = store.count(session.ownerId, null, null);
        long reservedRuns = store.countActiveRuns(session.ownerId);
        for (JsonNode binding : snapshot.path("bindings")) {
            Map<String, Object> row = new LinkedHashMap<>(); row.put("id", nodeId(binding.path("id"), "执行目标"));
            row.put("groupId", session.workspace.id()); row.put("instanceName", binding.path("name").asText());
            row.put("bindingState", binding.path("state").asText()); row.put("nativeInstanceAllocatedPerRun", true);
            // Original node selection subtracts these fields. They are actual platform records, not engine/CPU metrics.
            row.put("totalFlowCount", taskCount); row.put("activeFlowCount", reservedRuns);
            row.put("countScope", "PLATFORM_OWNER_REGISTERED");
            result.add(row);
        }
        return result;
    }
    private HdiCoreRuns runtime() { HdiCoreRuns service = runs.getIfAvailable(); if (service == null) throw new HdiTaskFault(503, "原任务运行服务尚未就绪"); return service; }
    private static String lastRun(HdiTaskStore.Task task) { if (task.lastRunId() == null) throw new HdiTaskFault(404, "任务尚无执行记录"); return task.lastRunId(); }
    private static int state(HdiTaskStore.Task task) {
        return switch (task.state()) {
            case "DRAFT" -> -1; case "READY" -> 0; case "QUEUED", "PREPARING", "COMPILING", "STARTING" -> 1;
            case "RUNNING" -> 2; case "SUCCEEDED" -> 3; case "FAILED", "UNKNOWN" -> 4; case "STOPPING" -> 10; case "CANCELLED" -> 0;
            default -> throw new HdiTaskFault(503, "任务状态尚不能确认");
        };
    }
    private static List<String> states(String value) {
        if (value == null || value.isBlank()) return null;
        if (value.length() > 64) throw bad("运行状态筛选无效");
        Set<String> result = new java.util.LinkedHashSet<>();
        for (String state : value.split(",", -1)) result.addAll(switch (state) {
            case "-1" -> List.of("DRAFT"); case "0" -> List.of("READY", "CANCELLED");
            case "1" -> List.of("QUEUED", "PREPARING", "COMPILING", "STARTING"); case "2" -> List.of("RUNNING");
            case "3", "31" -> List.of("SUCCEEDED"); case "4" -> List.of("FAILED", "UNKNOWN");
            case "10" -> List.of("STOPPING"); case "5", "6" -> List.of();
            default -> throw bad("运行状态筛选无效");
        });
        return List.copyOf(result);
    }
    private static JsonNode parse(String text) { try { return HdiProtocol.checked(HdiProtocol.JSON.readTree(text)); } catch (IOException error) { throw new HdiTaskFault(503, "任务存储内容不可用"); } }
    private static JsonNode body(HttpServletRequest request) throws IOException { return HdiProtocol.body(request, 256 * 1024); }
    private static JsonNode object(JsonNode node) { if (!node.isObject()) throw bad("任务请求必须是 JSON 对象"); return node; }
    private static String optionalText(JsonNode node, String field) { JsonNode value = node.get(field); if (value == null || value.isNull()) return null; if (!value.isTextual()) throw bad("任务文本字段无效"); return value.asText(); }
    private static boolean empty(JsonNode value) { return value == null || value.isNull() || value.isContainerNode() && value.isEmpty() || value.isTextual() && value.asText().isEmpty(); }
    private static long nodeId(JsonNode value, String label) { if (!value.isIntegralNumber() && !value.isTextual()) throw bad(label + "无效"); return HdiProtocol.positive(value.asText(), label); }
    private static Long optionalId(JsonNode value, String label) { return value == null || value.isNull() ? null : nodeId(value, label); }
    private static long queryId(HttpServletRequest request, String name) { return HdiProtocol.positive(HdiProtocol.query(request, name), "任务标识"); }
    private static long folder(JsonNode value) { if (value == null || value.isNull() || "-1".equals(value.asText())) return -1; throw new HdiTaskFault(422, "当前任务范围仅支持我的任务根目录"); }
    private static void checkIdentity(JsonNode value, long expected) { if (value != null && !value.isNull() && nodeId(value, "任务归属") != expected) throw new HdiTaskFault(403, "任务归属与当前用户不一致"); }
    private static void checkGroup(String group, HdiCoreSessions.Session session) { if (group != null && !group.isBlank() && !Integer.toString(session.workspace.id()).equals(group)) throw new HdiTaskFault(403, "执行资源工作区不匹配"); }
    private static void checkProject(String project, long owner) { if (project != null && !project.isBlank() && !HdiTaskStore.project(owner).equals(project)) throw new HdiTaskFault(403, "任务项目与当前用户不一致"); }
    private static void requireMethod(String actual, String expected) { if (!expected.equals(actual)) throw new HdiTaskFault(405, "任务接口请求方法不支持"); }
    private static void emptyBody(HttpServletRequest request) throws IOException { if (request.getInputStream().read() != -1) throw bad("此任务接口不接受请求正文"); }
    private static int page(HttpServletRequest request, String key, int fallback, int max) { String text = HdiProtocol.query(request, key); if (text == null) return fallback; long value = HdiProtocol.positive(text, "分页参数"); if (value > max) throw bad("分页参数超过限制"); return (int)value; }
    private static HdiTaskFault bad(String message) { return new HdiTaskFault(400, message); }
    private static HdiTaskFault unsupported() { return new HdiTaskFault(422, "此原任务功能尚未纳入平台已支持范围"); }
    private static String path(HttpServletRequest request) {
        String raw = request.getRequestURI(), context = request.getContextPath();
        if (context != null && !context.isEmpty() && raw.startsWith(context)) raw = raw.substring(context.length());
        if (raw.equals("/detl")) return "/";
        if (!raw.startsWith("/detl/") || raw.length() > 2048 || raw.indexOf('%') >= 0 || raw.indexOf('\\') >= 0 || raw.indexOf(';') >= 0
                || raw.contains("//") || raw.chars().anyMatch(Character::isISOControl)) throw bad("任务接口路径无效");
        for (String segment : raw.split("/")) if (segment.equals(".") || segment.equals("..")) throw bad("任务接口路径无效");
        return raw.substring(5);
    }
    private static void bytes(HttpServletResponse response, byte[] body, String type, boolean head) throws IOException { HdiProtocol.noCache(response); response.setContentType(type); response.setContentLength(body.length); if (!head) response.getOutputStream().write(body); }
}
