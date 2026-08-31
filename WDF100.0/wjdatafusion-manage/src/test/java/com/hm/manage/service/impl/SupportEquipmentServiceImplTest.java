package com.hm.manage.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.hm.manage.domain.SupportEquipmentAsset;
import com.hm.manage.domain.SupportHardwareAsset;
import com.hm.manage.domain.SupportServer;
import com.hm.manage.domain.bo.SupportEquipmentBatchBo;
import com.hm.manage.domain.bo.SupportEquipmentDeviceRefBo;
import com.hm.manage.domain.vo.SupportEquipmentPlatformBindingVo;
import com.hm.manage.mapper.SupportEquipmentBindingMapper;
import com.hm.manage.mapper.SupportHardwareAssetMapper;
import com.hm.manage.mapper.SupportServerMapper;
import com.hm.manage.service.ISupportHardwareAssetService;
import com.hm.manage.service.ISupportPlatformService;
import com.hm.manage.service.ISupportServerService;

class SupportEquipmentServiceImplTest
{
    private SupportEquipmentServiceImpl service;
    private SupportServerMapper serverMapper;
    private SupportHardwareAssetMapper hardwareAssetMapper;
    private SupportEquipmentBindingMapper equipmentBindingMapper;
    private ISupportServerService serverService;
    private ISupportHardwareAssetService hardwareAssetService;

    @BeforeEach
    void setUp()
    {
        service = new SupportEquipmentServiceImpl();
        serverMapper = mock(SupportServerMapper.class);
        hardwareAssetMapper = mock(SupportHardwareAssetMapper.class);
        equipmentBindingMapper = mock(SupportEquipmentBindingMapper.class);
        serverService = mock(ISupportServerService.class);
        hardwareAssetService = mock(ISupportHardwareAssetService.class);
        ReflectionTestUtils.setField(service, "serverMapper", serverMapper);
        ReflectionTestUtils.setField(service, "hardwareAssetMapper", hardwareAssetMapper);
        ReflectionTestUtils.setField(service, "equipmentBindingMapper", equipmentBindingMapper);
        ReflectionTestUtils.setField(service, "serverService", serverService);
        ReflectionTestUtils.setField(service, "hardwareAssetService", hardwareAssetService);
        ReflectionTestUtils.setField(service, "platformService", mock(ISupportPlatformService.class));
    }

    @Test
    void listMustExposeOneUnifiedProjectionWithLocationAndAllPlatformBindings()
    {
        SupportServer server = new SupportServer();
        server.setServerId(31L);
        server.setSiteId(9L);
        server.setServerName("应用服务器");
        server.setServerAddress("10.0.0.31");
        server.setEquipmentRoom("核心机房");
        server.setCabinetNo("A01");
        server.setRackUStart(10);
        server.setRackUEnd(12);
        server.setStatus("0");
        when(serverMapper.selectSupportServerList(org.mockito.ArgumentMatchers.any())).thenReturn(Collections.singletonList(server));

        SupportHardwareAsset hardware = new SupportHardwareAsset();
        hardware.setAssetId(41L);
        hardware.setSiteId(9L);
        hardware.setAssetName("核心交换机");
        hardware.setAssetType("SWITCH");
        hardware.setIpAddress("10.0.0.41");
        hardware.setEquipmentRoom("核心机房");
        hardware.setCabinetNo("A02");
        hardware.setRackUStart(20);
        hardware.setRackUEnd(20);
        hardware.setPortCount(48);
        hardware.setStatus("0");
        when(hardwareAssetMapper.selectSupportHardwareAssetList(org.mockito.ArgumentMatchers.any()))
            .thenReturn(Arrays.asList(hardware, hardware));

        SupportEquipmentPlatformBindingVo first = binding(31L, 101L, "视频平台", 100L, "主平台");
        SupportEquipmentPlatformBindingVo second = binding(31L, 102L, "存储平台", 100L, "主平台");
        when(equipmentBindingMapper.selectServerBindingsBySiteId(9L)).thenReturn(Arrays.asList(first, second));
        when(equipmentBindingMapper.selectHardwareBindingsBySiteId(9L)).thenReturn(Collections.emptyList());

        SupportEquipmentAsset query = new SupportEquipmentAsset();
        query.setSiteId(9L);
        List<SupportEquipmentAsset> rows = service.selectEquipmentAssetList(query);

        assertEquals(2, rows.size());
        SupportEquipmentAsset serverRow = rows.stream().filter(row -> "SERVER".equals(row.getSourceType())).findFirst().orElseThrow();
        assertEquals("核心机房 / A01 / 10-12U", serverRow.getInstallLocation());
        assertEquals(2, serverRow.getPlatformCount());
        assertEquals(Arrays.asList(101L, 102L), serverRow.getPlatformIds());
        assertTrue(serverRow.getCredentialCapable());
        SupportEquipmentAsset hardwareRow = rows.stream().filter(row -> "HARDWARE".equals(row.getSourceType())).findFirst().orElseThrow();
        assertEquals("核心机房 / A02 / 20U", hardwareRow.getInstallLocation());
        assertEquals(48, hardwareRow.getPortCount());
    }

    @Test
    void mixedDeleteMustValidateTheSiteAndDelegateBothAssetTypes()
    {
        SupportServer server = new SupportServer();
        server.setServerId(31L);
        server.setSiteId(9L);
        when(serverService.selectSupportServerByServerId(31L)).thenReturn(server);
        when(serverService.deleteSupportServerByServerIds(aryEq(new Long[]{31L}))).thenReturn(1);

        SupportHardwareAsset hardware = new SupportHardwareAsset();
        hardware.setAssetId(41L);
        hardware.setSiteId(9L);
        when(hardwareAssetService.selectSupportHardwareAssetByAssetId(41L)).thenReturn(hardware);
        when(hardwareAssetService.deleteSupportHardwareAssetByAssetIds(aryEq(new Long[]{41L}))).thenReturn(1);

        SupportEquipmentBatchBo command = new SupportEquipmentBatchBo();
        command.setSiteId(9L);
        command.setDevices(Arrays.asList(ref("SERVER", 31L), ref("HARDWARE", 41L)));

        assertEquals(2, service.deleteEquipmentAssets(command));
        verify(serverService).deleteSupportServerByServerIds(aryEq(new Long[]{31L}));
        verify(hardwareAssetService).deleteSupportHardwareAssetByAssetIds(aryEq(new Long[]{41L}));
    }

    private SupportEquipmentPlatformBindingVo binding(Long sourceId, Long platformId, String platformName,
        Long mainPlatformId, String mainPlatformName)
    {
        SupportEquipmentPlatformBindingVo binding = new SupportEquipmentPlatformBindingVo();
        binding.setSourceId(sourceId);
        binding.setPlatformId(platformId);
        binding.setPlatformName(platformName);
        binding.setPlatformLevel("SUB");
        binding.setMainPlatformId(mainPlatformId);
        binding.setMainPlatformName(mainPlatformName);
        binding.setNetworkEnv("图像网");
        return binding;
    }

    private SupportEquipmentDeviceRefBo ref(String sourceType, Long sourceId)
    {
        SupportEquipmentDeviceRefBo ref = new SupportEquipmentDeviceRefBo();
        ref.setSourceType(sourceType);
        ref.setSourceId(sourceId);
        return ref;
    }
}
