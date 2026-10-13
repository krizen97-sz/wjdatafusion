package com.hm.manage.controller;

import com.hm.common.core.domain.AjaxResult;
import com.hm.common.utils.SecurityUtils;
import com.hm.manage.service.governance.hdi.*;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Platform history and complete artifacts around the unchanged native task canvas. */
@RestController
@RequestMapping("/governance/hdi")
@ConditionalOnProperty(prefix = "data-governance-hdi-core", name = "enabled", havingValue = "true")
@PreAuthorize("@ss.hasPermi('governance:flow:list')")
public class DataGovernanceHdiCoreRunsController {
    private final HdiTaskStore store;
    private final HdiCoreRuns runs;
    public DataGovernanceHdiCoreRunsController(HdiTaskStore store, HdiCoreRuns runs) { this.store = store; this.runs = runs; }
    public record RunView(String id, String taskId, long revision, String mode, String state, String createdAt, String updatedAt, String error) { }
    @GetMapping("/tasks/{taskId}/runs")
    public AjaxResult list(@PathVariable long taskId, HttpServletResponse response) {
        HdiProtocol.noCache(response);
        List<RunView> list = store.runs(SecurityUtils.getUserId(), taskId).stream().map(run -> new RunView(run.id(), Long.toString(run.taskId()), run.revision(), run.mode(), run.state(), run.createdAt().toString(), run.updatedAt().toString(), run.errorMessage())).toList();
        return AjaxResult.success(list);
    }
    @GetMapping("/runs/{runId}")
    public AjaxResult status(@PathVariable String runId, HttpServletResponse response) {
        HdiProtocol.noCache(response); return nativeData(runs.status(SecurityUtils.getUserId(), runId));
    }
    @GetMapping("/runs/{runId}/artifacts")
    public AjaxResult artifacts(@PathVariable String runId, HttpServletResponse response) {
        HdiProtocol.noCache(response); return nativeData(runs.artifacts(SecurityUtils.getUserId(), runId));
    }
    @GetMapping("/runs/{runId}/logs")
    public AjaxResult logs(@PathVariable String runId, @RequestParam(defaultValue = "0") long cursor, @RequestParam(defaultValue = "32768") int limit, HttpServletResponse response) {
        HdiProtocol.noCache(response); return nativeData(runs.logs(SecurityUtils.getUserId(), runId, cursor, limit));
    }
    private static AjaxResult nativeData(com.fasterxml.jackson.databind.JsonNode node) {
        // Boot 4's Jackson 3 HTTP converter cannot serialize the native SDK's Jackson 2 tree as JSON data.
        return AjaxResult.success(HdiProtocol.JSON.convertValue(node, Object.class));
    }
    @GetMapping("/runs/{runId}/artifacts/{artifactId}/download")
    public ResponseEntity<byte[]> download(@PathVariable String runId, @PathVariable String artifactId) {
        long owner = SecurityUtils.getUserId();
        HdiCoreRuns.ArtifactChunk first = runs.artifact(owner, runId, artifactId, 0, 1024 * 1024);
        if (first.totalBytes() < 0 || first.totalBytes() > 8 * 1024 * 1024) throw new HdiTaskFault(413, "产物超过下载范围");
        // Buffer only the bounded, hash-verified artifact, so a failed later chunk cannot become a partial HTTP 200 download.
        ByteArrayOutputStream output = new ByteArrayOutputStream((int)first.totalBytes());
        HdiCoreRuns.ArtifactChunk chunk = first;
        while (true) {
            if (!artifactId.equals(chunk.artifactId()) || chunk.offset() != output.size() || chunk.nextOffset() != chunk.offset() + chunk.bytes().length
                || chunk.nextOffset() > first.totalBytes() || chunk.totalBytes() != first.totalBytes() || !first.sha256().equals(chunk.sha256())
                || !Objects.equals(first.filename(), chunk.filename()) || !Objects.equals(first.contentType(), chunk.contentType())
                || chunk.eof() != (chunk.nextOffset() == first.totalBytes()) || !chunk.eof() && chunk.bytes().length == 0) throw new HdiTaskFault(502, "产物读取一致性校验失败");
            output.writeBytes(chunk.bytes());
            if (chunk.eof()) break;
            chunk = runs.artifact(owner, runId, artifactId, chunk.nextOffset(), 1024 * 1024);
        }
        byte[] bytes = output.toByteArray();
        if (!first.sha256().equals(HdiTaskStore.sha256(bytes))) throw new HdiTaskFault(502, "完整产物摘要校验失败");
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").header("X-Content-Type-Options", "nosniff")
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(first.filename(), StandardCharsets.UTF_8).build().toString())
            .contentType(MediaType.parseMediaType(first.contentType())).contentLength(bytes.length).body(bytes);
    }
    @ExceptionHandler(HdiTaskFault.class)
    public ResponseEntity<AjaxResult> fault(HdiTaskFault failure) {
        return ResponseEntity.status(failure.status()).header(HttpHeaders.CACHE_CONTROL, "no-store").body(AjaxResult.error(failure.status(), failure.getMessage()));
    }
}
