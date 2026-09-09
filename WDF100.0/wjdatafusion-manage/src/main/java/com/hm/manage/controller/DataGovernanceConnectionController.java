package com.hm.manage.controller;

import com.hm.common.annotation.Log;
import com.hm.common.core.controller.BaseController;
import com.hm.common.core.domain.AjaxResult;
import com.hm.common.enums.BusinessType;
import com.hm.manage.service.governance.DataGovernanceConnections;
import com.hm.manage.service.governance.DataGovernanceConnections.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/governance/connections")
public class DataGovernanceConnectionController extends BaseController
{
    private final DataGovernanceConnections service;
    public DataGovernanceConnectionController(DataGovernanceConnections service) { this.service = service; }
    @GetMapping @PreAuthorize("@ss.hasPermi('governance:flow:edit')")
    public AjaxResult list() { return success(service.list(getUserId())); }
    @PostMapping @PreAuthorize("@ss.hasPermi('governance:flow:edit')")
    @Log(title="治理数据连接", businessType=BusinessType.INSERT, isSaveRequestData=false, isSaveResponseData=false)
    public AjaxResult create(@RequestBody ProfileInput input) { return success(service.save(null, input, getUserId())); }
    @PutMapping("/{id}") @PreAuthorize("@ss.hasPermi('governance:flow:edit')")
    @Log(title="治理数据连接", businessType=BusinessType.UPDATE, isSaveRequestData=false, isSaveResponseData=false)
    public AjaxResult update(@PathVariable String id, @RequestBody ProfileInput input) { return success(service.save(id, input, getUserId())); }
    @PostMapping("/{id}/test") @PreAuthorize("@ss.hasPermi('governance:flow:edit')")
    @Log(title="治理连接检查", businessType=BusinessType.OTHER, isSaveRequestData=false, isSaveResponseData=false)
    public AjaxResult test(@PathVariable String id) { return success(service.test(id, getUserId())); }
    @PostMapping("/{id}/snapshot") @PreAuthorize("@ss.hasPermi('governance:flow:edit')")
    @Log(title="治理字典快照", businessType=BusinessType.OTHER, isSaveRequestData=false, isSaveResponseData=false)
    public AjaxResult snapshot(@PathVariable String id, @RequestBody SnapshotRequest input) { return success(service.snapshot(id, input, getUserId())); }
}
