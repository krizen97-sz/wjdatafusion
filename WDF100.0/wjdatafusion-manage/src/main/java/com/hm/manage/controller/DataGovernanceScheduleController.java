package com.hm.manage.controller;

import com.hm.common.annotation.Log;
import com.hm.common.core.controller.BaseController;
import com.hm.common.core.domain.AjaxResult;
import com.hm.common.enums.BusinessType;
import com.hm.manage.service.governance.DataGovernanceScheduler;
import com.hm.manage.service.governance.DataGovernanceScheduleModels.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/governance")
public class DataGovernanceScheduleController extends BaseController
{
    private final DataGovernanceScheduler scheduler;
    public DataGovernanceScheduleController(DataGovernanceScheduler scheduler) { this.scheduler = scheduler; }
    @GetMapping("/releases") @PreAuthorize("@ss.hasPermi('governance:flow:list')")
    public AjaxResult releases(@RequestParam(required = false) String flowId) { return success(scheduler.releases(getUserId(), flowId)); }
    @GetMapping("/releases/{id}") @PreAuthorize("@ss.hasPermi('governance:flow:list')")
    public AjaxResult release(@PathVariable String id) { return success(scheduler.release(id, getUserId())); }
    @PostMapping("/releases") @PreAuthorize("@ss.hasPermi('governance:flow:edit')")
    @Log(title = "发布治理固定批次版本", businessType = BusinessType.INSERT, isSaveRequestData = false, isSaveResponseData = false)
    public AjaxResult publish(@RequestBody PublishRequest request) { return success(scheduler.publish(request, getUserId())); }
    @GetMapping("/schedules") @PreAuthorize("@ss.hasPermi('governance:flow:list')")
    public AjaxResult schedules(@RequestParam(required = false) String flowId) { return success(scheduler.schedules(getUserId(), flowId)); }
    @PostMapping("/schedules") @PreAuthorize("@ss.hasPermi('governance:flow:edit')")
    @Log(title = "创建治理固定批次任务", businessType = BusinessType.INSERT, isSaveRequestData = false, isSaveResponseData = false)
    public AjaxResult create(@RequestBody ScheduleRequest request) { return success(scheduler.create(request, getUserId())); }
    @PutMapping("/schedules/{id}") @PreAuthorize("@ss.hasPermi('governance:flow:edit')")
    @Log(title = "修改治理固定批次任务", businessType = BusinessType.UPDATE, isSaveRequestData = false, isSaveResponseData = false)
    public AjaxResult update(@PathVariable String id, @RequestBody ScheduleRequest request) { return success(scheduler.update(id, request, getUserId())); }
    @PostMapping("/schedules/{id}/state") @PreAuthorize("@ss.hasPermi('governance:flow:test')")
    @Log(title = "启停治理固定批次任务", businessType = BusinessType.UPDATE, isSaveRequestData = false, isSaveResponseData = false)
    public AjaxResult state(@PathVariable String id, @RequestBody StateRequest request) { return success(scheduler.state(id, request, getUserId())); }
    @PostMapping("/schedules/{id}/run") @PreAuthorize("@ss.hasPermi('governance:flow:test')")
    @Log(title = "立即运行治理固定批次任务", businessType = BusinessType.OTHER, isSaveRequestData = false, isSaveResponseData = false)
    public AjaxResult run(@PathVariable String id) { return success(scheduler.runNow(id, getUserId())); }
    @PostMapping("/schedules/{id}/recover") @PreAuthorize("@ss.hasPermi('governance:flow:test')")
    @Log(title = "恢复治理固定批次任务", businessType = BusinessType.OTHER, isSaveRequestData = false, isSaveResponseData = false)
    public AjaxResult recover(@PathVariable String id) { return success(scheduler.recover(id, getUserId())); }
}
