package com.hm.manage.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AutoInspectionValueComparisonTest
{
    @Test
    void firstPreviousSampleBuildsHealthyBaseline()
    {
        AutoInspectionValueComparison.Evaluation result = AutoInspectionValueComparison.evaluate(
                AutoInspectionValueComparison.MODE_PREVIOUS, AutoInspectionValueComparison.RULE_MIN,
                BigDecimal.ONE, BigDecimal.valueOf(100), null, true);

        assertEquals(AutoInspectionValueComparison.STATUS_NORMAL, result.status);
        assertTrue(result.baseline);
        assertNull(result.changeValue);
        assertTrue(result.detail.contains("按正常计入健康度"));
    }

    @Test
    void previousComparisonExposesCurrentPreviousAndDelta()
    {
        AutoInspectionValueComparison.Evaluation result = AutoInspectionValueComparison.evaluate(
                AutoInspectionValueComparison.MODE_PREVIOUS, AutoInspectionValueComparison.RULE_MIN,
                BigDecimal.ONE, BigDecimal.valueOf(105), BigDecimal.valueOf(100), true);

        assertEquals(AutoInspectionValueComparison.STATUS_NORMAL, result.status);
        assertFalse(result.baseline);
        assertEquals(BigDecimal.valueOf(5), result.changeValue);
        assertTrue(result.detail.contains("本次值105"));
        assertTrue(result.detail.contains("上次值100"));
        assertTrue(result.detail.contains("变化量+5"));
    }

    @Test
    void unchangedCounterFailsMinimumProgressRule()
    {
        AutoInspectionValueComparison.Evaluation result = AutoInspectionValueComparison.evaluate(
                AutoInspectionValueComparison.MODE_PREVIOUS, AutoInspectionValueComparison.RULE_MIN,
                BigDecimal.ONE, BigDecimal.valueOf(100), BigDecimal.valueOf(100), true);

        assertEquals(AutoInspectionValueComparison.STATUS_ABNORMAL, result.status);
        assertEquals(BigDecimal.ZERO, result.changeValue);
    }

    @Test
    void cumulativeCounterResetBuildsNewHealthyBaseline()
    {
        AutoInspectionValueComparison.Evaluation result = AutoInspectionValueComparison.evaluate(
                AutoInspectionValueComparison.MODE_PREVIOUS, AutoInspectionValueComparison.RULE_MIN,
                BigDecimal.ONE, BigDecimal.TEN, BigDecimal.valueOf(100), true);

        assertEquals(AutoInspectionValueComparison.STATUS_NORMAL, result.status);
        assertTrue(result.baseline);
        assertTrue(result.detail.contains("累计值回退"));
    }

    @Test
    void newBusinessWindowKeepsPriorValueAsEvidenceWithoutCalculatingNegativeDelta()
    {
        AutoInspectionValueComparison.Evaluation result = AutoInspectionValueComparison.establishBaseline(
                AutoInspectionValueComparison.RULE_MIN, BigDecimal.ONE,
                BigDecimal.valueOf(3000), BigDecimal.valueOf(20000000), "统计周期已切换为按天累计");

        assertEquals(AutoInspectionValueComparison.STATUS_NORMAL, result.status);
        assertTrue(result.baseline);
        assertEquals(BigDecimal.valueOf(20000000), result.previousValue);
        assertNull(result.changeValue);
        assertTrue(result.detail.contains("统计周期已切换"));
    }

    @Test
    void fixedComparisonKeepsExistingThresholdMeaning()
    {
        AutoInspectionValueComparison.Evaluation result = AutoInspectionValueComparison.evaluate(
                AutoInspectionValueComparison.MODE_FIXED, AutoInspectionValueComparison.RULE_MAX,
                BigDecimal.valueOf(80), BigDecimal.valueOf(85), null, false);

        assertEquals(AutoInspectionValueComparison.STATUS_ABNORMAL, result.status);
        assertFalse(result.baseline);
        assertEquals("本次值不得高于80", result.rule);
    }
}
