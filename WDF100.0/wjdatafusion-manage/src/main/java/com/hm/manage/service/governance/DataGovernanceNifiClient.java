package com.hm.manage.service.governance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hm.common.exception.ServiceException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.time.Instant;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.springframework.stereotype.Component;

/** Fixed administrator endpoint only. Redirects and global TLS overrides are deliberately absent. */
@Component
public class DataGovernanceNifiClient
{
    public static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    public static final long MAX_CONTENT_BYTES = 8L * 1024 * 1024;
    private final DataGovernanceProperties properties;
    private final ObjectMapper mapper = new ObjectMapper();
    private volatile String token;
    private volatile Instant tokenExpiry = Instant.EPOCH;

    public DataGovernanceNifiClient(DataGovernanceProperties properties) { this.properties = properties; }

    public boolean configured()
    {
        try { endpoint(); return !properties.getNifi().getRootGroupId().isBlank(); }
        catch (Exception ignored) { return false; }
    }

    public JsonNode json(String method, String path, Object body)
    {
        byte[] request;
        try { request = body == null ? null : mapper.writeValueAsBytes(body); }
        catch (Exception e) { throw new ServiceException("NiFi 请求编码失败"); }
        byte[] bytes = exchange(method, path, request, "application/json", true);
        try { return bytes.length == 0 ? mapper.createObjectNode() : mapper.readTree(bytes); }
        catch (Exception e) { throw new ServiceException("NiFi 返回了无效 JSON"); }
    }

    public String content(String path)
    {
        return new String(exchange("GET", path, null, "application/json", true), StandardCharsets.UTF_8);
    }

    /** Stream complete bytes; this is deliberately separate from the bounded UI preview. */
    public long transferContent(String path, OutputStream output, long maximumBytes)
    { return transferContent(path, output, maximumBytes, () -> { }); }

    public long transferContent(String path, OutputStream output, long maximumBytes, Runnable checkpoint)
    {
        if (maximumBytes < 0 || maximumBytes > MAX_CONTENT_BYTES) throw new ServiceException("完整产物大小限制无效");
        HttpURLConnection connection = null;
        try
        {
            checkpoint.run(); connection = open("GET", path, null, "application/json", true);
            long declared = connection.getContentLengthLong();
            if (declared > maximumBytes) throw new ServiceException("完整产物超过大小上限");
            long total = 0;
            try (InputStream in = connection.getInputStream())
            {
                byte[] buffer = new byte[8192]; int count;
                while ((count = in.read(buffer)) != -1)
                {
                    checkpoint.run();
                    if (count > maximumBytes - total) throw new ServiceException("完整产物超过大小上限");
                    output.write(buffer, 0, count); total += count;
                }
            }
            if (declared >= 0 && total != declared) throw new ServiceException("NiFi 产物传输不完整");
            return total;
        }
        catch (RuntimeException e) { throw e; }
        catch (Exception e) { checkpoint.run(); throw new ServiceException("NiFi 完整产物读取失败或中断"); }
        finally { if (connection != null) connection.disconnect(); }
    }

