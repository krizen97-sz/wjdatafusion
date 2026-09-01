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
    private String planMode;
    private String cronExpression;
    private String cronConfig;
    private String healthConfig;
    private String scopeType;
    private Long siteId;
    private String siteName;
    private Long mainPlatformId;
    private String mainPlatformName;
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
    public String getPlanMode() { return planMode; }
    public void setPlanMode(String planMode) { this.planMode = planMode; }
    public String getCronExpression() { return cronExpression; }
    public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }
    public String getCronConfig() { return cronConfig; }
    public void setCronConfig(String cronConfig) { this.cronConfig = cronConfig; }
    public String getHealthConfig() { return healthConfig; }
    public void setHealthConfig(String healthConfig) { this.healthConfig = healthConfig; }
    public String getScopeType() { return scopeType; }
    public void setScopeType(String scopeType) { this.scopeType = scopeType; }
    public Long getSiteId() { return siteId; }
    public void setSiteId(Long siteId) { this.siteId = siteId; }
    public String getSiteName() { return siteName; }
    public void setSiteName(String siteName) { this.siteName = siteName; }
    public Long getMainPlatformId() { return mainPlatformId; }
    public void setMainPlatformId(Long mainPlatformId) { this.mainPlatformId = mainPlatformId; }
    public String getMainPlatformName() { return mainPlatformName; }
    public void setMainPlatformName(String mainPlatformName) { this.mainPlatformName = mainPlatformName; }
    public Long getJobId() { return jobId; }
    public void setJobId(Long jobId) { this.jobId = jobId; }
    public String getReportStyle() { return reportStyle; }
    public void setReportStyle(String reportStyle) { this.reportStyle = reportStyle; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getStepCount() { return stepCount; }
    public void setStepCount(Integer stepCount) { this.stepCount = stepCount; }
}
