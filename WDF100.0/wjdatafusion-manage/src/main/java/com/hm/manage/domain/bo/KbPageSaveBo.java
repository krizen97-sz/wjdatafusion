package com.hm.manage.domain.bo;

import java.util.List;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class KbPageSaveBo
{
    @NotNull(message = "知识空间不能为空")
    private Long spaceId;

    private Long parentId;

    @NotBlank(message = "知识标题不能为空")
    @Size(max = 160, message = "知识标题不能超过160个字符")
    private String title;

    @Size(max = 500, message = "知识摘要不能超过500个字符")
    private String summary;

    @NotBlank(message = "知识正文不能为空")
    @Size(max = 2000000, message = "知识正文不能超过200万字符")
    private String content;

    @Size(max = 8, message = "单篇知识最多设置8个标签")
    private List<@Size(max = 40, message = "标签名称不能超过40个字符") String> tagNames;

    @Size(max = 20, message = "单篇知识最多关联20份文档")
    private List<Long> documentIds;

    @Min(value = 1, message = "当前版本号无效")
    private Integer expectedVersion;

    @Size(max = 500, message = "修改说明不能超过500个字符")
    private String changeNote;

    public Long getSpaceId() { return spaceId; }
    public void setSpaceId(Long spaceId) { this.spaceId = spaceId; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public List<String> getTagNames() { return tagNames; }
    public void setTagNames(List<String> tagNames) { this.tagNames = tagNames; }
    public List<Long> getDocumentIds() { return documentIds; }
    public void setDocumentIds(List<Long> documentIds) { this.documentIds = documentIds; }
    public Integer getExpectedVersion() { return expectedVersion; }
    public void setExpectedVersion(Integer expectedVersion) { this.expectedVersion = expectedVersion; }
    public String getChangeNote() { return changeNote; }
    public void setChangeNote(String changeNote) { this.changeNote = changeNote; }
}
