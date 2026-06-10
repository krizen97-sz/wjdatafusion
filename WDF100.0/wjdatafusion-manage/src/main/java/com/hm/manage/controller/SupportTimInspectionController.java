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
import com.hm.manage.domain.SupportTimInspection;
import com.hm.manage.domain.SupportTimInspectionItemConfig;
import com.hm.manage.domain.SupportTimInspectionTarget;
import com.hm.manage.service.ISupportTimInspectionService;

@RestController
@RequestMapping("/support/timInspection")
public class SupportTimInspectionController extends BaseController
{
    @Autowired
    private ISupportTimInspectionService timInspectionService;

    @PreAuthorize("@ss.hasPermi('support:timInspection:list')")
    @GetMapping("/list")
    public TableDataInfo list(SupportTimInspection inspection)
    {
        startPage();
        List<SupportTimInspection> list = timInspectionService.selectInspectionList(inspection);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('support:timInspection:query')")
    @GetMapping("/{inspectionId}")
    public AjaxResult getInfo(@PathVariable Long inspectionId)
    {
        return success(timInspectionService.selectInspectionDetail(inspectionId));
    }

    @PreAuthorize("@ss.hasPermi('support:timInspection:run')")
    @Log(title = "TIM系统巡检", businessType = BusinessType.OTHER)
    @PostMapping("/run")
    public AjaxResult run()
    {
        return success(timInspectionService.runManualInspection());
    }

    @PreAuthorize("@ss.hasPermi('support:timInspection:export')")
    @Log(title = "TIM系统巡检", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SupportTimInspection inspection)
    {
        timInspectionService.exportInspection(response, inspection);
    }

    @PreAuthorize("@ss.hasPermi('support:timInspection:config')")
    @GetMapping("/config")
    public AjaxResult config()
    {
        return success(timInspectionService.selectConfigList());
    }

    @PreAuthorize("@ss.hasPermi('support:timInspection:config')")
    @Log(title = "TIM巡检项配置", businessType = BusinessType.UPDATE)
    @PutMapping("/config/item")
    public AjaxResult updateItem(@RequestBody SupportTimInspectionItemConfig config)
    {
        return toAjax(timInspectionService.updateItemConfig(config));
    }

    @PreAuthorize("@ss.hasPermi('support:timInspection:config')")
    @GetMapping("/config/target/list")
    public TableDataInfo targetList(SupportTimInspectionTarget target)
    {
        startPage();
        List<SupportTimInspectionTarget> list = timInspectionService.selectTargetList(target);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('support:timInspection:config')")
    @GetMapping("/config/target/{targetId}")
    public AjaxResult getTarget(@PathVariable Long targetId)
    {
        return success(timInspectionService.selectTargetById(targetId));
    }

    @PreAuthorize("@ss.hasPermi('support:timInspection:config')")
    @Log(title = "TIM巡检目标配置", businessType = BusinessType.INSERT)
    @PostMapping("/config/target")
    public AjaxResult addTarget(@RequestBody SupportTimInspectionTarget target)
    {
        return toAjax(timInspectionService.insertTarget(target));
    }

    @PreAuthorize("@ss.hasPermi('support:timInspection:config')")
    @Log(title = "TIM巡检目标配置", businessType = BusinessType.UPDATE)
    @PutMapping("/config/target")
    public AjaxResult updateTarget(@RequestBody SupportTimInspectionTarget target)
    {
        return toAjax(timInspectionService.updateTarget(target));
    }

    @PreAuthorize("@ss.hasPermi('support:timInspection:config')")
    @Log(title = "TIM巡检目标配置", businessType = BusinessType.DELETE)
    @DeleteMapping("/config/target/{targetId}")
    public AjaxResult deleteTarget(@PathVariable Long targetId)
    {
        return toAjax(timInspectionService.deleteTargetById(targetId));
    }

    @PreAuthorize("@ss.hasPermi('support:timInspection:config')")
    @PostMapping("/config/target/test")
    public AjaxResult testTarget(@RequestBody SupportTimInspectionTarget target)
    {
        return success().put("message", timInspectionService.testTarget(target));
    }

    @PreAuthorize("@ss.hasPermi('support:credential:viewPlain')")
    @Log(title = "查看TIM巡检目标敏感信息", businessType = BusinessType.GRANT)
    @GetMapping("/config/target/plain/{targetId}")
    public AjaxResult targetPlain(@PathVariable Long targetId)
    {
        return success()
                .put("password", timInspectionService.getTargetPasswordPlain(targetId))
                .put("secret", timInspectionService.getTargetSecretPlain(targetId));
    }
}
