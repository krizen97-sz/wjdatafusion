package com.hm.manage.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.hm.manage.domain.SupportHardwareAsset;

public interface SupportHardwareAssetMapper
{
    SupportHardwareAsset selectSupportHardwareAssetByAssetId(Long assetId);

    List<SupportHardwareAsset> selectSupportHardwareAssetList(SupportHardwareAsset asset);

    SupportHardwareAsset selectSupportHardwareAssetBySiteAndIp(@Param("siteId") Long siteId, @Param("ipAddress") String ipAddress, @Param("assetId") Long assetId);

    int insertSupportHardwareAsset(SupportHardwareAsset asset);

    int updateSupportHardwareAsset(SupportHardwareAsset asset);

    int deleteSupportHardwareAssetByAssetId(Long assetId);

    int deleteSupportHardwareAssetByAssetIds(Long[] assetIds);

    int deleteSupportHardwareAssetBySiteIds(Long[] siteIds);
}
