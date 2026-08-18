package com.hm.manage.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.hm.common.annotation.Anonymous;
import com.hm.common.annotation.Log;
import com.hm.common.core.controller.BaseController;
import com.hm.common.core.domain.AjaxResult;
import com.hm.common.enums.BusinessType;
import com.hm.common.utils.StringUtils;
import com.hm.common.utils.file.FileUtils;
import com.hm.manage.domain.bo.DocAclSaveBo;
import com.hm.manage.domain.bo.DocCreateBo;
import com.hm.manage.domain.bo.DocFolderSaveBo;
import com.hm.manage.domain.bo.DocFolderReorderBo;
import com.hm.manage.domain.bo.DocQuotaUpdateBo;
import com.hm.manage.domain.bo.DocUpdateBo;
import com.hm.manage.service.IDocumentWorkspaceService;
import com.hm.manage.service.document.DocFileResource;

@RestController
@RequestMapping("/document/workspace")
public class DocumentWorkspaceController extends BaseController
{
    @Autowired
    private IDocumentWorkspaceService workspaceService;

    @PreAuthorize("@ss.hasPermi('document:file:manage')")
    @GetMapping("/folders")
    public AjaxResult folders()
    {
        return success(workspaceService.listFolders());
    }

    @PreAuthorize("@ss.hasPermi('document:file:manage')")
    @GetMapping("/summary")
    public AjaxResult summary()
    {
        return success(workspaceService.getWorkspaceSummary());
    }

    @PreAuthorize("@ss.hasPermi('document:file:manage') and @ss.hasRole('admin')")
    @GetMapping("/admin/storage-users")
    public AjaxResult storageUsers()
    {
        return success(workspaceService.listDocumentStorageUsers());
    }

    @PreAuthorize("@ss.hasPermi('document:file:manage') and @ss.hasRole('admin')")
    @Log(title = "文档用户容量", businessType = BusinessType.UPDATE)
    @PutMapping("/admin/storage-users/{userId}")
    public AjaxResult updateStoragePolicy(@PathVariable Long userId, @RequestBody DocQuotaUpdateBo input)
    {
        return success(workspaceService.updateDocumentStoragePolicy(userId, input));
    }

    @PreAuthorize("@ss.hasPermi('document:file:manage')")
    @Log(title = "文档目录", businessType = BusinessType.INSERT)
    @PostMapping("/folders")
    public AjaxResult createFolder(@RequestBody DocFolderSaveBo input)
    {
        return success(workspaceService.createFolder(input));
    }

    @PreAuthorize("@ss.hasPermi('document:file:manage')")
    @Log(title = "文档目录", businessType = BusinessType.UPDATE)
    @PutMapping("/folders/{folderId}")
    public AjaxResult updateFolder(@PathVariable Long folderId, @RequestBody DocFolderSaveBo input)
    {
        workspaceService.updateFolder(folderId, input);
        return success();
    }

    @PreAuthorize("@ss.hasPermi('document:file:manage')")
    @Log(title = "文档目录排序", businessType = BusinessType.UPDATE)
    @PutMapping("/folders/reorder")
    public AjaxResult reorderFolders(@RequestBody DocFolderReorderBo input)
    {
        workspaceService.reorderFolders(input);
        return success();
    }

    @PreAuthorize("@ss.hasPermi('document:file:manage')")
    @Log(title = "文档目录", businessType = BusinessType.DELETE)
    @DeleteMapping("/folders/{folderId}")
    public AjaxResult removeFolder(@PathVariable Long folderId)
    {
        workspaceService.removeFolder(folderId);
        return success();
    }

    @PreAuthorize("@ss.hasPermi('document:file:manage')")
    @GetMapping("/documents")
    public AjaxResult documents(@RequestParam(defaultValue = "MY") String scope,
        @RequestParam(required = false) Long folderId,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String fileType,
        @RequestParam(required = false) String accessPermission)
    {
        return success(workspaceService.listDocuments(scope, folderId, keyword, fileType, accessPermission));
    }

    @PreAuthorize("@ss.hasPermi('document:file:manage')")
    @GetMapping("/documents/{documentId}")
    public AjaxResult document(@PathVariable Long documentId)
    {
        return success(workspaceService.getDocument(documentId));
    }

    @PreAuthorize("@ss.hasPermi('document:file:manage') and @ss.hasPermi('document:document:add')")
    @Log(title = "在线文档", businessType = BusinessType.INSERT)
    @PostMapping("/documents")
    public AjaxResult createDocument(@RequestBody DocCreateBo input)
    {
        return success(workspaceService.createDocument(input));
    }

