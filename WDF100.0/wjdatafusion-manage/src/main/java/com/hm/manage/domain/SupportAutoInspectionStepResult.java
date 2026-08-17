package com.hm.manage.domain;

import java.math.BigDecimal;
import com.hm.common.core.domain.BaseEntity;

public class SupportAutoInspectionStepResult extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long stepResultId;
    private Long recordId;
    private Long stepId;
    private String toolCode;
    private String toolName;
    private String toolType;
    private String stepName;
    private String enabledFlag;
    private Integer sortOrder;
    private BigDecimal thresholdValue;
    private String thresholdUnit;
    private String compareRule;
    private Integer timeWindowMinutes;
    private Integer timeoutSeconds;
    private String stepParams;
    private String resultStatus;
    private BigDecimal actualValue;
    private String actualUnit;
    private String resultSummary;

    public Long getStepResultId() { return stepResultId; }
    public void setStepResultId(Long stepResultId) { this.stepResultId = stepResultId; }
    public Long getRecordId() { return recordId; }
    public void setRecordId(Long recordId) { this.recordId = recordId; }
    public Long getStepId() { return stepId; }
    public void setStepId(Long stepId) { this.stepId = stepId; }
    public String getToolCode() { return toolCode; }
    public void setToolCode(String toolCode) { this.toolCode = toolCode; }
    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }
    public String getToolType() { return toolType; }
    public void setToolType(String toolType) { this.toolType = toolType; }
    public String getStepName() { return stepName; }
    public void setStepName(String stepName) { this.stepName = stepName; }
    public String getEnabledFlag() { return enabledFlag; }
    public void setEnabledFlag(String enabledFlag) { this.enabledFlag = enabledFlag; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public BigDecimal getThresholdValue() { return thresholdValue; }
    public void setThresholdValue(BigDecimal thresholdValue) { this.thresholdValue = thresholdValue; }
    public String getThresholdUnit() { return thresholdUnit; }
    public void setThresholdUnit(String thresholdUnit) { this.thresholdUnit = thresholdUnit; }
    public String getCompareRule() { return compareRule; }
    public void setCompareRule(String compareRule) { this.compareRule = compareRule; }
    public Integer getTimeWindowMinutes() { return timeWindowMinutes; }
    public void setTimeWindowMinutes(Integer timeWindowMinutes) { this.timeWindowMinutes = timeWindowMinutes; }
    public Integer getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(Integer timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public String getStepParams() { return stepParams; }
    public void setStepParams(String stepParams) { this.stepParams = stepParams; }
    public String getResultStatus() { return resultStatus; }
    public void setResultStatus(String resultStatus) { this.resultStatus = resultStatus; }
    public BigDecimal getActualValue() { return actualValue; }
    public void setActualValue(BigDecimal actualValue) { this.actualValue = actualValue; }
    public String getActualUnit() { return actualUnit; }
    public void setActualUnit(String actualUnit) { this.actualUnit = actualUnit; }
    public String getResultSummary() { return resultSummary; }
    public void setResultSummary(String resultSummary) { this.resultSummary = resultSummary; }
}
