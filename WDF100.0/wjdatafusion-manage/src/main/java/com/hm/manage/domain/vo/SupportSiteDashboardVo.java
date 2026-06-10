package com.hm.manage.domain.vo;

import java.util.List;

public class SupportSiteDashboardVo
{
    private SupportSiteDashboardSummaryVo summary;
    private List<SupportSiteDashboardSiteVo> mySites;
    private List<SupportSiteDashboardChangeVo> latestChanges;

    public SupportSiteDashboardSummaryVo getSummary()
    {
        return summary;
    }

    public void setSummary(SupportSiteDashboardSummaryVo summary)
    {
        this.summary = summary;
    }

    public List<SupportSiteDashboardSiteVo> getMySites()
    {
        return mySites;
    }

    public void setMySites(List<SupportSiteDashboardSiteVo> mySites)
    {
        this.mySites = mySites;
    }

    public List<SupportSiteDashboardChangeVo> getLatestChanges()
    {
        return latestChanges;
    }

    public void setLatestChanges(List<SupportSiteDashboardChangeVo> latestChanges)
    {
        this.latestChanges = latestChanges;
    }
}
