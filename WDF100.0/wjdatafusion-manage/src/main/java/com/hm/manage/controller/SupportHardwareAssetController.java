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

    @PreAuthorize("@ss.hasPermi('support:hardwareAsset:query')")
    @GetMapping("/list")
    public TableDataInfo list(SupportHardwareAsset asset)
    {
        startPage();
        List<SupportHardwareAsset> list = hardwareAssetService.selectSupportHardwareAssetList(asset);
        changeLogService.recordQuery(asset.getSiteId(), "HARDWARE_ASSET", null, null, "查询硬件资产列表");
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('support:hardwareAsset:export')")
    @Log(title = "硬件资产管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SupportHardwareAsset asset)
    {
        List<SupportHardwareAsset> list = hardwareAssetService.selectSupportHardwareAssetList(asset);
        ExcelUtil<SupportHardwareAsset> util = new ExcelUtil<>(SupportHardwareAsset.class);
        util.exportExcel(response, list, "硬件资产数据");
    }

    @PreAuthorize("@ss.hasPermi('support:hardwareAsset:query')")
    @GetMapping(value = "/{assetId}")
    public AjaxResult getInfo(@PathVariable("assetId") Long assetId)
    {
        SupportHardwareAsset asset = hardwareAssetService.selectSupportHardwareAssetByAssetId(assetId);
        changeLogService.recordQuery(asset == null ? null : asset.getSiteId(), "HARDWARE_ASSET", assetId, asset == null ? null : asset.getAssetName(), "查询硬件资产详情");
        return success(asset);
    }

    @PreAuthorize("@ss.hasPermi('support:hardwareAsset:add')")
    @Log(title = "硬件资产管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody SupportHardwareAsset asset)
    {
        return toAjax(hardwareAssetService.insertSupportHardwareAsset(asset));
    }

    @PreAuthorize("@ss.hasPermi('support:hardwareAsset:edit')")
    @Log(title = "硬件资产管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SupportHardwareAsset asset)
    {
        return toAjax(hardwareAssetService.updateSupportHardwareAsset(asset));
    }

    @PreAuthorize("@ss.hasPermi('support:hardwareAsset:remove')")
    @Log(title = "硬件资产管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{assetIds}")
    public AjaxResult remove(@PathVariable Long[] assetIds)
    {
        return toAjax(hardwareAssetService.deleteSupportHardwareAssetByAssetIds(assetIds));
    }

    @PreAuthorize("@ss.hasPermi('support:hardwareAsset:edit')")
    @PostMapping("/bindPlatform")
    public AjaxResult bindPlatform(@RequestParam Long assetId, @RequestParam Long platformId)
    {
        return toAjax(hardwareAssetService.bindPlatform(assetId, platformId));
    }

    @PreAuthorize("@ss.hasPermi('support:hardwareAsset:edit')")
    @DeleteMapping("/unbindPlatform")
    public AjaxResult unbindPlatform(@RequestParam Long assetId, @RequestParam Long platformId)
    {
        return toAjax(hardwareAssetService.unbindPlatform(assetId, platformId));
    }
}
