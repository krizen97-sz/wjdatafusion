package com.hm.manage.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.mock.web.MockMultipartFile;
import com.hm.common.core.domain.entity.SysDept;
import com.hm.common.core.domain.entity.SysRole;
import com.hm.common.core.domain.entity.SysUser;
import com.hm.common.core.domain.model.LoginUser;
import com.hm.common.core.redis.RedisCache;
import com.hm.common.exception.ServiceException;
import com.hm.manage.domain.DocAcl;
import com.hm.manage.domain.DocDocument;
import com.hm.manage.domain.DocFolder;
import com.hm.manage.domain.DocOperationLog;
import com.hm.manage.domain.DocUserQuota;
import com.hm.manage.domain.DocVersion;
import com.hm.manage.domain.bo.DocAclSaveBo;
import com.hm.manage.domain.bo.DocCreateBo;
import com.hm.manage.domain.bo.DocFolderReorderBo;
import com.hm.manage.domain.bo.DocFolderSaveBo;
import com.hm.manage.domain.bo.DocQuotaUpdateBo;
import com.hm.manage.domain.vo.DocUserStorageVo;
import com.hm.manage.domain.vo.DocUserVo;
import com.hm.manage.domain.vo.DocWorkspaceSummaryVo;
import com.hm.manage.config.DocumentManagementProperties;
import com.hm.manage.mapper.DocumentWorkspaceMapper;
import com.hm.manage.service.document.DocFileResource;
import com.hm.manage.service.document.DocumentEditorProvider;
import com.hm.manage.service.document.DocumentStorageService;
import com.hm.system.service.ISysUserService;

class DocumentWorkspaceServiceImplTest
{
    @TempDir
    Path tempDir;

