package com.hm.manage.domain.vo;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

public class KbPageTreeVo
{
    private Long pageId;
    private Long spaceId;
    private Long parentId;
    private String pageType;
    private String title;
    private Integer sortOrder;
    private Integer contentVersion;
    private String lifecycleStatus;
    private String modifierName;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    public Long getPageId() { return pageId; }
    public void setPageId(Long pageId) { this.pageId = pageId; }
    public Long getSpaceId() { return spaceId; }
    public void setSpaceId(Long spaceId) { this.spaceId = spaceId; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getPageType() { return pageType; }
    public void setPageType(String pageType) { this.pageType = pageType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Integer getContentVersion() { return contentVersion; }
    public void setContentVersion(Integer contentVersion) { this.contentVersion = contentVersion; }
    public String getLifecycleStatus() { return lifecycleStatus; }
    public void setLifecycleStatus(String lifecycleStatus) { this.lifecycleStatus = lifecycleStatus; }
    public String getModifierName() { return modifierName; }
    public void setModifierName(String modifierName) { this.modifierName = modifierName; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
