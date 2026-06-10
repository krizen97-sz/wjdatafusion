package com.hm.manage.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hm.common.annotation.Excel;
import com.hm.common.core.domain.BaseEntity;

public class SupportSubplatformEndpoint extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long endpointId;
    private Long subPlatformId;

    @Excel(name = "页面名称")
    private String endpointName;

    @Excel(name = "访问URL")
    private String accessUrl;

    @Excel(name = "登录账号")
    private String loginUsername;

    private String loginPassword;

    @JsonIgnore
    private String loginPasswordCipher;

    public Long getEndpointId()
    {
        return endpointId;
    }

    public void setEndpointId(Long endpointId)
    {
        this.endpointId = endpointId;
    }

    public Long getSubPlatformId()
    {
        return subPlatformId;
    }

    public void setSubPlatformId(Long subPlatformId)
    {
        this.subPlatformId = subPlatformId;
    }

    public String getEndpointName()
    {
        return endpointName;
    }

    public void setEndpointName(String endpointName)
    {
        this.endpointName = endpointName;
    }

    public String getAccessUrl()
    {
        return accessUrl;
    }

    public void setAccessUrl(String accessUrl)
    {
        this.accessUrl = accessUrl;
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
}
