package com.hm.manage.domain.vo;

import java.io.Serializable;

public class AutoInspectionCredentialVo implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long serverId;
    private String username;
    private String password;
    private Boolean configured;

    public Long getServerId() { return serverId; }
    public void setServerId(Long serverId) { this.serverId = serverId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public Boolean getConfigured() { return configured; }
    public void setConfigured(Boolean configured) { this.configured = configured; }
}
