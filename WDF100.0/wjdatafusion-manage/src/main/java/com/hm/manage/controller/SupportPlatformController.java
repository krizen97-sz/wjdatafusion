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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.hm.common.annotation.Log;
import com.hm.common.core.controller.BaseController;
import com.hm.common.core.domain.AjaxResult;
import com.hm.common.core.page.TableDataInfo;
import com.hm.common.enums.BusinessType;
import com.hm.common.utils.poi.ExcelUtil;
import com.hm.manage.domain.SupportContact;
import com.hm.manage.domain.SupportPlatform;
import com.hm.manage.service.ISupportChangeLogService;
import com.hm.manage.service.ISupportPlatformService;

@RestController
@RequestMapping("/support/platform")
public class SupportPlatformController extends BaseController
{
    @Autowired
    private ISupportPlatformService platformService;

    @Autowired
    private ISupportChangeLogService changeLogService;

    @PreAuthorize("@ss.hasPermi('support:platform:list')")
    @GetMapping("/list")
    public TableDataInfo list(SupportPlatform platform)
    {
        startPage();
        List<SupportPlatform> list = platformService.selectSupportPlatformList(platform);
        changeLogService.recordQuery(platform.getSiteId(), "PLATFORM", null, null, "查询平台列表");
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('support:platform:export')")
    @Log(title = "平台管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SupportPlatform platform)
    {
        List<SupportPlatform> list = platformService.selectSupportPlatformList(platform);
        ExcelUtil<SupportPlatform> util = new ExcelUtil<>(SupportPlatform.class);
        util.exportExcel(response, list, "平台数据");
    }

    @PreAuthorize("@ss.hasPermi('support:platform:query')")
    @GetMapping(value = "/{platformId}")
    public AjaxResult getInfo(@PathVariable("platformId") Long platformId)
    {
        SupportPlatform platform = platformService.selectSupportPlatformByPlatformId(platformId);
        changeLogService.recordQuery(platform == null ? null : platform.getSiteId(), "PLATFORM", platformId, platform == null ? null : platform.getPlatformName(), "查询平台详情");
        return success(platform);
    }

    @PreAuthorize("@ss.hasPermi('support:platform:query')")
    @GetMapping(value = "/tree/{siteId}")
    public AjaxResult tree(@PathVariable("siteId") Long siteId)
    {
        changeLogService.recordQuery(siteId, "PLATFORM", null, null, "查询平台树");
        return success(platformService.selectPlatformTreeBySiteId(siteId));
    }

    @PreAuthorize("@ss.hasPermi('support:platform:add')")
    @Log(title = "平台管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody SupportPlatform platform)
    {
        return toAjax(platformService.insertSupportPlatform(platform));
    }

    @PreAuthorize("@ss.hasPermi('support:platform:edit')")
    @Log(title = "平台管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SupportPlatform platform)
    {
        return toAjax(platformService.updateSupportPlatform(platform));
    }

    @PreAuthorize("@ss.hasPermi('support:platform:remove')")
    @Log(title = "平台管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{platformIds}")
    public AjaxResult remove(@PathVariable Long[] platformIds)
    {
        return toAjax(platformService.deleteSupportPlatformByPlatformIds(platformIds));
    }

    @PreAuthorize("@ss.hasPermi('support:platform:edit')")
    @PostMapping("/bindServer")
    public AjaxResult bindServer(@RequestParam Long platformId, @RequestParam Long serverId)
    {
        return toAjax(platformService.bindServer(platformId, serverId));
    }

    @PreAuthorize("@ss.hasPermi('support:platform:edit')")
    @DeleteMapping("/unbindServer")
    public AjaxResult unbindServer(@RequestParam Long platformId, @RequestParam Long serverId)
    {
        return toAjax(platformService.unbindServer(platformId, serverId));
    }

    @PreAuthorize("@ss.hasPermi('support:platform:query')")
    @GetMapping("/servers/{platformId}")
    public AjaxResult listServers(@PathVariable Long platformId)
    {
        SupportPlatform platform = platformService.selectSupportPlatformByPlatformId(platformId);
        changeLogService.recordQuery(platform == null ? null : platform.getSiteId(), "SERVER", platformId, platform == null ? null : platform.getPlatformName(), "查询平台关联服务器");
        return success(platformService.listServersByPlatformId(platformId));
    }

    @PreAuthorize("@ss.hasPermi('support:platform:edit')")
    @PostMapping("/bindContact")
    public AjaxResult bindContact(@RequestParam Long platformId, @RequestParam Long contactId)
    {
        return toAjax(platformService.bindContact(platformId, contactId));
    }

    @PreAuthorize("@ss.hasPermi('support:platform:edit')")
    @DeleteMapping("/unbindContact")
    public AjaxResult unbindContact(@RequestParam Long platformId, @RequestParam Long contactId)
    {
        return toAjax(platformService.unbindContact(platformId, contactId));
    }

    @PreAuthorize("@ss.hasPermi('support:platform:query')")
    @GetMapping("/contacts/{platformId}")
    public AjaxResult listContacts(@PathVariable Long platformId)
    {
        SupportPlatform platform = platformService.selectSupportPlatformByPlatformId(platformId);
        changeLogService.recordQuery(platform == null ? null : platform.getSiteId(), "CONTACT", platformId, platform == null ? null : platform.getPlatformName(), "查询平台关联人员");
        List<SupportContact> list = platformService.listContactsByPlatformId(platformId);
        return success(list);
    }
}
