package com.hm.manage.domain.bo;

import com.hm.manage.domain.SupportHardwareAsset;
import com.hm.manage.domain.SupportServer;

public class SupportEquipmentCreateBo
{
    private Long siteId;
    private Long platformId;
    private SupportServer server;
    private SupportHardwareAsset hardware;

    public Long getSiteId() { return siteId; }
    public void setSiteId(Long siteId) { this.siteId = siteId; }
    public Long getPlatformId() { return platformId; }
    public void setPlatformId(Long platformId) { this.platformId = platformId; }
    public SupportServer getServer() { return server; }
    public void setServer(SupportServer server) { this.server = server; }
    public SupportHardwareAsset getHardware() { return hardware; }
    public void setHardware(SupportHardwareAsset hardware) { this.hardware = hardware; }
}
