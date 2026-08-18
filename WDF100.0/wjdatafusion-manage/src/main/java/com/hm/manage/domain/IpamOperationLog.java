package com.hm.manage.domain;

import com.hm.common.core.domain.BaseEntity;

public class IpamOperationLog extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long logId;
    private String actionType;
    private String targetType;
    private Long targetId;
    private String ipAddress;
    private String summary;
    private String detailContent;
    private String operatorName;
    private String operatorIp;

    public Long getLogId()
    {
        return logId;
    }

    public void setLogId(Long logId)
    {
        this.logId = logId;
    }

    public String getActionType()
    {
        return actionType;
    }

    public void setActionType(String actionType)
    {
        this.actionType = actionType;
    }

    public String getTargetType()
    {
        return targetType;
    }

    public void setTargetType(String targetType)
    {
        this.targetType = targetType;
    }

    public Long getTargetId()
    {
        return targetId;
    }

    public void setTargetId(Long targetId)
    {
        this.targetId = targetId;
    }

    public String getIpAddress()
    {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress)
    {
        this.ipAddress = ipAddress;
    }

    public String getSummary()
    {
        return summary;
    }

    public void setSummary(String summary)
    {
        this.summary = summary;
    }

    public String getDetailContent()
    {
        return detailContent;
    }

    public void setDetailContent(String detailContent)
    {
        this.detailContent = detailContent;
    }

    public String getOperatorName()
    {
        return operatorName;
    }

    public void setOperatorName(String operatorName)
    {
        this.operatorName = operatorName;
    }

    public String getOperatorIp()
    {
        return operatorIp;
    }

    public void setOperatorIp(String operatorIp)
    {
        this.operatorIp = operatorIp;
    }
}
