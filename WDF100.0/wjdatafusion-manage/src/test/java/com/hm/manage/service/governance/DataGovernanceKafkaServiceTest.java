package com.hm.manage.service.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hm.common.exception.ServiceException;
import com.hm.manage.service.governance.DataGovernanceKafkaModels.*;
import com.hm.manage.service.governance.DataGovernanceModels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DataGovernanceKafkaServiceTest
{
    @TempDir Path directory;
    DataGovernanceKafkaProperties properties;
    DataGovernanceKafkaStore store;
    DataGovernanceRunRepository runs;
    DataGovernanceKafkaTransport transport;
    DataGovernanceKafkaTransport.Session session;
    DataGovernanceScheduleDelivery ftp;
    DataGovernanceKafkaService service;
    DataGovernanceArtifactStore artifacts;
    ProfileView profile;
    Map<Integer, Long> actual;
    @BeforeEach void setup()
    {
        properties = new DataGovernanceKafkaProperties(); properties.setEnabled(true); properties.setStorageDir(directory.toString());
        store = new DataGovernanceKafkaStore(properties); runs = mock(DataGovernanceRunRepository.class);
        transport = mock(DataGovernanceKafkaTransport.class); session = mock(DataGovernanceKafkaTransport.Session.class); ftp = mock(DataGovernanceScheduleDelivery.class);
        var artifactProperties = new DataGovernanceProperties(); artifactProperties.setStorageDir(directory.resolve("local-artifact-fixtures").toString());
        artifacts = new DataGovernanceArtifactStore(artifactProperties);
        service = newService(); actual = new TreeMap<>(); actual.put(0, null);
        when(transport.open(any())).thenReturn(session); when(session.partitions()).thenReturn(List.of(0));
        when(session.committed()).thenAnswer(call -> new TreeMap<>(actual)); when(session.beginningOffsets()).thenReturn(Map.of(0, 0L));
        when(session.fetch(anyMap(), eq(100))).thenReturn(List.of(record(0, "{\"synthetic\":1}")));
        doAnswer(call -> { Map<Integer, Long> values = call.getArgument(0); actual.putAll(values); return null; }).when(session).commit(anyMap());
        profile = service.create(new ProfileRequest("本地合成源", List.of("127.0.0.1:19092"), "synthetic-topic", "rynew-governance-7-synthetic", null), 7);
    }
    DataGovernanceKafkaService newService() { return new DataGovernanceKafkaService(properties, store, runs, () -> transport, () -> ftp, () -> artifacts); }
    DataGovernanceKafkaTransport.Record record(long offset, String body) { return new DataGovernanceKafkaTransport.Record(0, offset, body.getBytes(StandardCharsets.UTF_8)); }
    @AfterEach void close() { store.close(); }
    StoredRun attach(ReceiptSummary receipt, String status, DataGovernanceScheduleDelivery.Binding delivery)
    {
        StoredRun stored = new StoredRun(); stored.ownerId = 7; stored.inputJson = service.read(receipt.id(), 7).inputJson(); stored.run = new TestRun();
        stored.run.id = UUID.randomUUID().toString(); stored.run.status = "SUCCEEDED"; stored.run.cleanupConfirmed = true;
        stored.run.definitionHash = "a".repeat(64);
        if (!status.equals("EMPTY")) {
            var stage = artifacts.begin(stored);
            artifacts.capture(stage, "synthetic.txt", "text/plain", 3, out -> out.write(new byte[]{1, 2, 3}));
            artifacts.publish(stage, stored);
        }
        stored.run.status = status;
        when(runs.find(stored.run.id)).thenReturn(stored);
        if (delivery != null) when(ftp.target(delivery.id(), 7)).thenReturn(delivery);
        service.attachRun(receipt.id(), stored.run.id, delivery, 7); return stored;
    }
    @Test void receivingIsPrivateUncommittedBoundedAndOffsetsAreStringsNotPrefetchPositions() throws Exception
    {
        List<DataGovernanceKafkaTransport.Record> prefetched = new ArrayList<>();
        for (long i = 0; i < 110; i++) prefetched.add(record(i, "message"));
        when(session.fetch(anyMap(), eq(100))).thenReturn(prefetched);
        var receipt = service.receive(profile.id(), 7); assertEquals("RECEIVED", receipt.status()); assertEquals(100, receipt.recordCount());
        assertEquals("100", receipt.nextOffsets().get("0")); assertNull(actual.get(0)); verify(session, never()).commit(anyMap());
        var rows = new ObjectMapper().readTree(service.read(receipt.id(), 7).inputJson()); assertEquals("99", rows.get(99).path("kafka_offset").asText()); assertEquals("message", rows.get(0).path("k_message").asText());
        assertThrows(ServiceException.class, () -> service.read(receipt.id(), 8)); assertTrue(service.receipts(8).isEmpty());
        String list = new ObjectMapper().writeValueAsString(service.receipts(7)); assertFalse(list.contains("k_message"));
        assertThrows(ServiceException.class, () -> service.receive(profile.id(), 7));
    }
    @Test void offsetHolesAndAlreadyConsumedBatchPrefixAreHandledWithoutSkippingRealRows()
    {
        actual.put(0, 10L); when(session.fetch(anyMap(), eq(100))).thenReturn(List.of(record(9, "old"), record(10, "new"), record(20, "new")));
        var receipt = service.receive(profile.id(), 7); assertEquals(2, receipt.recordCount()); assertEquals("21", receipt.nextOffsets().get("0"));
        attach(receipt, "SUCCEEDED", null); assertEquals("COMMITTED", service.commit(receipt.id(), 7).status()); assertEquals(21L, actual.get(0));
    }
    @Test void invalidLargeAndOutOfOrderRecordsRejectWholeBatchAndNeverCommit()
    {
        for (var records : List.of(List.of(record(0, "good"), new DataGovernanceKafkaTransport.Record(0, 1, new byte[]{(byte) 0xff})),
            List.of(record(0, "x".repeat(256 * 1024))), List.of(record(0, "first"), record(0, "duplicate")))) {
            when(session.fetch(anyMap(), eq(100))).thenReturn(records); var receipt = service.receive(profile.id(), 7);
            assertEquals("FAILED", receipt.status()); assertTrue(receipt.leaseHeld()); assertNull(service.read(receipt.id(), 7).inputJson());
            service.release(receipt.id(), 7);
        }
        verify(session, never()).commit(anyMap());
    }
    @Test void profileNamesGroupsAllowedBrokersAndLiveLeaseCannotBeBypassed()
    {
        assertThrows(ServiceException.class, () -> service.create(new ProfileRequest("wrong", List.of("10.1.1.1:9092"), "t", "rynew-governance-7-x", null), 7));
        assertThrows(ServiceException.class, () -> service.create(new ProfileRequest("wrong", List.of("127.0.0.1:19092"), "t", "other-group", null), 7));
        assertThrows(ServiceException.class, () -> service.create(new ProfileRequest("duplicate", profile.bootstrapServers(), profile.topic(), profile.groupId(), null), 7));
        var receipt = service.receive(profile.id(), 7);
        assertThrows(ServiceException.class, () -> service.update(profile.id(), new ProfileRequest("changed", profile.bootstrapServers(), "another", profile.groupId(), profile.revision()), 7));
        service.release(receipt.id(), 7); assertEquals("RELEASED", service.read(receipt.id(), 7).receipt().status()); assertNull(actual.get(0));
    }
    @Test void onlyServerAssociatedMatchingInputAndRealSuccessfulCleanedRunCanCommit()
    {
        var receipt = service.receive(profile.id(), 7);
        assertThrows(ServiceException.class, () -> service.commit(receipt.id(), 7));
        var stored = attach(receipt, "RUNNING", null); assertThrows(ServiceException.class, () -> service.commit(receipt.id(), 7));
        stored.run.status = "SUCCEEDED"; stored.run.cleanupConfirmed = false; assertThrows(ServiceException.class, () -> service.commit(receipt.id(), 7));
        stored.run.cleanupConfirmed = true; stored.inputJson += " "; assertThrows(ServiceException.class, () -> service.commit(receipt.id(), 7));
        stored.inputJson = service.read(receipt.id(), 7).inputJson(); assertEquals("COMMITTED", service.commit(receipt.id(), 7).status());
        assertEquals(1L, actual.get(0)); assertFalse(service.read(receipt.id(), 7).receipt().leaseHeld());
        service.commit(receipt.id(), 7); verify(session, times(1)).commit(anyMap());
    }
    @Test void attachIsImmutableAndCannotSubstituteAnotherOwnerOrDeliveryTarget()
    {
        var receipt = service.receive(profile.id(), 7); var stored = attach(receipt, "SUCCEEDED", null);
        assertThrows(ServiceException.class, () -> service.attachRun(receipt.id(), UUID.randomUUID().toString(), null, 7));
        assertThrows(ServiceException.class, () -> service.release(receipt.id(), 7));
        stored.ownerId = 8; assertThrows(ServiceException.class, () -> service.commit(receipt.id(), 7)); verify(session, never()).commit(anyMap());
    }
    @Test void ftpMustBeDeliveredToTheFrozenTargetWhileProvenEmptyOutputMaySkipDelivery()
    {
        var binding = new DataGovernanceScheduleDelivery.Binding(UUID.randomUUID().toString(), "目标", "a".repeat(64));
        var receipt = service.receive(profile.id(), 7); var stored = attach(receipt, "SUCCEEDED", binding);
        when(ftp.find(stored.run.id, binding.id(), 7)).thenReturn(new DataGovernanceScheduleDelivery.Delivery(UUID.randomUUID().toString(), "DELIVERED", null, "b".repeat(64)));
        assertThrows(ServiceException.class, () -> service.commit(receipt.id(), 7));
        stored.run.status = "EMPTY"; stored.run.output = List.of("not-empty"); assertThrows(ServiceException.class, () -> service.commit(receipt.id(), 7));
        stored.run.output = List.of(); stored.run.artifactsManifestAvailable = false; stored.run.artifactCount = 0;
        assertEquals("COMMITTED", service.commit(receipt.id(), 7).status());
        assertEquals("SKIPPED_EMPTY", service.read(receipt.id(), 7).receipt().deliveryStatus());
    }
    @Test void unknownCommitKeepsLeaseAndRetryReadsActualOffsetsBeforeAnyResend()
    {
        var receipt = service.receive(profile.id(), 7); attach(receipt, "SUCCEEDED", null);
        doAnswer(call -> { actual.putAll(call.getArgument(0)); throw new RuntimeException("simulated lost reply"); }).when(session).commit(anyMap());
        assertEquals("COMMIT_UNKNOWN", service.commit(receipt.id(), 7).status()); assertTrue(service.read(receipt.id(), 7).receipt().leaseHeld());
        assertThrows(ServiceException.class, () -> service.release(receipt.id(), 7));
        service = newService(); assertEquals("COMMITTED", service.commit(receipt.id(), 7).status()); verify(session, times(1)).commit(anyMap());
    }
    @Test void advancedOrRolledBackGroupOffsetsAreNeverOverwritten()
    {
        actual.put(0, 10L); when(session.fetch(anyMap(), eq(100))).thenReturn(List.of(record(10, "new")));
        var receipt = service.receive(profile.id(), 7); attach(receipt, "SUCCEEDED", null);
        actual.put(0, 12L); assertEquals("OFFSET_CONFLICT", service.commit(receipt.id(), 7).status());
        actual.put(0, 9L); assertEquals("OFFSET_CONFLICT", service.commit(receipt.id(), 7).status()); verify(session, never()).commit(anyMap());
    }
    @Test void restartKeepsLeasesWithoutAutomaticReceiveOrCommitAndStorageRejectsSecondWriter()
    {
        var receipt = service.receive(profile.id(), 7); Receipt interrupted = store.receipt(receipt.id()); interrupted.status = "RECEIVING"; store.saveReceipt(interrupted);
        clearInvocations(transport, session); service = newService(); assertEquals("FAILED", service.read(receipt.id(), 7).receipt().status());
        assertThrows(ServiceException.class, () -> service.receive(profile.id(), 7)); verifyNoInteractions(transport, session);
        var second = new DataGovernanceKafkaStore(properties); try { assertThrows(ServiceException.class, second::profiles); } finally { second.close(); }
    }
    @Test void disabledFeatureDoesNotInitializeStorageOrClients()
    {
        properties.setEnabled(false); clearInvocations(transport, session); assertTrue(service.profiles(7).isEmpty()); assertTrue(service.receipts(7).isEmpty()); verifyNoInteractions(transport, session);
    }
    @Test void completedArtifactsAreVerifiedByManifestCountDefinitionAndActualFileHash() throws Exception
    {
        var receipt = service.receive(profile.id(), 7); var stored = attach(receipt, "SUCCEEDED", null);
        stored.run.artifactCount = 2; assertThrows(ServiceException.class, () -> service.commit(receipt.id(), 7));
        stored.run.artifactCount = 1; stored.run.definitionHash = "b".repeat(64); assertThrows(ServiceException.class, () -> service.commit(receipt.id(), 7));
        stored.run.definitionHash = "a".repeat(64);
        var manifest = artifacts.manifest(stored.run.id, 7); var artifact = manifest.artifacts().get(0);
        Path file = directory.resolve("local-artifact-fixtures/artifacts").resolve(stored.run.id).resolve("content").resolve(artifact.id() + ".bin");
        java.nio.file.Files.write(file, new byte[]{3, 2, 1});
        assertThrows(ServiceException.class, () -> service.commit(receipt.id(), 7)); verify(session, never()).commit(anyMap());
        java.nio.file.Files.write(file, new byte[]{1, 2, 3});
        service = new DataGovernanceKafkaService(properties, store, runs, () -> transport, () -> ftp);
        assertThrows(ServiceException.class, () -> service.commit(receipt.id(), 7), "Missing optional artifact service cannot bypass the gate");
    }
    @Test void partialUnknownCommitRetriesOnlyPartitionsStillAtTheOriginalPosition()
    {
        actual.put(1, null); when(session.partitions()).thenReturn(List.of(0, 1)); when(session.beginningOffsets()).thenReturn(Map.of(0, 0L, 1, 0L));
        when(session.fetch(anyMap(), eq(100))).thenReturn(List.of(record(0, "a"), new DataGovernanceKafkaTransport.Record(1, 0, "b".getBytes(StandardCharsets.UTF_8))));
        var receipt = service.receive(profile.id(), 7); attach(receipt, "SUCCEEDED", null);
        var requests = new ArrayList<Map<Integer, Long>>();
        doAnswer(call -> {
            Map<Integer, Long> proposed = call.getArgument(0); requests.add(new TreeMap<>(proposed));
            if (requests.size() == 1) { actual.put(0, 1L); throw new RuntimeException("one partition reply was lost"); }
            actual.putAll(proposed); return null;
        }).when(session).commit(anyMap());
        assertEquals("COMMIT_UNKNOWN", service.commit(receipt.id(), 7).status());
        assertEquals("COMMITTED", service.commit(receipt.id(), 7).status());
        assertEquals(List.of(Map.of(0, 1L, 1, 1L), Map.of(1, 1L)), requests);
    }
    @Test void groupOffsetChangeDuringReceiveNeverPublishesAnExecutableReceipt()
    {
        when(session.fetch(anyMap(), eq(100))).thenAnswer(call -> { actual.put(0, 5L); return List.of(record(0, "synthetic")); });
        var receipt = service.receive(profile.id(), 7); assertEquals("FAILED", receipt.status()); assertNull(service.read(receipt.id(), 7).inputJson());
        verify(session, never()).commit(anyMap()); assertTrue(receipt.leaseHeld());
    }

}
