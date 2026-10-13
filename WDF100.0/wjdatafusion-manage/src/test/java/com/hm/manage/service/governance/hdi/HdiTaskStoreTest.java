package com.hm.manage.service.governance.hdi;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import static org.junit.jupiter.api.Assertions.*;

class HdiTaskStoreTest {
    HdiTaskStore store; JdbcTemplate jdbc;
    @BeforeEach void initialize() {
        var data = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        new ResourceDatabasePopulator(new FileSystemResource(Path.of("../sql/data_governance_hdi_core_upgrade_20261013.sql"))).execute(data);
        jdbc = new JdbcTemplate(data); store = new HdiTaskStore(jdbc, new DataSourceTransactionManager(data));
    }
    HdiTaskStore.PreparedVisual prepared(long id, long revision, String name) {
        String visual = "{\"flowVo\":{\"id\":" + id + ",\"flowDisplayName\":\"" + name + "\"},\"nodes\":{}}";
        String snapshot = "{\"revision\":" + revision + ",\"visual\":" + visual + "}";
        return new HdiTaskStore.PreparedVisual(visual, snapshot, HdiTaskStore.sha256(snapshot.getBytes(StandardCharsets.UTF_8)), "[]");
    }
    HdiTaskStore.Task task() { return store.save(7, null, null, "真实存储用例", -1, (id, revision) -> prepared(id, revision, "first")); }
    @Test void springWiringUsesPlatformMasterEvenWhenAnotherBusinessProvidesTheOnlyJdbcTemplate() {
        var unrelated = org.mockito.Mockito.mock(JdbcTemplate.class);
        try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            context.registerBean("masterDataSource", javax.sql.DataSource.class, () -> jdbc.getDataSource());
            context.registerBean("whitelistPostgresJdbcTemplate", JdbcTemplate.class, () -> unrelated);
            context.register(HdiTaskStore.class); context.refresh();
            org.mockito.Mockito.clearInvocations(unrelated); // Ignore Spring's InitializingBean lifecycle, not database queries.
            assertEquals(store.workspace(), context.getBean(HdiTaskStore.class).workspace());
            org.mockito.Mockito.verifyNoInteractions(unrelated);
        }
    }
    @Test void savesImmutableRevisionsAndRejectsForeignOwnersAndStaleWindows() {
        var first = task(); var snapshot = store.revision(7, first.id(), 1);
        var second = store.save(7, first.id(), 1L, "修改后的任务", -1, (id, revision) -> prepared(id, revision, "second"));
        assertEquals(2, second.revision()); assertEquals(snapshot, store.revision(7, first.id(), 1));
        assertEquals(409, assertThrows(HdiTaskFault.class, () -> store.save(7, first.id(), 1L, "陈旧窗口", -1, (id, revision) -> prepared(id, revision, "stale"))).status());
        assertEquals(404, assertThrows(HdiTaskFault.class, () -> store.get(8, first.id())).status());
        assertEquals(0, store.count(8, null, null)); assertEquals(1, store.count(7, "修改", null));
        assertEquals(0, store.count(7, null, null, List.of())); assertEquals(1, store.count(7, null, null, List.of("DRAFT")));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM dg_hdi_revision", Integer.class));
    }
    @Test void preparationOrHashFailureRollsBackNewTaskAndRevisionTogether() {
        assertThrows(IllegalStateException.class, () -> store.save(7, null, null, "编译中断", -1, (id, revision) -> { throw new IllegalStateException(); }));
        assertThrows(HdiTaskFault.class, () -> store.save(7, null, null, "摘要篡改", -1, (id, revision) -> new HdiTaskStore.PreparedVisual("{}", "{}", "0".repeat(64), "[]")));
        assertEquals(0, store.count(7, null, null)); assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dg_hdi_revision", Integer.class));
    }
    @Test void onlyOneConcurrentSaveWinsTheSameRevision() throws Exception {
        var task = task(); var ready = new CountDownLatch(2); var start = new CountDownLatch(1); var pool = Executors.newFixedThreadPool(2);
        try {
            var action = (java.util.concurrent.Callable<Boolean>) () -> { ready.countDown(); assertTrue(start.await(3, TimeUnit.SECONDS)); try { store.save(7, task.id(), 1L, "同时编辑", -1, (id, revision) -> prepared(id, revision, "next")); return true; } catch (HdiTaskFault error) { assertEquals(409, error.status()); return false; } };
            var one = pool.submit(action); var two = pool.submit(action); assertTrue(ready.await(3, TimeUnit.SECONDS)); start.countDown();
            assertNotEquals(one.get(5, TimeUnit.SECONDS), two.get(5, TimeUnit.SECONDS)); assertEquals(2, store.get(7, task.id()).revision());
        } finally { pool.shutdownNow(); }
    }
    @Test void runIdsAreUniqueAndTerminalOrStopStatesCannotBeReversedByLatePolling() {
        var task = task(); var run = store.reserveRun(7, task.id(), 1, "DEBUG", null);
        assertEquals(Long.toString(run.nativeFlowId() + 2), run.execId());
        assertThrows(HdiTaskFault.class, () -> store.reserveRun(7, task.id(), 1, "RUN", null));
        store.transition(run, "STOPPING", "{}", null); store.transition(run, "RUNNING", "{}", null);
        assertEquals("STOPPING", store.run(7, run.id()).state());
        store.transition(run, "CANCELLED", "{}", null); store.transition(run, "RUNNING", "{}", null);
        assertEquals("CANCELLED", store.get(7, task.id()).state());
        var next = store.reserveRun(7, task.id(), 1, "RUN", null); assertTrue(next.nativeFlowId() >= run.nativeFlowId() + 100000);
        assertThrows(HdiTaskFault.class, () -> store.run(8, next.id()));
    }
    @Test void scheduleClaimsAreAtomicVersionBoundAndSkipOverlapWithoutDuplicateDispatch() {
        var task = task(); store.publish(7, task.id(), 1);
        Instant fire = Instant.now().plusSeconds(60).truncatedTo(ChronoUnit.MILLIS), next = fire.plusSeconds(86400);
        var plan = store.saveSchedule(7, task.id(), 1, "{\"zoneId\":\"Asia/Shanghai\"}", true, fire);
        var run = store.claimScheduledRun(plan, fire, next, "test-trigger-1"); assertNotNull(run);
        assertNull(store.claimScheduledRun(plan, fire, next, "test-trigger-1"));
        var advanced = store.getSchedule(7, task.id()); assertEquals(next, advanced.nextFireAt()); assertEquals(1, run.revision());
        assertNull(store.claimScheduledRun(advanced, next, next.plusSeconds(86400), "test-trigger-2"));
        assertEquals("SKIPPED_OVERLAP", store.getSchedule(7, task.id()).lastOutcome()); assertEquals(1, store.runs(7, task.id()).size());
        store.save(7, task.id(), 1L, "后续草稿", -1, (id, revision) -> prepared(id, revision, "new")); store.publish(7, task.id(), 2);
        assertEquals(1, store.getSchedule(7, task.id()).revision());
        store.disableSchedule(7, task.id()); assertFalse(store.getSchedule(7, task.id()).enabled());
        assertNull(store.claimScheduledRun(advanced, next, next.plusSeconds(86400), "test-trigger-3"));
    }
    @Test void scheduleRequiresPublishedCurrentRevisionAndRecordsMissedFireWithoutRunning() {
        var task = task(); Instant fire = Instant.now().plusSeconds(60).truncatedTo(ChronoUnit.MILLIS);
        assertThrows(HdiTaskFault.class, () -> store.saveSchedule(7, task.id(), 1, "{}", true, fire));
        store.publish(7, task.id(), 1); var plan = store.saveSchedule(7, task.id(), 1, "{}", true, fire);
        assertTrue(store.skipScheduledFire(plan, fire, fire.plusSeconds(86400), "SKIPPED_RESTART"));
        assertEquals(0, store.runs(7, task.id()).size()); assertEquals("SKIPPED_RESTART", store.getSchedule(7, task.id()).lastOutcome());
    }
    @Test void enabledScheduleProjectsReadyOnlyWhileIdleAndFiltersMatchTheDisplayedState() {
        var task = task(); store.publish(7, task.id(), 1);
        store.saveSchedule(7, task.id(), 1, "{}", true, Instant.now().plusSeconds(60));
        assertTrue(HdiTaskStore.isScheduleReady(store.get(7, task.id())));
        assertEquals(1, store.count(7, null, null, List.of("SCHEDULE_READY")));
        assertEquals(0, store.count(7, null, null, List.of("READY")));
        var run = store.reserveRun(7, task.id(), 1, "RUN", null);
        assertFalse(HdiTaskStore.isScheduleReady(store.get(7, task.id())));
        assertEquals(0, store.count(7, null, null, List.of("SCHEDULE_READY")));
        assertEquals(1, store.count(7, null, null, List.of("SCHEDULE_READY", "QUEUED")));
        store.transition(run, "SUCCEEDED", "{}", null);
        assertEquals("SUCCEEDED", store.run(7, run.id()).state());
        assertEquals(1, store.list(7, null, null, List.of("SCHEDULE_READY"), 1, 10).size());
        assertEquals(0, store.count(7, null, null, List.of("SUCCEEDED")));
        store.disableSchedule(7, task.id());
        assertFalse(HdiTaskStore.isScheduleReady(store.get(7, task.id())));
        assertEquals(1, store.count(7, null, null, List.of("SUCCEEDED")));
    }
}
