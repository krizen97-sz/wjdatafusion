package com.hm.manage.service.governance.hdi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class HdiWorkerClientTest {
    @TempDir Path temporary;
    HdiCoreProperties properties;
    Path root;
    @BeforeEach void setup() throws Exception {
        root = temporary.toRealPath(); Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
        properties = new HdiCoreProperties(); properties.setEnabled(true); properties.setStateDir(root.toString()); mode("normal");
    }
    void mode(String mode) {
        properties.setWorkerCommand(List.of(Path.of(System.getProperty("java.home"), "bin/java").toString(), "-cp",
            System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")), FakeWorker.class.getName(), mode, root.resolve("requests").toString()));
    }
    static HdiTaskStore.Run run() {
        return new HdiTaskStore.Run(UUID.randomUUID().toString(), 17, 2, 4, 1, 21001, 21002, 22000, "a".repeat(64), "DEBUG", "QUEUED", null, null, Instant.now(), Instant.now());
    }
    @Test void correlatesHelloReserveAndStatusAndSendsRevisionAsDecimalString() throws Exception {
        try (HdiWorkerClient client = new HdiWorkerClient(properties)) {
            assertTrue(client.hello().path("ready").asBoolean()); HdiTaskStore.Run run = run();
            JsonNode reserved = client.reserve(run); assertEquals("2", reserved.path("revision").asText());
            assertEquals(run.id(), client.status(run).path("runId").asText());
            assertEquals("CANCELLED", client.cancel(run).path("state").asText());
        }
        assertEquals(List.of("hello", "reserve", "status", "cancel", "shutdown"), Files.readAllLines(root.resolve("requests")));
    }
    @Test void disconnectAfterSubmitIsUnknownAndNeverReplayedOrRestarted() throws Exception {
        mode("disconnect-submit");
        try (HdiWorkerClient client = new HdiWorkerClient(properties)) {
            HdiTaskStore.Run run = run(); client.reserve(run);
            var failure = assertThrows(HdiWorkerClient.Failure.class, () -> client.submit(run, HdiProtocol.JSON.createObjectNode().put("ok", true)));
            assertTrue(failure.uncertain()); assertThrows(HdiWorkerClient.Failure.class, () -> client.status(run));
            assertThrows(HdiWorkerClient.Failure.class, client::hello);
        }
        assertEquals(List.of("hello", "reserve", "submit"), Files.readAllLines(root.resolve("requests")));
    }
    @Test void rejectsMismatchedRequestAndRunOwnership() {
        for (String mode : List.of("request-mismatch", "owner-mismatch", "duplicate-json", "not-ready")) {
            mode(mode);
            try (HdiWorkerClient client = new HdiWorkerClient(properties)) {
                var failure = assertThrows(HdiWorkerClient.Failure.class, () -> client.reserve(run()), mode);
                assertTrue(failure.uncertain());
            }
        }
    }
    @Test void explicitWorkerFailureKeepsSafeCodeWithoutLeakingVendorBody() {
        mode("reject");
        try (HdiWorkerClient client = new HdiWorkerClient(properties)) {
            var failure = assertThrows(HdiWorkerClient.Failure.class, () -> client.reserve(run()));
            assertFalse(failure.uncertain()); assertEquals("CAPACITY_LIMIT", failure.code());
            assertFalse(failure.toString().contains("private-secret"));
        }
    }
    @Test void helloRefreshesActualCapacityInsteadOfReusingCachedCounts() {
        try (HdiWorkerClient client = new HdiWorkerClient(properties)) {
            assertEquals(0, client.hello().path("capacity").path("active").asInt());
            client.reserve(run());
            assertEquals(1, client.hello().path("capacity").path("active").asInt());
        }
    }
    @Test void requestTimeoutDoesNotAutomaticallyRetry() {
        mode("timeout");
        try (HdiWorkerClient client = new HdiWorkerClient(properties, Duration.ofMillis(500))) {
            long start = System.nanoTime(); var failure = assertThrows(HdiWorkerClient.Failure.class, () -> client.reserve(run()));
            assertTrue(failure.uncertain()); assertTrue(Duration.ofNanos(System.nanoTime()-start).toSeconds() < 3);
            assertThrows(HdiWorkerClient.Failure.class, client::hello);
        }
    }
    @Test void disabledOrSharedStateNeverStartsWorker() throws Exception {
        properties.setEnabled(false);
        try (HdiWorkerClient client = new HdiWorkerClient(properties)) { assertFalse(assertThrows(HdiWorkerClient.Failure.class, client::hello).uncertain()); }
        properties.setEnabled(true); Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwxr-xr-x"));
        try (HdiWorkerClient client = new HdiWorkerClient(properties)) { assertFalse(assertThrows(HdiWorkerClient.Failure.class, client::hello).uncertain()); }
        assertFalse(Files.exists(root.resolve("requests")));
    }
    public static class FakeWorker {
        public static void main(String[] args) throws Exception {
            String mode = args[0]; Path requests = Path.of(args[1]); ObjectNode view = null;
            try (BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = input.readLine()) != null) {
                    JsonNode request = HdiProtocol.JSON.readTree(line); String op = request.path("op").asText();
                    Files.writeString(requests, op + "\n", java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
                    if (op.equals("submit") && mode.equals("disconnect-submit")) return;
                    if (op.equals("reserve") && mode.equals("timeout")) { Thread.sleep(1000); return; }
                    ObjectNode response = HdiProtocol.JSON.createObjectNode().put("protocolVersion", 1).put("requestId", request.path("requestId").asText()).put("ok", true);
                    ObjectNode data = HdiProtocol.JSON.createObjectNode();
                    if (op.equals("hello")) {
                        data.put("engine", "original-detl").put("ready", !mode.equals("not-ready"));
                        data.set("capabilities", HdiProtocol.JSON.valueToTree(List.of("reserve", "submit", "status", "cancel", "logs", "artifact", "shutdown")));
                        data.set("binding", HdiProtocol.JSON.createObjectNode().put("id", "1").put("name", "local-original"));
                        data.set("capacity", HdiProtocol.JSON.createObjectNode().put("limit", 2).put("active", view == null ? 0 : 1).put("available", view == null ? 2 : 1));
                    } else if (op.equals("reserve")) {
                        view = ((ObjectNode)request).deepCopy(); view.remove(List.of("protocolVersion", "requestId", "op"));
                        view.put("state", "RESERVED").put("finalized", false).put("outputsDir", "/run/outputs").put("authorizedOutputRoot", "/run"); data = view;
                        if (mode.equals("owner-mismatch")) data.put("ownerId", "999");
                        if (mode.equals("reject")) { response.put("ok", false); response.set("error", HdiProtocol.JSON.createObjectNode().put("code", "CAPACITY_LIMIT").put("message", "private-secret")); }
                    } else if (op.equals("status")) data = view;
                    else if (op.equals("cancel")) { data = view; data.put("state", "CANCELLED").put("finalized", true); }
                    else if (op.equals("shutdown")) data.put("stopped", true);
                    response.set("data", data);
                    if (mode.equals("request-mismatch")) response.put("requestId", "unknown");
                    if (mode.equals("duplicate-json")) System.out.println("{\"ok\":true,\"ok\":false}");
                    else System.out.println(response);
                    if (op.equals("shutdown")) return;
                }
            }
        }
    }
}
