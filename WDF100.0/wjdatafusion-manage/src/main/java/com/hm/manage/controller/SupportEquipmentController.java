package com.hm.manage.controller;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.hm.common.annotation.Log;
import com.hm.common.core.controller.BaseController;
import com.hm.common.core.domain.AjaxResult;
import com.hm.common.enums.BusinessType;
import com.hm.common.utils.poi.ExcelUtil;
import com.hm.manage.domain.SupportEquipmentAsset;
import com.hm.manage.service.ISupportChangeLogService;
import com.hm.manage.service.ISupportEquipmentService;

@RestController
@RequestMapping("/support/equipment")
public class SupportEquipmentController extends BaseController
{
    @Autowired
    private ISupportEquipmentService equipmentService;

    @Autowired
    private ISupportChangeLogService changeLogService;

    @PreAuthorize("@ss.hasPermi('support:equipment:query')")
    @GetMapping("/list")
    public AjaxResult list(SupportEquipmentAsset query)
    {
        List<SupportEquipmentAsset> list = equipmentService.selectEquipmentAssetList(query);
        changeLogService.recordQuery(query.getSiteId(), "EQUIPMENT_ASSET", null, null, "查询设备资产清单");
        return success().put("rows", list).put("total", list.size());
    }

    @PreAuthorize("@ss.hasPermi('support:equipment:export')")
    @Log(title = "设备资产清单", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SupportEquipmentAsset query)
    {
        List<SupportEquipmentAsset> list = equipmentService.selectEquipmentAssetList(query);
        ExcelUtil<SupportEquipmentAsset> util = new ExcelUtil<>(SupportEquipmentAsset.class);
        util.exportExcel(response, list, "设备资产清单");
    }
}
