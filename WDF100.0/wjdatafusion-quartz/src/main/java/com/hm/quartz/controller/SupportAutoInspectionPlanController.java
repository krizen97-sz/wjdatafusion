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
import com.hm.manage.domain.SupportAutoInspectionPlan;
import com.hm.manage.domain.bo.AutoInspectionPlanQuery;
import com.hm.manage.domain.bo.AutoInspectionPlanSaveBo;
import com.hm.manage.domain.bo.AutoInspectionPlanStatusBo;
import com.hm.manage.service.ISupportAutoInspectionService;
import com.hm.quartz.service.SupportAutoInspectionPlanScheduleService;

@RestController
@RequestMapping("/support/autoInspection/plans")
public class SupportAutoInspectionPlanController extends BaseController
{
    @Autowired
    private ISupportAutoInspectionService autoInspectionService;

    @Autowired
    private SupportAutoInspectionPlanScheduleService planScheduleService;

    @PreAuthorize("@ss.hasPermi('support:autoInspection:plan')")
    @GetMapping
    public TableDataInfo list(AutoInspectionPlanQuery query)
    {
        startPage();
        List<SupportAutoInspectionPlan> list = autoInspectionService.selectPlanList(query);
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
    public AjaxResult add(@RequestBody AutoInspectionPlanSaveBo plan) throws SchedulerException, TaskException
    {
        return success(planScheduleService.savePlan(plan, getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('support:autoInspection:plan')")
    @Log(title = "自动化巡检计划", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AutoInspectionPlanSaveBo plan) throws SchedulerException, TaskException
    {
        return success(planScheduleService.savePlan(plan, getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('support:autoInspection:plan')")
    @Log(title = "自动化巡检计划状态", businessType = BusinessType.UPDATE)
    @PutMapping("/status")
    public AjaxResult changeStatus(@RequestBody AutoInspectionPlanStatusBo plan) throws SchedulerException, TaskException
    {
        return toAjax(planScheduleService.changeStatus(plan.getPlanId(), plan.getStatus(), getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('support:autoInspection:run')")
    @Log(title = "自动化巡检计划", businessType = BusinessType.OTHER)
    @PostMapping("/{planId}/run")
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
