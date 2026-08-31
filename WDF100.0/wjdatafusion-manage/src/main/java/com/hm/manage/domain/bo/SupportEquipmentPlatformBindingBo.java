package com.hm.manage.domain.bo;

public class SupportEquipmentPlatformBindingBo extends SupportEquipmentDeviceRefBo
{
    private Long siteId;
    private Long platformId;

    public Long getSiteId()
    {
        return siteId;
    }

    public void setSiteId(Long siteId)
    {
        this.siteId = siteId;
    }

    public Long getPlatformId()
    {
        return platformId;
    }

    public void setPlatformId(Long platformId)
    {
        this.platformId = platformId;
    }
}
