package com.hm.manage.controller;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.hm.common.annotation.Log;
import com.hm.common.core.controller.BaseController;
import com.hm.common.core.domain.AjaxResult;
import com.hm.common.core.page.TableDataInfo;
import com.hm.common.enums.BusinessType;
import com.hm.manage.domain.SupportSite;
import com.hm.manage.service.ISupportChangeLogService;
import com.hm.manage.service.ISupportSiteService;

@RestController
@RequestMapping("/support/site")
public class SupportSiteController extends BaseController
{
    @Autowired
    private ISupportSiteService supportSiteService;

    @Autowired
    private ISupportChangeLogService changeLogService;

    @PreAuthorize("@ss.hasPermi('support:site:list')")
    @GetMapping("/list")
    public TableDataInfo list(SupportSite site)
    {
        startPage();
        List<SupportSite> list = supportSiteService.selectSupportSiteList(site);
        changeLogService.recordQuery(null, "SITE", null, null, "查询现场列表");
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('support:site:export')")
    @Log(title = "现场管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, @RequestParam(value = "siteIds", required = false) Long[] siteIds) throws Exception
    {
        supportSiteService.exportSitePackage(response, siteIds);
    }

    @PreAuthorize("@ss.hasPermi('support:site:import')")
    @Log(title = "现场管理", businessType = BusinessType.IMPORT)
    @PostMapping("/importData")
    public AjaxResult importData(MultipartFile file) throws Exception
    {
        return success(supportSiteService.importSitePackage(file));
    }

    @PreAuthorize("@ss.hasPermi('support:site:query')")
    @GetMapping(value = "/{siteId}")
    public AjaxResult getInfo(@PathVariable("siteId") Long siteId)
    {
        SupportSite site = supportSiteService.selectSupportSiteBySiteId(siteId);
        changeLogService.recordQuery(siteId, "SITE", siteId, site == null ? null : site.getSiteName(), "查询现场详情");
        return success(site);
    }

    @PreAuthorize("@ss.hasPermi('support:site:query')")
    @GetMapping(value = "/overview/{siteId}")
    public AjaxResult overview(@PathVariable("siteId") Long siteId)
    {
        changeLogService.recordQuery(siteId, "SITE", siteId, null, "查询现场概览");
        return success(supportSiteService.getSiteOverview(siteId));
    }

    @PreAuthorize("@ss.hasPermi('support:site:query')")
    @GetMapping(value = "/workbench/{siteId}")
    public AjaxResult workbench(@PathVariable("siteId") Long siteId)
    {
        changeLogService.recordQuery(siteId, "SITE", siteId, null, "查询现场融合工作台");
        return success(supportSiteService.getSiteWorkbench(siteId));
    }

    @PreAuthorize("@ss.hasPermi('support:site:query')")
    @PostMapping("/code-preview")
    public AjaxResult previewCode(@RequestBody SupportSite site)
    {
        changeLogService.recordQuery(site.getSiteId(), "SITE", site.getSiteId(), site.getSiteName(), "预览现场编码");
        return success(supportSiteService.previewSiteCode(site));
    }

    @PreAuthorize("@ss.hasPermi('support:site:add')")
    @Log(title = "现场管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody SupportSite site)
    {
        return toAjax(supportSiteService.insertSupportSite(site));
    }

    @PreAuthorize("@ss.hasPermi('support:site:edit')")
    @Log(title = "现场管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SupportSite site)
    {
        return toAjax(supportSiteService.updateSupportSite(site));
    }

    @PreAuthorize("@ss.hasPermi('support:site:remove')")
    @Log(title = "现场管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{siteIds}")
    public AjaxResult remove(@PathVariable Long[] siteIds)
    {
        return toAjax(supportSiteService.deleteSupportSiteBySiteIds(siteIds));
    }
}