    @AfterEach
    void clearSecurityContext()
    {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rootFolderShouldListAllOwnedDocumentsWithoutApplyingFolderFilter()
    {
        DocumentWorkspaceMapper mapper = mock(DocumentWorkspaceMapper.class);
        DocumentWorkspaceServiceImpl service = new DocumentWorkspaceServiceImpl();
        ReflectionTestUtils.setField(service, "mapper", mapper);
        setCurrentUser(7L, "owner", "文档所有者");

        DocDocument document = ownedDocument(42L, 7L);
        when(mapper.selectDocumentList(7L, "MY", null, "", "", ""))
            .thenReturn(List.of(document));

        List<DocDocument> result = service.listDocuments("MY", 0L, "", "", "");

        assertEquals(List.of(document), result);
        verify(mapper).selectDocumentList(7L, "MY", null, "", "", "");
        verify(mapper, never()).selectFolderById(any(Long.class));
    }

    @Test
    void createFolderShouldAppendToSiblingOrderAndNormalizePresetColor()
    {
        DocumentWorkspaceMapper mapper = mock(DocumentWorkspaceMapper.class);
        DocumentWorkspaceServiceImpl service = new DocumentWorkspaceServiceImpl();
        ReflectionTestUtils.setField(service, "mapper", mapper);
        setCurrentUser(7L, "owner", "文档所有者");
        when(mapper.countSiblingFolder(7L, 0L, "项目资料", null)).thenReturn(0);
        when(mapper.selectNextFolderSortOrder(7L, 0L)).thenReturn(40);
        when(mapper.insertFolder(any(DocFolder.class))).thenAnswer(invocation -> {
            invocation.<DocFolder>getArgument(0).setFolderId(12L);
            return 1;
        });
        DocFolderSaveBo input = new DocFolderSaveBo();
        input.setParentId(0L);
        input.setFolderName("项目资料");
        input.setFolderColor("#2f8f6b");
        input.setSortOrder(-999);

        DocFolder result = service.createFolder(input);

        assertEquals(12L, result.getFolderId());
        assertEquals("#2F8F6B", result.getFolderColor());
        assertEquals(40, result.getSortOrder());
    }

    @Test
    void updateFolderShouldPreserveOrderAndColorWhenLegacyClientOmitsThem()
    {
        DocumentWorkspaceMapper mapper = mock(DocumentWorkspaceMapper.class);
        DocumentWorkspaceServiceImpl service = new DocumentWorkspaceServiceImpl();
        ReflectionTestUtils.setField(service, "mapper", mapper);
        setCurrentUser(7L, "owner", "文档所有者");
        DocFolder current = ownedFolder(12L, 0L, 7L, "旧名称", 0L, 0L);
        current.setSortOrder(37);
        current.setFolderColor("#8A63B8");
        when(mapper.selectFolderById(12L)).thenReturn(current);
        when(mapper.countSiblingFolder(7L, 0L, "新名称", 12L)).thenReturn(0);
        when(mapper.updateFolder(any(DocFolder.class))).thenReturn(1);
        DocFolderSaveBo input = new DocFolderSaveBo();
        input.setParentId(0L);
        input.setFolderName("新名称");

        service.updateFolder(12L, input);

        verify(mapper).updateFolder(argThat(folder -> folder.getSortOrder() == 37
            && "#8A63B8".equals(folder.getFolderColor()) && "新名称".equals(folder.getFolderName())));
    }

    @Test
    void reorderFoldersShouldLockAndPersistTheCompleteSiblingSequence()
    {
        DocumentWorkspaceMapper mapper = mock(DocumentWorkspaceMapper.class);
        DocumentWorkspaceServiceImpl service = new DocumentWorkspaceServiceImpl();
        ReflectionTestUtils.setField(service, "mapper", mapper);
        setCurrentUser(7L, "owner", "文档所有者");
        DocFolder first = ownedFolder(1L, 0L, 7L, "一", 0L, 0L);
        DocFolder second = ownedFolder(2L, 0L, 7L, "二", 0L, 0L);
        DocFolder third = ownedFolder(3L, 0L, 7L, "三", 0L, 0L);
        when(mapper.selectFoldersByOwnerAndParentForUpdate(7L, 0L)).thenReturn(List.of(first, second, third));
        when(mapper.updateFolderSortOrder(anyLong(), eq(7L), eq(0L), any(Integer.class), eq("owner")))
            .thenReturn(1);
        DocFolderReorderBo input = new DocFolderReorderBo();
        input.setParentId(0L);
        input.setFolderIds(List.of(3L, 1L, 2L));

        service.reorderFolders(input);

        verify(mapper).updateFolderSortOrder(3L, 7L, 0L, 10, "owner");
        verify(mapper).updateFolderSortOrder(1L, 7L, 0L, 20, "owner");
        verify(mapper).updateFolderSortOrder(2L, 7L, 0L, 30, "owner");
    }

    @Test
    void reorderFoldersShouldRejectAStaleOrCrossParentFolderSet()
    {
        DocumentWorkspaceMapper mapper = mock(DocumentWorkspaceMapper.class);
        DocumentWorkspaceServiceImpl service = new DocumentWorkspaceServiceImpl();
        ReflectionTestUtils.setField(service, "mapper", mapper);
        setCurrentUser(7L, "owner", "文档所有者");
        when(mapper.selectFoldersByOwnerAndParentForUpdate(7L, 0L)).thenReturn(List.of(
            ownedFolder(1L, 0L, 7L, "一", 0L, 0L),
            ownedFolder(2L, 0L, 7L, "二", 0L, 0L)));
        DocFolderReorderBo input = new DocFolderReorderBo();
        input.setParentId(0L);
        input.setFolderIds(List.of(1L, 9L));

        ServiceException error = assertThrows(ServiceException.class, () -> service.reorderFolders(input));

        assertTrue(error.getMessage().contains("目录结构已发生变化"));
        verify(mapper, never()).updateFolderSortOrder(anyLong(), anyLong(), anyLong(), any(Integer.class), anyString());
    }

    @Test
    void finalSaveShouldRotateEditorKeyAfterAnIdenticalForceSave() throws Exception
    {
        DocumentWorkspaceMapper mapper = mock(DocumentWorkspaceMapper.class);
        DocumentStorageService storage = mock(DocumentStorageService.class);
        DocumentEditorProvider editor = mock(DocumentEditorProvider.class);
        ISysUserService userService = mock(ISysUserService.class);
        DocumentWorkspaceServiceImpl service = new DocumentWorkspaceServiceImpl();
        ReflectionTestUtils.setField(service, "mapper", mapper);
        ReflectionTestUtils.setField(service, "storageService", storage);
        ReflectionTestUtils.setField(service, "editorProvider", editor);
        ReflectionTestUtils.setField(service, "sysUserService", userService);

        DocDocument document = new DocDocument();
        document.setDocumentId(42L);
        document.setFileType("docx");
        document.setStorageKey("documents/42/v3-same.docx");
        document.setChecksum("same-checksum");
        document.setContentVersion(3);
        document.setEditorKey("doc-42-v1");
        Path callbackFile = Files.writeString(tempDir.resolve("callback.docx"), "placeholder");

        when(mapper.selectDocumentRecordForUpdate(42L)).thenReturn(document);
        when(editor.verifyCallback(anyString(), anyString(), same(document), anyMap())).thenReturn(Map.of(
            "key", "doc-42-v1",
            "status", 2,
            "url", "http://onlyoffice-documentserver/cache/final.docx",
            "users", List.of("8")));
        when(userService.selectUserById(8L)).thenReturn(activeUser(8L, "editor", "编辑用户", "研发部"));
        when(storage.createTempFile()).thenReturn(callbackFile);
        when(storage.validateEditorOfficeFile(callbackFile, "docx")).thenReturn("docx");
        when(storage.checksum(callbackFile)).thenReturn("same-checksum");

        Map<String, Object> result = service.handleEditorCallback(42L, "access", "outbox", Map.of());

        assertEquals(0, result.get("error"));
        assertEquals("doc-42-v3", document.getEditorKey());
        assertEquals("编辑用户", document.getUpdateBy());
        verify(mapper).updateDocumentContent(document);
        verify(storage).deleteQuietly(callbackFile);
        verify(mapper, never()).insertOperationLog(any(DocOperationLog.class));
    }

    @Test
    void forceSaveShouldRequireBusinessEditAccessAndReturnQueueState()
    {
        DocumentWorkspaceMapper mapper = mock(DocumentWorkspaceMapper.class);
        DocumentEditorProvider editor = mock(DocumentEditorProvider.class);
        DocumentWorkspaceServiceImpl service = new DocumentWorkspaceServiceImpl();
        ReflectionTestUtils.setField(service, "mapper", mapper);
        ReflectionTestUtils.setField(service, "editorProvider", editor);
        setCurrentUser(7L, "editor", "编辑用户");

        DocDocument editable = ownedDocument(42L, 7L);
        editable.setContentVersion(3);
        when(mapper.selectAccessibleDocument(42L, 7L)).thenReturn(editable);
        when(editor.forceSave(editable)).thenReturn(true);

        Map<String, Object> result = service.forceSaveDocument(42L);

        assertEquals(true, result.get("queued"));
        assertEquals(3, result.get("contentVersion"));
        verify(editor).forceSave(editable);

        DocDocument viewer = ownedDocument(43L, 9L);
        viewer.setAccessPermission("VIEW");
        viewer.setContentVersion(1);
        when(mapper.selectAccessibleDocument(43L, 7L)).thenReturn(viewer);

        assertThrows(ServiceException.class, () -> service.forceSaveDocument(43L));
        verify(editor, never()).forceSave(viewer);
    }

    @Test
    void collaboratorCandidatesShouldContainOfflineActiveUsers() throws Exception
    {
        DocumentWorkspaceMapper mapper = mock(DocumentWorkspaceMapper.class);
        DocumentEditorProvider editor = mock(DocumentEditorProvider.class);
        RedisCache redisCache = mock(RedisCache.class);
        ISysUserService userService = mock(ISysUserService.class);
        DocumentWorkspaceServiceImpl service = new DocumentWorkspaceServiceImpl();
        ReflectionTestUtils.setField(service, "mapper", mapper);
        ReflectionTestUtils.setField(service, "editorProvider", editor);
        ReflectionTestUtils.setField(service, "redisCache", redisCache);
        ReflectionTestUtils.setField(service, "sysUserService", userService);
        setCurrentUser(7L, "owner", "文档所有者");

        DocDocument document = ownedDocument(42L, 7L);
        SysUser onlineUser = activeUser(2L, "online", "在线用户", "研发部");
        DocUserVo onlineCandidate = candidate(2L, "online", "在线用户", "研发部");
        DocUserVo offlineCandidate = candidate(3L, "offline", "离线用户", "财务部");
        when(mapper.selectAccessibleDocument(42L, 7L)).thenReturn(document);
        when(mapper.selectCollaboratorCandidates(7L, 7L, "用户", 100))
            .thenReturn(List.of(onlineCandidate, offlineCandidate));
        when(redisCache.keys("login_tokens:*")).thenReturn(Set.of("login_tokens:online"));
        when(redisCache.<LoginUser>getCacheObject("login_tokens:online"))
            .thenReturn(loginUser(onlineUser));

        List<DocUserVo> result = service.listCollaboratorCandidates(42L, "用户");

        assertEquals(2, result.size());
        Map<Long, DocUserVo> byId = result.stream().collect(Collectors.toMap(DocUserVo::getUserId, item -> item));
        assertTrue(byId.get(2L).getOnline());
        assertFalse(byId.get(3L).getOnline());
        assertEquals("财务部", byId.get(3L).getDeptName());
        verify(userService, never()).selectUserList(any(SysUser.class));
        verify(mapper, never()).countDocumentWorkspaceAccess(anyLong());
    }

    @Test
    void collaboratorSaveShouldApplyDiffAndDropDowngradedEditors()
    {
        DocumentWorkspaceMapper mapper = mock(DocumentWorkspaceMapper.class);
        DocumentEditorProvider editor = mock(DocumentEditorProvider.class);
        ISysUserService userService = mock(ISysUserService.class);
        DocumentWorkspaceServiceImpl service = new DocumentWorkspaceServiceImpl();
        ReflectionTestUtils.setField(service, "mapper", mapper);
        ReflectionTestUtils.setField(service, "editorProvider", editor);
        ReflectionTestUtils.setField(service, "sysUserService", userService);
        setCurrentUser(7L, "owner", "文档所有者");

        DocDocument document = ownedDocument(42L, 7L);
        DocAcl editorAcl = acl(42L, 2L, "EDIT");
        DocAcl viewerAcl = acl(42L, 3L, "VIEW");
        when(mapper.selectAccessibleDocument(42L, 7L)).thenReturn(document);
        when(mapper.countDocumentWorkspaceAccess(anyLong())).thenReturn(1);
        when(mapper.selectAclRecords(42L)).thenReturn(List.of(editorAcl, viewerAcl));
        when(userService.selectUserById(2L)).thenReturn(activeUser(2L, "editor", "原编辑者", "研发部"));
        when(userService.selectUserById(3L)).thenReturn(activeUser(3L, "viewer", "原查看者", "财务部"));
        when(userService.selectUserById(4L)).thenReturn(activeUser(4L, "new", "新协作者", "办公室"));

        DocAclSaveBo input = new DocAclSaveBo();
        input.setEntries(List.of(entry(2L, "VIEW"), entry(4L, "EDIT")));
        service.saveCollaborators(42L, input);

        verify(mapper, never()).deleteAclByDocumentId(42L);
        verify(mapper).updateAcl(argThat(acl -> acl.getUserId().equals(2L) && "VIEW".equals(acl.getPermission())));
        verify(mapper).insertAcl(argThat(acl -> acl.getUserId().equals(4L) && "EDIT".equals(acl.getPermission())));
        verify(mapper).deleteAcl(42L, 3L);
        verify(editor).revokeEditingRights(same(document),
            argThat(ids -> ids.size() == 1 && ids.contains(2L)));

        ArgumentCaptor<DocOperationLog> logCaptor = ArgumentCaptor.forClass(DocOperationLog.class);
        verify(mapper, times(3)).insertOperationLog(logCaptor.capture());
        Set<String> actions = logCaptor.getAllValues().stream()
            .map(DocOperationLog::getActionType).collect(Collectors.toSet());
        assertEquals(Set.of("ACL_CHANGE", "ACL_GRANT", "ACL_REVOKE"), actions);
        DocOperationLog changed = logCaptor.getAllValues().stream()
            .filter(item -> "ACL_CHANGE".equals(item.getActionType())).findFirst().orElseThrow();
        assertEquals("EDIT|PERMANENT", changed.getPreviousValue());
        assertEquals("VIEW|PERMANENT", changed.getCurrentValue());
        assertEquals(2L, changed.getTargetUserId());
        verify(userService, never()).checkUserDataScope(anyLong());
    }

    @Test
    void collaboratorSaveShouldPersistExpirationAsPartOfTheAclDiff()
    {
        DocumentWorkspaceMapper mapper = mock(DocumentWorkspaceMapper.class);
        DocumentEditorProvider editor = mock(DocumentEditorProvider.class);
        ISysUserService userService = mock(ISysUserService.class);
        DocumentWorkspaceServiceImpl service = new DocumentWorkspaceServiceImpl();
        ReflectionTestUtils.setField(service, "mapper", mapper);
        ReflectionTestUtils.setField(service, "editorProvider", editor);
        ReflectionTestUtils.setField(service, "sysUserService", userService);
        setCurrentUser(7L, "owner", "文档所有者");

        DocDocument document = ownedDocument(42L, 7L);
        DocAcl existing = acl(42L, 2L, "EDIT");
        Date expiresAt = new Date(((System.currentTimeMillis() + 86_400_000L) / 1000L) * 1000L);
        when(mapper.selectAccessibleDocument(42L, 7L)).thenReturn(document);
        when(mapper.countDocumentWorkspaceAccess(anyLong())).thenReturn(1);
        when(mapper.selectAclRecords(42L)).thenReturn(List.of(existing));
        when(userService.selectUserById(2L)).thenReturn(activeUser(2L, "editor", "编辑用户", "研发部"));

        DocAclSaveBo input = new DocAclSaveBo();
        input.setEntries(List.of(entry(2L, "EDIT", expiresAt)));
        service.saveCollaborators(42L, input);

        verify(mapper).updateAcl(argThat(acl -> expiresAt.equals(acl.getExpiresAt())));
        verify(editor).revokeEditingRights(same(document), argThat(ids -> ids.isEmpty()));
        ArgumentCaptor<DocOperationLog> logCaptor = ArgumentCaptor.forClass(DocOperationLog.class);
        verify(mapper).insertOperationLog(logCaptor.capture());
        assertTrue(logCaptor.getValue().getCurrentValue().startsWith("EDIT|"));
        assertTrue(logCaptor.getValue().getPreviousValue().endsWith("PERMANENT"));
    }

    @Test
    void expiryTaskShouldDropEditorsAndRemoveExpiredAclRows()
    {
        DocumentWorkspaceMapper mapper = mock(DocumentWorkspaceMapper.class);
        DocumentEditorProvider editor = mock(DocumentEditorProvider.class);
        DocumentWorkspaceServiceImpl service = new DocumentWorkspaceServiceImpl();
        ReflectionTestUtils.setField(service, "mapper", mapper);
        ReflectionTestUtils.setField(service, "editorProvider", editor);

        DocAcl editorAcl = acl(42L, 2L, "EDIT");
        editorAcl.setAclId(12L);
        editorAcl.setExpiresAt(new Date(System.currentTimeMillis() - 1_000L));
        editorAcl.setNickName("到期编辑者");
        DocAcl viewerAcl = acl(42L, 3L, "VIEW");
        viewerAcl.setAclId(13L);
        viewerAcl.setExpiresAt(new Date(System.currentTimeMillis() - 1_000L));
        viewerAcl.setNickName("到期查看者");
        DocDocument document = ownedDocument(42L, 7L);
        when(mapper.selectExpiredAclRecords(100)).thenReturn(List.of(editorAcl, viewerAcl));
        when(mapper.selectDocumentRecord(42L)).thenReturn(document);
        when(mapper.deleteExpiredAcl(12L)).thenReturn(1);
        when(mapper.deleteExpiredAcl(13L)).thenReturn(1);

        assertEquals(2, service.expireCollaboratorPermissions());

        verify(editor).revokeEditingRights(same(document),
            argThat(ids -> ids.size() == 1 && ids.contains(2L)));
        verify(mapper, times(2)).insertOperationLog(argThat(log -> "ACL_EXPIRE".equals(log.getActionType())));
    }

    @Test
    void changedSaveShouldKeepOnlyModifierMetadataAndRemoveTheSupersededCurrentFile() throws Exception
    {
        DocumentWorkspaceMapper mapper = mock(DocumentWorkspaceMapper.class);
        DocumentStorageService storage = mock(DocumentStorageService.class);
        DocumentEditorProvider editor = mock(DocumentEditorProvider.class);
        ISysUserService userService = mock(ISysUserService.class);
        DocumentWorkspaceServiceImpl service = new DocumentWorkspaceServiceImpl();
        ReflectionTestUtils.setField(service, "mapper", mapper);
        ReflectionTestUtils.setField(service, "storageService", storage);
        ReflectionTestUtils.setField(service, "editorProvider", editor);
        ReflectionTestUtils.setField(service, "sysUserService", userService);

        DocDocument document = ownedDocument(42L, 7L);
        document.setFileType("docx");
        document.setContentVersion(3);
        document.setStorageKey("documents/42/v3-old.docx");
        document.setChecksum("old-checksum");
        document.setEditorKey("doc-42-v3");
        Path previousFile = Files.writeString(tempDir.resolve("previous.docx"), "previous");
        Path callbackFile = Files.writeString(tempDir.resolve("callback-new.docx"), "callback");
        Path storedFile = Files.writeString(tempDir.resolve("stored-new.docx"), "stored");
        String nextStorageKey = "documents/42/v4-newchecksum.docx";
        when(mapper.selectDocumentRecordForUpdate(42L)).thenReturn(document);
        when(editor.verifyCallback(anyString(), anyString(), same(document), anyMap())).thenReturn(Map.of(
            "key", "doc-42-v3",
            "status", 6,
            "url", "http://onlyoffice-documentserver/cache/force.docx",
            "users", List.of("8")));
        when(userService.selectUserById(8L)).thenReturn(activeUser(8L, "editor", "编辑用户", "研发部"));
        when(storage.resolve("documents/42/v3-old.docx")).thenReturn(previousFile);
        when(storage.createTempFile()).thenReturn(callbackFile);
        when(storage.validateEditorOfficeFile(callbackFile, "docx")).thenReturn("docx");
        when(storage.checksum(callbackFile)).thenReturn("new-checksum-value");
        when(storage.resolve(nextStorageKey)).thenReturn(storedFile);

        Map<String, Object> result = service.handleEditorCallback(42L, "access", "outbox", Map.of());

        assertEquals(0, result.get("error"));
        assertEquals(4, document.getContentVersion());
        assertEquals(nextStorageKey, document.getStorageKey());
        assertEquals("new-checksum-value", document.getChecksum());
        assertEquals("编辑用户", document.getUpdateBy());
        ArgumentCaptor<DocVersion> versionCaptor = ArgumentCaptor.forClass(DocVersion.class);
        verify(mapper).insertVersion(versionCaptor.capture());
        DocVersion version = versionCaptor.getValue();
        assertEquals(4, version.getVersionNo());
        assertEquals("FORCE_SAVE", version.getSourceType());
        assertEquals(8L, version.getCreatorId());
        assertEquals("编辑用户", version.getCreatorName());
        verify(mapper, never()).insertOperationLog(any(DocOperationLog.class));
        verify(storage).deleteQuietly(previousFile);
        verify(storage, never()).deleteQuietly(storedFile);
    }

    @Test
    void legacySaveShouldUpgradeThePersistedFormatAndTitleToOoxml() throws Exception
    {
        DocumentWorkspaceMapper mapper = mock(DocumentWorkspaceMapper.class);
        DocumentStorageService storage = mock(DocumentStorageService.class);
        DocumentEditorProvider editor = mock(DocumentEditorProvider.class);
        ISysUserService userService = mock(ISysUserService.class);
        DocumentWorkspaceServiceImpl service = new DocumentWorkspaceServiceImpl();
        ReflectionTestUtils.setField(service, "mapper", mapper);
        ReflectionTestUtils.setField(service, "storageService", storage);
        ReflectionTestUtils.setField(service, "editorProvider", editor);
        ReflectionTestUtils.setField(service, "sysUserService", userService);

        DocDocument document = ownedDocument(42L, 7L);
        document.setTitle("现场验收.DOC");
        document.setFileType("doc");
        document.setDocumentType("word");
        document.setContentVersion(1);
        document.setStorageKey("documents/42/v1-old.doc");
        document.setChecksum("old-checksum");
        Path previousFile = Files.writeString(tempDir.resolve("previous.doc"), "previous");
        Path callbackFile = Files.writeString(tempDir.resolve("callback.docx"), "callback");
        Path storedFile = Files.writeString(tempDir.resolve("stored.docx"), "stored");
        String nextStorageKey = "documents/42/v2-newchecksum.docx";
        when(mapper.selectDocumentRecordForUpdate(42L)).thenReturn(document);
        when(editor.verifyCallback(anyString(), anyString(), same(document), anyMap())).thenReturn(Map.of(
            "key", "doc-42-v1",
            "status", 6,
            "url", "http://onlyoffice-documentserver/cache/force.docx",
            "users", List.of("8")));
        when(userService.selectUserById(8L)).thenReturn(activeUser(8L, "editor", "编辑用户", "研发部"));
        when(storage.resolve("documents/42/v1-old.doc")).thenReturn(previousFile);
        when(storage.createTempFile()).thenReturn(callbackFile);
        when(storage.validateEditorOfficeFile(callbackFile, "doc")).thenReturn("docx");
        when(storage.checksum(callbackFile)).thenReturn("new-checksum-value");
        when(storage.resolve(nextStorageKey)).thenReturn(storedFile);

        Map<String, Object> result = service.handleEditorCallback(42L, "access", "outbox", Map.of());

        assertEquals(0, result.get("error"));
        assertEquals("docx", document.getFileType());
        assertEquals("现场验收.docx", document.getTitle());
        assertEquals(nextStorageKey, document.getStorageKey());
        assertEquals(2, document.getContentVersion());
        verify(mapper).updateDocumentContent(document);
        verify(mapper).insertVersion(argThat(version -> "FORCE_SAVE".equals(version.getSourceType())
            && "编辑用户".equals(version.getCreatorName())));
    }

    @Test
    void storageTransitionShouldDeleteOldOnlyAfterCommitAndDiscardNewOnRollback()
    {
        Path previous = tempDir.resolve("previous.docx");
        Path current = tempDir.resolve("current.docx");

        DocumentStorageService committedStorage = mock(DocumentStorageService.class);
        DocumentWorkspaceServiceImpl committedService = new DocumentWorkspaceServiceImpl();
        ReflectionTestUtils.setField(committedService, "storageService", committedStorage);
        TransactionSynchronizationManager.initSynchronization();
        try
        {
            ReflectionTestUtils.invokeMethod(committedService, "scheduleStorageTransition", previous, current);
            verify(committedStorage, never()).deleteQuietly(any(Path.class));
            List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
            synchronizations.forEach(TransactionSynchronization::afterCommit);
            synchronizations.forEach(item -> item.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        }
        finally
        {
            TransactionSynchronizationManager.clearSynchronization();
        }
        verify(committedStorage).deleteQuietly(previous);
        verify(committedStorage, never()).deleteQuietly(current);

        DocumentStorageService rolledBackStorage = mock(DocumentStorageService.class);
        DocumentWorkspaceServiceImpl rolledBackService = new DocumentWorkspaceServiceImpl();
        ReflectionTestUtils.setField(rolledBackService, "storageService", rolledBackStorage);
        TransactionSynchronizationManager.initSynchronization();
        try
        {
            ReflectionTestUtils.invokeMethod(rolledBackService, "scheduleStorageTransition", previous, current);
            List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
            synchronizations.forEach(item -> item.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        }
        finally
        {
            TransactionSynchronizationManager.clearSynchronization();
        }
        verify(rolledBackStorage, never()).deleteQuietly(previous);
        verify(rolledBackStorage).deleteQuietly(current);
    }

    @Test
    void folderNavigationShouldRollChildCountsUpAndReturnCurrentUserStorageSummary()
    {
        DocumentWorkspaceMapper mapper = mock(DocumentWorkspaceMapper.class);
        DocumentWorkspaceServiceImpl service = new DocumentWorkspaceServiceImpl();
        ReflectionTestUtils.setField(service, "mapper", mapper);
        setCurrentUser(7L, "owner", "文档所有者");

        DocFolder rootFolder = ownedFolder(10L, 0L, 7L, "项目资料", 1L, 100L);
        DocFolder childFolder = ownedFolder(11L, 10L, 7L, "实施资料", 2L, 200L);
        DocFolder leafFolder = ownedFolder(12L, 11L, 7L, "验收资料", 3L, 300L);
        when(mapper.selectFoldersByOwner(7L)).thenReturn(List.of(rootFolder, childFolder, leafFolder));
        DocWorkspaceSummaryVo summary = new DocWorkspaceSummaryVo();
        summary.setFileCount(6L);
        summary.setTotalSize(600L);
        summary.setUnfiledCount(0L);
        when(mapper.selectWorkspaceSummary(7L)).thenReturn(summary);
        when(mapper.selectOwnedStorageBytes(7L)).thenReturn(600L);

        List<DocFolder> folders = service.listFolders();
        DocWorkspaceSummaryVo result = service.getWorkspaceSummary();

        assertEquals(6L, folders.get(0).getDocumentCount());
        assertEquals(600L, folders.get(0).getTotalSize());
        assertEquals(5L, folders.get(1).getDocumentCount());
        assertEquals(500L, folders.get(1).getTotalSize());
        assertEquals(3L, folders.get(2).getDocumentCount());
        assertEquals(6L, result.getFileCount());
        assertEquals(600L, result.getTotalSize());
        assertEquals(600L, result.getUsedSize());
        assertEquals(100L * 1024L * 1024L, result.getQuotaSize());
    }

    @Test
    void rootFolderShouldRejectNewUploadedAndCopiedDocuments() throws Exception
    {
        DocumentWorkspaceMapper mapper = mock(DocumentWorkspaceMapper.class);
        DocumentStorageService storage = mock(DocumentStorageService.class);
        DocumentWorkspaceServiceImpl service = new DocumentWorkspaceServiceImpl();
        ReflectionTestUtils.setField(service, "mapper", mapper);
        ReflectionTestUtils.setField(service, "storageService", storage);
        setCurrentUser(7L, "owner", "文档所有者");

        DocCreateBo input = new DocCreateBo();
        input.setFolderId(0L);
        input.setTitle("根目录文档");
        input.setFileType("docx");
        ServiceException createFailure = assertThrows(ServiceException.class, () -> service.createDocument(input));
        assertTrue(createFailure.getMessage().contains("根目录"));

        MockMultipartFile upload = new MockMultipartFile("file", "根目录文档.docx",
            "application/octet-stream", "content".getBytes());
        ServiceException uploadFailure = assertThrows(ServiceException.class, () -> service.uploadDocument(upload, 0L));
        assertTrue(uploadFailure.getMessage().contains("根目录"));

        DocDocument legacyRootDocument = ownedDocument(91L, 7L);
        legacyRootDocument.setFolderId(0L);
        when(mapper.selectAccessibleDocument(91L, 7L)).thenReturn(legacyRootDocument);
        ServiceException copyFailure = assertThrows(ServiceException.class, () -> service.copyDocument(91L));
        assertTrue(copyFailure.getMessage().contains("根目录"));
        verify(mapper, never()).insertDocument(any(DocDocument.class));
        verify(storage, never()).copyUploadToTemp(upload);
    }

    @Test
    void copyShouldCreateAnIndependentCurrentFileInTheSameFolder() throws Exception
    {
        DocumentWorkspaceMapper mapper = mock(DocumentWorkspaceMapper.class);
        DocumentStorageService storage = mock(DocumentStorageService.class);
        DocumentWorkspaceServiceImpl service = new DocumentWorkspaceServiceImpl();
        ReflectionTestUtils.setField(service, "mapper", mapper);
        ReflectionTestUtils.setField(service, "storageService", storage);
        setCurrentUser(7L, "owner", "文档所有者");

        DocDocument source = ownedDocument(42L, 7L);
        source.setFolderId(12L);
        source.setTitle("项目方案.docx");
        source.setFileType("docx");
        source.setDocumentType("word");
        source.setStorageKey("documents/42/v3-source.docx");
        source.setChecksum("abcdef1234567890");
        Path sourceFile = Files.writeString(tempDir.resolve("source.docx"), "source-content");
        Path copiedFile = Files.writeString(tempDir.resolve("copied.docx"), "source-content");
        when(mapper.selectAccessibleDocument(42L, 7L)).thenReturn(source);
        when(mapper.selectFolderById(12L)).thenReturn(ownedFolder(12L, 0L, 7L, "项目资料", 1L, 10L));
        when(mapper.countDocumentTitle(7L, 12L, "项目方案(附件一).docx")).thenReturn(0);
        when(storage.resolve(source.getStorageKey())).thenReturn(sourceFile);
        doAnswer(invocation -> {
            DocDocument copy = invocation.getArgument(0);
            copy.setDocumentId(99L);
            return 1;
        }).when(mapper).insertDocument(any(DocDocument.class));
        String copyStorageKey = "documents/99/v1-abcdef123456.docx";
        when(storage.copyIntoStorage(sourceFile, copyStorageKey)).thenReturn(copiedFile);

        DocDocument result = service.copyDocument(42L);

        assertEquals(99L, result.getDocumentId());
        assertEquals(12L, result.getFolderId());
        assertEquals("项目方案(附件一).docx", result.getTitle());
        assertEquals("ACTIVE", result.getLifecycleStatus());
        assertEquals(source.getChecksum(), result.getChecksum());
        verify(mapper).updateDocumentContent(result);
        verify(mapper).insertVersion(argThat(version -> "COPY".equals(version.getSourceType())
            && version.getVersionNo() == 1));
        verify(mapper).insertOperationLog(argThat(operation -> "COPY".equals(operation.getActionType())));
    }

    @Test
    void uploadShouldValidateBeforeCreatingTheDocumentAndKeepOnlyLightweightVersionMetadata() throws Exception
    {
        DocumentWorkspaceMapper mapper = mock(DocumentWorkspaceMapper.class);
        DocumentStorageService storage = mock(DocumentStorageService.class);
        DocumentWorkspaceServiceImpl service = new DocumentWorkspaceServiceImpl();
        ReflectionTestUtils.setField(service, "mapper", mapper);
        ReflectionTestUtils.setField(service, "storageService", storage);
        setCurrentUser(7L, "owner", "文档所有者");

        MockMultipartFile upload = new MockMultipartFile("file", "现场台账.xls", "application/vnd.ms-excel",
            "legacy-workbook".getBytes());
        Path temporary = Files.writeString(tempDir.resolve("upload.tmp"), "legacy-workbook");
        Path stored = Files.writeString(tempDir.resolve("stored.xls"), "legacy-workbook");
        when(storage.copyUploadToTemp(same(upload), anyLong())).thenReturn(temporary);
        when(storage.validateUploadedOfficeFile(temporary, "xls"))
            .thenReturn(new DocumentStorageService.UploadValidationResult(List.of("旧格式兼容提醒")));
        when(storage.checksum(temporary)).thenReturn("abcdef1234567890");
        doAnswer(invocation -> {
            DocDocument document = invocation.getArgument(0);
            document.setDocumentId(55L);
            return 1;
        }).when(mapper).insertDocument(any(DocDocument.class));
        when(storage.resolve("documents/55/v1-abcdef123456.xls")).thenReturn(stored);

        when(mapper.selectFolderById(12L)).thenReturn(ownedFolder(12L, 0L, 7L, "上传资料", 0L, 0L));

        Map<String, Object> result = service.uploadDocument(upload, 12L);

        DocDocument document = (DocDocument) result.get("document");
        assertEquals(55L, document.getDocumentId());
        assertEquals("现场台账.xls", document.getTitle());
        assertEquals("xls", document.getFileType());
        assertEquals("cell", document.getDocumentType());
        assertEquals(List.of("旧格式兼容提醒"), result.get("warnings"));
        ArgumentCaptor<DocVersion> versionCaptor = ArgumentCaptor.forClass(DocVersion.class);
        verify(mapper).insertVersion(versionCaptor.capture());
        assertEquals("UPLOAD", versionCaptor.getValue().getSourceType());
        assertEquals("文档所有者", versionCaptor.getValue().getCreatorName());
        verify(storage).validateUploadedOfficeFile(temporary, "xls");
        verify(mapper).insertDocument(document);
    }

    @Test
    void failedUploadValidationShouldNotCreateDatabaseOrPermanentStorageRecords() throws Exception
    {
        DocumentWorkspaceMapper mapper = mock(DocumentWorkspaceMapper.class);
        DocumentStorageService storage = mock(DocumentStorageService.class);
        DocumentWorkspaceServiceImpl service = new DocumentWorkspaceServiceImpl();
        ReflectionTestUtils.setField(service, "mapper", mapper);
        ReflectionTestUtils.setField(service, "storageService", storage);
        setCurrentUser(7L, "owner", "文档所有者");

        MockMultipartFile upload = new MockMultipartFile("file", "伪装文档.docx", "application/octet-stream",
            "not-docx".getBytes());
        Path temporary = Files.writeString(tempDir.resolve("invalid.tmp"), "not-docx");
        when(storage.copyUploadToTemp(same(upload), anyLong())).thenReturn(temporary);
        doThrow(new ServiceException("文件实际内容与 .DOCX 扩展名不一致"))
            .when(storage).validateUploadedOfficeFile(temporary, "docx");

        when(mapper.selectFolderById(12L)).thenReturn(ownedFolder(12L, 0L, 7L, "上传资料", 0L, 0L));

        ServiceException exception = assertThrows(ServiceException.class, () -> service.uploadDocument(upload, 12L));

        assertTrue(exception.getMessage().contains("实际内容"));
        verify(mapper, never()).insertDocument(any(DocDocument.class));
        verify(storage, never()).moveIntoStorage(any(Path.class), anyString());
        verify(storage).deleteQuietly(temporary);
    }

    @Test
    void archiveUploadShouldUseTransferValidationAndNeverBecomeAnEditorDocument() throws Exception
    {
        DocumentWorkspaceMapper mapper = mock(DocumentWorkspaceMapper.class);
        DocumentStorageService storage = mock(DocumentStorageService.class);
        DocumentWorkspaceServiceImpl service = new DocumentWorkspaceServiceImpl();
        ReflectionTestUtils.setField(service, "mapper", mapper);
        ReflectionTestUtils.setField(service, "storageService", storage);
        setCurrentUser(7L, "owner", "文档所有者");

        MockMultipartFile upload = new MockMultipartFile("file", "现场资料.zip", "application/zip",
            "archive-content".getBytes());
        Path temporary = Files.writeString(tempDir.resolve("archive.tmp"), "archive-content");
        Path stored = Files.writeString(tempDir.resolve("stored.zip"), "archive-content");
        when(storage.copyUploadToTemp(same(upload), anyLong())).thenReturn(temporary);
        when(storage.validateUploadedArchiveFile(temporary, "zip"))
            .thenReturn(new DocumentStorageService.UploadValidationResult(List.of()));
        when(storage.checksum(temporary)).thenReturn("0123456789abcdef");
        when(mapper.selectFolderById(12L)).thenReturn(ownedFolder(12L, 0L, 7L, "压缩资料", 0L, 0L));
        doAnswer(invocation -> {
            DocDocument document = invocation.getArgument(0);
            document.setDocumentId(56L);
            return 1;
        }).when(mapper).insertDocument(any(DocDocument.class));
        when(storage.resolve("documents/56/v1-0123456789ab.zip")).thenReturn(stored);

        Map<String, Object> result = service.uploadDocument(upload, 12L);

        DocDocument document = (DocDocument) result.get("document");
        assertEquals("zip", document.getFileType());
        assertEquals("archive", document.getDocumentType());
        verify(storage).validateUploadedArchiveFile(temporary, "zip");
        verify(storage, never()).validateUploadedOfficeFile(any(Path.class), anyString());
    }

    @Test
    void pdfUploadShouldUsePdfValidationAndRemainPreviewOnly() throws Exception
    {
        DocumentWorkspaceMapper mapper = mock(DocumentWorkspaceMapper.class);
        DocumentStorageService storage = mock(DocumentStorageService.class);
        DocumentWorkspaceServiceImpl service = new DocumentWorkspaceServiceImpl();
        ReflectionTestUtils.setField(service, "mapper", mapper);
        ReflectionTestUtils.setField(service, "storageService", storage);
        setCurrentUser(7L, "owner", "文档所有者");

        MockMultipartFile upload = new MockMultipartFile("file", "项目报告.pdf", "application/pdf",
            "%PDF-1.7".getBytes());
        Path temporary = Files.writeString(tempDir.resolve("pdf.tmp"), "%PDF-1.7");
        Path stored = Files.writeString(tempDir.resolve("stored.pdf"), "%PDF-1.7");
        when(storage.copyUploadToTemp(same(upload), anyLong())).thenReturn(temporary);
        when(storage.validateUploadedPdfFile(temporary))
            .thenReturn(new DocumentStorageService.UploadValidationResult(List.of()));
        when(storage.checksum(temporary)).thenReturn("fedcba9876543210");
        when(mapper.selectFolderById(12L)).thenReturn(ownedFolder(12L, 0L, 7L, "PDF资料", 0L, 0L));
        doAnswer(invocation -> {
            DocDocument document = invocation.getArgument(0);
            document.setDocumentId(57L);
            return 1;
        }).when(mapper).insertDocument(any(DocDocument.class));
        when(storage.resolve("documents/57/v1-fedcba987654.pdf")).thenReturn(stored);

        Map<String, Object> result = service.uploadDocument(upload, 12L);

        DocDocument document = (DocDocument) result.get("document");
        assertEquals("pdf", document.getFileType());
        assertEquals("pdf", document.getDocumentType());
        verify(storage).validateUploadedPdfFile(temporary);
        verify(storage, never()).validateUploadedOfficeFile(any(Path.class), anyString());
        verify(storage, never()).validateUploadedArchiveFile(any(Path.class), anyString());
    }

    @Test
    void pdfShouldBeReadableThroughPreviewButRejectEditingAndEditSharing() throws Exception
    {
        DocumentWorkspaceMapper mapper = mock(DocumentWorkspaceMapper.class);
        DocumentStorageService storage = mock(DocumentStorageService.class);
        DocumentEditorProvider editor = mock(DocumentEditorProvider.class);
        DocumentWorkspaceServiceImpl service = new DocumentWorkspaceServiceImpl();
        ReflectionTestUtils.setField(service, "mapper", mapper);
        ReflectionTestUtils.setField(service, "storageService", storage);
        ReflectionTestUtils.setField(service, "editorProvider", editor);
        setCurrentUser(7L, "owner", "文档所有者");

        DocDocument pdf = ownedDocument(57L, 7L);
        pdf.setTitle("项目报告.pdf");
        pdf.setFileType("pdf");
        pdf.setStorageKey("documents/57/current.pdf");
        Path stored = Files.writeString(tempDir.resolve("current.pdf"), "%PDF-1.7");
        when(mapper.selectAccessibleDocument(57L, 7L)).thenReturn(pdf);
        when(storage.resolve(pdf.getStorageKey())).thenReturn(stored);

        DocFileResource preview = service.getPreviewFile(57L);
        DocAclSaveBo input = new DocAclSaveBo();
        input.setEntries(List.of(entry(2L, "EDIT")));

        assertEquals(stored, preview.path());
        assertEquals("pdf", preview.fileType());
        assertThrows(ServiceException.class, () -> service.getEditorBootstrap(57L));
        assertThrows(ServiceException.class, () -> service.forceSaveDocument(57L));
        ServiceException shareError = assertThrows(ServiceException.class,
            () -> service.saveCollaborators(57L, input));
        assertTrue(shareError.getMessage().contains("共享查看权限"));
        verify(editor, never()).buildEditorBootstrap(any(DocDocument.class), any(LoginUser.class), anyBoolean());
        verify(editor, never()).forceSave(any(DocDocument.class));
    }

    @Test
    void uploadShouldRejectAFileThatWouldExceedTheOwnersQuota() throws Exception
    {
        DocumentWorkspaceMapper mapper = mock(DocumentWorkspaceMapper.class);
        DocumentStorageService storage = mock(DocumentStorageService.class);
        DocumentWorkspaceServiceImpl service = new DocumentWorkspaceServiceImpl();
        ReflectionTestUtils.setField(service, "mapper", mapper);
        ReflectionTestUtils.setField(service, "storageService", storage);
        setCurrentUser(7L, "owner", "文档所有者");

        MockMultipartFile upload = new MockMultipartFile("file", "增量.zip", "application/zip", "1234".getBytes());
        Path temporary = Files.writeString(tempDir.resolve("quota.tmp"), "1234");
        DocUserQuota quota = new DocUserQuota();
        quota.setUserId(7L);
        quota.setQuotaBytes(10L);
        quota.setMaxUploadBytes(100L * 1024L * 1024L);
        when(mapper.selectQuotaForUpdate(7L)).thenReturn(quota);
        when(mapper.selectOwnedStorageBytes(7L)).thenReturn(8L);
        when(mapper.selectFolderById(12L)).thenReturn(ownedFolder(12L, 0L, 7L, "压缩资料", 0L, 0L));
        when(storage.copyUploadToTemp(same(upload), anyLong())).thenReturn(temporary);
        when(storage.validateUploadedArchiveFile(temporary, "zip"))
            .thenReturn(new DocumentStorageService.UploadValidationResult(List.of()));

        ServiceException exception = assertThrows(ServiceException.class, () -> service.uploadDocument(upload, 12L));

        assertTrue(exception.getMessage().contains("可用空间不足"));
        verify(mapper, never()).insertDocument(any(DocDocument.class));
        verify(storage).deleteQuietly(temporary);
    }

    @Test
    void archiveSharingShouldRejectEditPermission()
    {
        DocumentWorkspaceMapper mapper = mock(DocumentWorkspaceMapper.class);
        DocumentEditorProvider editor = mock(DocumentEditorProvider.class);
        DocumentWorkspaceServiceImpl service = new DocumentWorkspaceServiceImpl();
        ReflectionTestUtils.setField(service, "mapper", mapper);
        ReflectionTestUtils.setField(service, "editorProvider", editor);
        setCurrentUser(7L, "owner", "文档所有者");
        DocDocument archive = ownedDocument(77L, 7L);
        archive.setFileType("rar");
        when(mapper.selectAccessibleDocument(77L, 7L)).thenReturn(archive);
        DocAclSaveBo input = new DocAclSaveBo();
        input.setEntries(List.of(entry(2L, "EDIT")));

        ServiceException exception = assertThrows(ServiceException.class,
            () -> service.saveCollaborators(77L, input));

        assertTrue(exception.getMessage().contains("仅支持共享下载权限"));
        verify(mapper, never()).insertAcl(any(DocAcl.class));
    }

    @Test
    void adminScopeShouldListEveryOwnersFileWithoutGrantingOwnerMutations()
    {
        DocumentWorkspaceMapper mapper = mock(DocumentWorkspaceMapper.class);
        DocumentWorkspaceServiceImpl service = new DocumentWorkspaceServiceImpl();
        ReflectionTestUtils.setField(service, "mapper", mapper);
        setAdminCurrentUser(1L, "admin", "管理员");
        DocDocument otherOwnersFile = ownedDocument(88L, 9L);
        otherOwnersFile.setAccessPermission(null);
        when(mapper.selectDocumentList(1L, "ADMIN_ALL", null, "", "", ""))
            .thenReturn(List.of(otherOwnersFile));

        List<DocDocument> result = service.listDocuments("ADMIN_ALL", null, "", "", "");

        assertEquals(1, result.size());
        assertEquals("ADMIN", result.get(0).getAccessPermission());
    }

    @Test
    void adminShouldUpdateQuotaOnlyForDocumentWorkspaceUsers()
    {
        DocumentWorkspaceMapper mapper = mock(DocumentWorkspaceMapper.class);
        DocumentWorkspaceServiceImpl service = new DocumentWorkspaceServiceImpl();
        ReflectionTestUtils.setField(service, "mapper", mapper);
        setAdminCurrentUser(1L, "admin", "管理员");
        long megabyte = 1024L * 1024L;
        DocUserQuota quota = new DocUserQuota();
        quota.setUserId(2L);
        quota.setQuotaBytes(100L * megabyte);
        quota.setMaxUploadBytes(100L * megabyte);
        DocUserStorageVo user = new DocUserStorageVo();
        user.setUserId(2L);
        user.setUserName("ry");
        user.setUsedSize(20L * megabyte);
        user.setFileCount(2L);
        user.setQuotaSize(500L * megabyte);
        user.setMaxUploadSize(50L * megabyte);
        when(mapper.countDocumentWorkspaceAccess(2L)).thenReturn(1);
        when(mapper.selectQuotaForUpdate(2L)).thenReturn(quota);
        when(mapper.selectOwnedStorageBytes(2L)).thenReturn(20L * megabyte);
        when(mapper.updateQuota(2L, 500L * megabyte, 50L * megabyte, "admin")).thenReturn(1);
        when(mapper.selectDocumentStorageUsers()).thenReturn(List.of(user));
        DocQuotaUpdateBo input = new DocQuotaUpdateBo();
        input.setQuotaMb(500L);
        input.setMaxUploadMb(50L);

        DocUserStorageVo result = service.updateDocumentStoragePolicy(2L, input);

        assertEquals(20L * megabyte, result.getUsedSize());
        assertEquals(500L * megabyte, result.getQuotaSize());
        assertEquals(50L * megabyte, result.getMaxUploadSize());
        assertEquals(4D, result.getUsagePercent());
    }

    @Test
    void adminShouldNotApplyOrdinaryQuotaPolicyToAnotherAdminAccount()
    {
        DocumentWorkspaceMapper mapper = mock(DocumentWorkspaceMapper.class);
        DocumentWorkspaceServiceImpl service = new DocumentWorkspaceServiceImpl();
        ReflectionTestUtils.setField(service, "mapper", mapper);
        setAdminCurrentUser(1L, "admin", "管理员");
        when(mapper.countDocumentWorkspaceAccess(9L)).thenReturn(1);
        when(mapper.countDocumentAdminRole(9L)).thenReturn(1);
        DocQuotaUpdateBo input = new DocQuotaUpdateBo();
        input.setQuotaMb(500L);
        input.setMaxUploadMb(50L);

        ServiceException exception = assertThrows(ServiceException.class,
            () -> service.updateDocumentStoragePolicy(9L, input));

        assertTrue(exception.getMessage().contains("总权限账号"));
        verify(mapper, never()).updateQuota(anyLong(), anyLong(), anyLong(), anyString());
    }

    @Test
    void batchDownloadShouldDeduplicateIdsAndResolveOnlyReadableCurrentFiles() throws Exception
    {
        DocumentWorkspaceMapper mapper = mock(DocumentWorkspaceMapper.class);
        DocumentStorageService storage = mock(DocumentStorageService.class);
        DocumentManagementProperties properties = new DocumentManagementProperties();
        DocumentWorkspaceServiceImpl service = new DocumentWorkspaceServiceImpl();
        ReflectionTestUtils.setField(service, "mapper", mapper);
        ReflectionTestUtils.setField(service, "storageService", storage);
        ReflectionTestUtils.setField(service, "documentProperties", properties);
        setCurrentUser(7L, "owner", "文档所有者");

        DocDocument word = ownedDocument(41L, 7L);
        word.setTitle("同名.docx");
        word.setFileType("docx");
        word.setStorageKey("documents/41/current.docx");
        DocDocument sheet = ownedDocument(42L, 9L);
        sheet.setAccessPermission("VIEW");
        sheet.setTitle("同名.xlsx");
        sheet.setFileType("xlsx");
        sheet.setStorageKey("documents/42/current.xlsx");
        Path wordPath = Files.writeString(tempDir.resolve("current.docx"), "word");
        Path sheetPath = Files.writeString(tempDir.resolve("current.xlsx"), "sheet");
        when(mapper.selectAccessibleDocument(41L, 7L)).thenReturn(word);
        when(mapper.selectAccessibleDocument(42L, 7L)).thenReturn(sheet);
        when(storage.resolve(word.getStorageKey())).thenReturn(wordPath);
        when(storage.resolve(sheet.getStorageKey())).thenReturn(sheetPath);

        List<DocFileResource> result = service.getBatchDownloadFiles(List.of(41L, 42L, 41L));

        assertEquals(2, result.size());
        assertEquals(wordPath, result.get(0).path());
        assertEquals(sheetPath, result.get(1).path());
        verify(mapper, times(1)).selectAccessibleDocument(41L, 7L);
    }

    @Test
    void batchDownloadShouldRejectTheWholeRequestWhenAnyDocumentIsNotReadable()
    {
        DocumentWorkspaceMapper mapper = mock(DocumentWorkspaceMapper.class);
        DocumentStorageService storage = mock(DocumentStorageService.class);
        DocumentWorkspaceServiceImpl service = new DocumentWorkspaceServiceImpl();
        ReflectionTestUtils.setField(service, "mapper", mapper);
        ReflectionTestUtils.setField(service, "storageService", storage);
        ReflectionTestUtils.setField(service, "documentProperties", new DocumentManagementProperties());
        setCurrentUser(7L, "owner", "文档所有者");
        when(mapper.selectAccessibleDocument(99L, 7L)).thenReturn(null);

        assertThrows(ServiceException.class, () -> service.getBatchDownloadFiles(List.of(99L)));
    }

    private static void setCurrentUser(Long userId, String userName, String nickName)
    {
        LoginUser loginUser = loginUser(activeUser(userId, userName, nickName, "办公室"));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(loginUser, null, List.of()));
    }

    private static void setAdminCurrentUser(Long userId, String userName, String nickName)
    {
        SysUser user = activeUser(userId, userName, nickName, "办公室");
        SysRole role = new SysRole();
        role.setRoleKey("admin");
        user.setRoles(List.of(role));
        LoginUser loginUser = loginUser(user);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(loginUser, null, List.of()));
    }

    private static LoginUser loginUser(SysUser user)
    {
        return new LoginUser(user.getUserId(), user.getDeptId(), user, Set.of());
    }

    private static SysUser activeUser(Long userId, String userName, String nickName, String deptName)
    {
        SysDept dept = new SysDept();
        dept.setDeptId(100L + userId);
        dept.setDeptName(deptName);
        SysUser user = new SysUser();
        user.setUserId(userId);
        user.setDeptId(dept.getDeptId());
        user.setUserName(userName);
        user.setNickName(nickName);
        user.setStatus("0");
        user.setDelFlag("0");
        user.setDept(dept);
        user.setRoles(List.of());
        return user;
    }

    private static DocUserVo candidate(Long userId, String userName, String nickName, String deptName)
    {
        DocUserVo user = new DocUserVo();
        user.setUserId(userId);
        user.setUserName(userName);
        user.setNickName(nickName);
        user.setDeptName(deptName);
        return user;
    }

    private static DocDocument ownedDocument(Long documentId, Long ownerId)
    {
        DocDocument document = new DocDocument();
        document.setDocumentId(documentId);
        document.setOwnerId(ownerId);
        document.setEditorKey("doc-" + documentId + "-v1");
        document.setAccessPermission("OWNER");
        document.setLifecycleStatus("ACTIVE");
        return document;
    }

    private static DocFolder ownedFolder(Long folderId, Long parentId, Long ownerId, String name,
        Long documentCount, Long totalSize)
    {
        DocFolder folder = new DocFolder();
        folder.setFolderId(folderId);
        folder.setParentId(parentId);
        folder.setOwnerId(ownerId);
        folder.setFolderName(name);
        folder.setDocumentCount(documentCount);
        folder.setTotalSize(totalSize);
        return folder;
    }

    private static DocAcl acl(Long documentId, Long userId, String permission)
    {
        DocAcl acl = new DocAcl();
        acl.setDocumentId(documentId);
        acl.setUserId(userId);
        acl.setPermission(permission);
        return acl;
    }

    private static DocAclSaveBo.Entry entry(Long userId, String permission)
    {
        return entry(userId, permission, null);
    }

    private static DocAclSaveBo.Entry entry(Long userId, String permission, Date expiresAt)
    {
        DocAclSaveBo.Entry entry = new DocAclSaveBo.Entry();
        entry.setUserId(userId);
        entry.setPermission(permission);
        entry.setExpiresAt(expiresAt);
        return entry;
    }
}
