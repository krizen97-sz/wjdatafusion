package com.hm.manage.domain.bo;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;

public class DocAclSaveBo
{
    private List<Entry> entries = new ArrayList<>();

    public List<Entry> getEntries() { return entries; }
    public void setEntries(List<Entry> entries) { this.entries = entries; }

    public static class Entry
    {
        private Long userId;
        private String permission;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private Date expiresAt;

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getPermission() { return permission; }
        public void setPermission(String permission) { this.permission = permission; }
        public Date getExpiresAt() { return expiresAt; }
        public void setExpiresAt(Date expiresAt) { this.expiresAt = expiresAt; }
    }
}
