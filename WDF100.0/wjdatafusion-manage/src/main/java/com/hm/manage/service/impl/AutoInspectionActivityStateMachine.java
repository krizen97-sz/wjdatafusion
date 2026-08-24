package com.hm.manage.service.impl;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import com.alibaba.fastjson2.JSON;

final class AutoInspectionActivityStateMachine
{
    static final String STATUS_NORMAL = "1";
    static final String STATUS_ABNORMAL = "2";
    static final String STATUS_BASELINE = "3";
    static final String STATUS_WARNING = "4";

    enum Mode
    {
        TOPIC_ACTIVITY,
        CONSUMER_PROGRESS,
        MQTT_ACTIVITY
    }

    static Evaluation evaluate(Mode mode, Rule rule, State previous, Observation current)
    {
        LocalDateTime now = current.observedAt;
        if (previous == null || decreased(current.primaryValue, previous.primaryValue)
                || decreased(current.secondaryValue, previous.secondaryValue))
        {
            LocalDateTime baselineActivity = current.externalLastActivityAt == null
                    ? now : current.externalLastActivityAt;
            return new Evaluation(STATUS_BASELINE, current.primaryValue, current.secondaryValue, now,
                    baselineActivity, 0, 0, 0,
                    previous == null ? "已建立首次采样基线" : "检测到计数器重置，已重新建立基线");
        }

        if (mode == Mode.CONSUMER_PROGRESS && !increased(current.secondaryValue, previous.secondaryValue))
        {
            return new Evaluation(STATUS_BASELINE, current.primaryValue, current.secondaryValue, now,
                    now, 0, 0, 0, "上游Topic本窗口没有新增消息，本次不判定消费停滞");
        }

        boolean active = increased(current.primaryValue, previous.primaryValue);
        if (mode == Mode.MQTT_ACTIVITY && current.externalLastActivityAt != null)
        {
            active = active || previous.lastActivityAt == null
                    || current.externalLastActivityAt.isAfter(previous.lastActivityAt);
        }

        if (active)
        {
            LocalDateTime activityTime = current.externalLastActivityAt == null
                    ? now : current.externalLastActivityAt;
            int normalStreak = previous.normalStreak + 1;
            boolean recovering = (STATUS_ABNORMAL.equals(previous.status) || STATUS_WARNING.equals(previous.status))
                    && normalStreak < rule.recoverySuccesses;
            return new Evaluation(recovering ? STATUS_WARNING : STATUS_NORMAL,
                    current.primaryValue, current.secondaryValue, now, activityTime,
                    0, normalStreak, 0,
                    recovering ? "已恢复数据活动，等待连续确认 " + normalStreak + "/" + rule.recoverySuccesses
                            : "检测到新的数据活动");
        }

        LocalDateTime lastActivity = previous.lastActivityAt == null
                ? (previous.observedAt == null ? now : previous.observedAt) : previous.lastActivityAt;
        long quietMinutes = Math.max(0, Duration.between(lastActivity, now).toMinutes());
        String status = quietMinutes >= rule.abnormalMinutes ? STATUS_ABNORMAL
                : quietMinutes >= rule.warningMinutes ? STATUS_WARNING : STATUS_NORMAL;
        int abnormalStreak = STATUS_ABNORMAL.equals(status) ? previous.abnormalStreak + 1 : 0;
        String detail = STATUS_ABNORMAL.equals(status)
                ? "持续" + quietMinutes + "分钟没有新的数据活动，已达到异常条件"
                : STATUS_WARNING.equals(status)
                        ? "持续" + quietMinutes + "分钟没有新的数据活动，进入关注状态"
                        : "当前静默" + quietMinutes + "分钟，尚未达到关注条件";
        return new Evaluation(status, current.primaryValue, current.secondaryValue, now, lastActivity,
                abnormalStreak, 0, quietMinutes, detail);
    }

