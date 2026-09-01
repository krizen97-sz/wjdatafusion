package com.hm.manage.domain;

import java.math.BigDecimal;
import java.util.Date;
import com.hm.common.core.domain.BaseEntity;

public class SupportAutoInspectionTargetResult extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long resultId;
    private Long recordId;
    private Long stepResultId;
    private String stepName;
    private String toolCode;
    private String toolName;
    private Integer sortOrder;
    private Long targetId;
    private String targetName;
    private String targetType;
    private String resultStatus;
    private BigDecimal actualValue;
    private String actualUnit;
    private String evaluationMode;
    private BigDecimal previousValue;
    private BigDecimal changeValue;
    private String evaluationRule;
    private String baselineFlag;
    private String comparisonScope;
    private String windowKey;
    private Date windowStart;
    private Date windowEnd;
    private String resultDetail;
    private String errorMessage;

    public Long getResultId() { return resultId; }
    public void setResultId(Long resultId) { this.resultId = resultId; }
    public Long getRecordId() { return recordId; }
    public void setRecordId(Long recordId) { this.recordId = recordId; }
    public Long getStepResultId() { return stepResultId; }
    public void setStepResultId(Long stepResultId) { this.stepResultId = stepResultId; }
    public String getStepName() { return stepName; }
    public void setStepName(String stepName) { this.stepName = stepName; }
    public String getToolCode() { return toolCode; }
    public void setToolCode(String toolCode) { this.toolCode = toolCode; }
    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
    public String getTargetName() { return targetName; }
    public void setTargetName(String targetName) { this.targetName = targetName; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public String getResultStatus() { return resultStatus; }
    public void setResultStatus(String resultStatus) { this.resultStatus = resultStatus; }
    public BigDecimal getActualValue() { return actualValue; }
    public void setActualValue(BigDecimal actualValue) { this.actualValue = actualValue; }
    public String getActualUnit() { return actualUnit; }
    public void setActualUnit(String actualUnit) { this.actualUnit = actualUnit; }
    public String getEvaluationMode() { return evaluationMode; }
    public void setEvaluationMode(String evaluationMode) { this.evaluationMode = evaluationMode; }
    public BigDecimal getPreviousValue() { return previousValue; }
    public void setPreviousValue(BigDecimal previousValue) { this.previousValue = previousValue; }
    public BigDecimal getChangeValue() { return changeValue; }
    public void setChangeValue(BigDecimal changeValue) { this.changeValue = changeValue; }
    public String getEvaluationRule() { return evaluationRule; }
    public void setEvaluationRule(String evaluationRule) { this.evaluationRule = evaluationRule; }
    public String getBaselineFlag() { return baselineFlag; }
    public void setBaselineFlag(String baselineFlag) { this.baselineFlag = baselineFlag; }
    public String getComparisonScope() { return comparisonScope; }
    public void setComparisonScope(String comparisonScope) { this.comparisonScope = comparisonScope; }
    public String getWindowKey() { return windowKey; }
    public void setWindowKey(String windowKey) { this.windowKey = windowKey; }
    public Date getWindowStart() { return windowStart; }
    public void setWindowStart(Date windowStart) { this.windowStart = windowStart; }
    public Date getWindowEnd() { return windowEnd; }
    public void setWindowEnd(Date windowEnd) { this.windowEnd = windowEnd; }
    public String getResultDetail() { return resultDetail; }
    public void setResultDetail(String resultDetail) { this.resultDetail = resultDetail; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
