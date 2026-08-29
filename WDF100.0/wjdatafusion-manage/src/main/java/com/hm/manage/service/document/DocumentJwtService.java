package com.hm.manage.service.document;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.hm.common.exception.ServiceException;
import com.hm.common.utils.StringUtils;
import com.hm.manage.config.DocumentManagementProperties;

@Component
public class DocumentJwtService
{
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final String HEADER = ENCODER.encodeToString(
        "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

    @Autowired
    private DocumentManagementProperties properties;

    public String sign(Map<String, Object> claims, Duration lifetime)
    {
        requireSecret();
        Instant now = Instant.now();
        Map<String, Object> payload = new LinkedHashMap<>(claims);
        payload.put("iat", now.getEpochSecond());
        payload.put("exp", now.plus(lifetime).getEpochSecond());
        String encodedPayload = ENCODER.encodeToString(JSON.toJSONBytes(payload));
        String signingInput = HEADER + "." + encodedPayload;
        return signingInput + "." + ENCODER.encodeToString(hmac(signingInput));
    }

    public Map<String, Object> verify(String token)
    {
        requireSecret();
        if (StringUtils.isBlank(token))
        {
            throw new ServiceException("编辑器签名缺失");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3)
        {
            throw new ServiceException("编辑器签名格式无效");
        }
        String signingInput = parts[0] + "." + parts[1];
        byte[] actual;
        try
        {
            actual = DECODER.decode(parts[2]);
        }
        catch (IllegalArgumentException exception)
        {
            throw new ServiceException("编辑器签名格式无效");
        }
        if (!parts[2].equals(ENCODER.encodeToString(actual))
            || !MessageDigest.isEqual(hmac(signingInput), actual))
        {
            throw new ServiceException("编辑器签名校验失败");
        }
        try
        {
            Map<String, Object> claims = JSON.parseObject(new String(DECODER.decode(parts[1]), StandardCharsets.UTF_8),
                new TypeReference<Map<String, Object>>() { });
            long expiresAt = numberValue(claims.get("exp"));
            if (expiresAt <= Instant.now().getEpochSecond())
            {
                throw new ServiceException("编辑器签名已过期");
            }
            return claims;
        }
        catch (ServiceException exception)
        {
            throw exception;
        }
        catch (Exception exception)
        {
            throw new ServiceException("编辑器签名内容无效");
        }
    }

    public long numberValue(Object value)
    {
        if (value instanceof Number number)
        {
            return number.longValue();
        }
        try
        {
            return Long.parseLong(String.valueOf(value));
        }
        catch (Exception exception)
        {
            throw new ServiceException("编辑器签名字段无效");
        }
    }

    private byte[] hmac(String content)
    {
        try
        {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getOnlyOffice().getJwtSecret().getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"));
            return mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
        }
        catch (Exception exception)
        {
            throw new IllegalStateException("无法生成编辑器签名", exception);
        }
    }

    private void requireSecret()
    {
        String secret = properties.getOnlyOffice().getJwtSecret();
        if (StringUtils.isBlank(secret) || secret.length() < 32)
        {
            throw new ServiceException("ONLYOFFICE JWT 密钥未配置或长度不足32位");
        }
    }
}
