package com.hm.manage.domain.bo;

public class SupportEquipmentPlacementBo
{
    private Long siteId;
    private String sourceType;
    private Long sourceId;
    private Long roomId;
    private Long cabinetId;
    private Integer rackUStart;
    private Integer rackUEnd;

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

    public Long getRoomId()
    {
        return roomId;
    }

    public void setRoomId(Long roomId)
    {
        this.roomId = roomId;
    }

    public Long getCabinetId()
    {
        return cabinetId;
    }

    public void setCabinetId(Long cabinetId)
    {
        this.cabinetId = cabinetId;
    }

    public Integer getRackUStart()
    {
        return rackUStart;
    }

    public void setRackUStart(Integer rackUStart)
    {
        this.rackUStart = rackUStart;
    }

    public Integer getRackUEnd()
    {
        return rackUEnd;
    }

    public void setRackUEnd(Integer rackUEnd)
    {
        this.rackUEnd = rackUEnd;
    }
}
