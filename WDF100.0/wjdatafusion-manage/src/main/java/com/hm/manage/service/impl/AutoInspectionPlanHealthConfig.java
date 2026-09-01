package com.hm.manage.service.impl;

import java.math.BigDecimal;
import java.text.ParseException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;
import org.quartz.CronExpression;
import com.alibaba.fastjson2.JSON;
import com.hm.common.utils.StringUtils;

final class AutoInspectionPlanHealthConfig
{
    static final String MODE_ROUTINE = "ROUTINE";
    static final String MODE_FREQUENT = "FREQUENT";

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int DEFAULT_INTERVAL_MINUTES = 5;
    private static final int DEFAULT_RETENTION_DAYS = 7;
    private static final int DEFAULT_ABNORMAL_RETENTION_DAYS = 90;

    private final int intervalMinutes;
    private final LocalTime activeStartTime;
    private final LocalTime activeEndTime;
    private final int dataDelayMinutes;
    private final BigDecimal healthTarget;
    private final int retentionDays;
    private final int abnormalRetentionDays;
    private final LocalDateTime monitorStartTime;

    private AutoInspectionPlanHealthConfig(int intervalMinutes, LocalTime activeStartTime,
                                           LocalTime activeEndTime, int dataDelayMinutes,
                                           BigDecimal healthTarget, int retentionDays,
                                           int abnormalRetentionDays, LocalDateTime monitorStartTime)
    {
        this.intervalMinutes = intervalMinutes;
        this.activeStartTime = activeStartTime;
        this.activeEndTime = activeEndTime;
        this.dataDelayMinutes = dataDelayMinutes;
        this.healthTarget = healthTarget;
        this.retentionDays = retentionDays;
        this.abnormalRetentionDays = abnormalRetentionDays;
        this.monitorStartTime = monitorStartTime;
    }

    static String normalizeMode(Object value)
    {
        return MODE_FREQUENT.equalsIgnoreCase(String.valueOf(value)) ? MODE_FREQUENT : MODE_ROUTINE;
    }

    static AutoInspectionPlanHealthConfig from(Object cronConfigValue, Object healthConfigValue)
    {
        Map<String, Object> cronConfig = parseMap(cronConfigValue);
        Map<String, Object> healthConfig = parseMap(healthConfigValue);
        int interval = positiveInt(cronConfig.get("interval"), DEFAULT_INTERVAL_MINUTES);
        String unit = text(cronConfig.get("intervalUnit"));
        int intervalMinutes = "hour".equalsIgnoreCase(unit) ? interval * 60 : interval;
        LocalTime start = parseTime(healthConfig.get("activeStartTime"), LocalTime.MIN);
        LocalTime end = parseTime(healthConfig.get("activeEndTime"), LocalTime.of(23, 59));
        int delay = boundedInt(healthConfig.get("dataDelayMinutes"), 0, 120, 0);
        BigDecimal target = decimal(healthConfig.get("healthTarget"), new BigDecimal("99"));
        int retention = boundedInt(healthConfig.get("retentionDays"), 1, 365, DEFAULT_RETENTION_DAYS);
        int abnormalRetention = boundedInt(healthConfig.get("abnormalRetentionDays"), retention, 730,
                DEFAULT_ABNORMAL_RETENTION_DAYS);
        LocalDateTime monitorStart = parseDateTime(healthConfig.get("monitorStartTime"));
        return new AutoInspectionPlanHealthConfig(Math.max(1, intervalMinutes), start, end, delay,
                target.max(BigDecimal.ZERO).min(new BigDecimal("100")), retention, abnormalRetention,
                monitorStart);
    }

