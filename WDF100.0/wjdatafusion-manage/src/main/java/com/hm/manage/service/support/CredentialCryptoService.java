package com.hm.manage.service.support;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import jakarta.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.hm.common.exception.ServiceException;
import com.hm.common.utils.StringUtils;
import com.hm.manage.config.SupportCredentialProperties;

@Component
public class CredentialCryptoService
{
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_LENGTH_BIT = 128;

    @Autowired
    private SupportCredentialProperties properties;

    @PostConstruct
    public void validateKeyOnStartup()
    {
        if (StringUtils.isEmpty(properties.getKey()))
        {
            throw new ServiceException("未配置 support.credential.key（可通过环境变量 SUPPORT_AES_KEY 注入）");
        }
    }

    public String encrypt(String plainText)
    {
        if (StringUtils.isEmpty(plainText))
        {
            return StringUtils.EMPTY;
        }
        try
        {
            byte[] key = resolveKey();
            byte[] nonce = new byte[NONCE_LENGTH];
            SecureRandom random = new SecureRandom();
            random.nextBytes(nonce);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, nonce);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), gcmParameterSpec);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(nonce.length + encrypted.length);
            buffer.put(nonce);
            buffer.put(encrypted);
            return Base64.getEncoder().encodeToString(buffer.array());
        }
        catch (Exception e)
        {
            throw new ServiceException("敏感信息加密失败");
        }
    }

    public String decrypt(String cipherText)
    {
        if (StringUtils.isEmpty(cipherText))
        {
            return StringUtils.EMPTY;
        }
        try
        {
            byte[] allBytes = Base64.getDecoder().decode(cipherText);
            ByteBuffer buffer = ByteBuffer.wrap(allBytes);
            byte[] nonce = new byte[NONCE_LENGTH];
            buffer.get(nonce);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);

            byte[] key = resolveKey();
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH_BIT, nonce);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), spec);
            byte[] plain = cipher.doFinal(encrypted);
            return new String(plain, StandardCharsets.UTF_8);
        }
        catch (Exception e)
        {
            throw new ServiceException("敏感信息解密失败");
        }
    }

    public String mask(String plainText)
    {
        if (StringUtils.isEmpty(plainText))
        {
            return StringUtils.EMPTY;
        }
        if (plainText.length() <= 2)
        {
            return "******";
        }
        return plainText.substring(0, 1) + "******" + plainText.substring(plainText.length() - 1);
    }

    private byte[] resolveKey() throws Exception
    {
        if (StringUtils.isEmpty(properties.getKey()))
        {
            throw new ServiceException("未配置 support.credential.key（可通过环境变量 SUPPORT_AES_KEY 注入）");
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return digest.digest(properties.getKey().getBytes(StandardCharsets.UTF_8));
    }
}
