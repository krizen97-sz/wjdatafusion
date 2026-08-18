package com.hm.manage.service.impl;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.hm.common.exception.ServiceException;
import com.hm.common.utils.StringUtils;
import com.hm.manage.domain.IpamNetwork;
import com.hm.manage.domain.vo.IpamCommunityAddressVo;
import com.hm.manage.domain.vo.IpamCommunityOverviewVo;
import com.hm.manage.domain.vo.IpamDashboardSummaryVo;
import com.hm.manage.domain.vo.IpamDashboardVo;
import com.hm.manage.mapper.IpamDashboardMapper;
import com.hm.manage.service.IIpamDashboardService;
import com.hm.manage.service.IIpamService;

@Service
public class IpamDashboardServiceImpl implements IIpamDashboardService
{
    private static final int MAX_COMMUNITY_NAME_LENGTH = 120;
    private static final int MAX_STATION_NAME_LENGTH = 80;

    @Autowired
    private IIpamService ipamService;

    @Autowired
    private IpamDashboardMapper ipamDashboardMapper;

    @Override
    public IpamDashboardVo getDashboard(String policeStationName)
    {
        String normalizedStationName = normalizeStationName(policeStationName);
        IpamNetwork networkQuery = new IpamNetwork();
        networkQuery.setPoliceStationName(normalizedStationName);
        List<IpamNetwork> networks = ipamService.selectNetworkList(networkQuery);
        List<IpamCommunityOverviewVo> communities = ipamDashboardMapper.selectCommunityOverview(normalizedStationName);

        IpamDashboardVo dashboard = new IpamDashboardVo();
        dashboard.setSummary(buildSummary(networks, communities));
        dashboard.setNetworks(networks);
        dashboard.setCommunities(communities);
        dashboard.setTargetTypes(ipamDashboardMapper.selectTargetTypeStats(normalizedStationName));
        dashboard.setManufacturers(ipamDashboardMapper.selectManufacturerStats(normalizedStationName));
        return dashboard;
    }

    @Override
    public List<IpamCommunityAddressVo> selectCommunityAddressList(String communityName)
    {
        String normalizedName = StringUtils.trim(communityName);
        if (StringUtils.isEmpty(normalizedName))
        {
            throw new ServiceException("小区名称不能为空");
        }
        if (normalizedName.length() > MAX_COMMUNITY_NAME_LENGTH)
        {
            throw new ServiceException("小区名称不能超过" + MAX_COMMUNITY_NAME_LENGTH + "个字符");
        }
        return ipamDashboardMapper.selectCommunityAddressList(normalizedName);
    }

    private IpamDashboardSummaryVo buildSummary(List<IpamNetwork> networks, List<IpamCommunityOverviewVo> communities)
    {
        IpamDashboardSummaryVo summary = new IpamDashboardSummaryVo();
        Set<String> stationNames = new HashSet<>();
        long enabledNetworkCount = 0L;
        long totalCount = 0L;
        long assignableCount = 0L;
        long freeCount = 0L;
        long reservedCount = 0L;
        long allocatedCount = 0L;
        long issuedCount = 0L;
        long disabledCount = 0L;

        for (IpamNetwork network : networks)
        {
            if ("0".equals(network.getStatus()))
            {
                enabledNetworkCount++;
            }
            String stationName = StringUtils.trim(network.getPoliceStationName());
            if (StringUtils.isNotEmpty(stationName))
            {
                stationNames.add(stationName);
            }
            long networkTotal = valueOrZero(network.getTotalCount());
            totalCount += networkTotal;
            assignableCount += Math.max(networkTotal - 3L, 0L);
            freeCount += valueOrZero(network.getFreeCount());
            reservedCount += valueOrZero(network.getReservedCount());
            allocatedCount += valueOrZero(network.getAllocatedCount());
            issuedCount += valueOrZero(network.getIssuedCount());
            disabledCount += valueOrZero(network.getDisabledCount());
        }

        long deviceCount = 0L;
        for (IpamCommunityOverviewVo community : communities)
        {
            deviceCount += valueOrZero(community.getDeviceCount());
        }

        summary.setNetworkCount((long) networks.size());
        summary.setEnabledNetworkCount(enabledNetworkCount);
        summary.setStationCount((long) stationNames.size());
        summary.setTotalCount(totalCount);
        summary.setAssignableCount(assignableCount);
        summary.setFreeCount(freeCount);
        summary.setReservedCount(reservedCount);
        summary.setAllocatedCount(allocatedCount);
        summary.setIssuedCount(issuedCount);
        summary.setDisabledCount(disabledCount);
        summary.setOccupiedCount(allocatedCount + issuedCount);
        summary.setCommunityCount((long) communities.size());
        summary.setDeviceCount(deviceCount);
        return summary;
    }

    private long valueOrZero(Long value)
    {
        return value == null ? 0L : value;
    }

    private String normalizeStationName(String policeStationName)
    {
        String normalizedName = StringUtils.trim(policeStationName);
        if (StringUtils.isEmpty(normalizedName))
        {
            return null;
        }
        if (normalizedName.length() > MAX_STATION_NAME_LENGTH)
        {
            throw new ServiceException("派出所名称不能超过" + MAX_STATION_NAME_LENGTH + "个字符");
        }
        return normalizedName;
    }
}
