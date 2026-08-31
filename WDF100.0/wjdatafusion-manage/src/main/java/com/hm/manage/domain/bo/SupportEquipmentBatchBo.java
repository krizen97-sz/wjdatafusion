package com.hm.manage.domain.bo;

import java.util.ArrayList;
import java.util.List;

public class SupportEquipmentBatchBo
{
    private Long siteId;
    private List<SupportEquipmentDeviceRefBo> devices = new ArrayList<>();

    public Long getSiteId()
    {
        return siteId;
    }

    public void setSiteId(Long siteId)
    {
        this.siteId = siteId;
    }

    public List<SupportEquipmentDeviceRefBo> getDevices()
    {
        return devices;
    }

    public void setDevices(List<SupportEquipmentDeviceRefBo> devices)
    {
        this.devices = devices;
    }
}
