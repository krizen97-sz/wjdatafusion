package com.hm.manage.service.governance;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Independent engine and single-instance report storage; no business datasource dependency. */
@Component
@ConfigurationProperties(prefix = "data-governance")
public class DataGovernanceProperties
{
    private String storageDir = System.getProperty("user.home") + "/.rynew/data-governance";
    private int testTimeoutSeconds = 60;
    private java.util.List<String> connectionAllowedEndpoints = java.util.List.of("127.0.0.1:15432", "::1:15432");
    private final Nifi nifi = new Nifi();

    public String getStorageDir() { return storageDir; }
    public void setStorageDir(String value) { storageDir = value; }
    public int getTestTimeoutSeconds() { return Math.max(5, Math.min(testTimeoutSeconds, 300)); }
    public void setTestTimeoutSeconds(int value) { testTimeoutSeconds = value; }
    public Nifi getNifi() { return nifi; }
    public java.util.List<String> getConnectionAllowedEndpoints() { return connectionAllowedEndpoints; }
    public void setConnectionAllowedEndpoints(java.util.List<String> value) { connectionAllowedEndpoints = value == null ? java.util.List.of() : java.util.List.copyOf(value); }

    public static class Nifi
    {
        private String baseUrl = "";
        private String rootGroupId = "";
        private String credentialsFile = "";
        private String tokenFile = "";
        private String caCertFile = "";
        private String designerPath = "/nifi/";
        private int connectTimeoutMillis = 3000;
        private int readTimeoutMillis = 10000;
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String value) { baseUrl = value; }
        public String getRootGroupId() { return rootGroupId; }
        public void setRootGroupId(String value) { rootGroupId = value; }
        public String getCredentialsFile() { return credentialsFile; }
        public void setCredentialsFile(String value) { credentialsFile = value; }
        public String getTokenFile() { return tokenFile; }
        public void setTokenFile(String value) { tokenFile = value; }
        public String getCaCertFile() { return caCertFile; }
        public void setCaCertFile(String value) { caCertFile = value; }
        public String getDesignerPath() { return designerPath; }
        public void setDesignerPath(String value) { designerPath = value; }
        public int getConnectTimeoutMillis() { return Math.max(100, Math.min(connectTimeoutMillis, 10000)); }
        public void setConnectTimeoutMillis(int value) { connectTimeoutMillis = value; }
        public int getReadTimeoutMillis() { return Math.max(100, Math.min(readTimeoutMillis, 15000)); }
        public void setReadTimeoutMillis(int value) { readTimeoutMillis = value; }
    }
}
