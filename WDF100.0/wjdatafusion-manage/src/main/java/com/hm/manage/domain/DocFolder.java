package com.hm.manage.domain;

import com.hm.common.core.domain.BaseEntity;

public class DocFolder extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long folderId;
    private Long parentId;
    private Long ownerId;
    private String folderName;
    private String folderColor;
    private Integer sortOrder;
    /** 当前目录及全部子目录中的非回收站文档数。 */
    private Long documentCount;
    /** 当前目录及全部子目录中的非回收站文档总字节数。 */
    private Long totalSize;

    public Long getFolderId() { return folderId; }
    public void setFolderId(Long folderId) { this.folderId = folderId; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public String getFolderName() { return folderName; }
    public void setFolderName(String folderName) { this.folderName = folderName; }
    public String getFolderColor() { return folderColor; }
    public void setFolderColor(String folderColor) { this.folderColor = folderColor; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Long getDocumentCount() { return documentCount; }
    public void setDocumentCount(Long documentCount) { this.documentCount = documentCount; }
    public Long getTotalSize() { return totalSize; }
    public void setTotalSize(Long totalSize) { this.totalSize = totalSize; }
}
