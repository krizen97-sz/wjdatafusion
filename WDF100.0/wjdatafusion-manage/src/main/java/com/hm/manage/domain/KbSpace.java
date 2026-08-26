package com.hm.manage.domain;

import com.hm.common.core.domain.BaseEntity;

public class KbSpace extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long spaceId;
    private String spaceName;
    private String description;
    private Integer sortOrder;
    private String status;

    public Long getSpaceId() { return spaceId; }
    public void setSpaceId(Long spaceId) { this.spaceId = spaceId; }
    public String getSpaceName() { return spaceName; }
    public void setSpaceName(String spaceName) { this.spaceName = spaceName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
