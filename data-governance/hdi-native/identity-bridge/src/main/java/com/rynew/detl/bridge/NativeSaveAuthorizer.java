package com.rynew.detl.bridge;

import com.fasterxml.jackson.databind.JsonNode;
import com.hikvision.idatafusion.detl.dto.task.*;
import com.hikvision.idatafusion.detl.mapper.*;
import com.hikvision.idatafusion.detl.model.*;
import com.hikvision.idatafusion.detl.utils.ObjectUtils;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;

/** Checks the original save DTO and referenced rows; never calls a save, interpreter, or executor. */
final class NativeSaveAuthorizer {
    static final int MAX_NATIVE_BYTES = 8 * 1024 * 1024;
    static final int MAX_ENVELOPE_BYTES = 12 * 1024 * 1024;
    private final boolean base64Visual;
    private final NativeFlowAuthorizer flows;
    private final WorkspaceMapper workspaces;
    private final SysFolderMapper folders;
    private final EtlInstanceDao instances;
    private final EtlGroupAdapterDao adapters;
    private final Supplier<List<HttpMessageConverter<?>>> converters;

    NativeSaveAuthorizer(boolean base64Visual, NativeFlowAuthorizer flows, WorkspaceMapper workspaces,
            SysFolderMapper folders, EtlInstanceDao instances, EtlGroupAdapterDao adapters,
            Supplier<List<HttpMessageConverter<?>>> converters) {
        this.base64Visual = base64Visual; this.flows = Objects.requireNonNull(flows);
        this.workspaces = Objects.requireNonNull(workspaces); this.folders = Objects.requireNonNull(folders);
        this.instances = Objects.requireNonNull(instances); this.adapters = Objects.requireNonNull(adapters);
        this.converters = Objects.requireNonNull(converters);
    }

    static final class SaveRequest {
        final IdentityMappings.Identity identity;
        final String mode;
        final FlowVisualVo visual;
        SaveRequest(IdentityMappings.Identity identity, String mode, FlowVisualVo visual) {
            this.identity = identity; this.mode = mode; this.visual = visual;
        }
    }

