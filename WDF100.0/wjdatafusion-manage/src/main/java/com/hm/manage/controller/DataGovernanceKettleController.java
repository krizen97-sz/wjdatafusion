package com.hm.manage.controller;

import com.hm.common.annotation.Log;
import com.hm.common.core.controller.BaseController;
import com.hm.common.core.domain.AjaxResult;
import com.hm.common.enums.BusinessType;
import com.hm.manage.service.governance.DataGovernanceKettleService;
import com.hm.manage.service.governance.DataGovernanceKettleService.*;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Native XML travels as Base64, without changing the global XSS/security filters. */
@RestController
@RequestMapping("/governance/kettle")
public class DataGovernanceKettleController extends BaseController
{
    private final DataGovernanceKettleService service;
    public DataGovernanceKettleController(DataGovernanceKettleService service) { this.service = service; }
    @GetMapping("/status") @PreAuthorize("@ss.hasPermi('governance:flow:list')")
    public AjaxResult status() { return success(service.status()); }
    @GetMapping("/catalog") @PreAuthorize("@ss.hasPermi('governance:flow:list')")
    public AjaxResult catalog() { return success(service.catalog()); }
    @GetMapping("/definitions") @PreAuthorize("@ss.hasPermi('governance:flow:list')")
    public AjaxResult definitions() { return success(service.definitions(getUserId())); }
    @PostMapping("/definitions") @PreAuthorize("@ss.hasPermi('governance:flow:edit')")
    @Log(title="创建原生 Kettle 流程", businessType=BusinessType.INSERT, isSaveRequestData=false, isSaveResponseData=false)
    public AjaxResult create(@RequestBody DefinitionInput input) { return success(service.save(null, input, getUserId())); }
    @GetMapping("/definitions/{id}") @PreAuthorize("@ss.hasPermi('governance:flow:list')")
    public AjaxResult definition(@PathVariable String id) { return success(service.definition(id, getUserId())); }
    @PutMapping("/definitions/{id}") @PreAuthorize("@ss.hasPermi('governance:flow:edit')")
    @Log(title="编辑原生 Kettle 流程", businessType=BusinessType.UPDATE, isSaveRequestData=false, isSaveResponseData=false)
    public AjaxResult update(@PathVariable String id, @RequestBody DefinitionInput input) { return success(service.save(id, input, getUserId())); }
    @PostMapping("/imports") @PreAuthorize("@ss.hasPermi('governance:flow:edit')")
    @Log(title="导入原生 Kettle 文件", businessType=BusinessType.INSERT, isSaveRequestData=false, isSaveResponseData=false)
    public AjaxResult imports(@RequestParam("file") MultipartFile file) throws IOException
    { try (var input = file.getInputStream()) { return success(service.imports(file.getOriginalFilename(), input, getUserId())); } }
    @GetMapping("/definitions/{id}/files") @PreAuthorize("@ss.hasPermi('governance:flow:list')")
    public AjaxResult files(@PathVariable String id) { return success(service.files(id, getUserId())); }
    @PostMapping("/definitions/{id}/files") @PreAuthorize("@ss.hasPermi('governance:flow:edit')")
    @Log(title="上传原生流程输入文件", businessType=BusinessType.INSERT, isSaveRequestData=false, isSaveResponseData=false)
    public AjaxResult upload(@PathVariable String id, @RequestParam("file") MultipartFile file) throws IOException
    { try (var input = file.getInputStream()) { return success(service.upload(id, file.getOriginalFilename(), input, getUserId())); } }
    @DeleteMapping("/definitions/{id}/files/{fileId}") @PreAuthorize("@ss.hasPermi('governance:flow:edit')")
    @Log(title="删除原生流程输入文件", businessType=BusinessType.DELETE, isSaveRequestData=false, isSaveResponseData=false)
    public AjaxResult deleteFile(@PathVariable String id, @PathVariable String fileId) { service.deleteFile(id, fileId, getUserId()); return success(); }
    @PostMapping("/definitions/{id}/validate") @PreAuthorize("@ss.hasPermi('governance:flow:test')")
    @Log(title="校验原生 Kettle 流程", businessType=BusinessType.OTHER, isSaveRequestData=false, isSaveResponseData=false)
    public AjaxResult validate(@PathVariable String id) { return success(service.validate(id, getUserId())); }
    @PostMapping("/definitions/{id}/runs") @PreAuthorize("@ss.hasPermi('governance:flow:test')")
    @Log(title="运行原生 Kettle 流程", businessType=BusinessType.OTHER, isSaveRequestData=false, isSaveResponseData=false)
    public AjaxResult submit(@PathVariable String id, @RequestBody RunInput input) { return success(service.submit(id, input, getUserId())); }
    @GetMapping("/runs/{id}") @PreAuthorize("@ss.hasPermi('governance:flow:list')")
    public AjaxResult run(@PathVariable String id) { return success(service.run(id, getUserId())); }
    @GetMapping("/runs/{id}/events") @PreAuthorize("@ss.hasPermi('governance:flow:list')")
    public AjaxResult events(@PathVariable String id, @RequestParam(defaultValue="0") long after) { return success(service.events(id, after, getUserId())); }
    @PostMapping("/runs/{id}/stop") @PreAuthorize("@ss.hasPermi('governance:flow:test')")
    @Log(title="停止原生 Kettle 运行", businessType=BusinessType.OTHER, isSaveRequestData=false, isSaveResponseData=false)
    public AjaxResult stop(@PathVariable String id) { return success(service.stop(id, getUserId())); }
    @GetMapping("/runs/{id}/files/{filename}") @PreAuthorize("@ss.hasPermi('governance:flow:list')")
    public void download(@PathVariable String id, @PathVariable String filename, HttpServletResponse response) throws IOException
    {
        try (var download = service.download(id, filename, getUserId()))
        {
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            if (download.bytes() >= 0) response.setContentLengthLong(download.bytes());
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString());
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store"); response.setHeader("X-Content-Type-Options", "nosniff");
            if (download.partial() != null) response.setHeader("X-Kettle-Partial", download.partial());
            download.stream().transferTo(response.getOutputStream());
        }
    }
}
