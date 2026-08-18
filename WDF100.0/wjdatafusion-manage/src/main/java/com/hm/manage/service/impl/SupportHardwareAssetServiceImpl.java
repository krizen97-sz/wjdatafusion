package com.hm.manage.service.impl;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.hm.common.exception.ServiceException;
import com.hm.common.utils.DateUtils;
import com.hm.common.utils.SecurityUtils;
import com.hm.common.utils.StringUtils;
import com.hm.manage.domain.SupportHardwareAsset;
import com.hm.manage.domain.SupportPlatform;
import com.hm.manage.domain.SupportPlatformAssetRel;
import com.hm.manage.mapper.SupportHardwareAssetMapper;
import com.hm.manage.mapper.SupportPlatformAssetRelMapper;
import com.hm.manage.mapper.SupportPlatformMapper;
import com.hm.manage.mapper.SupportSiteMapper;
import com.hm.manage.service.ISupportChangeLogService;
import com.hm.manage.service.ISupportHardwareAssetService;
import com.hm.manage.service.support.CredentialCryptoService;

@Service
public class SupportHardwareAssetServiceImpl implements ISupportHardwareAssetService
{
    private static final String LEVEL_MAIN = "MAIN";
    private static final String LEVEL_SUB = "SUB";
    private static final String TYPE_SERVER = "SERVER";

    @Autowired
    private SupportHardwareAssetMapper hardwareAssetMapper;

    @Autowired
    private SupportPlatformAssetRelMapper platformAssetRelMapper;

    @Autowired
    private SupportPlatformMapper platformMapper;

    @Autowired
    private SupportSiteMapper siteMapper;

    @Autowired
    private ISupportChangeLogService changeLogService;

    @Autowired
    private CredentialCryptoService cryptoService;

    @Override
    public SupportHardwareAsset selectSupportHardwareAssetByAssetId(Long assetId)
    {
        return hardwareAssetMapper.selectSupportHardwareAssetByAssetId(assetId);
    }

