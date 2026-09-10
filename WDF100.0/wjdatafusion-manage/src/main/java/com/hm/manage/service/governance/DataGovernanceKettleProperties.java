package com.hm.manage.service.governance;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Opt-in local original-engine gateway, independent of the NiFi configuration. */
@Component
@ConfigurationProperties(prefix = "data-governance-kettle")
public class DataGovernanceKettleProperties
{
    private boolean enabled;
    private String workerUrl = "http://127.0.0.1:19162";
    private String workerTokenFile = "";
    private String storageDir = System.getProperty("user.home") + "/.rynew/data-governance/kettle";
    private String catalogFile = "";
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public String getWorkerUrl() { return workerUrl; }
    public void setWorkerUrl(String value) { workerUrl = value; }
    public String getWorkerTokenFile() { return workerTokenFile; }
    public void setWorkerTokenFile(String value) { workerTokenFile = value; }
    public String getStorageDir() { return storageDir; }
    public void setStorageDir(String value) { storageDir = value; }
    public String getCatalogFile() { return catalogFile; }
    public void setCatalogFile(String value) { catalogFile = value; }
}
