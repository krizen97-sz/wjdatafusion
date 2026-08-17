package com.hm.manage.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.hm.common.annotation.Excel;
import com.hm.common.core.domain.BaseEntity;

public class SupportAutoInspectionRecord extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long recordId;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "巡检时间", dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date inspectionTime;
    private String sourceType;
    private String resultStatus;
    private String executorName;
    private Long templateId;
    private String templateName;
    private Long planId;
    private String planName;
    private String reportStyle;
    private Integer enabledStepCount;
    private Integer skippedStepCount;
    private Integer targetCount;
    private Integer abnormalCount;
    private String summary;
    private String abnormalSummary;

    public Long getRecordId() { return recordId; }
    public void setRecordId(Long recordId) { this.recordId = recordId; }
    public Date getInspectionTime() { return inspectionTime; }
    public void setInspectionTime(Date inspectionTime) { this.inspectionTime = inspectionTime; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getResultStatus() { return resultStatus; }
    public void setResultStatus(String resultStatus) { this.resultStatus = resultStatus; }
    public String getExecutorName() { return executorName; }
    public void setExecutorName(String executorName) { this.executorName = executorName; }
    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }
    public String getTemplateName() { return templateName; }
    public void setTemplateName(String templateName) { this.templateName = templateName; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public String getPlanName() { return planName; }
    public void setPlanName(String planName) { this.planName = planName; }
    public String getReportStyle() { return reportStyle; }
    public void setReportStyle(String reportStyle) { this.reportStyle = reportStyle; }
    public Integer getEnabledStepCount() { return enabledStepCount; }
    public void setEnabledStepCount(Integer enabledStepCount) { this.enabledStepCount = enabledStepCount; }
    public Integer getSkippedStepCount() { return skippedStepCount; }
    public void setSkippedStepCount(Integer skippedStepCount) { this.skippedStepCount = skippedStepCount; }
    public Integer getTargetCount() { return targetCount; }
    public void setTargetCount(Integer targetCount) { this.targetCount = targetCount; }
    public Integer getAbnormalCount() { return abnormalCount; }
    public void setAbnormalCount(Integer abnormalCount) { this.abnormalCount = abnormalCount; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getAbnormalSummary() { return abnormalSummary; }
    public void setAbnormalSummary(String abnormalSummary) { this.abnormalSummary = abnormalSummary; }
}
