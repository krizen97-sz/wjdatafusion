package com.hm.manage.domain.bo;

import java.io.Serializable;

public class IpamScenarioSettingBo implements Serializable
{
    private static final long serialVersionUID = 1L;

    private String scenarioType;

    public String getScenarioType()
    {
        return scenarioType;
    }

    public void setScenarioType(String scenarioType)
    {
        this.scenarioType = scenarioType;
    }
}
