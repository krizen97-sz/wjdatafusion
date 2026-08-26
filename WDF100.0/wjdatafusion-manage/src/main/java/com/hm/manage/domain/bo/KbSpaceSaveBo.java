package com.hm.manage.domain.bo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class KbSpaceSaveBo
{
    @NotBlank(message = "知识空间名称不能为空")
    @Size(max = 100, message = "知识空间名称不能超过100个字符")
    private String spaceName;

    @Size(max = 500, message = "知识空间说明不能超过500个字符")
    private String description;

    @Min(value = 0, message = "排序不能小于0")
    @Max(value = 100000, message = "排序不能超过100000")
    private Integer sortOrder;

    public String getSpaceName() { return spaceName; }
    public void setSpaceName(String spaceName) { this.spaceName = spaceName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
