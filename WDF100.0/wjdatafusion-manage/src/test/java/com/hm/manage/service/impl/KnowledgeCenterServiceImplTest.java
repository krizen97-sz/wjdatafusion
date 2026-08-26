package com.hm.manage.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import com.hm.common.core.domain.entity.SysUser;
import com.hm.common.core.domain.model.LoginUser;
import com.hm.common.exception.ServiceException;
import com.hm.manage.domain.DocDocument;
import com.hm.manage.domain.KbPage;
import com.hm.manage.domain.KbPageVersion;
import com.hm.manage.domain.KbSpace;
import com.hm.manage.domain.bo.KbPageSaveBo;
import com.hm.manage.domain.bo.KbFolderSaveBo;
import com.hm.manage.domain.bo.KbVersionRestoreBo;
import com.hm.manage.domain.vo.KbPageDetailVo;
import com.hm.manage.mapper.KnowledgeCenterMapper;
import com.hm.manage.service.IDocumentWorkspaceService;
import com.hm.manage.service.knowledge.KnowledgeHtmlSanitizer;

class KnowledgeCenterServiceImplTest
{
    @AfterEach
    void clearSecurityContext()
    {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createPageShouldSanitizeContentAndSnapshotExistingDocumentLinks()
    {
        KnowledgeCenterMapper mapper = mock(KnowledgeCenterMapper.class);
        IDocumentWorkspaceService documentService = mock(IDocumentWorkspaceService.class);
        KnowledgeCenterServiceImpl service = service(mapper, documentService);
        setCurrentUser(Set.of("knowledge:page:write", "document:file:manage"));
        when(mapper.selectSpaceById(1L)).thenReturn(activeSpace(1L));
        when(mapper.countSiblingTitle(1L, 0L, "数据库排查", null)).thenReturn(0);
        when(mapper.selectNextPageSortOrder(1L, 0L)).thenReturn(10);
        DocDocument document = accessibleDocument(99L, "巡检附件.docx");
        when(documentService.getDocument(99L)).thenReturn(document);
        AtomicReference<KbPage> saved = new AtomicReference<>();
        when(mapper.insertPage(any(KbPage.class))).thenAnswer(invocation -> {
            KbPage page = invocation.getArgument(0);
            page.setPageId(42L);
            saved.set(page);
            return 1;
        });
        when(mapper.insertTag(anyString(), anyString())).thenReturn(1);
        when(mapper.selectTagId("数据库")).thenReturn(8L);
        when(mapper.insertPageTag(42L, 8L)).thenReturn(1);
        when(mapper.insertPageDocument(42L, 99L, 0, "writer")).thenReturn(1);
        when(mapper.insertPageVersion(any(KbPageVersion.class))).thenReturn(1);
        when(mapper.selectPageById(42L)).thenAnswer(invocation -> saved.get());
        when(mapper.selectPageTags(42L)).thenReturn(List.of("数据库"));
        when(mapper.selectPageDocumentIds(42L)).thenReturn(List.of(99L));

        KbPageSaveBo input = new KbPageSaveBo();
        input.setSpaceId(1L);
        input.setParentId(0L);
        input.setTitle("数据库排查");
        input.setSummary("异常处理");
        input.setContent("<h2>检查</h2><script>alert(1)</script>"
            + "<img src=\"https://tracker.example/pixel\"><img src=\"/profile/local.png\">");
        input.setTagNames(List.of("数据库", "数据库"));
        input.setDocumentIds(List.of(99L, 99L));

        KbPageDetailVo result = service.createPage(input);

        assertEquals(42L, result.getPage().getPageId());
        assertFalse(result.getPage().getContent().contains("script"));
        assertFalse(result.getPage().getContent().contains("tracker.example"));
        assertTrue(result.getPage().getContent().contains("/profile/local.png"));
        assertEquals(List.of("数据库"), result.getTags());
        assertEquals("巡检附件.docx", result.getDocuments().get(0).getTitle());
        ArgumentCaptor<KbPageVersion> version = ArgumentCaptor.forClass(KbPageVersion.class);
        verify(mapper).insertPageVersion(version.capture());
        assertEquals(1, version.getValue().getVersionNo());
        assertEquals("[99]", version.getValue().getSnapshotDocumentIds());
        assertEquals("CREATE", version.getValue().getOperationType());
    }

    @Test
    void updatePageShouldRejectStaleVersionBeforeWritingAnything()
    {
        KnowledgeCenterMapper mapper = mock(KnowledgeCenterMapper.class);
        IDocumentWorkspaceService documentService = mock(IDocumentWorkspaceService.class);
        KnowledgeCenterServiceImpl service = service(mapper, documentService);
        setCurrentUser(Set.of("knowledge:page:write"));
        KbPage current = article(42L, 3);
        when(mapper.selectPageForUpdate(42L)).thenReturn(current);
        KbPageSaveBo input = new KbPageSaveBo();
        input.setExpectedVersion(2);

        ServiceException exception = assertThrows(ServiceException.class, () -> service.updatePage(42L, input));

        assertTrue(exception.getMessage().contains("其他用户更新"));
        verify(mapper, never()).updatePage(any(KbPage.class), anyInt());
    }

    @Test
    void readerWithoutDocumentModulePermissionShouldNotReceiveDocumentMetadata()
    {
        KnowledgeCenterMapper mapper = mock(KnowledgeCenterMapper.class);
        IDocumentWorkspaceService documentService = mock(IDocumentWorkspaceService.class);
        KnowledgeCenterServiceImpl service = service(mapper, documentService);
        setCurrentUser(Set.of("knowledge:page:list"));
        when(mapper.selectPageById(42L)).thenReturn(article(42L, 3));
        when(mapper.selectPageTags(42L)).thenReturn(List.of("数据库"));
        when(mapper.selectPageDocumentIds(42L)).thenReturn(List.of(99L));

        KbPageDetailVo result = service.getPage(42L);

        assertEquals("NO_MODULE_PERMISSION", result.getDocuments().get(0).getAccessStatus());
        assertEquals("无文档访问权限", result.getDocuments().get(0).getTitle());
        verify(documentService, never()).getDocument(anyLong());
    }

    @Test
    void writerWithoutDocumentPermissionShouldPreserveUnchangedDocumentLinks()
    {
        KnowledgeCenterMapper mapper = mock(KnowledgeCenterMapper.class);
        IDocumentWorkspaceService documentService = mock(IDocumentWorkspaceService.class);
        KnowledgeCenterServiceImpl service = service(mapper, documentService);
        setCurrentUser(Set.of("knowledge:page:write"));
        KbPage current = article(42L, 3);
        when(mapper.selectPageForUpdate(42L)).thenReturn(current);
        when(mapper.selectSpaceById(1L)).thenReturn(activeSpace(1L));
        when(mapper.countSiblingTitle(1L, 0L, "数据库排查", 42L)).thenReturn(0);
        when(mapper.selectPageTags(42L)).thenReturn(List.of());
        when(mapper.selectPageDocumentIds(42L)).thenReturn(List.of(99L));
        when(mapper.updatePage(any(KbPage.class), anyInt())).thenReturn(1);
        when(mapper.insertPageDocument(42L, 99L, 0, "writer")).thenReturn(1);
        when(mapper.insertPageVersion(any(KbPageVersion.class))).thenReturn(1);
        when(mapper.selectPageById(42L)).thenReturn(current);

        KbPageSaveBo input = pageInput(3);
        input.setContent("<p>正文补充</p>");
        input.setDocumentIds(List.of(99L));

        KbPageDetailVo result = service.updatePage(42L, input);

        assertEquals(4, result.getPage().getContentVersion());
        assertEquals("NO_MODULE_PERMISSION", result.getDocuments().get(0).getAccessStatus());
        verify(documentService, never()).getDocument(anyLong());
    }

    @Test
    void summaryOnlyUpdateShouldIgnoreQuillEmptyParagraphNoise()
    {
        KnowledgeCenterMapper mapper = mock(KnowledgeCenterMapper.class);
        KnowledgeCenterServiceImpl service = service(mapper, mock(IDocumentWorkspaceService.class));
        setCurrentUser(Set.of("knowledge:page:write"));
        KbPage current = article(42L, 3);
        current.setContent("<h2>排查步骤</h2><p>正文</p>");
        when(mapper.selectPageForUpdate(42L)).thenReturn(current);
        when(mapper.selectSpaceById(1L)).thenReturn(activeSpace(1L));
        when(mapper.countSiblingTitle(1L, 0L, "数据库排查", 42L)).thenReturn(0);
        when(mapper.selectPageTags(42L)).thenReturn(List.of());
        when(mapper.selectPageDocumentIds(42L)).thenReturn(List.of());
        when(mapper.updatePage(any(KbPage.class), anyInt())).thenReturn(1);
        when(mapper.insertPageVersion(any(KbPageVersion.class))).thenReturn(1);
        when(mapper.selectPageById(42L)).thenReturn(current);
        KbPageSaveBo input = pageInput(3);
        input.setSummary("补充摘要");
        input.setContent("<h2>排查步骤</h2>\n<p><br></p>\n<p>正文</p>");

        service.updatePage(42L, input);

        ArgumentCaptor<KbPageVersion> version = ArgumentCaptor.forClass(KbPageVersion.class);
        verify(mapper).insertPageVersion(version.capture());
        assertEquals("<h2>排查步骤</h2><p>正文</p>", version.getValue().getSnapshotContent());
        assertEquals("SUMMARY", version.getValue().getChangeFields());
    }

    @Test
    void restoringHistoricalContentShouldKeepCurrentArchiveStatusAndUnchangedLinks()
    {
        KnowledgeCenterMapper mapper = mock(KnowledgeCenterMapper.class);
        IDocumentWorkspaceService documentService = mock(IDocumentWorkspaceService.class);
        KnowledgeCenterServiceImpl service = service(mapper, documentService);
        setCurrentUser(Set.of("knowledge:page:write"));
        KbPage current = article(42L, 3);
        current.setLifecycleStatus("ARCHIVED");
        KbPageVersion snapshot = new KbPageVersion();
        snapshot.setSnapshotSpaceId(1L);
        snapshot.setSnapshotParentId(0L);
        snapshot.setSnapshotTitle("数据库排查");
        snapshot.setSnapshotSummary("历史摘要");
        snapshot.setSnapshotContent("<p>历史正文</p>");
        snapshot.setSnapshotTags("[]");
        snapshot.setSnapshotDocumentIds("[99]");
        when(mapper.selectPageForUpdate(42L)).thenReturn(current);
        when(mapper.selectPageVersion(42L, 1)).thenReturn(snapshot);
        when(mapper.selectSpaceById(1L)).thenReturn(activeSpace(1L));
        when(mapper.countSiblingTitle(1L, 0L, "数据库排查", 42L)).thenReturn(0);
        when(mapper.selectPageTags(42L)).thenReturn(List.of());
        when(mapper.selectPageDocumentIds(42L)).thenReturn(List.of(99L));
        when(mapper.updatePage(any(KbPage.class), anyInt())).thenReturn(1);
        when(mapper.insertPageDocument(42L, 99L, 0, "writer")).thenReturn(1);
        when(mapper.insertPageVersion(any(KbPageVersion.class))).thenReturn(1);
        when(mapper.selectPageById(42L)).thenReturn(current);

        KbVersionRestoreBo input = new KbVersionRestoreBo();
        input.setExpectedVersion(3);
        KbPageDetailVo result = service.restoreVersion(42L, 1, input);

        assertEquals("ARCHIVED", result.getPage().getLifecycleStatus());
        assertEquals("<p>历史正文</p>", result.getPage().getContent());
        verify(documentService, never()).getDocument(anyLong());
        ArgumentCaptor<KbPageVersion> version = ArgumentCaptor.forClass(KbPageVersion.class);
        verify(mapper).insertPageVersion(version.capture());
        assertEquals("ARCHIVED", version.getValue().getSnapshotLifecycleStatus());
        assertFalse(version.getValue().getChangeFields().contains("STATUS"));
    }

    @Test
    void trashedKnowledgeShouldRequireLifecycleRestoreBeforeContentEditing()
    {
        KnowledgeCenterMapper mapper = mock(KnowledgeCenterMapper.class);
        KnowledgeCenterServiceImpl service = service(mapper, mock(IDocumentWorkspaceService.class));
        setCurrentUser(Set.of("knowledge:page:write", "knowledge:page:remove"));
        KbPage current = article(42L, 3);
        current.setLifecycleStatus("TRASH");
        when(mapper.selectPageForUpdate(42L)).thenReturn(current);

        ServiceException exception = assertThrows(ServiceException.class,
            () -> service.updatePage(42L, pageInput(3)));

        assertTrue(exception.getMessage().contains("必须先恢复"));
        verify(mapper, never()).updatePage(any(KbPage.class), anyInt());
    }

    @Test
    void trashTreeShouldRequireDedicatedRemovePermission()
    {
        KnowledgeCenterMapper mapper = mock(KnowledgeCenterMapper.class);
        KnowledgeCenterServiceImpl service = service(mapper, mock(IDocumentWorkspaceService.class));
        setCurrentUser(Set.of("knowledge:page:list"));
        when(mapper.selectSpaceById(1L)).thenReturn(activeSpace(1L));

        ServiceException exception = assertThrows(ServiceException.class,
            () -> service.listPageTree(1L, "TRASH"));

        assertTrue(exception.getMessage().contains("回收站访问权限"));
    }

    @Test
    void folderShouldNotMoveAcrossSpacesWithoutMovingItsChildren()
    {
        KnowledgeCenterMapper mapper = mock(KnowledgeCenterMapper.class);
        KnowledgeCenterServiceImpl service = service(mapper, mock(IDocumentWorkspaceService.class));
        setCurrentUser(Set.of("knowledge:space:manage"));
        KbPage folder = new KbPage();
        folder.setPageId(10L);
        folder.setSpaceId(1L);
        folder.setPageType("FOLDER");
        when(mapper.selectPageById(10L)).thenReturn(folder);
        when(mapper.selectSpaceById(2L)).thenReturn(activeSpace(2L));
        KbFolderSaveBo input = new KbFolderSaveBo();
        input.setSpaceId(2L);
        input.setTitle("跨空间目录");

        ServiceException exception = assertThrows(ServiceException.class,
            () -> service.updateFolder(10L, input));

        assertTrue(exception.getMessage().contains("不能跨空间"));
        verify(mapper, never()).updateFolder(any(KbPage.class));
    }

    private KnowledgeCenterServiceImpl service(KnowledgeCenterMapper mapper,
        IDocumentWorkspaceService documentService)
    {
        KnowledgeCenterServiceImpl service = new KnowledgeCenterServiceImpl();
        ReflectionTestUtils.setField(service, "mapper", mapper);
        ReflectionTestUtils.setField(service, "documentWorkspaceService", documentService);
        ReflectionTestUtils.setField(service, "htmlSanitizer", new KnowledgeHtmlSanitizer());
        return service;
    }

    private KbSpace activeSpace(Long id)
    {
        KbSpace space = new KbSpace();
        space.setSpaceId(id);
        space.setStatus("0");
        return space;
    }

    private KbPage article(Long id, int version)
    {
        KbPage page = new KbPage();
        page.setPageId(id);
        page.setSpaceId(1L);
        page.setParentId(0L);
        page.setPageType("ARTICLE");
        page.setTitle("数据库排查");
        page.setSummary("");
        page.setContent("<p>正文</p>");
        page.setSortOrder(10);
        page.setContentVersion(version);
        page.setLifecycleStatus("ACTIVE");
        return page;
    }

    private DocDocument accessibleDocument(Long id, String title)
    {
        DocDocument document = new DocDocument();
        document.setDocumentId(id);
        document.setTitle(title);
        document.setFileType("docx");
        document.setDocumentType("word");
        document.setFileSize(1024L);
        document.setContentVersion(2);
        document.setLifecycleStatus("ACTIVE");
        document.setAccessPermission("VIEW");
        return document;
    }

    private KbPageSaveBo pageInput(int expectedVersion)
    {
        KbPageSaveBo input = new KbPageSaveBo();
        input.setSpaceId(1L);
        input.setParentId(0L);
        input.setTitle("数据库排查");
        input.setSummary("");
        input.setContent("<p>正文</p>");
        input.setTagNames(List.of());
        input.setDocumentIds(List.of());
        input.setExpectedVersion(expectedVersion);
        return input;
    }

    private void setCurrentUser(Set<String> permissions)
    {
        SysUser user = new SysUser();
        user.setUserId(7L);
        user.setUserName("writer");
        user.setNickName("知识维护人");
        LoginUser loginUser = new LoginUser(7L, 1L, user, permissions);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities()));
    }
}
