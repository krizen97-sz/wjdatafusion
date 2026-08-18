package com.hm.manage.service;

import java.util.List;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;
import com.hm.manage.domain.DocAcl;
import com.hm.manage.domain.DocDocument;
import com.hm.manage.domain.DocFolder;
import com.hm.manage.domain.DocOperationLog;
import com.hm.manage.domain.DocVersion;
import com.hm.manage.domain.bo.DocAclSaveBo;
import com.hm.manage.domain.bo.DocCreateBo;
import com.hm.manage.domain.bo.DocFolderSaveBo;
import com.hm.manage.domain.bo.DocFolderReorderBo;
import com.hm.manage.domain.bo.DocQuotaUpdateBo;
import com.hm.manage.domain.bo.DocUpdateBo;
import com.hm.manage.domain.vo.DocUserStorageVo;
import com.hm.manage.domain.vo.DocUserVo;
import com.hm.manage.domain.vo.DocWorkspaceSummaryVo;
import com.hm.manage.service.document.DocFileResource;

public interface IDocumentWorkspaceService
{
    List<DocFolder> listFolders();

    DocWorkspaceSummaryVo getWorkspaceSummary();

    List<DocUserStorageVo> listDocumentStorageUsers();

    DocUserStorageVo updateDocumentStoragePolicy(Long userId, DocQuotaUpdateBo input);

    DocFolder createFolder(DocFolderSaveBo input);

    void updateFolder(Long folderId, DocFolderSaveBo input);

    void reorderFolders(DocFolderReorderBo input);

    void removeFolder(Long folderId);

    List<DocDocument> listDocuments(String scope, Long folderId, String keyword, String fileType,
        String accessPermission);

    DocDocument getDocument(Long documentId);

    DocDocument createDocument(DocCreateBo input);

    DocDocument copyDocument(Long documentId);

    Map<String, Object> uploadDocument(MultipartFile file, Long folderId);

    void updateDocument(Long documentId, DocUpdateBo input);

    List<DocAcl> listCollaborators(Long documentId);

    void saveCollaborators(Long documentId, DocAclSaveBo input);

    List<DocUserVo> listCollaboratorCandidates(Long documentId, String keyword);

    List<DocVersion> listVersions(Long documentId);

    List<DocOperationLog> listOperations(Long documentId);

    Map<String, Object> getEditorBootstrap(Long documentId);

    Map<String, Object> forceSaveDocument(Long documentId);

    DocFileResource getDownloadFile(Long documentId);

    DocFileResource getPreviewFile(Long documentId);

    List<DocFileResource> getBatchDownloadFiles(List<Long> documentIds);

    DocFileResource getEditorFile(Long documentId, String accessToken);

    int expireCollaboratorPermissions();

    Map<String, Object> handleEditorCallback(Long documentId, String accessToken, String outboxToken,
        Map<String, Object> payload);
}
