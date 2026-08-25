package com.hm.manage.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.hm.manage.domain.vo.AutoInspectionDashboardVo;

class AutoInspectionDashboardAggregationTest
{
    private final SupportAutoInspectionServiceImpl service = new SupportAutoInspectionServiceImpl();

    @Test
    void combinedOverviewUsesRoutineRunsAndFrequentExpectedSamples() throws Exception
    {
        Map<String, Object> routine = mapOf(
                "recordCount", 2L,
                "normalCount", 1L,
                "abnormalTargetCount", 1L,
                "status", "2");
        Map<String, Object> frequent = mapOf(
                "expectedCount", 8L,
                "completedCount", 8L,
                "normalCount", 7L,
                "warningCount", 1L,
                "abnormalCount", 0L,
                "missingCount", 0L,
                "status", "4");

        Map<String, Object> result = invoke("buildCombinedHealthOverview",
                new Class<?>[] {Map.class, Map.class}, routine, frequent);

        assertEquals(new BigDecimal("80.0"), result.get("healthScore"));
        assertEquals("2", result.get("status"));
        assertEquals(2L, result.get("issueCount"));
    }

    @Test
    void combinedTrendKeepsWarningAndMissingSamplesVisible() throws Exception
    {
        List<Map<String, Object>> routine = new ArrayList<>();
        routine.add(mapOf("date", "2026-08-25", "total", 1L, "normal", 1L, "abnormal", 0L));
        List<Map<String, Object>> health = new ArrayList<>();
        health.add(mapOf(
                "healthDate", java.sql.Date.valueOf("2026-08-25"),
                "expectedCount", 12L,
                "completedCount", 11L,
                "normalCount", 10L,
                "warningCount", 1L,
                "abnormalCount", 0L,
                "missingCount", 1L));

        List<Map<String, Object>> result = invoke("buildCombinedHealthTrend",
                new Class<?>[] {List.class, List.class}, routine, health);

        assertEquals(1, result.size());
        assertEquals("4", result.get(0).get("status"));
        assertEquals(1L, result.get(0).get("frequentMissing"));
        assertEquals(new BigDecimal("84.6"), result.get(0).get("healthScore"));
    }

    @Test
    void dashboardVoPublishesUnifiedHealthFields() throws Exception
    {
        assertEquals(Object.class, AutoInspectionDashboardVo.class.getMethod("getFrequentSummary").getReturnType());
        assertEquals(Object.class, AutoInspectionDashboardVo.class.getMethod("getHealthOverview").getReturnType());
        assertEquals(Object.class, AutoInspectionDashboardVo.class.getMethod("getCombinedTrend").getReturnType());
        assertEquals(Object.class, AutoInspectionDashboardVo.class.getMethod("getCurrentPlanHealth").getReturnType());
        assertEquals(Object.class, AutoInspectionDashboardVo.class.getMethod("getLatestIssues").getReturnType());
    }

    @SuppressWarnings("unchecked")
    private <T> T invoke(String methodName, Class<?>[] parameterTypes, Object... args) throws Exception
    {
        Method method = SupportAutoInspectionServiceImpl.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return (T) method.invoke(service, args);
    }

    private Map<String, Object> mapOf(Object... values)
    {
        Map<String, Object> map = new HashMap<>();
        for (int index = 0; index < values.length; index += 2)
        {
            map.put(String.valueOf(values[index]), values[index + 1]);
        }
        return map;
    }
}
