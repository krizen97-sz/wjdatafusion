package com.hm.manage.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.hm.common.exception.ServiceException;
import com.hm.common.utils.StringUtils;
import com.hm.manage.domain.SupportEquipmentAsset;
import com.hm.manage.domain.SupportHardwareAsset;
import com.hm.manage.domain.SupportPlatform;
import com.hm.manage.domain.SupportServer;
import com.hm.manage.domain.bo.SupportEquipmentBatchBo;
import com.hm.manage.domain.bo.SupportEquipmentDeviceRefBo;
import com.hm.manage.domain.bo.SupportEquipmentPlatformBindingBo;
import com.hm.manage.domain.vo.SupportEquipmentPlatformBindingVo;
import com.hm.manage.mapper.SupportEquipmentBindingMapper;
import com.hm.manage.mapper.SupportHardwareAssetMapper;
import com.hm.manage.mapper.SupportServerMapper;
import com.hm.manage.service.ISupportEquipmentService;
import com.hm.manage.service.ISupportHardwareAssetService;
import com.hm.manage.service.ISupportPlatformService;
import com.hm.manage.service.ISupportServerService;

@Service
public class SupportEquipmentServiceImpl implements ISupportEquipmentService
{
    private static final String SOURCE_SERVER = "SERVER";
    private static final String SOURCE_HARDWARE = "HARDWARE";
    private static final String TYPE_SERVER = "SERVER";
    private static final String SCOPE_PUBLIC = "PUBLIC";
    private static final String SCOPE_PLATFORM = "PLATFORM";
    private static final String SCOPE_UNBOUND = "UNBOUND";

    @Autowired
    private SupportServerMapper serverMapper;

    @Autowired
    private SupportHardwareAssetMapper hardwareAssetMapper;

    @Autowired
    private SupportEquipmentBindingMapper equipmentBindingMapper;

    @Autowired
    private ISupportServerService serverService;

    @Autowired
    private ISupportHardwareAssetService hardwareAssetService;

    @Autowired
    private ISupportPlatformService platformService;

