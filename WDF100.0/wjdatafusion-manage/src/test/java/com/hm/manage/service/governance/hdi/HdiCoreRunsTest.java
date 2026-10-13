package com.hm.manage.service.governance.hdi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hm.manage.service.governance.DataGovernanceArtifactStore;
import com.hm.manage.service.governance.DataGovernanceProperties;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HdiCoreRunsTest {
    @TempDir Path temporary;
    HdiTaskStore store = mock(HdiTaskStore.class);
    HdiCompilerClient compiler = mock(HdiCompilerClient.class);
    HdiWorkerClient worker = mock(HdiWorkerClient.class);
    HdiCoreRuns runs;
    AtomicReference<HdiTaskStore.Run> row;
    ObjectNode reserved;
    final byte[] content = "mapped_message\noriginal value\n".getBytes(StandardCharsets.UTF_8);
    @BeforeEach void setup() {
        HdiCoreProperties properties = new HdiCoreProperties(); properties.setEnabled(true);
        DataGovernanceProperties artifacts = new DataGovernanceProperties(); artifacts.setStorageDir(temporary.toString());
        row = new AtomicReference<>(new HdiTaskStore.Run(UUID.randomUUID().toString(), 17, 2, 4, 1, 21001, 21002, 22000, "a".repeat(64), "DEBUG", "QUEUED", null, null, Instant.now(), Instant.now()));
        when(store.get(4, 17)).thenReturn(new HdiTaskStore.Task(17, 1, 4, "project", "task", -1, 2, null, "DRAFT", null, null, false, Instant.now(), Instant.now()));
        when(store.revision(4, 17, 2)).thenReturn(new HdiTaskStore.Revision(17, 2, 4, "{}", "a".repeat(64), "frozen-original-snapshot", "b".repeat(64), "[]"));
        when(store.reserveRun(eq(4L), eq(17L), eq(2L), anyString(), nullable(String.class))).thenAnswer(call -> row.get());
        when(store.run(eq(4L), anyString())).thenAnswer(call -> row.get());
        when(store.run(eq(999L), anyString())).thenThrow(new HdiTaskFault(404, "no access"));
        doAnswer(call -> {
            synchronized (row) {
                HdiTaskStore.Run old = row.get(); if (List.of("SUCCEEDED", "FAILED", "CANCELLED").contains(old.state())) return null;
                row.set(new HdiTaskStore.Run(old.id(), old.taskId(), old.revision(), old.ownerId(), old.workspaceId(), old.nativeFlowId(), old.nativeInstanceId(), old.metadataIdBase(), old.definitionSha256(), old.mode(), call.getArgument(1), call.getArgument(2), call.getArgument(3), old.createdAt(), Instant.now()));
            } return null;
        }).when(store).transition(any(), anyString(), nullable(String.class), nullable(String.class));
        reserved = view("RESERVED", false); reserved.put("outputsDir", "/owned/run/outputs").put("authorizedOutputRoot", "/owned/run");
        when(worker.reserve(any())).thenReturn(reserved);
        when(worker.submit(any(), any())).thenReturn(view("RUNNING", false));
        when(worker.status(any())).thenReturn(view("RUNNING", false));
        when(worker.cancel(any())).thenReturn(view("CANCELLED", true));
        when(compiler.compile(anyString(), anyString(), anyMap())).thenReturn(HdiProtocol.JSON.createObjectNode().put("ok", true));
        runs = new HdiCoreRuns(properties, store, compiler, worker, new DataGovernanceArtifactStore(artifacts));
    }
    @AfterEach void cleanup() { runs.close(); }
    ObjectNode view(String state, boolean finalized) { return HdiProtocol.JSON.createObjectNode().put("state", state).put("finalized", finalized); }
    void awaitState(String expected) throws Exception {
        long end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
        while (!row.get().state().equals(expected) && System.nanoTime() < end) Thread.sleep(10);
        assertEquals(expected, row.get().state());
    }
    @Test void reservesDatabaseBeforeWorkerAndCompilesOnlyFrozenRevisionWithWorkerOutputDirectory() throws Exception {
        runs.startDebug(4, 17, 2); awaitState("RUNNING");
        var order = inOrder(store, worker, compiler);
        order.verify(store).reserveRun(4, 17, 2, "DEBUG", null);
        order.verify(worker).reserve(any());
        order.verify(compiler).compile(eq("frozen-original-snapshot"), eq("b".repeat(64)), argThat(context -> context.get("outputsDir").equals("/owned/run/outputs") && context.get("execId").equals("21003") && context.get("nativeFlowId").equals("21001") && context.get("outputRootSource").equals("worker-reserve")));
        order.verify(worker).submit(any(), any());
    }
    @Test void uncertainSubmitIsPersistedUnknownAndNeverResubmitted() throws Exception {
        when(worker.submit(any(), any())).thenThrow(new HdiWorkerClient.Failure("EOF", true));
        runs.startDebug(4, 17, 2); awaitState("UNKNOWN");
        verify(worker, times(1)).submit(any(), any()); verify(worker, never()).cancel(any());
    }
    @Test void compilerRejectionCancelsReservationAndPersistsFailureWithoutStartingCore() throws Exception {
        when(compiler.compile(anyString(), anyString(), anyMap())).thenThrow(new HdiTaskFault(422, "配置超出范围"));
        runs.startDebug(4, 17, 2); awaitState("FAILED");
        verify(worker).cancel(any()); verify(worker, never()).submit(any(), any());
    }
    @Test void terminalWithoutFinalizedConfirmationIsNotReportedAsSucceeded() throws Exception {
        when(worker.submit(any(), any())).thenReturn(view("SUCCEEDED", false));
        runs.startDebug(4, 17, 2); awaitState("STOPPING");
        assertFalse(runs.artifacts(4, row.get().id()).path("available").asBoolean());
    }
    @Test void capturesAndRehashesFullArtifactThenDownloadsWithoutWorker() throws Exception {
        String sha = HdiTaskStore.sha256(content);
        ObjectNode success = view("SUCCEEDED", true); success.set("artifacts", HdiProtocol.JSON.createArrayNode().add(HdiProtocol.JSON.createObjectNode().put("artifactId", "native-result").put("name", "result.csv").put("bytes", content.length).put("sha256", sha)));
        when(worker.submit(any(), any())).thenReturn(success);
        when(worker.artifact(any(), eq("native-result"), eq(0L), anyInt())).thenReturn(HdiProtocol.JSON.createObjectNode().put("artifactId", "native-result").put("bytes", content.length).put("sha256", sha).put("offset", 0).put("nextOffset", content.length).put("eof", true).put("base64", Base64.getEncoder().encodeToString(content)));
        runs.startDebug(4, 17, 2); awaitState("SUCCEEDED");
        JsonNode manifest = runs.artifacts(4, row.get().id()); assertTrue(manifest.path("available").asBoolean(), row.get().errorMessage());
        String artifactId = manifest.path("artifacts").get(0).path("artifactId").asText(); assertNotEquals("native-result", artifactId);
        reset(worker);
        HdiCoreRuns.ArtifactChunk data = runs.artifact(4, row.get().id(), artifactId, 0, 1024);
        assertArrayEquals(content, data.bytes()); assertTrue(data.eof()); verifyNoInteractions(worker);
    }
    @Test void artifactHashMismatchPreservesRealEngineSuccessButDoesNotPublishPartialResult() throws Exception {
        ObjectNode success = view("SUCCEEDED", true); success.set("artifacts", HdiProtocol.JSON.createArrayNode().add(HdiProtocol.JSON.createObjectNode().put("artifactId", "native-result").put("name", "result.csv").put("bytes", content.length).put("sha256", "0".repeat(64))));
        when(worker.submit(any(), any())).thenReturn(success);
        when(worker.artifact(any(), anyString(), anyLong(), anyInt())).thenReturn(HdiProtocol.JSON.createObjectNode().put("artifactId", "native-result").put("bytes", content.length).put("sha256", "0".repeat(64)).put("offset", 0).put("nextOffset", content.length).put("eof", true).put("base64", Base64.getEncoder().encodeToString(content)));
        runs.startDebug(4, 17, 2); awaitState("SUCCEEDED");
        assertFalse(runs.artifacts(4, row.get().id()).path("available").asBoolean()); assertTrue(row.get().errorMessage().contains("产物"));
    }
    @Test void restartQuarantinesPendingRunsWithoutDispatchingThem() {
        when(store.unfinished()).thenReturn(List.of(row.get())); runs.initialize();
        assertEquals("UNKNOWN", row.get().state()); verifyNoInteractions(worker, compiler);
    }
    @Test void unauthorizedRunCannotReadStatusLogsMetricsOrArtifacts() {
        assertThrows(HdiTaskFault.class, () -> runs.status(999, row.get().id()));
        assertThrows(HdiTaskFault.class, () -> runs.logs(999, row.get().id(), 0, 100));
        assertThrows(HdiTaskFault.class, () -> runs.moduleMetrics(999, row.get().id()));
        assertThrows(HdiTaskFault.class, () -> runs.artifacts(999, row.get().id()));
        verifyNoInteractions(worker);
    }
    @Test void missingMetricsRemainExplicitlyUncollectedAndRowsAreNull() {
        assertFalse(runs.moduleMetrics(4, row.get().id()).path("available").asBoolean());
        assertEquals("NOT_COLLECTED", runs.moduleMetrics(4, row.get().id()).path("reason").asText());
    }
}
