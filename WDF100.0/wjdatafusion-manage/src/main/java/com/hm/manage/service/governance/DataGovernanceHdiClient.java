package com.hm.manage.service.governance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

/** Transport/identity adapter only; workflow payloads and plugins remain original. */
@Component
public class DataGovernanceHdiClient
{
    public static final String EXCHANGE_PATH = "/detl/_platform/session";
    private static final int MAX_RESPONSE = 256 * 1024;
    private final DataGovernanceHdiProperties properties;
    private final ObjectMapper mapper = new ObjectMapper();
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    @Autowired
    public DataGovernanceHdiClient(DataGovernanceHdiProperties properties)
    { this(properties, Clock.systemUTC()); }

    DataGovernanceHdiClient(DataGovernanceHdiProperties properties, Clock clock)
    { this.properties = properties; this.clock = clock; }

    /** Cookies stay on the server. Never serialize this result directly to a browser or log. */
    public static final class NativeSession
    {
        private final String cookieHeader;
        private final Map<String, Object> context;
        NativeSession(String cookieHeader, Map<String, Object> context)
        { this.cookieHeader = cookieHeader; this.context = context; }
        public String cookieHeader() { return cookieHeader; }
        public Map<String, Object> context() { return context; }
        @Override public String toString() { return "HDI native session [private]"; }
    }

    /** Subject is derived by the platform from its authenticated LoginUser, never request JSON. */
    public NativeSession exchange(long platformUserId) throws IOException
    {
        if (!properties.isEnabled()) throw new IOException("HDI 任务模块尚未启用");
        if (platformUserId <= 0) throw new IOException("平台用户无效");
        URI base = localServiceUri(properties.getTaskServiceUrl());
        byte[] key = privateKey(Path.of(properties.getIdentityKeyFile()));
        byte[] body = mapper.writeValueAsBytes(Map.of("subject", "rynew:user:" + platformUserId));
        byte[] nonceBytes = new byte[32]; random.nextBytes(nonceBytes);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);
        String timestamp = Long.toString(clock.millis());
        HttpURLConnection connection = (HttpURLConnection) base.resolve(EXCHANGE_PATH).toURL().openConnection(Proxy.NO_PROXY);
        try
        {
            connection.setConnectTimeout(5000); connection.setReadTimeout(15000);
            connection.setInstanceFollowRedirects(false); connection.setRequestMethod("POST");
            connection.setDoOutput(true); connection.setFixedLengthStreamingMode(body.length);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("X-RYNEW-Timestamp", timestamp);
            connection.setRequestProperty("X-RYNEW-Nonce", nonce);
            connection.setRequestProperty("X-RYNEW-Signature", sign(key, "POST", EXCHANGE_PATH, timestamp, nonce, body));
            try (var output = connection.getOutputStream()) { output.write(body); }
            int status = connection.getResponseCode();
            if (status != 200) throw new IOException("HDI 原任务服务拒绝身份交换（HTTP " + status + "）");
            JsonNode root;
            try (InputStream input = connection.getInputStream())
            {
                byte[] response = input.readNBytes(MAX_RESPONSE + 1);
                if (response.length > MAX_RESPONSE) throw new IOException("HDI 身份响应超过限制");
                root = mapper.readTree(response);
            }
            if (root == null || !"0".equals(root.path("code").asText()) || !root.path("data").isObject())
                throw new IOException("HDI 原任务服务未确认身份交换");
            JsonNode data = root.path("data");
            if (!data.path("user").isObject() || data.path("projectCode").asText().isBlank()
                    || !data.path("projectList").isArray()) throw new IOException("HDI 原任务域映射不完整");
            Map<String, Object> context = new LinkedHashMap<>();
            for (String name : List.of("user", "projectCode", "projectName", "projectList", "workspaceId", "locale"))
                if (data.has(name)) context.put(name, mapper.convertValue(data.get(name), Object.class));
            List<String> cookies = new ArrayList<>();
            for (var header : connection.getHeaderFields().entrySet())
                if ("Set-Cookie".equalsIgnoreCase(header.getKey()))
                    for (String value : header.getValue())
                        for (HttpCookie cookie : HttpCookie.parse(value))
                        {
                            if (!List.of("SESSION", "JSESSIONID").contains(cookie.getName())) continue;
                            if (cookie.getMaxAge() == 0 || !cookie.getValue().matches("[A-Za-z0-9_+=./-]{1,4096}"))
                                throw new IOException("HDI 原会话无效");
                            cookies.add(cookie.getName() + "=" + cookie.getValue());
                        }
            if (cookies.isEmpty() || cookies.size() > 2) throw new IOException("HDI 未建立原会话");
            return new NativeSession(String.join("; ", cookies), context);
        }
        catch (IllegalArgumentException | GeneralSecurityException ex)
        { throw new IOException("HDI 身份交换协议无效"); }
        catch (JsonProcessingException ex)
        { throw new IOException("HDI 身份交换响应格式无效"); }
        finally { java.util.Arrays.fill(key, (byte) 0); connection.disconnect(); }
    }

    static URI localServiceUri(String value) throws IOException
    {
        try
        {
            URI uri = URI.create(value);
            if (!"http".equals(uri.getScheme()) || !"127.0.0.1".equals(uri.getHost())
                    || uri.getPort() < 1024 || uri.getPort() > 65535 || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || !List.of("/detl", "/detl/").contains(uri.getRawPath()))
                throw new IllegalArgumentException();
            return uri;
        }
        catch (IllegalArgumentException ex) { throw new IOException("HDI 任务服务必须使用本机回环地址和 /detl 路径"); }
    }

    static byte[] privateKey(Path configured) throws IOException
    {
        Path path = configured.toAbsolutePath().normalize();
        for (Path part = path; part != null; part = part.getParent())
            if (Files.isSymbolicLink(part)) throw new IOException("HDI 身份密钥不能使用符号链接");
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) < 32 || Files.size(path) > 64)
            throw new IOException("HDI 身份密钥必须为独立的 32–64 字节私有文件");
        try
        {
            for (PosixFilePermission permission : Files.getPosixFilePermissions(path))
                if (permission.name().startsWith("GROUP_") || permission.name().startsWith("OTHERS_"))
                    throw new IOException("HDI 身份密钥文件权限过宽");
        }
        catch (UnsupportedOperationException ex) { throw new IOException("当前系统无法验证 HDI 密钥私有权限"); }
        return Files.readAllBytes(path);
    }

    static String sign(byte[] key, String method, String path, String timestamp, String nonce, byte[] body)
            throws GeneralSecurityException
    {
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        String canonical = String.join("\n", method, path, timestamp, nonce, digest);
        Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
    }
}
