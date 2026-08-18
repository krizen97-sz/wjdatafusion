package com.hm.manage.domain;

import com.hm.common.core.domain.BaseEntity;

public class DocVersion extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long versionId;
    private Long documentId;
    private Integer versionNo;
    private String sourceType;
    private Long creatorId;
    private String creatorName;
    private Boolean current;

    public Long getVersionId() { return versionId; }
    public void setVersionId(Long versionId) { this.versionId = versionId; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public Long getCreatorId() { return creatorId; }
    public void setCreatorId(Long creatorId) { this.creatorId = creatorId; }
    public String getCreatorName() { return creatorName; }
    public void setCreatorName(String creatorName) { this.creatorName = creatorName; }
    public Boolean getCurrent() { return current; }
    public void setCurrent(Boolean current) { this.current = current; }
}
