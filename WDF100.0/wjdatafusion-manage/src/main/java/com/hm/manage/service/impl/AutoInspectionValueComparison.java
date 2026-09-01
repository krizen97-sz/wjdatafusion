package com.hm.manage.service.impl;

import java.math.BigDecimal;
import com.hm.common.utils.StringUtils;

/**
 * Separates metric acquisition from result evaluation for every numeric inspection tool.
 */
final class AutoInspectionValueComparison
{
    static final String MODE_FIXED = "FIXED";
    static final String MODE_PREVIOUS = "PREVIOUS";
    static final String RULE_MIN = "MIN";
    static final String RULE_MAX = "MAX";
    static final String STATUS_NORMAL = "1";
    static final String STATUS_ABNORMAL = "2";

    private AutoInspectionValueComparison()
    {
    }

    static Evaluation evaluate(String mode, String compareRule, BigDecimal threshold,
                               BigDecimal currentValue, BigDecimal previousValue,
                               boolean resetOnDecrease)
    {
        String normalizedMode = MODE_PREVIOUS.equalsIgnoreCase(mode) ? MODE_PREVIOUS : MODE_FIXED;
        String normalizedRule = RULE_MIN.equalsIgnoreCase(compareRule) ? RULE_MIN : RULE_MAX;
        BigDecimal safeThreshold = threshold == null ? BigDecimal.ZERO : threshold;

        if (currentValue == null)
        {
            return new Evaluation(normalizedMode, STATUS_ABNORMAL, false, null, previousValue, null,
                    buildRule(normalizedMode, normalizedRule, safeThreshold), "本次没有取得可比较的数值");
        }

        if (MODE_PREVIOUS.equals(normalizedMode)
                && (previousValue == null || (resetOnDecrease && currentValue.compareTo(previousValue) < 0)))
        {
            String reason = previousValue == null ? "首次采样" : "检测到累计值回退";
            return new Evaluation(normalizedMode, STATUS_NORMAL, true, currentValue, previousValue, null,
                    buildRule(normalizedMode, normalizedRule, safeThreshold),
                    reason + "，已建立新的对照基线，本次按正常计入健康度");
        }

        BigDecimal comparedValue = MODE_PREVIOUS.equals(normalizedMode)
                ? currentValue.subtract(previousValue) : currentValue;
        boolean abnormal = RULE_MIN.equals(normalizedRule)
                ? comparedValue.compareTo(safeThreshold) < 0
                : comparedValue.compareTo(safeThreshold) > 0;
        String detail = MODE_PREVIOUS.equals(normalizedMode)
                ? "本次值" + decimal(currentValue) + "，上次值" + decimal(previousValue)
                        + "，变化量" + signed(comparedValue) + "；" + buildRule(normalizedMode, normalizedRule, safeThreshold)
                : "本次值" + decimal(currentValue) + "；" + buildRule(normalizedMode, normalizedRule, safeThreshold);
        return new Evaluation(normalizedMode, abnormal ? STATUS_ABNORMAL : STATUS_NORMAL, false,
                currentValue, previousValue, MODE_PREVIOUS.equals(normalizedMode) ? comparedValue : null,
                buildRule(normalizedMode, normalizedRule, safeThreshold), detail);
    }

    static Evaluation establishBaseline(String compareRule, BigDecimal threshold,
                                        BigDecimal currentValue, BigDecimal previousValue,
                                        String reason)
    {
        if (currentValue == null)
        {
            return evaluate(MODE_PREVIOUS, compareRule, threshold, null, previousValue, false);
        }
        String normalizedRule = RULE_MIN.equalsIgnoreCase(compareRule) ? RULE_MIN : RULE_MAX;
        BigDecimal safeThreshold = threshold == null ? BigDecimal.ZERO : threshold;
        String detail = StringUtils.defaultIfBlank(reason, "首次采样")
                + "，已建立新的对照基线，本次按正常计入健康度";
        return new Evaluation(MODE_PREVIOUS, STATUS_NORMAL, true, currentValue, previousValue, null,
                buildRule(MODE_PREVIOUS, normalizedRule, safeThreshold), detail);
    }

    static String normalizeMode(Object value, boolean legacyPreviousMode)
    {
        String mode = StringUtils.trimToEmpty(value == null ? "" : value.toString()).toUpperCase();
        if (MODE_PREVIOUS.equals(mode))
        {
            return MODE_PREVIOUS;
        }
        if (MODE_FIXED.equals(mode))
        {
            return MODE_FIXED;
        }
        return legacyPreviousMode ? MODE_PREVIOUS : MODE_FIXED;
    }

    static String buildRule(String mode, String compareRule, BigDecimal threshold)
    {
        String subject = MODE_PREVIOUS.equals(mode) ? "本次值 - 上次值" : "本次值";
        String relation = RULE_MIN.equals(compareRule) ? "不得低于" : "不得高于";
        return subject + relation + decimal(threshold == null ? BigDecimal.ZERO : threshold);
    }

    private static String signed(BigDecimal value)
    {
        if (value == null)
        {
            return "-";
        }
        return value.signum() > 0 ? "+" + decimal(value) : decimal(value);
    }

    private static String decimal(BigDecimal value)
    {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }

    static final class Evaluation
    {
        final String mode;
        final String status;
        final boolean baseline;
        final BigDecimal currentValue;
        final BigDecimal previousValue;
        final BigDecimal changeValue;
        final String rule;
        final String detail;

        Evaluation(String mode, String status, boolean baseline, BigDecimal currentValue,
                   BigDecimal previousValue, BigDecimal changeValue, String rule, String detail)
        {
            this.mode = mode;
            this.status = status;
            this.baseline = baseline;
            this.currentValue = currentValue;
            this.previousValue = previousValue;
            this.changeValue = changeValue;
            this.rule = rule;
            this.detail = detail;
        }
    }
}
