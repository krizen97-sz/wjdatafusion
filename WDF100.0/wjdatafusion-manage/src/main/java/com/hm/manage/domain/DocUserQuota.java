package com.hm.manage.domain;

import java.util.Date;

/**
 * Per-user document storage policy. Missing rows use the service defaults.
 */
public class DocUserQuota
{
    private Long userId;
    private Long quotaBytes;
    private Long maxUploadBytes;
    private String createBy;
    private Date createTime;
    private String updateBy;
    private Date updateTime;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getQuotaBytes() { return quotaBytes; }
    public void setQuotaBytes(Long quotaBytes) { this.quotaBytes = quotaBytes; }
    public Long getMaxUploadBytes() { return maxUploadBytes; }
    public void setMaxUploadBytes(Long maxUploadBytes) { this.maxUploadBytes = maxUploadBytes; }
    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public String getUpdateBy() { return updateBy; }
    public void setUpdateBy(String updateBy) { this.updateBy = updateBy; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
