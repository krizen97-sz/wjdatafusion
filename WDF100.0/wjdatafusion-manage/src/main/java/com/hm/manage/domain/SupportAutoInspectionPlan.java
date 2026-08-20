package com.hm.manage.domain;

import com.hm.common.core.domain.BaseEntity;

public class SupportAutoInspectionPlan extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long planId;
    private Long templateId;
    private String templateName;
    private String planName;
    private String labelName;
    private String cronExpression;
    private String cronConfig;
    private Long jobId;
    private String reportStyle;
    private String status;
    private Integer stepCount;

    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }
    public String getTemplateName() { return templateName; }
    public void setTemplateName(String templateName) { this.templateName = templateName; }
    public String getPlanName() { return planName; }
    public void setPlanName(String planName) { this.planName = planName; }
    public String getLabelName() { return labelName; }
    public void setLabelName(String labelName) { this.labelName = labelName; }
    public String getCronExpression() { return cronExpression; }
    public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }
    public String getCronConfig() { return cronConfig; }
    public void setCronConfig(String cronConfig) { this.cronConfig = cronConfig; }
    public Long getJobId() { return jobId; }
    public void setJobId(Long jobId) { this.jobId = jobId; }
    public String getReportStyle() { return reportStyle; }
    public void setReportStyle(String reportStyle) { this.reportStyle = reportStyle; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getStepCount() { return stepCount; }
    public void setStepCount(Integer stepCount) { this.stepCount = stepCount; }
}
