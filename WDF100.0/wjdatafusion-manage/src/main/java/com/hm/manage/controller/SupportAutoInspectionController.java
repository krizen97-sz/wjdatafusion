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
import com.hm.manage.domain.SupportAutoInspectionRecord;
import com.hm.manage.domain.SupportAutoInspectionTarget;
import com.hm.manage.domain.SupportAutoInspectionTemplate;
import com.hm.manage.domain.SupportAutoInspectionTool;
import com.hm.manage.domain.bo.AutoInspectionDashboardQuery;
import com.hm.manage.domain.bo.AutoInspectionHealthQuery;
import com.hm.manage.domain.bo.AutoInspectionRecordQuery;
import com.hm.manage.domain.bo.AutoInspectionReportExportBo;
import com.hm.manage.domain.bo.AutoInspectionServerCredentialBatchBo;
import com.hm.manage.domain.bo.AutoInspectionTargetQuery;
import com.hm.manage.domain.bo.AutoInspectionTargetSaveBo;
import com.hm.manage.domain.bo.AutoInspectionTemplateQuery;
import com.hm.manage.domain.bo.AutoInspectionTemplateSaveBo;
import com.hm.manage.service.ISupportAutoInspectionService;

@RestController
@RequestMapping("/support/autoInspection")
public class SupportAutoInspectionController extends BaseController
{
    @Autowired
    private ISupportAutoInspectionService autoInspectionService;

    @PreAuthorize("@ss.hasPermi('support:autoInspection:query')")
    @GetMapping("/tools")
    public AjaxResult tools(SupportAutoInspectionTool query)
    {
        return success(autoInspectionService.selectToolList(query));
    }

