package com.hm.manage.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSON;
import com.hm.common.exception.ServiceException;
import com.hm.common.utils.DateUtils;
import com.hm.common.utils.SecurityUtils;
import com.hm.common.utils.StringUtils;
import com.hm.common.utils.ip.IpUtils;
import com.hm.manage.domain.IpamAddress;
import com.hm.manage.domain.IpamNetwork;
import com.hm.manage.domain.IpamOperationLog;
import com.hm.manage.domain.IpamScanResult;
import com.hm.manage.domain.IpamSegment;
import com.hm.manage.domain.bo.IpamConfigCommitBo;
import com.hm.manage.domain.bo.IpamConfigRowBo;
import com.hm.manage.domain.bo.IpamScenarioSettingBo;
import com.hm.manage.domain.vo.IpamCommunityOverviewVo;
import com.hm.manage.domain.vo.IpamSegmentOverviewVo;
import com.hm.manage.mapper.IpamMapper;
import com.hm.manage.mapper.IpamScanMapper;
import com.hm.manage.service.IIpamService;
import com.hm.manage.util.IpamAddressUtils;
import com.hm.manage.util.IpamAddressUtils.CidrRange;
import com.hm.manage.util.IpamConfigValidator;

@Service
public class IpamServiceImpl implements IIpamService
{
    private static final String STATUS_NORMAL = "0";
    private static final String IP_STATUS_FREE = "FREE";
    private static final String IP_STATUS_RESERVED = "RESERVED";
    private static final String IP_STATUS_ALLOCATED = "ALLOCATED";
    private static final String IP_STATUS_DISABLED = "DISABLED";
    private static final String IP_STATUS_ISSUED = "ISSUED";
    private static final String SCENARIO_SOCIAL = "SOCIAL";
    private static final String SCENARIO_INTERNAL = "INTERNAL";
    private static final String SETTING_SCENARIO_TYPE = "SCENARIO_TYPE";
    private static final String SETTING_SCENARIO_NAME = "IPAM使用场景";
    private static final String SETTING_NETWORK_RANGE_LOCK = "NETWORK_RANGE_LOCK";
    private static final int MIN_MANAGED_PREFIX_LENGTH = 1;
    private static final int MAX_MANAGED_PREFIX_LENGTH = 30;
    private static final int MIN_GRID_PAGE_SIZE = 64;
    private static final int DEFAULT_GRID_PAGE_SIZE = 256;
    private static final int MAX_GRID_PAGE_SIZE = 1024;
    private static final int MAX_POLICE_STATION_NAME_LENGTH = 80;

    @Autowired
    private IpamMapper ipamMapper;

    @Autowired
    private IpamScanMapper ipamScanMapper;

    @Override
    public List<IpamNetwork> selectNetworkList(IpamNetwork network)
    {
        List<IpamNetwork> networks = ipamMapper.selectNetworkList(network);
        for (IpamNetwork item : networks)
        {
            fillNetworkPresentation(item);
        }
        return networks;
    }

    @Override
    public IpamNetwork selectNetworkById(Long networkId)
    {
        return fillNetworkPresentation(ipamMapper.selectNetworkById(networkId));
    }

