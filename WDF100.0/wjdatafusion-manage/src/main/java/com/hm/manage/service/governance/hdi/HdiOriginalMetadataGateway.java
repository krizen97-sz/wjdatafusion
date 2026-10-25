package com.hm.manage.service.governance.hdi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Additional original datasource clients execute only inside the guarded, one-shot SDK JVM. */
@Service
public class HdiOriginalMetadataGateway {
    private final HdiCompilerClient compiler;
    public HdiOriginalMetadataGateway(HdiCompilerClient compiler) { this.compiler = compiler; }

    public JsonNode read(String type, JsonNode datasource, JsonNode binding, String action, JsonNode query) {
        Map<String,Integer> types = Map.of("MySQL", 2, "RabbitMQ", 120, "FTP", 18);
        if (!types.containsKey(type) || !datasource.isObject() || datasource.path("dbType").asInt() != types.get(type)
                || !Set.of("test", "schemas", "tables", "columns").contains(action) || !type.equals("MySQL") && !action.equals("test"))
            throw new HdiTaskFault(422, "原数据源元数据操作无效");
        String kind = type.equals("MySQL") ? "mysql" : type.equals("RabbitMQ") ? "rabbitmq" : "sftp";
        if (!kind.equals(binding.path("kind").asText()) || !binding.path("endpoints").isArray()
                || binding.path("endpoints").isEmpty() || binding.path("endpoints").size() > 16)
            throw new HdiTaskFault(422, "连接元数据范围无效");
        ObjectNode parameters = HdiProtocol.JSON.createObjectNode().put("action", action);
        parameters.set("datasource", datasource); parameters.set("query", query == null ? HdiProtocol.JSON.createObjectNode() : query);
        var allowed = parameters.putArray("allowedEndpoints"); Set<String> destinations = new LinkedHashSet<>();
        for (JsonNode endpoint : binding.path("endpoints")) {
            if (!endpoint.path("host").isTextual() || !endpoint.path("port").isInt()) throw new HdiTaskFault(422, "元数据端点无效");
            String destination = endpoint.path("host").asText() + ":" + endpoint.path("port").asInt();
            if (!destinations.add(destination)) throw new HdiTaskFault(422, "元数据端点重复"); allowed.add(destination);
        }
        JsonNode response = compiler.connectionDescription("connectionMetadata", type, null, parameters).path("metadata");
        if (!response.path("available").asBoolean(false)) {
            String reason = response.path("reason").asText();
            if (reason.equals("METADATA_ENDPOINT_DENIED")) throw new HdiTaskFault(422, "元数据访问超出已批准连接范围，已在连接前阻止");
            if (reason.equals("METADATA_GUARD_UNAVAILABLE")) throw new HdiTaskFault(503, "原元数据隔离环境不可用，请核对Java11或17配置");
            throw new HdiTaskFault(422, type + "原连接测试或元数据读取失败，请核对账号、服务和允许端点");
        }
        if (!response.path("guardActive").asBoolean(false)) throw new HdiTaskFault(502, "原元数据隔离响应无效");
        if (action.equals("schemas") && (!response.path("schemas").isArray() || response.path("schemas").size() > 200)
                || action.equals("tables") && (!response.path("tables").isArray() || response.path("tables").size() > 1000)
                || action.equals("columns") && (!response.path("columns").isArray() || response.path("columns").size() > 512 || !response.path("primaryKeys").isArray()))
            throw new HdiTaskFault(502, "原元数据返回结构无效");
        return response;
    }
}
