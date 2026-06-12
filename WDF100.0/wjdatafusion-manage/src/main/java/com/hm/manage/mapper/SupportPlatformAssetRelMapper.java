package com.hm.manage.mapper;

import org.apache.ibatis.annotations.Param;
import com.hm.manage.domain.SupportPlatformAssetRel;

public interface SupportPlatformAssetRelMapper
{
    int insertSupportPlatformAssetRel(SupportPlatformAssetRel rel);

    int deleteSupportPlatformAssetRel(@Param("platformId") Long platformId, @Param("assetId") Long assetId);

    int countByPlatformAndAsset(@Param("platformId") Long platformId, @Param("assetId") Long assetId);

    int deleteByPlatformId(Long platformId);

    int deleteByAssetId(Long assetId);

    int deleteBySiteIds(Long[] siteIds);
}
