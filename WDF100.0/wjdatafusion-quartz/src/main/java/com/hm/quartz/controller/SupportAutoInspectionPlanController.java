package com.hm.quartz.controller;

import java.util.List;
import java.util.Map;
import org.quartz.SchedulerException;
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
import com.hm.common.exception.job.TaskException;
import com.hm.manage.service.ISupportAutoInspectionService;
import com.hm.quartz.service.SupportAutoInspectionPlanScheduleService;

@RestController
@RequestMapping("/support/autoInspection/plan")
public class SupportAutoInspectionPlanController extends BaseController
{
    @Autowired
    private ISupportAutoInspectionService autoInspectionService;

    @Autowired
    private SupportAutoInspectionPlanScheduleService planScheduleService;

    @PreAuthorize("@ss.hasPermi('support:autoInspection:plan')")
    @GetMapping("/list")
    public TableDataInfo list(@RequestParam Map<String, Object> plan)
    {
        startPage();
        List<Map<String, Object>> list = autoInspectionService.selectPlanList(plan);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('support:autoInspection:plan')")
    @GetMapping("/{planId}")
    public AjaxResult getInfo(@PathVariable Long planId)
    {
        return success(autoInspectionService.selectPlanById(planId));
    }

    @PreAuthorize("@ss.hasPermi('support:autoInspection:plan')")
    @Log(title = "自动化巡检计划", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody Map<String, Object> plan) throws SchedulerException, TaskException
    {
        return success(planScheduleService.savePlan(plan, getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('support:autoInspection:plan')")
    @Log(title = "自动化巡检计划", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody Map<String, Object> plan) throws SchedulerException, TaskException
    {
        return success(planScheduleService.savePlan(plan, getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('support:autoInspection:plan')")
    @Log(title = "自动化巡检计划状态", businessType = BusinessType.UPDATE)
    @PutMapping("/changeStatus")
    public AjaxResult changeStatus(@RequestBody Map<String, Object> plan) throws SchedulerException, TaskException
    {
        Long planId = Long.valueOf(String.valueOf(plan.get("planId")));
        return toAjax(planScheduleService.changeStatus(planId, String.valueOf(plan.get("status")), getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('support:autoInspection:run')")
    @Log(title = "自动化巡检计划", businessType = BusinessType.OTHER)
    @PostMapping("/run/{planId}")
    public AjaxResult run(@PathVariable Long planId)
    {
        return success(planScheduleService.runPlanOnce(planId));
    }

    @PreAuthorize("@ss.hasPermi('support:autoInspection:plan')")
    @Log(title = "自动化巡检计划", businessType = BusinessType.DELETE)
    @DeleteMapping("/{planId}")
    public AjaxResult remove(@PathVariable Long planId) throws SchedulerException
    {
        return toAjax(planScheduleService.deletePlan(planId));
    }
}
