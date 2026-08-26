package com.hm.manage.domain.bo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class KbFolderSaveBo
{
    @NotNull(message = "知识空间不能为空")
    private Long spaceId;

    private Long parentId;

    @NotBlank(message = "目录名称不能为空")
    @Size(max = 100, message = "目录名称不能超过100个字符")
    private String title;

    @Min(value = 0, message = "排序不能小于0")
    @Max(value = 100000, message = "排序不能超过100000")
    private Integer sortOrder;

    public Long getSpaceId() { return spaceId; }
    public void setSpaceId(Long spaceId) { this.spaceId = spaceId; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
