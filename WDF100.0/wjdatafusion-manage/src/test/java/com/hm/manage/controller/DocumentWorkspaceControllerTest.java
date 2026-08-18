package com.hm.manage.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import com.hm.common.annotation.Anonymous;
import com.hm.manage.domain.DocDocument;
import com.hm.manage.service.IDocumentWorkspaceService;
import com.hm.manage.service.document.DocFileResource;

class DocumentWorkspaceControllerTest
{
    @TempDir
    Path tempDirectory;

    @Test
    void authenticatedEndpointsShouldUseRuoyiFileManagementPermission()
    {
        for (Method method : DocumentWorkspaceController.class.getDeclaredMethods())
        {
            boolean mapped = method.isAnnotationPresent(GetMapping.class)
                || method.isAnnotationPresent(PostMapping.class)
                || method.isAnnotationPresent(PutMapping.class)
                || method.isAnnotationPresent(DeleteMapping.class);
            if (!mapped || method.isAnnotationPresent(Anonymous.class))
            {
                continue;
            }
            PreAuthorize permission = method.getAnnotation(PreAuthorize.class);
            assertNotNull(permission, method.getName() + " 必须使用若依接口权限校验");
            assertTrue(permission.value().contains("document:file:manage"),
                method.getName() + " 必须要求文件管理权限");
            assertFalse(permission.value().contains("document:workspace:access"),
                method.getName() + " 不能继续依赖旧入口权限");
        }
    }

    @Test
    void batchDownloadShouldStreamCurrentFilesAndDeduplicateArchiveNames() throws Exception
    {
        Path first = Files.writeString(tempDirectory.resolve("first.docx"), "first-current-file");
        Path second = Files.writeString(tempDirectory.resolve("second.docx"), "second-current-file");
        IDocumentWorkspaceService service = mock(IDocumentWorkspaceService.class);
        when(service.getBatchDownloadFiles(List.of(1L, 2L))).thenReturn(List.of(
            new DocFileResource(first, "项目说明.docx", "docx"),
            new DocFileResource(second, "项目说明.docx", "docx")));
        DocumentWorkspaceController controller = new DocumentWorkspaceController();
        ReflectionTestUtils.setField(controller, "workspaceService", service);
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.batchDownload(List.of(1L, 2L), response);

        assertEquals("application/zip", response.getContentType());
        assertTrue(response.getHeader("Content-Disposition").contains("attachment"));
        List<String> entryNames = new ArrayList<>();
        List<String> contents = new ArrayList<>();
        try (ZipInputStream input = new ZipInputStream(
            new ByteArrayInputStream(response.getContentAsByteArray())))
        {
            for (var entry = input.getNextEntry(); entry != null; entry = input.getNextEntry())
            {
                entryNames.add(entry.getName());
                contents.add(new String(input.readAllBytes()));
            }
        }
        assertEquals(List.of("项目说明.docx", "项目说明 (2).docx"), entryNames);
        assertEquals(List.of("first-current-file", "second-current-file"), contents);
    }

    @Test
    void copyEndpointShouldReturnTheCreatedSameFolderDocument()
    {
        IDocumentWorkspaceService service = mock(IDocumentWorkspaceService.class);
        DocDocument copy = new DocDocument();
        copy.setDocumentId(99L);
        copy.setFolderId(12L);
        copy.setTitle("项目方案(附件一).docx");
        when(service.copyDocument(42L)).thenReturn(copy);
        DocumentWorkspaceController controller = new DocumentWorkspaceController();
        ReflectionTestUtils.setField(controller, "workspaceService", service);

        assertEquals(copy, controller.copyDocument(42L).get("data"));
    }

    @Test
    void pdfPreviewShouldStreamInlineWithThePdfContentType() throws Exception
    {
        Path pdf = Files.writeString(tempDirectory.resolve("preview.pdf"), "%PDF-1.7\npreview");
        IDocumentWorkspaceService service = mock(IDocumentWorkspaceService.class);
        when(service.getPreviewFile(57L)).thenReturn(new DocFileResource(pdf, "项目报告.pdf", "pdf"));
        DocumentWorkspaceController controller = new DocumentWorkspaceController();
        ReflectionTestUtils.setField(controller, "workspaceService", service);
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.preview(57L, response);

        assertEquals("application/pdf", response.getContentType());
        assertTrue(response.getHeader("Content-Disposition").startsWith("inline"));
        assertEquals(Files.readAllBytes(pdf).length, response.getContentAsByteArray().length);
    }
}
