package com.hm.manage.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "document-management")
public class DocumentManagementProperties
{
    private String storageRoot;
    private long maxFileSize = 100L * 1024L * 1024L;
    private long maxExpandedFileSize = 250L * 1024L * 1024L;
    private int maxArchiveEntries = 10000;
    private int maxPdfPages = 2000;
    private int maxBatchDownloadFiles = 50;
    private long maxBatchDownloadSize = 500L * 1024L * 1024L;
    private final OnlyOffice onlyOffice = new OnlyOffice();

    public String getStorageRoot()
    {
        return storageRoot;
    }

    public void setStorageRoot(String storageRoot)
    {
        this.storageRoot = storageRoot;
    }

    public long getMaxFileSize()
    {
        return Math.max(1024L * 1024L, maxFileSize);
    }

    public void setMaxFileSize(long maxFileSize)
    {
        this.maxFileSize = maxFileSize;
    }

    public long getMaxExpandedFileSize()
    {
        return Math.max(getMaxFileSize(), maxExpandedFileSize);
    }

    public void setMaxExpandedFileSize(long maxExpandedFileSize)
    {
        this.maxExpandedFileSize = maxExpandedFileSize;
    }

    public int getMaxArchiveEntries()
    {
        return Math.max(100, Math.min(maxArchiveEntries, 20000));
    }

    public void setMaxArchiveEntries(int maxArchiveEntries)
    {
        this.maxArchiveEntries = maxArchiveEntries;
    }

    public int getMaxPdfPages()
    {
        return Math.max(1, Math.min(maxPdfPages, 10000));
    }

    public void setMaxPdfPages(int maxPdfPages)
    {
        this.maxPdfPages = maxPdfPages;
    }

    public int getMaxBatchDownloadFiles()
    {
        return Math.max(1, Math.min(maxBatchDownloadFiles, 200));
    }

    public void setMaxBatchDownloadFiles(int maxBatchDownloadFiles)
    {
        this.maxBatchDownloadFiles = maxBatchDownloadFiles;
    }

    public long getMaxBatchDownloadSize()
    {
        return Math.max(getMaxFileSize(), maxBatchDownloadSize);
    }

    public void setMaxBatchDownloadSize(long maxBatchDownloadSize)
    {
        this.maxBatchDownloadSize = maxBatchDownloadSize;
    }

    public OnlyOffice getOnlyOffice()
    {
        return onlyOffice;
    }

    public static class OnlyOffice
    {
        private boolean enabled;
        private String serverUrl;
        private String platformBaseUrl;
        private String jwtSecret;
        private int fileTokenMinutes = 15;
        private int callbackTokenHours = 72;
        private int connectTimeoutSeconds = 10;
        private int readTimeoutSeconds = 60;
        private String language = "zh-CN";
        private String region = "zh-CN";
        private List<String> trustedDownloadHosts = new ArrayList<>();

        public boolean isEnabled()
        {
            return enabled;
        }

        public void setEnabled(boolean enabled)
        {
            this.enabled = enabled;
        }

        public String getServerUrl()
        {
            return serverUrl;
        }

        public void setServerUrl(String serverUrl)
        {
            this.serverUrl = serverUrl;
        }

        public String getPlatformBaseUrl()
        {
            return platformBaseUrl;
        }

        public void setPlatformBaseUrl(String platformBaseUrl)
        {
            this.platformBaseUrl = platformBaseUrl;
        }

        public String getJwtSecret()
        {
            return jwtSecret;
        }

        public void setJwtSecret(String jwtSecret)
        {
            this.jwtSecret = jwtSecret;
        }

        public int getFileTokenMinutes()
        {
            return Math.max(1, Math.min(fileTokenMinutes, 60));
        }

        public void setFileTokenMinutes(int fileTokenMinutes)
        {
            this.fileTokenMinutes = fileTokenMinutes;
        }

        public int getCallbackTokenHours()
        {
            return Math.max(1, Math.min(callbackTokenHours, 168));
        }

        public void setCallbackTokenHours(int callbackTokenHours)
        {
            this.callbackTokenHours = callbackTokenHours;
        }

        public int getConnectTimeoutSeconds()
        {
            return Math.max(1, Math.min(connectTimeoutSeconds, 60));
        }

        public void setConnectTimeoutSeconds(int connectTimeoutSeconds)
        {
            this.connectTimeoutSeconds = connectTimeoutSeconds;
        }

        public int getReadTimeoutSeconds()
        {
            return Math.max(5, Math.min(readTimeoutSeconds, 300));
        }

        public void setReadTimeoutSeconds(int readTimeoutSeconds)
        {
            this.readTimeoutSeconds = readTimeoutSeconds;
        }

        public String getLanguage()
        {
            return language;
        }

        public void setLanguage(String language)
        {
            this.language = language;
        }

        public String getRegion()
        {
            return region;
        }

        public void setRegion(String region)
        {
            this.region = region;
        }

        public List<String> getTrustedDownloadHosts()
        {
            return trustedDownloadHosts;
        }

        public void setTrustedDownloadHosts(List<String> trustedDownloadHosts)
        {
            this.trustedDownloadHosts = trustedDownloadHosts == null ? new ArrayList<>() : trustedDownloadHosts;
        }
    }
}
