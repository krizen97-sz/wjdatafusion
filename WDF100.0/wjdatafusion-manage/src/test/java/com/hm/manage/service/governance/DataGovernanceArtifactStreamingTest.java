package com.hm.manage.service.governance;

import com.hm.common.exception.ServiceException;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DataGovernanceArtifactStreamingTest
{
    private HttpServer server;
    private DataGovernanceNifiClient serve(byte[] bytes) throws Exception
    {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/nifi-api/content", exchange -> {
            exchange.sendResponseHeaders(200, bytes.length);
            try { exchange.getResponseBody().write(bytes); } catch (java.io.IOException ignored) { }
            finally { exchange.close(); }
        }); server.start();
        var properties = new DataGovernanceProperties();
        properties.getNifi().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/nifi-api");
        return new DataGovernanceNifiClient(properties);
    }
    @AfterEach void close() { if (server != null) server.stop(0); }
    @Test void streamsMoreThanJsonResponseLimitWhilePreviewRemainsBounded() throws Exception
    {
        byte[] bytes = new byte[3 * 1024 * 1024 + 11]; Arrays.fill(bytes, (byte) 'q'); var client = serve(bytes);
        String preview = client.contentPreview("/content", 8192, () -> { });
        assertTrue(preview.startsWith("q".repeat(8192))); assertTrue(preview.endsWith("[样本预览已截断]"));
        var output = new ByteArrayOutputStream();
        assertEquals(bytes.length, client.transferContent("/content", output, DataGovernanceNifiClient.MAX_CONTENT_BYTES));
        assertArrayEquals(bytes, output.toByteArray());
    }
    @Test void refusesOversizeResponseBeforeWritingAndPreservesCancellation() throws Exception
    {
        var client = serve(new byte[16384]); var output = new ByteArrayOutputStream();
        assertThrows(ServiceException.class, () -> client.transferContent("/content", output, 8192)); assertEquals(0, output.size());
        RuntimeException cancellation = new RuntimeException("test-cancel");
        assertSame(cancellation, assertThrows(RuntimeException.class, () -> client.transferContent("/content", output, 16384, () -> { throw cancellation; })));
        assertEquals(0, output.size());
    }
}