    @PreAuthorize("@ss.hasPermi('document:file:manage') and @ss.hasPermi('document:document:add')")
    @Log(title = "复制在线文档", businessType = BusinessType.INSERT)
    @PostMapping("/documents/{documentId}/copy")
    public AjaxResult copyDocument(@PathVariable Long documentId)
    {
        return success(workspaceService.copyDocument(documentId));
    }

    @PreAuthorize("@ss.hasPermi('document:file:manage')")
    @Log(title = "文档文件上传", businessType = BusinessType.IMPORT)
    @PostMapping(value = "/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AjaxResult uploadDocument(@RequestParam("file") MultipartFile file,
        @RequestParam(required = false) Long folderId)
    {
        return success(workspaceService.uploadDocument(file, folderId));
    }

    @PreAuthorize("@ss.hasPermi('document:file:manage') and @ss.hasPermi('document:document:edit')")
    @Log(title = "在线文档", businessType = BusinessType.UPDATE)
    @PutMapping("/documents/{documentId}")
    public AjaxResult updateDocument(@PathVariable Long documentId, @RequestBody DocUpdateBo input)
    {
        workspaceService.updateDocument(documentId, input);
        return success();
    }

    @PreAuthorize("@ss.hasPermi('document:file:manage') and @ss.hasPermi('document:document:remove')")
    @Log(title = "在线文档", businessType = BusinessType.DELETE)
    @DeleteMapping("/documents/{documentId}")
    public AjaxResult trashDocument(@PathVariable Long documentId)
    {
        DocUpdateBo input = new DocUpdateBo();
        input.setLifecycleStatus("TRASH");
        workspaceService.updateDocument(documentId, input);
        return success();
    }

    @PreAuthorize("@ss.hasPermi('document:file:manage')")
    @GetMapping("/documents/{documentId}/collaborators")
    public AjaxResult collaborators(@PathVariable Long documentId)
    {
        return success(workspaceService.listCollaborators(documentId));
    }

    @PreAuthorize("@ss.hasPermi('document:file:manage') and @ss.hasPermi('document:document:share')")
    @Log(title = "文档协作权限", businessType = BusinessType.GRANT)
    @PutMapping("/documents/{documentId}/collaborators")
    public AjaxResult saveCollaborators(@PathVariable Long documentId, @RequestBody DocAclSaveBo input)
    {
        workspaceService.saveCollaborators(documentId, input);
        return success();
    }

    @PreAuthorize("@ss.hasPermi('document:file:manage') and @ss.hasPermi('document:document:share')")
    @GetMapping("/documents/{documentId}/collaborator-candidates")
    public AjaxResult collaboratorCandidates(@PathVariable Long documentId,
        @RequestParam(required = false) String keyword)
    {
        return success(workspaceService.listCollaboratorCandidates(documentId, keyword));
    }

    @PreAuthorize("@ss.hasPermi('document:file:manage')")
    @GetMapping("/documents/{documentId}/versions")
    public AjaxResult versions(@PathVariable Long documentId)
    {
        return success(workspaceService.listVersions(documentId));
    }

    @PreAuthorize("@ss.hasPermi('document:file:manage') and @ss.hasPermi('document:document:share')")
    @GetMapping("/documents/{documentId}/operations")
    public AjaxResult operations(@PathVariable Long documentId)
    {
        return success(workspaceService.listOperations(documentId));
    }

    @PreAuthorize("@ss.hasPermi('document:file:manage')")
    @GetMapping("/documents/{documentId}/editor-config")
    public AjaxResult editorConfig(@PathVariable Long documentId)
    {
        return success(workspaceService.getEditorBootstrap(documentId));
    }

    @PreAuthorize("@ss.hasPermi('document:file:manage') and @ss.hasPermi('document:document:edit')")
    @PostMapping("/documents/{documentId}/force-save")
    public AjaxResult forceSave(@PathVariable Long documentId)
    {
        return success(workspaceService.forceSaveDocument(documentId));
    }

    @PreAuthorize("@ss.hasPermi('document:file:manage')")
    @Log(title = "文档文件", businessType = BusinessType.EXPORT)
    @PostMapping("/documents/{documentId}/download")
    public void download(@PathVariable Long documentId, HttpServletResponse response) throws IOException
    {
        DocFileResource resource = workspaceService.getDownloadFile(documentId);
        writeFile(resource, response, true);
    }

    @PreAuthorize("@ss.hasPermi('document:file:manage')")
    @GetMapping("/documents/{documentId}/preview")
    public void preview(@PathVariable Long documentId, HttpServletResponse response) throws IOException
    {
        DocFileResource resource = workspaceService.getPreviewFile(documentId);
        response.setHeader("Content-Disposition", ContentDisposition.inline()
            .filename(resource.filename(), StandardCharsets.UTF_8).build().toString());
        writeFile(resource, response, false);
    }

