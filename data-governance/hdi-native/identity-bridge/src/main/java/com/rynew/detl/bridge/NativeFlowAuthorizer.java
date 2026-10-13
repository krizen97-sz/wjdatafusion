package com.rynew.detl.bridge;

import com.fasterxml.jackson.databind.JsonNode;
import com.hikvision.idatafusion.detl.mapper.*;
import com.hikvision.idatafusion.detl.model.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Objects;
import java.util.Set;

/** Additional platform scope constraint. Original task permissions still apply afterwards. */
final class NativeFlowAuthorizer {
    static final int MAX_FLOW_IDS = 100;
    static final int MAX_REFERENCES = 512;
    private final EtlFlowMapper mapper;
    private final EtlStreamDao streams;
    private final EtlStepDao steps;
    private final EtlStepItemDao items;
    private final EtlModuleDao modules;
    private final EtlDstTableDao tables;

    NativeFlowAuthorizer(EtlFlowMapper mapper, EtlStreamDao streams, EtlStepDao steps, EtlStepItemDao items, EtlModuleDao modules, EtlDstTableDao tables) {
        this.mapper = Objects.requireNonNull(mapper, "Original flow mapper is required");
        this.streams = Objects.requireNonNull(streams); this.steps = Objects.requireNonNull(steps);
        this.items = Objects.requireNonNull(items); this.modules = Objects.requireNonNull(modules); this.tables = Objects.requireNonNull(tables);
    }

    static final class ScopeRequest {
        final IdentityMappings.Identity identity;
        final List<Long> flowIds;
        final List<Reference> references;
        ScopeRequest(IdentityMappings.Identity identity, List<Long> flowIds, List<Reference> references) {
            this.identity = identity;
            this.flowIds = List.copyOf(flowIds);
            this.references = List.copyOf(references);
        }
    }
    static final class Reference {
        final String kind; final long id, flowId;
        Reference(String kind, long id, long flowId) { this.kind = kind; this.id = id; this.flowId = flowId; }
    }

    static ScopeRequest parse(byte[] body, IdentityMappings mappings) {
        try {
            JsonNode document = IdentityMappings.parse(body);
            IdentityMappings.fields(document, Set.of("subject", "flowIds"));
            IdentityMappings.Identity identity = mappings.resolveSubject(IdentityMappings.text(document, "subject", 48));
            JsonNode values = document.get("flowIds");
            if (values == null || !values.isArray() || values.size() < 1 || values.size() > MAX_FLOW_IDS) throw TicketVerifier.denied();
            List<Long> ids = new ArrayList<>();
            Set<Long> unique = new HashSet<>();
            for (JsonNode value : values) {
                long id = positiveId(value);
                if (!unique.add(id)) throw TicketVerifier.denied();
                ids.add(id);
            }
            return new ScopeRequest(identity, ids, List.of());
        } catch (Exception invalid) { throw TicketVerifier.denied(); }
    }

    private static long positiveId(JsonNode value) {
        if (value == null || !value.isTextual() || !value.textValue().matches("[1-9][0-9]{0,18}")) throw TicketVerifier.denied();
        long id = Long.parseLong(value.textValue());
        if (id < 1) throw TicketVerifier.denied();
        return id;
    }

    List<Map<String,Object>> requireScope(ScopeRequest request) {
        List<Map<String,Object>> bindings = new ArrayList<>();
        for (Long id : request.flowIds) {
            EtlFlow flow = mapper.selectByPrimaryKey(id);
            if (flow == null || !Objects.equals(flow.getGroupId(), request.identity.workspaceId)) throw new ScopeDenied();
            Map<String,Object> binding = new LinkedHashMap<>();
            binding.put("id", Long.toString(id)); binding.put("workspaceId", Long.toString(flow.getGroupId()));
            binding.put("ownerId", flow.getUserId() == null ? null : Long.toString(flow.getUserId()));
            binding.put("tenantCode", flow.getTenantCode()); binding.put("projectCode", flow.getProjectCode());
            bindings.add(binding);
        }
        Map<Long,EtlStream> streamCache = new HashMap<>();
        Map<Long,EtlStep> stepCache = new HashMap<>();
        Map<Long,EtlStepItem> itemCache = new HashMap<>();
        for (Reference reference : request.references) {
            Long parent;
            switch (reference.kind) {
                case "stream": parent = streamFlow(reference.id, streamCache); break;
                case "step": parent = stepFlow(reference.id, streamCache, stepCache); break;
                case "item": parent = itemFlow(reference.id, streamCache, stepCache, itemCache); break;
                case "module":
                    EtlModule module = modules.selectByPrimaryKey(reference.id);
                    if (module == null) throw new ScopeDenied();
                    parent = module.getFlowId();
                    if (!Objects.equals(parent, itemFlow(module.getItemId(), streamCache, stepCache, itemCache))) throw new ScopeDenied();
                    break;
                case "dstTable":
                    EtlDstTable table = tables.selectByPrimaryKey(reference.id);
                    if (table == null) throw new ScopeDenied();
                    parent = streamFlow(table.getStreamId(), streamCache);
                    if (!Objects.equals(parent, itemFlow(table.getItemId(), streamCache, stepCache, itemCache))) throw new ScopeDenied();
                    break;
                default: throw TicketVerifier.denied();
            }
            if (!Objects.equals(parent, reference.flowId)) throw new ScopeDenied();
        }
        return bindings;
    }
    private Long streamFlow(Long id, Map<Long,EtlStream> cache) {
        if (id == null) throw new ScopeDenied();
        EtlStream stream = cache.computeIfAbsent(id, streams::selectByPrimaryKey);
        if (stream == null || !Integer.valueOf(2).equals(stream.getUpNodeType())) throw new ScopeDenied();
        return stream.getUpNodeId();
    }
    private Long stepFlow(Long id, Map<Long,EtlStream> streamsCache, Map<Long,EtlStep> cache) {
        if (id == null) throw new ScopeDenied();
        EtlStep step = cache.computeIfAbsent(id, steps::selectByPrimaryKey);
        if (step == null || !Integer.valueOf(3).equals(step.getNodeType())) throw new ScopeDenied();
        return streamFlow(step.getNodeId(), streamsCache);
    }
    private Long itemFlow(Long id, Map<Long,EtlStream> streamsCache, Map<Long,EtlStep> stepsCache, Map<Long,EtlStepItem> cache) {
        if (id == null) throw new ScopeDenied();
        EtlStepItem item = cache.computeIfAbsent(id, items::selectByPrimaryKey);
        if (item == null) throw new ScopeDenied();
        return stepFlow(item.getStepId(), streamsCache, stepsCache);
    }

    static final class ScopeDenied extends SecurityException {
        ScopeDenied() { super("Native task scope denied"); }
    }
}
