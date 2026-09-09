package com.hm.manage.service.governance;

import com.hm.manage.service.governance.DataGovernanceModels.StoredRun;
import java.util.Map;

/** API summaries omit sample bodies and frozen engine definitions. */
public final class DataGovernanceScheduleModels
{
    private DataGovernanceScheduleModels() { }
    public record PublishRequest(String flowId, String name, String inputJson, Map<String, Object> parameters) { }
    public record ScheduleRequest(String name, String releaseId, String cron, String timeZone, Long revision) { }
    public record StateRequest(Boolean enabled, Long revision) { }
    public record ReleaseSummary(String id, int version, String flowId, String projectId, String name,
        String definitionHash, String createdAt, String inputMode) { }
    public record ReleaseDetail(ReleaseSummary release, String inputJson, Map<String, Object> parameters) { }
    public record ScheduleSummary(String id, long revision, String name, String releaseId, String flowId,
        String cron, String timeZone, boolean enabled, String nextRunAt, String status, String activeRunId,
        String lastRunId, String lastRunStatus, String lastRunAt, String lastFinishedAt, String lastError,
        long skippedCount, String lastSkippedAt, String lastSkippedReason, boolean recoveryRequired,
        String createdAt, String updatedAt, String inputMode) { }
    /** Never returned to a controller. */
    public static class Release
    {
        public String id;
        public long ownerId;
        public int version;
        public String name;
        public String createdAt;
        public StoredRun snapshot;
    }
    /** Single-instance durable state, independent of RuoYi's system-job reflection mechanism. */
    public static class Schedule
    {
        public String id;
        public long ownerId;
        public long revision;
        public String name;
        public String releaseId;
        public String flowId;
        public String cron;
        public String timeZone;
        public boolean enabled;
        public String nextRunAt;
        public String status = "PAUSED";
        public String activeRunId;
        public String lastRunId;
        public String lastRunStatus;
        public String lastRunAt;
        public String lastFinishedAt;
        public String lastError;
        public long skippedCount;
        public String lastSkippedAt;
        public String lastSkippedReason;
        public boolean recoveryRequired;
        public String createdAt;
        public String updatedAt;
    }
}
