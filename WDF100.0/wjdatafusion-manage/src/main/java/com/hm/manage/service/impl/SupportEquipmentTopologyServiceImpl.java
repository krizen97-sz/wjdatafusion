package com.hm.manage.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.hm.common.exception.ServiceException;
import com.hm.common.utils.DateUtils;
import com.hm.common.utils.SecurityUtils;
import com.hm.common.utils.StringUtils;
import com.hm.manage.domain.SupportEquipmentCabinet;
import com.hm.manage.domain.SupportEquipmentLink;
import com.hm.manage.domain.SupportEquipmentRoom;
import com.hm.manage.domain.SupportHardwareAsset;
import com.hm.manage.domain.SupportServer;
import com.hm.manage.domain.bo.SupportEquipmentPlacementBo;
import com.hm.manage.domain.vo.SupportEquipmentTopologyDeviceVo;
import com.hm.manage.mapper.SupportEquipmentLocationMapper;
import com.hm.manage.mapper.SupportEquipmentTopologyMapper;
import com.hm.manage.mapper.SupportHardwareAssetMapper;
import com.hm.manage.mapper.SupportServerMapper;
import com.hm.manage.mapper.SupportSiteMapper;
import com.hm.manage.service.ISupportChangeLogService;
import com.hm.manage.service.ISupportEquipmentTopologyService;
import com.hm.manage.util.SupportEquipmentLayoutUtils;

@Service
public class SupportEquipmentTopologyServiceImpl implements ISupportEquipmentTopologyService
{
    private static final String SOURCE_SERVER = "SERVER";
    private static final String SOURCE_HARDWARE = "HARDWARE";
    private static final String ASSET_SWITCH = "SWITCH";
    private static final String MEDIUM_OPTICAL = "OPTICAL";
    private static final String MEDIUM_ELECTRICAL = "ELECTRICAL";

    @Autowired
    private SupportEquipmentLocationMapper locationMapper;

    @Autowired
    private SupportEquipmentTopologyMapper topologyMapper;

    @Autowired
    private SupportHardwareAssetMapper hardwareAssetMapper;

    @Autowired
    private SupportServerMapper serverMapper;

    @Autowired
    private SupportSiteMapper siteMapper;

    @Autowired
    private ISupportChangeLogService changeLogService;

    @Override
    public Map<String, Object> selectTopology(Long siteId)
    {
        requireSite(siteId);
        List<SupportEquipmentRoom> rooms = locationMapper.selectRoomsBySiteId(siteId);
        List<SupportEquipmentCabinet> cabinets = locationMapper.selectCabinetsBySiteId(siteId);
        List<SupportEquipmentTopologyDeviceVo> devices = buildDevices(siteId, rooms, cabinets);
        Map<String, SupportEquipmentTopologyDeviceVo> deviceMap = new HashMap<>();
        for (SupportEquipmentTopologyDeviceVo device : devices)
        {
            deviceMap.put(device.getDeviceKey(), device);
        }
        List<SupportEquipmentLink> links = topologyMapper.selectLinksBySiteId(siteId);
        enrichLinks(links, deviceMap);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("rooms", rooms);
        data.put("cabinets", cabinets);
        data.put("devices", devices);
        data.put("links", links);
        return data;
    }

