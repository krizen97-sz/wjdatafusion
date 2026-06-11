package com.hm.manage.domain;

import com.hm.common.core.domain.BaseEntity;

public class SupportTimInspectionPlanTarget extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long planTargetId;
    private Long planId;
    private String itemCode;
    private Long targetId;

    public Long getPlanTargetId()
    {
        return planTargetId;
    }

    public void setPlanTargetId(Long planTargetId)
    {
        this.planTargetId = planTargetId;
    }

    public Long getPlanId()
    {
        return planId;
    }

    public void setPlanId(Long planId)
    {
        this.planId = planId;
    }

    public String getItemCode()
    {
        return itemCode;
    }

    public void setItemCode(String itemCode)
    {
        this.itemCode = itemCode;
    }

    public Long getTargetId()
    {
        return targetId;
    }

    public void setTargetId(Long targetId)
    {
        this.targetId = targetId;
    }
}
