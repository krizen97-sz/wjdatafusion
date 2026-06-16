package com.hm.manage.domain;

import com.hm.common.annotation.Excel;
import com.hm.common.core.domain.BaseEntity;

public class SupportEquipmentAsset extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private String sourceType;
    private Long sourceId;
    private Long siteId;

    @Excel(name = "设备类型")
    private String assetType;

    @Excel(name = "设备类型名称")
    private String assetTypeLabel;

    @Excel(name = "设备名称")
    private String assetName;

    @Excel(name = "网络环境")
    private String networkEnv;

    @Excel(name = "IP地址")
    private String ipAddress;

    @Excel(name = "管理地址")
    private String manageIp;

    @Excel(name = "厂商")
    private String manufacturer;

    @Excel(name = "型号")
    private String assetModel;

    @Excel(name = "安装位置")
    private String installLocation;

    @Excel(name = "登录账号")
    private String loginUsername;

    @Excel(name = "绑定范围")
    private String bindingLabel;

    @Excel(name = "状态", readConverterExp = "0=正常,1=停用")
    private String status;

    private Long platformId;
    private String platformName;
    private Long mainPlatformId;
    private String mainPlatformName;
    private String bindingScope;

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

    public Long getSiteId()
    {
        return siteId;
    }

    public void setSiteId(Long siteId)
    {
        this.siteId = siteId;
    }

    public String getAssetType()
    {
        return assetType;
    }

    public void setAssetType(String assetType)
    {
        this.assetType = assetType;
    }

    public String getAssetTypeLabel()
    {
        return assetTypeLabel;
    }

    public void setAssetTypeLabel(String assetTypeLabel)
    {
        this.assetTypeLabel = assetTypeLabel;
    }

    public String getAssetName()
    {
        return assetName;
    }

    public void setAssetName(String assetName)
    {
        this.assetName = assetName;
    }

    public String getNetworkEnv()
    {
        return networkEnv;
    }

    public void setNetworkEnv(String networkEnv)
    {
        this.networkEnv = networkEnv;
    }

    public String getIpAddress()
    {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress)
    {
        this.ipAddress = ipAddress;
    }

    public String getManageIp()
    {
        return manageIp;
    }

    public void setManageIp(String manageIp)
    {
        this.manageIp = manageIp;
    }

    public String getManufacturer()
    {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer)
    {
        this.manufacturer = manufacturer;
    }

    public String getAssetModel()
    {
        return assetModel;
    }

    public void setAssetModel(String assetModel)
    {
        this.assetModel = assetModel;
    }

    public String getInstallLocation()
    {
        return installLocation;
    }

    public void setInstallLocation(String installLocation)
    {
        this.installLocation = installLocation;
    }

    public String getLoginUsername()
    {
        return loginUsername;
    }

    public void setLoginUsername(String loginUsername)
    {
        this.loginUsername = loginUsername;
    }

    public String getBindingLabel()
    {
        return bindingLabel;
    }

    public void setBindingLabel(String bindingLabel)
    {
        this.bindingLabel = bindingLabel;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public Long getPlatformId()
    {
        return platformId;
    }

    public void setPlatformId(Long platformId)
    {
        this.platformId = platformId;
    }

    public String getPlatformName()
    {
        return platformName;
    }

    public void setPlatformName(String platformName)
    {
        this.platformName = platformName;
    }

    public Long getMainPlatformId()
    {
        return mainPlatformId;
    }

    public void setMainPlatformId(Long mainPlatformId)
    {
        this.mainPlatformId = mainPlatformId;
    }

    public String getMainPlatformName()
    {
        return mainPlatformName;
    }

    public void setMainPlatformName(String mainPlatformName)
    {
        this.mainPlatformName = mainPlatformName;
    }

    public String getBindingScope()
    {
        return bindingScope;
    }

    public void setBindingScope(String bindingScope)
    {
        this.bindingScope = bindingScope;
    }
}
