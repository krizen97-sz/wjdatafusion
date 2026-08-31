package com.hm.manage.service.impl;

import java.text.ParseException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.TimeZone;
import org.quartz.CronExpression;
import com.hm.common.utils.StringUtils;

final class AutoInspectionPlanScheduleMatcher
{
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private AutoInspectionPlanScheduleMatcher()
    {
    }

    static TodaySchedule resolve(String cronExpression, LocalDateTime planCreateTime,
                                 LocalDate date, ZoneId zoneId)
    {
        if (date == null || zoneId == null || StringUtils.isBlank(cronExpression))
        {
            return TodaySchedule.notScheduled();
        }
        try
        {
            ZonedDateTime dayStart = date.atStartOfDay(zoneId);
            ZonedDateTime dayEnd = date.plusDays(1).atStartOfDay(zoneId);
            ZonedDateTime eligibleFrom = dayStart;
            if (planCreateTime != null)
            {
                ZonedDateTime createdAt = planCreateTime.atZone(zoneId);
                if (!createdAt.isBefore(dayEnd))
                {
                    return TodaySchedule.notScheduled();
                }
                if (createdAt.isAfter(eligibleFrom))
                {
                    eligibleFrom = createdAt;
                }
            }

            CronExpression cron = new CronExpression(cronExpression.trim());
            cron.setTimeZone(TimeZone.getTimeZone(zoneId));
            Date firstDate = cron.getNextValidTimeAfter(Date.from(eligibleFrom.minusNanos(1).toInstant()));
            ZonedDateTime first = firstDate == null ? null : firstDate.toInstant().atZone(zoneId);
            if (first == null || !first.isBefore(dayEnd) || !first.toLocalDate().equals(date))
            {
                return TodaySchedule.notScheduled();
            }
            Date secondDate = cron.getNextValidTimeAfter(Date.from(first.toInstant()));
            ZonedDateTime second = secondDate == null ? null : secondDate.toInstant().atZone(zoneId);
            boolean multiple = second != null && second.isBefore(dayEnd) && second.toLocalDate().equals(date);
            String display = TIME_FORMATTER.format(first) + (multiple ? "起，多次执行" : "");
            return new TodaySchedule(true, display);
        }
        catch (ParseException ignored)
        {
            return TodaySchedule.notScheduled();
        }
    }

    record TodaySchedule(boolean scheduled, String display)
    {
        private static TodaySchedule notScheduled()
        {
            return new TodaySchedule(false, "");
        }
    }
}
