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
