package com.hm.manage.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.hm.common.annotation.Log;
import com.hm.common.core.controller.BaseController;
import com.hm.common.core.domain.AjaxResult;
import com.hm.common.core.page.TableDataInfo;
import com.hm.common.enums.BusinessType;
import com.hm.manage.domain.SupportHardwareAsset;
import com.hm.manage.service.ISupportChangeLogService;
import com.hm.manage.service.ISupportHardwareAssetService;

@RestController
@RequestMapping("/support/hardwareAsset")
public class SupportHardwareAssetController extends BaseController
{
    @Autowired
    private ISupportHardwareAssetService hardwareAssetService;

    @Autowired
    private ISupportChangeLogService changeLogService;

    @PreAuthorize("@ss.hasAnyPermi('support:hardwareAsset:query,support:equipment:query')")
    @GetMapping("/list")
    public TableDataInfo list(SupportHardwareAsset asset)
    {
        startPage();
        List<SupportHardwareAsset> list = hardwareAssetService.selectSupportHardwareAssetList(asset);
        changeLogService.recordQuery(asset.getSiteId(), "HARDWARE_ASSET", null, null, "查询硬件资产列表");
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasAnyPermi('support:hardwareAsset:query,support:equipment:query')")
    @GetMapping(value = "/{assetId}")
    public AjaxResult getInfo(@PathVariable("assetId") Long assetId)
    {
        SupportHardwareAsset asset = hardwareAssetService.selectSupportHardwareAssetByAssetId(assetId);
        changeLogService.recordQuery(asset == null ? null : asset.getSiteId(), "HARDWARE_ASSET", assetId, asset == null ? null : asset.getAssetName(), "查询硬件资产详情");
        return success(asset);
    }

    @PreAuthorize("@ss.hasPermi('support:credential:viewPlain')")
    @GetMapping("/plain/{assetId}")
    public AjaxResult viewPlain(@PathVariable Long assetId)
    {
        SupportHardwareAsset asset = hardwareAssetService.selectSupportHardwareAssetByAssetId(assetId);
        changeLogService.recordQuery(asset == null ? null : asset.getSiteId(), "HARDWARE_ASSET", assetId, asset == null ? null : asset.getAssetName(), "查看硬件资产密码");
        return success().put("plain", hardwareAssetService.getHardwareAssetPasswordPlain(assetId));
    }

    @PreAuthorize("@ss.hasAnyPermi('support:hardwareAsset:edit,support:equipment:edit')")
    @Log(title = "硬件资产管理", businessType = BusinessType.UPDATE, isSaveRequestData = false)
    @PutMapping
    public AjaxResult edit(@RequestBody SupportHardwareAsset asset)
    {
        return toAjax(hardwareAssetService.updateSupportHardwareAsset(asset));
    }


}
