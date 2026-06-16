package com.hm.manage.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hm.common.annotation.Excel;
import com.hm.common.core.domain.BaseEntity;

public class SupportHardwareAsset extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long assetId;

    private Long siteId;

    @Excel(name = "资产名称")
    private String assetName;

    @Excel(name = "资产类型")
    private String assetType;

    @Excel(name = "网络环境")
    private String networkEnv;

    @Excel(name = "IP地址")
    private String ipAddress;

    @Excel(name = "管理地址")
    private String manageIp;

    @Excel(name = "MAC地址")
    private String macAddress;

    @Excel(name = "厂商")
    private String manufacturer;

    @Excel(name = "型号")
    private String assetModel;

    @Excel(name = "序列号")
    private String serialNo;

    @Excel(name = "安装位置")
    private String installLocation;

    @Excel(name = "归属组织")
    private String ownerOrg;

    @Excel(name = "责任人")
    private String ownerContact;

    @Excel(name = "登录账号")
    private String loginUsername;

    private String loginPassword;

    @JsonIgnore
    private String loginPasswordCipher;

    @Excel(name = "状态", readConverterExp = "0=正常,1=停用")
    private String status;

    @Excel(name = "通道数")
    private Integer channelCount;

    @Excel(name = "输出类型")
    private String outputType;

    @Excel(name = "终端类型")
    private String terminalType;

    @Excel(name = "使用部门")
    private String department;

    @Excel(name = "使用位置")
    private String useLocation;

    @Excel(name = "交换机层级")
    private String switchLevel;

    @Excel(name = "端口数")
    private Integer portCount;

    @Excel(name = "上联设备")
    private String uplinkDevice;

    @Excel(name = "VLAN说明")
    private String vlanInfo;

    @Excel(name = "网闸模式")
    private String gatewayMode;

    @Excel(name = "数据流向")
    private String gatewayDirection;

    @Excel(name = "带宽")
    private String gatewayBandwidth;

    @Excel(name = "安全域说明")
    private String securityZone;

    private Long platformId;

    private String platformName;

    private String platformLevel;

    private Long mainPlatformId;

    private String mainPlatformName;

    public Long getAssetId()
    {
        return assetId;
    }

    public void setAssetId(Long assetId)
    {
        this.assetId = assetId;
    }

    public Long getSiteId()
    {
        return siteId;
    }

    public void setSiteId(Long siteId)
    {
        this.siteId = siteId;
    }

    public String getAssetName()
    {
        return assetName;
    }

    public void setAssetName(String assetName)
    {
        this.assetName = assetName;
    }

    public String getAssetType()
    {
        return assetType;
    }

    public void setAssetType(String assetType)
    {
        this.assetType = assetType;
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

    public String getMacAddress()
    {
        return macAddress;
    }

    public void setMacAddress(String macAddress)
    {
        this.macAddress = macAddress;
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

    public String getSerialNo()
    {
        return serialNo;
    }

    public void setSerialNo(String serialNo)
    {
        this.serialNo = serialNo;
    }

    public String getInstallLocation()
    {
        return installLocation;
    }

    public void setInstallLocation(String installLocation)
    {
        this.installLocation = installLocation;
    }

    public String getOwnerOrg()
    {
        return ownerOrg;
    }

    public void setOwnerOrg(String ownerOrg)
    {
        this.ownerOrg = ownerOrg;
    }

    public String getOwnerContact()
    {
        return ownerContact;
    }

    public void setOwnerContact(String ownerContact)
    {
        this.ownerContact = ownerContact;
    }

    public String getLoginUsername()
    {
        return loginUsername;
    }

    public void setLoginUsername(String loginUsername)
    {
        this.loginUsername = loginUsername;
    }

    public String getLoginPassword()
    {
        return loginPassword;
    }

    public void setLoginPassword(String loginPassword)
    {
        this.loginPassword = loginPassword;
    }

    public String getLoginPasswordCipher()
    {
        return loginPasswordCipher;
    }

    public void setLoginPasswordCipher(String loginPasswordCipher)
    {
        this.loginPasswordCipher = loginPasswordCipher;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public Integer getChannelCount()
    {
        return channelCount;
    }

    public void setChannelCount(Integer channelCount)
    {
        this.channelCount = channelCount;
    }

    public String getOutputType()
    {
        return outputType;
    }

    public void setOutputType(String outputType)
    {
        this.outputType = outputType;
    }

    public String getTerminalType()
    {
        return terminalType;
    }

    public void setTerminalType(String terminalType)
    {
        this.terminalType = terminalType;
    }

    public String getDepartment()
    {
        return department;
    }

    public void setDepartment(String department)
    {
        this.department = department;
    }

    public String getUseLocation()
    {
        return useLocation;
    }

    public void setUseLocation(String useLocation)
    {
        this.useLocation = useLocation;
    }

    public String getSwitchLevel()
    {
        return switchLevel;
    }

    public void setSwitchLevel(String switchLevel)
    {
        this.switchLevel = switchLevel;
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

    public String getVlanInfo()
    {
        return vlanInfo;
    }

    public void setVlanInfo(String vlanInfo)
    {
        this.vlanInfo = vlanInfo;
    }

    public String getGatewayMode()
    {
        return gatewayMode;
    }

    public void setGatewayMode(String gatewayMode)
    {
        this.gatewayMode = gatewayMode;
    }

    public String getGatewayDirection()
    {
        return gatewayDirection;
    }

    public void setGatewayDirection(String gatewayDirection)
    {
        this.gatewayDirection = gatewayDirection;
    }

    public String getGatewayBandwidth()
    {
        return gatewayBandwidth;
    }

    public void setGatewayBandwidth(String gatewayBandwidth)
    {
        this.gatewayBandwidth = gatewayBandwidth;
    }

    public String getSecurityZone()
    {
        return securityZone;
    }

    public void setSecurityZone(String securityZone)
    {
        this.securityZone = securityZone;
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
}
