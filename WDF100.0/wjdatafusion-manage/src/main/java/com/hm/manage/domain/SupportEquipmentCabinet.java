package com.hm.manage.domain;

import com.hm.common.core.domain.BaseEntity;

public class SupportEquipmentCabinet extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long cabinetId;
    private Long roomId;
    private Long siteId;
    private String cabinetNo;
    private Integer uCapacity;
    private String status;

    public Long getCabinetId()
    {
        return cabinetId;
    }

    public void setCabinetId(Long cabinetId)
    {
        this.cabinetId = cabinetId;
    }

    public Long getRoomId()
    {
        return roomId;
    }

    public void setRoomId(Long roomId)
    {
        this.roomId = roomId;
    }

    public Long getSiteId()
    {
        return siteId;
    }

    public void setSiteId(Long siteId)
    {
        this.siteId = siteId;
    }

    public String getCabinetNo()
    {
        return cabinetNo;
    }

    public void setCabinetNo(String cabinetNo)
    {
        this.cabinetNo = cabinetNo;
    }

    public Integer getUCapacity()
    {
        return uCapacity;
    }

    public void setUCapacity(Integer uCapacity)
    {
        this.uCapacity = uCapacity;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }
}
