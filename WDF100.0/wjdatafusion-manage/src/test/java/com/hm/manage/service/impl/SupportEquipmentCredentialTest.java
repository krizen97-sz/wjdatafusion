package com.hm.manage.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.util.ReflectionTestUtils;
import com.hm.common.annotation.Log;
import com.hm.common.exception.ServiceException;
import com.hm.manage.controller.SupportEquipmentController;
import com.hm.manage.controller.SupportSubplatformEndpointController;
import com.hm.manage.domain.SupportServer;
import com.hm.manage.domain.SupportHardwareAsset;
import com.hm.manage.domain.SupportServerCredential;
import com.hm.manage.domain.SupportSubplatformEndpoint;
import com.hm.manage.domain.bo.SupportEquipmentBatchBo;
import com.hm.manage.domain.bo.SupportEquipmentDeviceRefBo;
import com.hm.manage.service.ISupportServerService;
import com.hm.manage.service.ISupportHardwareAssetService;

class SupportEquipmentCredentialTest
{
    private SupportEquipmentCredentialService service;
    private ISupportServerService servers;
    private ISupportHardwareAssetService hardware;

    @BeforeEach
    void setup()
    {
        service = new SupportEquipmentCredentialService();
        servers = mock(ISupportServerService.class);
        hardware = mock(ISupportHardwareAssetService.class);
        ReflectionTestUtils.setField(service, "serverService", servers);
        ReflectionTestUtils.setField(service, "hardwareAssetService", hardware);
    }

    private SupportEquipmentDeviceRefBo ref(String type, long id)
    {
        var ref = new SupportEquipmentDeviceRefBo();
        ref.setSourceType(type);
        ref.setSourceId(id);
        return ref;
    }

    private SupportEquipmentBatchBo command(SupportEquipmentDeviceRefBo... refs)
    {
        var command = new SupportEquipmentBatchBo();
        command.setSiteId(1L);
        command.setDevices(List.of(refs));
        return command;
    }

    @Test
    void rejectsEmptyOversizedUnknownAndCrossSiteReferencesBeforeDecrypting()
    {
        assertThrows(ServiceException.class, () -> service.readPlain(null));
        assertThrows(ServiceException.class, () -> service.readPlain(command()));
        var oversized = command();
        oversized.setDevices(java.util.Collections.nCopies(101, ref("SERVER", 1)));
        assertThrows(ServiceException.class, () -> service.readPlain(oversized));
        assertThrows(ServiceException.class, () -> service.readPlain(command(ref("invalid", 1))));
        var server = new SupportServer();
        server.setServerId(1L); server.setSiteId(1L);
        when(servers.selectSupportServerByServerId(1L)).thenReturn(server);
        var other = new SupportHardwareAsset();
        other.setAssetId(1L); other.setSiteId(2L);
        when(hardware.selectSupportHardwareAssetByAssetId(1L)).thenReturn(other);
        assertThrows(ServiceException.class, () -> service.readPlain(command(ref("SERVER", 1), ref("HARDWARE", 1))));
        verify(servers, never()).getServerCredentialPasswordPlain(any());
        verify(servers, never()).selectServerCredentialList(any());
        verify(hardware, never()).getHardwareAssetPasswordPlain(any());
    }

    @Test
    void returnsAllCredentialsAndKeepsTypedIdsDistinctWithoutDuplicatingRows()
    {
        var server = new SupportServer();
        server.setServerId(1L); server.setSiteId(1L);
        when(servers.selectSupportServerByServerId(1L)).thenReturn(server);
        var credential = new SupportServerCredential();
        credential.setCredentialId(10L); credential.setUsername("root");
        var legacy = new SupportServerCredential();
        legacy.setUsername("HIK"); legacy.setLegacyRegistration(true);
        server.setOsPasswordCipher("legacy-cipher");
        when(servers.selectServerCredentialList(1L)).thenReturn(List.of(credential, legacy));
        var crypto = mock(com.hm.manage.service.support.CredentialCryptoService.class);
        ReflectionTestUtils.setField(service, "cryptoService", crypto);
        when(crypto.decrypt("legacy-cipher")).thenReturn("legacy-hik");
        when(servers.getServerCredentialPasswordPlain(10L)).thenReturn("test-root");
        var asset = new SupportHardwareAsset();
        asset.setAssetId(1L); asset.setSiteId(1L); asset.setLoginUsername("admin");
        when(hardware.selectSupportHardwareAssetByAssetId(1L)).thenReturn(asset);
        when(hardware.getHardwareAssetPasswordPlain(1L)).thenReturn("test-hardware");
        var result = service.readPlain(command(ref("SERVER", 1), ref("HARDWARE", 1), ref("SERVER", 1)));
        assertEquals(2, result.size());
        assertEquals("SERVER:1", result.get(0).get("deviceKey"));
        assertEquals("HARDWARE:1", result.get(1).get("deviceKey"));
        assertEquals(2, ((List<?>) result.get(0).get("credentials")).size());
        verify(servers, times(1)).getServerCredentialPasswordPlain(10L);
        verify(servers, never()).getServerCredentialPasswordPlain(null);
        verify(servers, never()).selectServerCredentialPlainSummaries(any());
        verify(crypto).decrypt("legacy-cipher");
    }

    @Test
    void plainEndpointsRequireCredentialPermissionAndNeverLogResponseBodies() throws Exception
    {
        var batch = SupportEquipmentController.class.getMethod("credentialPlain", SupportEquipmentBatchBo.class, HttpServletResponse.class);
        var page = SupportSubplatformEndpointController.class.getMethod("viewPlain", Long.class, HttpServletResponse.class);
        for (var method : List.of(batch, page))
        {
            assertTrue(method.getAnnotation(PreAuthorize.class).value().contains("support:credential:viewPlain"));
            assertFalse(method.getAnnotation(Log.class).isSaveResponseData());
        }
        for (String method : List.of("add", "edit"))
            assertFalse(SupportSubplatformEndpointController.class.getMethod(method, SupportSubplatformEndpoint.class).getAnnotation(Log.class).isSaveRequestData());
    }
}
