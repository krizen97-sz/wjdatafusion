package com.hm.manage.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import com.hm.common.exception.ServiceException;
import com.hm.manage.domain.IpamAddress;
import com.hm.manage.domain.IpamNetwork;
import com.hm.manage.domain.IpamSegment;
import com.hm.manage.mapper.IpamMapper;
import com.hm.manage.mapper.IpamScanMapper;

class IpamServiceImplTest
{
    private IpamServiceImpl service;
    private IpamMapper mapper;

    @BeforeEach
    void setUp()
    {
        service = new IpamServiceImpl();
        mapper = mock(IpamMapper.class);
        ReflectionTestUtils.setField(service, "ipamMapper", mapper);
        ReflectionTestUtils.setField(service, "ipamScanMapper", mock(IpamScanMapper.class));
    }

    @Test
    void shouldRejectAnyOverlappingNetworkRange()
    {
        IpamNetwork input = new IpamNetwork();
        input.setNetworkName("新网段");
        input.setPoliceStationName("湖塘派出所");
        input.setGatewayIp("2.57.1.1");
        input.setSubnetMask("255.255.255.0");

        IpamNetwork overlap = new IpamNetwork();
        overlap.setNetworkName("已有网段");
        overlap.setStartIp("2.57.1.0");
        overlap.setEndIp("2.57.1.127");
        when(mapper.lockSettingRow("NETWORK_RANGE_LOCK")).thenReturn("1");
        when(mapper.selectOverlappingNetwork(any(), any(), any())).thenReturn(overlap);

        assertThrows(ServiceException.class, () -> service.insertNetwork(input));
        verify(mapper, never()).insertNetwork(any(IpamNetwork.class));
    }

    @Test
    void shouldRequirePoliceStationForNewNetwork()
    {
        IpamNetwork input = new IpamNetwork();
        input.setNetworkName("新网段");
        input.setGatewayIp("2.57.1.1");
        input.setSubnetMask("255.255.255.0");

        assertThrows(ServiceException.class, () -> service.insertNetwork(input));
        verify(mapper, never()).insertNetwork(any(IpamNetwork.class));
    }

    @Test
    void shouldPersistCredentialWithoutAKeyOrCipherColumn()
    {
        IpamSegment segment = new IpamSegment();
        segment.setSegmentId(10L);
        segment.setNetworkId(1L);
        segment.setCidrBlock("2.57.1.0/24");
        segment.setStartIp("2.57.1.0");
        segment.setEndIp("2.57.1.255");
        segment.setGatewayIp("2.57.1.1");
        segment.setStatus("0");
        when(mapper.selectSegmentById(10L)).thenReturn(segment);
        when(mapper.selectAddressByIp("2.57.1.10")).thenReturn(null);
        when(mapper.insertAddress(any(IpamAddress.class))).thenReturn(1);

        IpamAddress address = new IpamAddress();
        address.setSegmentId(10L);
        address.setIpAddress("2.57.1.10");
        address.setStatus("ALLOCATED");
        address.setCommunityName("第一小区");
        address.setLoginPassword("plain-secret");

        service.allocateAddress(address);

        ArgumentCaptor<IpamAddress> captor = ArgumentCaptor.forClass(IpamAddress.class);
        verify(mapper).insertAddress(captor.capture());
        assertEquals("plain-secret", captor.getValue().getLoginPassword());
        assertTrue(captor.getValue().getCredentialConfigured());
    }

    @Test
    void shouldViewCredentialDirectlyAndWriteSecurityAudit()
    {
        IpamAddress address = new IpamAddress();
        address.setAddressId(20L);
        address.setIpAddress("2.57.1.20");
        address.setLoginPassword("plain-secret");
        when(mapper.selectAddressById(20L)).thenReturn(address);
        when(mapper.insertOperationLog(any())).thenReturn(1);

        assertEquals("plain-secret", service.getAddressCredential(20L));
        verify(mapper).insertOperationLog(any());
    }

    @Test
    void shouldPreserveExistingCredentialExactlyWhenPasswordIsLeftBlank()
    {
        IpamSegment segment = new IpamSegment();
        segment.setSegmentId(10L);
        segment.setNetworkId(1L);
        segment.setCidrBlock("2.57.1.0/24");
        segment.setStartIp("2.57.1.0");
        segment.setEndIp("2.57.1.255");
        segment.setGatewayIp("2.57.1.1");
        segment.setStatus("0");

        IpamAddress origin = new IpamAddress();
        origin.setAddressId(20L);
        origin.setNetworkId(1L);
        origin.setSegmentId(10L);
        origin.setIpAddress("2.57.1.20");
        origin.setIpValue(34013460L);
        origin.setStatus("ALLOCATED");
        origin.setCommunityName("第一小区");
        origin.setLoginPassword(" secret-with-spaces ");
        origin.setCredentialConfigured(true);

        when(mapper.selectAddressById(20L)).thenReturn(origin);
        when(mapper.selectSegmentById(10L)).thenReturn(segment);
        when(mapper.updateAddress(any(IpamAddress.class))).thenReturn(1);

        IpamAddress update = new IpamAddress();
        update.setAddressId(20L);
        update.setStatus("ALLOCATED");
        update.setCommunityName("第一小区");
        update.setLoginPassword("");

        service.updateAddress(update);

        ArgumentCaptor<IpamAddress> captor = ArgumentCaptor.forClass(IpamAddress.class);
        verify(mapper).updateAddress(captor.capture());
        assertEquals(" secret-with-spaces ", captor.getValue().getLoginPassword());
        assertTrue(captor.getValue().getCredentialConfigured());
    }
}
