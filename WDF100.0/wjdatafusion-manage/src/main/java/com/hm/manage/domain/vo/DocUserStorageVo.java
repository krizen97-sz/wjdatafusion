package com.hm.manage.domain.vo;

public class DocUserStorageVo
{
    private Long userId;
    private String userName;
    private String nickName;
    private String deptName;
    private Long fileCount;
    private Long usedSize;
    private Long quotaSize;
    private Long maxUploadSize;
    private Double usagePercent;
    private Boolean adminUser;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getNickName() { return nickName; }
    public void setNickName(String nickName) { this.nickName = nickName; }
    public String getDeptName() { return deptName; }
    public void setDeptName(String deptName) { this.deptName = deptName; }
    public Long getFileCount() { return fileCount; }
    public void setFileCount(Long fileCount) { this.fileCount = fileCount; }
    public Long getUsedSize() { return usedSize; }
    public void setUsedSize(Long usedSize) { this.usedSize = usedSize; }
    public Long getQuotaSize() { return quotaSize; }
    public void setQuotaSize(Long quotaSize) { this.quotaSize = quotaSize; }
    public Long getMaxUploadSize() { return maxUploadSize; }
    public void setMaxUploadSize(Long maxUploadSize) { this.maxUploadSize = maxUploadSize; }
    public Double getUsagePercent() { return usagePercent; }
    public void setUsagePercent(Double usagePercent) { this.usagePercent = usagePercent; }
    public Boolean getAdminUser() { return adminUser; }
    public void setAdminUser(Boolean adminUser) { this.adminUser = adminUser; }
}