    @PreAuthorize("@ss.hasPermi('support:autoInspection:target')")
    @GetMapping("/targets")
    public TableDataInfo targets(AutoInspectionTargetQuery query)
    {
        startPage();
        List<SupportAutoInspectionTarget> list = autoInspectionService.selectTargetList(query);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('support:autoInspection:target')")
    @GetMapping("/targets/{targetId}")
    public AjaxResult target(@PathVariable Long targetId)
    {
        return success(autoInspectionService.selectTargetById(targetId));
    }

    @PreAuthorize("@ss.hasPermi('support:autoInspection:target')")
    @GetMapping("/targets/server-assets")
    public AjaxResult serverAssets()
    {
        return success(autoInspectionService.selectServerAssetTree());
    }

    @PreAuthorize("@ss.hasPermi('support:credential:viewPlain')")
    @Log(title = "查看现场服务器巡检凭据", businessType = BusinessType.GRANT)
    @GetMapping("/targets/server-credentials/{serverId}")
    public AjaxResult serverCredential(@PathVariable Long serverId, @RequestParam String username)
    {
        return success(autoInspectionService.selectServerCredentialPlain(serverId, username));
    }

    @PreAuthorize("@ss.hasPermi('support:credential:viewPlain')")
    @Log(title = "批量查看现场服务器巡检凭据", businessType = BusinessType.GRANT)
    @PostMapping("/targets/server-credentials/batch")
    public AjaxResult serverCredentialBatch(@RequestBody AutoInspectionServerCredentialBatchBo query)
    {
        return success(autoInspectionService.selectServerCredentialPlainBatch(query));
    }

    @PreAuthorize("@ss.hasPermi('support:autoInspection:target')")
    @Log(title = "自动化巡检目标", businessType = BusinessType.INSERT)
    @PostMapping("/targets")
    public AjaxResult addTarget(@RequestBody AutoInspectionTargetSaveBo target)
    {
        return toAjax(autoInspectionService.insertTarget(target));
    }

    @PreAuthorize("@ss.hasPermi('support:autoInspection:target')")
    @Log(title = "自动化巡检目标", businessType = BusinessType.UPDATE)
    @PutMapping("/targets")
    public AjaxResult updateTarget(@RequestBody AutoInspectionTargetSaveBo target)
    {
        return toAjax(autoInspectionService.updateTarget(target));
    }

    @PreAuthorize("@ss.hasPermi('support:autoInspection:target')")
    @Log(title = "自动化巡检目标", businessType = BusinessType.DELETE)
    @DeleteMapping("/targets/{targetId}")
    public AjaxResult deleteTarget(@PathVariable Long targetId)
    {
        return toAjax(autoInspectionService.deleteTargetById(targetId));
    }

    @PreAuthorize("@ss.hasPermi('support:autoInspection:target')")
    @PostMapping("/targets/test")
    public AjaxResult testTarget(@RequestBody AutoInspectionTargetSaveBo target)
    {
        return success().put("message", autoInspectionService.testTarget(target));
    }

    @PreAuthorize("@ss.hasPermi('support:autoInspection:target')")
    @PostMapping("/targets/preview")
    public AjaxResult previewTarget(@RequestBody AutoInspectionTargetSaveBo target)
    {
        return success(autoInspectionService.previewTarget(target));
    }

    @PreAuthorize("@ss.hasPermi('support:credential:viewPlain')")
    @Log(title = "查看自动化巡检敏感信息", businessType = BusinessType.GRANT)
    @GetMapping("/targets/plain/{targetId}")
    public AjaxResult targetPlain(@PathVariable Long targetId)
    {
        return success()
                .put("password", autoInspectionService.getTargetPasswordPlain(targetId))
                .put("secret", autoInspectionService.getTargetSecretPlain(targetId));
    }

    @PreAuthorize("@ss.hasPermi('support:autoInspection:template')")
    @GetMapping("/templates")
    public TableDataInfo templates(AutoInspectionTemplateQuery query)
    {
        startPage();
        List<SupportAutoInspectionTemplate> list = autoInspectionService.selectTemplateList(query);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('support:autoInspection:template')")
    @GetMapping("/templates/{templateId}")
    public AjaxResult template(@PathVariable Long templateId)
    {
        return success(autoInspectionService.selectTemplateById(templateId));
    }

    @PreAuthorize("@ss.hasPermi('support:autoInspection:template')")
    @Log(title = "自动化巡检模板", businessType = BusinessType.INSERT)
    @PostMapping("/templates")
    public AjaxResult addTemplate(@RequestBody AutoInspectionTemplateSaveBo template)
    {
        return success(autoInspectionService.saveTemplate(template));
    }

    @PreAuthorize("@ss.hasPermi('support:autoInspection:template')")
    @Log(title = "自动化巡检模板", businessType = BusinessType.UPDATE)
    @PutMapping("/templates")
    public AjaxResult updateTemplate(@RequestBody AutoInspectionTemplateSaveBo template)
    {
        return success(autoInspectionService.saveTemplate(template));
    }

    @PreAuthorize("@ss.hasPermi('support:autoInspection:template')")
    @Log(title = "自动化巡检模板", businessType = BusinessType.INSERT)
    @PostMapping("/templates/{templateId}/copy")
    public AjaxResult copyTemplate(@PathVariable Long templateId)
    {
        return success(autoInspectionService.copyTemplate(templateId));
    }

    @PreAuthorize("@ss.hasPermi('support:autoInspection:template')")
    @Log(title = "自动化巡检模板", businessType = BusinessType.DELETE)
    @DeleteMapping("/templates/{templateId}")
    public AjaxResult deleteTemplate(@PathVariable Long templateId)
    {
        return toAjax(autoInspectionService.deleteTemplateById(templateId));
    }

    @PreAuthorize("@ss.hasPermi('support:autoInspection:run')")
    @Log(title = "自动化巡检模板", businessType = BusinessType.OTHER)
    @PostMapping("/templates/{templateId}/run")
    public AjaxResult runTemplate(@PathVariable Long templateId)
    {
        return success(autoInspectionService.runManualTemplate(templateId));
    }

    @PreAuthorize("@ss.hasPermi('support:autoInspection:query')")
    @GetMapping("/dashboard")
    public AjaxResult dashboard(AutoInspectionDashboardQuery query)
    {
        return success(autoInspectionService.selectDashboard(query));
    }

    @PreAuthorize("@ss.hasPermi('support:autoInspection:query')")
    @GetMapping("/health/daily")
    public AjaxResult dailyHealth(AutoInspectionHealthQuery query)
    {
        return success(autoInspectionService.selectDailyHealth(query));
    }

    @PreAuthorize("@ss.hasPermi('support:autoInspection:query')")
    @GetMapping("/records")
    public TableDataInfo records(AutoInspectionRecordQuery query)
    {
        startPage();
        List<SupportAutoInspectionRecord> list = autoInspectionService.selectRecordList(query);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('support:autoInspection:query')")
    @GetMapping("/records/{recordId}")
    public AjaxResult record(@PathVariable Long recordId)
    {
        return success(autoInspectionService.selectRecordDetail(recordId));
    }

    @PreAuthorize("@ss.hasPermi('support:autoInspection:export')")
    @Log(title = "自动化巡检记录", businessType = BusinessType.EXPORT)
    @PostMapping("/reports/export")
    public void export(HttpServletResponse response, AutoInspectionReportExportBo query)
    {
        autoInspectionService.exportRecord(response, query);
    }
}
