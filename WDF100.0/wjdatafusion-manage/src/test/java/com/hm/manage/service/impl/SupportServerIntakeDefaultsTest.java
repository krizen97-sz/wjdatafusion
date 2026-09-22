package com.hm.manage.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.hm.common.exception.ServiceException;
import com.hm.manage.domain.SupportServer;
import com.hm.manage.mapper.SupportServerMapper;
import com.hm.manage.mapper.SupportSiteMapper;

class SupportServerIntakeDefaultsTest
{
    private final SupportServerServiceImpl service = new SupportServerServiceImpl();
    private SupportServerMapper mapper;

    @BeforeEach
    void setup()
    {
        mapper = mock(SupportServerMapper.class);
        ReflectionTestUtils.setField(service, "serverMapper", mapper);
        SupportSiteMapper sites = mock(SupportSiteMapper.class);
        when(sites.selectSiteIdForUpdate(1L)).thenReturn(1L);
        ReflectionTestUtils.setField(service, "siteMapper", sites);
    }

    private SupportServer server(Integer port)
    {
        SupportServer server = new SupportServer();
        server.setSiteId(1L);
        server.setServerName("intake-test");
        server.setServerAddress("198.18.0.1");
        server.setSshPort(port);
        return server;
    }

    @Test
    void newServerDefaultsTo55555AndPreservesExplicit22()
    {
        SupportServer defaultPort = server(null);
        service.insertSupportServer(defaultPort);
        assertEquals(55555, defaultPort.getSshPort());
        SupportServer explicitPort = server(22);
        service.insertSupportServer(explicitPort);
        assertEquals(22, explicitPort.getSshPort());
    }

    @Test
    void updateWithoutPortPreservesStoredPort()
    {
        SupportServer original = server(2222);
        original.setServerId(9L);
        when(mapper.selectSupportServerByServerId(9L)).thenReturn(original);
        SupportServer update = server(null);
        update.setServerId(9L);
        service.updateSupportServer(update);
        assertEquals(2222, update.getSshPort());
        original.setSshPort(22);
        update.setSshPort(null);
        service.updateSupportServer(update);
        assertEquals(22, update.getSshPort());
    }

    @Test
    void invalidPortStillRejectsInsteadOfUsingDefault()
    {
        assertThrows(ServiceException.class, () -> service.insertSupportServer(server(0)));
        assertThrows(ServiceException.class, () -> service.insertSupportServer(server(65536)));
        verifyNoInteractions(mapper);
    }
}
