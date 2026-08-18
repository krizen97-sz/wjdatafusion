package com.hm.manage.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.hm.common.exception.ServiceException;
import com.hm.manage.domain.IpamNetwork;
import com.hm.manage.domain.vo.IpamCommunityAddressVo;
import com.hm.manage.domain.vo.IpamCommunityOverviewVo;
import com.hm.manage.domain.vo.IpamDashboardVo;
import com.hm.manage.mapper.IpamDashboardMapper;
import com.hm.manage.service.IIpamService;

class IpamDashboardServiceImplTest
{
    private IIpamService ipamService;
    private IpamDashboardMapper dashboardMapper;
    private IpamDashboardServiceImpl service;

    @BeforeEach
    void setUp()
    {
        ipamService = mock(IIpamService.class);
        dashboardMapper = mock(IpamDashboardMapper.class);
        service = new IpamDashboardServiceImpl();
        ReflectionTestUtils.setField(service, "ipamService", ipamService);
        ReflectionTestUtils.setField(service, "ipamDashboardMapper", dashboardMapper);
        when(dashboardMapper.selectTargetTypeStats(nullable(String.class))).thenReturn(Collections.emptyList());
        when(dashboardMapper.selectManufacturerStats(nullable(String.class))).thenReturn(Collections.emptyList());
    }

    @Test
    void dashboardMustAggregateAllNetworkAndCommunityCounters()
    {
        IpamNetwork first = network("0", "湖塘", 256L, 100L, 3L, 120L, 30L, 3L);
        IpamNetwork second = network("1", "湖塘", 256L, 200L, 3L, 50L, 0L, 3L);
        IpamCommunityOverviewVo firstCommunity = community(2L);
        IpamCommunityOverviewVo secondCommunity = community(4L);
        when(ipamService.selectNetworkList(any(IpamNetwork.class))).thenReturn(List.of(first, second));
        when(dashboardMapper.selectCommunityOverview(null)).thenReturn(List.of(firstCommunity, secondCommunity));

        IpamDashboardVo dashboard = service.getDashboard(null);

        assertEquals(2L, dashboard.getSummary().getNetworkCount());
        assertEquals(1L, dashboard.getSummary().getEnabledNetworkCount());
        assertEquals(1L, dashboard.getSummary().getStationCount());
        assertEquals(512L, dashboard.getSummary().getTotalCount());
        assertEquals(506L, dashboard.getSummary().getAssignableCount());
        assertEquals(300L, dashboard.getSummary().getFreeCount());
        assertEquals(6L, dashboard.getSummary().getReservedCount());
        assertEquals(170L, dashboard.getSummary().getAllocatedCount());
        assertEquals(30L, dashboard.getSummary().getIssuedCount());
        assertEquals(6L, dashboard.getSummary().getDisabledCount());
        assertEquals(200L, dashboard.getSummary().getOccupiedCount());
        assertEquals(2L, dashboard.getSummary().getCommunityCount());
        assertEquals(6L, dashboard.getSummary().getDeviceCount());
    }

    @Test
    void dashboardMustApplyOneTrimmedStationFilterToEveryDimension()
    {
        when(ipamService.selectNetworkList(any(IpamNetwork.class))).thenReturn(Collections.emptyList());
        when(dashboardMapper.selectCommunityOverview("湖塘")).thenReturn(Collections.emptyList());

        service.getDashboard("  湖塘  ");

        verify(dashboardMapper).selectCommunityOverview("湖塘");
        verify(dashboardMapper).selectTargetTypeStats("湖塘");
        verify(dashboardMapper).selectManufacturerStats("湖塘");
    }

    @Test
    void communityDetailMustTrimTheExactName()
    {
        List<IpamCommunityAddressVo> expected = List.of(new IpamCommunityAddressVo());
        when(dashboardMapper.selectCommunityAddressList("湖塘花园")).thenReturn(expected);

        assertEquals(expected, service.selectCommunityAddressList("  湖塘花园  "));
    }

    @Test
    void communityDetailMustRejectBlankName()
    {
        assertThrows(ServiceException.class, () -> service.selectCommunityAddressList("  "));
    }

    private IpamNetwork network(String status, String stationName, Long total, Long free, Long reserved,
                                Long allocated, Long issued, Long disabled)
    {
        IpamNetwork network = new IpamNetwork();
        network.setStatus(status);
        network.setPoliceStationName(stationName);
        network.setTotalCount(total);
        network.setFreeCount(free);
        network.setReservedCount(reserved);
        network.setAllocatedCount(allocated);
        network.setIssuedCount(issued);
        network.setDisabledCount(disabled);
        return network;
    }

    private IpamCommunityOverviewVo community(Long deviceCount)
    {
        IpamCommunityOverviewVo community = new IpamCommunityOverviewVo();
        community.setDeviceCount(deviceCount);
        return community;
    }
}
