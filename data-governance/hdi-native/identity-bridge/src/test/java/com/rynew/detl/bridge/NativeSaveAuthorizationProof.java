package com.rynew.detl.bridge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hikvision.idatafusion.detl.mapper.*;
import com.hikvision.idatafusion.detl.model.*;
import java.lang.reflect.Proxy;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import static com.rynew.detl.bridge.IdentityBridgeProof.*;

/** Original DTO/decoder plus read-only original Mapper interfaces. This is not a live database test. */
final class NativeSaveAuthorizationProof {
    static int sequence = 130;
    static final class Database {
        final Map<Class<?>,Map<Long,Object>> rows = new HashMap<>();
        final AtomicInteger reads = new AtomicInteger();
        boolean unavailable;
        void put(Class<?> dao, long id, Object value) { rows.computeIfAbsent(dao, key -> new HashMap<>()).put(id, value); }
        <T> T mapper(Class<T> type) {
            return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type}, (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    if (method.getName().equals("toString")) return "OriginalReadMapperProof";
                    if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                    if (method.getName().equals("equals")) return proxy == args[0];
                }
                reads.incrementAndGet();
                if (unavailable) throw new IllegalStateException("synthetic private DAO diagnostic");
                if (method.getName().equals("selectByPrimaryKey") && args.length == 1 && args[0] instanceof Long)
                    return rows.getOrDefault(type, Map.of()).get(args[0]);
                if (type == EtlGroupAdapterDao.class && method.getName().equals("getAdapterInGroupById") && args.length == 2)
                    return Long.valueOf(901).equals(args[0]) ? rows.getOrDefault(type, Map.of()).get(args[1]) : null;
                throw new AssertionError("Unexpected mapper method: " + method.getName());
            }));
        }
        NativeFlowAuthorizer flows() {
            return new NativeFlowAuthorizer(mapper(EtlFlowMapper.class), mapper(EtlStreamDao.class), mapper(EtlStepDao.class), mapper(EtlStepItemDao.class), mapper(EtlModuleDao.class), mapper(EtlDstTableDao.class));
        }
        NativeSaveAuthorizer saves(boolean base64) {
            return new NativeSaveAuthorizer(base64, flows(), mapper(WorkspaceMapper.class), mapper(SysFolderMapper.class), mapper(EtlInstanceDao.class), mapper(EtlGroupAdapterDao.class),
                () -> List.of(new MappingJackson2HttpMessageConverter(IdentityMappings.JSON)));
        }
        IdentityBridgeFilter filter(Path replay, boolean base64) {
            return new IdentityBridgeFilter(verifier(replay), new IdentityMappings(mapping()), new NativeSessionIssuer(900), flows(), saves(base64));
        }
    }
    static Database database() {
        Database db = new Database();
        for (long id : new long[]{1,2}) {
            EtlFlow flow = flow(id, 901); flow.setUserId(99L); flow.setTenantCode("tenant-a"); flow.setProjectCode("project-a"); db.put(EtlFlowMapper.class, id, flow);
            EtlStream stream = new EtlStream(); stream.setId(200 + id); stream.setUpNodeId(id); stream.setUpNodeType(2); db.put(EtlStreamDao.class, 200 + id, stream);
            EtlStep step = new EtlStep(); step.setId(300 + id); step.setNodeId(200 + id); step.setNodeType(3); db.put(EtlStepDao.class, 300 + id, step);
            EtlStepItem item = new EtlStepItem(); item.setId(400 + id); item.setStepId(300 + id); db.put(EtlStepItemDao.class, 400 + id, item);
            EtlModule module = new EtlModule(); module.setId(500 + id); module.setFlowId(id); module.setItemId(400 + id); db.put(EtlModuleDao.class, 500 + id, module);
            EtlDstTable table = new EtlDstTable(); table.setId(600 + id); table.setStreamId(200 + id); table.setItemId(400 + id); db.put(EtlDstTableDao.class, 600 + id, table);
        }
        EtlGroup group = new EtlGroup(); group.setId(901L); group.setEnableFlag(1); group.setProduct("iDataFusion HDI"); db.put(WorkspaceMapper.class, 901, group);
        for (long id : new long[]{21,22}) { EtlInstance instance = new EtlInstance(); instance.setId(id); instance.setGroupId(id == 21 ? 901L : 902L); instance.setEnableFlag(1); db.put(EtlInstanceDao.class, id, instance); }
        for (long id : new long[]{11,12,13}) { EtlAdapter adapter = new EtlAdapter(); adapter.setId(id); adapter.setAdapterName(id == 11 ? "dataGeneratorInput" : id == 12 ? "fileOutput" : "fieldMapping"); adapter.setAdapterType(id == 11 ? "input" : id == 12 ? "output" : "preproc"); adapter.setEnableFlag(1); db.put(EtlGroupAdapterDao.class, id, adapter); }
        SysFolder folder = new SysFolder(); folder.setId(31L); folder.setWorkspaceId(901L); folder.setProjectCode("project-a"); folder.setEnableFlag(1); db.put(SysFolderMapper.class, 31, folder);
        return db;
    }
    static ObjectNode graph() throws Exception {
        String json = "{\"flowId\":1,\"flowVo\":{\"id\":1,\"groupId\":901,\"userId\":99,\"tenantCode\":\"tenant-a\",\"projectCode\":\"project-a\",\"flowType\":0,\"folderId\":-1,\"flowDisplayName\":\"本地验证\"},\"flowType\":0,\"nodes\":{"
            + "\"1001\":{\"id\":1001,\"adapterName\":\"dataGeneratorInput\",\"adapterType\":\"input\",\"stepType\":\"input\",\"moduleAdapterId\":11,\"moduleInstanceList\":[{\"id\":21}],\"adapterData\":[{\"fieldName\":\"label\",\"fieldType\":\"varchar\",\"ruleType\":\"FIXED_VALUE\",\"fieldExpression\":\"中文+ <tag>\"}]},"
            + "\"1002\":{\"id\":1002,\"adapterName\":\"fileOutput\",\"adapterType\":\"output\",\"stepType\":\"output\",\"moduleAdapterId\":12,\"moduleInstanceList\":[{\"id\":21}],\"adapterData\":{\"sourceType\":\"local\",\"fileType\":\"txt\",\"localDir\":\"/isolated/output\"},\"dstTableVo\":{\"id\":601}}},"
            + "\"streamVoList\":[{\"id\":201,\"streamId\":1001,\"streamNodeVoList\":[{\"id\":\"1001\",\"stepId\":301,\"itemId\":401,\"streamNodeModuleVoList\":[{\"moduleId\":501}]}]}],\"links\":[{\"source\":1001,\"target\":1002}],\"paramVoList\":[]}";
        return (ObjectNode)IdentityMappings.JSON.readTree(json);
    }
    static byte[] nativeBody(JsonNode graph, boolean base64) throws Exception {
        byte[] json = IdentityMappings.JSON.writeValueAsBytes(graph);
        if (!base64) return json;
        String encoded = Base64.getEncoder().encodeToString(URLEncoder.encode(new String(json, StandardCharsets.UTF_8), "UTF-8").getBytes(StandardCharsets.UTF_8));
        return IdentityMappings.JSON.writeValueAsBytes(Map.of("visualBase64", encoded));
    }
    static byte[] envelope(byte[] nativeBody, String mode) throws Exception {
        return IdentityMappings.JSON.writeValueAsBytes(Map.of("subject", "rynew:user:7", "mode", mode, "nativeBodyBase64", Base64.getEncoder().encodeToString(nativeBody)));
    }
    static Request signed(byte[] body) { return new Request(body, nonce(sequence++), IdentityBridgeFilter.SAVE_PATH); }
    static Response checkGraph(IdentityBridgeFilter filter, JsonNode graph, boolean base64) throws Exception { return exchange(filter, signed(envelope(nativeBody(graph, base64), "save"))); }
    static ObjectNode at(ObjectNode root, String pointer) { return (ObjectNode)root.at(pointer); }
    static void run(Path root) throws Exception {
        Database db = database(); Path replay = child(root, "save-scope-replays"); IdentityBridgeFilter filter = db.filter(replay, true);
        ObjectNode graph = graph(); byte[] original = nativeBody(graph, true), copy = original.clone();
        Request valid = signed(envelope(original, "save")); Response response = exchange(filter, valid);
        check(response.status == 200 && response.text.toString().equals("{\"code\":\"0\",\"data\":{\"authorized\":true}}"), "original basic generator/file DTO with owned internal keys passes");
        check(Arrays.equals(original, copy) && valid.session == null, "save authorization preserves original bytes and creates no session");
        check(exchange(filter, valid).status == 401, "save authorization ticket is one-time");
        NativeSaveAuthorizer.SaveRequest decoded = db.saves(true).parse(envelope(original, "push"), new IdentityMappings(mapping()));
        check(IdentityMappings.JSON.valueToTree(decoded.visual.getNodes().get("1001").getAdapterData()).get(0).path("fieldExpression").asText().equals("中文+ <tag>"), "actual vendor base64 and URL decoder preserves UTF8 plus and brackets");
        check(exchange(filter, signed(envelope(original, "push"))).status == 200, "same scope proof supports native push mode");
        for (String[] field : new String[][]{{"/streamVoList/0", "id", "202"}, {"/streamVoList/0/streamNodeVoList/0", "stepId", "302"}, {"/streamVoList/0/streamNodeVoList/0", "itemId", "402"}, {"/streamVoList/0/streamNodeVoList/0/streamNodeModuleVoList/0", "moduleId", "502"}, {"/nodes/1002/dstTableVo", "id", "602"}}) {
            ObjectNode foreign = graph.deepCopy(); at(foreign, field[0]).put(field[1], Long.parseLong(field[2]));
            check(checkGraph(filter, foreign, true).status == 403, "old database key must belong to exact target flow, not only same workspace: " + field[1]);
        }
        for (String field : List.of("userId", "tenantCode", "projectCode", "groupId")) {
            ObjectNode changed = graph.deepCopy(); ObjectNode info = at(changed, "/flowVo");
            if (field.endsWith("Id")) info.put(field, 73); else info.put(field, "other");
            check(checkGraph(filter, changed, true).status == 403, "existing persistent identity cannot be changed: " + field);
        }
        ObjectNode shared = graph.deepCopy(); at(shared, "/flowVo").remove(List.of("userId", "tenantCode", "projectCode"));
        check(checkGraph(filter, shared, true).status == 200, "shared editor may omit identity while original owner stays unchanged");
        ObjectNode newFlow = graph.deepCopy(); newFlow.remove("flowId"); at(newFlow, "/flowVo").remove("id"); at(newFlow, "/flowVo").put("userId", 73);
        check(checkGraph(filter, newFlow, true).status == 403, "new flow cannot claim old internal database keys");
        at(newFlow, "/streamVoList/0").remove("id"); at(newFlow, "/streamVoList/0/streamNodeVoList/0").remove(List.of("stepId", "itemId"));
        at(newFlow, "/streamVoList/0/streamNodeVoList/0/streamNodeModuleVoList/0").remove("moduleId"); at(newFlow, "/nodes/1002/dstTableVo").remove("id");
        check(checkGraph(filter, newFlow, true).status == 200, "new natural visual IDs pass with no old database keys");
        ObjectNode draft = (ObjectNode)IdentityMappings.JSON.readTree("{\"flowVo\":{\"flowDisplayName\":\"新草稿\",\"folderId\":-1,\"flowType\":0},\"nodes\":{},\"streamVoList\":[],\"flowType\":0}");
        check(checkGraph(filter, draft, true).status == 200, "original empty new draft remains supported");
        ObjectNode mismatch = graph.deepCopy(); mismatch.put("flowId", 2); check(checkGraph(filter, mismatch, true).status == 403, "outer DTO flowId cannot disagree with actual flowVo id");
        ObjectNode zero = graph.deepCopy(); at(zero, "/streamVoList/0").put("id", 0); check(checkGraph(filter, zero, true).status == 403, "zero is not a native new-record placeholder");
        ObjectNode instance = graph.deepCopy(); at(instance, "/nodes/1001/moduleInstanceList/0").put("id", 22); check(checkGraph(filter, instance, true).status == 403, "execution instance must be in mapped workspace");
        ObjectNode adapter = graph.deepCopy(); at(adapter, "/nodes/1001").put("moduleAdapterId", 12); check(checkGraph(filter, adapter, true).status == 403, "adapter id must match original workspace binding and node type");
        ObjectNode folder = graph.deepCopy(); at(folder, "/flowVo").put("folderId", 31); check(checkGraph(filter, folder, true).status == 200, "real same-workspace folder is accepted");
        at(folder, "/flowVo").put("folderId", 32); check(checkGraph(filter, folder, true).status == 403, "missing or unbound folder is rejected");
        ObjectNode unrelated = graph.deepCopy(); at(unrelated, "/nodes/1001").put("adapterName", "jdbcInput"); check(checkGraph(filter, unrelated, true).status == 422, "unverified plugin scope is explicit, not silently allowed");
        ObjectNode remoteFile = graph.deepCopy(); at(remoteFile, "/nodes/1002/adapterData").put("sourceType", "sftp"); check(checkGraph(filter, remoteFile, true).status == 422, "unverified remote file source is explicit");
        ObjectNode datasource = graph.deepCopy(); at(datasource, "/nodes/1001").put("dbId", 22); check(checkGraph(filter, datasource, true).status == 422, "datasource references are not hidden behind generator name");
        ObjectNode ignoredIds = graph.deepCopy(); at(ignoredIds, "/nodes/1001").putArray("streamFieldVoList").addObject().put("id", 777);
        at(ignoredIds, "/nodes/1002").putArray("dstTableFieldVoList").addObject().put("id", 778);
        ignoredIds.putArray("paramVoList").addObject().put("id", 779).put("paramName", "bounded.literal").put("paramValue", "3");
        check(checkGraph(filter, ignoredIds, true).status == 200, "original batch-insert ignored field/param IDs are not mistaken for writable old PKs");
        ObjectNode mappedFields = graph.deepCopy();
        ObjectNode mappingNode = at(mappedFields, "/nodes").putObject("1003");
        mappingNode.put("id", 1003).put("adapterName", "fieldMapping").put("adapterType", "preproc").put("stepType", "preproc").put("moduleAdapterId", 13);
        mappingNode.putArray("moduleInstanceList").addObject().put("id", 21);
        mappingNode.putArray("transformFieldVoList").addObject().put("id", 999).put("itemId", 402).put("fieldSrcName", "label").put("fieldDstName", "renamed");
        check(checkGraph(filter, mappedFields, true).status == 200, "original fieldMapping uses current generated item and ignores copied field PK");
        mappingNode.put("dbId", 22);
        check(checkGraph(filter, mappedFields, true).status == 422, "fieldMapping cannot smuggle an unverified datasource reference");
        ObjectNode sourceParam = graph.deepCopy(); sourceParam.putArray("paramVoList").addObject().put("valueType", "el-select-source").put("paramValue", "{}");
        check(checkGraph(filter, sourceParam, true).status == 422, "typed parameter cannot introduce an unaudited datasource");
        ObjectNode upload = graph.deepCopy(); at(upload, "/nodes/1002").putArray("adapterParamVoList").addObject().put("paramName", "etl.file.isUpload").put("paramValue", "true");
        check(checkGraph(filter, upload, true).status == 422, "file parameter cannot override local-only scope");
        ObjectNode mixedOuter = (ObjectNode)IdentityMappings.JSON.readTree(original); mixedOuter.putObject("flowVo").put("id", 2);
        check(exchange(filter, signed(envelope(IdentityMappings.JSON.writeValueAsBytes(mixedOuter), "save"))).status == 200, "base64 mode validates exactly the decoded DTO the original controller uses");
        String nativeText = new String(original, StandardCharsets.UTF_8);
        byte[] duplicate = (nativeText.substring(0, nativeText.length()-1) + ",\"visualBase64\":\"e30=\"}").getBytes(StandardCharsets.UTF_8);
        check(exchange(filter, signed(envelope(duplicate, "save"))).status == 401, "ambiguous duplicate native fields are rejected before vendor decoder");
        ObjectNode typed = graph.deepCopy(); at(typed, "/nodes/1002/adapterData").put("@type", "java.lang.Runtime");
        check(checkGraph(filter, typed, true).status == 401, "native type metadata is rejected before invoking vendor deserialization");
        check(exchange(filter, signed(envelope(new byte[]{(byte)0xc3, (byte)0x28}, "save"))).status == 401, "native request must be strict UTF8");
        check(exchange(filter, signed(envelope(original, "delete"))).status == 401, "mode does not grant another native operation");
        ObjectNode large = draft.deepCopy(); at(large, "/flowVo").put("addComment", "x".repeat(5000));
        check(checkGraph(filter, large, true).status == 200, "save envelope has its own bound beyond ordinary 4KiB tickets");
        check(exchange(filter, signed(envelope(new byte[NativeSaveAuthorizer.MAX_NATIVE_BYTES + 1], "save"))).status == 401, "decoded native request larger than 8MiB is rejected");
        Request excessive = signed(new byte[NativeSaveAuthorizer.MAX_ENVELOPE_BYTES + 1]);
        check(exchange(filter, excessive).status == 401, "save envelope has a fixed 12MiB bound");
        IdentityBridgeFilter rawFilter = db.filter(child(root, "save-raw-mode"), false);
        check(checkGraph(rawFilter, graph, false).status == 200, "raw mode uses supplied actual Spring MVC message converter");
        NativeSaveAuthorizer noConverter = new NativeSaveAuthorizer(false, db.flows(), db.mapper(WorkspaceMapper.class), db.mapper(SysFolderMapper.class), db.mapper(EtlInstanceDao.class), db.mapper(EtlGroupAdapterDao.class), List::of);
        boolean missingConverter = false;
        try { noConverter.parse(envelope(nativeBody(graph, false), "save"), new IdentityMappings(mapping())); } catch (NativeSaveAuthorizer.Unavailable expected) { missingConverter = true; }
        check(missingConverter, "raw mode without native converter has no fallback parser");
        db.unavailable = true; Response unavailable = checkGraph(filter, graph, true);
        check(unavailable.status == 503 && !unavailable.text.toString().contains("diagnostic"), "save DAO failure never grants or leaks database details");
    }
}
