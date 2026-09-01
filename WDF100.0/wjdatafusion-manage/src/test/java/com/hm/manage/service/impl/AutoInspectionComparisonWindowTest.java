package com.hm.manage.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AutoInspectionComparisonWindowTest
{
    @Test
    void dailyWindowRejectsPreviousDayBaseline()
    {
        AutoInspectionComparisonWindow.Window current = AutoInspectionComparisonWindow.resolve(
                AutoInspectionComparisonWindow.SCOPE_DAY,
                LocalDateTime.of(2026, 9, 1, 0, 5));

        assertFalse(AutoInspectionComparisonWindow.isSameWindow(
                current, "DAY:2026-08-31", LocalDateTime.of(2026, 8, 31, 23, 55)));
        assertEquals("DAY:2026-09-01", current.key());
        assertEquals(LocalDateTime.of(2026, 9, 1, 0, 0), current.start());
    }

    @Test
    void dailyWindowKeepsSamplesFromSameDay()
    {
        AutoInspectionComparisonWindow.Window current = AutoInspectionComparisonWindow.resolve(
                AutoInspectionComparisonWindow.SCOPE_DAY,
                LocalDateTime.of(2026, 9, 1, 0, 10));

        assertTrue(AutoInspectionComparisonWindow.isSameWindow(
                current, "", LocalDateTime.of(2026, 9, 1, 0, 5)));
    }

    @Test
    void continuousWindowDoesNotResetAtMidnight()
    {
        AutoInspectionComparisonWindow.Window current = AutoInspectionComparisonWindow.resolve(
                AutoInspectionComparisonWindow.SCOPE_CONTINUOUS,
                LocalDateTime.of(2026, 9, 1, 0, 5));

        assertTrue(AutoInspectionComparisonWindow.isSameWindow(
                current, AutoInspectionComparisonWindow.SCOPE_CONTINUOUS,
                LocalDateTime.of(2026, 8, 31, 23, 55)));
    }
}
