package com.hm.manage.domain;

import com.hm.common.core.domain.BaseEntity;

public class SupportEquipmentLink extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long linkId;
    private Long siteId;
    private String sourceType;
    private Long sourceId;
    private String targetType;
    private Long targetId;
    private String mediumType;
    private Integer portCount;
    private String sourcePort;
    private String targetPort;
    private String status;

    private String sourceName;
    private String sourceIp;
    private String targetName;
    private String targetIp;

    public Long getLinkId()
    {
        return linkId;
    }

    public void setLinkId(Long linkId)
    {
        this.linkId = linkId;
    }

    public Long getSiteId()
    {
        return siteId;
    }

    public void setSiteId(Long siteId)
    {
        this.siteId = siteId;
    }

    public String getSourceType()
    {
        return sourceType;
    }

    public void setSourceType(String sourceType)
    {
        this.sourceType = sourceType;
    }

    public Long getSourceId()
    {
        return sourceId;
    }

    public void setSourceId(Long sourceId)
    {
        this.sourceId = sourceId;
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

    public String getMediumType()
    {
        return mediumType;
    }

    public void setMediumType(String mediumType)
    {
        this.mediumType = mediumType;
    }

    public Integer getPortCount()
    {
        return portCount;
    }

    public void setPortCount(Integer portCount)
    {
        this.portCount = portCount;
    }

    public String getSourcePort()
    {
        return sourcePort;
    }

    public void setSourcePort(String sourcePort)
    {
        this.sourcePort = sourcePort;
    }

    public String getTargetPort()
    {
        return targetPort;
    }

    public void setTargetPort(String targetPort)
    {
        this.targetPort = targetPort;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getSourceName()
    {
        return sourceName;
    }

    public void setSourceName(String sourceName)
    {
        this.sourceName = sourceName;
    }

    public String getSourceIp()
    {
        return sourceIp;
    }

    public void setSourceIp(String sourceIp)
    {
        this.sourceIp = sourceIp;
    }

    public String getTargetName()
    {
        return targetName;
    }

    public void setTargetName(String targetName)
    {
        this.targetName = targetName;
    }

    public String getTargetIp()
    {
        return targetIp;
    }

    public void setTargetIp(String targetIp)
    {
        this.targetIp = targetIp;
    }
}
