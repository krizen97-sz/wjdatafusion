package com.hm.manage.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.hm.common.core.domain.BaseEntity;

public class DocAcl extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long aclId;
    private Long documentId;
    private Long userId;
    private String permission;
    private Long grantedBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date expiresAt;
    private Boolean expired;
    private String userName;
    private String nickName;
    private String avatar;
    private Boolean online;

    public Long getAclId() { return aclId; }
    public void setAclId(Long aclId) { this.aclId = aclId; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getPermission() { return permission; }
    public void setPermission(String permission) { this.permission = permission; }
    public Long getGrantedBy() { return grantedBy; }
    public void setGrantedBy(Long grantedBy) { this.grantedBy = grantedBy; }
    public Date getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Date expiresAt) { this.expiresAt = expiresAt; }
    public Boolean getExpired() { return expired; }
    public void setExpired(Boolean expired) { this.expired = expired; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getNickName() { return nickName; }
    public void setNickName(String nickName) { this.nickName = nickName; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public Boolean getOnline() { return online; }
    public void setOnline(Boolean online) { this.online = online; }
}
