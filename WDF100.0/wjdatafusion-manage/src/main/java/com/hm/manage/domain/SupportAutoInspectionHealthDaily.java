package com.hm.manage.domain;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.hm.common.core.domain.BaseEntity;

public class SupportAutoInspectionHealthDaily extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long summaryId;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date healthDate;
    private Long planId;
    private String planName;
    private Long templateId;
    private String templateName;
    private String scopeType;
    private Long siteId;
    private String siteName;
    private Long mainPlatformId;
    private String mainPlatformName;
    private Integer expectedCount;
    private Integer completedCount;
    private Integer normalCount;
    private Integer warningCount;
    private Integer abnormalCount;
    private Integer skippedCount;
    private Integer missingCount;
    private BigDecimal healthScore;
    private BigDecimal healthTarget;
    private String dayStatus;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date firstAbnormalTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastAbnormalTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastRunTime;
    private String lastResultStatus;
    private String abnormalSummary;

    public Long getSummaryId() { return summaryId; }
    public void setSummaryId(Long summaryId) { this.summaryId = summaryId; }
    public Date getHealthDate() { return healthDate; }
    public void setHealthDate(Date healthDate) { this.healthDate = healthDate; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public String getPlanName() { return planName; }
    public void setPlanName(String planName) { this.planName = planName; }
    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }
    public String getTemplateName() { return templateName; }
    public void setTemplateName(String templateName) { this.templateName = templateName; }
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
    public Integer getExpectedCount() { return expectedCount; }
    public void setExpectedCount(Integer expectedCount) { this.expectedCount = expectedCount; }
    public Integer getCompletedCount() { return completedCount; }
    public void setCompletedCount(Integer completedCount) { this.completedCount = completedCount; }
    public Integer getNormalCount() { return normalCount; }
    public void setNormalCount(Integer normalCount) { this.normalCount = normalCount; }
    public Integer getWarningCount() { return warningCount; }
    public void setWarningCount(Integer warningCount) { this.warningCount = warningCount; }
    public Integer getAbnormalCount() { return abnormalCount; }
    public void setAbnormalCount(Integer abnormalCount) { this.abnormalCount = abnormalCount; }
    public Integer getSkippedCount() { return skippedCount; }
    public void setSkippedCount(Integer skippedCount) { this.skippedCount = skippedCount; }
    public Integer getMissingCount() { return missingCount; }
    public void setMissingCount(Integer missingCount) { this.missingCount = missingCount; }
    public BigDecimal getHealthScore() { return healthScore; }
    public void setHealthScore(BigDecimal healthScore) { this.healthScore = healthScore; }
    public BigDecimal getHealthTarget() { return healthTarget; }
    public void setHealthTarget(BigDecimal healthTarget) { this.healthTarget = healthTarget; }
    public String getDayStatus() { return dayStatus; }
    public void setDayStatus(String dayStatus) { this.dayStatus = dayStatus; }
    public Date getFirstAbnormalTime() { return firstAbnormalTime; }
    public void setFirstAbnormalTime(Date firstAbnormalTime) { this.firstAbnormalTime = firstAbnormalTime; }
    public Date getLastAbnormalTime() { return lastAbnormalTime; }
    public void setLastAbnormalTime(Date lastAbnormalTime) { this.lastAbnormalTime = lastAbnormalTime; }
    public Date getLastRunTime() { return lastRunTime; }
    public void setLastRunTime(Date lastRunTime) { this.lastRunTime = lastRunTime; }
    public String getLastResultStatus() { return lastResultStatus; }
    public void setLastResultStatus(String lastResultStatus) { this.lastResultStatus = lastResultStatus; }
    public String getAbnormalSummary() { return abnormalSummary; }
    public void setAbnormalSummary(String abnormalSummary) { this.abnormalSummary = abnormalSummary; }
}
