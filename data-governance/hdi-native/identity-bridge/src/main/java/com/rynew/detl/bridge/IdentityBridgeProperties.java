package com.rynew.detl.bridge;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rynew.detl.identity-bridge")
public class IdentityBridgeProperties {
    private boolean enabled;
    private String secretFile = "", mappingFile = "", replayDirectory = "";
    private int sessionSeconds = 900;
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getSecretFile() { return secretFile; }
    public void setSecretFile(String value) { secretFile = value; }
    public String getMappingFile() { return mappingFile; }
    public void setMappingFile(String value) { mappingFile = value; }
    public String getReplayDirectory() { return replayDirectory; }
    public void setReplayDirectory(String value) { replayDirectory = value; }
    public int getSessionSeconds() { return sessionSeconds; }
    public void setSessionSeconds(int value) { sessionSeconds = value; }
}
