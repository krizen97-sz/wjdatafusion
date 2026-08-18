package com.hm.manage.domain.vo;

import java.util.ArrayList;
import java.util.List;
import com.hm.manage.domain.IpamNetwork;

public class IpamDashboardVo
{
    private IpamDashboardSummaryVo summary = new IpamDashboardSummaryVo();
    private List<IpamNetwork> networks = new ArrayList<>();
    private List<IpamCommunityOverviewVo> communities = new ArrayList<>();
    private List<IpamDashboardDimensionVo> targetTypes = new ArrayList<>();
    private List<IpamDashboardDimensionVo> manufacturers = new ArrayList<>();

    public IpamDashboardSummaryVo getSummary() { return summary; }
    public void setSummary(IpamDashboardSummaryVo summary) { this.summary = summary; }
    public List<IpamNetwork> getNetworks() { return networks; }
    public void setNetworks(List<IpamNetwork> networks) { this.networks = networks; }
    public List<IpamCommunityOverviewVo> getCommunities() { return communities; }
    public void setCommunities(List<IpamCommunityOverviewVo> communities) { this.communities = communities; }
    public List<IpamDashboardDimensionVo> getTargetTypes() { return targetTypes; }
    public void setTargetTypes(List<IpamDashboardDimensionVo> targetTypes) { this.targetTypes = targetTypes; }
    public List<IpamDashboardDimensionVo> getManufacturers() { return manufacturers; }
    public void setManufacturers(List<IpamDashboardDimensionVo> manufacturers) { this.manufacturers = manufacturers; }
}
