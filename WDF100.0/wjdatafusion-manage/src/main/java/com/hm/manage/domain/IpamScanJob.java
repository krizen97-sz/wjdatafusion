package com.hm.manage.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.hm.common.core.domain.BaseEntity;

public class IpamScanJob extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long scanId;
    private String scanScope;
    private String triggerType;
    private Long networkId;
    private String networkName;
    private String scanStatus;
    private Long totalCount;
    private Long completedCount;
    private Long onlineCount;
    private Long offlineCount;
    private Long errorCount;
    private Integer timeoutMs;
    private Integer intervalMs;
    private Integer concurrencyCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startedTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date finishedTime;

    private String errorMessage;

    public Long getScanId()
    {
        return scanId;
    }

    public void setScanId(Long scanId)
    {
        this.scanId = scanId;
    }

    public String getScanScope()
    {
        return scanScope;
    }

    public void setScanScope(String scanScope)
    {
        this.scanScope = scanScope;
    }

    public String getTriggerType()
    {
        return triggerType;
    }

    public void setTriggerType(String triggerType)
    {
        this.triggerType = triggerType;
    }

    public Long getNetworkId()
    {
        return networkId;
    }

    public void setNetworkId(Long networkId)
    {
        this.networkId = networkId;
    }

    public String getNetworkName()
    {
        return networkName;
    }

    public void setNetworkName(String networkName)
    {
        this.networkName = networkName;
    }

    public String getScanStatus()
    {
        return scanStatus;
    }

    public void setScanStatus(String scanStatus)
    {
        this.scanStatus = scanStatus;
    }

    public Long getTotalCount()
    {
        return totalCount;
    }

    public void setTotalCount(Long totalCount)
    {
        this.totalCount = totalCount;
    }

    public Long getCompletedCount()
    {
        return completedCount;
    }

    public void setCompletedCount(Long completedCount)
    {
        this.completedCount = completedCount;
    }

    public Long getOnlineCount()
    {
        return onlineCount;
    }

    public void setOnlineCount(Long onlineCount)
    {
        this.onlineCount = onlineCount;
    }

    public Long getOfflineCount()
    {
        return offlineCount;
    }

    public void setOfflineCount(Long offlineCount)
    {
        this.offlineCount = offlineCount;
    }

    public Long getErrorCount()
    {
        return errorCount;
    }

    public void setErrorCount(Long errorCount)
    {
        this.errorCount = errorCount;
    }

    public Integer getTimeoutMs()
    {
        return timeoutMs;
    }

    public void setTimeoutMs(Integer timeoutMs)
    {
        this.timeoutMs = timeoutMs;
    }

    public Integer getIntervalMs()
    {
        return intervalMs;
    }

    public void setIntervalMs(Integer intervalMs)
    {
        this.intervalMs = intervalMs;
    }

    public Integer getConcurrencyCount()
    {
        return concurrencyCount;
    }

    public void setConcurrencyCount(Integer concurrencyCount)
    {
        this.concurrencyCount = concurrencyCount;
    }

    public Date getStartedTime()
    {
        return startedTime;
    }

    public void setStartedTime(Date startedTime)
    {
        this.startedTime = startedTime;
    }

    public Date getFinishedTime()
    {
        return finishedTime;
    }

    public void setFinishedTime(Date finishedTime)
    {
        this.finishedTime = finishedTime;
    }

    public String getErrorMessage()
    {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage)
    {
        this.errorMessage = errorMessage;
    }
}
