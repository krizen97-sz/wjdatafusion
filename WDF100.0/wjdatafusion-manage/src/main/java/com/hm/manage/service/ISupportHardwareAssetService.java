package com.hm.manage.service;

import java.util.List;
import com.hm.manage.domain.SupportHardwareAsset;

public interface ISupportHardwareAssetService
{
    SupportHardwareAsset selectSupportHardwareAssetByAssetId(Long assetId);

    List<SupportHardwareAsset> selectSupportHardwareAssetList(SupportHardwareAsset asset);

    int insertSupportHardwareAsset(SupportHardwareAsset asset);

    int updateSupportHardwareAsset(SupportHardwareAsset asset);

    int deleteSupportHardwareAssetByAssetIds(Long[] assetIds);

    int bindPlatform(Long assetId, Long platformId);

    int unbindPlatform(Long assetId, Long platformId);
}
