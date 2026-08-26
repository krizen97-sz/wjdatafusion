package com.hm.manage.domain.vo;

public class KbDocumentLinkVo
{
    private Long documentId;
    private String title;
    private String fileType;
    private String documentType;
    private Long fileSize;
    private Integer contentVersion;
    private String lifecycleStatus;
    private String accessPermission;
    private String accessStatus;
    private Integer sortOrder;

    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public Integer getContentVersion() { return contentVersion; }
    public void setContentVersion(Integer contentVersion) { this.contentVersion = contentVersion; }
    public String getLifecycleStatus() { return lifecycleStatus; }
    public void setLifecycleStatus(String lifecycleStatus) { this.lifecycleStatus = lifecycleStatus; }
    public String getAccessPermission() { return accessPermission; }
    public void setAccessPermission(String accessPermission) { this.accessPermission = accessPermission; }
    public String getAccessStatus() { return accessStatus; }
    public void setAccessStatus(String accessStatus) { this.accessStatus = accessStatus; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