    @PreAuthorize("@ss.hasPermi('document:file:manage')")
    @Log(title = "文档文件批量下载", businessType = BusinessType.EXPORT)
    @PostMapping("/documents/batch-download")
    public void batchDownload(@RequestParam("documentIds") List<Long> documentIds,
        HttpServletResponse response) throws IOException
    {
        List<DocFileResource> resources = workspaceService.getBatchDownloadFiles(documentIds);
        response.setContentType("application/zip");
        response.setHeader("Cache-Control", "private, no-store, max-age=0");
        response.setHeader("X-Content-Type-Options", "nosniff");
        String archiveName = "文档批量下载_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
            + ".zip";
        FileUtils.setAttachmentResponseHeader(response, archiveName);
        Set<String> usedNames = new HashSet<>();
        try (ZipOutputStream zip = new ZipOutputStream(response.getOutputStream(), StandardCharsets.UTF_8))
        {
            for (DocFileResource resource : resources)
            {
                ZipEntry entry = new ZipEntry(uniqueZipEntryName(resource.filename(), usedNames));
                entry.setTime(Files.getLastModifiedTime(resource.path()).toMillis());
                zip.putNextEntry(entry);
                try (var input = Files.newInputStream(resource.path()))
                {
                    input.transferTo(zip);
                }
                zip.closeEntry();
            }
            zip.finish();
        }
    }

    @Anonymous
    @GetMapping("/editor/file/{documentId}")
    public void editorFile(@PathVariable Long documentId,
        @RequestParam(name = "access_token") String accessToken,
        HttpServletResponse response) throws IOException
    {
        DocFileResource resource = workspaceService.getEditorFile(documentId, accessToken);
        writeFile(resource, response, false);
    }

    @Anonymous
    @PostMapping("/editor/callback/{documentId}")
    public Map<String, Object> editorCallback(@PathVariable Long documentId,
        @RequestParam(name = "access_token") String accessToken,
        @RequestHeader(value = "AuthorizationJwt", required = false) String authorizationJwt,
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestBody Map<String, Object> payload)
    {
        try
        {
            String outboxToken = StringUtils.isNotBlank(authorizationJwt) ? authorizationJwt : authorization;
            return workspaceService.handleEditorCallback(documentId, accessToken, outboxToken, payload);
        }
        catch (Exception exception)
        {
            logger.error("处理文档编辑器回调失败，documentId={}", documentId, exception);
            return Map.of("error", 1);
        }
    }

    private void writeFile(DocFileResource resource, HttpServletResponse response, boolean attachment) throws IOException
    {
        response.setContentType(contentType(resource.fileType()));
        response.setContentLengthLong(Files.size(resource.path()));
        response.setHeader("Cache-Control", "private, no-store, max-age=0");
        response.setHeader("X-Content-Type-Options", "nosniff");
        if (attachment)
        {
            FileUtils.setAttachmentResponseHeader(response, resource.filename());
        }
        try (var input = Files.newInputStream(resource.path()))
        {
            input.transferTo(response.getOutputStream());
        }
    }

    private String contentType(String fileType)
    {
        if ("docx".equals(fileType))
        {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        if ("xlsx".equals(fileType))
        {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        }
        if ("doc".equals(fileType))
        {
            return "application/msword";
        }
        if ("xls".equals(fileType))
        {
            return "application/vnd.ms-excel";
        }
        if ("zip".equals(fileType))
        {
            return "application/zip";
        }
        if ("rar".equals(fileType))
        {
            return "application/vnd.rar";
        }
        if ("pdf".equals(fileType))
        {
            return MediaType.APPLICATION_PDF_VALUE;
        }
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    private String uniqueZipEntryName(String filename, Set<String> usedNames)
    {
        String safeName = StringUtils.trimToEmpty(filename)
            .replace('/', '_').replace('\\', '_').replaceAll("[\\p{Cntrl}]", "_");
        if (StringUtils.isBlank(safeName) || ".".equals(safeName) || "..".equals(safeName))
        {
            safeName = "文档";
        }
        int extensionIndex = safeName.lastIndexOf('.');
        String baseName = extensionIndex > 0 ? safeName.substring(0, extensionIndex) : safeName;
        String extension = extensionIndex > 0 ? safeName.substring(extensionIndex) : "";
        String candidate = safeName;
        int suffix = 2;
        while (!usedNames.add(candidate.toLowerCase(Locale.ROOT)))
        {
            candidate = baseName + " (" + suffix++ + ")" + extension;
        }
        return candidate;
    }
}
