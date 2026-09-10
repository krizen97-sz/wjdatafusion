package com.hm.manage.service.governance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.springframework.stereotype.Component;

/** No redirects, ambient proxies, browser-selected URLs or forwarded credentials. */
@Component
public class DataGovernanceKettleClient
{
    public static class Failure extends RuntimeException
    {
        public final int status;
        public Failure(int status) { super("原生 Kettle worker 请求未完成"); this.status = status; }
        public boolean uncertain() { return status == 0 || status >= 500; }
    }
    public record Download(InputStream stream, long bytes, String partial) implements AutoCloseable
    { public void close() throws IOException { stream.close(); } }
    private final DataGovernanceKettleProperties properties;
    private final ObjectMapper mapper = new ObjectMapper();
    public DataGovernanceKettleClient(DataGovernanceKettleProperties properties) { this.properties = properties; }

    protected HttpURLConnection connection(String method, String path) throws IOException
    {
        if (!properties.isEnabled()) throw new Failure(503);
        URI base;
        try { base = URI.create(properties.getWorkerUrl()); }
        catch (Exception e) { throw new Failure(503); }
        if (!"http".equals(base.getScheme()) || !"127.0.0.1".equals(base.getHost()) || base.getPort() < 1
            || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null
            || !(base.getPath().isEmpty() || base.getPath().equals("/"))) throw new Failure(503);
        if (!path.startsWith("/") || path.contains("\r") || path.contains("\n")) throw new Failure(400);
        Path tokenPath = Path.of(properties.getWorkerTokenFile());
        if (!Files.isRegularFile(tokenPath, LinkOption.NOFOLLOW_LINKS) || Files.size(tokenPath) > 4096) throw new Failure(503);
        String token = Files.readString(tokenPath).trim();
        if (token.length() < 20 || !token.matches("[A-Za-z0-9_-]+")) throw new Failure(503);
        HttpURLConnection connection = (HttpURLConnection) new URL("http://127.0.0.1:" + base.getPort() + path).openConnection(Proxy.NO_PROXY);
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(3000); connection.setReadTimeout(path.equals("/health") ? 3000 : 150000);
        connection.setRequestMethod(method); connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setRequestProperty("Accept", "application/json");
        return connection;
    }
    public JsonNode request(String method, String path, Object body)
    {
        HttpURLConnection connection = null;
        try
        {
            connection = connection(method, path);
            if (body != null)
            {
                byte[] bytes = mapper.writeValueAsBytes(body);
                connection.setDoOutput(true); connection.setFixedLengthStreamingMode(bytes.length);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                try (OutputStream output = connection.getOutputStream()) { output.write(bytes); }
            }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new Failure(status);
            try (InputStream input = connection.getInputStream())
            {
                byte[] bytes = input.readNBytes(24 * 1024 * 1024 + 1);
                if (bytes.length > 24 * 1024 * 1024) throw new Failure(502);
                return mapper.readTree(bytes);
            }
        }
        catch (Failure e) { throw e; }
        catch (Exception e) { throw new Failure(0); }
        finally { if (connection != null) connection.disconnect(); }
    }
    public Download download(String runId, String filename)
    {
        HttpURLConnection connection = null;
        try
        {
            connection = connection("GET", "/runs/" + runId + "/files/" + URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20"));
            int status = connection.getResponseCode();
            if (status != 200) throw new Failure(status);
            HttpURLConnection held = connection;
            InputStream stream = new FilterInputStream(connection.getInputStream())
            { @Override public void close() throws IOException { try { super.close(); } finally { held.disconnect(); } } };
            return new Download(stream, connection.getContentLengthLong(), connection.getHeaderField("X-Kettle-Partial"));
        }
        catch (Failure e) { if (connection != null) connection.disconnect(); throw e; }
        catch (Exception e) { if (connection != null) connection.disconnect(); throw new Failure(0); }
    }
}