    Map<String, Object> toMap()
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("activeStartTime", TIME_FORMATTER.format(activeStartTime));
        result.put("activeEndTime", TIME_FORMATTER.format(activeEndTime));
        result.put("dataDelayMinutes", dataDelayMinutes);
        result.put("healthTarget", healthTarget.stripTrailingZeros());
        result.put("retentionDays", retentionDays);
        result.put("abnormalRetentionDays", abnormalRetentionDays);
        if (monitorStartTime != null)
        {
            result.put("monitorStartTime", DATE_TIME_FORMATTER.format(monitorStartTime));
        }
        return result;
    }

    boolean isActive(LocalDateTime time)
    {
        LocalTime current = time.toLocalTime().withSecond(0).withNano(0);
        if (activeStartTime.equals(activeEndTime))
        {
            return true;
        }
        if (activeStartTime.isBefore(activeEndTime))
        {
            return !current.isBefore(activeStartTime) && !current.isAfter(activeEndTime);
        }
        return !current.isBefore(activeStartTime) || !current.isAfter(activeEndTime);
    }

    LocalDateTime alignSlot(LocalDateTime time)
    {
        LocalDateTime minute = time.withSecond(0).withNano(0);
        long epochMinutes = Duration.between(LocalDateTime.of(1970, 1, 1, 0, 0), minute).toMinutes();
        long aligned = epochMinutes - Math.floorMod(epochMinutes, intervalMinutes);
        return LocalDateTime.of(1970, 1, 1, 0, 0).plusMinutes(aligned);
    }

    LocalDateTime resolveWindowEnd(LocalDateTime executionTime)
    {
        return executionTime.minusMinutes(dataDelayMinutes);
    }

    int expectedSlots(LocalDate date, LocalDateTime now)
    {
        return expectedSlots(date, now, null);
    }

    int expectedSlots(LocalDate date, LocalDateTime now, LocalDateTime notBefore)
    {
        if (date == null || date.isAfter(now.toLocalDate()))
        {
            return 0;
        }
        LocalDateTime end = date.equals(now.toLocalDate()) ? now : date.plusDays(1).atStartOfDay().minusNanos(1);
        LocalDateTime slot = alignSlot(date.atStartOfDay());
        if (slot.isBefore(date.atStartOfDay()))
        {
            slot = slot.plusMinutes(intervalMinutes);
        }
        int count = 0;
        while (slot.toLocalDate().equals(date) && !slot.isAfter(end))
        {
            if (isActive(slot) && (notBefore == null || !slot.isBefore(notBefore)))
            {
                count++;
            }
            slot = slot.plusMinutes(intervalMinutes);
        }
        return count;
    }

    int expectedSlots(LocalDate date, LocalDateTime now, LocalDateTime notBefore,
                      String cronExpression, ZoneId zoneId)
    {
        if (StringUtils.isBlank(cronExpression) || zoneId == null)
        {
            return expectedSlots(date, now, notBefore);
        }
        if (date == null || date.isAfter(now.toLocalDate()))
        {
            return 0;
        }
        try
        {
            CronExpression cron = new CronExpression(cronExpression.trim());
            cron.setTimeZone(TimeZone.getTimeZone(zoneId));
            LocalDateTime dayStart = date.atStartOfDay();
            LocalDateTime searchStart = notBefore != null && notBefore.isAfter(dayStart)
                    ? notBefore : dayStart;
            LocalDateTime limit = date.equals(now.toLocalDate())
                    ? now : date.plusDays(1).atStartOfDay().minusNanos(1);
            ZonedDateTime cursor = searchStart.atZone(zoneId);
            Date nextDate = cron.getNextValidTimeAfter(Date.from(cursor.minusNanos(1).toInstant()));
            int count = 0;
            while (nextDate != null)
            {
                LocalDateTime slot = nextDate.toInstant().atZone(zoneId).toLocalDateTime();
                if (slot.isAfter(limit) || !slot.toLocalDate().equals(date))
                {
                    break;
                }
                if (isActive(slot) && (notBefore == null || !slot.isBefore(notBefore)))
                {
                    count++;
                }
                nextDate = cron.getNextValidTimeAfter(nextDate);
            }
            return count;
        }
        catch (ParseException ignored)
        {
            return expectedSlots(date, now, notBefore);
        }
    }

    int getIntervalMinutes() { return intervalMinutes; }
    int getDataDelayMinutes() { return dataDelayMinutes; }
    BigDecimal getHealthTarget() { return healthTarget; }
    int getRetentionDays() { return retentionDays; }
    int getAbnormalRetentionDays() { return abnormalRetentionDays; }
    LocalDateTime getMonitorStartTime() { return monitorStartTime; }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseMap(Object value)
    {
        if (value instanceof Map<?, ?> map)
        {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        if (value == null || StringUtils.isBlank(value.toString()))
        {
            return new LinkedHashMap<>();
        }
        try
        {
            return JSON.parseObject(value.toString(), Map.class);
        }
        catch (Exception ignored)
        {
            return new LinkedHashMap<>();
        }
    }

    private static LocalTime parseTime(Object value, LocalTime fallback)
    {
        try
        {
            return LocalTime.parse(text(value), TIME_FORMATTER);
        }
        catch (Exception ignored)
        {
            return fallback;
        }
    }

    private static LocalDateTime parseDateTime(Object value)
    {
        try
        {
            return LocalDateTime.parse(text(value), DATE_TIME_FORMATTER);
        }
        catch (Exception ignored)
        {
            return null;
        }
    }

    private static int positiveInt(Object value, int fallback)
    {
        try
        {
            int parsed = Integer.parseInt(text(value));
            return parsed > 0 ? parsed : fallback;
        }
        catch (Exception ignored)
        {
            return fallback;
        }
    }

    private static int boundedInt(Object value, int min, int max, int fallback)
    {
        try
        {
            return Math.max(min, Math.min(max, Integer.parseInt(text(value))));
        }
        catch (Exception ignored)
        {
            return fallback;
        }
    }

    private static BigDecimal decimal(Object value, BigDecimal fallback)
    {
        try
        {
            return new BigDecimal(text(value));
        }
        catch (Exception ignored)
        {
            return fallback;
        }
    }

    private static String text(Object value)
    {
        return value == null ? "" : value.toString().trim();
    }
}
