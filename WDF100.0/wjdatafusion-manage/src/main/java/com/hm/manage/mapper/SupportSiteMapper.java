package com.hm.manage.mapper;

import java.util.List;
import com.hm.manage.domain.SupportSite;

public interface SupportSiteMapper
{
    SupportSite selectSupportSiteBySiteId(Long siteId);

    List<SupportSite> selectSupportSiteList(SupportSite site);

    int insertSupportSite(SupportSite site);

    int updateSupportSite(SupportSite site);

    Integer selectMaxSiteSequenceByRegion(SupportSite site);

    int deleteSupportSiteBySiteId(Long siteId);

    int deleteSupportSiteBySiteIds(Long[] siteIds);
}