    @Override
    public List<SupportHardwareAsset> selectSupportHardwareAssetList(SupportHardwareAsset asset)
    {
        return hardwareAssetMapper.selectSupportHardwareAssetList(asset);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertSupportHardwareAsset(SupportHardwareAsset asset)
    {
        normalizeAndValidate(asset, false);
        encryptPassword(asset);
        asset.setCreateBy(SecurityUtils.getUsername());
        asset.setCreateTime(DateUtils.getNowDate());
        int rows = hardwareAssetMapper.insertSupportHardwareAsset(asset);
        if (rows > 0)
        {
            bindPlatformIfPresent(asset);
            changeLogService.record(asset.getSiteId(), "INSERT", "HARDWARE_ASSET", asset.getAssetId(), asset.getAssetName(), buildSummary("新增硬件资产", asset), null, asset);
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateSupportHardwareAsset(SupportHardwareAsset asset)
    {
        if (asset == null || asset.getAssetId() == null)
        {
            throw new ServiceException("资产ID不能为空");
        }
        SupportHardwareAsset original = hardwareAssetMapper.selectSupportHardwareAssetByAssetId(asset.getAssetId());
        if (original == null)
        {
            throw new ServiceException("硬件资产不存在");
        }
        normalizeAndValidate(asset, true);
        encryptPassword(asset);
        asset.setUpdateBy(SecurityUtils.getUsername());
        asset.setUpdateTime(DateUtils.getNowDate());
        int rows = hardwareAssetMapper.updateSupportHardwareAsset(asset);
        if (rows > 0)
        {
            platformAssetRelMapper.deleteByAssetId(asset.getAssetId());
            bindPlatformIfPresent(asset);
            changeLogService.record(asset.getSiteId(), "UPDATE", "HARDWARE_ASSET", asset.getAssetId(), asset.getAssetName(), buildSummary("修改硬件资产", asset), original, asset);
        }
        return rows;
    }

    @Override
    public String getHardwareAssetPasswordPlain(Long assetId)
    {
        SupportHardwareAsset asset = hardwareAssetMapper.selectSupportHardwareAssetByAssetId(assetId);
        if (asset == null)
        {
            return StringUtils.EMPTY;
        }
        return cryptoService.decrypt(asset.getLoginPasswordCipher());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteSupportHardwareAssetByAssetIds(Long[] assetIds)
    {
        List<SupportHardwareAsset> deletedAssets = new ArrayList<>();
        for (Long assetId : assetIds)
        {
            SupportHardwareAsset asset = hardwareAssetMapper.selectSupportHardwareAssetByAssetId(assetId);
            if (asset != null)
            {
                deletedAssets.add(asset);
            }
            platformAssetRelMapper.deleteByAssetId(assetId);
        }
        int rows = hardwareAssetMapper.deleteSupportHardwareAssetByAssetIds(assetIds);
        if (rows > 0)
        {
            for (SupportHardwareAsset asset : deletedAssets)
            {
                changeLogService.record(asset.getSiteId(), "DELETE", "HARDWARE_ASSET", asset.getAssetId(), asset.getAssetName(), buildSummary("删除硬件资产", asset), asset, null);
            }
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int bindPlatform(Long assetId, Long platformId)
    {
        SupportHardwareAsset asset = requireAsset(assetId);
        SupportPlatform platform = requirePlatform(platformId);
        ensureSameSite(asset, platform);
        if (platformAssetRelMapper.countByPlatformAndAsset(platformId, assetId) > 0)
        {
            throw new ServiceException("当前硬件资产已绑定该平台");
        }
        platformAssetRelMapper.deleteByAssetId(assetId);
        SupportPlatformAssetRel rel = new SupportPlatformAssetRel();
        rel.setAssetId(assetId);
        rel.setPlatformId(platformId);
        rel.setCreateBy(SecurityUtils.getUsername());
        rel.setCreateTime(DateUtils.getNowDate());
        int rows = platformAssetRelMapper.insertSupportPlatformAssetRel(rel);
        if (rows > 0)
        {
            changeLogService.record(asset.getSiteId(), "BIND", "HARDWARE_ASSET", assetId, asset.getAssetName(), "平台 " + platform.getPlatformName() + " 绑定硬件资产 " + asset.getAssetName());
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int unbindPlatform(Long assetId, Long platformId)
    {
        SupportHardwareAsset asset = requireAsset(assetId);
        SupportPlatform platform = requirePlatform(platformId);
        int rows = platformAssetRelMapper.deleteSupportPlatformAssetRel(platformId, assetId);
        if (rows > 0)
        {
            changeLogService.record(asset.getSiteId(), "UNBIND", "HARDWARE_ASSET", assetId, asset.getAssetName(), "平台 " + platform.getPlatformName() + " 解绑硬件资产 " + asset.getAssetName());
        }
        return rows;
    }

    private void normalizeAndValidate(SupportHardwareAsset asset, boolean update)
    {
        if (asset == null)
        {
            throw new ServiceException("硬件资产数据不能为空");
        }
        if (update && asset.getAssetId() == null)
        {
            throw new ServiceException("资产ID不能为空");
        }
        if (asset.getSiteId() == null)
        {
            throw new ServiceException("现场ID不能为空");
        }
        if (siteMapper.selectSupportSiteBySiteId(asset.getSiteId()) == null)
        {
            throw new ServiceException("现场不存在");
        }
        asset.setAssetName(StringUtils.trimToEmpty(asset.getAssetName()));
        asset.setAssetType(StringUtils.trimToEmpty(asset.getAssetType()));
        asset.setNetworkEnv(StringUtils.trimToEmpty(asset.getNetworkEnv()));
        asset.setIpAddress(StringUtils.trimToEmpty(asset.getIpAddress()));
        normalizeOptionalFields(asset);
        SupportPlatform platform = resolvePlatformForAsset(asset);
        if (StringUtils.isBlank(asset.getNetworkEnv()) && platform != null)
        {
            asset.setNetworkEnv(StringUtils.trimToEmpty(resolvePlatformNetworkEnv(platform)));
        }
        if (StringUtils.isBlank(asset.getAssetName()))
        {
            throw new ServiceException("资产名称不能为空");
        }
        if (StringUtils.isBlank(asset.getAssetType()))
        {
            throw new ServiceException("资产类型不能为空");
        }
        if (TYPE_SERVER.equalsIgnoreCase(asset.getAssetType()))
        {
            throw new ServiceException("服务器请继续使用服务器管理维护");
        }
        if (StringUtils.isBlank(asset.getNetworkEnv()))
        {
            throw new ServiceException("网络环境不能为空");
        }
        if (StringUtils.isBlank(asset.getIpAddress()))
        {
            throw new ServiceException("IP地址不能为空");
        }
        validateRackLocation(asset);
        if (StringUtils.isBlank(asset.getStatus()))
        {
            asset.setStatus("0");
        }
        SupportHardwareAsset sameIpAsset = hardwareAssetMapper.selectSupportHardwareAssetBySiteAndIp(asset.getSiteId(), asset.getIpAddress(), update ? asset.getAssetId() : null);
        if (sameIpAsset != null)
        {
            throw new ServiceException("当前现场已存在相同IP的硬件资产");
        }
    }

    private void normalizeOptionalFields(SupportHardwareAsset asset)
    {
        asset.setManageIp(StringUtils.trimToEmpty(asset.getManageIp()));
        asset.setMacAddress(StringUtils.trimToEmpty(asset.getMacAddress()));
        asset.setManufacturer(StringUtils.trimToEmpty(asset.getManufacturer()));
        asset.setAssetModel(StringUtils.trimToEmpty(asset.getAssetModel()));
        asset.setSerialNo(StringUtils.trimToEmpty(asset.getSerialNo()));
        asset.setInstallLocation(StringUtils.trimToEmpty(asset.getInstallLocation()));
        asset.setEquipmentRoom(StringUtils.trimToEmpty(asset.getEquipmentRoom()));
        asset.setCabinetNo(StringUtils.trimToEmpty(asset.getCabinetNo()));
        asset.setOwnerOrg(StringUtils.trimToEmpty(asset.getOwnerOrg()));
        asset.setOwnerContact(StringUtils.trimToEmpty(asset.getOwnerContact()));
        asset.setLoginUsername(StringUtils.trimToEmpty(asset.getLoginUsername()));
        asset.setOutputType(StringUtils.trimToEmpty(asset.getOutputType()));
        asset.setTerminalType(StringUtils.trimToEmpty(asset.getTerminalType()));
        asset.setDepartment(StringUtils.trimToEmpty(asset.getDepartment()));
        asset.setUseLocation(StringUtils.trimToEmpty(asset.getUseLocation()));
        asset.setSwitchLevel(StringUtils.trimToEmpty(asset.getSwitchLevel()));
        asset.setUplinkDevice(StringUtils.trimToEmpty(asset.getUplinkDevice()));
        asset.setVlanInfo(StringUtils.trimToEmpty(asset.getVlanInfo()));
        asset.setGatewayMode(StringUtils.trimToEmpty(asset.getGatewayMode()));
        asset.setGatewayDirection(StringUtils.trimToEmpty(asset.getGatewayDirection()));
        asset.setGatewayBandwidth(StringUtils.trimToEmpty(asset.getGatewayBandwidth()));
        asset.setSecurityZone(StringUtils.trimToEmpty(asset.getSecurityZone()));
    }

    private void validateRackLocation(SupportHardwareAsset asset)
    {
        Integer rackUStart = asset.getRackUStart();
        Integer rackUEnd = asset.getRackUEnd();
        if (rackUStart == null && rackUEnd == null)
        {
            return;
        }
        if (rackUStart == null || rackUEnd == null)
        {
            throw new ServiceException("设备U位需要同时选择起始U位和结束U位");
        }
        if (rackUStart < 1 || rackUStart > 45 || rackUEnd < 1 || rackUEnd > 45)
        {
            throw new ServiceException("设备U位范围必须在1U到45U之间");
        }
        if (rackUStart > rackUEnd)
        {
            throw new ServiceException("设备起始U位不能大于结束U位");
        }
    }

    private void encryptPassword(SupportHardwareAsset asset)
    {
        if (StringUtils.isNotEmpty(asset.getLoginPassword()))
        {
            asset.setLoginPasswordCipher(cryptoService.encrypt(asset.getLoginPassword()));
        }
        asset.setLoginPassword(null);
    }

    private SupportPlatform resolvePlatformForAsset(SupportHardwareAsset asset)
    {
        if (asset.getPlatformId() == null)
        {
            return null;
        }
        SupportPlatform platform = requirePlatform(asset.getPlatformId());
        ensureSameSite(asset, platform);
        return platform;
    }

    private String resolvePlatformNetworkEnv(SupportPlatform platform)
    {
        if (LEVEL_SUB.equalsIgnoreCase(platform.getPlatformLevel()) && platform.getParentPlatformId() != null)
        {
            SupportPlatform mainPlatform = platformMapper.selectSupportPlatformByPlatformId(platform.getParentPlatformId());
            if (mainPlatform != null && StringUtils.isNotBlank(mainPlatform.getNetworkEnv()))
            {
                return mainPlatform.getNetworkEnv();
            }
        }
        return platform.getNetworkEnv();
    }

    private void bindPlatformIfPresent(SupportHardwareAsset asset)
    {
        if (asset.getPlatformId() == null)
        {
            return;
        }
        SupportPlatformAssetRel rel = new SupportPlatformAssetRel();
        rel.setAssetId(asset.getAssetId());
        rel.setPlatformId(asset.getPlatformId());
        rel.setCreateBy(SecurityUtils.getUsername());
        rel.setCreateTime(DateUtils.getNowDate());
        platformAssetRelMapper.insertSupportPlatformAssetRel(rel);
    }

    private SupportHardwareAsset requireAsset(Long assetId)
    {
        SupportHardwareAsset asset = hardwareAssetMapper.selectSupportHardwareAssetByAssetId(assetId);
        if (asset == null)
        {
            throw new ServiceException("硬件资产不存在");
        }
        return asset;
    }

    private SupportPlatform requirePlatform(Long platformId)
    {
        SupportPlatform platform = platformMapper.selectSupportPlatformByPlatformId(platformId);
        if (platform == null)
        {
            throw new ServiceException("平台不存在");
        }
        return platform;
    }

    private void ensureSameSite(SupportHardwareAsset asset, SupportPlatform platform)
    {
        if (!asset.getSiteId().equals(platform.getSiteId()))
        {
            throw new ServiceException("仅允许绑定同一现场下的平台");
        }
    }

    private String buildSummary(String action, SupportHardwareAsset asset)
    {
        return action + " " + asset.getAssetName() + "（" + asset.getAssetType() + " / " + asset.getNetworkEnv() + "）";
    }
}
