package com.hm.manage.domain.vo;

public class DocUserVo
{
    private Long userId;
    private String userName;
    private String nickName;
    private String avatar;
    private String deptName;
    private Boolean online;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getNickName() { return nickName; }
    public void setNickName(String nickName) { this.nickName = nickName; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public String getDeptName() { return deptName; }
    public void setDeptName(String deptName) { this.deptName = deptName; }
    public Boolean getOnline() { return online; }
    public void setOnline(Boolean online) { this.online = online; }
}
