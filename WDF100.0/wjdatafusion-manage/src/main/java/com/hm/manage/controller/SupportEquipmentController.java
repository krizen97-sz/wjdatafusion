package com.hm.manage.controller;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RestController;
import com.hm.common.annotation.Log;
import com.hm.common.core.controller.BaseController;
import com.hm.common.core.domain.AjaxResult;
import com.hm.common.enums.BusinessType;
import com.hm.common.utils.poi.ExcelUtil;
import com.hm.manage.domain.SupportEquipmentAsset;
import com.hm.manage.domain.bo.SupportEquipmentBatchBo;
import com.hm.manage.domain.bo.SupportEquipmentCreateBo;
import com.hm.manage.domain.bo.SupportEquipmentPlatformBindingBo;
import com.hm.manage.service.ISupportEquipmentService;
import com.hm.manage.service.impl.SupportEquipmentServerIntakeService;
import com.hm.manage.domain.bo.SupportEquipmentServerIntakeBo;
import com.hm.framework.web.service.PermissionService;

@RestController
@RequestMapping("/support/equipment")
public class SupportEquipmentController extends BaseController
{
    @Autowired
    private ISupportEquipmentService equipmentService;

    @Autowired
    private SupportEquipmentServerIntakeService serverIntakeService;

    @Autowired
    private PermissionService permissions;

    @PreAuthorize("@ss.hasAnyPermi('support:equipment:add,support:server:add')")
    @PostMapping("/servers/template")
    public void serverTemplate(HttpServletResponse response) throws Exception
    {
        serverIntakeService.exportTemplate(response);
    }

    @PreAuthorize("@ss.hasAnyPermi('support:equipment:add,support:server:add')")
    @PostMapping("/servers/importPreview")
    public AjaxResult previewServerFile(@RequestParam Long siteId, @RequestParam Long platformId,
        @RequestParam MultipartFile file) throws Exception
    {
        return success(serverIntakeService.previewFile(siteId, platformId, file));
    }

    @PreAuthorize("@ss.hasAnyPermi('support:equipment:add,support:server:add')")
    @PostMapping("/servers/preview")
    public AjaxResult previewServers(@RequestBody SupportEquipmentServerIntakeBo command)
    {
        return success(serverIntakeService.preview(command));
    }

    @PreAuthorize("@ss.hasAnyPermi('support:equipment:add,support:server:add')")
    @Log(title = "设备服务器批量录入", businessType = BusinessType.INSERT, isSaveRequestData = false)
    @PostMapping("/servers/commit")
    public AjaxResult commitServers(@RequestBody SupportEquipmentServerIntakeBo command)
    {
        return success(serverIntakeService.commit(command));
    }

    @PreAuthorize("@ss.hasPermi('support:equipment:add') or (#command.server != null and @ss.hasPermi('support:server:add')) or (#command.hardware != null and @ss.hasPermi('support:hardwareAsset:add'))")
    @Log(title = "设备统一录入", businessType = BusinessType.INSERT, isSaveRequestData = false)
    @PostMapping
    public AjaxResult create(@RequestBody SupportEquipmentCreateBo command)
    {
        return success(equipmentService.createEquipment(command));
    }

    @PreAuthorize("@ss.hasAnyPermi('support:equipment:export,support:server:export,support:hardwareAsset:export')")
    @Log(title = "设备资产清单", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SupportEquipmentAsset query)
    {
        List<SupportEquipmentAsset> list = equipmentService.selectEquipmentAssetList(query).stream()
            .filter(row -> canOperate(row.getSourceType(), "export")).collect(Collectors.toList());
        ExcelUtil<SupportEquipmentAsset> util = new ExcelUtil<>(SupportEquipmentAsset.class);
        util.exportExcel(response, list, "设备资产清单");
    }

    @PreAuthorize("@ss.hasAnyPermi('support:equipment:remove,support:server:remove,support:hardwareAsset:remove')")
    @Log(title = "设备统一管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/batch")
    public AjaxResult remove(@RequestBody SupportEquipmentBatchBo command)
    {
        if (command != null && command.getDevices() != null)
        {
            for (var device : command.getDevices())
            {
                if (device == null || !canOperate(device.getSourceType(), "remove"))
                    throw new AccessDeniedException("无权删除清单中的设备类型");
            }
        }
        return toAjax(equipmentService.deleteEquipmentAssets(command));
    }

    private boolean canOperate(String sourceType, String action)
    {
        String type = sourceType == null ? "" : sourceType.trim().toUpperCase(Locale.ROOT);
        String resource = "SERVER".equals(type) ? "server" : "HARDWARE".equals(type) ? "hardwareAsset" : null;
        return resource != null && (permissions.hasPermi("support:equipment:" + action)
            || permissions.hasPermi("support:" + resource + ":" + action));
    }

    @PreAuthorize("@ss.hasPermi('support:equipment:edit')")
    @Log(title = "设备统一管理", businessType = BusinessType.UPDATE)
    @PutMapping("/platform/bind")
    public AjaxResult bindPlatform(@RequestBody SupportEquipmentPlatformBindingBo command)
    {
        return toAjax(equipmentService.bindPlatform(command));
    }

    @PreAuthorize("@ss.hasPermi('support:equipment:edit')")
    @Log(title = "设备统一管理", businessType = BusinessType.UPDATE)
    @PutMapping("/platform/unbind")
    public AjaxResult unbindPlatform(@RequestBody SupportEquipmentPlatformBindingBo command)
    {
        return toAjax(equipmentService.unbindPlatform(command));
    }
}
