package com.hm.manage.service.governance.hdi;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Base64;

/** Original UI envelope and transport encoding; task semantics remain in the original model. */
public final class HdiProtocol {
    public static final ObjectMapper JSON = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    public record Reply(String code, String msg, boolean success, Object data) { }
    private HdiProtocol() { }
    public static void send(HttpServletResponse response, Object data) throws IOException {
        write(response, 200, new Reply("0", null, true, data));
    }
    public static void failure(HttpServletResponse response, HdiTaskFault failure) throws IOException {
        write(response, failure.status(), new Reply(Integer.toString(failure.status()), failure.getMessage(), false, null));
    }
    public static void write(HttpServletResponse response, int status, Object body) throws IOException {
        response.setStatus(status); response.setContentType("application/json;charset=UTF-8"); noCache(response);
        JSON.writeValue(response.getOutputStream(), body);
    }
    public static void noCache(HttpServletResponse response) { response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Content-Type-Options", "nosniff"); }
    public static JsonNode body(HttpServletRequest request, int limit) throws IOException {
        if (request.getContentLengthLong() > limit || request.getHeader("Content-Encoding") != null) throw new HdiTaskFault(413, "任务请求过大或编码不支持");
        byte[] bytes = request.getInputStream().readNBytes(limit + 1);
        if (bytes.length > limit) throw new HdiTaskFault(413, "任务请求超过大小限制");
        if (bytes.length == 0) return JSON.createObjectNode();
        try { return checked(JSON.readTree(bytes)); }
        catch (IOException error) { throw new HdiTaskFault(400, "任务 JSON 格式无效"); }
    }
    public static JsonNode visual(JsonNode body, int limit) {
        if (!body.isObject() || body.size() != 1 || !body.path("visualBase64").isTextual()) throw new HdiTaskFault(400, "请使用原画布的 visualBase64 保存格式");
        try {
            String encoded = body.path("visualBase64").asText();
            if (encoded.length() > limit) throw new HdiTaskFault(413, "任务配置超过大小限制");
            byte[] bytes = Base64.getDecoder().decode(encoded);
            String percent = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
            if (!percent.regionMatches(true, 0, "%7B", 0, 3)) throw new HdiTaskFault(400, "原任务配置需要 URI 编码的 JSON 对象");
            String decoded = decodeUri(percent);
            if (decoded.getBytes(StandardCharsets.UTF_8).length > limit) throw new HdiTaskFault(413, "任务配置超过大小限制");
            JsonNode result = checked(JSON.readTree(decoded));
            if (!result.isObject() || !result.path("flowVo").isObject()) throw new HdiTaskFault(400, "原任务配置缺少 flowVo");
            return result;
        } catch (IOException | IllegalArgumentException error) { throw new HdiTaskFault(400, "原任务配置编码无效"); }
    }
    private static String decodeUri(String value) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '%') {
                if (i + 2 >= value.length()) throw new HdiTaskFault(400, "任务 URI 编码无效");
                int high = Character.digit(value.charAt(++i), 16), low = Character.digit(value.charAt(++i), 16);
                if (high < 0 || low < 0) throw new HdiTaskFault(400, "任务 URI 编码无效");
                output.write((high << 4) | low);
            } else {
                if (c > 127) throw new HdiTaskFault(400, "任务 URI 编码无效");
                output.write(c == '+' ? ' ' : c);
            }
        }
        return StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(output.toByteArray())).toString();
    }
    public static JsonNode checked(JsonNode value) {
        if (value == null) throw new HdiTaskFault(400, "任务 JSON 不能为空");
        ArrayDeque<JsonNode> pending = new ArrayDeque<>(); pending.push(value); int count = 0;
        while (!pending.isEmpty()) {
            JsonNode node = pending.pop();
            if (++count > 150000) throw new HdiTaskFault(413, "任务配置结构超过限制");
            if (node.isObject()) {
                var fields = node.fields();
                while (fields.hasNext()) {
                    var entry = fields.next();
                    if (java.util.Set.of("@type", "@class", "$type").contains(entry.getKey())) throw new HdiTaskFault(400, "任务配置不允许类型元数据");
                    pending.push(entry.getValue());
                }
            } else if (node.isArray()) node.forEach(pending::push);
        }
        return value;
    }
    public static long positive(String value, String label) {
        try { if (value == null || !value.matches("[1-9][0-9]{0,15}")) throw new NumberFormatException(); long id = Long.parseLong(value); if (id > HdiTaskStore.MAX_BROWSER_ID) throw new NumberFormatException(); return id; }
        catch (NumberFormatException error) { throw new HdiTaskFault(400, label + "无效"); }
    }
    public static String query(HttpServletRequest request, String name) {
        String[] values = request.getParameterValues(name);
        if (values == null) return null;
        if (values.length != 1 || values[0].length() > 2048) throw new HdiTaskFault(400, "任务查询参数无效");
        return values[0];
    }
}
