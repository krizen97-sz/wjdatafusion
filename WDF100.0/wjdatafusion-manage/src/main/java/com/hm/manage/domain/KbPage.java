package com.hm.manage.domain;

import com.hm.common.core.domain.BaseEntity;

public class KbPage extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long pageId;
    private Long spaceId;
    private Long parentId;
    private String pageType;
    private String title;
    private String summary;
    private String content;
    private Integer sortOrder;
    private Integer contentVersion;
    private String lifecycleStatus;
    private Long createUserId;
    private Long updateUserId;
    private String creatorName;
    private String modifierName;

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
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Integer getContentVersion() { return contentVersion; }
    public void setContentVersion(Integer contentVersion) { this.contentVersion = contentVersion; }
    public String getLifecycleStatus() { return lifecycleStatus; }
    public void setLifecycleStatus(String lifecycleStatus) { this.lifecycleStatus = lifecycleStatus; }
    public Long getCreateUserId() { return createUserId; }
    public void setCreateUserId(Long createUserId) { this.createUserId = createUserId; }
    public Long getUpdateUserId() { return updateUserId; }
    public void setUpdateUserId(Long updateUserId) { this.updateUserId = updateUserId; }
    public String getCreatorName() { return creatorName; }
    public void setCreatorName(String creatorName) { this.creatorName = creatorName; }
    public String getModifierName() { return modifierName; }
    public void setModifierName(String modifierName) { this.modifierName = modifierName; }
}
