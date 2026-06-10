package com.hm.manage.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hm.common.core.domain.BaseEntity;

public class SupportTimInspectionTarget extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long targetId;
    private String itemCode;
    private String targetName;
    private String targetType;
    private Long serverId;
    private String serverName;
    private String serverAddress;
    private String host;
    private Integer port;
    private String path;
    private String url;
    private String httpMethod;
    private String topic;
    private String consumerGroup;
    private String username;
    private String password;
    @JsonIgnore
    private String passwordCipher;
    private String appKey;
    private String secret;
    @JsonIgnore
    private String secretCipher;
    private String resultPath;
    private String extraParams;
    private String status;

    public Long getTargetId()
    {
        return targetId;
    }

    public void setTargetId(Long targetId)
    {
        this.targetId = targetId;
    }

    public String getItemCode()
    {
        return itemCode;
    }

    public void setItemCode(String itemCode)
    {
        this.itemCode = itemCode;
    }

    public String getTargetName()
    {
        return targetName;
    }

    public void setTargetName(String targetName)
    {
        this.targetName = targetName;
    }

    public String getTargetType()
    {
        return targetType;
    }

    public void setTargetType(String targetType)
    {
        this.targetType = targetType;
    }

    public Long getServerId()
    {
        return serverId;
    }

    public void setServerId(Long serverId)
    {
        this.serverId = serverId;
    }

    public String getServerName()
    {
        return serverName;
    }

    public void setServerName(String serverName)
    {
        this.serverName = serverName;
    }

    public String getServerAddress()
    {
        return serverAddress;
    }

    public void setServerAddress(String serverAddress)
    {
        this.serverAddress = serverAddress;
    }

    public String getHost()
    {
        return host;
    }

    public void setHost(String host)
    {
        this.host = host;
    }

    public Integer getPort()
    {
        return port;
    }

    public void setPort(Integer port)
    {
        this.port = port;
    }

    public String getPath()
    {
        return path;
    }

    public void setPath(String path)
    {
        this.path = path;
    }

    public String getUrl()
    {
        return url;
    }

    public void setUrl(String url)
    {
        this.url = url;
    }

    public String getHttpMethod()
    {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod)
    {
        this.httpMethod = httpMethod;
    }

    public String getTopic()
    {
        return topic;
    }

    public void setTopic(String topic)
    {
        this.topic = topic;
    }

    public String getConsumerGroup()
    {
        return consumerGroup;
    }

    public void setConsumerGroup(String consumerGroup)
    {
        this.consumerGroup = consumerGroup;
    }

    public String getUsername()
    {
        return username;
    }

    public void setUsername(String username)
    {
        this.username = username;
    }

    public String getPassword()
    {
        return password;
    }

    public void setPassword(String password)
    {
        this.password = password;
    }

    public String getPasswordCipher()
    {
        return passwordCipher;
    }

    public void setPasswordCipher(String passwordCipher)
    {
        this.passwordCipher = passwordCipher;
    }

    public String getAppKey()
    {
        return appKey;
    }

    public void setAppKey(String appKey)
    {
        this.appKey = appKey;
    }

    public String getSecret()
    {
        return secret;
    }

    public void setSecret(String secret)
    {
        this.secret = secret;
    }

    public String getSecretCipher()
    {
        return secretCipher;
    }

    public void setSecretCipher(String secretCipher)
    {
        this.secretCipher = secretCipher;
    }

    public String getResultPath()
    {
        return resultPath;
    }

    public void setResultPath(String resultPath)
    {
        this.resultPath = resultPath;
    }

    public String getExtraParams()
    {
        return extraParams;
    }

    public void setExtraParams(String extraParams)
    {
        this.extraParams = extraParams;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }
}
