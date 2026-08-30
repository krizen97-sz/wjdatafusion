package com.hm.manage.domain;

import java.math.BigDecimal;
import com.hm.common.core.domain.BaseEntity;

public class SupportEquipmentRoom extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long roomId;
    private Long siteId;
    private String roomName;
    private String roomCode;
    private BigDecimal roomWidth;
    private BigDecimal roomDepth;
    private String status;

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

    public String getRoomName()
    {
        return roomName;
    }

    public void setRoomName(String roomName)
    {
        this.roomName = roomName;
    }

    public String getRoomCode()
    {
        return roomCode;
    }

    public void setRoomCode(String roomCode)
    {
        this.roomCode = roomCode;
    }

    public BigDecimal getRoomWidth()
    {
        return roomWidth;
    }

    public void setRoomWidth(BigDecimal roomWidth)
    {
        this.roomWidth = roomWidth;
    }

    public BigDecimal getRoomDepth()
    {
        return roomDepth;
    }

    public void setRoomDepth(BigDecimal roomDepth)
    {
        this.roomDepth = roomDepth;
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
