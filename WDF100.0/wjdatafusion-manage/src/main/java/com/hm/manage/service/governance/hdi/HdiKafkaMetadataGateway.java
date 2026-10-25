package com.hm.manage.service.governance.hdi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Kafka metadata runs in the original SDK child with pre-connect endpoint enforcement. */
@Service
public class HdiKafkaMetadataGateway {
    private final HdiCompilerClient compiler;
    public HdiKafkaMetadataGateway(HdiCompilerClient compiler) { this.compiler = compiler; }

    public JsonNode read(String bootstrapServers, JsonNode binding, boolean topics) {
        if (bootstrapServers == null || bootstrapServers.length() > 4096 || !binding.path("kind").asText().equals("kafka")
                || !binding.path("endpoints").isArray() || binding.path("endpoints").isEmpty() || binding.path("endpoints").size() > 16)
            throw new HdiTaskFault(422, "Kafka元数据连接范围无效");
        ObjectNode parameters = HdiProtocol.JSON.createObjectNode().put("action", topics ? "topics" : "test").put("bootstrapServers", bootstrapServers);
        var allowed = parameters.putArray("allowedEndpoints"); Set<String> destinations = new LinkedHashSet<>();
        for (JsonNode endpoint : binding.path("endpoints")) {
            if (!endpoint.path("host").isTextual() || !endpoint.path("port").isInt()) throw new HdiTaskFault(422, "Kafka元数据端点无效");
            String destination = endpoint.path("host").asText() + ":" + endpoint.path("port").intValue();
            if (!destinations.add(destination)) throw new HdiTaskFault(422, "Kafka元数据端点重复");
            allowed.add(destination);
        }
        JsonNode response = compiler.connectionDescription("connectionMetadata", "Kafka", null, parameters).path("metadata");
        if (!response.path("available").asBoolean(false)) {
            String reason = response.path("reason").asText();
            if (reason.equals("METADATA_ENDPOINT_DENIED")) throw new HdiTaskFault(422, "Kafka公布的节点尚未加入平台允许范围，已在连接前阻止访问");
            if (reason.equals("METADATA_GUARD_UNAVAILABLE")) throw new HdiTaskFault(503, "Kafka元数据隔离环境不可用，请配置受支持的Java 11或17");
            throw new HdiTaskFault(422, "Kafka元数据读取失败，请核对连接和允许端点");
        }
        if (!response.path("guardActive").asBoolean(false) || !response.path("nodes").isArray() || response.path("nodes").isEmpty()
                || !response.path("topics").isArray() || response.path("topics").size() > 1000)
            throw new HdiTaskFault(502, "Kafka元数据隔离响应无效");
        for (JsonNode node : response.path("nodes")) if (!node.path("host").isTextual() || !node.path("port").isInt()
                || !destinations.contains(node.path("host").asText() + ":" + node.path("port").intValue()))
            throw new HdiTaskFault(502, "Kafka元数据节点与连接范围不一致");
        for (JsonNode topic : response.path("topics")) if (!topic.isTextual() || topic.asText().isEmpty() || topic.asText().length() > 249)
            throw new HdiTaskFault(502, "Kafka主题元数据无效");
        return response;
    }
}
