package com.hm.manage.service.impl;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import org.springframework.stereotype.Service;
import com.hm.common.exception.ServiceException;
import com.hm.manage.domain.SupportServer;
import com.hm.manage.domain.SupportServerCredential;
import com.hm.manage.domain.bo.SupportServerLoginCheckBo;
import com.hm.manage.domain.vo.SupportServerLoginCheckVo;
import com.hm.manage.mapper.SupportServerMapper;
import com.hm.manage.mapper.SupportServerCredentialMapper;
import com.hm.manage.service.support.CredentialCryptoService;
import com.hm.manage.service.support.SupportServerLoginVerifier;

@Service
public class SupportServerLoginCheckService
{
    private final SupportServerMapper servers;
    private final SupportServerCredentialMapper credentials;
    private final CredentialCryptoService crypto;
    private final SupportServerLoginVerifier verifier;
    private final Semaphore capacity = new Semaphore(4);
    private final Map<Long, Long> nextAttempt = new ConcurrentHashMap<>();
    private final java.util.Set<Long> running = ConcurrentHashMap.newKeySet();

    public SupportServerLoginCheckService(SupportServerMapper servers, SupportServerCredentialMapper credentials,
        CredentialCryptoService crypto, SupportServerLoginVerifier verifier)
    {
        this.servers = servers; this.credentials = credentials; this.crypto = crypto; this.verifier = verifier;
    }

    public SupportServerLoginCheckVo check(SupportServerLoginCheckBo command)
    {
        if (command == null || command.siteId() == null || command.serverId() == null)
            throw new ServiceException("请选择当前现场的服务器");
        SupportServer server = servers.selectSupportServerByServerId(command.serverId());
        if (server == null || !Objects.equals(server.getSiteId(), command.siteId()))
            throw new ServiceException("服务器不存在或不属于当前现场");
        if (!"0".equals(server.getStatus())) throw new ServiceException("停用服务器不能发起登录校验");
        String host = server.getServerAddress();
        int port = server.getSshPort() == null ? SupportServer.DEFAULT_SSH_PORT : server.getSshPort();
        if (host == null || host.isBlank() || host.contains("/") || host.contains("@") || host.matches(".*\\s+.*") || port < 1 || port > 65535)
            throw new ServiceException("请先配置正确的服务器地址和SSH端口");
        if (command.hostFingerprint() != null && !command.hostFingerprint().matches("SHA256:[A-Za-z0-9+/]{43}"))
            throw new ServiceException("SSH主机指纹格式无效");
        if (!running.add(server.getServerId())) throw new ServiceException("该服务器正在校验，请勿重复提交");
        boolean acquired = false;
        try
        {
            long now = System.currentTimeMillis();
            nextAttempt.entrySet().removeIf(entry -> entry.getValue() < now);
            if (nextAttempt.getOrDefault(server.getServerId(), 0L) > now)
                throw new ServiceException("该服务器刚执行过登录校验，请等待60秒后再试，避免账号锁定");
            acquired = capacity.tryAcquire();
            if (!acquired) throw new ServiceException("登录校验任务繁忙，请稍后重试");
            List<SupportServerCredential> accounts = credentials.selectCredentialsByServerId(server.getServerId());
            Account root = account(server, accounts, "root");
            Account hik = Boolean.TRUE.equals(server.getRootDirectLogin()) ? null : account(server, accounts, "hik");
            if (root == null) throw new ServiceException("未登记root密码，请先保存root账号密码");
            boolean direct = Boolean.TRUE.equals(server.getRootDirectLogin()) || hik == null;
            Account login = direct ? root : hik;
            String loginPassword = decrypt(login.cipher);
            String rootPassword = direct ? loginPassword : decrypt(root.cipher);
            if (!direct && rootPassword.chars().anyMatch(Character::isISOControl))
                throw new ServiceException("root密码包含终端控制字符，无法安全执行su密码校验");
            // No fallback or repeated password attempt: an authentication rejection may lock a real account.
            SupportServerLoginCheckVo result = verifier.verify(host, port, login.username, loginPassword,
                rootPassword, direct, command.hostFingerprint());
            if (!List.of("HOST_KEY_REQUIRED", "HOST_KEY_CHANGED").contains(result.status()))
                nextAttempt.put(server.getServerId(), System.currentTimeMillis() + 60000);
            return result;
        }
        finally
        {
            running.remove(server.getServerId());
            if (acquired) capacity.release();
        }
    }

    private Account account(SupportServer server, List<SupportServerCredential> accounts, String name)
    {
        List<SupportServerCredential> matches = accounts.stream()
            .filter(item -> name.equals(normalize(item.getUsername())))
            .toList();
        if (matches.size() > 1) throw new ServiceException(name + "账号存在多份凭据，请先在账号管理中保留唯一有效凭据");
        if (!matches.isEmpty())
        {
            SupportServerCredential credential = matches.get(0);
            if (!"0".equals(credential.getStatus())) throw new ServiceException(name + "账号凭据已停用");
            if (credential.getPasswordCipher() == null || credential.getPasswordCipher().isEmpty()) return null;
            return new Account(credential.getUsername().trim(), credential.getPasswordCipher());
        }
        if (name.equals(normalize(server.getOsUsername())) && server.getOsPasswordCipher() != null && !server.getOsPasswordCipher().isEmpty())
            return new Account(server.getOsUsername().trim(), server.getOsPasswordCipher());
        return null;
    }

    private String decrypt(String cipher)
    {
        try
        {
            String value = crypto.decrypt(cipher);
            if (value == null || value.isEmpty()) throw new IllegalArgumentException();
            return value;
        }
        catch (Exception e) { throw new ServiceException("凭据无法解密，请重新登记密码后校验"); }
    }

    private String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
    private record Account(String username, String cipher) {}
}
