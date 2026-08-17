package com.hm.manage.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import com.hm.common.core.domain.BaseEntity;

public class SupportAutoInspectionTemplateStep extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long stepId;
    private Long templateId;
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
    private List<Long> targetIds = new ArrayList<>();
    private SupportAutoInspectionTarget target;
    private List<SupportAutoInspectionTarget> targets = new ArrayList<>();

    public Long getStepId() { return stepId; }
    public void setStepId(Long stepId) { this.stepId = stepId; }
    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }
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
    public List<Long> getTargetIds() { return targetIds; }
    public void setTargetIds(List<Long> targetIds) { this.targetIds = targetIds; }
    public SupportAutoInspectionTarget getTarget() { return target; }
    public void setTarget(SupportAutoInspectionTarget target) { this.target = target; }
    public List<SupportAutoInspectionTarget> getTargets() { return targets; }
    public void setTargets(List<SupportAutoInspectionTarget> targets) { this.targets = targets; }
}
