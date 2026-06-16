package com.hm.manage.controller;

import java.util.List;
import java.util.Map;
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
import com.hm.manage.service.ISupportAutoInspectionService;

@RestController
@RequestMapping("/support/autoInspection")
public class SupportAutoInspectionController extends BaseController
{
    @Autowired
    private ISupportAutoInspectionService autoInspectionService;

    @PreAuthorize("@ss.hasPermi('support:autoInspection:query')")
    @GetMapping("/tool/list")
    public AjaxResult toolList()
    {
        return success(autoInspectionService.selectToolList(null));
    }

    @PreAuthorize("@ss.hasPermi('support:autoInspection:target')")
    @GetMapping("/target/list")
    public TableDataInfo targetList(@RequestParam Map<String, Object> target)
    {
        startPage();
        List<Map<String, Object>> list = autoInspectionService.selectTargetList(target);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('support:autoInspection:target')")
    @GetMapping("/target/{targetId}")
    public AjaxResult getTarget(@PathVariable Long targetId)
    {
        return success(autoInspectionService.selectTargetById(targetId));
    }

    @PreAuthorize("@ss.hasPermi('support:autoInspection:target')")
    @GetMapping("/target/serverAssetTree")
    public AjaxResult serverAssetTree()
    {
        return success(autoInspectionService.selectServerAssetTree());
    }

    @PreAuthorize("@ss.hasPermi('support:credential:viewPlain')")
    @Log(title = "查看现场服务器巡检凭据", businessType = BusinessType.GRANT)
    @GetMapping("/target/serverCredentialPlain/{serverId}")
    public AjaxResult serverCredentialPlain(@PathVariable Long serverId, @RequestParam String username)
    {
        return success(autoInspectionService.selectServerCredentialPlain(serverId, username));
    }

    @PreAuthorize("@ss.hasPermi('support:autoInspection:target')")
    @Log(title = "自动化巡检目标", businessType = BusinessType.INSERT)
    @PostMapping("/target")
    public AjaxResult addTarget(@RequestBody Map<String, Object> target)
    {
        return toAjax(autoInspectionService.insertTarget(target));
    }

    @PreAuthorize("@ss.hasPermi('support:autoInspection:target')")
    @Log(title = "自动化巡检目标", businessType = BusinessType.UPDATE)
    @PutMapping("/target")
    public AjaxResult updateTarget(@RequestBody Map<String, Object> target)
    {
        return toAjax(autoInspectionService.updateTarget(target));
    }

    @PreAuthorize("@ss.hasPermi('support:autoInspection:target')")
    @Log(title = "自动化巡检目标", businessType = BusinessType.DELETE)
    @DeleteMapping("/target/{targetId}")
    public AjaxResult deleteTarget(@PathVariable Long targetId)
    {
        return toAjax(autoInspectionService.deleteTargetById(targetId));
    }

    @PreAuthorize("@ss.hasPermi('support:autoInspection:target')")
    @PostMapping("/target/test")
    public AjaxResult testTarget(@RequestBody Map<String, Object> target)
    {
        return success().put("message", autoInspectionService.testTarget(target));
    }

    @PreAuthorize("@ss.hasPermi('support:credential:viewPlain')")
    @Log(title = "查看自动化巡检敏感信息", businessType = BusinessType.GRANT)
    @GetMapping("/target/plain/{targetId}")
    public AjaxResult targetPlain(@PathVariable Long targetId)
    {
        return success()
                .put("password", autoInspectionService.getTargetPasswordPlain(targetId))
                .put("secret", autoInspectionService.getTargetSecretPlain(targetId));
    }

    @PreAuthorize("@ss.hasPermi('support:autoInspection:template')")
    @GetMapping("/template/list")
    public TableDataInfo templateList(@RequestParam Map<String, Object> template)
    {
        startPage();
        List<Map<String, Object>> list = autoInspectionService.selectTemplateList(template);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('support:autoInspection:template')")
    @GetMapping("/template/{templateId}")
    public AjaxResult getTemplate(@PathVariable Long templateId)
    {
        return success(autoInspectionService.selectTemplateById(templateId));
    }

    @PreAuthorize("@ss.hasPermi('support:autoInspection:template')")
    @Log(title = "自动化巡检模板", businessType = BusinessType.INSERT)
    @PostMapping("/template")
    public AjaxResult addTemplate(@RequestBody Map<String, Object> template)
    {
        return success(autoInspectionService.saveTemplate(template));
    }

    @PreAuthorize("@ss.hasPermi('support:autoInspection:template')")
    @Log(title = "自动化巡检模板", businessType = BusinessType.UPDATE)
    @PutMapping("/template")
    public AjaxResult updateTemplate(@RequestBody Map<String, Object> template)
    {
        return success(autoInspectionService.saveTemplate(template));
    }

    @PreAuthorize("@ss.hasPermi('support:autoInspection:template')")
    @Log(title = "自动化巡检模板", businessType = BusinessType.DELETE)
    @DeleteMapping("/template/{templateId}")
    public AjaxResult deleteTemplate(@PathVariable Long templateId)
    {
        return toAjax(autoInspectionService.deleteTemplateById(templateId));
    }

    @PreAuthorize("@ss.hasPermi('support:autoInspection:run')")
    @Log(title = "自动化巡检模板", businessType = BusinessType.OTHER)
    @PostMapping("/template/run/{templateId}")
    public AjaxResult runTemplate(@PathVariable Long templateId)
    {
        return success(autoInspectionService.runManualTemplate(templateId));
    }

    @PreAuthorize("@ss.hasPermi('support:autoInspection:query')")
    @GetMapping("/record/list")
    public TableDataInfo recordList(@RequestParam Map<String, Object> record)
    {
        startPage();
        List<Map<String, Object>> list = autoInspectionService.selectRecordList(record);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('support:autoInspection:query')")
    @GetMapping("/record/{recordId}")
    public AjaxResult recordDetail(@PathVariable Long recordId)
    {
        return success(autoInspectionService.selectRecordDetail(recordId));
    }

    @PreAuthorize("@ss.hasPermi('support:autoInspection:export')")
    @Log(title = "自动化巡检记录", businessType = BusinessType.EXPORT)
    @PostMapping("/record/export")
    public void export(HttpServletResponse response, @RequestParam Map<String, Object> record)
    {
        autoInspectionService.exportRecord(response, record);
    }
}
