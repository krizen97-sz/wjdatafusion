package com.hm.manage.domain;

import java.math.BigDecimal;
import com.hm.common.core.domain.BaseEntity;

public class SupportAutoInspectionTool extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private String toolCode;
    private String toolName;
    private String toolType;
    private String targetType;
    private String valueUnit;
    private String defaultCompareRule;
    private BigDecimal defaultThresholdValue;
    private Integer defaultTimeoutSeconds;
    private Integer defaultTimeWindowMinutes;
    private String paramSchema;
    private String builtInFlag;
    private String status;

    public String getToolCode() { return toolCode; }
    public void setToolCode(String toolCode) { this.toolCode = toolCode; }
    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }
    public String getToolType() { return toolType; }
    public void setToolType(String toolType) { this.toolType = toolType; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public String getValueUnit() { return valueUnit; }
    public void setValueUnit(String valueUnit) { this.valueUnit = valueUnit; }
    public String getDefaultCompareRule() { return defaultCompareRule; }
    public void setDefaultCompareRule(String defaultCompareRule) { this.defaultCompareRule = defaultCompareRule; }
    public BigDecimal getDefaultThresholdValue() { return defaultThresholdValue; }
    public void setDefaultThresholdValue(BigDecimal defaultThresholdValue) { this.defaultThresholdValue = defaultThresholdValue; }
    public Integer getDefaultTimeoutSeconds() { return defaultTimeoutSeconds; }
    public void setDefaultTimeoutSeconds(Integer defaultTimeoutSeconds) { this.defaultTimeoutSeconds = defaultTimeoutSeconds; }
    public Integer getDefaultTimeWindowMinutes() { return defaultTimeWindowMinutes; }
    public void setDefaultTimeWindowMinutes(Integer defaultTimeWindowMinutes) { this.defaultTimeWindowMinutes = defaultTimeWindowMinutes; }
    public String getParamSchema() { return paramSchema; }
    public void setParamSchema(String paramSchema) { this.paramSchema = paramSchema; }
    public String getBuiltInFlag() { return builtInFlag; }
    public void setBuiltInFlag(String builtInFlag) { this.builtInFlag = builtInFlag; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
