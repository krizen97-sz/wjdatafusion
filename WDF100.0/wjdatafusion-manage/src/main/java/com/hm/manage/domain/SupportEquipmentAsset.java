package com.hm.manage.domain;

import java.util.ArrayList;
import java.util.List;
import com.hm.common.annotation.Excel;
import com.hm.common.core.domain.BaseEntity;
import com.hm.manage.domain.vo.SupportEquipmentPlatformBindingVo;

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

    @Excel(name = "所属机房")
    private String equipmentRoom;

    @Excel(name = "机柜编号")
    private String cabinetNo;

    @Excel(name = "起始U位")
    private Integer rackUStart;

    @Excel(name = "结束U位")
    private Integer rackUEnd;

    @Excel(name = "端口数")
    private Integer portCount;

    @Excel(name = "上联设备")
    private String uplinkDevice;

    @Excel(name = "绑定范围")
    private String bindingLabel;

    @Excel(name = "状态", readConverterExp = "0=正常,1=停用")
    private String status;

    private Long platformId;
    private String platformName;
    private Long mainPlatformId;
    private String mainPlatformName;
    private String bindingScope;
    private String platformLevel;
    private Integer platformCount;
    private Boolean credentialCapable;
    private List<Long> platformIds = new ArrayList<>();
    private List<Long> mainPlatformIds = new ArrayList<>();
    private List<String> platformNames = new ArrayList<>();
    private List<SupportEquipmentPlatformBindingVo> platformBindings = new ArrayList<>();

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

    public Integer getPortCount()
    {
        return portCount;
    }

    public void setPortCount(Integer portCount)
    {
        this.portCount = portCount;
    }

    public String getUplinkDevice()
    {
        return uplinkDevice;
    }

    public void setUplinkDevice(String uplinkDevice)
    {
        this.uplinkDevice = uplinkDevice;
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

    public String getPlatformLevel()
    {
        return platformLevel;
    }

    public void setPlatformLevel(String platformLevel)
    {
        this.platformLevel = platformLevel;
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
