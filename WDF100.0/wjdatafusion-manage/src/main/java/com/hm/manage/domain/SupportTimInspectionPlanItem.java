package com.hm.manage.domain;

import java.math.BigDecimal;
import java.util.List;
import com.hm.common.core.domain.BaseEntity;

public class SupportTimInspectionPlanItem extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long planItemId;
    private Long planId;
    private String itemCode;
    private String itemName;
    private String itemType;
    private String enabledFlag;
    private Integer sortOrder;
    private BigDecimal thresholdValue;
    private String thresholdUnit;
    private String compareRule;
    private Integer timeWindowMinutes;
    private Integer timeoutSeconds;
    private List<Long> targetIds;

    public Long getPlanItemId()
    {
        return planItemId;
    }

    public void setPlanItemId(Long planItemId)
    {
        this.planItemId = planItemId;
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

    public String getItemName()
    {
        return itemName;
    }

    public void setItemName(String itemName)
    {
        this.itemName = itemName;
    }

    public String getItemType()
    {
        return itemType;
    }

    public void setItemType(String itemType)
    {
        this.itemType = itemType;
    }

    public String getEnabledFlag()
    {
        return enabledFlag;
    }

    public void setEnabledFlag(String enabledFlag)
    {
        this.enabledFlag = enabledFlag;
    }

    public Integer getSortOrder()
    {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder)
    {
        this.sortOrder = sortOrder;
    }

    public BigDecimal getThresholdValue()
    {
        return thresholdValue;
    }

    public void setThresholdValue(BigDecimal thresholdValue)
    {
        this.thresholdValue = thresholdValue;
    }

    public String getThresholdUnit()
    {
        return thresholdUnit;
    }

    public void setThresholdUnit(String thresholdUnit)
    {
        this.thresholdUnit = thresholdUnit;
    }

    public String getCompareRule()
    {
        return compareRule;
    }

    public void setCompareRule(String compareRule)
    {
        this.compareRule = compareRule;
    }

    public Integer getTimeWindowMinutes()
    {
        return timeWindowMinutes;
    }

    public void setTimeWindowMinutes(Integer timeWindowMinutes)
    {
        this.timeWindowMinutes = timeWindowMinutes;
    }

    public Integer getTimeoutSeconds()
    {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(Integer timeoutSeconds)
    {
        this.timeoutSeconds = timeoutSeconds;
    }

    public List<Long> getTargetIds()
    {
        return targetIds;
    }

    public void setTargetIds(List<Long> targetIds)
    {
        this.targetIds = targetIds;
    }
}
