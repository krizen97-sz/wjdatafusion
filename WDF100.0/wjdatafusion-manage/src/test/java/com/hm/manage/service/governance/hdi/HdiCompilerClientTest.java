package com.hm.manage.service.governance.hdi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class HdiCompilerClientTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path temporary;
    private Path root;
    private HdiCoreProperties properties;
    private final Map<String,Object> context = Map.of("businessTaskId", "17", "revision", "2", "ownerId", "4", "workspaceId", "1", "projectCode", "test-project");

    @BeforeEach void setup() throws Exception {
        root = temporary.toRealPath(); Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
        properties = new HdiCoreProperties(); properties.setEnabled(true); properties.setStateDir(root.toString()); command("prepare");
    }
    private void command(String mode) {
        properties.setCompilerCommand(List.of(Path.of(System.getProperty("java.home"), "bin/java").toString(), "-cp",
            System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")), FakeCompiler.class.getName(), mode, root.resolve("started").toString()));
    }
    private HdiCompilerClient client() { return new HdiCompilerClient(properties); }
    private JsonNode visual() { return JSON.createObjectNode().set("nodes", JSON.createObjectNode()); }
    private static String sha(String value) { return HdiTaskStore.sha256(value.getBytes(StandardCharsets.UTF_8)); }
    private static ObjectNode packet(Object value) throws Exception {
        byte[] data = JSON.writeValueAsBytes(value);
        return JSON.createObjectNode().put("base64", Base64.getEncoder().encodeToString(data)).put("sha256", HdiTaskStore.sha256(data));
    }

    @Test void verifiesPreparedSnapshotAndCanonicalOwnership() {
        JsonNode result = client().prepare(context, visual());
        assertTrue(result.path("ok").asBoolean());
        assertEquals("17", result.path("canonicalVisual").path("flowVo").path("id").asText());
        assertEquals(sha(result.path("snapshotJson").asText()), result.path("snapshotSha256").asText());
    }

    @Test void rejectsSnapshotOwnerOrDigestChangesEvenWhenCompilerReportsSuccess() {
        for (String mode : List.of("owner-tamper", "digest-tamper", "wrong-operation", "wrong-protocol", "duplicate-json", "trailing-json", "success-nonzero")) {
            command(mode); HdiTaskFault fault = assertThrows(HdiTaskFault.class, () -> client().prepare(context, visual()), mode);
            assertEquals(502, fault.status(), mode);
        }
    }

    @Test void rawVendorFailuresAreNeverShownInUserErrorsAndDiagnosticsArePrivateAndBounded() throws Exception {
        command("unsupported"); HdiTaskFault fault = assertThrows(HdiTaskFault.class, () -> client().prepare(context, visual()));
        assertEquals(422, fault.status()); assertFalse(fault.toString().contains("credential-secret"));
        try (var stream = Files.list(root.resolve("compiler-diagnostics"))) {
            Path log = stream.findFirst().orElseThrow(); assertEquals(65536, Files.size(log));
            assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(log));
        }
    }

    @Test void timesOutOwnedProcessAndReleasesConcurrencySlot() throws Exception {
        command("hang"); HdiCompilerClient client = new HdiCompilerClient(properties, Duration.ofMillis(600), 4096, 1);
        long started = System.nanoTime(); HdiTaskFault fault = assertThrows(HdiTaskFault.class, () -> client.prepare(context, visual()));
        assertEquals(504, fault.status()); assertTrue(Duration.ofNanos(System.nanoTime()-started).toSeconds() < 5);
        long pid = Long.parseLong(Files.readString(root.resolve("started")));
        assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));
        command("prepare"); assertTrue(client.prepare(context, visual()).path("ok").asBoolean());
    }

    @Test void refusesExcessConcurrencyInsteadOfStartingMoreProcesses() throws Exception {
        command("hang"); HdiCompilerClient client = new HdiCompilerClient(properties, Duration.ofMillis(1200), 4096, 1);
        CompletableFuture<HdiTaskFault> first = CompletableFuture.supplyAsync(() -> assertThrows(HdiTaskFault.class, () -> client.prepare(context, visual())));
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!Files.exists(root.resolve("started")) && System.nanoTime() < end) Thread.sleep(10);
        assertTrue(Files.exists(root.resolve("started")));
        assertEquals(503, assertThrows(HdiTaskFault.class, () -> client.prepare(context, visual())).status());
        assertEquals(504, first.get(3, TimeUnit.SECONDS).status());
    }

    @Test void timeoutTerminatesOwnedCompilerChildWithoutTouchingUnrelatedProcess() throws Exception {
        Process unrelated = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin/java").toString(), "-cp",
            System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")), FakeCompiler.class.getName(), "hold", root.resolve("unrelated").toString()).start();
        try {
            command("hang-child"); HdiCompilerClient client = new HdiCompilerClient(properties, Duration.ofMillis(1600), 4096, 1);
            assertEquals(504, assertThrows(HdiTaskFault.class, () -> client.prepare(context, visual())).status());
            long child = Long.parseLong(Files.readString(root.resolve("started-child")));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (ProcessHandle.of(child).map(ProcessHandle::isAlive).orElse(false) && System.nanoTime() < deadline) Thread.sleep(10);
            assertFalse(ProcessHandle.of(child).map(ProcessHandle::isAlive).orElse(false));
            assertTrue(unrelated.isAlive());
        } finally { unrelated.destroyForcibly(); unrelated.waitFor(2, TimeUnit.SECONDS); }
    }

    @Test void excessiveOutputAndDisabledConfigurationFailClosed() {
        command("overflow"); HdiCompilerClient limited = new HdiCompilerClient(properties, Duration.ofSeconds(2), 1024, 1);
        assertEquals(502, assertThrows(HdiTaskFault.class, () -> limited.prepare(context, visual())).status());
        properties.setEnabled(false); assertEquals(503, assertThrows(HdiTaskFault.class, () -> client().prepare(context, visual())).status());
        properties.setEnabled(true); properties.setCompilerCommand(List.of());
        assertEquals(503, assertThrows(HdiTaskFault.class, () -> client().prepare(context, visual())).status());
    }

    @Test void browserContentIsOnlyStdinAndCannotBecomeACommandArgument() throws Exception {
        String marker = root.resolve("must-not-exist").toString();
        JsonNode result = client().prepare(context, JSON.createObjectNode().put("name", "$(touch " + marker + "); `echo secret`"));
        assertTrue(result.path("ok").asBoolean()); assertFalse(Files.exists(Path.of(marker)));
    }

    @Test void rejectsSharedAndSymbolicDiagnosticDirectoriesBeforeSpawning() throws Exception {
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwxr-xr-x"));
        assertEquals(503, assertThrows(HdiTaskFault.class, () -> client().prepare(context, visual())).status());
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
        Files.createSymbolicLink(root.resolve("compiler-diagnostics"), root);
        assertEquals(503, assertThrows(HdiTaskFault.class, () -> client().prepare(context, visual())).status());
        assertFalse(Files.exists(root.resolve("started")));
    }

    @Test void verifiesOriginalBinaryPayloadDigestsAndEveryRunBinding() throws Exception {
        Fixture fixture = fixture();
        for (String mode : List.of("compile", "payload-tamper", "module-instance-tamper", "output-dir-tamper", "exec-tamper")) {
            Path result = root.resolve(mode + ".json"); ObjectNode response = fixture.response().deepCopy();
            if (mode.equals("payload-tamper")) ((ObjectNode)response.path("flowjson")).put("sha256", "0".repeat(64));
            if (mode.equals("module-instance-tamper")) {
                ObjectNode flow = (ObjectNode)JSON.readTree(Base64.getDecoder().decode(response.path("flowjson").path("base64").asText()));
                ((ObjectNode)flow.path("streams").get(0).path("modules").get(0).path("module_info")).put("module_instance_id", 999);
                response.set("flowjson", packet(flow));
            }
            if (mode.equals("output-dir-tamper")) ((ObjectNode)response.path("metadataSnapshot")).put("outputsDir", "/wrong/output");
            if (mode.equals("exec-tamper")) response.put("execId", "999");
            Files.writeString(result, JSON.writeValueAsString(response));
            command("from-file"); List<String> argv = new ArrayList<>(properties.getCompilerCommand()); argv.add(result.toString()); properties.setCompilerCommand(argv);
            if (mode.equals("compile")) assertTrue(client().compile(fixture.snapshot(), sha(fixture.snapshot()), fixture.run()).path("ok").asBoolean());
            else assertEquals(502, assertThrows(HdiTaskFault.class, () -> client().compile(fixture.snapshot(), sha(fixture.snapshot()), fixture.run())).status(), mode);
        }
    }

    @Test void tamperedStoredSnapshotNeverStartsTheCompiler() throws Exception {
        Fixture fixture = fixture();
        assertEquals(502, assertThrows(HdiTaskFault.class, () -> client().compile(fixture.snapshot() + " ", sha(fixture.snapshot()), fixture.run())).status());
        assertFalse(Files.exists(root.resolve("started")));
    }

    @Test void validatesScheduleEchoAndQuartzExpression() {
        ObjectNode schedule = JSON.createObjectNode().put("scheduleType", 2).put("unit", 4).put("timeInterval", "1").put("executeTime", "04:05:06");
        JsonNode valid = client().schedule(schedule, "Asia/Shanghai"); assertEquals("6 5 4 * * ?", valid.path("cronExpressions").get(0).asText());
        command("schedule-tamper"); assertEquals(502, assertThrows(HdiTaskFault.class, () -> client().schedule(schedule, "Asia/Shanghai")).status());
    }

    @Test void optionalRealOriginalCliReturnsVerifiedSnapshot() throws Exception {
        String bundle = System.getenv("HDI_ORIGINAL_BUNDLE"), javaHome = System.getenv("HDI_COMPILER_JAVA_HOME"), script = System.getenv("HDI_COMPILER_SCRIPT");
        assumeTrue(bundle != null && javaHome != null && script != null, "original CLI integration not configured");
        Path runtime = root.resolve("compiler-state"); Files.createDirectory(runtime, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        properties.setCompilerCommand(List.of("python3", script, "--bundle", bundle, "--java-home", javaHome, "--runtime", runtime.toString()));
        JsonNode result = client().prepare(context, visual()); assertEquals("17", result.path("canonicalVisual").path("flowId").asText());
        JsonNode schedule = client().schedule(JSON.createObjectNode().put("scheduleType", 2).put("unit", 4).put("timeInterval", "1").put("executeTime", "04:05:06").put("flowIncrStatus", 1).put("startFullImmediately", 0), "Asia/Shanghai");
        assertEquals("6 5 4 * * ?", schedule.path("cronExpressions").get(0).asText());
        JsonNode calendar = client().schedule(JSON.createObjectNode().put("scheduleType", 2).put("unit", 6).put("timeInterval", "90").put("flowIncrStatus", 1).put("startFullImmediately", 0), "Asia/Shanghai");
        assertEquals("CALENDAR", calendar.path("mode").asText()); assertEquals(90, calendar.path("calendar").path("interval").asInt());
    }

    @Test void optionalRealOriginalCliCompilesAndBindsActualThreeNodePayload() throws Exception {
        String bundle = System.getenv("HDI_ORIGINAL_BUNDLE"), javaHome = System.getenv("HDI_COMPILER_JAVA_HOME"), script = System.getenv("HDI_COMPILER_SCRIPT"), fixture = System.getenv("HDI_COMPILER_VISUAL");
        assumeTrue(bundle != null && javaHome != null && script != null && fixture != null, "original three-node CLI integration not configured");
        Path runtime = root.resolve("compiler-state"); Files.createDirectory(runtime, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        properties.setCompilerCommand(List.of("python3", script, "--bundle", bundle, "--java-home", javaHome, "--runtime", runtime.toString()));
        HdiCompilerClient client = client();
        JsonNode prepared = client.prepare(context, JSON.readTree(Files.readAllBytes(Path.of(fixture))));
        Map<String,Object> run = Map.of("businessTaskId", "17", "revision", "2", "nativeFlowId", "21001", "nativeInstanceId", "21002", "metadataIdBase", "22000", "execId", "21003", "outputsDir", root.resolve("outputs").toString(), "authorizedOutputRoot", root.toString(), "mode", "test");
        JsonNode compiled = client.compile(prepared.path("snapshotJson").asText(), prepared.path("snapshotSha256").asText(), run);
        assertTrue(compiled.path("ok").asBoolean()); assertEquals(3, compiled.path("nodeMappings").size());
    }

    private record Fixture(String snapshot, Map<String,Object> run, ObjectNode response) { }
    private Fixture fixture() throws Exception {
        JsonNode visual = JSON.valueToTree(Map.of("nodes", Map.of("110", Map.of("id", 110), "130", Map.of("id", 130))));
        String snapshot = JSON.writeValueAsString(Map.of("protocolVersion", 1, "context", context, "visual", visual));
        Map<String,Object> run = Map.of("businessTaskId", "17", "revision", "2", "nativeFlowId", "21001", "nativeInstanceId", "21002", "metadataIdBase", "22000", "execId", "21003", "outputsDir", "/owned/outputs", "authorizedOutputRoot", "/owned", "mode", "test");
        var mappings = List.of(Map.of("visualNodeId", "110", "nodeId", "22019", "moduleId", "22016", "stepId", "22017", "itemId", "22018", "streamId", "22000", "adapterName", "dataGeneratorInput"),
            Map.of("visualNodeId", "130", "nodeId", "22035", "moduleId", "22032", "stepId", "22033", "itemId", "22034", "streamId", "22000", "adapterName", "fileOutput"));
        List<Object> modules = new ArrayList<>();
        for (var mapping : mappings) modules.add(Map.of("module_info", Map.of("id", Long.valueOf(mapping.get("moduleId")), "flow_id", 21001, "module_instance_id", 21002, "visual_node_id", Long.valueOf(mapping.get("nodeId")), "item_id", Long.valueOf(mapping.get("itemId"))), "adapter", Map.of("adapter_name", mapping.get("adapterName"))));
        ObjectNode response = JSON.createObjectNode().put("ok", true).put("protocolVersion", 1).put("operation", "compile").put("businessTaskId", "17").put("revision", "2").put("ownerId", "4").put("workspaceId", "1").put("flowId", "21001").put("instanceId", "21002").put("execId", "21003").put("snapshotSha256", sha(snapshot));
        response.set("diagnostics", JSON.createArrayNode()); response.set("nodeMappings", JSON.valueToTree(mappings));
        response.set("metadataSnapshot", JSON.valueToTree(Map.of("outputsDir", "/owned/outputs", "metadataIdBase", "22000")));
        response.set("flowjson", packet(Map.of("flow", Map.of("id", 21001, "group_id", 1), "streams", List.of(Map.of("id", 22000, "modules", modules)))));
        response.set("schedule", packet(Map.of("id", 21001, "exec_id", "21003", "oper_type", 1, "oper_value", 1, "strategy_flow", Map.of("id", 21001, "group_id", 1, "etl_schedule", Map.of("node_id", 21001)))));
        response.set("stop", packet(Map.of("id", 21001, "oper_type", 1, "oper_value", 0)));
        return new Fixture(snapshot, run, response);
    }

    /** Separate JVM used to exercise pipes/timeouts, never a substitute for SDK compilation tests. */
    public static class FakeCompiler {
        public static void main(String[] arguments) throws Exception {
            String mode = arguments[0]; Files.writeString(Path.of(arguments[1]), Long.toString(ProcessHandle.current().pid()));
            if (mode.equals("hang-child")) {
                new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin/java").toString(), "-cp",
                    System.getProperty("java.class.path"), FakeCompiler.class.getName(), "hold", arguments[1] + "-child").start();
                Thread.sleep(10000); return;
            }
            if (mode.equals("hang") || mode.equals("hold")) { Thread.sleep(10000); return; }
            JsonNode request = JSON.readTree(System.in.readAllBytes());
            if (request.path("operation").asText().equals("schedule")) {
                ObjectNode response = JSON.createObjectNode().put("ok", true).put("protocolVersion", 1).put("operation", "schedule").put("mode", "CRON").put("zoneId", request.path("zoneId").asText());
                response.set("scheduleConfig", request.path("scheduleConfig")); response.set("diagnostics", JSON.createArrayNode());
                response.set("cronExpressions", JSON.createArrayNode().add(mode.equals("schedule-tamper") ? "invalid cron" : "6 5 4 * * ?"));
                System.out.print(response); return;
            }
            if (mode.equals("overflow")) { System.out.print("x".repeat(1024 * 128)); return; }
            if (mode.equals("unsupported")) {
                System.err.print("credential-secret".repeat(10000));
                System.out.print("{\"ok\":false,\"protocolVersion\":1,\"code\":\"UNSUPPORTED_GRAPH\",\"message\":\"credential-secret\"}"); System.exit(2);
            }
            if (mode.equals("from-file")) { System.out.print(Files.readString(Path.of(arguments[2]))); return; }
            ObjectNode context = request.path("context").deepCopy(), visual = request.path("visual").deepCopy();
            visual.put("flowId", Long.parseLong(context.path("businessTaskId").asText()));
            visual.set("flowVo", JSON.valueToTree(Map.of("id", 17, "groupId", 1, "userId", 4, "projectCode", "test-project")));
            if (mode.equals("owner-tamper")) context.put("ownerId", "999");
            String snapshot = JSON.writeValueAsString(Map.of("protocolVersion", 1, "context", context, "visual", visual));
            ObjectNode result = JSON.createObjectNode().put("ok", true).put("protocolVersion", 1).put("operation", "prepare").put("snapshotJson", snapshot).put("snapshotSha256", sha(snapshot));
            result.set("canonicalVisual", visual); result.set("diagnostics", JSON.createArrayNode());
            if (mode.equals("wrong-protocol")) result.put("protocolVersion", 2);
            if (mode.equals("wrong-operation")) result.put("operation", "compile");
            if (mode.equals("digest-tamper")) result.put("snapshotSha256", "0".repeat(64));
            if (mode.equals("duplicate-json")) { System.out.print("{\"ok\":true,\"ok\":false}"); return; }
            System.out.print(JSON.writeValueAsString(result));
            if (mode.equals("trailing-json")) System.out.print(" {}");
            if (mode.equals("success-nonzero")) System.exit(3);
        }
    }
}
