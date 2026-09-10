package com.hm.manage.controller;

import com.hm.common.annotation.Log;
import com.hm.common.core.controller.BaseController;
import com.hm.common.core.domain.AjaxResult;
import com.hm.common.enums.BusinessType;
import com.hm.manage.service.governance.DataGovernanceKafkaExecution;
import com.hm.manage.service.governance.DataGovernanceKafkaProperties;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/governance")
public class DataGovernanceKafkaExecutionController extends BaseController
{
    private final DataGovernanceKafkaExecution execution;
    private final DataGovernanceKafkaProperties properties;
    public DataGovernanceKafkaExecutionController(DataGovernanceKafkaExecution execution, DataGovernanceKafkaProperties properties) { this.execution = execution; this.properties = properties; }
    @GetMapping("/kafka-status") @PreAuthorize("@ss.hasPermi('governance:flow:list')")
    public AjaxResult status() { return success(java.util.Map.of("enabled", properties.isEnabled(), "ownerId", getUserId(), "maxRecords", 100, "maxInputBytes", 262144, "isolation", "READ_UNCOMMITTED")); }
    @GetMapping("/kafka-receipts/{id}/execution") @PreAuthorize("@ss.hasPermi('governance:flow:list')")
    public AjaxResult view(@PathVariable String id) { return success(execution.view(id, getUserId())); }
    @PostMapping("/kafka-receipts/{id}/execute") @PreAuthorize("@ss.hasPermi('governance:flow:test')")
    @Log(title="Kafka批次执行发布版本", businessType=BusinessType.OTHER, isSaveRequestData=false, isSaveResponseData=false)
    public AjaxResult execute(@PathVariable String id, @RequestBody DataGovernanceKafkaExecution.Request request) { return success(execution.execute(id, request, getUserId())); }
    @PostMapping("/kafka-receipts/{id}/execution/recover") @PreAuthorize("@ss.hasPermi('governance:flow:test')")
    @Log(title="核查Kafka批次执行", businessType=BusinessType.OTHER, isSaveRequestData=false, isSaveResponseData=false)
    public AjaxResult recover(@PathVariable String id) { return success(execution.recover(id, getUserId())); }
}
