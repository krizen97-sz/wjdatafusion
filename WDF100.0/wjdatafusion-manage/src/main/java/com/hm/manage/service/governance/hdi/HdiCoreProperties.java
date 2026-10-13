package com.hm.manage.service.governance.hdi;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Platform-owned management of the original HDI task core. No original management server. */
@Component
@ConfigurationProperties(prefix = "data-governance-hdi-core")
public class HdiCoreProperties {
    private boolean enabled;
    private String bundleDir = "";
    private String hostDir = "";
    private String stateDir = "";
    private String publicOrigin = "";
    private List<String> compilerCommand = new ArrayList<>();
    private List<String> workerCommand = new ArrayList<>();
    private int sessionIdleSeconds = 1800;
    private int maxVisualBytes = 4 * 1024 * 1024;
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public String getBundleDir() { return bundleDir; }
    public void setBundleDir(String value) { bundleDir = value; }
    public String getHostDir() { return hostDir; }
    public void setHostDir(String value) { hostDir = value; }
    public String getStateDir() { return stateDir; }
    public void setStateDir(String value) { stateDir = value; }
    public String getPublicOrigin() { return publicOrigin; }
    public void setPublicOrigin(String value) { publicOrigin = value; }
    public List<String> getCompilerCommand() { return List.copyOf(compilerCommand); }
    public void setCompilerCommand(List<String> value) { compilerCommand = new ArrayList<>(value); }
    public List<String> getWorkerCommand() { return List.copyOf(workerCommand); }
    public void setWorkerCommand(List<String> value) { workerCommand = new ArrayList<>(value); }
    public int getSessionIdleSeconds() { return sessionIdleSeconds; }
    public void setSessionIdleSeconds(int value) { sessionIdleSeconds = value; }
    public int getMaxVisualBytes() { return maxVisualBytes; }
    public void setMaxVisualBytes(int value) { maxVisualBytes = value; }
}
