package com.hm.manage.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.hm.common.annotation.Excel;
import com.hm.common.core.domain.BaseEntity;

public class SupportTimInspection extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long inspectionId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "巡检时间", dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date inspectionTime;

    @Excel(name = "巡检类型")
    private String inspectionType;

    @Excel(name = "执行来源", readConverterExp = "AUTO=自动,MANUAL=手动")
    private String sourceType;

    @Excel(name = "巡检结果", readConverterExp = "1=正常,2=异常,3=未检测")
    private String resultStatus;

    @Excel(name = "执行人")
    private String executorName;

    @Excel(name = "启用项数")
    private Integer enabledItemCount;

    @Excel(name = "跳过项数")
    private Integer skippedItemCount;

    @Excel(name = "巡检摘要")
    private String summary;

    @Excel(name = "异常摘要")
    private String abnormalSummary;

    public Long getInspectionId()
    {
        return inspectionId;
    }

    public void setInspectionId(Long inspectionId)
    {
        this.inspectionId = inspectionId;
    }

    public Date getInspectionTime()
    {
        return inspectionTime;
    }

    public void setInspectionTime(Date inspectionTime)
    {
        this.inspectionTime = inspectionTime;
    }

    public String getInspectionType()
    {
        return inspectionType;
    }

    public void setInspectionType(String inspectionType)
    {
        this.inspectionType = inspectionType;
    }

    public String getSourceType()
    {
        return sourceType;
    }

    public void setSourceType(String sourceType)
    {
        this.sourceType = sourceType;
    }

    public String getResultStatus()
    {
        return resultStatus;
    }

    public void setResultStatus(String resultStatus)
    {
        this.resultStatus = resultStatus;
    }

    public String getExecutorName()
    {
        return executorName;
    }

    public void setExecutorName(String executorName)
    {
        this.executorName = executorName;
    }

    public Integer getEnabledItemCount()
    {
        return enabledItemCount;
    }

    public void setEnabledItemCount(Integer enabledItemCount)
    {
        this.enabledItemCount = enabledItemCount;
    }

    public Integer getSkippedItemCount()
    {
        return skippedItemCount;
    }

    public void setSkippedItemCount(Integer skippedItemCount)
    {
        this.skippedItemCount = skippedItemCount;
    }

    public String getSummary()
    {
        return summary;
    }

    public void setSummary(String summary)
    {
        this.summary = summary;
    }

    public String getAbnormalSummary()
    {
        return abnormalSummary;
    }

    public void setAbnormalSummary(String abnormalSummary)
    {
        this.abnormalSummary = abnormalSummary;
    }
}
