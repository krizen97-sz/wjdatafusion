package com.hm.manage.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AutoInspectionExecutionPolicyTest
{
    @Test
    void legacyStepKeepsCompatibleDefaults()
    {
        AutoInspectionExecutionPolicy policy = AutoInspectionExecutionPolicy.fromStep(new HashMap<>());
        assertEquals(0, policy.getRetryCount());
        assertEquals(3, policy.getRetryIntervalSeconds());
        assertFalse(policy.shouldStopAfterFailure());
    }

    @Test
    void policyReadsJsonAndClampsUnsafeValues()
    {
        Map<String, Object> step = new HashMap<>();
        step.put("stepParams", "{\"executionPolicy\":{\"retryCount\":9,\"retryIntervalSeconds\":120,\"failureAction\":\"STOP\"}}");
        AutoInspectionExecutionPolicy policy = AutoInspectionExecutionPolicy.fromStep(step);
        assertEquals(3, policy.getRetryCount());
        assertEquals(60, policy.getRetryIntervalSeconds());
        assertTrue(policy.shouldStopAfterFailure());
    }

    @Test
    void invalidPolicyFallsBackWithoutBreakingHistoricalTemplates()
    {
        Map<String, Object> step = new HashMap<>();
        step.put("stepParams", "not-json");
        AutoInspectionExecutionPolicy policy = AutoInspectionExecutionPolicy.fromStep(step);
        assertEquals(AutoInspectionExecutionPolicy.ACTION_CONTINUE, policy.getFailureAction());
        assertEquals("异常不复检；异常后继续后续步骤", policy.describe());
    }
}
