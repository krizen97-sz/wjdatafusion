package com.hm.manage.domain.vo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class AutoInspectionScopeNodeVo implements Serializable
{
    private static final long serialVersionUID = 1L;

    private String id;
    private String value;
    private String label;
    private String scopeType;
    private Long siteId;
    private String siteName;
    private String siteCode;
    private Long mainPlatformId;
    private String mainPlatformName;
    private String networkEnv;
    private List<AutoInspectionScopeNodeVo> children = new ArrayList<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getScopeType() { return scopeType; }
    public void setScopeType(String scopeType) { this.scopeType = scopeType; }
    public Long getSiteId() { return siteId; }
    public void setSiteId(Long siteId) { this.siteId = siteId; }
    public String getSiteName() { return siteName; }
    public void setSiteName(String siteName) { this.siteName = siteName; }
    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }
    public Long getMainPlatformId() { return mainPlatformId; }
    public void setMainPlatformId(Long mainPlatformId) { this.mainPlatformId = mainPlatformId; }
    public String getMainPlatformName() { return mainPlatformName; }
    public void setMainPlatformName(String mainPlatformName) { this.mainPlatformName = mainPlatformName; }
    public String getNetworkEnv() { return networkEnv; }
    public void setNetworkEnv(String networkEnv) { this.networkEnv = networkEnv; }
    public List<AutoInspectionScopeNodeVo> getChildren() { return children; }
    public void setChildren(List<AutoInspectionScopeNodeVo> children) { this.children = children; }
}
