package com.hm.manage.domain.vo;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonFormat;

public class AutoInspectionMetricsVo
{
    private Long planId;
    private String beginTime;
    private String endTime;
    private String generatedTime;
    private int sampleLimit;
    private int sampleCount;
    private boolean truncated;
    private List<PlanOption> plans = new ArrayList<>();
    private List<MetricSeries> metrics = new ArrayList<>();
    private List<ExecutionPoint> executions = new ArrayList<>();

    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public String getBeginTime() { return beginTime; }
    public void setBeginTime(String beginTime) { this.beginTime = beginTime; }
    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
    public String getGeneratedTime() { return generatedTime; }
    public void setGeneratedTime(String generatedTime) { this.generatedTime = generatedTime; }
    public int getSampleLimit() { return sampleLimit; }
    public void setSampleLimit(int sampleLimit) { this.sampleLimit = sampleLimit; }
    public int getSampleCount() { return sampleCount; }
    public void setSampleCount(int sampleCount) { this.sampleCount = sampleCount; }
    public boolean isTruncated() { return truncated; }
    public void setTruncated(boolean truncated) { this.truncated = truncated; }
    public List<PlanOption> getPlans() { return plans; }
    public void setPlans(List<PlanOption> plans) { this.plans = plans; }
    public List<MetricSeries> getMetrics() { return metrics; }
    public void setMetrics(List<MetricSeries> metrics) { this.metrics = metrics; }
    public List<ExecutionPoint> getExecutions() { return executions; }
    public void setExecutions(List<ExecutionPoint> executions) { this.executions = executions; }

    public static class PlanOption
    {
        private Long planId;
        private String planName;
        private String siteName;
        private String mainPlatformName;
        private String status;
        private long recordCount;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private Date lastSampleTime;

        public Long getPlanId() { return planId; }
        public void setPlanId(Long planId) { this.planId = planId; }
        public String getPlanName() { return planName; }
        public void setPlanName(String planName) { this.planName = planName; }
        public String getSiteName() { return siteName; }
        public void setSiteName(String siteName) { this.siteName = siteName; }
        public String getMainPlatformName() { return mainPlatformName; }
        public void setMainPlatformName(String mainPlatformName) { this.mainPlatformName = mainPlatformName; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public long getRecordCount() { return recordCount; }
        public void setRecordCount(long recordCount) { this.recordCount = recordCount; }
        public Date getLastSampleTime() { return lastSampleTime; }
        public void setLastSampleTime(Date lastSampleTime) { this.lastSampleTime = lastSampleTime; }
    }

    public static class MetricSeries
    {
        private String metricKey;
        private String metricName;
        private String stepName;
        private String targetName;
        private String toolName;
        private String unit;
        private String comparisonScope;
        private int sampleCount;
        private int numericCount;
        private String minimum;
        private String maximum;
        private String average;
        private MetricPoint latest;
        private List<MetricPoint> points = new ArrayList<>();
        private Map<String, Integer> statusCounts = new LinkedHashMap<>();

        public String getMetricKey() { return metricKey; }
        public void setMetricKey(String metricKey) { this.metricKey = metricKey; }
        public String getMetricName() { return metricName; }
        public void setMetricName(String metricName) { this.metricName = metricName; }
        public String getStepName() { return stepName; }
        public void setStepName(String stepName) { this.stepName = stepName; }
        public String getTargetName() { return targetName; }
        public void setTargetName(String targetName) { this.targetName = targetName; }
        public String getToolName() { return toolName; }
        public void setToolName(String toolName) { this.toolName = toolName; }
        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = unit; }
        public String getComparisonScope() { return comparisonScope; }
        public void setComparisonScope(String comparisonScope) { this.comparisonScope = comparisonScope; }
        public int getSampleCount() { return sampleCount; }
        public void setSampleCount(int sampleCount) { this.sampleCount = sampleCount; }
        public int getNumericCount() { return numericCount; }
        public void setNumericCount(int numericCount) { this.numericCount = numericCount; }
        public String getMinimum() { return minimum; }
        public void setMinimum(String minimum) { this.minimum = minimum; }
        public String getMaximum() { return maximum; }
        public void setMaximum(String maximum) { this.maximum = maximum; }
        public String getAverage() { return average; }
        public void setAverage(String average) { this.average = average; }
        public MetricPoint getLatest() { return latest; }
        public void setLatest(MetricPoint latest) { this.latest = latest; }
        public List<MetricPoint> getPoints() { return points; }
        public void setPoints(List<MetricPoint> points) { this.points = points; }
        public Map<String, Integer> getStatusCounts() { return statusCounts; }
        public void setStatusCounts(Map<String, Integer> statusCounts) { this.statusCounts = statusCounts; }
    }

    public static class MetricPoint
    {
        private Long resultId;
        private Long recordId;
        private String sampleTime;
        private String value;
        private String previousValue;
        private String changeValue;
        private String resultStatus;
        private String evaluationMode;
        private String evaluationRule;
        private String baselineFlag;
        private String windowKey;

        public Long getResultId() { return resultId; }
        public void setResultId(Long resultId) { this.resultId = resultId; }
        public Long getRecordId() { return recordId; }
        public void setRecordId(Long recordId) { this.recordId = recordId; }
        public String getSampleTime() { return sampleTime; }
        public void setSampleTime(String sampleTime) { this.sampleTime = sampleTime; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        public String getPreviousValue() { return previousValue; }
        public void setPreviousValue(String previousValue) { this.previousValue = previousValue; }
        public String getChangeValue() { return changeValue; }
        public void setChangeValue(String changeValue) { this.changeValue = changeValue; }
        public String getResultStatus() { return resultStatus; }
        public void setResultStatus(String resultStatus) { this.resultStatus = resultStatus; }
        public String getEvaluationMode() { return evaluationMode; }
        public void setEvaluationMode(String evaluationMode) { this.evaluationMode = evaluationMode; }
        public String getEvaluationRule() { return evaluationRule; }
        public void setEvaluationRule(String evaluationRule) { this.evaluationRule = evaluationRule; }
        public String getBaselineFlag() { return baselineFlag; }
        public void setBaselineFlag(String baselineFlag) { this.baselineFlag = baselineFlag; }
        public String getWindowKey() { return windowKey; }
        public void setWindowKey(String windowKey) { this.windowKey = windowKey; }
    }

    public static class ExecutionPoint
    {
        private Long recordId;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private Date sampleTime;
        private Long durationMs;
        private String resultStatus;

        public Long getRecordId() { return recordId; }
        public void setRecordId(Long recordId) { this.recordId = recordId; }
        public Date getSampleTime() { return sampleTime; }
        public void setSampleTime(Date sampleTime) { this.sampleTime = sampleTime; }
        public Long getDurationMs() { return durationMs; }
        public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
        public String getResultStatus() { return resultStatus; }
        public void setResultStatus(String resultStatus) { this.resultStatus = resultStatus; }
    }
}
