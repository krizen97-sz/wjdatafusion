package com.hm.manage.service.impl;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

final class AutoInspectionComparisonWindow
{
    static final String SCOPE_CONTINUOUS = "CONTINUOUS";
    static final String SCOPE_DAY = "DAY";
    static final String SCOPE_HOUR = "HOUR";

    private static final DateTimeFormatter KEY_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter KEY_HOUR = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH");
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private AutoInspectionComparisonWindow()
    {
    }

    static String normalizeScope(Object value)
    {
        String scope = value == null ? "" : value.toString().trim().toUpperCase(Locale.ROOT);
        if (SCOPE_DAY.equals(scope) || SCOPE_HOUR.equals(scope))
        {
            return scope;
        }
        return SCOPE_CONTINUOUS;
    }

    static Window resolve(Object scopeValue, LocalDateTime observedAt)
    {
        LocalDateTime end = observedAt == null ? LocalDateTime.now() : observedAt;
        String scope = normalizeScope(scopeValue);
        if (SCOPE_DAY.equals(scope))
        {
            LocalDateTime start = end.toLocalDate().atStartOfDay();
            return new Window(scope, "DAY:" + KEY_DATE.format(start), start, end);
        }
        if (SCOPE_HOUR.equals(scope))
        {
            LocalDateTime start = end.truncatedTo(ChronoUnit.HOURS);
            return new Window(scope, "HOUR:" + KEY_HOUR.format(start), start, end);
        }
        return new Window(SCOPE_CONTINUOUS, SCOPE_CONTINUOUS, null, end);
    }

    static boolean isSameWindow(Window current, String previousWindowKey,
                                LocalDateTime previousObservedAt)
    {
        if (current == null || SCOPE_CONTINUOUS.equals(current.scope()))
        {
            return true;
        }
        String key = previousWindowKey == null ? "" : previousWindowKey.trim();
        if (key.isEmpty() && previousObservedAt != null)
        {
            key = resolve(current.scope(), previousObservedAt).key();
        }
        return current.key().equals(key);
    }

    static String scopeLabel(String scope)
    {
        if (SCOPE_DAY.equals(normalizeScope(scope)))
        {
            return "按天累计";
        }
        if (SCOPE_HOUR.equals(normalizeScope(scope)))
        {
            return "按小时累计";
        }
        return "连续累计";
    }

    record Window(String scope, String key, LocalDateTime start, LocalDateTime end)
    {
        String display()
        {
            if (start == null)
            {
                return "连续累计，当前采样时间 " + DISPLAY_TIME.format(end);
            }
            return DISPLAY_TIME.format(start) + " 至 " + DISPLAY_TIME.format(end);
        }
    }
}
