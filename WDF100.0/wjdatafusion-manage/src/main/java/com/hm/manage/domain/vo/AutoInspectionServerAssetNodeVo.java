package com.hm.manage.domain.vo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class AutoInspectionServerAssetNodeVo implements Serializable
{
    private static final long serialVersionUID = 1L;

    private String id;
    private String nodeId;
    private String label;
    private String type;
    private Object value;
    private Boolean disabled;
    private Long siteId;
    private String siteCode;
    private Long platformId;
    private Long serverId;
    private String serverName;
    private String serverAddress;
    private Integer sshPort;
    private String osUsername;
    private String osType;
    private String sourcePath;
    private List<AutoInspectionServerAssetNodeVo> children = new ArrayList<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Object getValue() { return value; }
    public void setValue(Object value) { this.value = value; }
    public Boolean getDisabled() { return disabled; }
    public void setDisabled(Boolean disabled) { this.disabled = disabled; }
    public Long getSiteId() { return siteId; }
    public void setSiteId(Long siteId) { this.siteId = siteId; }
    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }
    public Long getPlatformId() { return platformId; }
    public void setPlatformId(Long platformId) { this.platformId = platformId; }
    public Long getServerId() { return serverId; }
    public void setServerId(Long serverId) { this.serverId = serverId; }
    public String getServerName() { return serverName; }
    public void setServerName(String serverName) { this.serverName = serverName; }
    public String getServerAddress() { return serverAddress; }
    public void setServerAddress(String serverAddress) { this.serverAddress = serverAddress; }
    public Integer getSshPort() { return sshPort; }
    public void setSshPort(Integer sshPort) { this.sshPort = sshPort; }
    public String getOsUsername() { return osUsername; }
    public void setOsUsername(String osUsername) { this.osUsername = osUsername; }
    public String getOsType() { return osType; }
    public void setOsType(String osType) { this.osType = osType; }
    public String getSourcePath() { return sourcePath; }
    public void setSourcePath(String sourcePath) { this.sourcePath = sourcePath; }
    public List<AutoInspectionServerAssetNodeVo> getChildren() { return children; }
    public void setChildren(List<AutoInspectionServerAssetNodeVo> children) { this.children = children; }
}
