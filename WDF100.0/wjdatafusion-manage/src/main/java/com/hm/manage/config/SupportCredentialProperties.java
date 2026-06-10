package com.hm.manage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "support.credential")
public class SupportCredentialProperties
{
    /**
     * AES密钥，建议通过环境变量 SUPPORT_AES_KEY 注入。
     */
    private String key;

    public String getKey()
    {
        return key;
    }

    public void setKey(String key)
    {
        this.key = key;
    }
}
