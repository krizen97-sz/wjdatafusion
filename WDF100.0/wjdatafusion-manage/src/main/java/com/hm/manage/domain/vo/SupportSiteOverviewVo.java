package com.hm.manage.domain.vo;

import java.util.List;
import java.util.Map;
import com.hm.manage.domain.SupportContact;
import com.hm.manage.domain.SupportOrg;
import com.hm.manage.domain.SupportPlatform;
import com.hm.manage.domain.SupportServer;
import com.hm.manage.domain.SupportSubplatformEndpoint;

public class SupportSiteOverviewVo
{
    private Long siteId;
    private String siteName;
    private Integer platformCount;
    private Integer serverCount;
    private Integer orgCount;
    private Integer contactCount;
    private List<SupportPlatform> platformTree;
    private List<SupportServer> servers;
    private List<SupportOrg> orgs;
    private List<SupportContact> contacts;
    private List<SupportSubplatformEndpoint> endpoints;
    private Map<Long, List<SupportServer>> platformServers;
    private Map<Long, List<SupportContact>> platformContacts;

    public Long getSiteId()
    {
        return siteId;
    }

    public void setSiteId(Long siteId)
    {
        this.siteId = siteId;
    }

    public String getSiteName()
    {
        return siteName;
    }

    public void setSiteName(String siteName)
    {
        this.siteName = siteName;
    }

    public Integer getPlatformCount()
    {
        return platformCount;
    }

    public void setPlatformCount(Integer platformCount)
    {
        this.platformCount = platformCount;
    }

    public Integer getServerCount()
    {
        return serverCount;
    }

    public void setServerCount(Integer serverCount)
    {
        this.serverCount = serverCount;
    }

    public Integer getOrgCount()
    {
        return orgCount;
    }

    public void setOrgCount(Integer orgCount)
    {
        this.orgCount = orgCount;
    }

    public Integer getContactCount()
    {
        return contactCount;
    }

    public void setContactCount(Integer contactCount)
    {
        this.contactCount = contactCount;
    }

    public List<SupportPlatform> getPlatformTree()
    {
        return platformTree;
    }

    public void setPlatformTree(List<SupportPlatform> platformTree)
    {
        this.platformTree = platformTree;
    }

    public List<SupportServer> getServers()
    {
        return servers;
    }

    public void setServers(List<SupportServer> servers)
    {
        this.servers = servers;
    }

    public List<SupportOrg> getOrgs()
    {
        return orgs;
    }

    public void setOrgs(List<SupportOrg> orgs)
    {
        this.orgs = orgs;
    }

    public List<SupportContact> getContacts()
    {
        return contacts;
    }

    public void setContacts(List<SupportContact> contacts)
    {
        this.contacts = contacts;
    }

    public List<SupportSubplatformEndpoint> getEndpoints()
    {
        return endpoints;
    }

    public void setEndpoints(List<SupportSubplatformEndpoint> endpoints)
    {
        this.endpoints = endpoints;
    }

    public Map<Long, List<SupportServer>> getPlatformServers()
    {
        return platformServers;
    }

    public void setPlatformServers(Map<Long, List<SupportServer>> platformServers)
    {
        this.platformServers = platformServers;
    }

    public Map<Long, List<SupportContact>> getPlatformContacts()
    {
        return platformContacts;
    }

    public void setPlatformContacts(Map<Long, List<SupportContact>> platformContacts)
    {
        this.platformContacts = platformContacts;
    }
}
