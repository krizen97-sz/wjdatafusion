package com.hm.manage.controller;

import com.hm.common.annotation.Log;
import com.hm.common.core.controller.BaseController;
import com.hm.common.core.domain.AjaxResult;
import com.hm.common.enums.BusinessType;
import com.hm.manage.service.governance.DataGovernanceKafkaModels.ProfileRequest;
import com.hm.manage.service.governance.DataGovernanceKafkaService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/governance")
public class DataGovernanceKafkaController extends BaseController
{
    private final DataGovernanceKafkaService kafka;
    public DataGovernanceKafkaController(DataGovernanceKafkaService kafka) { this.kafka = kafka; }
    @GetMapping("/kafka-profiles") @PreAuthorize("@ss.hasPermi('governance:flow:list')")
    public AjaxResult profiles() { return success(kafka.profiles(getUserId())); }
    @PostMapping("/kafka-profiles") @PreAuthorize("@ss.hasPermi('governance:flow:edit')")
    @Log(title = "Kafka 批次源", businessType = BusinessType.INSERT, isSaveRequestData = false, isSaveResponseData = false)
    public AjaxResult create(@RequestBody ProfileRequest request) { return success(kafka.create(request, getUserId())); }
    @PutMapping("/kafka-profiles/{id}") @PreAuthorize("@ss.hasPermi('governance:flow:edit')")
    @Log(title = "Kafka 批次源", businessType = BusinessType.UPDATE, isSaveRequestData = false, isSaveResponseData = false)
    public AjaxResult update(@PathVariable String id, @RequestBody ProfileRequest request) { return success(kafka.update(id, request, getUserId())); }
    @PostMapping("/kafka-profiles/{id}/receive") @PreAuthorize("@ss.hasPermi('governance:flow:test')")
    @Log(title = "接收 Kafka 有界批次", businessType = BusinessType.OTHER, isSaveRequestData = false, isSaveResponseData = false)
    public AjaxResult receive(@PathVariable String id) { return success(kafka.receive(id, getUserId())); }
    @GetMapping("/kafka-receipts") @PreAuthorize("@ss.hasPermi('governance:flow:list')")
    public AjaxResult receipts() { return success(kafka.receipts(getUserId())); }
    @GetMapping("/kafka-receipts/{id}") @PreAuthorize("@ss.hasPermi('governance:flow:list')")
    public AjaxResult receipt(@PathVariable String id) { return success(kafka.read(id, getUserId())); }
    @PostMapping("/kafka-receipts/{id}/commit") @PreAuthorize("@ss.hasPermi('governance:flow:test')")
    @Log(title = "确认 Kafka 批次位点", businessType = BusinessType.OTHER, isSaveRequestData = false, isSaveResponseData = false)
    public AjaxResult commit(@PathVariable String id) { return success(kafka.commit(id, getUserId())); }
    @PostMapping("/kafka-receipts/{id}/release") @PreAuthorize("@ss.hasPermi('governance:flow:test')")
    @Log(title = "释放 Kafka 本地批次租约", businessType = BusinessType.OTHER, isSaveRequestData = false, isSaveResponseData = false)
    public AjaxResult release(@PathVariable String id) { return success(kafka.release(id, getUserId())); }
}
