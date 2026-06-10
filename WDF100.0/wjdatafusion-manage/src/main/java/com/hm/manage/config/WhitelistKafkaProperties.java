package com.hm.manage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "whitelist.kafka")
public class WhitelistKafkaProperties
{
    private String bootstrapServers;
    private String topic;
    private String groupId;
    private String autoOffsetReset = "earliest";
    private Integer pollTimeoutMs = 3000;
    private Integer maxPollRecords = 100;
    private Integer pullRounds = 3;
    private String securityProtocol;
    private String saslMechanism;
    private String saslJaasConfig;

    public String getBootstrapServers()
    {
        return bootstrapServers;
    }

    public void setBootstrapServers(String bootstrapServers)
    {
        this.bootstrapServers = bootstrapServers;
    }

    public String getTopic()
    {
        return topic;
    }

    public void setTopic(String topic)
    {
        this.topic = topic;
    }

    public String getGroupId()
    {
        return groupId;
    }

    public void setGroupId(String groupId)
    {
        this.groupId = groupId;
    }

    public String getAutoOffsetReset()
    {
        return autoOffsetReset;
    }

    public void setAutoOffsetReset(String autoOffsetReset)
    {
        this.autoOffsetReset = autoOffsetReset;
    }

    public Integer getPollTimeoutMs()
    {
        return pollTimeoutMs;
    }

    public void setPollTimeoutMs(Integer pollTimeoutMs)
    {
        this.pollTimeoutMs = pollTimeoutMs;
    }

    public Integer getMaxPollRecords()
    {
        return maxPollRecords;
    }

    public void setMaxPollRecords(Integer maxPollRecords)
    {
        this.maxPollRecords = maxPollRecords;
    }

    public Integer getPullRounds()
    {
        return pullRounds;
    }

    public void setPullRounds(Integer pullRounds)
    {
        this.pullRounds = pullRounds;
    }

    public String getSecurityProtocol()
    {
        return securityProtocol;
    }

    public void setSecurityProtocol(String securityProtocol)
    {
        this.securityProtocol = securityProtocol;
    }

    public String getSaslMechanism()
    {
        return saslMechanism;
    }

    public void setSaslMechanism(String saslMechanism)
    {
        this.saslMechanism = saslMechanism;
    }

    public String getSaslJaasConfig()
    {
        return saslJaasConfig;
    }

    public void setSaslJaasConfig(String saslJaasConfig)
    {
        this.saslJaasConfig = saslJaasConfig;
    }
}
