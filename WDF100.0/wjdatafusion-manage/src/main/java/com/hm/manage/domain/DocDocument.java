package com.hm.manage.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.hm.common.core.domain.BaseEntity;

public class DocDocument extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long documentId;
    private Long folderId;
    private Long ownerId;
    private String title;
    private String fileType;
    private String documentType;
    private String storageKey;
    private Long fileSize;
    private Integer contentVersion;
    private String editorKey;
    private String checksum;
    private String lifecycleStatus;
    private String ownerName;
    private String ownerAvatar;
    private String folderName;
    private String accessPermission;
    private String collaboratorNames;
    private Integer collaboratorCount;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastOpenedTime;

    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public Long getFolderId() { return folderId; }
    public void setFolderId(Long folderId) { this.folderId = folderId; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }
    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public Integer getContentVersion() { return contentVersion; }
    public void setContentVersion(Integer contentVersion) { this.contentVersion = contentVersion; }
    public String getEditorKey() { return editorKey; }
    public void setEditorKey(String editorKey) { this.editorKey = editorKey; }
    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }
    public String getLifecycleStatus() { return lifecycleStatus; }
    public void setLifecycleStatus(String lifecycleStatus) { this.lifecycleStatus = lifecycleStatus; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public String getOwnerAvatar() { return ownerAvatar; }
    public void setOwnerAvatar(String ownerAvatar) { this.ownerAvatar = ownerAvatar; }
    public String getFolderName() { return folderName; }
    public void setFolderName(String folderName) { this.folderName = folderName; }
    public String getAccessPermission() { return accessPermission; }
    public void setAccessPermission(String accessPermission) { this.accessPermission = accessPermission; }
    public String getCollaboratorNames() { return collaboratorNames; }
    public void setCollaboratorNames(String collaboratorNames) { this.collaboratorNames = collaboratorNames; }
    public Integer getCollaboratorCount() { return collaboratorCount; }
    public void setCollaboratorCount(Integer collaboratorCount) { this.collaboratorCount = collaboratorCount; }
    public Date getLastOpenedTime() { return lastOpenedTime; }
    public void setLastOpenedTime(Date lastOpenedTime) { this.lastOpenedTime = lastOpenedTime; }
}
