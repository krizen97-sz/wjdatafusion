package com.hm.manage.service.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hm.common.exception.ServiceException;
import java.time.*;
import java.util.*;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DataGovernanceLocalSchedulerTest
{
    final ObjectMapper mapper = new ObjectMapper();
    final Map<String,DataGovernanceLocalScheduler.Schedule> records = new LinkedHashMap<>();
    final MutableClock clock = new MutableClock();
    DataGovernanceLocalStore store;
    DataGovernanceLocalService service;
    DataGovernanceLocalScheduler scheduler;
    DataGovernanceLocalService.Definition definition;

    @BeforeEach void open()
    {
        store = mock(DataGovernanceLocalStore.class); service = mock(DataGovernanceLocalService.class);
        when(store.list("schedules", DataGovernanceLocalScheduler.Schedule.class)).thenAnswer(call -> records.values().stream().map(this::copy).toList());
        when(store.read(eq("schedules"), anyString(), eq(DataGovernanceLocalScheduler.Schedule.class))).thenAnswer(call -> {
            var value = records.get(call.getArgument(1)); if (value == null) throw new ServiceException("missing", 404); return copy(value);
        });
        doAnswer(call -> { DataGovernanceLocalScheduler.Schedule value = call.getArgument(2); records.put(value.id, copy(value)); return null; }).when(store).write(eq("schedules"), anyString(), any());
        definition = new DataGovernanceLocalService.Definition(); definition.id = "flow-1"; definition.owner = 7; definition.revision = 1;
        definition.graph = new LinkedHashMap<>(Map.of("nodes", List.of(Map.of("id", "source", "value", "frozen-original"))));
        when(service.capture("flow-1", 7)).thenAnswer(call -> mapper.convertValue(definition, DataGovernanceLocalService.Definition.class));
        when(service.submitFrozen(anyString(), anyLong(), anyLong(), anyMap(), anyString())).thenReturn(run("run-1", "RUNNING", false));
        when(service.run(anyString(), anyLong())).thenReturn(run("run-1", "RUNNING", false));
        scheduler = create(Runnable::run);
    }
    @AfterEach void close() { scheduler.close(); }
    DataGovernanceLocalScheduler create(Executor executor) { return new DataGovernanceLocalScheduler(store, service, clock, executor); }
    DataGovernanceLocalScheduler.Schedule copy(DataGovernanceLocalScheduler.Schedule value) { return mapper.convertValue(value, DataGovernanceLocalScheduler.Schedule.class); }
    Map<String,Object> body(long revision, long definitionRevision) { return new LinkedHashMap<>(Map.of("name", "基础流程计划", "revision", revision, "definitionRevision", definitionRevision, "cron", "*/5 * * * * ?", "timeZone", "Asia/Shanghai")); }
    String plan() { return (String)scheduler.save(null, "flow-1", body(1, 1), 7).get("id"); }
    Map<String,Object> current(String id) { return scheduler.list("flow-1", 7).stream().filter(s -> id.equals(s.get("id"))).findFirst().orElseThrow(); }
    static Map<String,Object> run(String id, String state, boolean finalized) { return Map.of("id", id, "state", state, "finalized", finalized); }

    @Test void freezesGraphAndPersistsRequestBeforeDispatch()
    {
        String id = plan(); assertEquals(false, current(id).get("enabled")); assertNull(current(id).get("nextRunAt"));
        definition.graph.put("nodes", List.of(Map.of("id", "source", "value", "changed-current"))); definition.revision = 2;
        when(service.submitFrozen(anyString(), anyLong(), anyLong(), anyMap(), anyString())).thenAnswer(call -> {
            var pending = records.get(id); assertNotNull(pending.activeRequestId); assertNull(pending.activeRunId);
            assertEquals(pending.activeRequestId, call.getArgument(4)); assertEquals(1L, (Long)call.getArgument(2));
            assertTrue(call.getArgument(3).toString().contains("frozen-original")); assertFalse(call.getArgument(3).toString().contains("changed-current"));
            return run("run-1", "RUNNING", false);
        });
        scheduler.runNow(id, 7); assertEquals("run-1", current(id).get("activeRunId"));
        assertThrows(ServiceException.class, () -> scheduler.save(id, "flow-1", body(1, 2), 7));
        assertFalse(current(id).containsKey("graph"));
    }
    @Test void serializesPlansAndSkipsBusyAndMissedTicks()
    {
        String first = plan(), second = plan(); scheduler.enable(first, true, 1, 7); scheduler.enable(second, true, 1, 7);
        clock.advance(5); scheduler.tick(); verify(service, times(1)).submitFrozen(anyString(), anyLong(), anyLong(), anyMap(), anyString());
        assertThrows(ServiceException.class, () -> scheduler.runNow(second, 7));
        clock.advance(25); scheduler.tick(); verify(service, times(1)).submitFrozen(anyString(), anyLong(), anyLong(), anyMap(), anyString());
        assertEquals("2030-01-01T00:00:35Z", current(second).get("nextRunAt"));
        when(service.run("run-1", 7)).thenReturn(run("run-1", "SUCCEEDED", true)); clock.advance(1); scheduler.tick();
        assertNull(current(first).get("activeRunId")); clock.advance(4); scheduler.tick();
        verify(service, times(2)).submitFrozen(anyString(), anyLong(), anyLong(), anyMap(), anyString());
    }
    @Test void disabledPlanStillPollsItsAcceptedRunWithoutStoppingIt()
    {
        String id = plan(); scheduler.enable(id, true, 1, 7); scheduler.runNow(id, 7);
        scheduler.enable(id, false, 2, 7); assertEquals("run-1", current(id).get("activeRunId"));
        when(service.run("run-1", 7)).thenReturn(run("run-1", "SUCCEEDED", true)); clock.advance(1); scheduler.tick();
        assertNull(current(id).get("activeRunId")); assertEquals("PAUSED", current(id).get("state"));
        clock.advance(30); scheduler.tick(); verify(service, times(1)).submitFrozen(anyString(), anyLong(), anyLong(), anyMap(), anyString());
    }
    @Test void finalizedIsRequiredBeforeReleasingOverlapBarrier()
    {
        String id = plan(); scheduler.runNow(id, 7);
        when(service.run("run-1", 7)).thenReturn(run("run-1", "SUCCEEDED", false)); scheduler.tick();
        assertEquals("FINISHING", current(id).get("state")); assertThrows(ServiceException.class, () -> scheduler.runNow(id, 7));
        when(service.run("run-1", 7)).thenReturn(run("run-1", "SUCCEEDED", true)); scheduler.tick();
        assertNull(current(id).get("activeRequestId"));
    }
    @Test void unknownSubmissionRetainsIntentAndRecoveryNeverSubmits()
    {
        String id = plan(); when(service.submitFrozen(anyString(), anyLong(), anyLong(), anyMap(), anyString())).thenThrow(new ServiceException("unknown"));
        scheduler.runNow(id, 7); String request = (String)current(id).get("activeRequestId");
        assertEquals(true, current(id).get("recoveryRequired")); assertNull(current(id).get("activeRunId"));
        scheduler.recover(id, 7); verify(service).findRequest("flow-1", request, 7);
        assertEquals(request, current(id).get("activeRequestId")); assertThrows(ServiceException.class, () -> scheduler.enable(id, true, 1, 7));
        when(service.findRequest("flow-1", request, 7)).thenReturn(run("run-recovered", "SUCCEEDED", true));
        scheduler.recover(id, 7); assertNull(current(id).get("activeRequestId")); assertEquals(false, current(id).get("enabled"));
        verify(service, times(1)).submitFrozen(anyString(), anyLong(), anyLong(), anyMap(), anyString());
    }
    @Test void restartPausesUndispatchedIntentAndOldQueuedCallbackCannotSubmit()
    {
        scheduler.close(); List<Runnable> queue = new ArrayList<>(); scheduler = create(queue::add);
        String id = plan(); scheduler.runNow(id, 7); String request = (String)current(id).get("activeRequestId");
        scheduler.close(); scheduler = create(Runnable::run); assertEquals(true, current(id).get("recoveryRequired"));
        scheduler.recover(id, 7); queue.get(0).run(); clock.advance(100); scheduler.tick();
        assertEquals(request, current(id).get("activeRequestId")); verify(service, never()).submitFrozen(anyString(), anyLong(), anyLong(), anyMap(), anyString());
    }
    @Test void restartOnlyQueriesAcceptedRunAndInterruptedCompletionStaysPaused()
    {
        String id = plan(); scheduler.enable(id, true, 1, 7); scheduler.runNow(id, 7);
        scheduler.close(); scheduler = create(Runnable::run);
        assertEquals(true, current(id).get("recoveryRequired")); assertEquals(false, current(id).get("enabled"));
        when(service.run("run-1", 7)).thenReturn(run("run-1", "INTERRUPTED", true)); scheduler.recover(id, 7);
        assertNull(current(id).get("activeRequestId")); assertEquals("INTERRUPTED", current(id).get("state")); assertEquals(false, current(id).get("enabled"));
        scheduler.enable(id, true, 2, 7); assertEquals("READY", current(id).get("state"));
        verify(service, times(1)).submitFrozen(anyString(), anyLong(), anyLong(), anyMap(), anyString());
    }
    @Test void restartAndMissedWindowsNeverCatchUp()
    {
        String id = plan(); scheduler.enable(id, true, 1, 7); clock.advance(31); scheduler.tick();
        assertEquals("2030-01-01T00:00:35Z", current(id).get("nextRunAt"));
        scheduler.close(); clock.advance(100); scheduler = create(Runnable::run);
        assertEquals("2030-01-01T00:02:15Z", current(id).get("nextRunAt")); scheduler.tick();
        verify(service, never()).submitFrozen(anyString(), anyLong(), anyLong(), anyMap(), anyString());
    }
    @Test void invalidDraftCronOwnershipAndRevisionNeverWriteIntent()
    {
        Map<String,Object> invalid = body(1, 1); invalid.put("cron", "invalid");
        assertThrows(ServiceException.class, () -> scheduler.save(null, "flow-1", invalid, 7)); assertTrue(records.isEmpty());
        when(service.capture("flow-1", 7)).thenThrow(new ServiceException("invalid graph"));
        assertThrows(ServiceException.class, () -> plan()); assertTrue(records.isEmpty());
        doReturn(definition).when(service).capture("flow-1", 7); String id = plan();
        assertThrows(ServiceException.class, () -> scheduler.runNow(id, 8));
        assertThrows(ServiceException.class, () -> scheduler.recover(id, 8));
        assertThrows(ServiceException.class, () -> scheduler.enable(id, true, 9, 7));
        assertThrows(ServiceException.class, () -> scheduler.save(id, "flow-1", body(1, 2), 7));
        assertNull(current(id).get("activeRequestId")); verify(service, never()).submitFrozen(anyString(), anyLong(), anyLong(), anyMap(), anyString());
    }
    @Test void rejectedQueueRetainsDurableRequestForReadOnlyRecovery()
    {
        scheduler.close(); scheduler = create(task -> { throw new java.util.concurrent.RejectedExecutionException(); });
        String id = plan(); scheduler.runNow(id, 7); assertEquals(true, current(id).get("recoveryRequired"));
        assertNotNull(current(id).get("activeRequestId")); scheduler.recover(id, 7);
        verify(service, never()).submitFrozen(anyString(), anyLong(), anyLong(), anyMap(), anyString());
    }
    @Test void persistenceFailurePreventsDispatch()
    {
        String id = plan(); doThrow(new ServiceException("disk unavailable")).when(store).write(eq("schedules"), eq(id), any());
        assertThrows(ServiceException.class, () -> scheduler.runNow(id, 7));
        verify(service, never()).submitFrozen(anyString(), anyLong(), anyLong(), anyMap(), anyString());
    }
    static class MutableClock extends Clock
    {
        Instant now = Instant.parse("2030-01-01T00:00:00Z"); void advance(long seconds) { now = now.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
