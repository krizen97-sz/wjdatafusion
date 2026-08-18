package com.hm.manage.domain.bo;

public class DocFolderSaveBo
{
    private Long parentId;
    private String folderName;
    private String folderColor;
    private Integer sortOrder;

    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getFolderName() { return folderName; }
    public void setFolderName(String folderName) { this.folderName = folderName; }
    public String getFolderColor() { return folderColor; }
    public void setFolderColor(String folderColor) { this.folderColor = folderColor; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
