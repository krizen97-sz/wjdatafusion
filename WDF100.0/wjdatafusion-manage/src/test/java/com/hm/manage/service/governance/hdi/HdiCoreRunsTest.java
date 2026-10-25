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
    @Test void unexpectedCompilerFailureStillCancelsTheUnsubmittedReservation() throws Exception {
        when(compiler.compile(anyString(), anyString(), anyMap())).thenThrow(new IllegalStateException("do-not-log-business-value"));
        runs.startDebug(4, 17, 2); awaitState("FAILED");
        assertTrue(row.get().errorMessage().contains("尚未提交")); assertFalse(row.get().errorMessage().contains("business-value"));
        verify(worker).cancel(any()); verify(worker, never()).submit(any(), any());
    }
    @Test void bareReservationCannotResolveUnknownOutcomeOrEraseItsError() throws Exception {
        when(worker.submit(any(), any())).thenThrow(new HdiWorkerClient.Failure("EOF", true));
        runs.startDebug(4, 17, 2); awaitState("UNKNOWN");
        String error = row.get().errorMessage(); when(worker.status(any())).thenReturn(view("RESERVED", false));
        assertEquals("UNKNOWN", runs.status(4, row.get().id()).path("state").asText());
        assertEquals(error, row.get().errorMessage()); verify(worker, times(1)).submit(any(), any());
    }
    @Test void explicitRejectionAndConfirmedBareReservationCleanlyFailInsteadOfHanging() throws Exception {
        when(worker.submit(any(), any())).thenThrow(new HdiWorkerClient.Failure("LOCAL_OUTPUT_REQUIRED", false));
        when(worker.status(any())).thenReturn(view("RESERVED", false));
        runs.startDebug(4, 17, 2); awaitState("FAILED");
        assertTrue(row.get().errorMessage().contains("传输协议")); verify(worker).cancel(any()); verify(worker, times(1)).submit(any(), any());
    }
    @Test void negativeReplyWithObservedExecutionRemainsUnknownAndIsNeverAutomaticallyCancelled() throws Exception {
        when(worker.submit(any(), any())).thenThrow(new HdiWorkerClient.Failure("WORKER_INTERNAL_ERROR", false));
        when(worker.status(any())).thenReturn(view("RUNNING", false));
        runs.startDebug(4, 17, 2); awaitState("UNKNOWN"); verify(worker, never()).cancel(any());
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
    private ObjectNode stoppedPartial() {
        ObjectNode result = view("CANCELLED", true).put("nativeState", "stop").put("stopRequestedByUser", true)
            .put("recoveryRequired", false).put("artifactCompleteness", "PARTIAL");
        result.putArray("artifacts").add(HdiProtocol.JSON.createObjectNode().put("artifactId", "stopped-result")
            .put("name", "stopped.csv").put("bytes", content.length).put("sha256", HdiTaskStore.sha256(content)));
        return result;
    }
    @Test void explicitNativeStopPublishesPartialFilesWithoutChangingCancellationToSuccess() throws Exception {
        when(worker.submit(any(), any())).thenReturn(stoppedPartial()); String sha = HdiTaskStore.sha256(content);
        when(worker.artifact(any(), eq("stopped-result"), eq(0L), anyInt())).thenReturn(HdiProtocol.JSON.createObjectNode()
            .put("artifactId", "stopped-result").put("bytes", content.length).put("sha256", sha).put("offset", 0)
            .put("nextOffset", content.length).put("eof", true).put("base64", Base64.getEncoder().encodeToString(content)));
        runs.startDebug(4, 17, 2); awaitState("CANCELLED");
        JsonNode status = runs.status(4, row.get().id()), manifest = runs.artifacts(4, row.get().id());
        assertEquals("CANCELLED", status.path("state").asText()); assertEquals("stop", status.path("nativeState").asText());
        assertEquals("PARTIAL", status.path("artifactCompleteness").asText());
        assertTrue(manifest.path("available").asBoolean()); assertEquals("PARTIAL", manifest.path("artifactCompleteness").asText());
        String id = manifest.path("artifacts").get(0).path("artifactId").asText(); reset(worker);
        assertArrayEquals(content, runs.artifact(4, row.get().id(), id, 0, 1024).bytes()); verifyNoInteractions(worker);
        assertThrows(HdiTaskFault.class, () -> runs.artifact(999, row.get().id(), id, 0, 1024));
    }
    private void rejectsPartial(ObjectNode observation, String state) throws Exception {
        when(worker.submit(any(), any())).thenReturn(observation); runs.startDebug(4, 17, 2); awaitState(state);
        assertFalse(runs.artifacts(4, row.get().id()).path("available").asBoolean());
        assertThrows(HdiTaskFault.class, () -> runs.artifact(4, row.get().id(), "stopped-result", 0, 1024));
        verify(worker, never()).artifact(any(), anyString(), anyLong(), anyInt());
    }
    @Test void cancellationWithoutExplicitUserIntentDoesNotPublishPartialFiles() throws Exception {
        ObjectNode observation = stoppedPartial(); observation.remove("stopRequestedByUser"); rejectsPartial(observation, "CANCELLED");
    }
    @Test void forcedStopTimeoutDoesNotPublishPartialFiles() throws Exception {
        rejectsPartial(stoppedPartial().put("errorCode", "ORIGINAL_STOP_TIMEOUT_PROCESS_CLOSED"), "CANCELLED");
    }
    @Test void incompleteCleanupCannotPublishPartialFiles() throws Exception {
        rejectsPartial(stoppedPartial().put("finalized", false), "STOPPING");
    }
    @Test void recoveryRequiredCannotPublishPartialFiles() throws Exception {
        rejectsPartial(stoppedPartial().put("recoveryRequired", true), "CANCELLED");
    }
    @Test void failureOrUnacknowledgedStopCannotPublishPartialFiles() throws Exception {
        rejectsPartial(stoppedPartial().put("nativeState", "running"), "CANCELLED");
    }
    @Test void externalOutputCompletionPreservesNativeStateAndExposesObservedErrorsWithoutInventingArtifacts() throws Exception {
        ObjectNode success = view("SUCCEEDED", true); success.put("nativeState", "finish"); success.putArray("artifacts");
        success.putArray("outputWarningCodes").add("JDBC_OUTPUT_ERROR_OBSERVED").add("JDBC_OUTPUT_ROWS_SKIPPED").add("not-a-trusted-message");
        when(worker.submit(any(), any())).thenReturn(success);
        runs.startDebug(4, 17, 2); awaitState("SUCCEEDED");
        JsonNode status = runs.status(4, row.get().id()); assertEquals("SUCCEEDED", status.path("engineState").asText());
        assertEquals("finish", status.path("nativeState").asText()); assertEquals(2, status.path("outputWarningCodes").size());
        assertTrue(status.path("error").asText().contains("数据库输出错误")); assertTrue(status.path("outputRows").isNull());
        JsonNode files = runs.artifacts(4, row.get().id()); assertTrue(files.path("available").asBoolean()); assertTrue(files.path("artifacts").isEmpty());
        verify(worker, never()).artifact(any(), anyString(), anyLong(), anyInt());
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
