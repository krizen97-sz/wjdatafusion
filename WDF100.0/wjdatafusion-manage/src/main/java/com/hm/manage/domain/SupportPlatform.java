package com.hm.manage.domain;

import java.util.List;
import com.hm.common.annotation.Excel;
import com.hm.common.core.domain.BaseEntity;

public class SupportPlatform extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long platformId;
    private Long siteId;

    @Excel(name = "平台名称")
    private String platformName;

    @Excel(name = "平台级别")
    private String platformLevel;

    @Excel(name = "网络环境")
    private String networkEnv;

    private Long parentPlatformId;

    @Excel(name = "状态", readConverterExp = "0=正常,1=停用")
    private String status;

    private List<SupportPlatform> children;

    public Long getPlatformId()
    {
        return platformId;
    }

    public void setPlatformId(Long platformId)
    {
        this.platformId = platformId;
    }

    public Long getSiteId()
    {
        return siteId;
    }

    public void setSiteId(Long siteId)
    {
        this.siteId = siteId;
    }

    public String getPlatformName()
    {
        return platformName;
    }

    public void setPlatformName(String platformName)
    {
        this.platformName = platformName;
    }

    public String getPlatformLevel()
    {
        return platformLevel;
    }

    public void setPlatformLevel(String platformLevel)
    {
        this.platformLevel = platformLevel;
    }

    public String getNetworkEnv()
    {
        return networkEnv;
    }

    public void setNetworkEnv(String networkEnv)
    {
        this.networkEnv = networkEnv;
    }

    public Long getParentPlatformId()
    {
        return parentPlatformId;
    }

    public void setParentPlatformId(Long parentPlatformId)
    {
        this.parentPlatformId = parentPlatformId;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public List<SupportPlatform> getChildren()
    {
        return children;
    }

    public void setChildren(List<SupportPlatform> children)
    {
        this.children = children;
    }
}
