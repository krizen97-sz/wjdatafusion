package com.hm.manage.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class AutoInspectionPlanScheduleMatcherTest
{
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalDate MONDAY = LocalDate.of(2026, 8, 31);

    @Test
    void shouldMatchSingleDailyExecution()
    {
        AutoInspectionPlanScheduleMatcher.TodaySchedule schedule = AutoInspectionPlanScheduleMatcher.resolve(
                "0 0 8 * * ?", LocalDateTime.of(2026, 8, 1, 10, 0), MONDAY, ZONE);

        assertTrue(schedule.scheduled());
        assertEquals("08:00", schedule.display());
    }

    @Test
    void shouldSupportRuoYiCronWithYearField()
    {
        AutoInspectionPlanScheduleMatcher.TodaySchedule schedule = AutoInspectionPlanScheduleMatcher.resolve(
                "0 0 8 31 8 ? 2026", null, MONDAY, ZONE);

        assertTrue(schedule.scheduled());
        assertEquals("08:00", schedule.display());
    }

    @Test
    void shouldMatchMultipleExecutionsAndRespectWeekday()
    {
        AutoInspectionPlanScheduleMatcher.TodaySchedule interval = AutoInspectionPlanScheduleMatcher.resolve(
                "0 0/5 * * * ?", null, MONDAY, ZONE);
        AutoInspectionPlanScheduleMatcher.TodaySchedule sunday = AutoInspectionPlanScheduleMatcher.resolve(
                "0 0 8 ? * SUN", null, MONDAY, ZONE);

        assertTrue(interval.scheduled());
        assertEquals("00:00起，多次执行", interval.display());
        assertFalse(sunday.scheduled());
    }

    @Test
    void shouldExcludePastSlotWhenPlanWasCreatedAfterExecutionTime()
    {
        AutoInspectionPlanScheduleMatcher.TodaySchedule schedule = AutoInspectionPlanScheduleMatcher.resolve(
                "0 0 8 * * ?", LocalDateTime.of(2026, 8, 31, 12, 0), MONDAY, ZONE);

        assertFalse(schedule.scheduled());
    }

    @Test
    void shouldFailClosedForInvalidCron()
    {
        AutoInspectionPlanScheduleMatcher.TodaySchedule schedule = AutoInspectionPlanScheduleMatcher.resolve(
                "not-a-cron", null, MONDAY, ZONE);

        assertFalse(schedule.scheduled());
    }
}
