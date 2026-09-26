package com.hm.manage.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.hm.common.exception.ServiceException;
import com.hm.manage.domain.SupportServer;
import com.hm.manage.domain.SupportServerCredential;
import com.hm.manage.domain.bo.SupportServerLoginCheckBo;
import com.hm.manage.domain.vo.SupportServerLoginCheckVo;
import com.hm.manage.mapper.SupportServerMapper;
import com.hm.manage.mapper.SupportServerCredentialMapper;
import com.hm.manage.service.support.CredentialCryptoService;
import com.hm.manage.service.support.SupportServerLoginVerifier;

class SupportServerLoginCheckServiceTest
{
    private SupportServerMapper servers;
    private SupportServerCredentialMapper accounts;
    private CredentialCryptoService crypto;
    private SupportServerLoginVerifier verifier;
    private SupportServerLoginCheckService service;
    private SupportServer server;
    @BeforeEach void setup()
    {
        servers = mock(SupportServerMapper.class); accounts = mock(SupportServerCredentialMapper.class);
        crypto = mock(CredentialCryptoService.class); verifier = mock(SupportServerLoginVerifier.class);
        service = new SupportServerLoginCheckService(servers, accounts, crypto, verifier);
        server = new SupportServer(); server.setServerId(1L); server.setSiteId(9L); server.setStatus("0");
        server.setServerAddress("192.0.2.1"); server.setSshPort(55555);
        when(servers.selectSupportServerByServerId(1L)).thenReturn(server);
        when(crypto.decrypt(anyString())).thenAnswer(call -> "plain-" + call.getArgument(0));
        when(verifier.verify(anyString(), anyInt(), anyString(), anyString(), anyString(), anyBoolean(), any()))
            .thenReturn(result("SUCCESS"));
    }
    @Test void hikThenSuUsesSavedActualUsernameAndBothSecrets()
    {
        when(accounts.selectCredentialsByServerId(1L)).thenReturn(List.of(account("HIK", "h"), account("root", "r")));
        service.check(command());
        verify(verifier).verify("192.0.2.1", 55555, "HIK", "plain-h", "plain-r", false, null);
    }
    @Test void rootOnlyDirectEvenWhenPolicyIsOff()
    {
        when(accounts.selectCredentialsByServerId(1L)).thenReturn(List.of(account("root", "r")));
        service.check(command());
        verify(verifier).verify("192.0.2.1", 55555, "root", "plain-r", "plain-r", true, null);
    }
    @Test void explicitDirectIgnoresHikAndPreservesHistoricalPort()
    {
        server.setRootDirectLogin(true); server.setSshPort(22);
        when(accounts.selectCredentialsByServerId(1L)).thenReturn(List.of(account("hik", "h"), account("hik", "h2"), account("root", "r")));
        service.check(command());
        verify(verifier).verify("192.0.2.1", 22, "root", "plain-r", "plain-r", true, null);
        verify(crypto, never()).decrypt("h");
    }
    @Test void rootRequiredNoNetworkAttempt()
    {
        when(accounts.selectCredentialsByServerId(1L)).thenReturn(List.of(account("hik", "h")));
        assertThrows(ServiceException.class, () -> service.check(command()));
        verifyNoInteractions(verifier, crypto);
    }
    @Test void duplicateOrDisabledCredentialsAreNotGuessed()
    {
        when(accounts.selectCredentialsByServerId(1L)).thenReturn(List.of(account("root", "r"), account("ROOT", "r2")));
        assertThrows(ServiceException.class, () -> service.check(command()));
        SupportServerCredential disabled = account("root", "r"); disabled.setStatus("1");
        when(accounts.selectCredentialsByServerId(1L)).thenReturn(List.of(disabled));
        assertThrows(ServiceException.class, () -> service.check(command()));
        verifyNoInteractions(verifier);
    }
    @Test void legacyRootCipherCanBeUsedWithoutMutatingIt()
    {
        server.setOsUsername("root"); server.setOsPasswordCipher("legacy"); server.setSshPort(null);
        when(accounts.selectCredentialsByServerId(1L)).thenReturn(List.of());
        service.check(command());
        verify(verifier).verify("192.0.2.1", 55555, "root", "plain-legacy", "plain-legacy", true, null);
        verify(servers, never()).updateSupportServer(any());
    }
    @Test void clearedCredentialDoesNotReviveLegacyPassword()
    {
        server.setOsUsername("root"); server.setOsPasswordCipher("legacy");
        when(accounts.selectCredentialsByServerId(1L)).thenReturn(List.of(account("root", "")));
        assertThrows(ServiceException.class, () -> service.check(command()));
        verifyNoInteractions(verifier, crypto);
    }
    @Test void failedHikDoesNotFallbackAndIsThrottled()
    {
        when(accounts.selectCredentialsByServerId(1L)).thenReturn(List.of(account("hik", "h"), account("root", "r")));
        when(verifier.verify(anyString(), anyInt(), anyString(), anyString(), anyString(), anyBoolean(), any())).thenReturn(result("AUTH_REJECTED"));
        assertEquals("AUTH_REJECTED", service.check(command()).status());
        assertThrows(ServiceException.class, () -> service.check(command()));
        verify(verifier, times(1)).verify(anyString(), anyInt(), eq("hik"), anyString(), anyString(), eq(false), any());
    }
    @Test void hostConfirmationDoesNotConsumePasswordAttempt()
    {
        when(accounts.selectCredentialsByServerId(1L)).thenReturn(List.of(account("root", "r")));
        when(verifier.verify(anyString(), anyInt(), anyString(), anyString(), anyString(), anyBoolean(), any())).thenReturn(result("HOST_KEY_REQUIRED"));
        service.check(command()); service.check(command());
        verify(verifier, times(2)).verify(anyString(), anyInt(), anyString(), anyString(), anyString(), anyBoolean(), any());
    }
    @Test void invalidScopeStoppedAndCipherNeverDecrypted()
    {
        assertThrows(ServiceException.class, () -> service.check(new SupportServerLoginCheckBo(99L, 1L, null)));
        server.setStatus("1");
        assertThrows(ServiceException.class, () -> service.check(command()));
        verifyNoInteractions(accounts, crypto, verifier);
    }
    @Test void controlCharactersNotSentToSu()
    {
        when(accounts.selectCredentialsByServerId(1L)).thenReturn(List.of(account("hik", "h"), account("root", "r")));
        when(crypto.decrypt("r")).thenReturn("bad\nsecret");
        assertThrows(ServiceException.class, () -> service.check(command()));
        verifyNoInteractions(verifier);
    }
    private SupportServerLoginCheckBo command() { return new SupportServerLoginCheckBo(9L, 1L, null); }
    private SupportServerCredential account(String user, String cipher)
    {
        var account = new SupportServerCredential(); account.setUsername(user); account.setPasswordCipher(cipher); account.setStatus("0"); return account;
    }
    private SupportServerLoginCheckVo result(String status)
    {
        return new SupportServerLoginCheckVo(status, "safe message", "ROOT_DIRECT", "root", "192.0.2.1", 55555, null, false, "2026-09-26 22:30:00", List.of());
    }
}
