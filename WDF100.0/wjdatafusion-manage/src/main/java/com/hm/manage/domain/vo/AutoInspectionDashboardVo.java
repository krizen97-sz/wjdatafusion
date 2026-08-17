package com.hm.manage.domain.vo;

import java.io.Serializable;

public class AutoInspectionDashboardVo implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Object summary;
    private Object weekSummary;
    private Object trend;
    private Object calendar;
    private Object toolStats;
    private Object latestAbnormalTargets;
    private Object recentRecords;
    private String generatedTime;

    public Object getSummary() { return summary; }
    public void setSummary(Object summary) { this.summary = summary; }
    public Object getWeekSummary() { return weekSummary; }
    public void setWeekSummary(Object weekSummary) { this.weekSummary = weekSummary; }
    public Object getTrend() { return trend; }
    public void setTrend(Object trend) { this.trend = trend; }
    public Object getCalendar() { return calendar; }
    public void setCalendar(Object calendar) { this.calendar = calendar; }
    public Object getToolStats() { return toolStats; }
    public void setToolStats(Object toolStats) { this.toolStats = toolStats; }
    public Object getLatestAbnormalTargets() { return latestAbnormalTargets; }
    public void setLatestAbnormalTargets(Object latestAbnormalTargets) { this.latestAbnormalTargets = latestAbnormalTargets; }
    public Object getRecentRecords() { return recentRecords; }
    public void setRecentRecords(Object recentRecords) { this.recentRecords = recentRecords; }
    public String getGeneratedTime() { return generatedTime; }
    public void setGeneratedTime(String generatedTime) { this.generatedTime = generatedTime; }
}
