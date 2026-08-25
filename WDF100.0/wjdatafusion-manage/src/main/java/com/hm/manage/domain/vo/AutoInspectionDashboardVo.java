package com.hm.manage.domain.vo;

import java.io.Serializable;

public class AutoInspectionDashboardVo implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Object summary;
    private Object frequentSummary;
    private Object healthOverview;
    private Object weekSummary;
    private Object trend;
    private Object combinedTrend;
    private Object calendar;
    private Object toolStats;
    private Object currentPlanHealth;
    private Object latestAbnormalTargets;
    private Object latestIssues;
    private Object recentRecords;
    private String generatedTime;

    public Object getSummary() { return summary; }
    public void setSummary(Object summary) { this.summary = summary; }
    public Object getFrequentSummary() { return frequentSummary; }
    public void setFrequentSummary(Object frequentSummary) { this.frequentSummary = frequentSummary; }
    public Object getHealthOverview() { return healthOverview; }
    public void setHealthOverview(Object healthOverview) { this.healthOverview = healthOverview; }
    public Object getWeekSummary() { return weekSummary; }
    public void setWeekSummary(Object weekSummary) { this.weekSummary = weekSummary; }
    public Object getTrend() { return trend; }
    public void setTrend(Object trend) { this.trend = trend; }
    public Object getCombinedTrend() { return combinedTrend; }
    public void setCombinedTrend(Object combinedTrend) { this.combinedTrend = combinedTrend; }
    public Object getCalendar() { return calendar; }
    public void setCalendar(Object calendar) { this.calendar = calendar; }
    public Object getToolStats() { return toolStats; }
    public void setToolStats(Object toolStats) { this.toolStats = toolStats; }
    public Object getCurrentPlanHealth() { return currentPlanHealth; }
    public void setCurrentPlanHealth(Object currentPlanHealth) { this.currentPlanHealth = currentPlanHealth; }
    public Object getLatestAbnormalTargets() { return latestAbnormalTargets; }
    public void setLatestAbnormalTargets(Object latestAbnormalTargets) { this.latestAbnormalTargets = latestAbnormalTargets; }
    public Object getLatestIssues() { return latestIssues; }
    public void setLatestIssues(Object latestIssues) { this.latestIssues = latestIssues; }
    public Object getRecentRecords() { return recentRecords; }
    public void setRecentRecords(Object recentRecords) { this.recentRecords = recentRecords; }
    public String getGeneratedTime() { return generatedTime; }
    public void setGeneratedTime(String generatedTime) { this.generatedTime = generatedTime; }
}
