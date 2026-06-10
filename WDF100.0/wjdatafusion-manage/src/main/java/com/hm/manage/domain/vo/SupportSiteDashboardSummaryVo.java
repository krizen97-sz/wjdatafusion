package com.hm.manage.domain.vo;

public class SupportSiteDashboardSummaryVo
{
    private Integer mySiteCount;
    private Integer createdSiteCount;
    private Integer updatedSiteCount;
    private Integer todayChangeCount;

    public Integer getMySiteCount()
    {
        return mySiteCount;
    }

    public void setMySiteCount(Integer mySiteCount)
    {
        this.mySiteCount = mySiteCount;
    }

    public Integer getCreatedSiteCount()
    {
        return createdSiteCount;
    }

    public void setCreatedSiteCount(Integer createdSiteCount)
    {
        this.createdSiteCount = createdSiteCount;
    }

    public Integer getUpdatedSiteCount()
    {
        return updatedSiteCount;
    }

    public void setUpdatedSiteCount(Integer updatedSiteCount)
    {
        this.updatedSiteCount = updatedSiteCount;
    }

    public Integer getTodayChangeCount()
    {
        return todayChangeCount;
    }

    public void setTodayChangeCount(Integer todayChangeCount)
    {
        this.todayChangeCount = todayChangeCount;
    }
}
