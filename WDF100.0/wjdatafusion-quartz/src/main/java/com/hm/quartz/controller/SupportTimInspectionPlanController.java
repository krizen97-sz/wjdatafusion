package com.hm.quartz.controller;

import java.util.List;
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
import org.springframework.web.bind.annotation.RestController;
import com.hm.common.annotation.Log;
import com.hm.common.core.controller.BaseController;
import com.hm.common.core.domain.AjaxResult;
import com.hm.common.core.page.TableDataInfo;
import com.hm.common.enums.BusinessType;
import com.hm.common.exception.job.TaskException;
import com.hm.manage.domain.SupportTimInspectionPlan;
import com.hm.manage.service.ISupportTimInspectionPlanService;
import com.hm.quartz.service.SupportTimInspectionPlanScheduleService;

@RestController
@RequestMapping("/support/timInspection/plan")
public class SupportTimInspectionPlanController extends BaseController
{
    @Autowired
    private ISupportTimInspectionPlanService planService;

    @Autowired
    private SupportTimInspectionPlanScheduleService planScheduleService;

    @PreAuthorize("@ss.hasPermi('support:timInspection:plan')")
    @GetMapping("/list")
    public TableDataInfo list(SupportTimInspectionPlan plan)
    {
        startPage();
        List<SupportTimInspectionPlan> list = planService.selectPlanList(plan);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('support:timInspection:plan')")
    @GetMapping("/template")
    public AjaxResult template()
    {
        return success(planService.buildPlanTemplate());
    }

    @PreAuthorize("@ss.hasPermi('support:timInspection:plan')")
    @GetMapping("/{planId}")
    public AjaxResult getInfo(@PathVariable Long planId)
    {
        return success(planService.selectPlanById(planId));
    }

    @PreAuthorize("@ss.hasPermi('support:timInspection:plan')")
    @Log(title = "TIM巡检计划", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody SupportTimInspectionPlan plan) throws SchedulerException, TaskException
    {
        return success(planScheduleService.savePlan(plan, getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('support:timInspection:plan')")
    @Log(title = "TIM巡检计划", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SupportTimInspectionPlan plan) throws SchedulerException, TaskException
    {
        return success(planScheduleService.savePlan(plan, getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('support:timInspection:plan')")
    @Log(title = "TIM巡检计划状态", businessType = BusinessType.UPDATE)
    @PutMapping("/changeStatus")
    public AjaxResult changeStatus(@RequestBody SupportTimInspectionPlan plan) throws SchedulerException, TaskException
    {
        return toAjax(planScheduleService.changeStatus(plan.getPlanId(), plan.getStatus(), getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('support:timInspection:run')")
    @Log(title = "TIM巡检计划", businessType = BusinessType.OTHER)
    @PostMapping("/run/{planId}")
    public AjaxResult run(@PathVariable Long planId)
    {
        return success(planScheduleService.runPlanOnce(planId));
    }

    @PreAuthorize("@ss.hasPermi('support:timInspection:plan')")
    @Log(title = "TIM巡检计划", businessType = BusinessType.DELETE)
    @DeleteMapping("/{planId}")
    public AjaxResult remove(@PathVariable Long planId) throws SchedulerException
    {
        return toAjax(planScheduleService.deletePlan(planId));
    }
}
