package com.hm.manage.domain.vo;

import java.util.ArrayList;
import java.util.List;
import com.hm.manage.domain.IpamNetwork;

public class IpamWorkbookCatalogVo
{
    private String scenarioType;
    private List<IpamNetwork> networks = new ArrayList<>();
    private List<IpamCommunityOverviewVo> communities = new ArrayList<>();

    public String getScenarioType()
    {
        return scenarioType;
    }

    public void setScenarioType(String scenarioType)
    {
        this.scenarioType = scenarioType;
    }

    public List<IpamNetwork> getNetworks()
    {
        return networks;
    }

    public void setNetworks(List<IpamNetwork> networks)
    {
        this.networks = networks;
    }

    public List<IpamCommunityOverviewVo> getCommunities()
    {
        return communities;
    }

    public void setCommunities(List<IpamCommunityOverviewVo> communities)
    {
        this.communities = communities;
    }
}
