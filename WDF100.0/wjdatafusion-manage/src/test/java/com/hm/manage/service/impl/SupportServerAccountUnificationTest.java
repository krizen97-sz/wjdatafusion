package com.hm.manage.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.hm.common.exception.ServiceException;
import com.hm.common.utils.SecurityUtils;
import com.hm.manage.domain.SupportServer;
import com.hm.manage.domain.SupportServerCredential;
import com.hm.manage.mapper.SupportServerMapper;
import com.hm.manage.mapper.SupportServerCredentialMapper;
import com.hm.manage.mapper.SupportSiteMapper;
import com.hm.manage.service.ISupportChangeLogService;
import com.hm.manage.service.support.CredentialCryptoService;

class SupportServerAccountUnificationTest
{
    private SupportServerServiceImpl service;
    private SupportServerMapper servers;
    private SupportServerCredentialMapper credentials;
    private SupportSiteMapper sites;
    private CredentialCryptoService crypto;
    private SupportServer server;
    private List<SupportServerCredential> accounts;

    @BeforeEach void setup()
    {
        service = new SupportServerServiceImpl();
        servers = mock(SupportServerMapper.class); credentials = mock(SupportServerCredentialMapper.class);
        sites = mock(SupportSiteMapper.class); crypto = mock(CredentialCryptoService.class);
        ReflectionTestUtils.setField(service, "serverMapper", servers);
        ReflectionTestUtils.setField(service, "credentialMapper", credentials);
        ReflectionTestUtils.setField(service, "siteMapper", sites);
        ReflectionTestUtils.setField(service, "cryptoService", crypto);
        ReflectionTestUtils.setField(service, "changeLogService", mock(ISupportChangeLogService.class));
        server = new SupportServer(); server.setServerId(1L); server.setSiteId(9L);
        server.setServerName("test"); server.setOsUsername("HIK"); server.setOsPasswordCipher("legacy-cipher");
        accounts = new ArrayList<>();
        when(servers.selectSupportServerByServerId(1L)).thenReturn(server);
        when(servers.selectSupportServerByServerIdForUpdate(1L)).thenReturn(server);
        when(sites.selectSiteIdForUpdate(9L)).thenReturn(9L);
        when(credentials.selectCredentialsByServerId(1L)).thenAnswer(call -> accounts);
        when(credentials.selectCredentialsByServerIdForUpdate(1L)).thenAnswer(call -> accounts);
        when(crypto.encrypt(anyString())).thenReturn("new-cipher");
        when(credentials.insertCredential(any())).thenAnswer(call -> {
            ((SupportServerCredential) call.getArgument(0)).setCredentialId(100L); return 1;
        });
        when(credentials.updateCredential(any())).thenReturn(1);
        when(credentials.deleteCredentialById(anyLong())).thenReturn(1);
        when(servers.clearLegacyCredential(anyLong(), anyString(), any(), any())).thenReturn(1);
    }
    @Test void legacyAndStoredAccountsAreVisibleTogetherWithoutDatabaseWrites() throws Exception
    {
        accounts.add(account(10L, "root", "root-cipher"));
        var result = service.selectServerCredentialList(1L);
        assertEquals(2, result.size());
        var legacy = result.get(1);
        assertTrue(legacy.getLegacyRegistration()); assertNull(legacy.getCredentialId());
        assertEquals("hik", legacy.getUsername()); assertEquals("******", legacy.getPassword());
        assertEquals(1, accounts.size());
        String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(result);
        assertFalse(json.contains("legacy-cipher")); assertFalse(json.contains("root-cipher"));
        verify(credentials, never()).insertCredential(any());
        verify(servers, never()).clearLegacyCredential(any(), any(), any(), any());
        verifyNoInteractions(crypto);
    }
    @Test void aRegisteredAccountShadowsLegacyEvenWhenItsPasswordWasCleared()
    {
        accounts.add(account(10L, "hik", ""));
        var result = service.selectServerCredentialList(1L);
        assertEquals(1, result.size()); assertFalse(result.get(0).getLegacyRegistration());
        assertEquals("", service.selectServerCredentialPlainSummaries(new Long[] {1L}).get(0).get("hikPassword"));
        assertFalse(service.selectSupportServerByServerId(1L).getHikCredentialConfigured());
        verifyNoInteractions(crypto);
    }
    @Test void explicitLegacyEditPreservesUnchangedCipherAndMovesOneAccount()
    {
        var input = account(null, "HIK", null); input.setLegacyRegistration(true);
        service.insertServerCredential(input);
        assertEquals("legacy-cipher", input.getPasswordCipher());
        assertEquals("hik", input.getUsername());
        verify(credentials, times(1)).insertCredential(input);
        verify(servers).clearLegacyCredential(eq(1L), eq("hik"), any(), any());
        verify(servers, never()).updateSupportServer(any());
    }
    @Test void legacyEditWithNewPasswordUsesEncryption()
    {
        var input = account(null, "HIK", null); input.setLegacyRegistration(true); input.setPassword("test-secret");
        service.insertServerCredential(input);
        assertEquals("new-cipher", input.getPasswordCipher()); assertNull(input.getPassword());
        verify(crypto).encrypt("test-secret");
    }
    @Test void fabricatedOrAlreadyMigratedLegacyEditIsRejected()
    {
        var input = account(null, "root", null); input.setLegacyRegistration(true);
        assertThrows(ServiceException.class, () -> service.insertServerCredential(input));
        accounts.add(account(10L, "HIK", "stored"));
        input.setUsername("HIK");
        assertThrows(ServiceException.class, () -> service.insertServerCredential(input));
        verify(credentials, never()).insertCredential(any());
    }
    @Test void hikCaseIsCanonicalizedAndDuplicateInsertIsRejected()
    {
        var input = account(null, "HIK", null); input.setPassword("new");
        service.insertServerCredential(input);
        assertEquals("hik", input.getUsername());
        accounts.add(account(10L, "hik", "stored"));
        assertThrows(ServiceException.class, () -> service.insertServerCredential(input));
    }
    @Test void blankPasswordEditUsesCurrentLockedCredentialNotEarlierSnapshot()
    {
        var old = account(10L, "HIK", "old");
        var current = account(10L, "HIK", "concurrent-new");
        when(credentials.selectCredentialById(10L)).thenReturn(old);
        when(credentials.selectCredentialByIdForUpdate(10L)).thenReturn(current);
        var input = account(10L, "HIK", null);
        service.updateServerCredential(input);
        assertEquals("concurrent-new", input.getPasswordCipher()); assertEquals("hik", input.getUsername());
        verify(servers).clearLegacyCredential(eq(1L), eq("hik"), any(), any());
    }
    @Test void deletingStoredAccountClearsOnlyItsMatchingLegacyShadow()
    {
        var account = account(10L, "HIK", "stored");
        when(credentials.selectCredentialById(10L)).thenReturn(account);
        when(credentials.selectCredentialByIdForUpdate(10L)).thenReturn(account);
        try (var security = mockStatic(SecurityUtils.class))
        {
            security.when(SecurityUtils::getUsername).thenReturn("tester");
            service.deleteServerCredentialById(10L);
        }
        verify(servers).clearLegacyCredential(eq(1L), eq("hik"), eq("tester"), any());
        verify(credentials).deleteCredentialById(10L);
        verify(credentials, never()).deleteCredentialsByServerId(any());
    }
    @Test void deletingLegacyRequiresSameCurrentUsernameAndLeavesOtherAccountsAlone()
    {
        accounts.add(account(10L, "root", "stored"));
        assertThrows(ServiceException.class, () -> service.deleteLegacyServerCredential(1L, "old-name"));
        try (var security = mockStatic(SecurityUtils.class))
        {
            security.when(SecurityUtils::getUsername).thenReturn("tester");
            service.deleteLegacyServerCredential(1L, "HIK");
        }
        verify(servers).clearLegacyCredential(eq(1L), eq("hik"), eq("tester"), any());
        verify(credentials, never()).deleteCredentialById(any());
    }
    private SupportServerCredential account(Long id, String username, String cipher)
    {
        var value = new SupportServerCredential(); value.setCredentialId(id); value.setServerId(1L);
        value.setUsername(username); value.setCredentialName(username + " account"); value.setStatus("0");
        value.setPasswordCipher(cipher); value.setCreateBy("tester"); value.setUpdateBy("tester");
        return value;
    }
}
