package com.hm.manage.service.governance;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Opt-in, separate namespace; no network clients or storage are opened by the default deployment. */
@Component
@ConfigurationProperties(prefix = "data-governance-kafka")
public class DataGovernanceKafkaProperties
{
    private boolean enabled;
    private String storageDir = System.getProperty("user.home") + "/.rynew/data-governance/kafka";
    private List<String> allowedBrokers = new ArrayList<>(List.of("127.0.0.1:19092"));
    private int timeoutMillis = 5000;
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public String getStorageDir() { return storageDir; }
    public void setStorageDir(String value) { storageDir = value; }
    public List<String> getAllowedBrokers() { return allowedBrokers; }
    public void setAllowedBrokers(List<String> value) { allowedBrokers = value; }
    public int getTimeoutMillis() { return Math.max(500, Math.min(timeoutMillis, 10000)); }
    public void setTimeoutMillis(int value) { timeoutMillis = value; }
}
