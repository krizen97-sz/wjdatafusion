package com.hm.manage.controller;

import com.hm.common.annotation.Log;
import com.hm.common.core.controller.BaseController;
import com.hm.common.core.domain.AjaxResult;
import com.hm.common.enums.BusinessType;
import com.hm.manage.service.governance.DataGovernanceArtifactStore;
import com.hm.manage.service.governance.DataGovernanceFtpDelivery;
import com.hm.manage.service.governance.DataGovernanceFtpDelivery.*;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/governance")
public class DataGovernanceDeliveryController extends BaseController
{
    private final DataGovernanceFtpDelivery service;
    private final DataGovernanceArtifactStore artifacts;
    public DataGovernanceDeliveryController(DataGovernanceFtpDelivery service, DataGovernanceArtifactStore artifacts)
    { this.service = service; this.artifacts = artifacts; }
    @GetMapping("/ftp-connections") @PreAuthorize("@ss.hasPermi('governance:flow:edit')")
    public AjaxResult profiles() { return success(service.profiles(getUserId())); }
    @PostMapping("/ftp-connections") @PreAuthorize("@ss.hasPermi('governance:flow:edit')")
    @Log(title="治理FTP连接", businessType=BusinessType.INSERT, isSaveRequestData=false, isSaveResponseData=false)
    public AjaxResult create(@RequestBody ProfileInput input) { return success(service.saveProfile(null, input, getUserId())); }
    @PutMapping("/ftp-connections/{id}") @PreAuthorize("@ss.hasPermi('governance:flow:edit')")
    @Log(title="治理FTP连接", businessType=BusinessType.UPDATE, isSaveRequestData=false, isSaveResponseData=false)
    public AjaxResult update(@PathVariable String id, @RequestBody ProfileInput input) { return success(service.saveProfile(id, input, getUserId())); }
    @PostMapping("/ftp-connections/{id}/test") @PreAuthorize("@ss.hasPermi('governance:flow:edit')")
    @Log(title="治理FTP连接检查", businessType=BusinessType.OTHER, isSaveRequestData=false, isSaveResponseData=false)
    public AjaxResult test(@PathVariable String id) { return success(service.test(id, getUserId())); }
    @GetMapping("/test-runs/{runId}/artifacts") @PreAuthorize("@ss.hasPermi('governance:flow:list')")
    public AjaxResult manifest(@PathVariable String runId) { return success(artifacts.manifest(runId, getUserId())); }
    @GetMapping("/test-runs/{runId}/artifacts/{artifactId}/content") @PreAuthorize("@ss.hasPermi('governance:flow:list')")
    public void content(@PathVariable String runId, @PathVariable String artifactId, jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException
    {
        long owner = getUserId();
        var manifest = artifacts.manifest(runId, owner);
        var artifact = manifest.artifacts().stream().filter(a -> a.id().equals(artifactId)).findFirst()
            .orElseThrow(() -> new com.hm.common.exception.ServiceException("产物不存在或无权访问"));
        // Keep the bounded stream in this authenticated request. The existing JWT chain does not authenticate an async redispatch.
        try (InputStream input = artifacts.read(runId, artifactId, owner))
        {
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            response.setContentLengthLong(artifact.byteSize());
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(artifact.filename(), StandardCharsets.UTF_8).build().toString());
            response.setHeader("X-Content-Type-Options", "nosniff");
            input.transferTo(response.getOutputStream());
        }
    }
    @GetMapping("/deliveries") @PreAuthorize("@ss.hasPermi('governance:flow:list')")
    public AjaxResult jobs(@RequestParam(required=false) String runId) { return success(service.jobs(runId, getUserId())); }
    @GetMapping("/deliveries/{id}") @PreAuthorize("@ss.hasPermi('governance:flow:list')")
    public AjaxResult job(@PathVariable String id) { return success(service.job(id, getUserId())); }
    @PostMapping("/deliveries") @PreAuthorize("@ss.hasPermi('governance:flow:test')")
    @Log(title="治理完整产物交付", businessType=BusinessType.OTHER, isSaveRequestData=false, isSaveResponseData=false)
    public AjaxResult submit(@RequestBody Submit input) { return success(service.submit(input, getUserId())); }
    @PostMapping("/deliveries/{id}/retry") @PreAuthorize("@ss.hasPermi('governance:flow:test')")
    @Log(title="治理交付重试", businessType=BusinessType.OTHER, isSaveRequestData=false, isSaveResponseData=false)
    public AjaxResult retry(@PathVariable String id, @RequestBody Retry input) { return success(service.retry(id, input, getUserId())); }
}
