package com.hm.manage.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hm.common.core.domain.BaseEntity;

public class SupportServerCredential extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long credentialId;
    private Long serverId;
    private String credentialName;
    private String username;
    private String password;

    @JsonIgnore
    private String passwordCipher;

    private String purpose;
    private String isDefault;
    private String status;

    public Long getCredentialId()
    {
        return credentialId;
    }

    public void setCredentialId(Long credentialId)
    {
        this.credentialId = credentialId;
    }

    public Long getServerId()
    {
        return serverId;
    }

    public void setServerId(Long serverId)
    {
        this.serverId = serverId;
    }

    public String getCredentialName()
    {
        return credentialName;
    }

    public void setCredentialName(String credentialName)
    {
        this.credentialName = credentialName;
    }

    public String getUsername()
    {
        return username;
    }

    public void setUsername(String username)
    {
        this.username = username;
    }

    public String getPassword()
    {
        return password;
    }

    public void setPassword(String password)
    {
        this.password = password;
    }

    public String getPasswordCipher()
    {
        return passwordCipher;
    }

    public void setPasswordCipher(String passwordCipher)
    {
        this.passwordCipher = passwordCipher;
    }

    public String getPurpose()
    {
        return purpose;
    }

    public void setPurpose(String purpose)
    {
        this.purpose = purpose;
    }

    public String getIsDefault()
    {
        return isDefault;
    }

    public void setIsDefault(String isDefault)
    {
        this.isDefault = isDefault;
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
