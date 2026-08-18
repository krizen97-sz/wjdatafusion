package com.hm.manage.mapper;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

class DocumentWorkspaceMapperContractTest
{
    @Test
    void documentReadsMustKeepOwnerOrAclBoundary() throws Exception
    {
        try (InputStream input = getClass().getClassLoader()
            .getResourceAsStream("mapper/document/DocumentWorkspaceMapper.xml"))
        {
            assertNotNull(input);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Document document = factory.newDocumentBuilder().parse(input);
            var xpath = XPathFactory.newInstance().newXPath();
            String list = xpath.evaluate("/mapper/select[@id='selectDocumentList']", document);
            String detail = xpath.evaluate("/mapper/select[@id='selectAccessibleDocument']", document);
            String shared = xpath.evaluate("/mapper/select[@id='selectDocumentList']", document);
            String rawAcl = xpath.evaluate("/mapper/select[@id='selectAclRecords']", document);
            String activeAcl = xpath.evaluate("/mapper/select[@id='selectAcl']", document);
            String insertAcl = xpath.evaluate("/mapper/insert[@id='insertAcl']", document);
            String updateAcl = xpath.evaluate("/mapper/update[@id='updateAcl']", document);
            String deleteAcl = xpath.evaluate("/mapper/delete[@id='deleteAcl']", document);
            String expiredAcl = xpath.evaluate("/mapper/select[@id='selectExpiredAclRecords']", document);
            String insertVersion = xpath.evaluate("/mapper/insert[@id='insertVersion']", document);
            String versions = xpath.evaluate("/mapper/select[@id='selectVersions']", document);
            String updateContent = xpath.evaluate("/mapper/update[@id='updateDocumentContent']", document);
            String insertLog = xpath.evaluate("/mapper/insert[@id='insertOperationLog']", document);
            String operationList = xpath.evaluate("/mapper/select[@id='selectOperationLogs']", document);
            String folderList = xpath.evaluate("/mapper/select[@id='selectFoldersByOwner']", document);
            String folderById = xpath.evaluate("/mapper/select[@id='selectFolderById']", document);
            String folderLock = xpath.evaluate("/mapper/select[@id='selectFoldersByOwnerAndParentForUpdate']", document);
            String insertFolder = xpath.evaluate("/mapper/insert[@id='insertFolder']", document);
            String updateFolder = xpath.evaluate("/mapper/update[@id='updateFolder']", document);
            String updateFolderSort = xpath.evaluate("/mapper/update[@id='updateFolderSortOrder']", document);
            String workspaceSummary = xpath.evaluate("/mapper/select[@id='selectWorkspaceSummary']", document);
            String titleCount = xpath.evaluate("/mapper/select[@id='countDocumentTitle']", document);
            String workspaceAccess = xpath.evaluate("/mapper/select[@id='countDocumentWorkspaceAccess']", document);
            String collaboratorCandidates = xpath.evaluate("/mapper/select[@id='selectCollaboratorCandidates']", document);
            String storageUsers = xpath.evaluate("/mapper/select[@id='selectDocumentStorageUsers']", document);

            assertTrue(list.contains("d.owner_id = #{userId}"));
            assertTrue(list.contains("access_acl.user_id = #{userId}"));
            assertTrue(list.contains("access_acl.expires_at is null or access_acl.expires_at > now()"));
            assertTrue(detail.contains("d.owner_id = #{userId}"));
            assertTrue(detail.contains("access_acl.user_id = #{userId}"));
            assertTrue(detail.contains("access_acl.expires_at is null or access_acl.expires_at > now()"));
            assertTrue(shared.contains("permission_acl.permission = #{accessPermission}"));
            assertTrue(rawAcl.contains("from doc_acl"));
            assertTrue(activeAcl.contains("expires_at is null or expires_at > now()"));
            assertTrue(insertAcl.contains("expires_at"));
            assertTrue(insertAcl.contains("#{expiresAt}"));
            assertTrue(updateAcl.contains("expires_at = #{expiresAt}"));
            assertTrue(updateAcl.contains("where document_id = #{documentId} and user_id = #{userId}"));
            assertTrue(deleteAcl.contains("where document_id = #{documentId} and user_id = #{userId}"));
            assertTrue(expiredAcl.contains("a.expires_at <= now()"));
            assertTrue(insertVersion.contains("creator_id"));
            assertTrue(insertVersion.contains("creator_name"));
            assertFalse(insertVersion.contains("storage_key"));
            assertFalse(versions.contains("storage_key"));
            assertTrue(updateContent.contains("file_type = #{fileType}"));
            assertTrue(updateContent.contains("title = #{title}"));
            assertTrue(insertLog.contains("target_user_id"));
            assertTrue(insertLog.contains("previous_value"));
            assertTrue(operationList.contains("order by create_time desc"));
            assertTrue(folderList.contains("document_count"));
            assertTrue(folderList.contains("total_size"));
            assertTrue(folderList.contains("folder_color"));
            assertTrue(folderById.contains("folder_color"));
            assertTrue(folderLock.contains("owner_id = #{ownerId}"));
            assertTrue(folderLock.contains("parent_id = #{parentId}"));
            assertTrue(folderLock.contains("for update"));
            assertTrue(insertFolder.contains("folder_color"));
            assertTrue(insertFolder.contains("#{folderColor}"));
            assertTrue(updateFolder.contains("folder_color = #{folderColor}"));
            assertTrue(updateFolderSort.contains("parent_id = #{parentId}"));
            assertTrue(updateFolderSort.contains("owner_id = #{ownerId}"));
            assertTrue(updateFolderSort.contains("sort_order = #{sortOrder}"));
            assertTrue(workspaceSummary.contains("as fileCount"));
            assertTrue(workspaceSummary.contains("as totalSize"));
            assertTrue(workspaceSummary.contains("as unfiledCount"));
            assertTrue(workspaceSummary.contains("owner_id = #{ownerId}"));
            assertTrue(workspaceSummary.contains("lifecycle_status != 'TRASH'"));
            assertTrue(titleCount.contains("folder_id = #{folderId}"));
            assertTrue(titleCount.contains("lower(title) = lower(#{title})"));
            assertTrue(workspaceAccess.contains("document:file:manage"));
            assertFalse(workspaceAccess.contains("document:workspace:access"));
            assertTrue(collaboratorCandidates.contains("document:file:manage"));
            assertFalse(collaboratorCandidates.contains("document:workspace:access"));
            assertTrue(collaboratorCandidates.contains("u.user_id != #{excludeUserId}"));
            assertTrue(collaboratorCandidates.contains("u.user_id != #{ownerId}"));
            assertTrue(collaboratorCandidates.contains("instr(lower(concat_ws"));
            assertTrue(collaboratorCandidates.contains("limit #{limit}"));
            assertTrue(storageUsers.contains("document:file:manage"));
            assertFalse(storageUsers.contains("document:workspace:access"));
        }
    }
}
