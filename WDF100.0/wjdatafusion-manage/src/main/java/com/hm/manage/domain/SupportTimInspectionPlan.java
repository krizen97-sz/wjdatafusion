package com.hm.manage.domain;

import java.util.List;
import com.hm.common.core.domain.BaseEntity;

public class SupportTimInspectionPlan extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long planId;
    private String planName;
    private String cronExpression;
    private Long jobId;
    private String reportStyle;
    private String status;
    private Integer itemCount;
    private Integer targetCount;
    private List<SupportTimInspectionPlanItem> items;

    public Long getPlanId()
    {
        return planId;
    }

    public void setPlanId(Long planId)
    {
        this.planId = planId;
    }

    public String getPlanName()
    {
        return planName;
    }

    public void setPlanName(String planName)
    {
        this.planName = planName;
    }

    public String getCronExpression()
    {
        return cronExpression;
    }

    public void setCronExpression(String cronExpression)
    {
        this.cronExpression = cronExpression;
    }

    public Long getJobId()
    {
        return jobId;
    }

    public void setJobId(Long jobId)
    {
        this.jobId = jobId;
    }

    public String getReportStyle()
    {
        return reportStyle;
    }

    public void setReportStyle(String reportStyle)
    {
        this.reportStyle = reportStyle;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public Integer getItemCount()
    {
        return itemCount;
    }

    public void setItemCount(Integer itemCount)
    {
        this.itemCount = itemCount;
    }

    public Integer getTargetCount()
    {
        return targetCount;
    }

    public void setTargetCount(Integer targetCount)
    {
        this.targetCount = targetCount;
    }

    public List<SupportTimInspectionPlanItem> getItems()
    {
        return items;
    }

    public void setItems(List<SupportTimInspectionPlanItem> items)
    {
        this.items = items;
    }
}
