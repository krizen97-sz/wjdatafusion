package com.hm.manage.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.hm.common.exception.ServiceException;
import com.hm.manage.domain.IpamAddress;
import com.hm.manage.domain.IpamNetwork;
import com.hm.manage.domain.bo.IpamConfigCommitBo;
import com.hm.manage.domain.bo.IpamConfigRowBo;
import com.hm.manage.domain.bo.IpamWorkbookCommitBo;
import com.hm.manage.domain.vo.IpamCommunityOverviewVo;
import com.hm.manage.domain.vo.IpamWorkbookCatalogVo;
import com.hm.manage.mapper.IpamWorkbookMapper;
import com.hm.manage.service.IIpamService;

class IpamWorkbookServiceImplTest
{
    private IpamWorkbookMapper workbookMapper;
    private IIpamService ipamService;
    private IpamWorkbookServiceImpl service;

    @BeforeEach
    void setUp()
    {
        workbookMapper = mock(IpamWorkbookMapper.class);
        ipamService = mock(IIpamService.class);
        service = new IpamWorkbookServiceImpl();
        ReflectionTestUtils.setField(service, "ipamWorkbookMapper", workbookMapper);
        ReflectionTestUtils.setField(service, "ipamService", ipamService);
    }

    @Test
    void catalogMustQueryOnlyWorkbookNetworksCommunitiesAndGlobalScenario()
    {
        List<IpamNetwork> networks = List.of(new IpamNetwork());
        List<IpamCommunityOverviewVo> communities = List.of(new IpamCommunityOverviewVo());
        when(ipamService.selectNetworkList(org.mockito.ArgumentMatchers.any(IpamNetwork.class))).thenReturn(networks);
        when(workbookMapper.selectCommunityCatalog()).thenReturn(communities);
        when(ipamService.getScenarioType()).thenReturn("SOCIAL");

        IpamWorkbookCatalogVo catalog = service.getCatalog();
        assertEquals("SOCIAL", catalog.getScenarioType());
        assertEquals(networks, catalog.getNetworks());
        assertEquals(communities, catalog.getCommunities());
        verify(workbookMapper).selectCommunityCatalog();
    }

    @Test
    void communityLookupMustTrimAndUseExactMapperContract()
    {
        List<IpamAddress> expected = List.of(new IpamAddress());
        when(workbookMapper.selectCommunityAddressList("湖塘花园")).thenReturn(expected);

        assertEquals(expected, service.selectCommunityAddressList("  湖塘花园  "));
        verify(workbookMapper).selectCommunityAddressList("湖塘花园");
    }

    @Test
    void workbookCommitMustReuseExistingConfigServiceForEverySheet()
    {
        IpamConfigCommitBo first = sheet(1L, 1);
        IpamConfigCommitBo second = sheet(2L, 1);
        when(ipamService.commitConfigSheet(first)).thenReturn(1);
        when(ipamService.commitConfigSheet(second)).thenReturn(2);
        IpamWorkbookCommitBo workbook = new IpamWorkbookCommitBo();
        workbook.setSheets(List.of(first, second));

        assertEquals(3, service.commitWorkbook(workbook));
        verify(ipamService).commitConfigSheet(first);
        verify(ipamService).commitConfigSheet(second);
    }

    @Test
    void workbookCommitMustRejectMoreThan4096Rows()
    {
        IpamWorkbookCommitBo workbook = new IpamWorkbookCommitBo();
        workbook.setSheets(List.of(sheet(1L, 4097)));

        assertThrows(ServiceException.class, () -> service.commitWorkbook(workbook));
    }

    private IpamConfigCommitBo sheet(Long networkId, int rowCount)
    {
        IpamConfigCommitBo sheet = new IpamConfigCommitBo();
        sheet.setNetworkId(networkId);
        List<IpamConfigRowBo> rows = new ArrayList<>();
        for (int index = 0; index < rowCount; index++)
        {
            rows.add(new IpamConfigRowBo());
        }
        sheet.setRows(rows);
        return sheet;
    }
}
