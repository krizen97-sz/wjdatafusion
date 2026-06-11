package com.hm.manage.domain.vo;

import com.hm.common.annotation.Excel;

public class SupportAutoInspectionExportVo
{
    @Excel(name = "记录ID")
    private Long recordId;

    @Excel(name = "巡检时间")
    private String inspectionTime;

    @Excel(name = "模板名称")
    private String templateName;

    @Excel(name = "计划名称")
    private String planName;

    @Excel(name = "执行来源")
    private String sourceType;

    @Excel(name = "巡检结果")
    private String resultStatus;

    @Excel(name = "执行人")
    private String executorName;

    @Excel(name = "巡检摘要", width = 40)
    private String summary;

    @Excel(name = "异常摘要", width = 50)
    private String abnormalSummary;

    @Excel(name = "步骤结果", width = 60)
    private String stepSummary;

    @Excel(name = "目标明细", width = 80)
    private String targetSummary;

    public Long getRecordId() { return recordId; }
    public void setRecordId(Long recordId) { this.recordId = recordId; }
    public String getInspectionTime() { return inspectionTime; }
    public void setInspectionTime(String inspectionTime) { this.inspectionTime = inspectionTime; }
    public String getTemplateName() { return templateName; }
    public void setTemplateName(String templateName) { this.templateName = templateName; }
    public String getPlanName() { return planName; }
    public void setPlanName(String planName) { this.planName = planName; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getResultStatus() { return resultStatus; }
    public void setResultStatus(String resultStatus) { this.resultStatus = resultStatus; }
    public String getExecutorName() { return executorName; }
    public void setExecutorName(String executorName) { this.executorName = executorName; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getAbnormalSummary() { return abnormalSummary; }
    public void setAbnormalSummary(String abnormalSummary) { this.abnormalSummary = abnormalSummary; }
    public String getStepSummary() { return stepSummary; }
    public void setStepSummary(String stepSummary) { this.stepSummary = stepSummary; }
    public String getTargetSummary() { return targetSummary; }
    public void setTargetSummary(String targetSummary) { this.targetSummary = targetSummary; }
}
