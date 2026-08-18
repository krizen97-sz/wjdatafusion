package com.hm.manage.service.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.hm.common.exception.ServiceException;
import com.hm.manage.config.IpamScanProperties;
import com.hm.manage.domain.IpamNetwork;
import com.hm.manage.domain.IpamScanJob;
import com.hm.manage.mapper.IpamMapper;
import com.hm.manage.mapper.IpamScanMapper;

class IpamScanServiceImplTest
{
    private IpamScanServiceImpl service;
    private IpamMapper ipamMapper;
    private IpamScanMapper scanMapper;

    @BeforeEach
    void setUp()
    {
        service = new IpamScanServiceImpl();
        ipamMapper = mock(IpamMapper.class);
        scanMapper = mock(IpamScanMapper.class);
        ReflectionTestUtils.setField(service, "ipamMapper", ipamMapper);
        ReflectionTestUtils.setField(service, "ipamScanMapper", scanMapper);
        ReflectionTestUtils.setField(service, "scanProperties", new IpamScanProperties());
    }

    @AfterEach
    void tearDown()
    {
        service.shutdownExecutors();
    }

    @Test
    void shouldRejectManualScanWhenDatabaseLeaseIsHeld()
    {
        IpamNetwork network = new IpamNetwork();
        network.setNetworkId(1L);
        network.setNetworkName("测试网段");
        network.setStatus("0");
        when(ipamMapper.selectNetworkById(1L)).thenReturn(network);
        when(scanMapper.tryAcquireScanLock(anyString(), any(Date.class), any(Date.class))).thenReturn(0);

        assertThrows(ServiceException.class, () -> service.startNetworkScan(1L));
        verify(scanMapper, never()).insertScanJob(any(IpamScanJob.class));
    }

    @Test
    void shouldOnlyFailJobsOlderThanLeaseDuringStartupRecovery()
    {
        service.markInterruptedJobs();

        verify(scanMapper).clearExpiredScanLock(any(Date.class));
        verify(scanMapper).markInterruptedJobsFailed(any(Date.class), any(Date.class), anyString());
    }
}
