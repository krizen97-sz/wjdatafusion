package com.hm.manage.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AutoInspectionComparisonScopeTest
{
    private final SupportAutoInspectionServiceImpl service = new SupportAutoInspectionServiceImpl();

    @Test
    void datedHttpCountDefaultsToDailyWindow() throws Exception
    {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("toolCode", "HTTP_COUNT");
        step.put("stepParams", Map.of("evaluationConfig", Map.of("mode", "PREVIOUS")));
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("extraParams", "{\"startTime\":\"${todayStart}\",\"endTime\":\"${endTime}\"}");

        assertEquals(AutoInspectionComparisonWindow.SCOPE_DAY, resolve(step, target));
    }

    @Test
    void explicitScopeOverridesDatePlaceholderInference() throws Exception
    {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("toolCode", "HTTP_COUNT");
        step.put("stepParams", Map.of("evaluationConfig",
                Map.of("mode", "PREVIOUS", "scope", "CONTINUOUS")));
        Map<String, Object> target = Map.of("url", "https://host/api?date=${today}");

        assertEquals(AutoInspectionComparisonWindow.SCOPE_CONTINUOUS, resolve(step, target));
    }

    private String resolve(Map<String, Object> step, Map<String, Object> target) throws Exception
    {
        Method method = SupportAutoInspectionServiceImpl.class.getDeclaredMethod(
                "resolveComparisonScope", Map.class, Map.class);
        method.setAccessible(true);
        return (String) method.invoke(service, step, target);
    }
}
