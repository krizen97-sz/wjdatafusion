package com.hm.manage.domain;

import java.math.BigDecimal;
import com.hm.common.core.domain.BaseEntity;

public class SupportTimInspectionItem extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long itemId;
    private Long inspectionId;
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
    private String resultStatus;
    private BigDecimal actualValue;
    private String actualUnit;
    private String resultSummary;

    public Long getItemId()
    {
        return itemId;
    }

    public void setItemId(Long itemId)
    {
        this.itemId = itemId;
    }

    public Long getInspectionId()
    {
        return inspectionId;
    }

    public void setInspectionId(Long inspectionId)
    {
        this.inspectionId = inspectionId;
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

    public String getResultStatus()
    {
        return resultStatus;
    }

    public void setResultStatus(String resultStatus)
    {
        this.resultStatus = resultStatus;
    }

    public BigDecimal getActualValue()
    {
        return actualValue;
    }

    public void setActualValue(BigDecimal actualValue)
    {
        this.actualValue = actualValue;
    }

    public String getActualUnit()
    {
        return actualUnit;
    }

    public void setActualUnit(String actualUnit)
    {
        this.actualUnit = actualUnit;
    }

    public String getResultSummary()
    {
        return resultSummary;
    }

    public void setResultSummary(String resultSummary)
    {
        this.resultSummary = resultSummary;
    }
}
