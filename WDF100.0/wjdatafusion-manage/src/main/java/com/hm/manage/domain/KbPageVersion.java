package com.hm.manage.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

public class KbPageVersion
{
    private Long versionId;
    private Long pageId;
    private Integer versionNo;
    private Long snapshotSpaceId;
    private Long snapshotParentId;
    private String snapshotTitle;
    private String snapshotSummary;
    private String snapshotContent;
    private String snapshotLifecycleStatus;
    private String snapshotTags;
    private String snapshotDocumentIds;
    private String operationType;
    private String changeFields;
    private String changeNote;
    private String contentChecksum;
    private Long operatorId;
    private String operatorName;
    private Integer restoredFromVersion;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    public Long getVersionId() { return versionId; }
    public void setVersionId(Long versionId) { this.versionId = versionId; }
    public Long getPageId() { return pageId; }
    public void setPageId(Long pageId) { this.pageId = pageId; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public Long getSnapshotSpaceId() { return snapshotSpaceId; }
    public void setSnapshotSpaceId(Long snapshotSpaceId) { this.snapshotSpaceId = snapshotSpaceId; }
    public Long getSnapshotParentId() { return snapshotParentId; }
    public void setSnapshotParentId(Long snapshotParentId) { this.snapshotParentId = snapshotParentId; }
    public String getSnapshotTitle() { return snapshotTitle; }
    public void setSnapshotTitle(String snapshotTitle) { this.snapshotTitle = snapshotTitle; }
    public String getSnapshotSummary() { return snapshotSummary; }
    public void setSnapshotSummary(String snapshotSummary) { this.snapshotSummary = snapshotSummary; }
    public String getSnapshotContent() { return snapshotContent; }
    public void setSnapshotContent(String snapshotContent) { this.snapshotContent = snapshotContent; }
    public String getSnapshotLifecycleStatus() { return snapshotLifecycleStatus; }
    public void setSnapshotLifecycleStatus(String snapshotLifecycleStatus) { this.snapshotLifecycleStatus = snapshotLifecycleStatus; }
    public String getSnapshotTags() { return snapshotTags; }
    public void setSnapshotTags(String snapshotTags) { this.snapshotTags = snapshotTags; }
    public String getSnapshotDocumentIds() { return snapshotDocumentIds; }
    public void setSnapshotDocumentIds(String snapshotDocumentIds) { this.snapshotDocumentIds = snapshotDocumentIds; }
    public String getOperationType() { return operationType; }
    public void setOperationType(String operationType) { this.operationType = operationType; }
    public String getChangeFields() { return changeFields; }
    public void setChangeFields(String changeFields) { this.changeFields = changeFields; }
    public String getChangeNote() { return changeNote; }
    public void setChangeNote(String changeNote) { this.changeNote = changeNote; }
    public String getContentChecksum() { return contentChecksum; }
    public void setContentChecksum(String contentChecksum) { this.contentChecksum = contentChecksum; }
    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    public Integer getRestoredFromVersion() { return restoredFromVersion; }
    public void setRestoredFromVersion(Integer restoredFromVersion) { this.restoredFromVersion = restoredFromVersion; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
}
