package com.hm.manage.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

public class IpamScanResult
{
    private Long resultId;
    private Long scanId;
    private Long networkId;
    private Long segmentId;
    private String ipAddress;
    private Long ipValue;
    private String connectivityStatus;
    private Long responseTimeMs;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastScanTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastOnlineTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastOfflineTime;

    private String errorMessage;

    public Long getResultId()
    {
        return resultId;
    }

    public void setResultId(Long resultId)
    {
        this.resultId = resultId;
    }

    public Long getScanId()
    {
        return scanId;
    }

    public void setScanId(Long scanId)
    {
        this.scanId = scanId;
    }

    public Long getNetworkId()
    {
        return networkId;
    }

    public void setNetworkId(Long networkId)
    {
        this.networkId = networkId;
    }

    public Long getSegmentId()
    {
        return segmentId;
    }

    public void setSegmentId(Long segmentId)
    {
        this.segmentId = segmentId;
    }

    public String getIpAddress()
    {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress)
    {
        this.ipAddress = ipAddress;
    }

    public Long getIpValue()
    {
        return ipValue;
    }

    public void setIpValue(Long ipValue)
    {
        this.ipValue = ipValue;
    }

    public String getConnectivityStatus()
    {
        return connectivityStatus;
    }

    public void setConnectivityStatus(String connectivityStatus)
    {
        this.connectivityStatus = connectivityStatus;
    }

    public Long getResponseTimeMs()
    {
        return responseTimeMs;
    }

    public void setResponseTimeMs(Long responseTimeMs)
    {
        this.responseTimeMs = responseTimeMs;
    }

    public Date getLastScanTime()
    {
        return lastScanTime;
    }

    public void setLastScanTime(Date lastScanTime)
    {
        this.lastScanTime = lastScanTime;
    }

    public Date getLastOnlineTime()
    {
        return lastOnlineTime;
    }

    public void setLastOnlineTime(Date lastOnlineTime)
    {
        this.lastOnlineTime = lastOnlineTime;
    }

    public Date getLastOfflineTime()
    {
        return lastOfflineTime;
    }

    public void setLastOfflineTime(Date lastOfflineTime)
    {
        this.lastOfflineTime = lastOfflineTime;
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