    @Override
    public List<SupportEquipmentAsset> selectEquipmentAssetList(SupportEquipmentAsset query)
    {
        SupportEquipmentAsset safeQuery = query == null ? new SupportEquipmentAsset() : query;
        if (safeQuery.getSiteId() == null)
        {
            return new ArrayList<>();
        }
        Map<Long, List<SupportEquipmentPlatformBindingVo>> serverBindings = groupBindings(
            equipmentBindingMapper.selectServerBindingsBySiteId(safeQuery.getSiteId()));
        Map<Long, List<SupportEquipmentPlatformBindingVo>> hardwareBindings = groupBindings(
            equipmentBindingMapper.selectHardwareBindingsBySiteId(safeQuery.getSiteId()));

        List<SupportEquipmentAsset> rows = new ArrayList<>();
        rows.addAll(buildServerRows(safeQuery, serverBindings));
        rows.addAll(buildHardwareRows(safeQuery, hardwareBindings));
        return rows.stream()
            .filter(row -> matchQuery(row, safeQuery))
            .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteEquipmentAssets(SupportEquipmentBatchBo command)
    {
        if (command == null || command.getSiteId() == null)
        {
            throw new ServiceException("现场ID不能为空");
        }
        if (command.getDevices() == null || command.getDevices().isEmpty())
        {
            throw new ServiceException("请选择需要删除的设备");
        }
        if (command.getDevices().size() > 500)
        {
            throw new ServiceException("单次最多删除500台设备");
        }

        Map<String, SupportEquipmentDeviceRefBo> uniqueRefs = new LinkedHashMap<>();
        for (SupportEquipmentDeviceRefBo ref : command.getDevices())
        {
            if (ref == null || ref.getSourceId() == null)
            {
                throw new ServiceException("设备来源和设备ID不能为空");
            }
            String sourceType = normalizeSourceType(ref.getSourceType());
            ref.setSourceType(sourceType);
            uniqueRefs.put(sourceType + ":" + ref.getSourceId(), ref);
        }

        List<Long> serverIds = new ArrayList<>();
        List<Long> hardwareIds = new ArrayList<>();
        for (SupportEquipmentDeviceRefBo ref : uniqueRefs.values())
        {
            if (SOURCE_SERVER.equals(ref.getSourceType()))
            {
                SupportServer server = serverService.selectSupportServerByServerId(ref.getSourceId());
                requireSameSite(command.getSiteId(), server == null ? null : server.getSiteId());
                serverIds.add(ref.getSourceId());
            }
            else
            {
                SupportHardwareAsset asset = hardwareAssetService.selectSupportHardwareAssetByAssetId(ref.getSourceId());
                requireSameSite(command.getSiteId(), asset == null ? null : asset.getSiteId());
                hardwareIds.add(ref.getSourceId());
            }
        }

        int rows = 0;
        if (!serverIds.isEmpty())
        {
            rows += serverService.deleteSupportServerByServerIds(serverIds.toArray(new Long[0]));
        }
        if (!hardwareIds.isEmpty())
        {
            rows += hardwareAssetService.deleteSupportHardwareAssetByAssetIds(hardwareIds.toArray(new Long[0]));
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int bindPlatform(SupportEquipmentPlatformBindingBo command)
    {
        validateBindingCommand(command);
        if (SOURCE_SERVER.equals(command.getSourceType()))
        {
            return platformService.bindServer(command.getPlatformId(), command.getSourceId());
        }
        return hardwareAssetService.bindPlatform(command.getSourceId(), command.getPlatformId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int unbindPlatform(SupportEquipmentPlatformBindingBo command)
    {
        validateBindingCommand(command);
        if (SOURCE_SERVER.equals(command.getSourceType()))
        {
            return platformService.unbindServer(command.getPlatformId(), command.getSourceId());
        }
        return hardwareAssetService.unbindPlatform(command.getSourceId(), command.getPlatformId());
    }

    private List<SupportEquipmentAsset> buildServerRows(SupportEquipmentAsset query,
        Map<Long, List<SupportEquipmentPlatformBindingVo>> bindingMap)
    {
        SupportServer serverQuery = new SupportServer();
        serverQuery.setSiteId(query.getSiteId());
        List<SupportEquipmentAsset> rows = new ArrayList<>();
        for (SupportServer server : serverMapper.selectSupportServerList(serverQuery))
        {
            SupportEquipmentAsset row = new SupportEquipmentAsset();
            row.setSourceType(SOURCE_SERVER);
            row.setSourceId(server.getServerId());
            row.setSiteId(server.getSiteId());
            row.setAssetType(TYPE_SERVER);
            row.setAssetTypeLabel("服务器");
            row.setAssetName(StringUtils.defaultIfEmpty(server.getServerName(), server.getServerAddress()));
            row.setIpAddress(server.getServerAddress());
            row.setLoginUsername(server.getOsUsername());
            row.setEquipmentRoom(server.getEquipmentRoom());
            row.setCabinetNo(server.getCabinetNo());
            row.setRackUStart(server.getRackUStart());
            row.setRackUEnd(server.getRackUEnd());
            row.setInstallLocation(formatInstallLocation(server.getEquipmentRoom(), server.getCabinetNo(),
                server.getRackUStart(), server.getRackUEnd(), null));
            row.setCredentialCapable(Boolean.TRUE);
            row.setStatus(server.getStatus());
            fillBinding(row, bindingMap.get(server.getServerId()), SCOPE_UNBOUND);
            rows.add(row);
        }
        return rows;
    }

    private List<SupportEquipmentAsset> buildHardwareRows(SupportEquipmentAsset query,
        Map<Long, List<SupportEquipmentPlatformBindingVo>> bindingMap)
    {
        SupportHardwareAsset assetQuery = new SupportHardwareAsset();
        assetQuery.setSiteId(query.getSiteId());
        Map<Long, SupportHardwareAsset> assets = new LinkedHashMap<>();
        for (SupportHardwareAsset asset : hardwareAssetMapper.selectSupportHardwareAssetList(assetQuery))
        {
            assets.putIfAbsent(asset.getAssetId(), asset);
        }

        List<SupportEquipmentAsset> rows = new ArrayList<>();
        for (SupportHardwareAsset asset : assets.values())
        {
            SupportEquipmentAsset row = new SupportEquipmentAsset();
            row.setSourceType(SOURCE_HARDWARE);
            row.setSourceId(asset.getAssetId());
            row.setSiteId(asset.getSiteId());
            row.setAssetType(asset.getAssetType());
            row.setAssetTypeLabel(resolveHardwareTypeLabel(asset.getAssetType()));
            row.setAssetName(asset.getAssetName());
            row.setNetworkEnv(asset.getNetworkEnv());
            row.setIpAddress(asset.getIpAddress());
            row.setManageIp(asset.getManageIp());
            row.setManufacturer(asset.getManufacturer());
            row.setAssetModel(asset.getAssetModel());
            row.setEquipmentRoom(asset.getEquipmentRoom());
            row.setCabinetNo(asset.getCabinetNo());
            row.setRackUStart(asset.getRackUStart());
            row.setRackUEnd(asset.getRackUEnd());
            row.setInstallLocation(formatInstallLocation(asset.getEquipmentRoom(), asset.getCabinetNo(),
                asset.getRackUStart(), asset.getRackUEnd(), asset.getInstallLocation()));
            row.setLoginUsername(asset.getLoginUsername());
            row.setPortCount(asset.getPortCount());
            row.setUplinkDevice(asset.getUplinkDevice());
            row.setCredentialCapable(Boolean.FALSE);
            row.setStatus(asset.getStatus());
            fillBinding(row, bindingMap.get(asset.getAssetId()), SCOPE_PUBLIC);
            rows.add(row);
        }
        return rows;
    }

    private void fillBinding(SupportEquipmentAsset row, List<SupportEquipmentPlatformBindingVo> sourceBindings,
        String emptyScope)
    {
        List<SupportEquipmentPlatformBindingVo> bindings = sourceBindings == null
            ? new ArrayList<>() : new ArrayList<>(sourceBindings);
        row.setPlatformBindings(bindings);
        row.setPlatformCount(bindings.size());
        row.setPlatformIds(distinctLongs(bindings.stream().map(SupportEquipmentPlatformBindingVo::getPlatformId).collect(Collectors.toList())));
        row.setMainPlatformIds(distinctLongs(bindings.stream().map(SupportEquipmentPlatformBindingVo::getMainPlatformId).collect(Collectors.toList())));
        row.setPlatformNames(bindings.stream().map(SupportEquipmentPlatformBindingVo::getPlatformName)
            .filter(StringUtils::isNotBlank).distinct().collect(Collectors.toList()));
        if (bindings.isEmpty())
        {
            row.setBindingScope(emptyScope);
            row.setBindingLabel(SCOPE_PUBLIC.equals(emptyScope) ? "现场公共设备" : "未归属平台");
            return;
        }

        SupportEquipmentPlatformBindingVo first = bindings.get(0);
        row.setPlatformId(first.getPlatformId());
        row.setPlatformName(first.getPlatformName());
        row.setPlatformLevel(first.getPlatformLevel());
        row.setMainPlatformId(first.getMainPlatformId());
        row.setMainPlatformName(first.getMainPlatformName());
        if (StringUtils.isBlank(row.getNetworkEnv()))
        {
            row.setNetworkEnv(first.getNetworkEnv());
        }
        row.setBindingScope(SCOPE_PLATFORM);
        row.setBindingLabel(bindings.size() == 1
            ? resolvePlatformLevelLabel(first.getPlatformLevel()) + " · " + first.getPlatformName()
            : "关联 " + bindings.size() + " 个平台");
    }

    private Map<Long, List<SupportEquipmentPlatformBindingVo>> groupBindings(List<SupportEquipmentPlatformBindingVo> bindings)
    {
        Map<Long, List<SupportEquipmentPlatformBindingVo>> result = new LinkedHashMap<>();
        if (bindings == null)
        {
            return result;
        }
        for (SupportEquipmentPlatformBindingVo binding : bindings)
        {
            if (binding == null || binding.getSourceId() == null)
            {
                continue;
            }
            result.computeIfAbsent(binding.getSourceId(), key -> new ArrayList<>()).add(binding);
        }
        return result;
    }

    private List<Long> distinctLongs(List<Long> values)
    {
        Set<Long> unique = new LinkedHashSet<>();
        for (Long value : values)
        {
            if (value != null)
            {
                unique.add(value);
            }
        }
        return new ArrayList<>(unique);
    }

    private boolean matchQuery(SupportEquipmentAsset row, SupportEquipmentAsset query)
    {
        if (StringUtils.isNotBlank(query.getSourceType()) && !query.getSourceType().equalsIgnoreCase(row.getSourceType()))
        {
            return false;
        }
        if (StringUtils.isNotBlank(query.getAssetType()) && !query.getAssetType().equalsIgnoreCase(row.getAssetType()))
        {
            return false;
        }
        if (StringUtils.isNotBlank(query.getNetworkEnv()) && !query.getNetworkEnv().equals(row.getNetworkEnv()))
        {
            return false;
        }
        if (StringUtils.isNotBlank(query.getStatus()) && !query.getStatus().equals(row.getStatus()))
        {
            return false;
        }
        if (StringUtils.isNotBlank(query.getBindingScope()) && !query.getBindingScope().equalsIgnoreCase(row.getBindingScope()))
        {
            return false;
        }
        if (query.getPlatformId() != null &&
            !row.getPlatformIds().contains(query.getPlatformId()) &&
            !row.getMainPlatformIds().contains(query.getPlatformId()))
        {
            return false;
        }
        if (query.getMainPlatformId() != null)
        {
            boolean platformMatched = row.getMainPlatformIds().contains(query.getMainPlatformId()) ||
                row.getPlatformIds().contains(query.getMainPlatformId());
            boolean publicSameNetwork = SCOPE_PUBLIC.equals(row.getBindingScope()) &&
                StringUtils.isNotBlank(query.getNetworkEnv()) && query.getNetworkEnv().equals(row.getNetworkEnv());
            if (!platformMatched && !publicSameNetwork)
            {
                return false;
            }
        }
        String keyword = StringUtils.trimToEmpty(query.getAssetName()).toLowerCase(Locale.ROOT);
        if (StringUtils.isBlank(keyword))
        {
            return true;
        }
        String text = String.join(" ",
            StringUtils.defaultString(row.getAssetName()),
            StringUtils.defaultString(row.getAssetTypeLabel()),
            StringUtils.defaultString(row.getIpAddress()),
            StringUtils.defaultString(row.getManageIp()),
            StringUtils.defaultString(row.getManufacturer()),
            StringUtils.defaultString(row.getAssetModel()),
            StringUtils.defaultString(row.getInstallLocation()),
            StringUtils.defaultString(row.getLoginUsername()),
            StringUtils.defaultString(row.getNetworkEnv()),
            StringUtils.defaultString(row.getBindingLabel())
        ).toLowerCase(Locale.ROOT);
        return text.contains(keyword);
    }

    private void validateBindingCommand(SupportEquipmentPlatformBindingBo command)
    {
        if (command == null || command.getSiteId() == null || command.getSourceId() == null || command.getPlatformId() == null)
        {
            throw new ServiceException("现场、设备和平台不能为空");
        }
        String sourceType = normalizeSourceType(command.getSourceType());
        command.setSourceType(sourceType);
        SupportPlatform platform = platformService.selectSupportPlatformByPlatformId(command.getPlatformId());
        requireSameSite(command.getSiteId(), platform == null ? null : platform.getSiteId());
        if (SOURCE_SERVER.equals(sourceType))
        {
            SupportServer server = serverService.selectSupportServerByServerId(command.getSourceId());
            requireSameSite(command.getSiteId(), server == null ? null : server.getSiteId());
        }
        else
        {
            SupportHardwareAsset asset = hardwareAssetService.selectSupportHardwareAssetByAssetId(command.getSourceId());
            requireSameSite(command.getSiteId(), asset == null ? null : asset.getSiteId());
        }
    }

    private void requireSameSite(Long expectedSiteId, Long actualSiteId)
    {
        if (actualSiteId == null)
        {
            throw new ServiceException("设备或平台不存在");
        }
        if (!Objects.equals(expectedSiteId, actualSiteId))
        {
            throw new ServiceException("仅允许管理当前现场下的设备和平台");
        }
    }

    private String normalizeSourceType(String sourceType)
    {
        String normalized = StringUtils.trimToEmpty(sourceType).toUpperCase(Locale.ROOT);
        if (!SOURCE_SERVER.equals(normalized) && !SOURCE_HARDWARE.equals(normalized))
        {
            throw new ServiceException("不支持的设备来源类型");
        }
        return normalized;
    }

    private String formatInstallLocation(String room, String cabinet, Integer startU, Integer endU, String fallback)
    {
        List<String> parts = new ArrayList<>();
        if (StringUtils.isNotBlank(room)) parts.add(room);
        if (StringUtils.isNotBlank(cabinet)) parts.add(cabinet);
        if (startU != null && endU != null)
        {
            parts.add(Objects.equals(startU, endU) ? startU + "U" : startU + "-" + endU + "U");
        }
        return parts.isEmpty() ? fallback : String.join(" / ", parts);
    }

    private String resolveHardwareTypeLabel(String type)
    {
        if ("DECODER".equalsIgnoreCase(type)) return "解码器";
        if ("TERMINAL".equalsIgnoreCase(type)) return "终端";
        if ("SWITCH".equalsIgnoreCase(type)) return "交换机";
        if ("GATEWAY".equalsIgnoreCase(type)) return "网闸";
        return StringUtils.defaultIfEmpty(type, "硬件");
    }

    private String resolvePlatformLevelLabel(String platformLevel)
    {
        if ("MAIN".equalsIgnoreCase(platformLevel)) return "主平台";
        if ("SUB".equalsIgnoreCase(platformLevel)) return "子平台";
        return "平台";
    }
}
