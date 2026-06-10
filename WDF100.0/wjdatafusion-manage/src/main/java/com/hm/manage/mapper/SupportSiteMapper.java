package com.hm.manage.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.hm.manage.domain.SupportSite;
import com.hm.manage.domain.vo.SupportSiteDashboardSiteVo;

public interface SupportSiteMapper
{
    SupportSite selectSupportSiteBySiteId(Long siteId);

    List<SupportSite> selectSupportSiteList(SupportSite site);

    List<SupportSiteDashboardSiteVo> selectDashboardSites(@Param("operators") List<String> operators);

    int insertSupportSite(SupportSite site);

    int updateSupportSite(SupportSite site);

    Integer selectMaxSiteSequenceByRegion(SupportSite site);

    int deleteSupportSiteBySiteId(Long siteId);

    int deleteSupportSiteBySiteIds(Long[] siteIds);
}
