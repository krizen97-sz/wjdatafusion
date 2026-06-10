package com.hm.manage.domain;

import java.math.BigDecimal;
import com.hm.common.core.domain.BaseEntity;

public class SupportTimInspectionTargetResult extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long resultId;
    private Long inspectionId;
    private Long itemId;
    private Long targetId;
    private String targetName;
    private String targetType;
    private String resultStatus;
    private BigDecimal actualValue;
    private String actualUnit;
    private String resultDetail;
    private String errorMessage;

    public Long getResultId()
    {
        return resultId;
    }

    public void setResultId(Long resultId)
    {
        this.resultId = resultId;
    }

    public Long getInspectionId()
    {
        return inspectionId;
    }

    public void setInspectionId(Long inspectionId)
    {
        this.inspectionId = inspectionId;
    }

    public Long getItemId()
    {
        return itemId;
    }

    public void setItemId(Long itemId)
    {
        this.itemId = itemId;
    }

    public Long getTargetId()
    {
        return targetId;
    }

    public void setTargetId(Long targetId)
    {
        this.targetId = targetId;
    }

    public String getTargetName()
    {
        return targetName;
    }

    public void setTargetName(String targetName)
    {
        this.targetName = targetName;
    }

    public String getTargetType()
    {
        return targetType;
    }

    public void setTargetType(String targetType)
    {
        this.targetType = targetType;
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

    public String getResultDetail()
    {
        return resultDetail;
    }

    public void setResultDetail(String resultDetail)
    {
        this.resultDetail = resultDetail;
    }

    public String getErrorMessage()
    {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage)
    {
        this.errorMessage = errorMessage;
    }
}
