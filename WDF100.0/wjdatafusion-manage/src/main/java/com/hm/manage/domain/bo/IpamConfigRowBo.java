package com.hm.manage.domain.bo;

import java.io.Serializable;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;

public class IpamConfigRowBo implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long addressId;

    @Size(max = 64, message = "IP地址长度不能超过64个字符")
    private String ipAddress;

    @Size(max = 20, message = "地址状态长度不能超过20个字符")
    private String status;

    @Size(max = 120, message = "小区名称或项目名称不能超过120个字符")
    private String communityName;

    @Size(max = 40, message = "设备类别不能超过40个字符")
    private String targetType;

    @Size(max = 160, message = "设备名称不能超过160个字符")
    private String targetName;

    @Size(max = 60, message = "品牌不能超过60个字符")
    private String manufacturer;

    @Size(max = 128, message = "小区内网IP不能超过128个字符")
    private String internalIpAddress;

    @Size(max = 80, message = "接入单位不能超过80个字符")
    private String accessUnit;

    @Size(max = 255, message = "用途说明不能超过255个字符")
    private String purpose;

    @Size(max = 120, message = "登录账号不能超过120个字符")
    private String loginUsername;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Size(max = 500, message = "登录密码不能超过500个字符")
    private String loginPassword;

    @Size(max = 128, message = "映射地址不能超过128个字符")
    private String mappingAddress;

    @Size(max = 80, message = "映射端口不能超过80个字符")
    private String mappingPort;

    @Size(max = 500, message = "映射说明不能超过500个字符")
    private String mappingDescription;

    @Size(max = 80, message = "联系人不能超过80个字符")
    private String ownerName;

    @Size(max = 40, message = "联系电话不能超过40个字符")
    private String ownerPhone;

    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;

    public Long getAddressId()
    {
        return addressId;
    }

    public void setAddressId(Long addressId)
    {
        this.addressId = addressId;
    }

    public String getIpAddress()
    {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress)
    {
        this.ipAddress = ipAddress;
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

    public String getRemark()
    {
        return remark;
    }

    public void setRemark(String remark)
    {
        this.remark = remark;
    }
}