    @Override
    public List<SupportEquipmentLink> selectLinksBySiteId(Long siteId)
    {
        return topologyMapper.selectLinksBySiteId(siteId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateCabinetLayout(SupportEquipmentCabinet cabinet)
    {
        if (cabinet == null || cabinet.getCabinetId() == null)
        {
            throw new ServiceException("机柜ID不能为空");
        }
        SupportEquipmentCabinet original = locationMapper.selectCabinetByCabinetId(cabinet.getCabinetId());
        if (original == null)
        {
            throw new ServiceException("机柜不存在");
        }
        SupportEquipmentRoom room = locationMapper.selectRoomByRoomId(original.getRoomId());
        if (room == null)
        {
            throw new ServiceException("机柜所属机房不存在");
        }
        normalizeCabinetLayout(cabinet, room);
        cabinet.setSiteId(original.getSiteId());
        cabinet.setRoomId(original.getRoomId());
        cabinet.setCabinetNo(original.getCabinetNo());
        cabinet.setUpdateBy(SecurityUtils.getUsername());
        cabinet.setUpdateTime(DateUtils.getNowDate());
        int rows = locationMapper.updateCabinetLayout(cabinet);
        if (rows > 0)
        {
            changeLogService.record(original.getSiteId(), "UPDATE", "EQUIPMENT_CABINET", original.getCabinetId(), original.getCabinetNo(),
                "调整机柜 " + original.getCabinetNo() + " 的三维摆放位置", original, cabinet);
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateDevicePlacement(SupportEquipmentPlacementBo placement)
    {
        if (placement == null || placement.getSourceId() == null)
        {
            throw new ServiceException("设备来源和设备ID不能为空");
        }
        String sourceType = normalizeSourceType(placement.getSourceType());
        DeviceRef device = requireDevice(sourceType, placement.getSourceId());
        if (placement.getSiteId() != null && !placement.getSiteId().equals(device.siteId))
        {
            throw new ServiceException("不允许跨现场调整设备位置");
        }

        boolean clearPlacement = placement.getRoomId() == null && placement.getCabinetId() == null
            && placement.getRackUStart() == null && placement.getRackUEnd() == null;
        String roomName = null;
        String cabinetNo = null;
        Integer rackUStart = null;
        Integer rackUEnd = null;
        if (!clearPlacement)
        {
            if (placement.getRoomId() == null || placement.getCabinetId() == null
                || placement.getRackUStart() == null || placement.getRackUEnd() == null)
            {
                throw new ServiceException("机房、机柜、起始U位和结束U位需要完整配置");
            }
            SupportEquipmentRoom room = locationMapper.selectRoomByRoomId(placement.getRoomId());
            SupportEquipmentCabinet cabinet = locationMapper.selectCabinetByCabinetId(placement.getCabinetId());
            if (room == null || cabinet == null)
            {
                throw new ServiceException("机房或机柜不存在");
            }
            if (!device.siteId.equals(room.getSiteId()) || !device.siteId.equals(cabinet.getSiteId())
                || !room.getRoomId().equals(cabinet.getRoomId()))
            {
                throw new ServiceException("设备、机房和机柜必须属于同一现场");
            }
            rackUStart = placement.getRackUStart();
            rackUEnd = placement.getRackUEnd();
            int capacity = cabinet.getUCapacity() == null ? 45 : cabinet.getUCapacity();
            if (rackUStart < 1 || rackUEnd < rackUStart || rackUEnd > capacity)
            {
                throw new ServiceException("U位范围必须在1到" + capacity + "之间，且起始U位不能大于结束U位");
            }
            roomName = room.getRoomName();
            cabinetNo = cabinet.getCabinetNo();
            Long excludeAssetId = SOURCE_HARDWARE.equals(sourceType) ? placement.getSourceId() : null;
            Long excludeServerId = SOURCE_SERVER.equals(sourceType) ? placement.getSourceId() : null;
            int conflicts = locationMapper.countHardwareRackConflicts(device.siteId, roomName, cabinetNo,
                rackUStart, rackUEnd, excludeAssetId)
                + locationMapper.countServerRackConflicts(device.siteId, roomName, cabinetNo,
                    rackUStart, rackUEnd, excludeServerId);
            if (conflicts > 0)
            {
                throw new ServiceException("所选U位已被其他设备占用，请重新选择");
            }
        }

        if (Objects.equals(device.equipmentRoom, roomName) && Objects.equals(device.cabinetNo, cabinetNo)
            && Objects.equals(device.rackUStart, rackUStart) && Objects.equals(device.rackUEnd, rackUEnd))
        {
            return 0;
        }

        String username = SecurityUtils.getUsername();
        int rows = SOURCE_SERVER.equals(sourceType)
            ? locationMapper.updateServerPlacement(placement.getSourceId(), roomName, cabinetNo, rackUStart, rackUEnd,
                username, DateUtils.getNowDate())
            : locationMapper.updateHardwarePlacement(placement.getSourceId(), roomName, cabinetNo, rackUStart, rackUEnd,
                username, DateUtils.getNowDate());
        if (rows > 0)
        {
            Map<String, Object> before = buildLocationDetail(device.equipmentRoom, device.cabinetNo, device.rackUStart, device.rackUEnd);
            Map<String, Object> after = buildLocationDetail(roomName, cabinetNo, rackUStart, rackUEnd);
            String action = clearPlacement ? "清空" : "调整";
            changeLogService.record(device.siteId, "UPDATE", SOURCE_SERVER.equals(sourceType) ? "SERVER" : "HARDWARE_ASSET",
                placement.getSourceId(), device.name, action + "设备安装位置：" + device.name, before, after);
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertLink(SupportEquipmentLink link)
    {
        normalizeAndValidateLink(link, false);
        link.setCreateBy(SecurityUtils.getUsername());
        link.setCreateTime(DateUtils.getNowDate());
        int rows = topologyMapper.insertLink(link);
        if (rows > 0)
        {
            changeLogService.record(link.getSiteId(), "INSERT", "EQUIPMENT_LINK", link.getLinkId(), buildLinkName(link),
                "新增设备上联：" + buildLinkName(link), null, link);
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateLink(SupportEquipmentLink link)
    {
        if (link == null || link.getLinkId() == null)
        {
            throw new ServiceException("链路ID不能为空");
        }
        SupportEquipmentLink original = topologyMapper.selectLinkByLinkId(link.getLinkId());
        if (original == null)
        {
            throw new ServiceException("设备链路不存在");
        }
        normalizeAndValidateLink(link, true);
        if (!original.getSiteId().equals(link.getSiteId()))
        {
            throw new ServiceException("不允许跨现场修改设备链路");
        }
        link.setUpdateBy(SecurityUtils.getUsername());
        link.setUpdateTime(DateUtils.getNowDate());
        int rows = topologyMapper.updateLink(link);
        if (rows > 0)
        {
            changeLogService.record(link.getSiteId(), "UPDATE", "EQUIPMENT_LINK", link.getLinkId(), buildLinkName(link),
                "修改设备上联：" + buildLinkName(link), original, link);
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteLink(Long linkId)
    {
        SupportEquipmentLink original = topologyMapper.selectLinkByLinkId(linkId);
        if (original == null)
        {
            return 0;
        }
        int rows = topologyMapper.deleteLinkByLinkId(linkId);
        if (rows > 0)
        {
            changeLogService.record(original.getSiteId(), "DELETE", "EQUIPMENT_LINK", original.getLinkId(), buildLinkName(original),
                "删除设备上联：" + buildLinkName(original), original, null);
        }
        return rows;
    }

    private List<SupportEquipmentTopologyDeviceVo> buildDevices(Long siteId, List<SupportEquipmentRoom> rooms, List<SupportEquipmentCabinet> cabinets)
    {
        Map<String, SupportEquipmentRoom> roomMap = new HashMap<>();
        for (SupportEquipmentRoom room : rooms)
        {
            roomMap.put(normalizeKey(room.getRoomName()), room);
        }
        Map<String, SupportEquipmentCabinet> cabinetMap = new HashMap<>();
        for (SupportEquipmentCabinet cabinet : cabinets)
        {
            cabinetMap.put(cabinet.getRoomId() + ":" + normalizeKey(cabinet.getCabinetNo()), cabinet);
        }

        List<SupportEquipmentTopologyDeviceVo> devices = new ArrayList<>();
        SupportServer serverQuery = new SupportServer();
        serverQuery.setSiteId(siteId);
        for (SupportServer server : serverMapper.selectSupportServerList(serverQuery))
        {
            SupportEquipmentTopologyDeviceVo device = new SupportEquipmentTopologyDeviceVo();
            device.setDeviceKey(deviceKey(SOURCE_SERVER, server.getServerId()));
            device.setSourceType(SOURCE_SERVER);
            device.setSourceId(server.getServerId());
            device.setSiteId(siteId);
            device.setAssetType(SOURCE_SERVER);
            device.setAssetTypeLabel("服务器");
            device.setAssetName(StringUtils.defaultIfEmpty(server.getServerName(), server.getServerAddress()));
            device.setIpAddress(server.getServerAddress());
            device.setEquipmentRoom(server.getEquipmentRoom());
            device.setCabinetNo(server.getCabinetNo());
            device.setRackUStart(server.getRackUStart());
            device.setRackUEnd(server.getRackUEnd());
            device.setStatus(server.getStatus());
            resolvePlacement(device, roomMap, cabinetMap);
            devices.add(device);
        }

        SupportHardwareAsset assetQuery = new SupportHardwareAsset();
        assetQuery.setSiteId(siteId);
        for (SupportHardwareAsset asset : hardwareAssetMapper.selectSupportHardwareAssetList(assetQuery))
        {
            SupportEquipmentTopologyDeviceVo device = new SupportEquipmentTopologyDeviceVo();
            device.setDeviceKey(deviceKey(SOURCE_HARDWARE, asset.getAssetId()));
            device.setSourceType(SOURCE_HARDWARE);
            device.setSourceId(asset.getAssetId());
            device.setSiteId(siteId);
            device.setAssetType(asset.getAssetType());
            device.setAssetTypeLabel(assetTypeLabel(asset.getAssetType()));
            device.setAssetName(asset.getAssetName());
            device.setIpAddress(asset.getIpAddress());
            device.setManageIp(asset.getManageIp());
            device.setNetworkEnv(asset.getNetworkEnv());
            device.setEquipmentRoom(asset.getEquipmentRoom());
            device.setCabinetNo(asset.getCabinetNo());
            device.setRackUStart(asset.getRackUStart());
            device.setRackUEnd(asset.getRackUEnd());
            device.setLegacyPortCount(asset.getPortCount());
            device.setLegacyUplinkDevice(asset.getUplinkDevice());
            device.setStatus(asset.getStatus());
            resolvePlacement(device, roomMap, cabinetMap);
            devices.add(device);
        }
        return devices;
    }

    private void resolvePlacement(SupportEquipmentTopologyDeviceVo device, Map<String, SupportEquipmentRoom> roomMap,
        Map<String, SupportEquipmentCabinet> cabinetMap)
    {
        SupportEquipmentRoom room = roomMap.get(normalizeKey(device.getEquipmentRoom()));
        if (room == null)
        {
            return;
        }
        device.setRoomId(room.getRoomId());
        SupportEquipmentCabinet cabinet = cabinetMap.get(room.getRoomId() + ":" + normalizeKey(device.getCabinetNo()));
        if (cabinet != null)
        {
            device.setCabinetId(cabinet.getCabinetId());
        }
    }

    private void enrichLinks(List<SupportEquipmentLink> links, Map<String, SupportEquipmentTopologyDeviceVo> deviceMap)
    {
        for (SupportEquipmentLink link : links)
        {
            SupportEquipmentTopologyDeviceVo source = deviceMap.get(deviceKey(link.getSourceType(), link.getSourceId()));
            SupportEquipmentTopologyDeviceVo target = deviceMap.get(deviceKey(link.getTargetType(), link.getTargetId()));
            if (source != null)
            {
                link.setSourceName(source.getAssetName());
                link.setSourceIp(source.getIpAddress());
            }
            if (target != null)
            {
                link.setTargetName(target.getAssetName());
                link.setTargetIp(target.getIpAddress());
            }
        }
    }

    private void normalizeCabinetLayout(SupportEquipmentCabinet cabinet, SupportEquipmentRoom room)
    {
        SupportEquipmentLayoutUtils.normalizeAndValidate(cabinet, room);
        SupportEquipmentCabinet collision = SupportEquipmentLayoutUtils.findCollision(cabinet, locationMapper.selectCabinetsByRoomId(room.getRoomId()), room);
        if (collision != null)
        {
            throw new ServiceException("机柜位置与" + collision.getCabinetNo() + "重叠，请重新摆放");
        }
    }

    private void normalizeAndValidateLink(SupportEquipmentLink link, boolean update)
    {
        if (link == null || link.getSiteId() == null)
        {
            throw new ServiceException("现场ID不能为空");
        }
        requireSite(link.getSiteId());
        link.setSourceType(normalizeSourceType(link.getSourceType()));
        link.setTargetType(normalizeSourceType(link.getTargetType()));
        link.setMediumType(StringUtils.trimToEmpty(link.getMediumType()).toUpperCase(Locale.ROOT));
        link.setSourcePort(StringUtils.trimToEmpty(link.getSourcePort()));
        link.setTargetPort(StringUtils.trimToEmpty(link.getTargetPort()));
        if (link.getSourceId() == null || link.getTargetId() == null)
        {
            throw new ServiceException("链路两端设备不能为空");
        }
        if (!SOURCE_HARDWARE.equals(link.getTargetType()))
        {
            throw new ServiceException("上联目标必须选择交换机资产");
        }
        DeviceRef source = requireDevice(link.getSourceType(), link.getSourceId());
        DeviceRef target = requireDevice(link.getTargetType(), link.getTargetId());
        if (!link.getSiteId().equals(source.siteId) || !link.getSiteId().equals(target.siteId))
        {
            throw new ServiceException("仅允许关联同一现场内的设备");
        }
        if (link.getSourceType().equals(link.getTargetType()) && link.getSourceId().equals(link.getTargetId()))
        {
            throw new ServiceException("设备不能上联到自身");
        }
        if (!ASSET_SWITCH.equalsIgnoreCase(target.assetType))
        {
            throw new ServiceException("上联目标必须是交换机");
        }
        if (!MEDIUM_OPTICAL.equals(link.getMediumType()) && !MEDIUM_ELECTRICAL.equals(link.getMediumType()))
        {
            throw new ServiceException("链路介质只能选择光口或电口");
        }
        if (link.getPortCount() == null)
        {
            link.setPortCount(1);
        }
        if (link.getPortCount() < 1 || link.getPortCount() > 256)
        {
            throw new ServiceException("链路端口数量必须在1到256之间");
        }
        if (StringUtils.isBlank(link.getStatus()))
        {
            link.setStatus("0");
        }
        if (update && link.getLinkId() == null)
        {
            throw new ServiceException("链路ID不能为空");
        }
        if (topologyMapper.countDuplicateLink(link) > 0)
        {
            throw new ServiceException("相同设备、介质和端口的上联关系已存在");
        }
        link.setSourceName(source.name);
        link.setSourceIp(source.ip);
        link.setTargetName(target.name);
        link.setTargetIp(target.ip);
    }

    private DeviceRef requireDevice(String sourceType, Long sourceId)
    {
        if (SOURCE_SERVER.equals(sourceType))
        {
            SupportServer server = serverMapper.selectSupportServerByServerId(sourceId);
            if (server == null)
            {
                throw new ServiceException("源服务器不存在");
            }
            return new DeviceRef(server.getSiteId(), SOURCE_SERVER, server.getServerName(), server.getServerAddress(),
                server.getEquipmentRoom(), server.getCabinetNo(), server.getRackUStart(), server.getRackUEnd());
        }
        SupportHardwareAsset asset = hardwareAssetMapper.selectSupportHardwareAssetByAssetId(sourceId);
        if (asset == null)
        {
            throw new ServiceException("硬件资产不存在");
        }
        return new DeviceRef(asset.getSiteId(), asset.getAssetType(), asset.getAssetName(), asset.getIpAddress(),
            asset.getEquipmentRoom(), asset.getCabinetNo(), asset.getRackUStart(), asset.getRackUEnd());
    }

    private Map<String, Object> buildLocationDetail(String roomName, String cabinetNo, Integer rackUStart, Integer rackUEnd)
    {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("所属机房", roomName);
        detail.put("机柜编号", cabinetNo);
        detail.put("起始U位", rackUStart);
        detail.put("结束U位", rackUEnd);
        return detail;
    }

    private String normalizeSourceType(String sourceType)
    {
        String normalized = StringUtils.trimToEmpty(sourceType).toUpperCase(Locale.ROOT);
        if (!SOURCE_SERVER.equals(normalized) && !SOURCE_HARDWARE.equals(normalized))
        {
            throw new ServiceException("设备来源类型无效");
        }
        return normalized;
    }

    private void requireSite(Long siteId)
    {
        if (siteId == null || siteMapper.selectSupportSiteBySiteId(siteId) == null)
        {
            throw new ServiceException("现场不存在");
        }
    }

    private String buildLinkName(SupportEquipmentLink link)
    {
        String medium = MEDIUM_OPTICAL.equals(link.getMediumType()) ? "光口" : "电口";
        String source = StringUtils.defaultIfEmpty(link.getSourceName(), deviceKey(link.getSourceType(), link.getSourceId()));
        String target = StringUtils.defaultIfEmpty(link.getTargetName(), deviceKey(link.getTargetType(), link.getTargetId()));
        return source + " -> " + target + "（" + medium + " " + link.getPortCount() + "）";
    }

    private String assetTypeLabel(String assetType)
    {
        if ("DECODER".equalsIgnoreCase(assetType)) return "解码器";
        if ("TERMINAL".equalsIgnoreCase(assetType)) return "终端";
        if (ASSET_SWITCH.equalsIgnoreCase(assetType)) return "交换机";
        if ("GATEWAY".equalsIgnoreCase(assetType)) return "网闸";
        return StringUtils.defaultIfEmpty(assetType, "其他设备");
    }

    private String normalizeKey(String value)
    {
        return StringUtils.trimToEmpty(value).toLowerCase(Locale.ROOT);
    }

    private String deviceKey(String sourceType, Long sourceId)
    {
        return StringUtils.trimToEmpty(sourceType).toUpperCase(Locale.ROOT) + ":" + sourceId;
    }

    private static class DeviceRef
    {
        private final Long siteId;
        private final String assetType;
        private final String name;
        private final String ip;
        private final String equipmentRoom;
        private final String cabinetNo;
        private final Integer rackUStart;
        private final Integer rackUEnd;

        private DeviceRef(Long siteId, String assetType, String name, String ip, String equipmentRoom,
            String cabinetNo, Integer rackUStart, Integer rackUEnd)
        {
            this.siteId = siteId;
            this.assetType = assetType;
            this.name = name;
            this.ip = ip;
            this.equipmentRoom = equipmentRoom;
            this.cabinetNo = cabinetNo;
            this.rackUStart = rackUStart;
            this.rackUEnd = rackUEnd;
        }
    }
}
