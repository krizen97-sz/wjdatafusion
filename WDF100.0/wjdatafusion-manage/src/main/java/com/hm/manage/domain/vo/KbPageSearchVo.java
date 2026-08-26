package com.hm.manage.domain.vo;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

public class KbPageSearchVo
{
    private Long pageId;
    private Long spaceId;
    private Long parentId;
    private String title;
    private String summary;
    private Integer contentVersion;
    private String modifierName;
    private String tagNames;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    public Long getPageId() { return pageId; }
    public void setPageId(Long pageId) { this.pageId = pageId; }
    public Long getSpaceId() { return spaceId; }
    public void setSpaceId(Long spaceId) { this.spaceId = spaceId; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public Integer getContentVersion() { return contentVersion; }
    public void setContentVersion(Integer contentVersion) { this.contentVersion = contentVersion; }
    public String getModifierName() { return modifierName; }
    public void setModifierName(String modifierName) { this.modifierName = modifierName; }
    public String getTagNames() { return tagNames; }
    public void setTagNames(String tagNames) { this.tagNames = tagNames; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