    SaveRequest parse(byte[] body, IdentityMappings mappings) {
        try {
            JsonNode envelope = IdentityMappings.parse(body);
            IdentityMappings.fields(envelope, Set.of("subject", "mode", "nativeBodyBase64"));
            IdentityMappings.Identity identity = mappings.resolveSubject(IdentityMappings.text(envelope, "subject", 48));
            String mode = IdentityMappings.text(envelope, "mode", 8);
            if (!Set.of("save", "push").contains(mode)) throw TicketVerifier.denied();
            String encoded = IdentityMappings.text(envelope, "nativeBodyBase64", 4 * ((MAX_NATIVE_BYTES + 2) / 3));
            byte[] raw = Base64.getDecoder().decode(encoded);
            if (raw.length == 0 || raw.length > MAX_NATIVE_BYTES || !Base64.getEncoder().encodeToString(raw).equals(encoded)) throw TicketVerifier.denied();
            JsonNode nativeBody = IdentityMappings.parse(raw);
            if (!nativeBody.isObject()) throw TicketVerifier.denied();
            rejectTypeMetadata(nativeBody);
            FlowVisualVo visual;
            if (base64Visual) {
                JsonNode packed = nativeBody.get("visualBase64");
                if (packed == null || !packed.isTextual() || packed.textValue().isEmpty()) throw TicketVerifier.denied();
                byte[] uriBytes = Base64.getDecoder().decode(packed.textValue());
                String uri = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(uriBytes)).toString();
                String json = URLDecoder.decode(uri, "UTF-8");
                // Reject ambiguous duplicate keys/malformed UTF-8 before the actual original decoder.
                JsonNode decodedJson = IdentityMappings.parse(json.getBytes(StandardCharsets.UTF_8));
                if (!decodedJson.isObject()) throw TicketVerifier.denied();
                rejectTypeMetadata(decodedJson);
                visual = ObjectUtils.getDecodeFlowVisual(packed.textValue());
            } else {
                visual = readThroughOriginalMvc(raw);
            }
            if (visual == null || visual.getFlowVo() == null) throw TicketVerifier.denied();
            return new SaveRequest(identity, mode, visual);
        } catch (Unavailable unavailable) { throw unavailable; }
        catch (Exception invalid) { throw TicketVerifier.denied(); }
    }

    @SuppressWarnings("unchecked")
    private FlowVisualVo readThroughOriginalMvc(byte[] raw) throws Exception {
        List<HttpMessageConverter<?>> original;
        try { original = converters.get(); } catch (Exception unavailable) { throw new Unavailable(); }
        if (original == null) throw new Unavailable();
        for (HttpMessageConverter<?> converter : original) {
            if (converter.canRead(FlowVisualVo.class, MediaType.APPLICATION_JSON)) {
                HttpInputMessage input = new HttpInputMessage() {
                    public java.io.InputStream getBody() { return new java.io.ByteArrayInputStream(raw); }
                    public HttpHeaders getHeaders() {
                        HttpHeaders headers = new HttpHeaders(); headers.setContentType(new MediaType("application", "json", StandardCharsets.UTF_8)); return headers;
                    }
                };
                return ((HttpMessageConverter<FlowVisualVo>)converter).read(FlowVisualVo.class, input);
            }
        }
        throw new Unavailable();
    }

    void requireScope(SaveRequest request) {
        IdentityMappings.Identity identity = request.identity;
        FlowVisualVo visual = request.visual;
        FlowVo flow = visual.getFlowVo();
        Long flowId = flow.getId();
        requirePositiveOrNull(flowId);
        if (visual.getFlowId() != null && !Objects.equals(visual.getFlowId(), flowId)) throw new NativeFlowAuthorizer.ScopeDenied();
        if (flow.getGroupId() != null && !Objects.equals(flow.getGroupId(), identity.workspaceId)) throw new NativeFlowAuthorizer.ScopeDenied();
        EtlGroup workspace = workspaces.selectByPrimaryKey(identity.workspaceId);
        if (workspace == null || !Integer.valueOf(1).equals(workspace.getEnableFlag())) throw new NativeFlowAuthorizer.ScopeDenied();
        if ("iDataFusion DFP".equals(workspace.getProduct())) throw new UnsupportedGraph();
        if ((flow.getFlowType() != null && flow.getFlowType() != 0) || (visual.getFlowType() != null && visual.getFlowType() != 0)) throw new UnsupportedGraph();
        List<NativeFlowAuthorizer.Reference> references = new ArrayList<>();
        for (StreamVo stream : safe(visual.getStreamVoList())) {
            if (stream == null) throw TicketVerifier.denied();
            addReference(references, "stream", stream.getId(), flowId);
            if (stream.getExecDatasource() != null || present(stream.getDirectSql())) throw new UnsupportedGraph();
            for (StreamNodeVo node : safe(stream.getStreamNodeVoList())) {
                if (node == null) throw TicketVerifier.denied();
                addReference(references, "step", node.getStepId(), flowId);
                addReference(references, "item", node.getItemId(), flowId);
                for (StreamNodeModuleVo module : safe(node.getStreamNodeModuleVoList())) {
                    if (module == null) throw TicketVerifier.denied();
                    addReference(references, "module", module.getModuleId(), flowId);
                }
            }
        }
        Map<String,NodeVo> nodes = visual.getNodes() == null ? Map.of() : visual.getNodes();
        if (nodes.size() > 128) throw new UnsupportedGraph();
        for (Map.Entry<String,NodeVo> entry : nodes.entrySet()) {
            NodeVo node = entry.getValue();
            if (node == null || node.getId() == null || !entry.getKey().equals(node.getId().toString())) throw TicketVerifier.denied();
            String type;
            if ("dataGeneratorInput".equals(node.getAdapterName())) type = "input";
            else if ("fileOutput".equals(node.getAdapterName())) type = "output";
            else throw new UnsupportedGraph();
            if (!type.equals(node.getAdapterType()) || !type.equals(node.getStepType())) throw new NativeFlowAuthorizer.ScopeDenied();
            if (node.getDbId() != null || !safe(node.getSrcTableUseVoList()).isEmpty() || present(node.getSqlStr())
                    || node.getLookupProcessVo() != null || "sql".equalsIgnoreCase(node.getTableConfigType())
                    || "lifecycle".equalsIgnoreCase(node.getTableConfigType()) || !safe(node.getDatasourceParamVoList()).isEmpty()) throw new UnsupportedGraph();
            if (present(node.getReferenceConfigAdapter()) && !node.getAdapterName().equals(node.getReferenceConfigAdapter())) throw new UnsupportedGraph();
            requirePositiveOrNull(node.getModuleAdapterId());
            if (node.getModuleAdapterId() == null) throw new NativeFlowAuthorizer.ScopeDenied();
            EtlAdapter adapter = adapters.getAdapterInGroupById(identity.workspaceId, node.getModuleAdapterId());
            if (adapter == null || !node.getAdapterName().equals(adapter.getAdapterName()) || !type.equals(adapter.getAdapterType())) throw new NativeFlowAuthorizer.ScopeDenied();
            for (InstanceModuleVo instance : safe(node.getModuleInstanceList())) {
                if (instance == null || instance.getId() == null || instance.getId() < 1) throw TicketVerifier.denied();
                EtlInstance row = instances.selectByPrimaryKey(instance.getId());
                if (row == null || !Objects.equals(row.getGroupId(), identity.workspaceId) || !Integer.valueOf(1).equals(row.getEnableFlag())) throw new NativeFlowAuthorizer.ScopeDenied();
            }
            if (node.getDstTableVo() != null) {
                DstTableVo table = node.getDstTableVo();
                addReference(references, "dstTable", table.getId(), flowId);
                if (table.getDbId() != null || present(table.getDatasourceIdStr()) || present(table.getAddCreateSql()) || present(table.getAvroSchemaUrl())) throw new UnsupportedGraph();
            }
            if ("fileOutput".equals(node.getAdapterName()) && node.getAdapterData() != null) {
                JsonNode config = IdentityMappings.JSON.valueToTree(node.getAdapterData());
                if (!config.isObject() || !"local".equals(config.path("sourceType").asText()) || hasReference(config.get("dbId"))) throw new UnsupportedGraph();
            }
            checkParams(node.getAdapterParamVoList()); checkParams(node.getTableParamVoList());
            if ("fileOutput".equals(node.getAdapterName())) for (ParamVo param : safe(node.getAdapterParamVoList())) {
                if ("etl.file.isUpload".equals(param.getParamName()) && !"false".equalsIgnoreCase(param.getParamValue())) throw new UnsupportedGraph();
            }
        }
        checkParams(visual.getParamVoList());
        String project = identity.projectCode;
        if (flowId == null) {
            if (flow.getUserId() != null && flow.getUserId() != identity.userId) throw new NativeFlowAuthorizer.ScopeDenied();
            equalIfPresent(flow.getTenantCode(), identity.tenantCode); equalIfPresent(flow.getProjectCode(), identity.projectCode);
        } else {
            Map<String,Object> binding = flows.requireScope(new NativeFlowAuthorizer.ScopeRequest(identity, List.of(flowId), references)).get(0);
            if (flow.getUserId() != null && !Objects.equals(flow.getUserId().toString(), binding.get("ownerId"))) throw new NativeFlowAuthorizer.ScopeDenied();
            equalIfPresent(flow.getTenantCode(), binding.get("tenantCode")); equalIfPresent(flow.getProjectCode(), binding.get("projectCode"));
            project = (String)binding.get("projectCode");
        }
        Long folderId = flow.getFolderId();
        if (folderId != null && folderId != -1) {
            requirePositiveOrNull(folderId);
            SysFolder folder = folders.selectByPrimaryKey(folderId);
            if (folder == null || !Objects.equals(folder.getWorkspaceId(), identity.workspaceId)
                    || !Objects.equals(folder.getProjectCode(), project) || !Integer.valueOf(1).equals(folder.getEnableFlag())) throw new NativeFlowAuthorizer.ScopeDenied();
        }
    }

    private static void addReference(List<NativeFlowAuthorizer.Reference> references, String kind, Long id, Long flowId) {
        requirePositiveOrNull(id);
        if (id == null) return;
        if (flowId == null || references.size() >= NativeFlowAuthorizer.MAX_REFERENCES) throw new NativeFlowAuthorizer.ScopeDenied();
        references.add(new NativeFlowAuthorizer.Reference(kind, id, flowId));
    }
    private static void requirePositiveOrNull(Long id) { if (id != null && id < 1) throw new NativeFlowAuthorizer.ScopeDenied(); }
    private static void rejectTypeMetadata(JsonNode document) {
        Deque<JsonNode> pending = new ArrayDeque<>(); pending.add(document);
        while (!pending.isEmpty()) {
            JsonNode node = pending.removeFirst();
            if (node.isObject() && node.has("@type")) throw TicketVerifier.denied();
            if (node.isContainerNode()) node.forEach(pending::addLast);
        }
    }
    private static void equalIfPresent(Object value, Object expected) { if (value != null && !Objects.equals(value, expected)) throw new NativeFlowAuthorizer.ScopeDenied(); }
    private static boolean present(String value) { return value != null && !value.isEmpty(); }
    private static boolean hasReference(JsonNode value) { return value != null && !value.isNull() && !(value.isTextual() && value.textValue().isEmpty()); }
    private static <T> List<T> safe(List<T> values) { return values == null ? List.of() : values; }
    private static void checkParams(List<ParamVo> params) {
        for (ParamVo param : safe(params)) {
            if (param == null) throw TicketVerifier.denied();
            if ("el-select-source".equalsIgnoreCase(param.getValueType()) || "el-input-fel".equalsIgnoreCase(param.getValueType()) || present(param.getGlobalParamKey())) throw new UnsupportedGraph();
        }
    }
    static final class UnsupportedGraph extends RuntimeException { }
    static final class Unavailable extends RuntimeException { }
}
