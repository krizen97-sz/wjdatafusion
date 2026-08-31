package com.hm.manage.domain.vo;

import java.util.ArrayList;
import java.util.List;

public class SupportEquipmentTopologyDeviceVo
{
    private String deviceKey;
    private String sourceType;
    private Long sourceId;
    private Long siteId;
    private String assetType;
    private String assetTypeLabel;
    private String assetName;
    private String ipAddress;
    private String manageIp;
    private String networkEnv;
    private String manufacturer;
    private String assetModel;
    private String installLocation;
    private String loginUsername;
    private String equipmentRoom;
    private String cabinetNo;
    private Long roomId;
    private Long cabinetId;
    private Integer rackUStart;
    private Integer rackUEnd;
    private Integer legacyPortCount;
    private String legacyUplinkDevice;
    private String status;
    private Long platformId;
    private String platformName;
    private String platformLevel;
    private Long mainPlatformId;
    private String mainPlatformName;
    private String bindingScope;
    private String bindingLabel;
    private Integer platformCount;
    private Boolean credentialCapable;
    private List<Long> platformIds = new ArrayList<>();
    private List<Long> mainPlatformIds = new ArrayList<>();
    private List<String> platformNames = new ArrayList<>();
    private List<SupportEquipmentPlatformBindingVo> platformBindings = new ArrayList<>();

    public String getDeviceKey()
    {
        return deviceKey;
    }

    public void setDeviceKey(String deviceKey)
    {
        this.deviceKey = deviceKey;
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

    public String getNetworkEnv()
    {
        return networkEnv;
    }

    public void setNetworkEnv(String networkEnv)
    {
        this.networkEnv = networkEnv;
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

    public String getEquipmentRoom()
    {
        return equipmentRoom;
    }

    public void setEquipmentRoom(String equipmentRoom)
    {
        this.equipmentRoom = equipmentRoom;
    }

    public String getCabinetNo()
    {
        return cabinetNo;
    }

    public void setCabinetNo(String cabinetNo)
    {
        this.cabinetNo = cabinetNo;
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

    public Integer getLegacyPortCount()
    {
        return legacyPortCount;
    }

    public void setLegacyPortCount(Integer legacyPortCount)
    {
        this.legacyPortCount = legacyPortCount;
    }

    public String getLegacyUplinkDevice()
    {
        return legacyUplinkDevice;
    }

    public void setLegacyUplinkDevice(String legacyUplinkDevice)
    {
        this.legacyUplinkDevice = legacyUplinkDevice;
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

    public String getPlatformLevel()
    {
        return platformLevel;
    }

    public void setPlatformLevel(String platformLevel)
    {
        this.platformLevel = platformLevel;
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

    public String getBindingLabel()
    {
        return bindingLabel;
    }

    public void setBindingLabel(String bindingLabel)
    {
        this.bindingLabel = bindingLabel;
    }

    public Integer getPlatformCount()
    {
        return platformCount;
    }

    public void setPlatformCount(Integer platformCount)
    {
        this.platformCount = platformCount;
    }

    public Boolean getCredentialCapable()
    {
        return credentialCapable;
    }

    public void setCredentialCapable(Boolean credentialCapable)
    {
        this.credentialCapable = credentialCapable;
    }

    public List<Long> getPlatformIds()
    {
        return platformIds;
    }

    public void setPlatformIds(List<Long> platformIds)
    {
        this.platformIds = platformIds;
    }

    public List<Long> getMainPlatformIds()
    {
        return mainPlatformIds;
    }

    public void setMainPlatformIds(List<Long> mainPlatformIds)
    {
        this.mainPlatformIds = mainPlatformIds;
    }

    public List<String> getPlatformNames()
    {
        return platformNames;
    }

    public void setPlatformNames(List<String> platformNames)
    {
        this.platformNames = platformNames;
    }

    public List<SupportEquipmentPlatformBindingVo> getPlatformBindings()
    {
        return platformBindings;
    }

    public void setPlatformBindings(List<SupportEquipmentPlatformBindingVo> platformBindings)
    {
        this.platformBindings = platformBindings;
    }
}
