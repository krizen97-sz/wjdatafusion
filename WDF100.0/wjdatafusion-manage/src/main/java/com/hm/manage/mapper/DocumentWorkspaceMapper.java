package com.hm.manage.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.hm.manage.domain.DocAcl;
import com.hm.manage.domain.DocDocument;
import com.hm.manage.domain.DocFolder;
import com.hm.manage.domain.DocOperationLog;
import com.hm.manage.domain.DocUserQuota;
import com.hm.manage.domain.DocVersion;
import com.hm.manage.domain.vo.DocUserStorageVo;
import com.hm.manage.domain.vo.DocUserVo;
import com.hm.manage.domain.vo.DocWorkspaceSummaryVo;

public interface DocumentWorkspaceMapper
{
    List<DocFolder> selectFoldersByOwner(Long ownerId);

    DocWorkspaceSummaryVo selectWorkspaceSummary(Long ownerId);

    Long selectOwnedStorageBytes(Long ownerId);

    int insertDefaultQuota(@Param("userId") Long userId, @Param("operator") String operator);

    DocUserQuota selectQuota(Long userId);

    DocUserQuota selectQuotaForUpdate(Long userId);

    int updateQuota(@Param("userId") Long userId, @Param("quotaBytes") Long quotaBytes,
        @Param("maxUploadBytes") Long maxUploadBytes, @Param("operator") String operator);

    int countDocumentWorkspaceAccess(Long userId);

    int countDocumentAdminRole(Long userId);

    List<DocUserVo> selectCollaboratorCandidates(@Param("excludeUserId") Long excludeUserId,
        @Param("ownerId") Long ownerId, @Param("keyword") String keyword, @Param("limit") int limit);

    List<DocUserStorageVo> selectDocumentStorageUsers();

    DocFolder selectFolderById(Long folderId);

    List<DocFolder> selectFoldersByOwnerAndParentForUpdate(@Param("ownerId") Long ownerId,
        @Param("parentId") Long parentId);

    Integer selectNextFolderSortOrder(@Param("ownerId") Long ownerId, @Param("parentId") Long parentId);

    int countSiblingFolder(@Param("ownerId") Long ownerId, @Param("parentId") Long parentId,
        @Param("folderName") String folderName, @Param("excludeFolderId") Long excludeFolderId);

    int countChildFolders(Long folderId);

    int countDocumentsByFolder(Long folderId);

    int insertFolder(DocFolder folder);

    int updateFolder(DocFolder folder);

    int updateFolderSortOrder(@Param("folderId") Long folderId, @Param("ownerId") Long ownerId,
        @Param("parentId") Long parentId, @Param("sortOrder") Integer sortOrder,
        @Param("operator") String operator);

    int deleteFolder(Long folderId);

    List<DocDocument> selectDocumentList(@Param("userId") Long userId, @Param("scope") String scope,
        @Param("folderId") Long folderId, @Param("keyword") String keyword, @Param("fileType") String fileType,
        @Param("accessPermission") String accessPermission);

    DocDocument selectDocumentRecord(Long documentId);

    DocDocument selectDocumentRecordForUpdate(Long documentId);

    DocDocument selectAccessibleDocument(@Param("documentId") Long documentId, @Param("userId") Long userId);

    int insertDocument(DocDocument document);

    int countDocumentTitle(@Param("ownerId") Long ownerId, @Param("folderId") Long folderId,
        @Param("title") String title);

    int updateDocumentMetadata(DocDocument document);

    int updateDocumentContent(DocDocument document);

    List<DocAcl> selectAclList(Long documentId);

    List<DocAcl> selectAclRecords(Long documentId);

    DocAcl selectAcl(@Param("documentId") Long documentId, @Param("userId") Long userId);

    int deleteAclByDocumentId(Long documentId);

    int deleteAcl(@Param("documentId") Long documentId, @Param("userId") Long userId);

    int insertAcl(DocAcl acl);

    int updateAcl(DocAcl acl);

    List<DocAcl> selectExpiredAclRecords(@Param("limit") int limit);

    int deleteExpiredAcl(Long aclId);

    int insertVersion(DocVersion version);

    List<DocVersion> selectVersions(Long documentId);

    int insertOperationLog(DocOperationLog operationLog);

    List<DocOperationLog> selectOperationLogs(Long documentId);
}
