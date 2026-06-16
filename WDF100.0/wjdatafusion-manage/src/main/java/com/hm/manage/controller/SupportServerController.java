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
import org.springframework.web.bind.annotation.RestController;
import com.hm.common.annotation.Log;
import com.hm.common.core.controller.BaseController;
import com.hm.common.core.domain.AjaxResult;
import com.hm.common.core.page.TableDataInfo;
import com.hm.common.enums.BusinessType;
import com.hm.common.utils.poi.ExcelUtil;
import com.hm.manage.domain.SupportServer;
import com.hm.manage.domain.SupportServerCredential;
import com.hm.manage.service.ISupportChangeLogService;
import com.hm.manage.service.ISupportServerService;

@RestController
@RequestMapping("/support/server")
public class SupportServerController extends BaseController
{
    @Autowired
    private ISupportServerService serverService;

    @Autowired
    private ISupportChangeLogService changeLogService;

    @PreAuthorize("@ss.hasPermi('support:server:list')")
    @GetMapping("/list")
    public TableDataInfo list(SupportServer server)
    {
        startPage();
        List<SupportServer> list = serverService.selectSupportServerList(server);
        changeLogService.recordQuery(server.getSiteId(), "SERVER", null, null, "查询服务器列表");
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('support:server:export')")
    @Log(title = "服务器管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SupportServer server)
    {
        List<SupportServer> list = serverService.selectSupportServerList(server);
        ExcelUtil<SupportServer> util = new ExcelUtil<>(SupportServer.class);
        util.exportExcel(response, list, "服务器数据");
    }

    @PreAuthorize("@ss.hasPermi('support:server:add')")
    @PostMapping("/importTemplate")
    public void importTemplate(HttpServletResponse response) throws Exception
    {
        serverService.exportImportTemplate(response);
    }

    @PreAuthorize("@ss.hasPermi('support:server:add')")
    @PostMapping("/importPreview")
    public AjaxResult importPreview(MultipartFile file) throws Exception
    {
        return success(serverService.parseImportFile(file));
    }

    @PreAuthorize("@ss.hasPermi('support:server:query')")
    @GetMapping(value = "/{serverId}")
    public AjaxResult getInfo(@PathVariable("serverId") Long serverId)
    {
        SupportServer server = serverService.selectSupportServerByServerId(serverId);
        changeLogService.recordQuery(server == null ? null : server.getSiteId(), "SERVER", serverId, server == null ? null : server.getServerName(), "查询服务器详情");
        return success(server);
    }

    @PreAuthorize("@ss.hasPermi('support:server:add')")
    @Log(title = "服务器管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody SupportServer server)
    {
        return toAjax(serverService.insertSupportServer(server));
    }

    @PreAuthorize("@ss.hasPermi('support:server:edit')")
    @Log(title = "服务器管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SupportServer server)
    {
        return toAjax(serverService.updateSupportServer(server));
    }

    @PreAuthorize("@ss.hasPermi('support:server:remove')")
    @Log(title = "服务器管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{serverIds}")
    public AjaxResult remove(@PathVariable Long[] serverIds)
    {
        return toAjax(serverService.deleteSupportServerByServerIds(serverIds));
    }

    @PreAuthorize("@ss.hasPermi('support:credential:viewPlain')")
    @Log(title = "查看服务器密码明文", businessType = BusinessType.GRANT)
    @GetMapping("/plain/{serverId}")
    public AjaxResult viewPlain(@PathVariable Long serverId)
    {
        SupportServer server = serverService.selectSupportServerByServerId(serverId);
        changeLogService.recordQuery(server == null ? null : server.getSiteId(), "SERVER", serverId, server == null ? null : server.getServerName(), "查看服务器密码明文");
        return success().put("plain", serverService.getServerPasswordPlain(serverId));
    }

    @PreAuthorize("@ss.hasPermi('support:server:query')")
    @GetMapping("/credential/list/{serverId}")
    public AjaxResult credentialList(@PathVariable Long serverId)
    {
        return success(serverService.selectServerCredentialList(serverId));
    }

    @PreAuthorize("@ss.hasPermi('support:server:add')")
    @Log(title = "服务器凭据档案", businessType = BusinessType.INSERT)
    @PostMapping("/credential")
    public AjaxResult addCredential(@RequestBody SupportServerCredential credential)
    {
        return toAjax(serverService.insertServerCredential(credential));
    }

    @PreAuthorize("@ss.hasPermi('support:server:edit')")
    @Log(title = "服务器凭据档案", businessType = BusinessType.UPDATE)
    @PutMapping("/credential")
    public AjaxResult editCredential(@RequestBody SupportServerCredential credential)
    {
        return toAjax(serverService.updateServerCredential(credential));
    }

    @PreAuthorize("@ss.hasPermi('support:server:remove')")
    @Log(title = "服务器凭据档案", businessType = BusinessType.DELETE)
    @DeleteMapping("/credential/{credentialId}")
    public AjaxResult removeCredential(@PathVariable Long credentialId)
    {
        return toAjax(serverService.deleteServerCredentialById(credentialId));
    }

    @PreAuthorize("@ss.hasPermi('support:credential:viewPlain')")
    @Log(title = "查看服务器凭据明文", businessType = BusinessType.GRANT)
    @GetMapping("/credential/plain/{credentialId}")
    public AjaxResult viewCredentialPlain(@PathVariable Long credentialId)
    {
        return success().put("plain", serverService.getServerCredentialPasswordPlain(credentialId));
    }
}
