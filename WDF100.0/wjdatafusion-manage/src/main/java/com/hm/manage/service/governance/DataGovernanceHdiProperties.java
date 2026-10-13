package com.hm.manage.service.governance;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Original HDI task sidecar. No connection to a remote HDI installation is allowed. */
@Component
@ConfigurationProperties(prefix = "data-governance-hdi")
public class DataGovernanceHdiProperties
{
    private boolean enabled;
    private String taskServiceUrl = "http://127.0.0.1:19312/detl";
    private String identityKeyFile = "";
    private String bundleDir = "";
    private String publicOrigin = "";
    private String hostDir = "";
    private int sessionIdleSeconds = 1800;
    private int maxRequestBytes = 8 * 1024 * 1024;
    private int maxResponseBytes = 32 * 1024 * 1024;
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 30000;
    public String getHostDir() { return hostDir; }
    public void setHostDir(String value) { hostDir = value; }
    public int getSessionIdleSeconds() { return sessionIdleSeconds; }
    public void setSessionIdleSeconds(int value) { sessionIdleSeconds = value; }
    public int getMaxRequestBytes() { return maxRequestBytes; }
    public void setMaxRequestBytes(int value) { maxRequestBytes = value; }
    public int getMaxResponseBytes() { return maxResponseBytes; }
    public void setMaxResponseBytes(int value) { maxResponseBytes = value; }
    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int value) { connectTimeoutMs = value; }
    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int value) { readTimeoutMs = value; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public String getTaskServiceUrl() { return taskServiceUrl; }
    public void setTaskServiceUrl(String value) { taskServiceUrl = value; }
    public String getIdentityKeyFile() { return identityKeyFile; }
    public void setIdentityKeyFile(String value) { identityKeyFile = value; }
    public String getBundleDir() { return bundleDir; }
    public void setBundleDir(String value) { bundleDir = value; }
    public String getPublicOrigin() { return publicOrigin; }
    public void setPublicOrigin(String value) { publicOrigin = value; }
}
