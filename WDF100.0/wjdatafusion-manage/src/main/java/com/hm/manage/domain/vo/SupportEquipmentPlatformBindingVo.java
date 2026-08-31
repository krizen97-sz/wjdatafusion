package com.hm.manage.domain.vo;

public class SupportEquipmentPlatformBindingVo
{
    private Long sourceId;
    private Long platformId;
    private String platformName;
    private String platformLevel;
    private Long mainPlatformId;
    private String mainPlatformName;
    private String networkEnv;

    public Long getSourceId()
    {
        return sourceId;
    }

    public void setSourceId(Long sourceId)
    {
        this.sourceId = sourceId;
    }

    public Long getPlatformId()
    {
        return platformId;
    }

    public void setPlatformId(Long platformId)
    {
        this.platformId = platformId;
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

    public Long getMainPlatformId()
    {
        return mainPlatformId;
    }

    public void setMainPlatformId(Long mainPlatformId)
    {
        this.mainPlatformId = mainPlatformId;
    }

    public String getMainPlatformName()
    {
        return mainPlatformName;
    }

    public void setMainPlatformName(String mainPlatformName)
    {
        this.mainPlatformName = mainPlatformName;
    }

    public String getNetworkEnv()
    {
        return networkEnv;
    }

    public void setNetworkEnv(String networkEnv)
    {
        this.networkEnv = networkEnv;
    }
}
