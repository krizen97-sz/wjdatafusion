package com.hm.manage.service;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;
import com.hm.manage.domain.SupportSite;
import com.hm.manage.domain.vo.SupportSiteDashboardVo;
import com.hm.manage.domain.vo.SupportSiteOverviewVo;

public interface ISupportSiteService
{
    SupportSite selectSupportSiteBySiteId(Long siteId);

    List<SupportSite> selectSupportSiteList(SupportSite site);

    int insertSupportSite(SupportSite site);

    int updateSupportSite(SupportSite site);

    int deleteSupportSiteBySiteIds(Long[] siteIds);

    SupportSiteOverviewVo getSiteOverview(Long siteId);

    SupportSiteOverviewVo getSiteWorkbench(Long siteId);

    SupportSiteDashboardVo getSiteDashboard();

    String previewSiteCode(SupportSite site);

    void exportSitePackage(HttpServletResponse response, Long[] siteIds) throws Exception;

    String importSitePackage(MultipartFile file) throws Exception;
}
