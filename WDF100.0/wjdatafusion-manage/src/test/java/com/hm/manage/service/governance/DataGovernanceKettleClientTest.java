package com.hm.manage.service.governance;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DataGovernanceKettleClientTest
{
    @TempDir Path temporary;
    @Test void onlyFixedLoopbackIsAcceptedAndRedirectsNeverFollow() throws Exception
    {
        Path token = temporary.resolve("broker-token"); Files.writeString(token, "synthetic-test-token-1234567890");
        DataGovernanceKettleProperties p = new DataGovernanceKettleProperties(); p.setEnabled(true); p.setWorkerTokenFile(token.toString());
        DataGovernanceKettleClient client = new DataGovernanceKettleClient(p);
        for (String url : List.of("http://localhost:19162", "http://example.invalid:19162", "http://127.0.0.1:19162/other", "http://user@127.0.0.1:19162", "https://127.0.0.1:19162"))
        { p.setWorkerUrl(url); assertThrows(DataGovernanceKettleClient.Failure.class, () -> client.request("GET", "/health", null)); }
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0); AtomicInteger followed = new AtomicInteger();
        server.createContext("/health", exchange -> {
            assertEquals("Bearer synthetic-test-token-1234567890", exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "{\"status\":\"UP\"}".getBytes(StandardCharsets.UTF_8); exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close(); });
        server.createContext("/redirect", exchange -> { exchange.getResponseHeaders().add("Location", "/private"); exchange.sendResponseHeaders(302, -1); exchange.close(); });
        server.createContext("/private", exchange -> { followed.incrementAndGet(); exchange.sendResponseHeaders(200, -1); exchange.close(); });
        server.start();
        try
        {
            p.setWorkerUrl("http://127.0.0.1:" + server.getAddress().getPort());
            assertEquals("UP", client.request("GET", "/health", null).path("status").asText());
            assertThrows(DataGovernanceKettleClient.Failure.class, () -> client.request("GET", "/redirect", null)); assertEquals(0, followed.get());
            Path link = temporary.resolve("token-link"); Files.createSymbolicLink(link, token); p.setWorkerTokenFile(link.toString());
            assertThrows(DataGovernanceKettleClient.Failure.class, () -> client.request("GET", "/health", null));
        }
        finally { server.stop(0); }
    }
}
