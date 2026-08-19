package com.hm.manage.domain.vo;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public class AutoInspectionTargetPreviewVo
{
    private boolean passed;
    private String resultStatus;
    private String message;
    private String targetName;
    private String targetType;
    private BigDecimal actualValue;
    private String actualUnit;
    private String detail;
    private String errorMessage;
    private Map<String, Object> preview = new LinkedHashMap<>();

    public boolean isPassed() { return passed; }
    public void setPassed(boolean passed) { this.passed = passed; }
    public String getResultStatus() { return resultStatus; }
    public void setResultStatus(String resultStatus) { this.resultStatus = resultStatus; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getTargetName() { return targetName; }
    public void setTargetName(String targetName) { this.targetName = targetName; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public BigDecimal getActualValue() { return actualValue; }
    public void setActualValue(BigDecimal actualValue) { this.actualValue = actualValue; }
    public String getActualUnit() { return actualUnit; }
    public void setActualUnit(String actualUnit) { this.actualUnit = actualUnit; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Map<String, Object> getPreview() { return preview; }
    public void setPreview(Map<String, Object> preview) { this.preview = preview == null ? new LinkedHashMap<>() : preview; }
}
