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
import com.hm.manage.domain.SupportSubplatformEndpoint;
import com.hm.manage.service.ISupportChangeLogService;
import com.hm.manage.service.ISupportSubplatformEndpointService;

@RestController
@RequestMapping("/support/endpoint")
public class SupportSubplatformEndpointController extends BaseController
{
    @Autowired
    private ISupportSubplatformEndpointService endpointService;

    @Autowired
    private ISupportChangeLogService changeLogService;

    @PreAuthorize("@ss.hasPermi('support:platform:list')")
    @GetMapping("/list")
    public TableDataInfo list(SupportSubplatformEndpoint endpoint)
    {
        startPage();
        List<SupportSubplatformEndpoint> list = endpointService.selectSupportSubplatformEndpointList(endpoint);
        changeLogService.recordQuery(null, "ENDPOINT", null, null, "查询页面列表");
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('support:platform:export')")
    @Log(title = "子平台页面", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SupportSubplatformEndpoint endpoint)
    {
        List<SupportSubplatformEndpoint> list = endpointService.selectSupportSubplatformEndpointList(endpoint);
        ExcelUtil<SupportSubplatformEndpoint> util = new ExcelUtil<>(SupportSubplatformEndpoint.class);
        util.exportExcel(response, list, "子平台页面数据");
    }

    @PreAuthorize("@ss.hasPermi('support:platform:query')")
    @GetMapping(value = "/{endpointId}")
    public AjaxResult getInfo(@PathVariable("endpointId") Long endpointId)
    {
        SupportSubplatformEndpoint endpoint = endpointService.selectSupportSubplatformEndpointByEndpointId(endpointId);
        changeLogService.recordQuery(null, "ENDPOINT", endpointId, endpoint == null ? null : endpoint.getEndpointName(), "查询页面详情");
        return success(endpoint);
    }

    @PreAuthorize("@ss.hasPermi('support:platform:add')")
    @Log(title = "子平台页面", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody SupportSubplatformEndpoint endpoint)
    {
        return toAjax(endpointService.insertSupportSubplatformEndpoint(endpoint));
    }

    @PreAuthorize("@ss.hasPermi('support:platform:edit')")
    @Log(title = "子平台页面", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SupportSubplatformEndpoint endpoint)
    {
        return toAjax(endpointService.updateSupportSubplatformEndpoint(endpoint));
    }

    @PreAuthorize("@ss.hasPermi('support:platform:remove')")
    @Log(title = "子平台页面", businessType = BusinessType.DELETE)
    @DeleteMapping("/{endpointIds}")
    public AjaxResult remove(@PathVariable Long[] endpointIds)
    {
        return toAjax(endpointService.deleteSupportSubplatformEndpointByEndpointIds(endpointIds));
    }

    @PreAuthorize("@ss.hasPermi('support:credential:viewPlain')")
    @Log(title = "查看页面密码明文", businessType = BusinessType.GRANT)
    @GetMapping("/plain/{endpointId}")
    public AjaxResult viewPlain(@PathVariable Long endpointId)
    {
        SupportSubplatformEndpoint endpoint = endpointService.selectSupportSubplatformEndpointByEndpointId(endpointId);
        changeLogService.recordQuery(null, "ENDPOINT", endpointId, endpoint == null ? null : endpoint.getEndpointName(), "查看页面密码明文");
        return success().put("plain", endpointService.getEndpointPasswordPlain(endpointId));
    }
}
