package com.hm.manage.domain;

import com.hm.common.annotation.Excel;
import com.hm.common.core.domain.BaseEntity;

public class IpamSegment extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long segmentId;
    private Long networkId;

    @Excel(name = "子网段名称")
    private String segmentName;

    @Excel(name = "CIDR")
    private String cidrBlock;

    @Excel(name = "起始IP")
    private String startIp;

    @Excel(name = "结束IP")
    private String endIp;

    @Excel(name = "网关IP")
    private String gatewayIp;

    @Excel(name = "掩码")
    private Integer prefixLength;

    @Excel(name = "地址总数")
    private Long totalCount;

    @Excel(name = "状态", readConverterExp = "0=启用,1=停用")
    private String status;

    private String networkName;
    private String networkCidr;
    private Long reservedCount;
    private Long allocatedCount;
    private Long issuedCount;
    private Long disabledCount;
    private Long freeCount;
    private Long communityCount;
    private Long deviceCount;

    public Long getSegmentId()
    {
        return segmentId;
    }

    public void setSegmentId(Long segmentId)
    {
        this.segmentId = segmentId;
    }

    public Long getNetworkId()
    {
        return networkId;
    }

    public void setNetworkId(Long networkId)
    {
        this.networkId = networkId;
    }

    public String getSegmentName()
    {
        return segmentName;
    }

    public void setSegmentName(String segmentName)
    {
        this.segmentName = segmentName;
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

    public String getGatewayIp()
    {
        return gatewayIp;
    }

    public void setGatewayIp(String gatewayIp)
    {
        this.gatewayIp = gatewayIp;
    }

    public Integer getPrefixLength()
    {
        return prefixLength;
    }

    public void setPrefixLength(Integer prefixLength)
    {
        this.prefixLength = prefixLength;
    }

    public Long getTotalCount()
    {
        return totalCount;
    }

    public void setTotalCount(Long totalCount)
    {
        this.totalCount = totalCount;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getNetworkName()
    {
        return networkName;
    }

    public void setNetworkName(String networkName)
    {
        this.networkName = networkName;
    }

    public String getNetworkCidr()
    {
        return networkCidr;
    }

    public void setNetworkCidr(String networkCidr)
    {
        this.networkCidr = networkCidr;
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

    public Long getCommunityCount()
    {
        return communityCount;
    }

    public void setCommunityCount(Long communityCount)
    {
        this.communityCount = communityCount;
    }

    public Long getDeviceCount()
    {
        return deviceCount;
    }

    public void setDeviceCount(Long deviceCount)
    {
        this.deviceCount = deviceCount;
    }
}