    public String contentPreview(String path, int maximumCharacters, Runnable checkpoint)
    {
        if (maximumCharacters < 1 || maximumCharacters > 8192) throw new ServiceException("预览长度限制无效");
        HttpURLConnection connection = null;
        try
        {
            checkpoint.run(); connection = open("GET", path, null, "application/json", true);
            StringBuilder result = new StringBuilder(maximumCharacters + 1);
            try (var reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))
            {
                char[] buffer = new char[2048];
                while (result.length() <= maximumCharacters)
                {
                    checkpoint.run();
                    int count = reader.read(buffer, 0, Math.min(buffer.length, maximumCharacters + 1 - result.length()));
                    if (count == -1) break;
                    result.append(buffer, 0, count);
                }
            }
            if (result.length() <= maximumCharacters) return result.toString();
            int end = maximumCharacters;
            if (Character.isHighSurrogate(result.charAt(end - 1))) end--;
            return result.substring(0, end) + "\n[样本预览已截断]";
        }
        catch (RuntimeException e) { throw e; }
        catch (Exception e) { checkpoint.run(); throw new ServiceException("NiFi 产物预览读取失败"); }
        finally { if (connection != null) connection.disconnect(); }
    }

    private URI endpoint()
    {
        String value = properties.getNifi().getBaseUrl();
        if (value.isBlank()) value = credentials().path("baseUrl").asText("");
        URI uri = URI.create(value.replaceAll("/+$", ""));
        if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
            || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())))
            throw new ServiceException("尚未配置有效的 NiFi 服务地址");
        return uri;
    }

    private JsonNode credentials()
    {
        String file = properties.getNifi().getCredentialsFile();
        if (file.isBlank()) return mapper.createObjectNode();
        try
        {
            Path path = Path.of(file);
            if (Files.size(path) > 16384) throw new IllegalArgumentException();
            return mapper.readTree(Files.readAllBytes(path));
        }
        catch (Exception e) { throw new ServiceException("NiFi 私有凭据文件不可用"); }
    }

    private synchronized String accessToken()
    {
        if (!properties.getNifi().getTokenFile().isBlank())
        {
            try
            {
                Path path = Path.of(properties.getNifi().getTokenFile());
                if (Files.size(path) > 16384) throw new IllegalArgumentException();
                return Files.readString(path).trim();
            }
            catch (Exception e) { throw new ServiceException("NiFi 服务令牌文件不可用"); }
        }
        if (token != null && tokenExpiry.isAfter(Instant.now())) return token;
        JsonNode secret = credentials();
        String username = secret.path("username").asText("");
        String password = secret.path("password").asText("");
        if (username.isBlank() || password.isBlank()) return ""; // unsecured localhost engine/test server
        String form = "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
            + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8);
        token = new String(exchange("POST", "/access/token", form.getBytes(StandardCharsets.UTF_8),
            "application/x-www-form-urlencoded", false), StandardCharsets.UTF_8).trim();
        if (token.isEmpty() || token.contains("\n")) throw new ServiceException("NiFi 身份验证未返回有效令牌");
        tokenExpiry = Instant.now().plusSeconds(600);
        return token;
    }

    private SSLContext trustContext()
    {
        String file = properties.getNifi().getCaCertFile();
        if (file.isBlank()) file = credentials().path("caCert").asText("");
        if (file.isBlank()) return null;
        try (InputStream in = Files.newInputStream(Path.of(file)))
        {
            KeyStore trust = KeyStore.getInstance(KeyStore.getDefaultType());
            trust.load(null, null);
            int index = 0;
            for (var certificate : CertificateFactory.getInstance("X.509").generateCertificates(in))
                trust.setCertificateEntry("nifi-ca-" + index++, certificate);
            if (index == 0) throw new IllegalArgumentException();
            TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            factory.init(trust);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, factory.getTrustManagers(), null);
            return context;
        }
        catch (Exception e) { throw new ServiceException("NiFi 私有 CA 证书不可用"); }
    }

    private byte[] exchange(String method, String path, byte[] body, String contentType, boolean authenticate)
    {
        HttpURLConnection connection = null;
        try
        {
            connection = open(method, path, body, contentType, authenticate);
            try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream())
            {
                byte[] buffer = new byte[8192]; int count;
                while ((count = in.read(buffer)) != -1)
                {
                    if (out.size() + count > MAX_RESPONSE_BYTES) throw new ServiceException("NiFi 响应超过安全上限");
                    out.write(buffer, 0, count);
                }
                return out.toByteArray();
            }
        }
        catch (ServiceException e) { throw e; }
        catch (Exception e) { throw new ServiceException("NiFi 连接失败或超时，请检查独立引擎和证书配置"); }
        finally { if (connection != null) connection.disconnect(); }
    }

    private HttpURLConnection open(String method, String path, byte[] body, String contentType, boolean authenticate) throws Exception
    {
        if (!path.startsWith("/") || path.startsWith("//") || path.contains("..") || path.contains("\r") || path.contains("\n"))
            throw new ServiceException("NiFi 请求路径无效");
        HttpURLConnection connection = null;
        try
        {
            connection = (HttpURLConnection) URI.create(endpoint().toString() + path).toURL().openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(properties.getNifi().getConnectTimeoutMillis());
            connection.setReadTimeout(properties.getNifi().getReadTimeoutMillis());
            if (connection instanceof HttpsURLConnection secure)
            {
                SSLContext trust = trustContext();
                if (trust != null) secure.setSSLSocketFactory(trust.getSocketFactory());
                // Keep the JDK hostname verifier, including when a private CA is configured.
            }
            connection.setRequestMethod(method);
            // Token and FlowFile-content endpoints return text/binary; json() still parses JSON strictly.
            connection.setRequestProperty("Accept", "*/*");
            if (authenticate)
            {
                String bearer = accessToken();
                if (!bearer.isBlank()) connection.setRequestProperty("Authorization", "Bearer " + bearer);
            }
            if (body != null)
            {
                connection.setRequestProperty("Content-Type", contentType);
                connection.setDoOutput(true);
                try (var out = connection.getOutputStream()) { out.write(body); }
            }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300)
            {
                if (status == 401) { token = null; tokenExpiry = Instant.EPOCH; }
                if (status == 409) throw new ServiceException("流程版本或引擎状态已变化，请刷新画布后重试", 409);
                throw new ServiceException("NiFi 请求失败 (HTTP " + status + ")");
            }
            return connection;
        }
        catch (Exception e) { if (connection != null) connection.disconnect(); throw e; }
    }
}
