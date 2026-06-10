package com.hm.manage.controller;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.hm.common.annotation.Log;
import com.hm.common.core.controller.BaseController;
import com.hm.common.core.domain.AjaxResult;
import com.hm.common.core.page.TableDataInfo;
import com.hm.common.enums.BusinessType;
import com.hm.common.utils.poi.ExcelUtil;
import com.hm.manage.domain.SupportOrg;
import com.hm.manage.service.ISupportChangeLogService;
import com.hm.manage.service.ISupportOrgService;

@RestController
@RequestMapping("/support/org")
public class SupportOrgController extends BaseController
{
    @Autowired
    private ISupportOrgService orgService;

    @Autowired
    private ISupportChangeLogService changeLogService;

    @PreAuthorize("@ss.hasPermi('support:org:list')")
    @GetMapping("/list")
    public TableDataInfo list(SupportOrg org)
    {
        startPage();
        List<SupportOrg> list = orgService.selectSupportOrgList(org);
        changeLogService.recordQuery(null, "ORG", null, null, "查询组织列表");
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('support:org:export')")
    @Log(title = "组织管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SupportOrg org)
    {
        List<SupportOrg> list = orgService.selectSupportOrgList(org);
        ExcelUtil<SupportOrg> util = new ExcelUtil<>(SupportOrg.class);
        util.exportExcel(response, list, "组织数据");
    }

    @PreAuthorize("@ss.hasPermi('support:org:query')")
    @GetMapping(value = "/{orgId}")
    public AjaxResult getInfo(@PathVariable("orgId") Long orgId)
    {
        SupportOrg org = orgService.selectSupportOrgByOrgId(orgId);
        changeLogService.recordQuery(null, "ORG", orgId, org == null ? null : org.getOrgName(), "查询组织详情");
        return success(org);
    }

    @PreAuthorize("@ss.hasPermi('support:org:query')")
    @GetMapping(value = "/platforms/{orgId}")
    public AjaxResult platforms(@PathVariable("orgId") Long orgId)
    {
        changeLogService.recordQuery(null, "ORG", orgId, null, "查询组织关联平台");
        return success(orgService.listPlatformsByOrgId(orgId));
    }

    @PreAuthorize("@ss.hasPermi('support:org:add')")
    @Log(title = "组织管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody SupportOrg org)
    {
        return toAjax(orgService.insertSupportOrg(org));
    }

    @PreAuthorize("@ss.hasPermi('support:org:edit')")
    @Log(title = "组织管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SupportOrg org)
    {
        return toAjax(orgService.updateSupportOrg(org));
    }

    @PreAuthorize("@ss.hasPermi('support:org:remove')")
    @Log(title = "组织管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{orgIds}")
    public AjaxResult remove(@PathVariable Long[] orgIds)
    {
        return toAjax(orgService.deleteSupportOrgByOrgIds(orgIds));
    }
}
