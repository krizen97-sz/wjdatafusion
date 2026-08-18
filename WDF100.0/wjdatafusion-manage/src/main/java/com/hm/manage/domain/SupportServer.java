package com.hm.manage.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hm.common.annotation.Excel;
import com.hm.common.core.domain.BaseEntity;

public class SupportServer extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long serverId;
    private Long siteId;

    @Excel(name = "服务器名称")
    private String serverName;

    @Excel(name = "服务器地址")
    private String serverAddress;

    @Excel(name = "SSH端口")
    private Integer sshPort;

    @Excel(name = "操作系统")
    private String osType;

    @Excel(name = "所属机房")
    private String equipmentRoom;

    @Excel(name = "机柜编号")
    private String cabinetNo;

    @Excel(name = "起始U位")
    private Integer rackUStart;

    @Excel(name = "结束U位")
    private Integer rackUEnd;

    @Excel(name = "系统账号")
    private String osUsername;

    private String osPassword;

    @JsonIgnore
    private String osPasswordCipher;

    private String hikPassword;
    private String rootPassword;
    private String otherUsername;
    private String otherPassword;
    private Boolean hikCredentialConfigured;
    private Boolean rootCredentialConfigured;
    private Boolean otherCredentialConfigured;

    @Excel(name = "状态", readConverterExp = "0=正常,1=停用")
    private String status;

    public Long getServerId()
    {
        return serverId;
    }

    public void setServerId(Long serverId)
    {
        this.serverId = serverId;
    }

    public Long getSiteId()
    {
        return siteId;
    }

    public void setSiteId(Long siteId)
    {
        this.siteId = siteId;
    }

    public String getServerName()
    {
        return serverName;
    }

    public void setServerName(String serverName)
    {
        this.serverName = serverName;
    }

    public String getServerAddress()
    {
        return serverAddress;
    }

    public void setServerAddress(String serverAddress)
    {
        this.serverAddress = serverAddress;
    }

    public Integer getSshPort()
    {
        return sshPort;
    }

    public void setSshPort(Integer sshPort)
    {
        this.sshPort = sshPort;
    }

    public String getOsType()
    {
        return osType;
    }

    public void setOsType(String osType)
    {
        this.osType = osType;
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

    public String getOsUsername()
    {
        return osUsername;
    }

    public void setOsUsername(String osUsername)
    {
        this.osUsername = osUsername;
    }

    public String getOsPassword()
    {
        return osPassword;
    }

    public void setOsPassword(String osPassword)
    {
        this.osPassword = osPassword;
    }

    public String getOsPasswordCipher()
    {
        return osPasswordCipher;
    }

    public void setOsPasswordCipher(String osPasswordCipher)
    {
        this.osPasswordCipher = osPasswordCipher;
    }

    public String getHikPassword()
    {
        return hikPassword;
    }

    public void setHikPassword(String hikPassword)
    {
        this.hikPassword = hikPassword;
    }

    public String getRootPassword()
    {
        return rootPassword;
    }

    public void setRootPassword(String rootPassword)
    {
        this.rootPassword = rootPassword;
    }

    public String getOtherUsername()
    {
        return otherUsername;
    }

    public void setOtherUsername(String otherUsername)
    {
        this.otherUsername = otherUsername;
    }

    public String getOtherPassword()
    {
        return otherPassword;
    }

    public void setOtherPassword(String otherPassword)
    {
        this.otherPassword = otherPassword;
    }

    public Boolean getHikCredentialConfigured()
    {
        return hikCredentialConfigured;
    }

    public void setHikCredentialConfigured(Boolean hikCredentialConfigured)
    {
        this.hikCredentialConfigured = hikCredentialConfigured;
    }

    public Boolean getRootCredentialConfigured()
    {
        return rootCredentialConfigured;
    }

    public void setRootCredentialConfigured(Boolean rootCredentialConfigured)
    {
        this.rootCredentialConfigured = rootCredentialConfigured;
    }

    public Boolean getOtherCredentialConfigured()
    {
        return otherCredentialConfigured;
    }

    public void setOtherCredentialConfigured(Boolean otherCredentialConfigured)
    {
        this.otherCredentialConfigured = otherCredentialConfigured;
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
