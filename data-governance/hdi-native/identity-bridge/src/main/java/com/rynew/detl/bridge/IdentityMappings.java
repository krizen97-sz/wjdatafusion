package com.rynew.detl.bridge;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;

final class IdentityMappings {
    static final ObjectMapper JSON = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    static final class Identity {
        final String subject, userName, accountName, realUserName, tenantCode, projectCode, projectName, language;
        final long userId, workspaceId; final int roleType;
        final List<Map<String,String>> projectList;
        Identity(JsonNode n) {
            fields(n, Set.of("subject", "userId", "userName", "accountName", "realUserName", "roleType", "tenantCode", "workspaceId", "projectCode", "projectName", "language", "projectList"));
            subject = text(n, "subject", 48); if (!subject.matches("rynew:user:[1-9][0-9]{0,18}")) throw TicketVerifier.denied();
            userId = positive(n, "userId"); workspaceId = positive(n, "workspaceId");
            if (!n.path("roleType").isInt()) throw TicketVerifier.denied(); roleType = n.path("roleType").intValue();
            if (roleType < 1 || roleType > 5) throw TicketVerifier.denied();
            userName = text(n, "userName", 128); accountName = text(n, "accountName", 128); realUserName = text(n, "realUserName", 128);
            tenantCode = text(n, "tenantCode", 128); projectCode = text(n, "projectCode", 128); projectName = text(n, "projectName", 128); language = text(n, "language", 16);
            if (!Set.of("zh_CN", "en_US").contains(language)) throw TicketVerifier.denied();
            if (!projectCode.matches("[A-Za-z0-9_.:-]{1,128}") || !tenantCode.matches("[A-Za-z0-9_.:-]{1,128}")) throw TicketVerifier.denied();
            JsonNode projects = n.path("projectList"); if (!projects.isArray() || projects.size() < 1 || projects.size() > 100) throw TicketVerifier.denied();
            List<Map<String,String>> list = new ArrayList<>(); Set<String> seen = new HashSet<>();
            for (JsonNode project : projects) {
                fields(project, Set.of("projectCode", "projectName")); String code = text(project, "projectCode", 128), name = text(project, "projectName", 128);
                if (!code.matches("[A-Za-z0-9_.:-]{1,128}") || !seen.add(code)) throw TicketVerifier.denied();
                list.add(Map.of("projectCode", code, "projectName", name));
            }
            if (!seen.contains(projectCode)) throw TicketVerifier.denied(); projectList = List.copyOf(list);
        }
        Map<String,Object> response() {
            return Map.of("user", Map.of("id", userId, "userId", userId, "userName", userName, "accountName", accountName, "realUserName", realUserName, "language", language),
                "projectCode", projectCode, "projectName", projectName, "projectList", projectList);
        }
    }
    private final Map<String, Identity> identities;
    IdentityMappings(byte[] bytes) {
        try {
            JsonNode document = parse(bytes); fields(document, Set.of("schemaVersion", "users"));
            if (!document.path("schemaVersion").isInt() || document.path("schemaVersion").intValue() != 1 || !document.path("users").isArray() || document.path("users").size() > 1000) throw TicketVerifier.denied();
            Map<String, Identity> result = new HashMap<>();
            for (JsonNode user : document.path("users")) { Identity value = new Identity(user); if (result.put(value.subject, value) != null) throw TicketVerifier.denied(); }
            identities = Map.copyOf(result);
        } catch (Exception ignored) { throw new IllegalArgumentException("Identity mapping configuration is invalid"); }
    }
    Identity resolve(byte[] body) {
        try {
            JsonNode request = parse(body); fields(request, Set.of("subject")); String subject = text(request, "subject", 48);
            Identity identity = identities.get(subject); if (identity == null) throw TicketVerifier.denied(); return identity;
        } catch (Exception ignored) { throw TicketVerifier.denied(); }
    }
    static JsonNode parse(byte[] bytes) throws Exception {
        String value = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        return JSON.readTree(value);
    }
    static void fields(JsonNode node, Set<String> allowed) { if (node == null || !node.isObject()) throw TicketVerifier.denied(); node.fieldNames().forEachRemaining(name -> { if (!allowed.contains(name)) throw TicketVerifier.denied(); }); }
    static String text(JsonNode n, String key, int max) { JsonNode value = n.get(key); if (value == null || !value.isTextual()) throw TicketVerifier.denied(); String text = value.textValue(); if (text.isBlank() || !text.equals(text.trim()) || text.length() > max || text.codePoints().anyMatch(Character::isISOControl)) throw TicketVerifier.denied(); return text; }
    static long positive(JsonNode n, String key) { JsonNode value = n.get(key); if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 1) throw TicketVerifier.denied(); return value.longValue(); }
}