    static Rule ruleFromStepParams(Object stepParams)
    {
        Map<String, Object> root = parseMap(stepParams);
        Map<String, Object> value = root.get("activityRule") instanceof Map<?, ?>
                ? castMap(root.get("activityRule")) : root;
        int warning = bounded(value.get("warningMinutes"), 1, 1440, 3);
        int abnormal = bounded(value.get("abnormalMinutes"), warning, 10080, 5);
        int recovery = bounded(value.get("recoverySuccesses"), 1, 20, 2);
        return new Rule(warning, abnormal, recovery);
    }

    private static boolean increased(BigDecimal current, BigDecimal previous)
    {
        return current != null && previous != null && current.compareTo(previous) > 0;
    }

    private static boolean decreased(BigDecimal current, BigDecimal previous)
    {
        return current != null && previous != null && current.compareTo(previous) < 0;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseMap(Object value)
    {
        if (value instanceof Map<?, ?> map)
        {
            return (Map<String, Object>) map;
        }
        try
        {
            return JSON.parseObject(value == null ? "{}" : value.toString(), Map.class);
        }
        catch (Exception ignored)
        {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value)
    {
        return (Map<String, Object>) value;
    }

    private static int bounded(Object value, int min, int max, int fallback)
    {
        try
        {
            return Math.max(min, Math.min(max, Integer.parseInt(String.valueOf(value))));
        }
        catch (Exception ignored)
        {
            return fallback;
        }
    }

    static final class Rule
    {
        final int warningMinutes;
        final int abnormalMinutes;
        final int recoverySuccesses;

        Rule(int warningMinutes, int abnormalMinutes, int recoverySuccesses)
        {
            this.warningMinutes = warningMinutes;
            this.abnormalMinutes = abnormalMinutes;
            this.recoverySuccesses = recoverySuccesses;
        }
    }

    static final class State
    {
        final BigDecimal primaryValue;
        final BigDecimal secondaryValue;
        final LocalDateTime observedAt;
        final LocalDateTime lastActivityAt;
        final int abnormalStreak;
        final int normalStreak;
        final String status;

        State(BigDecimal primaryValue, BigDecimal secondaryValue, LocalDateTime observedAt,
              LocalDateTime lastActivityAt, int abnormalStreak, int normalStreak, String status)
        {
            this.primaryValue = primaryValue;
            this.secondaryValue = secondaryValue;
            this.observedAt = observedAt;
            this.lastActivityAt = lastActivityAt;
            this.abnormalStreak = abnormalStreak;
            this.normalStreak = normalStreak;
            this.status = status;
        }
    }

    static final class Observation
    {
        final BigDecimal primaryValue;
        final BigDecimal secondaryValue;
        final LocalDateTime observedAt;
        final LocalDateTime externalLastActivityAt;

        Observation(BigDecimal primaryValue, BigDecimal secondaryValue, LocalDateTime observedAt,
                    LocalDateTime externalLastActivityAt)
        {
            this.primaryValue = primaryValue;
            this.secondaryValue = secondaryValue;
            this.observedAt = observedAt;
            this.externalLastActivityAt = externalLastActivityAt;
        }
    }

    static final class Evaluation
    {
        final String status;
        final BigDecimal primaryValue;
        final BigDecimal secondaryValue;
        final LocalDateTime observedAt;
        final LocalDateTime lastActivityAt;
        final int abnormalStreak;
        final int normalStreak;
        final long quietMinutes;
        final String detail;

        Evaluation(String status, BigDecimal primaryValue, BigDecimal secondaryValue,
                   LocalDateTime observedAt, LocalDateTime lastActivityAt, int abnormalStreak,
                   int normalStreak, long quietMinutes, String detail)
        {
            this.status = status;
            this.primaryValue = primaryValue;
            this.secondaryValue = secondaryValue;
            this.observedAt = observedAt;
            this.lastActivityAt = lastActivityAt;
            this.abnormalStreak = abnormalStreak;
            this.normalStreak = normalStreak;
            this.quietMinutes = quietMinutes;
            this.detail = detail;
        }
    }
}
