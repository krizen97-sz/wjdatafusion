package com.hm.manage.domain.bo;

import java.util.List;

public class DocFolderReorderBo
{
    private Long parentId;
    private List<Long> folderIds;

    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public List<Long> getFolderIds() { return folderIds; }
    public void setFolderIds(List<Long> folderIds) { this.folderIds = folderIds; }
}
