package com.hm.manage.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hm.common.annotation.Excel;
import com.hm.common.core.domain.BaseEntity;

public class IpamAddress extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long addressId;
    private Long networkId;
    private Long segmentId;

    @Excel(name = "现场IP")
    private String ipAddress;

    private Long ipValue;

    @Excel(name = "状态", readConverterExp = "FREE=空闲,RESERVED=保留,ALLOCATED=已占用,ISSUED=已下发,DISABLED=禁用")
    private String status;

    @Excel(name = "小区名称")
    private String communityName;

    @Excel(name = "设备类别", readConverterExp = "RECORDER=录像机,CAMERA=摄像机,NVR=NVR,CVR=CVR," +
        "PLATFORM=平台,STORAGE_SERVER=存储服务器,DECODER=解码器,ACCESS_CONTROL=门禁," +
        "FACE_DEVICE=人脸,BARRIER_GATE=道闸,IAC=IAC,MAPPING_DEVICE=网络映射设备,OTHER=其他")
    private String targetType;

    @Excel(name = "设备名称")
    private String targetName;

    @Excel(name = "品牌")
    private String manufacturer;

    @Excel(name = "小区内网IP")
    private String internalIpAddress;

    @Excel(name = "接入单位")
    private String accessUnit;

    @Excel(name = "用途说明")
    private String purpose;

    @Excel(name = "登录账号")
    private String loginUsername;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String loginPassword;

    @Excel(name = "密码已配置")
    private Boolean credentialConfigured;

    @Excel(name = "映射地址")
    private String mappingAddress;

    @Excel(name = "映射端口")
    private String mappingPort;

    @Excel(name = "映射说明")
    private String mappingDescription;

    @Excel(name = "责任人")
    private String ownerName;

    @Excel(name = "联系电话")
    private String ownerPhone;

    private String issueBatch;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "分配时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date allocatedTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date issuedTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date releasedTime;

    private String networkName;
    private String policeStationName;
    private String networkCidr;
    private String segmentName;
    private String segmentCidr;
    private Integer lastOctet;
    private Boolean boundaryAddress;
    private String reservedReason;
    private String connectivityStatus;
    private Long scanResponseTimeMs;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastScanTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastOnlineTime;

    public Long getAddressId()
    {
        return addressId;
    }

    public void setAddressId(Long addressId)
    {
        this.addressId = addressId;
    }

    public Long getNetworkId()
    {
        return networkId;
    }

    public void setNetworkId(Long networkId)
    {
        this.networkId = networkId;
    }

    public Long getSegmentId()
    {
        return segmentId;
    }

    public void setSegmentId(Long segmentId)
    {
        this.segmentId = segmentId;
    }

    public String getIpAddress()
    {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress)
    {
        this.ipAddress = ipAddress;
    }

    public Long getIpValue()
    {
        return ipValue;
    }

    public void setIpValue(Long ipValue)
    {
        this.ipValue = ipValue;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getCommunityName()
    {
        return communityName;
    }

    public void setCommunityName(String communityName)
    {
        this.communityName = communityName;
    }

    public String getTargetType()
    {
        return targetType;
    }

    public void setTargetType(String targetType)
    {
        this.targetType = targetType;
    }

    public String getTargetName()
    {
        return targetName;
    }

    public void setTargetName(String targetName)
    {
        this.targetName = targetName;
    }

    public String getManufacturer()
    {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer)
    {
        this.manufacturer = manufacturer;
    }

    public String getInternalIpAddress()
    {
        return internalIpAddress;
    }

    public void setInternalIpAddress(String internalIpAddress)
    {
        this.internalIpAddress = internalIpAddress;
    }

    public String getAccessUnit()
    {
        return accessUnit;
    }

    public void setAccessUnit(String accessUnit)
    {
        this.accessUnit = accessUnit;
    }

    public String getPurpose()
    {
        return purpose;
    }

    public void setPurpose(String purpose)
    {
        this.purpose = purpose;
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

    public Boolean getCredentialConfigured()
    {
        return credentialConfigured;
    }

    public void setCredentialConfigured(Boolean credentialConfigured)
    {
        this.credentialConfigured = credentialConfigured;
    }

    public String getMappingAddress()
    {
        return mappingAddress;
    }

    public void setMappingAddress(String mappingAddress)
    {
        this.mappingAddress = mappingAddress;
    }

    public String getMappingPort()
    {
        return mappingPort;
    }

    public void setMappingPort(String mappingPort)
    {
        this.mappingPort = mappingPort;
    }

    public String getMappingDescription()
    {
        return mappingDescription;
    }

    public void setMappingDescription(String mappingDescription)
    {
        this.mappingDescription = mappingDescription;
    }

    public String getOwnerName()
    {
        return ownerName;
    }

    public void setOwnerName(String ownerName)
    {
        this.ownerName = ownerName;
    }

    public String getOwnerPhone()
    {
        return ownerPhone;
    }

    public void setOwnerPhone(String ownerPhone)
    {
        this.ownerPhone = ownerPhone;
    }

    public String getIssueBatch()
    {
        return issueBatch;
    }

    public void setIssueBatch(String issueBatch)
    {
        this.issueBatch = issueBatch;
    }

    public Date getAllocatedTime()
    {
        return allocatedTime;
    }

    public void setAllocatedTime(Date allocatedTime)
    {
        this.allocatedTime = allocatedTime;
    }

    public Date getIssuedTime()
    {
        return issuedTime;
    }

    public void setIssuedTime(Date issuedTime)
    {
        this.issuedTime = issuedTime;
    }

    public Date getReleasedTime()
    {
        return releasedTime;
    }

    public void setReleasedTime(Date releasedTime)
    {
        this.releasedTime = releasedTime;
    }

    public String getNetworkName()
    {
        return networkName;
    }

    public void setNetworkName(String networkName)
    {
        this.networkName = networkName;
    }

    public String getPoliceStationName()
    {
        return policeStationName;
    }

    public void setPoliceStationName(String policeStationName)
    {
        this.policeStationName = policeStationName;
    }

    public String getNetworkCidr()
    {
        return networkCidr;
    }

    public void setNetworkCidr(String networkCidr)
    {
        this.networkCidr = networkCidr;
    }

    public String getSegmentName()
    {
        return segmentName;
    }

    public void setSegmentName(String segmentName)
    {
        this.segmentName = segmentName;
    }

    public String getSegmentCidr()
    {
        return segmentCidr;
    }

    public void setSegmentCidr(String segmentCidr)
    {
        this.segmentCidr = segmentCidr;
    }

    public Integer getLastOctet()
    {
        return lastOctet;
    }

    public void setLastOctet(Integer lastOctet)
    {
        this.lastOctet = lastOctet;
    }

    public Boolean getBoundaryAddress()
    {
        return boundaryAddress;
    }

    public void setBoundaryAddress(Boolean boundaryAddress)
    {
        this.boundaryAddress = boundaryAddress;
    }

    public String getReservedReason()
    {
        return reservedReason;
    }

    public void setReservedReason(String reservedReason)
    {
        this.reservedReason = reservedReason;
    }

    public String getConnectivityStatus()
    {
        return connectivityStatus;
    }

    public void setConnectivityStatus(String connectivityStatus)
    {
        this.connectivityStatus = connectivityStatus;
    }

    public Long getScanResponseTimeMs()
    {
        return scanResponseTimeMs;
    }

    public void setScanResponseTimeMs(Long scanResponseTimeMs)
    {
        this.scanResponseTimeMs = scanResponseTimeMs;
    }

    public Date getLastScanTime()
    {
        return lastScanTime;
    }

    public void setLastScanTime(Date lastScanTime)
    {
        this.lastScanTime = lastScanTime;
    }

    public Date getLastOnlineTime()
    {
        return lastOnlineTime;
    }

    public void setLastOnlineTime(Date lastOnlineTime)
    {
        this.lastOnlineTime = lastOnlineTime;
    }
}
