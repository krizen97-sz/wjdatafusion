package com.hm.manage.service.governance;

import com.hm.common.exception.ServiceException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DataGovernanceNifiClientTest
{
    private HttpServer server;
    private DataGovernanceNifiClient client() throws Exception
    {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        DataGovernanceProperties config = new DataGovernanceProperties();
        config.getNifi().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/nifi-api");
        return new DataGovernanceNifiClient(config);
    }
    @AfterEach void close() { if (server != null) server.stop(0); }
    @Test void readsActualJsonAndDoesNotFollowRedirects() throws Exception
    {
        var client = client(); AtomicInteger targetCalls = new AtomicInteger();
        server.createContext("/nifi-api/flow/about", exchange -> {
            byte[] body = "{\"about\":{\"version\":\"2.11.0\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.createContext("/nifi-api/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "/target"); exchange.sendResponseHeaders(302, -1); exchange.close();
        });
        server.createContext("/target", exchange -> { targetCalls.incrementAndGet(); exchange.sendResponseHeaders(200, -1); exchange.close(); });
        server.start();
        assertEquals("2.11.0", client.json("GET", "/flow/about", null).path("about").path("version").asText());
        assertThrows(ServiceException.class, () -> client.json("GET", "/redirect", null));
        assertEquals(0, targetCalls.get());
    }
    @Test void neverIncludesUpstreamErrorBodyInException() throws Exception
    {
        var client = client();
        server.createContext("/nifi-api/fail", exchange -> {
            byte[] body = "password=private-test-value".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, body.length); exchange.getResponseBody().write(body); exchange.close();
        }); server.start();
        var error = assertThrows(ServiceException.class, () -> client.json("GET", "/fail", null));
        assertTrue(error.getMessage().contains("500")); assertFalse(error.getMessage().contains("private-test-value"));
    }
    @Test void boundsResponsesAndRejectsAlternateRequestTargets() throws Exception
    {
        var client = client();
        server.createContext("/nifi-api/large", exchange -> {
            byte[] body = new byte[DataGovernanceNifiClient.MAX_RESPONSE_BYTES + 1];
            exchange.sendResponseHeaders(200, body.length);
            try { exchange.getResponseBody().write(body); } finally { exchange.close(); }
        }); server.start();
        assertThrows(ServiceException.class, () -> client.content("/large"));
        assertThrows(ServiceException.class, () -> client.json("GET", "//other-host/", null));
        assertThrows(ServiceException.class, () -> client.json("GET", "/../access/token", null));
    }
}
