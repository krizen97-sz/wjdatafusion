package com.hm.manage.service.impl;

import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.hm.common.utils.SecurityUtils;
import com.hm.manage.domain.SupportHardwareAsset;
import com.hm.manage.domain.SupportPlatform;
import com.hm.manage.domain.SupportSite;
import com.hm.manage.mapper.SupportHardwareAssetMapper;
import com.hm.manage.mapper.SupportPlatformAssetRelMapper;
import com.hm.manage.mapper.SupportPlatformMapper;
import com.hm.manage.mapper.SupportSiteMapper;
import com.hm.manage.service.ISupportChangeLogService;
import com.hm.manage.service.support.CredentialCryptoService;

class SupportEquipmentLegacyBindingTest
{
    @Test
    void legacyProfileSaveDoesNotEraseOtherPlatformBindingsWhenOwnershipWasNotEdited()
    {
        var service = new SupportHardwareAssetServiceImpl();
        var assets = mock(SupportHardwareAssetMapper.class);
        var relations = mock(SupportPlatformAssetRelMapper.class);
        var platforms = mock(SupportPlatformMapper.class);
        var sites = mock(SupportSiteMapper.class);
        ReflectionTestUtils.setField(service, "hardwareAssetMapper", assets);
        ReflectionTestUtils.setField(service, "platformAssetRelMapper", relations);
        ReflectionTestUtils.setField(service, "platformMapper", platforms);
        ReflectionTestUtils.setField(service, "siteMapper", sites);
        ReflectionTestUtils.setField(service, "changeLogService", mock(ISupportChangeLogService.class));
        ReflectionTestUtils.setField(service, "cryptoService", mock(CredentialCryptoService.class));
        var original = asset("old");
        when(assets.selectSupportHardwareAssetByAssetId(1L)).thenReturn(original);
        when(assets.updateSupportHardwareAsset(any())).thenReturn(1);
        when(sites.selectSupportSiteBySiteId(9L)).thenReturn(new SupportSite());
        when(sites.selectSiteIdForUpdate(9L)).thenReturn(9L);
        var platform = new SupportPlatform();
        platform.setPlatformId(10L); platform.setSiteId(9L); platform.setPlatformLevel("SUB");
        when(platforms.selectSupportPlatformByPlatformId(10L)).thenReturn(platform);
        try (var auth = mockStatic(SecurityUtils.class))
        {
            auth.when(SecurityUtils::getUsername).thenReturn("test-operator");
            service.updateSupportHardwareAsset(asset("new"));
        }
        verifyNoInteractions(relations);
    }

    private SupportHardwareAsset asset(String name)
    {
        var asset = new SupportHardwareAsset();
        asset.setAssetId(1L); asset.setSiteId(9L); asset.setPlatformId(10L);
        asset.setAssetName(name); asset.setAssetType("SWITCH");
        asset.setNetworkEnv("IMAGE"); asset.setIpAddress("192.0.2.10"); asset.setStatus("0");
        return asset;
    }
}