    @Override
    public String getScenarioType()
    {
        String scenarioType;
        try
        {
            scenarioType = ipamMapper.selectSettingValue(SETTING_SCENARIO_TYPE);
        }
        catch (Exception ignored)
        {
            scenarioType = ipamMapper.selectFirstNetworkScenario();
        }
        return normalizeScenarioType(scenarioType);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateScenarioType(IpamScenarioSettingBo setting)
    {
        if (setting == null)
        {
            throw new ServiceException("使用场景不能为空");
        }
        String scenarioType = normalizeScenarioType(setting.getScenarioType());
        Date now = DateUtils.getNowDate();
        String username = currentUsername();
        ipamMapper.upsertSetting(SETTING_SCENARIO_TYPE, scenarioType, SETTING_SCENARIO_NAME, username, now);
        ipamMapper.updateAllNetworkScenario(scenarioType, username, now);
        recordOperation("UPDATE_SCENARIO", "SETTING", null, null, "统一切换IPAM使用场景", setting);
        return 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertNetwork(IpamNetwork network)
    {
        if (network == null)
        {
            throw new ServiceException("网段信息不能为空");
        }
        network.setPoliceStationName(normalizePoliceStationName(network.getPoliceStationName()));
        CidrRange range = IpamAddressUtils.parseGatewayAndMask(network.getGatewayIp(), network.getSubnetMask());
        validateManagedRange(range);
        ipamMapper.lockSettingRow(SETTING_NETWORK_RANGE_LOCK);
        IpamNetwork overlap = ipamMapper.selectOverlappingNetwork(range.getStartValue(), range.getEndValue(), null);
        if (overlap != null)
        {
            throw new ServiceException("网段范围与“" + overlap.getNetworkName() + "”重叠："
                + overlap.getStartIp() + " - " + overlap.getEndIp());
        }

        Date now = DateUtils.getNowDate();
        network.setCidrBlock(range.getCidrBlock());
        network.setStartIp(range.getStartIp());
        network.setEndIp(range.getEndIp());
        network.setStartValue(range.getStartValue());
        network.setEndValue(range.getEndValue());
        network.setPrefixLength(range.getPrefixLength());
        network.setSubnetMask(IpamAddressUtils.prefixLengthToSubnetMask(range.getPrefixLength()));
        network.setGatewayIp(IpamAddressUtils.longToIp(IpamAddressUtils.ipToLong(network.getGatewayIp())));
        network.setScenarioType(getScenarioType());
        network.setStatus(StringUtils.defaultIfBlank(network.getStatus(), STATUS_NORMAL));
        network.setNetworkName(StringUtils.defaultIfBlank(network.getNetworkName(), range.getStartIp() + " - " + range.getEndIp()));
        network.setCreateBy(currentUsername());
        network.setCreateTime(now);
        int rows = ipamMapper.insertNetwork(network);

        IpamSegment segment = buildSegment(network, range, now);
        segment.setGatewayIp(normalizeGatewayIp(segment, network.getGatewayIp()));
        ipamMapper.insertSegment(segment);
        recordOperation("CREATE_NETWORK", "NETWORK", network.getNetworkId(), segment.getGatewayIp(), "新增IP网段 " + network.getNetworkName(), network);
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateNetwork(IpamNetwork network)
    {
        if (network == null || network.getNetworkId() == null)
        {
            throw new ServiceException("网段ID不能为空");
        }
        network.setPoliceStationName(normalizePoliceStationName(network.getPoliceStationName()));
        IpamNetwork origin = requireNetwork(network.getNetworkId());
        IpamSegment segment = requireManagedSegment(network.getNetworkId());
        CidrRange range = IpamAddressUtils.parseGatewayAndMask(network.getGatewayIp(), network.getSubnetMask());
        validateManagedRange(range);
        ipamMapper.lockSettingRow(SETTING_NETWORK_RANGE_LOCK);
        IpamNetwork overlap = ipamMapper.selectOverlappingNetwork(range.getStartValue(), range.getEndValue(), network.getNetworkId());
        if (overlap != null)
        {
            throw new ServiceException("网段范围与“" + overlap.getNetworkName() + "”重叠："
                + overlap.getStartIp() + " - " + overlap.getEndIp());
        }
        boolean rangeChanged = !range.getCidrBlock().equals(origin.getCidrBlock());
        if (rangeChanged)
        {
            Long[] networkIds = new Long[] { network.getNetworkId() };
            if (ipamMapper.countActiveAddressesByNetworkIds(networkIds) > 0)
            {
                throw new ServiceException("网段内已有保留、占用或禁用地址，不能修改子网掩码或地址范围");
            }
            ipamMapper.deleteFreeAddressesByNetworkIds(networkIds);
        }
        network.setNetworkName(StringUtils.defaultIfBlank(network.getNetworkName(), origin.getNetworkName()));
        network.setCidrBlock(range.getCidrBlock());
        network.setStartIp(range.getStartIp());
        network.setEndIp(range.getEndIp());
        network.setStartValue(range.getStartValue());
        network.setEndValue(range.getEndValue());
        network.setPrefixLength(range.getPrefixLength());
        network.setSubnetMask(IpamAddressUtils.prefixLengthToSubnetMask(range.getPrefixLength()));
        network.setGatewayIp(IpamAddressUtils.longToIp(IpamAddressUtils.ipToLong(network.getGatewayIp())));
        network.setScenarioType(getScenarioType());
        network.setStatus(StringUtils.defaultIfBlank(network.getStatus(), origin.getStatus()));
        network.setUpdateBy(currentUsername());
        network.setUpdateTime(DateUtils.getNowDate());
        int rows = ipamMapper.updateNetwork(network);

        IpamSegment segmentUpdate = new IpamSegment();
        segmentUpdate.setSegmentId(segment.getSegmentId());
        segmentUpdate.setSegmentName(network.getNetworkName());
        segmentUpdate.setCidrBlock(range.getCidrBlock());
        segmentUpdate.setStartIp(range.getStartIp());
        segmentUpdate.setEndIp(range.getEndIp());
        segmentUpdate.setPrefixLength(range.getPrefixLength());
        segmentUpdate.setTotalCount(range.getTotalCount());
        segmentUpdate.setGatewayIp(normalizeGatewayIp(segmentUpdate, network.getGatewayIp()));
        segmentUpdate.setStatus(network.getStatus());
        segmentUpdate.setRemark(network.getRemark());
        segmentUpdate.setUpdateBy(currentUsername());
        segmentUpdate.setUpdateTime(network.getUpdateTime());
        ipamMapper.updateSegment(segmentUpdate);
        recordOperation("UPDATE_NETWORK", "NETWORK", network.getNetworkId(), segmentUpdate.getGatewayIp(), "更新IP网段 " + network.getNetworkName(), network);
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteNetworkByIds(Long[] networkIds)
    {
        if (networkIds == null || networkIds.length == 0)
        {
            throw new ServiceException("请选择要删除的网段");
        }
        int activeCount = ipamMapper.countActiveAddressesByNetworkIds(networkIds);
        if (activeCount > 0)
        {
            throw new ServiceException("存在保留、已占用或禁用地址，不能删除网段");
        }
        ipamMapper.deleteFreeAddressesByNetworkIds(networkIds);
        ipamMapper.deleteSegmentsByNetworkIds(networkIds);
        int rows = ipamMapper.deleteNetworkByIds(networkIds);
        recordOperation("DELETE_NETWORK", "NETWORK", null, null, "删除IP网段", networkIds);
        return rows;
    }

    private Map<String, Object> getAddressGrid(Long segmentId, Integer pageNum, Integer pageSize)
    {
        IpamSegment segment = requireSegment(segmentId);
        int normalizedPageSize = normalizeGridPageSize(pageSize);
        long totalCount = segment.getTotalCount() == null ? 0L : segment.getTotalCount();
        long pageCount = Math.max((totalCount + normalizedPageSize - 1L) / normalizedPageSize, 1L);
        int normalizedPageNum = normalizeGridPageNum(pageNum, pageCount);
        long offset = (long) (normalizedPageNum - 1) * normalizedPageSize;
        long windowStart = IpamAddressUtils.ipToLong(segment.getStartIp()) + offset;
        long windowEnd = Math.min(IpamAddressUtils.ipToLong(segment.getEndIp()), windowStart + normalizedPageSize - 1L);
        Map<String, IpamAddress> persistedMap = new HashMap<>();
        for (IpamAddress item : ipamMapper.selectAddressesBySegmentIdAndRange(segmentId, windowStart, windowEnd))
        {
            persistedMap.put(item.getIpAddress(), item);
        }
        Map<String, IpamScanResult> scanResultMap = new HashMap<>();
        for (IpamScanResult item : ipamScanMapper.selectResultsBySegmentIdAndRange(segmentId, windowStart, windowEnd))
        {
            scanResultMap.put(item.getIpAddress(), item);
        }

        List<IpamAddress> addresses = new ArrayList<>();
        Map<String, Long> summary = buildGridSummary(segment);
        for (String ip : IpamAddressUtils.expandAddressGrid(segment.getCidrBlock(), offset, normalizedPageSize))
        {
            boolean boundary = IpamAddressUtils.isBoundaryAddress(ip, segment.getCidrBlock());
            boolean gateway = isGatewayAddress(segment, ip);
            IpamAddress row = persistedMap.get(ip);
            if (row == null)
            {
                row = buildVirtualAddress(segment, ip, boundary, gateway);
            }
            fillGridFields(row, segment, boundary, gateway);
            fillConnectivityFields(row, scanResultMap.get(ip));
            addresses.add(row);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("segment", segment);
        result.put("summary", summary);
        result.put("rows", addresses);
        result.put("pageNum", normalizedPageNum);
        result.put("pageSize", normalizedPageSize);
        result.put("pageCount", pageCount);
        result.put("rangeStartIp", addresses.isEmpty() ? null : addresses.get(0).getIpAddress());
        result.put("rangeEndIp", addresses.isEmpty() ? null : addresses.get(addresses.size() - 1).getIpAddress());
        return result;
    }

    @Override
    public Map<String, Object> getAddressGridByNetworkId(Long networkId, Integer pageNum, Integer pageSize)
    {
        return getAddressGrid(requireManagedSegment(networkId).getSegmentId(), pageNum, pageSize);
    }

    @Override
    public List<IpamAddress> selectAddressList(IpamAddress address)
    {
        return ipamMapper.selectAddressList(address);
    }

    @Override
    public IpamAddress selectAddressById(Long addressId)
    {
        return ipamMapper.selectAddressById(addressId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String getAddressCredential(Long addressId)
    {
        IpamAddress address = requireAddress(addressId);
        recordSecurityOperation("VIEW_CREDENTIAL", "ADDRESS", addressId, address.getIpAddress(), "查看设备密码");
        return address.getLoginPassword();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int allocateAddress(IpamAddress address)
    {
        if (address == null)
        {
            throw new ServiceException("地址信息不能为空");
        }
        if (address.getAddressId() != null)
        {
            return updateAddress(address);
        }
        IpamSegment segment = requireSegment(address.getSegmentId());
        ensureSegmentEnabled(segment);
        normalizeAddressForSave(address, segment);

        IpamAddress existed = ipamMapper.selectAddressByIp(address.getIpAddress());
        if (existed != null && !IP_STATUS_FREE.equals(existed.getStatus()))
        {
            throw new ServiceException("该IP已被占用或保留，不能重复分配：" + address.getIpAddress());
        }
        if (existed != null)
        {
            address.setAddressId(existed.getAddressId());
            prepareCredentialForSave(address, existed);
            int rows = ipamMapper.updateAddressIfFree(address);
            if (rows != 1)
            {
                throw new ServiceException("该IP刚刚已被其他操作占用，请刷新后重试：" + address.getIpAddress());
            }
            recordOperation("ALLOCATE_ADDRESS", "ADDRESS", address.getAddressId(), address.getIpAddress(), "分配IP地址 " + address.getIpAddress(), address);
            return rows;
        }
        prepareCredentialForSave(address, null);
        int rows = ipamMapper.insertAddress(address);
        recordOperation("ALLOCATE_ADDRESS", "ADDRESS", address.getAddressId(), address.getIpAddress(), "分配IP地址 " + address.getIpAddress(), address);
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateAddress(IpamAddress address)
    {
        if (address == null || address.getAddressId() == null)
        {
            throw new ServiceException("地址ID不能为空");
        }
        IpamAddress origin = requireAddress(address.getAddressId());
        if (IP_STATUS_FREE.equals(StringUtils.defaultIfBlank(address.getStatus(), origin.getStatus())))
        {
            return releaseAddress(address.getAddressId());
        }
        IpamSegment segment = requireSegment(origin.getSegmentId());
        ensureSegmentEnabled(segment);
        address.setNetworkId(origin.getNetworkId());
        address.setSegmentId(origin.getSegmentId());
        address.setIpAddress(origin.getIpAddress());
        address.setIpValue(origin.getIpValue());
        address.setAllocatedTime(origin.getAllocatedTime());
        address.setIssuedTime(origin.getIssuedTime());
        address.setIssueBatch(origin.getIssueBatch());
        normalizeAddressForSave(address, segment);
        prepareCredentialForSave(address, origin);
        int rows = ipamMapper.updateAddress(address);
        recordOperation("UPDATE_ADDRESS", "ADDRESS", address.getAddressId(), address.getIpAddress(), "更新IP地址 " + address.getIpAddress(), address);
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int releaseAddress(Long addressId)
    {
        IpamAddress origin = requireAddress(addressId);
        if (IP_STATUS_RESERVED.equals(origin.getStatus()) || IP_STATUS_DISABLED.equals(origin.getStatus()))
        {
            throw new ServiceException("保留或禁用地址不能直接释放，请先编辑状态");
        }
        int rows = markAddressFree(origin, "释放为可用地址");
        recordOperation("RELEASE_ADDRESS", "ADDRESS", addressId, origin.getIpAddress(), "释放IP地址 " + origin.getIpAddress(), origin);
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int commitConfigSheet(IpamConfigCommitBo commitBo)
    {
        if (commitBo == null || commitBo.getNetworkId() == null)
        {
            throw new ServiceException("请选择要配置的网段");
        }
        IpamSegment segment = requireManagedSegment(commitBo.getNetworkId());
        ensureSegmentEnabled(segment);
        List<IpamConfigRowBo> rows = commitBo.getRows();
        IpamConfigValidator.validate(commitBo, IpamAddressUtils.parseCidr(segment.getCidrBlock()), segment.getGatewayIp());

        int affected = 0;
        for (IpamConfigRowBo row : rows)
        {
            String ip = IpamAddressUtils.longToIp(IpamAddressUtils.ipToLong(row.getIpAddress()));
            if (!IpamAddressUtils.containsIp(segment.getCidrBlock(), ip))
            {
                throw new ServiceException("IP地址不在当前网段内：" + ip);
            }

            String status = StringUtils.defaultIfBlank(row.getStatus(), IP_STATUS_ALLOCATED);
            if (!isAddressStatus(status))
            {
                throw new ServiceException("地址状态不合法：" + status);
            }
            if (IpamAddressUtils.isBoundaryAddress(ip, segment.getCidrBlock()) || isGatewayAddress(segment, ip))
            {
                if (!IP_STATUS_RESERVED.equals(status))
                {
                    throw new ServiceException("网段边界或网关地址只能保留：" + ip);
                }
                continue;
            }

            row.setIpAddress(ip);
            row.setStatus(status);

            IpamAddress origin = null;
            if (row.getAddressId() != null)
            {
                origin = requireAddress(row.getAddressId());
                if (!segment.getSegmentId().equals(origin.getSegmentId()) || !ip.equals(origin.getIpAddress()))
                {
                    throw new ServiceException("配置行与已存地址不匹配：" + ip);
                }
            }

            if (IP_STATUS_FREE.equals(status))
            {
                if (origin != null)
                {
                    affected += markAddressFree(origin, "可视化配置释放为空闲地址");
                }
                continue;
            }

            IpamAddress address = toAddress(row, segment);
            affected += row.getAddressId() == null ? allocateAddress(address) : updateAddress(address);
        }
        recordOperation("COMMIT_CONFIG_SHEET", "SEGMENT", segment.getSegmentId(), segment.getCidrBlock(), "保存可视化IP配置", commitBo);
        return affected;
    }

    private IpamSegmentOverviewVo buildOverview(Long networkId, Long segmentId, String keyword, String targetType, String manufacturer)
    {
        IpamSegmentOverviewVo overview = new IpamSegmentOverviewVo();
        if (segmentId != null)
        {
            overview.setSegment(requireSegment(segmentId));
        }
        List<IpamCommunityOverviewVo> communities = ipamMapper.selectCommunityOverview(networkId, segmentId, keyword, targetType, manufacturer);
        overview.setCommunities(communities);
        overview.setCommunityCount((long) communities.size());
        long addressCount = 0L;
        long deviceCount = 0L;
        for (IpamCommunityOverviewVo item : communities)
        {
            addressCount += item.getAddressCount() == null ? 0L : item.getAddressCount();
            deviceCount += item.getDeviceCount() == null ? 0L : item.getDeviceCount();
        }
        overview.setAddressCount(addressCount);
        overview.setDeviceCount(deviceCount);
        return overview;
    }

    @Override
    public IpamSegmentOverviewVo getNetworkOverview(Long networkId, String keyword, String targetType, String manufacturer)
    {
        IpamSegment segment = requireManagedSegment(networkId);
        return buildOverview(networkId, segment.getSegmentId(), keyword, targetType, manufacturer);
    }

    private IpamNetwork requireNetwork(Long networkId)
    {
        if (networkId == null)
        {
            throw new ServiceException("网段ID不能为空");
        }
        IpamNetwork network = fillNetworkPresentation(ipamMapper.selectNetworkById(networkId));
        if (network == null)
        {
            throw new ServiceException("网段不存在或已删除");
        }
        return network;
    }

    private IpamSegment requireSegment(Long segmentId)
    {
        if (segmentId == null)
        {
            throw new ServiceException("网段地址池ID不能为空");
        }
        IpamSegment segment = ipamMapper.selectSegmentById(segmentId);
        if (segment == null)
        {
            throw new ServiceException("网段地址池不存在或已删除");
        }
        return segment;
    }

    private IpamSegment requireManagedSegment(Long networkId)
    {
        IpamSegment query = new IpamSegment();
        query.setNetworkId(networkId);
        List<IpamSegment> segments = ipamMapper.selectSegmentList(query);
        if (segments.isEmpty())
        {
            throw new ServiceException("该网段缺少地址池数据，请先完成数据修复");
        }
        if (segments.size() > 1)
        {
            throw new ServiceException("该历史网段包含多个地址池，请拆分为独立网段后再编辑");
        }
        return segments.get(0);
    }

    private IpamAddress requireAddress(Long addressId)
    {
        if (addressId == null)
        {
            throw new ServiceException("地址ID不能为空");
        }
        IpamAddress address = ipamMapper.selectAddressById(addressId);
        if (address == null)
        {
            throw new ServiceException("地址不存在或已删除");
        }
        return address;
    }

    private int markAddressFree(IpamAddress origin, String remark)
    {
        IpamAddress address = new IpamAddress();
        address.setAddressId(origin.getAddressId());
        address.setReleasedTime(DateUtils.getNowDate());
        address.setUpdateBy(currentUsername());
        address.setUpdateTime(DateUtils.getNowDate());
        address.setRemark(remark);
        return ipamMapper.markAddressFree(address);
    }

    private String normalizeGatewayIp(IpamSegment segment, String gatewayIp)
    {
        if (StringUtils.isBlank(gatewayIp))
        {
            throw new ServiceException("网关IP不能为空");
        }
        String ip = IpamAddressUtils.longToIp(IpamAddressUtils.ipToLong(gatewayIp));
        if (!IpamAddressUtils.containsIp(segment.getCidrBlock(), ip))
        {
            throw new ServiceException("网关IP必须在网段范围内：" + ip);
        }
        if (IpamAddressUtils.isBoundaryAddress(ip, segment.getCidrBlock()))
        {
            throw new ServiceException("网关IP不能使用网段边界地址：" + ip);
        }
        IpamAddress existed = ipamMapper.selectAddressByIp(ip);
        if (existed != null && (IP_STATUS_ALLOCATED.equals(existed.getStatus()) || IP_STATUS_ISSUED.equals(existed.getStatus()) || IP_STATUS_DISABLED.equals(existed.getStatus())))
        {
            throw new ServiceException("网关IP已被占用或禁用，不能设置为网关：" + ip);
        }
        return ip;
    }

    private String normalizeScenarioType(String scenarioType)
    {
        if (StringUtils.isBlank(scenarioType))
        {
            return SCENARIO_SOCIAL;
        }
        String normalized = scenarioType.trim().toUpperCase();
        if (SCENARIO_SOCIAL.equals(normalized) || SCENARIO_INTERNAL.equals(normalized))
        {
            return normalized;
        }
        throw new ServiceException("使用场景不合法：" + scenarioType);
    }

    private String normalizePoliceStationName(String policeStationName)
    {
        String normalized = StringUtils.trim(policeStationName);
        if (StringUtils.isEmpty(normalized))
        {
            throw new ServiceException("所属派出所不能为空");
        }
        if (normalized.length() > MAX_POLICE_STATION_NAME_LENGTH)
        {
            throw new ServiceException("所属派出所不能超过" + MAX_POLICE_STATION_NAME_LENGTH + "个字符");
        }
        return normalized;
    }

    private boolean isGatewayAddress(IpamSegment segment, String ip)
    {
        return segment != null && StringUtils.isNotBlank(segment.getGatewayIp()) && segment.getGatewayIp().equals(ip);
    }

    private IpamSegment buildSegment(IpamNetwork network, CidrRange child, Date now)
    {
        IpamSegment segment = new IpamSegment();
        segment.setNetworkId(network.getNetworkId());
        segment.setSegmentName(network.getNetworkName());
        segment.setCidrBlock(child.getCidrBlock());
        segment.setStartIp(child.getStartIp());
        segment.setEndIp(child.getEndIp());
        segment.setGatewayIp(IpamAddressUtils.longToIp(child.getStartValue() + 1));
        segment.setPrefixLength(child.getPrefixLength());
        segment.setTotalCount(child.getTotalCount());
        segment.setStatus(StringUtils.defaultIfBlank(network.getStatus(), STATUS_NORMAL));
        segment.setRemark(network.getRemark());
        segment.setCreateBy(currentUsername());
        segment.setCreateTime(now);
        return segment;
    }

    private void validateManagedRange(CidrRange range)
    {
        if (range.getPrefixLength() < MIN_MANAGED_PREFIX_LENGTH || range.getPrefixLength() > MAX_MANAGED_PREFIX_LENGTH)
        {
            throw new ServiceException("子网掩码必须保留网络地址、广播地址和至少一个可分配地址");
        }
    }

    private IpamNetwork fillNetworkPresentation(IpamNetwork network)
    {
        if (network != null && network.getPrefixLength() != null)
        {
            network.setSubnetMask(IpamAddressUtils.prefixLengthToSubnetMask(network.getPrefixLength()));
        }
        return network;
    }

    private int normalizeGridPageSize(Integer pageSize)
    {
        if (pageSize == null)
        {
            return DEFAULT_GRID_PAGE_SIZE;
        }
        return Math.max(MIN_GRID_PAGE_SIZE, Math.min(pageSize, MAX_GRID_PAGE_SIZE));
    }

    private int normalizeGridPageNum(Integer pageNum, long pageCount)
    {
        long requested = pageNum == null ? 1L : Math.max(pageNum.longValue(), 1L);
        return (int) Math.min(requested, Math.min(pageCount, Integer.MAX_VALUE));
    }

    private void ensureSegmentEnabled(IpamSegment segment)
    {
        if (!STATUS_NORMAL.equals(segment.getStatus()))
        {
            throw new ServiceException("网段已停用，不能分配地址");
        }
    }

    private void normalizeAddressForSave(IpamAddress address, IpamSegment segment)
    {
        String ip = IpamAddressUtils.longToIp(IpamAddressUtils.ipToLong(address.getIpAddress()));
        if (!IpamAddressUtils.containsIp(segment.getCidrBlock(), ip))
        {
            throw new ServiceException("IP地址不在当前网段内");
        }
        if (IpamAddressUtils.isBoundaryAddress(ip, segment.getCidrBlock()))
        {
            throw new ServiceException(resolveBoundaryReason(segment, ip) + "不能分配或修改：" + ip);
        }
        if (isGatewayAddress(segment, ip) && !IP_STATUS_RESERVED.equals(address.getStatus()))
        {
            throw new ServiceException("网关地址不能配置为占用或禁用：" + ip);
        }
        String status = StringUtils.defaultIfBlank(address.getStatus(), IP_STATUS_ALLOCATED);
        if (!isAddressStatus(status))
        {
            throw new ServiceException("地址状态不合法：" + status);
        }
        if (IP_STATUS_ALLOCATED.equals(status) || IP_STATUS_ISSUED.equals(status))
        {
            if (StringUtils.isBlank(address.getCommunityName()))
            {
                throw new ServiceException(resolveSubjectNameLabel(segment) + "不能为空");
            }
        }

        Date now = DateUtils.getNowDate();
        address.setNetworkId(segment.getNetworkId());
        address.setSegmentId(segment.getSegmentId());
        address.setIpAddress(ip);
        address.setIpValue(IpamAddressUtils.ipToLong(ip));
        address.setStatus(status);
        address.setReleasedTime(null);
        if (IP_STATUS_ALLOCATED.equals(status))
        {
            address.setAllocatedTime(address.getAllocatedTime() == null ? now : address.getAllocatedTime());
            address.setIssuedTime(null);
            address.setIssueBatch(null);
        }
        else if (IP_STATUS_ISSUED.equals(status))
        {
            address.setAllocatedTime(address.getAllocatedTime() == null ? now : address.getAllocatedTime());
            address.setIssuedTime(address.getIssuedTime() == null ? now : address.getIssuedTime());
        }
        else if (IP_STATUS_RESERVED.equals(status) || IP_STATUS_DISABLED.equals(status))
        {
            address.setAllocatedTime(null);
            address.setIssuedTime(null);
            address.setIssueBatch(null);
        }
        address.setCreateBy(currentUsername());
        address.setCreateTime(address.getCreateTime() == null ? now : address.getCreateTime());
        address.setUpdateBy(currentUsername());
        address.setUpdateTime(now);
    }

    private void prepareCredentialForSave(IpamAddress address, IpamAddress origin)
    {
        if (StringUtils.isBlank(address.getLoginPassword()))
        {
            address.setLoginPassword(origin == null ? null : origin.getLoginPassword());
        }
        address.setCredentialConfigured(StringUtils.isNotBlank(address.getLoginPassword()));
    }

    private IpamAddress toAddress(IpamConfigRowBo row, IpamSegment segment)
    {
        IpamAddress address = new IpamAddress();
        address.setAddressId(row.getAddressId());
        address.setNetworkId(segment.getNetworkId());
        address.setSegmentId(segment.getSegmentId());
        address.setIpAddress(row.getIpAddress());
        address.setStatus(row.getStatus());
        address.setCommunityName(row.getCommunityName());
        address.setTargetType(row.getTargetType());
        address.setTargetName(row.getTargetName());
        address.setManufacturer(row.getManufacturer());
        address.setInternalIpAddress(row.getInternalIpAddress());
        address.setAccessUnit(row.getAccessUnit());
        address.setPurpose(row.getPurpose());
        address.setLoginUsername(row.getLoginUsername());
        address.setLoginPassword(row.getLoginPassword());
        address.setMappingAddress(row.getMappingAddress());
        address.setMappingPort(row.getMappingPort());
        address.setMappingDescription(row.getMappingDescription());
        address.setOwnerName(row.getOwnerName());
        address.setOwnerPhone(row.getOwnerPhone());
        address.setRemark(row.getRemark());
        return address;
    }

    private boolean isAddressStatus(String status)
    {
        return IP_STATUS_RESERVED.equals(status)
            || IP_STATUS_ALLOCATED.equals(status)
            || IP_STATUS_ISSUED.equals(status)
            || IP_STATUS_DISABLED.equals(status)
            || IP_STATUS_FREE.equals(status);
    }

    private String resolveSubjectNameLabel(IpamSegment segment)
    {
        IpamNetwork network = requireNetwork(segment.getNetworkId());
        return SCENARIO_INTERNAL.equals(normalizeScenarioType(network.getScenarioType())) ? "项目名称" : "小区名称";
    }

    private IpamAddress buildVirtualAddress(IpamSegment segment, String ip, boolean boundary, boolean gateway)
    {
        IpamAddress address = new IpamAddress();
        address.setNetworkId(segment.getNetworkId());
        address.setSegmentId(segment.getSegmentId());
        address.setIpAddress(ip);
        address.setIpValue(IpamAddressUtils.ipToLong(ip));
        address.setStatus((boundary || gateway) ? IP_STATUS_RESERVED : IP_STATUS_FREE);
        if (boundary)
        {
            address.setReservedReason(resolveBoundaryReason(segment, ip));
        }
        if (gateway)
        {
            address.setReservedReason("网关");
        }
        return address;
    }

    private void fillGridFields(IpamAddress address, IpamSegment segment, boolean boundary, boolean gateway)
    {
        address.setNetworkId(segment.getNetworkId());
        address.setNetworkName(segment.getNetworkName());
        address.setNetworkCidr(segment.getNetworkCidr());
        address.setSegmentId(segment.getSegmentId());
        address.setSegmentName(segment.getSegmentName());
        address.setSegmentCidr(segment.getCidrBlock());
        address.setLastOctet(IpamAddressUtils.getLastOctet(address.getIpAddress()));
        address.setBoundaryAddress(boundary);
        if (boundary)
        {
            address.setStatus(IP_STATUS_RESERVED);
            address.setReservedReason(resolveBoundaryReason(segment, address.getIpAddress()));
        }
        if (gateway)
        {
            address.setStatus(IP_STATUS_RESERVED);
            address.setReservedReason("网关");
        }
    }

    private void fillConnectivityFields(IpamAddress address, IpamScanResult scanResult)
    {
        if (scanResult == null)
        {
            return;
        }
        address.setConnectivityStatus(scanResult.getConnectivityStatus());
        address.setScanResponseTimeMs(scanResult.getResponseTimeMs());
        address.setLastScanTime(scanResult.getLastScanTime());
        address.setLastOnlineTime(scanResult.getLastOnlineTime());
    }

    private String resolveBoundaryReason(IpamSegment segment, String ip)
    {
        return segment != null && segment.getStartIp() != null && segment.getStartIp().equals(ip) ? "网络地址" : "广播地址";
    }

    private Map<String, Long> buildGridSummary(IpamSegment segment)
    {
        Map<String, Long> summary = new LinkedHashMap<>();
        summary.put("total", defaultLong(segment.getTotalCount()));
        summary.put(IP_STATUS_FREE, defaultLong(segment.getFreeCount()));
        summary.put(IP_STATUS_RESERVED, defaultLong(segment.getReservedCount()));
        summary.put(IP_STATUS_ALLOCATED, defaultLong(segment.getAllocatedCount()));
        summary.put(IP_STATUS_ISSUED, defaultLong(segment.getIssuedCount()));
        summary.put(IP_STATUS_DISABLED, defaultLong(segment.getDisabledCount()));
        return summary;
    }

    private long defaultLong(Long value)
    {
        return value == null ? 0L : value;
    }

    private void recordOperation(String actionType, String targetType, Long targetId, String ipAddress, String summary, Object detail)
    {
        try
        {
            IpamOperationLog log = new IpamOperationLog();
            log.setActionType(actionType);
            log.setTargetType(targetType);
            log.setTargetId(targetId);
            log.setIpAddress(ipAddress);
            log.setSummary(summary);
            Object sanitizedDetail = sanitizeAuditDetail(detail);
            log.setDetailContent(sanitizedDetail == null ? null : JSON.toJSONString(sanitizedDetail));
            log.setOperatorName(currentUsername());
            log.setOperatorIp(currentIp());
            log.setCreateTime(DateUtils.getNowDate());
            ipamMapper.insertOperationLog(log);
        }
        catch (Exception ignored)
        {
            // IPAM操作不能因为审计日志写入失败而中断主流程。
        }
    }

    private void recordSecurityOperation(String actionType, String targetType, Long targetId, String ipAddress, String summary)
    {
        IpamOperationLog log = new IpamOperationLog();
        log.setActionType(actionType);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setIpAddress(ipAddress);
        log.setSummary(summary);
        log.setDetailContent(null);
        log.setOperatorName(currentUsername());
        log.setOperatorIp(currentIp());
        log.setCreateTime(DateUtils.getNowDate());
        if (ipamMapper.insertOperationLog(log) != 1)
        {
            throw new ServiceException("凭据审计日志写入失败，本次操作已取消");
        }
    }

    private Object sanitizeAuditDetail(Object detail)
    {
        if (detail instanceof IpamConfigCommitBo commit)
        {
            Map<String, Object> safe = new LinkedHashMap<>();
            safe.put("networkId", commit.getNetworkId());
            safe.put("rowCount", commit.getRows() == null ? 0 : commit.getRows().size());
            safe.put("ipAddresses", commit.getRows() == null ? List.of() : commit.getRows().stream()
                .filter(row -> row != null && StringUtils.isNotBlank(row.getIpAddress()))
                .map(IpamConfigRowBo::getIpAddress)
                .toList());
            return safe;
        }
        if (detail instanceof IpamAddress address)
        {
            Map<String, Object> safe = new LinkedHashMap<>();
            safe.put("addressId", address.getAddressId());
            safe.put("networkId", address.getNetworkId());
            safe.put("ipAddress", address.getIpAddress());
            safe.put("status", address.getStatus());
            safe.put("communityName", address.getCommunityName());
            safe.put("targetType", address.getTargetType());
            safe.put("targetName", address.getTargetName());
            safe.put("manufacturer", address.getManufacturer());
            safe.put("credentialConfigured", address.getCredentialConfigured());
            return safe;
        }
        return detail;
    }

    private String currentUsername()
    {
        try
        {
            return SecurityUtils.getUsername();
        }
        catch (Exception e)
        {
            return "system";
        }
    }

    private String currentIp()
    {
        try
        {
            return IpUtils.getIpAddr();
        }
        catch (Exception e)
        {
            return null;
        }
    }
}
