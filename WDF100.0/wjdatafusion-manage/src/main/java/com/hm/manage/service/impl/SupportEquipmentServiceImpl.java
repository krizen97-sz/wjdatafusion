package com.hm.manage.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.hm.common.utils.StringUtils;
import com.hm.manage.domain.SupportEquipmentAsset;
import com.hm.manage.domain.SupportHardwareAsset;
import com.hm.manage.domain.SupportPlatform;
import com.hm.manage.domain.SupportServer;
import com.hm.manage.mapper.SupportHardwareAssetMapper;
import com.hm.manage.mapper.SupportPlatformMapper;
import com.hm.manage.mapper.SupportServerMapper;
import com.hm.manage.service.ISupportEquipmentService;

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
    private SupportPlatformMapper platformMapper;

    @Override
    public List<SupportEquipmentAsset> selectEquipmentAssetList(SupportEquipmentAsset query)
    {
        SupportEquipmentAsset safeQuery = query == null ? new SupportEquipmentAsset() : query;
        if (safeQuery.getSiteId() == null)
        {
            return new ArrayList<>();
        }
        List<SupportPlatform> platforms = platformMapper.selectPlatformsBySiteId(safeQuery.getSiteId());
        Map<Long, SupportPlatform> platformMap = platforms.stream()
            .collect(Collectors.toMap(SupportPlatform::getPlatformId, item -> item, (a, b) -> a));
        Map<Long, List<SupportPlatform>> serverPlatformMap = buildServerPlatformMap(platforms);

        List<SupportEquipmentAsset> rows = new ArrayList<>();
        rows.addAll(buildServerRows(safeQuery, serverPlatformMap, platformMap));
        rows.addAll(buildHardwareRows(safeQuery));
        return rows.stream()
            .filter(row -> matchQuery(row, safeQuery))
            .collect(Collectors.toList());
    }

    private Map<Long, List<SupportPlatform>> buildServerPlatformMap(List<SupportPlatform> platforms)
    {
        Map<Long, List<SupportPlatform>> result = new HashMap<>();
        for (SupportPlatform platform : platforms)
        {
            if (!"SUB".equalsIgnoreCase(platform.getPlatformLevel()))
            {
                continue;
            }
            List<SupportServer> servers = serverMapper.selectServersByPlatformId(platform.getPlatformId());
            for (SupportServer server : servers)
            {
                result.computeIfAbsent(server.getServerId(), key -> new ArrayList<>()).add(platform);
            }
        }
        return result;
    }

    private List<SupportEquipmentAsset> buildServerRows(SupportEquipmentAsset query, Map<Long, List<SupportPlatform>> serverPlatformMap, Map<Long, SupportPlatform> platformMap)
    {
        SupportServer serverQuery = new SupportServer();
        serverQuery.setSiteId(query.getSiteId());
        List<SupportServer> servers = serverMapper.selectSupportServerList(serverQuery);
        Map<Long, SupportEquipmentAsset> rowMap = new LinkedHashMap<>();
        for (SupportServer server : servers)
        {
            List<SupportPlatform> relatedPlatforms = serverPlatformMap.getOrDefault(server.getServerId(), new ArrayList<>());
            if (!matchPlatformScope(query, relatedPlatforms))
            {
                continue;
            }
            SupportEquipmentAsset row = new SupportEquipmentAsset();
            row.setSourceType(SOURCE_SERVER);
            row.setSourceId(server.getServerId());
            row.setSiteId(server.getSiteId());
            row.setAssetType(TYPE_SERVER);
            row.setAssetTypeLabel("服务器");
            row.setAssetName(StringUtils.defaultIfEmpty(server.getServerName(), server.getServerAddress()));
            row.setIpAddress(server.getServerAddress());
            row.setStatus(server.getStatus());
            fillServerBinding(row, relatedPlatforms, platformMap);
            rowMap.put(server.getServerId(), row);
        }
        return new ArrayList<>(rowMap.values());
    }

    private void fillServerBinding(SupportEquipmentAsset row, List<SupportPlatform> relatedPlatforms, Map<Long, SupportPlatform> platformMap)
    {
        if (relatedPlatforms == null || relatedPlatforms.isEmpty())
        {
            row.setBindingScope(SCOPE_UNBOUND);
            row.setBindingLabel("未关联子平台");
            return;
        }
        SupportPlatform firstSub = relatedPlatforms.get(0);
        SupportPlatform main = platformMap.get(firstSub.getParentPlatformId());
        row.setPlatformId(firstSub.getPlatformId());
        row.setPlatformName(firstSub.getPlatformName());
        row.setMainPlatformId(main == null ? firstSub.getParentPlatformId() : main.getPlatformId());
        row.setMainPlatformName(main == null ? null : main.getPlatformName());
        row.setNetworkEnv(main == null ? null : main.getNetworkEnv());
        row.setBindingScope(SCOPE_PLATFORM);
        if (relatedPlatforms.size() == 1)
        {
            row.setBindingLabel("子平台 · " + firstSub.getPlatformName());
        }
        else
        {
            row.setBindingLabel("关联 " + relatedPlatforms.size() + " 个子平台");
        }
    }

    private boolean matchPlatformScope(SupportEquipmentAsset query, List<SupportPlatform> relatedPlatforms)
    {
        if (query.getPlatformId() == null && query.getMainPlatformId() == null)
        {
            return true;
        }
        if (relatedPlatforms == null || relatedPlatforms.isEmpty())
        {
            return false;
        }
        if (query.getPlatformId() != null)
        {
            return relatedPlatforms.stream().anyMatch(platform ->
                Objects.equals(platform.getPlatformId(), query.getPlatformId()) ||
                Objects.equals(platform.getParentPlatformId(), query.getPlatformId()));
        }
        return relatedPlatforms.stream().anyMatch(platform -> Objects.equals(platform.getParentPlatformId(), query.getMainPlatformId()));
    }

    private List<SupportEquipmentAsset> buildHardwareRows(SupportEquipmentAsset query)
    {
        SupportHardwareAsset assetQuery = new SupportHardwareAsset();
        assetQuery.setSiteId(query.getSiteId());
        List<SupportHardwareAsset> assets = hardwareAssetMapper.selectSupportHardwareAssetList(assetQuery);
        List<SupportEquipmentAsset> rows = new ArrayList<>();
        for (SupportHardwareAsset asset : assets)
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
            row.setInstallLocation(asset.getInstallLocation());
            row.setStatus(asset.getStatus());
            row.setPlatformId(asset.getPlatformId());
            row.setPlatformName(asset.getPlatformName());
            row.setMainPlatformId(asset.getMainPlatformId());
            row.setMainPlatformName(asset.getMainPlatformName());
            row.setBindingScope(asset.getPlatformId() == null ? SCOPE_PUBLIC : SCOPE_PLATFORM);
            row.setBindingLabel(asset.getPlatformId() == null ? "现场公共资产" : resolvePlatformLevelLabel(asset.getPlatformLevel()) + " · " + asset.getPlatformName());
            rows.add(row);
        }
        return rows;
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
            !Objects.equals(row.getPlatformId(), query.getPlatformId()) &&
            !Objects.equals(row.getMainPlatformId(), query.getPlatformId()))
        {
            return false;
        }
        if (query.getMainPlatformId() != null)
        {
            boolean platformMatched = Objects.equals(row.getMainPlatformId(), query.getMainPlatformId()) || Objects.equals(row.getPlatformId(), query.getMainPlatformId());
            boolean publicSameNetwork = SCOPE_PUBLIC.equals(row.getBindingScope())
                && StringUtils.isNotBlank(query.getNetworkEnv())
                && query.getNetworkEnv().equals(row.getNetworkEnv());
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
            StringUtils.defaultString(row.getNetworkEnv()),
            StringUtils.defaultString(row.getBindingLabel())
        ).toLowerCase(Locale.ROOT);
        return text.contains(keyword);
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
