package com.hm.manage.domain.bo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class AutoInspectionServerCredentialBatchBo implements Serializable
{
    private static final long serialVersionUID = 1L;

    private List<Long> serverIds = new ArrayList<>();
    private String username;

    public List<Long> getServerIds() { return serverIds; }
    public void setServerIds(List<Long> serverIds) { this.serverIds = serverIds; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
}
