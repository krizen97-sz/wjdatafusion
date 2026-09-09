package com.hm.manage.controller;

import com.hm.common.annotation.Log;
import com.hm.common.core.controller.BaseController;
import com.hm.common.core.domain.AjaxResult;
import com.hm.common.enums.BusinessType;
import com.hm.manage.service.governance.DataGovernanceEngine;
import com.hm.manage.service.governance.DataGovernanceModels.*;
import com.hm.manage.service.governance.DataGovernanceService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/governance")
public class DataGovernanceController extends BaseController
{
    private final DataGovernanceEngine engine;
    private final DataGovernanceService service;
    public DataGovernanceController(DataGovernanceEngine engine, DataGovernanceService service)
    { this.engine = engine; this.service = service; }

    @GetMapping("/overview") @PreAuthorize("@ss.hasPermi('governance:flow:list')")
    public AjaxResult overview() { return success(engine.overview()); }
    @GetMapping("/catalog") @PreAuthorize("@ss.hasPermi('governance:flow:list')")
    public AjaxResult catalog() { return success(service.catalog()); }
    @GetMapping("/templates") @PreAuthorize("@ss.hasPermi('governance:flow:list')")
    public AjaxResult templates() { return success(service.templates()); }
    @GetMapping("/projects") @PreAuthorize("@ss.hasPermi('governance:flow:list')")
    public AjaxResult projects() { return success(engine.projects()); }
    @PostMapping("/projects") @PreAuthorize("@ss.hasPermi('governance:project:add')")
    @Log(title = "数据治理项目", businessType = BusinessType.INSERT, isSaveRequestData = false, isSaveResponseData = false)
    public AjaxResult project(@RequestBody CreateProject request) { return success(engine.createProject(request)); }
    @GetMapping("/flows") @PreAuthorize("@ss.hasPermi('governance:flow:list')")
    public AjaxResult flows(@RequestParam String projectId) { return success(engine.flows(projectId)); }
    @PostMapping("/flows") @PreAuthorize("@ss.hasPermi('governance:flow:add')")
    @Log(title = "数据治理流程", businessType = BusinessType.INSERT, isSaveRequestData = false, isSaveResponseData = false)
    public AjaxResult flow(@RequestBody CreateFlow request) { return success(engine.createFlow(request)); }
    @PostMapping("/flows/{id}/tests") @PreAuthorize("@ss.hasPermi('governance:flow:test')")
    @Log(title = "数据治理样本测试", businessType = BusinessType.OTHER, isSaveRequestData = false, isSaveResponseData = false)
    public AjaxResult test(@PathVariable String id, @RequestBody TestInput input) { return success(service.submit(id, input, getUserId())); }
    @GetMapping("/test-runs") @PreAuthorize("@ss.hasPermi('governance:flow:list')")
    public AjaxResult runs(@RequestParam(required = false) String flowId) { return success(service.runs(flowId, getUserId())); }
    @GetMapping("/test-runs/{id}") @PreAuthorize("@ss.hasPermi('governance:flow:list')")
    public AjaxResult run(@PathVariable String id) { return success(service.run(id, getUserId())); }
    @PostMapping("/test-runs/{id}/cancel") @PreAuthorize("@ss.hasPermi('governance:flow:test')")
    @Log(title = "取消治理测试", businessType = BusinessType.OTHER, isSaveRequestData = false, isSaveResponseData = false)
    public AjaxResult cancel(@PathVariable String id) { return success(service.cancel(id, getUserId())); }
}
