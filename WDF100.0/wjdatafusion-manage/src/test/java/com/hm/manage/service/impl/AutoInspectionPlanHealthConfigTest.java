package com.hm.manage.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AutoInspectionPlanHealthConfigTest
{
    @Test
    void frequentPlanUsesIntervalAndActiveWindow()
    {
        AutoInspectionPlanHealthConfig config = AutoInspectionPlanHealthConfig.from(
                "{\"type\":\"interval\",\"interval\":5,\"intervalUnit\":\"minute\"}",
                "{\"activeStartTime\":\"06:00\",\"activeEndTime\":\"23:59\",\"dataDelayMinutes\":1}");

        assertEquals(5, config.getIntervalMinutes());
        assertEquals(1, config.getDataDelayMinutes());
        assertFalse(config.isActive(LocalDateTime.of(2026, 8, 24, 5, 59)));
        assertTrue(config.isActive(LocalDateTime.of(2026, 8, 24, 6, 0)));
        assertEquals(LocalDateTime.of(2026, 8, 24, 12, 5),
                config.alignSlot(LocalDateTime.of(2026, 8, 24, 12, 7, 38)));
    }

    @Test
    void expectedSlotsNeverCountFutureSlots()
    {
        AutoInspectionPlanHealthConfig config = AutoInspectionPlanHealthConfig.from(
                "{\"type\":\"interval\",\"interval\":5,\"intervalUnit\":\"minute\"}",
                "{\"activeStartTime\":\"06:00\",\"activeEndTime\":\"23:59\"}");

        LocalDateTime now = LocalDateTime.of(2026, 8, 24, 6, 11);
        assertEquals(3, config.expectedSlots(LocalDate.of(2026, 8, 24), now));
        assertEquals(0, config.expectedSlots(LocalDate.of(2026, 8, 25), now));
    }

    @Test
    void expectedSlotsFollowQuartzAlignedMinutesInsideBusinessWindow()
    {
        AutoInspectionPlanHealthConfig config = AutoInspectionPlanHealthConfig.from(
                "{\"type\":\"interval\",\"interval\":5,\"intervalUnit\":\"minute\"}",
                "{\"activeStartTime\":\"06:03\",\"activeEndTime\":\"06:20\"}");

        assertEquals(4, config.expectedSlots(LocalDate.of(2026, 8, 24),
                LocalDateTime.of(2026, 8, 24, 6, 20)));
    }

    @Test
    void newPlanDoesNotAccumulateMissingSlotsBeforeCreation()
    {
        AutoInspectionPlanHealthConfig config = AutoInspectionPlanHealthConfig.from(
                "{\"type\":\"interval\",\"interval\":5,\"intervalUnit\":\"minute\"}",
                "{\"activeStartTime\":\"00:00\",\"activeEndTime\":\"23:59\"}");
        LocalDateTime now = LocalDateTime.of(2026, 8, 24, 10, 20);

        assertEquals(4, config.expectedSlots(LocalDate.of(2026, 8, 24), now,
                LocalDateTime.of(2026, 8, 24, 10, 3)));
    }

    @Test
    void monitorStartTimeRoundTripsInsideHealthConfig()
    {
        AutoInspectionPlanHealthConfig config = AutoInspectionPlanHealthConfig.from(
                "{\"type\":\"interval\",\"interval\":5,\"intervalUnit\":\"minute\"}",
                "{\"monitorStartTime\":\"2026-08-24 10:03:00\"}");

        assertEquals(LocalDateTime.of(2026, 8, 24, 10, 3), config.getMonitorStartTime());
        assertEquals("2026-08-24 10:03:00", config.toMap().get("monitorStartTime"));
    }
}
