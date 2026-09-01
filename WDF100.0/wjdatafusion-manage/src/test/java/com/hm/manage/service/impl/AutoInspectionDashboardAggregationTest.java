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
    void combinedOverviewUsesUnifiedDailyPlanHealthWithoutDoubleCountingRuns() throws Exception
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

        assertEquals(new BigDecimal("87.5"), result.get("healthScore"));
        assertEquals("4", result.get("status"));
        assertEquals(1L, result.get("issueCount"));
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
        assertEquals(new BigDecimal("83.3"), result.get(0).get("healthScore"));
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

    @Test
    void planHealthPublishesTodaySchedule() throws Exception
    {
        List<Map<String, Object>> plans = List.of(mapOf(
                "planId", 9L,
                "planName", "每日巡检",
                "planMode", "ROUTINE",
                "scopeType", "MAIN_PLATFORM",
                "siteId", 2L,
                "siteName", "武进分局",
                "mainPlatformId", 19L,
                "mainPlatformName", "TIM平台",
                "todaySchedule", "08:00"));
        List<Map<String, Object>> dailyHealth = List.of(mapOf(
                "healthDate", java.sql.Date.valueOf("2026-08-31"),
                "planId", 9L,
                "dayStatus", "1",
                "healthScore", new BigDecimal("100"),
                "expectedCount", 1,
                "completedCount", 1));

        List<Map<String, Object>> result = invoke("buildCurrentPlanHealth",
                new Class<?>[] {java.time.LocalDate.class, List.class, List.class, List.class},
                java.time.LocalDate.of(2026, 8, 31), plans, List.of(), dailyHealth);

        assertEquals(1, result.size());
        assertEquals("08:00", result.get(0).get("todaySchedule"));
        assertEquals("MAIN_PLATFORM", result.get(0).get("scopeType"));
        assertEquals("TIM平台", result.get(0).get("mainPlatformName"));
        assertEquals("1", result.get(0).get("resultStatus"));
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
