package com.hm.manage.service.impl;

import java.util.Collections;
import java.util.Map;
import com.alibaba.fastjson2.JSON;
import com.hm.common.utils.StringUtils;

final class AutoInspectionExecutionPolicy
{
    static final String ACTION_CONTINUE = "CONTINUE";
    static final String ACTION_STOP = "STOP";

    private final int retryCount;
    private final int retryIntervalSeconds;
    private final String failureAction;

    private AutoInspectionExecutionPolicy(int retryCount, int retryIntervalSeconds, String failureAction)
    {
        this.retryCount = Math.max(0, Math.min(retryCount, 3));
        this.retryIntervalSeconds = Math.max(1, Math.min(retryIntervalSeconds, 60));
        this.failureAction = ACTION_STOP.equalsIgnoreCase(failureAction) ? ACTION_STOP : ACTION_CONTINUE;
    }

    static AutoInspectionExecutionPolicy fromStep(Map<String, Object> step)
    {
        Map<String, Object> params = parseMap(step == null ? null : step.get("stepParams"));
        Map<String, Object> policy = parseMap(params.get("executionPolicy"));
        int retryCount = toInt(policy.get("retryCount"), 0);
        int retryIntervalSeconds = toInt(policy.get("retryIntervalSeconds"), 3);
        String failureAction = StringUtils.defaultIfBlank(stringValue(policy.get("failureAction")), ACTION_CONTINUE);
        return new AutoInspectionExecutionPolicy(retryCount, retryIntervalSeconds, failureAction);
    }

    int getRetryCount() { return retryCount; }
    int getRetryIntervalSeconds() { return retryIntervalSeconds; }
    boolean shouldStopAfterFailure() { return ACTION_STOP.equals(failureAction); }
    String getFailureAction() { return failureAction; }

    String describe()
    {
        String retry = retryCount == 0 ? "异常不复检" : "异常复检" + retryCount + "次，间隔" + retryIntervalSeconds + "秒";
        return retry + "；异常后" + (shouldStopAfterFailure() ? "停止后续步骤" : "继续后续步骤");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseMap(Object value)
    {
        if (value instanceof Map<?, ?>)
        {
            return (Map<String, Object>) value;
        }
        if (value == null || StringUtils.isBlank(value.toString()))
        {
            return Collections.emptyMap();
        }
        try
        {
            Map<String, Object> parsed = JSON.parseObject(value.toString(), Map.class);
            return parsed == null ? Collections.emptyMap() : parsed;
        }
        catch (Exception ignored)
        {
            return Collections.emptyMap();
        }
    }

    private static int toInt(Object value, int defaultValue)
    {
        if (value instanceof Number number)
        {
            return number.intValue();
        }
        try
        {
            return value == null ? defaultValue : Integer.parseInt(value.toString());
        }
        catch (NumberFormatException ignored)
        {
            return defaultValue;
        }
    }

    private static String stringValue(Object value)
    {
        return value == null ? "" : value.toString().trim();
    }
}
