package com.rynew.detl.bridge;

import com.fasterxml.jackson.databind.JsonNode;
import com.hikvision.idatafusion.detl.mapper.EtlFlowMapper;
import com.hikvision.idatafusion.detl.model.EtlFlow;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Additional platform scope constraint. Original task permissions still apply afterwards. */
final class NativeFlowAuthorizer {
    static final int MAX_FLOW_IDS = 100;
    private final EtlFlowMapper mapper;

    NativeFlowAuthorizer(EtlFlowMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "Original flow mapper is required");
    }

    static final class ScopeRequest {
        final IdentityMappings.Identity identity;
        final List<Long> flowIds;
        ScopeRequest(IdentityMappings.Identity identity, List<Long> flowIds) {
            this.identity = identity;
            this.flowIds = List.copyOf(flowIds);
        }
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
                if (!value.isTextual() || !value.textValue().matches("[1-9][0-9]{0,18}")) throw TicketVerifier.denied();
                long id = Long.parseLong(value.textValue());
                if (id < 1 || !unique.add(id)) throw TicketVerifier.denied();
                ids.add(id);
            }
            return new ScopeRequest(identity, ids);
        } catch (Exception invalid) { throw TicketVerifier.denied(); }
    }

    void requireScope(ScopeRequest request) {
        for (Long id : request.flowIds) {
            EtlFlow flow = mapper.selectByPrimaryKey(id);
            if (flow == null || !Objects.equals(flow.getGroupId(), request.identity.workspaceId)) throw new ScopeDenied();
        }
    }

    static final class ScopeDenied extends SecurityException {
        ScopeDenied() { super("Native task scope denied"); }
    }
}
