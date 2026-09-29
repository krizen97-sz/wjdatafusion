package com.hm.manage.controller;

import java.util.Map;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.hm.common.annotation.Log;
import com.hm.common.core.controller.BaseController;
import com.hm.common.core.domain.AjaxResult;
import com.hm.common.enums.BusinessType;
import com.hm.manage.domain.SupportPlatform;
import com.hm.manage.domain.bo.SupportServerScanBo;
import com.hm.manage.mapper.SupportServerScanMapper;
import com.hm.manage.service.ISupportPlatformService;
import com.hm.manage.service.impl.SupportServerScanCredentialService;
import com.hm.manage.service.impl.SupportServerScanService;

@RestController
@RequestMapping("/support/equipment/servers/scan")
public class SupportServerScanController extends BaseController
{
    private final SupportServerScanService scans;
    private final SupportServerScanCredentialService credentials;
    private final SupportServerScanMapper mapper;
    private final ISupportPlatformService platforms;
    public SupportServerScanController(SupportServerScanService scans, SupportServerScanCredentialService credentials,
        SupportServerScanMapper mapper, ISupportPlatformService platforms)
    { this.scans = scans; this.credentials = credentials; this.mapper = mapper; this.platforms = platforms; }

    @PreAuthorize("@ss.hasPermi('support:server:scan')")
    @GetMapping("/options/{siteId}")
    public AjaxResult options(@PathVariable Long siteId, HttpServletResponse response)
    {
        response.setHeader("Cache-Control", "no-store");
        SupportPlatform query = new SupportPlatform(); query.setSiteId(siteId);
        return success(Map.of("credentials", credentials.list(siteId), "platforms", platforms.selectSupportPlatformList(query)));
    }
    @PreAuthorize("@ss.hasPermi('support:server:scan') and @ss.hasAnyPermi('support:server:edit,support:equipment:edit')")
    @Log(title = "扫描授权凭据配置", businessType = BusinessType.UPDATE, isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/credentials")
    public AjaxResult saveCredential(@RequestBody SupportServerScanBo.Credential command)
    { credentials.save(command, getUsername()); return success(); }

    @PreAuthorize("@ss.hasPermi('support:server:scan') and @ss.hasAnyPermi('support:server:edit,support:equipment:edit')")
    @Log(title = "扫描授权凭据删除", businessType = BusinessType.DELETE, isSaveRequestData = false, isSaveResponseData = false)
    @DeleteMapping("/credentials/{siteId}/{id}")
    public AjaxResult deleteCredential(@PathVariable Long siteId, @PathVariable Long id)
    { credentials.delete(siteId, id); return success(); }

    @PreAuthorize("@ss.hasPermi('support:server:scan') and @ss.hasAnyPermi('support:server:add,support:equipment:add') and @ss.hasAnyPermi('support:server:edit,support:equipment:edit')")
    @Log(title = "服务器端口发现", businessType = BusinessType.OTHER, isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/tasks")
    public AjaxResult start(@RequestBody SupportServerScanBo.Start command)
    { return success(scans.start(command, getUserId(), getUsername())); }

    @PreAuthorize("@ss.hasPermi('support:server:scan')")
    @GetMapping("/tasks/{id}")
    public AjaxResult status(@PathVariable String id, HttpServletResponse response)
    { response.setHeader("Cache-Control", "no-store"); return success(scans.status(id, getUserId())); }

    @PreAuthorize("@ss.hasPermi('support:server:scan')")
    @GetMapping("/active/{siteId}")
    public AjaxResult active(@PathVariable Long siteId, HttpServletResponse response)
    { response.setHeader("Cache-Control", "no-store"); return success(scans.active(siteId, getUserId())); }

    @PreAuthorize("@ss.hasPermi('support:server:scan') and @ss.hasAnyPermi('support:server:add,support:equipment:add') and @ss.hasAnyPermi('support:server:edit,support:equipment:edit')")
    @Log(title = "服务器扫描并同步", businessType = BusinessType.UPDATE, isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/tasks/{id}/confirm")
    public AjaxResult confirm(@PathVariable String id, @RequestBody SupportServerScanBo.Confirm command)
    { return success(scans.confirm(id, getUserId(), command)); }

    @PreAuthorize("@ss.hasPermi('support:server:scan')")
    @PostMapping("/tasks/{id}/cancel")
    public AjaxResult cancel(@PathVariable String id) { return success(scans.cancel(id, getUserId())); }

    @PreAuthorize("@ss.hasAnyPermi('support:equipment:query,support:server:query,support:server:list')")
    @GetMapping("/profiles/{siteId}")
    public AjaxResult profiles(@PathVariable Long siteId) { return success(mapper.selectProfiles(siteId)); }
}
