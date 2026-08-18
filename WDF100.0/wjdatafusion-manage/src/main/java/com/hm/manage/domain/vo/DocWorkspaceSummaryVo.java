package com.hm.manage.domain.vo;

public class DocWorkspaceSummaryVo
{
    private Long fileCount;
    private Long totalSize;
    private Long unfiledCount;
    private Long usedSize;
    private Long quotaSize;
    private Long remainingSize;
    private Long maxUploadSize;
    private Double usagePercent;
    private Boolean documentAdmin;

    public Long getFileCount() { return fileCount; }
    public void setFileCount(Long fileCount) { this.fileCount = fileCount; }
    public Long getTotalSize() { return totalSize; }
    public void setTotalSize(Long totalSize) { this.totalSize = totalSize; }
    public Long getUnfiledCount() { return unfiledCount; }
    public void setUnfiledCount(Long unfiledCount) { this.unfiledCount = unfiledCount; }
    public Long getUsedSize() { return usedSize; }
    public void setUsedSize(Long usedSize) { this.usedSize = usedSize; }
    public Long getQuotaSize() { return quotaSize; }
    public void setQuotaSize(Long quotaSize) { this.quotaSize = quotaSize; }
    public Long getRemainingSize() { return remainingSize; }
    public void setRemainingSize(Long remainingSize) { this.remainingSize = remainingSize; }
    public Long getMaxUploadSize() { return maxUploadSize; }
    public void setMaxUploadSize(Long maxUploadSize) { this.maxUploadSize = maxUploadSize; }
    public Double getUsagePercent() { return usagePercent; }
    public void setUsagePercent(Double usagePercent) { this.usagePercent = usagePercent; }
    public Boolean getDocumentAdmin() { return documentAdmin; }
    public void setDocumentAdmin(Boolean documentAdmin) { this.documentAdmin = documentAdmin; }
}
