package com.hm.manage.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import com.hm.common.exception.ServiceException;
import com.hm.manage.domain.SupportHardwareAsset;
import com.hm.manage.domain.SupportPlatform;
import com.hm.manage.domain.SupportServer;
import com.hm.manage.domain.SupportSite;
import com.hm.manage.domain.bo.SupportEquipmentCreateBo;
import com.hm.manage.mapper.SupportSiteMapper;
import com.hm.manage.service.ISupportHardwareAssetService;
import com.hm.manage.service.ISupportServerService;
import com.hm.manage.service.ISupportPlatformService;

class SupportEquipmentCreateTest
{
    private SupportEquipmentServiceImpl service;
    private ISupportServerService servers;
    private ISupportHardwareAssetService hardware;
    private ISupportPlatformService platforms;
    private SupportSiteMapper sites;

    @BeforeEach
    void setup()
    {
        service = new SupportEquipmentServiceImpl();
        servers = mock(ISupportServerService.class);
        hardware = mock(ISupportHardwareAssetService.class);
        platforms = mock(ISupportPlatformService.class);
        sites = mock(SupportSiteMapper.class);
        ReflectionTestUtils.setField(service, "serverService", servers);
        ReflectionTestUtils.setField(service, "hardwareAssetService", hardware);
        ReflectionTestUtils.setField(service, "platformService", platforms);
        ReflectionTestUtils.setField(service, "siteMapper", sites);
        when(sites.selectSupportSiteBySiteId(1L)).thenReturn(new SupportSite());
        when(servers.insertSupportServer(any())).thenAnswer(invocation -> {
            SupportServer server = invocation.getArgument(0);
            server.setServerId(10L);
            return 1;
        });
    }

    private SupportEquipmentCreateBo serverCommand(Long platformId)
    {
        SupportEquipmentCreateBo command = new SupportEquipmentCreateBo();
        command.setSiteId(1L);
        command.setPlatformId(platformId);
        SupportServer server = new SupportServer();
        server.setServerName("server");
        server.setServerAddress("10.0.0.1");
        server.setSiteId(99L);
        server.setServerId(999L);
        server.setEquipmentRoom("untrusted placement");
        command.setServer(server);
        return command;
    }

    private void platform(String level, Long siteId)
    {
        SupportPlatform platform = new SupportPlatform();
        platform.setPlatformId(2L);
        platform.setSiteId(siteId);
        platform.setPlatformLevel(level);
        when(platforms.selectSupportPlatformByPlatformId(2L)).thenReturn(platform);
    }

    @Test
    void standaloneServerReturnsIdentityAndIgnoresProtectedInput()
    {
        var result = service.createEquipment(serverCommand(null));
        assertEquals("SERVER", result.getSourceType());
        assertEquals(10L, result.getSourceId());
        var captor = ArgumentCaptor.forClass(SupportServer.class);
        verify(servers).insertSupportServer(captor.capture());
        assertEquals(1L, captor.getValue().getSiteId());
        assertNull(captor.getValue().getEquipmentRoom());
        verifyNoInteractions(platforms, hardware);
    }

    @Test
    void serverWithSubplatformCreatesAndBindsInOrder()
    {
        platform("SUB", 1L);
        when(platforms.bindServer(2L, 10L)).thenReturn(1);
        service.createEquipment(serverCommand(2L));
        var order = inOrder(servers, platforms);
        order.verify(platforms).selectSupportPlatformByPlatformId(2L);
        order.verify(servers).insertSupportServer(any());
        order.verify(platforms).bindServer(2L, 10L);
    }

    @Test
    void foreignOrMainPlatformRejectsBeforeAnyInsert()
    {
        platform("SUB", 9L);
        assertThrows(ServiceException.class, () -> service.createEquipment(serverCommand(2L)));
        platform("MAIN", 1L);
        assertThrows(ServiceException.class, () -> service.createEquipment(serverCommand(2L)));
        verifyNoInteractions(servers, hardware);
    }

    @Test
    void ambiguousOrMissingSiteRejectsBeforeAnyInsert()
    {
        var command = serverCommand(null);
        command.setHardware(new SupportHardwareAsset());
        assertThrows(ServiceException.class, () -> service.createEquipment(command));
        command.setHardware(null);
        command.setSiteId(99L);
        assertThrows(ServiceException.class, () -> service.createEquipment(command));
        verifyNoInteractions(servers, hardware);
    }

    @Test
    void failedBindingRaisesErrorWithinRollbackTransaction() throws Exception
    {
        platform("SUB", 1L);
        when(platforms.bindServer(2L, 10L)).thenReturn(0);
        assertThrows(ServiceException.class, () -> service.createEquipment(serverCommand(2L)));
        var tx = SupportEquipmentServiceImpl.class.getMethod("createEquipment", SupportEquipmentCreateBo.class)
            .getAnnotation(org.springframework.transaction.annotation.Transactional.class);
        assertArrayEquals(new Class<?>[] { Exception.class }, tx.rollbackFor());
    }

    @Test
    void publicHardwarePreservesCredentialsButNotIdentity()
    {
        var command = new SupportEquipmentCreateBo();
        command.setSiteId(1L);
        var asset = new SupportHardwareAsset();
        asset.setAssetType("SWITCH");
        asset.setAssetName("Switch");
        asset.setNetworkEnv("POLICE");
        asset.setLoginPassword(" private ");
        asset.setAssetId(999L);
        asset.setPlatformId(99L);
        command.setHardware(asset);
        when(hardware.insertSupportHardwareAsset(any())).thenAnswer(invocation -> {
            SupportHardwareAsset created = invocation.getArgument(0);
            assertNull(created.getAssetId());
            assertNull(created.getPlatformId());
            assertEquals(" private ", created.getLoginPassword());
            created.setAssetId(11L);
            return 1;
        });
        var result = service.createEquipment(command);
        assertEquals("HARDWARE", result.getSourceType());
        assertEquals(11L, result.getSourceId());
        verifyNoInteractions(servers, platforms);
    }
}
