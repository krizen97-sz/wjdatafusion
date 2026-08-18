package com.hm.manage.domain.vo;

import java.util.ArrayList;
import java.util.List;
import com.hm.manage.domain.IpamSegment;

public class IpamSegmentOverviewVo
{
    private IpamSegment segment;
    private Long communityCount;
    private Long addressCount;
    private Long deviceCount;
    private List<IpamCommunityOverviewVo> communities = new ArrayList<>();

    public IpamSegment getSegment() { return segment; }
    public void setSegment(IpamSegment segment) { this.segment = segment; }
    public Long getCommunityCount() { return communityCount; }
    public void setCommunityCount(Long communityCount) { this.communityCount = communityCount; }
    public Long getAddressCount() { return addressCount; }
    public void setAddressCount(Long addressCount) { this.addressCount = addressCount; }
    public Long getDeviceCount() { return deviceCount; }
    public void setDeviceCount(Long deviceCount) { this.deviceCount = deviceCount; }
    public List<IpamCommunityOverviewVo> getCommunities() { return communities; }
    public void setCommunities(List<IpamCommunityOverviewVo> communities) { this.communities = communities; }
}
