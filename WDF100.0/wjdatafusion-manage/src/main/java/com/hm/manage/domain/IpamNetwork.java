package com.hm.manage.domain;

import com.hm.common.annotation.Excel;
import com.hm.common.core.domain.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class IpamNetwork extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long networkId;

    private String keyword;

    @Excel(name = "网段名称")
    private String networkName;

    @Excel(name = "所属派出所")
    private String policeStationName;

    private String cidrBlock;

    @Excel(name = "网络地址")
    private String startIp;

    @Excel(name = "广播地址")
    private String endIp;

    @JsonIgnore
    private Long startValue;

    @JsonIgnore
    private Long endValue;

    private Integer prefixLength;

    private Long segmentId;

    @Excel(name = "网关IP")
    private String gatewayIp;

    @Excel(name = "子网掩码")
    private String subnetMask;

    @Excel(name = "使用场景", readConverterExp = "SOCIAL=社会面场景,INTERNAL=公安内网场景")
    private String scenarioType;

    @Excel(name = "状态", readConverterExp = "0=启用,1=停用")
    private String status;

    private Long segmentCount;
    private Long totalCount;
    private Long reservedCount;
    private Long allocatedCount;
    private Long issuedCount;
    private Long disabledCount;
    private Long freeCount;

    public Long getNetworkId()
    {
        return networkId;
    }

    public void setNetworkId(Long networkId)
    {
        this.networkId = networkId;
    }

    public String getKeyword()
    {
        return keyword;
    }

    public void setKeyword(String keyword)
    {
        this.keyword = keyword;
    }

    public String getNetworkName()
    {
        return networkName;
    }

    public void setNetworkName(String networkName)
    {
        this.networkName = networkName;
    }

    public String getPoliceStationName()
    {
        return policeStationName;
    }

    public void setPoliceStationName(String policeStationName)
    {
        this.policeStationName = policeStationName;
    }

    public String getCidrBlock()
    {
        return cidrBlock;
    }

    public void setCidrBlock(String cidrBlock)
    {
        this.cidrBlock = cidrBlock;
    }

    public String getStartIp()
    {
        return startIp;
    }

    public void setStartIp(String startIp)
    {
        this.startIp = startIp;
    }

    public String getEndIp()
    {
        return endIp;
    }

    public void setEndIp(String endIp)
    {
        this.endIp = endIp;
    }

    public Long getStartValue()
    {
        return startValue;
    }

    public void setStartValue(Long startValue)
    {
        this.startValue = startValue;
    }

    public Long getEndValue()
    {
        return endValue;
    }

    public void setEndValue(Long endValue)
    {
        this.endValue = endValue;
    }

    public Integer getPrefixLength()
    {
        return prefixLength;
    }

    public void setPrefixLength(Integer prefixLength)
    {
        this.prefixLength = prefixLength;
    }

    public Long getSegmentId()
    {
        return segmentId;
    }

    public void setSegmentId(Long segmentId)
    {
        this.segmentId = segmentId;
    }

    public String getGatewayIp()
    {
        return gatewayIp;
    }

    public void setGatewayIp(String gatewayIp)
    {
        this.gatewayIp = gatewayIp;
    }

    public String getSubnetMask()
    {
        return subnetMask;
    }

    public void setSubnetMask(String subnetMask)
    {
        this.subnetMask = subnetMask;
    }

    public String getScenarioType()
    {
        return scenarioType;
    }

    public void setScenarioType(String scenarioType)
    {
        this.scenarioType = scenarioType;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public Long getSegmentCount()
    {
        return segmentCount;
    }

    public void setSegmentCount(Long segmentCount)
    {
        this.segmentCount = segmentCount;
    }

    public Long getTotalCount()
    {
        return totalCount;
    }

    public void setTotalCount(Long totalCount)
    {
        this.totalCount = totalCount;
    }

    public Long getReservedCount()
    {
        return reservedCount;
    }

    public void setReservedCount(Long reservedCount)
    {
        this.reservedCount = reservedCount;
    }

    public Long getAllocatedCount()
    {
        return allocatedCount;
    }

    public void setAllocatedCount(Long allocatedCount)
    {
        this.allocatedCount = allocatedCount;
    }

    public Long getIssuedCount()
    {
        return issuedCount;
    }

    public void setIssuedCount(Long issuedCount)
    {
        this.issuedCount = issuedCount;
    }

    public Long getDisabledCount()
    {
        return disabledCount;
    }

    public void setDisabledCount(Long disabledCount)
    {
        this.disabledCount = disabledCount;
    }

    public Long getFreeCount()
    {
        return freeCount;
    }

    public void setFreeCount(Long freeCount)
    {
        this.freeCount = freeCount;
    }
}
