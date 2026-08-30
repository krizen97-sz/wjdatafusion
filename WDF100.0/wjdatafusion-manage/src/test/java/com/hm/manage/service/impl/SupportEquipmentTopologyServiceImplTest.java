package com.hm.manage.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import com.hm.common.core.domain.entity.SysUser;
import com.hm.common.core.domain.model.LoginUser;
import com.hm.manage.domain.SupportEquipmentCabinet;
import com.hm.manage.domain.SupportEquipmentRoom;
import com.hm.manage.domain.SupportServer;
import com.hm.manage.domain.bo.SupportEquipmentPlacementBo;
import com.hm.manage.mapper.SupportEquipmentLocationMapper;
import com.hm.manage.mapper.SupportEquipmentTopologyMapper;
import com.hm.manage.mapper.SupportHardwareAssetMapper;
import com.hm.manage.mapper.SupportServerMapper;
import com.hm.manage.mapper.SupportSiteMapper;
import com.hm.manage.service.ISupportChangeLogService;

class SupportEquipmentTopologyServiceImplTest
{
    @AfterEach
    void clearSecurityContext()
    {
        SecurityContextHolder.clearContext();
    }

    @Test
    void placementMustUpdateOnlyResolvedServerLocationFields()
    {
        SupportEquipmentTopologyServiceImpl service = new SupportEquipmentTopologyServiceImpl();
        SupportEquipmentLocationMapper locationMapper = mock(SupportEquipmentLocationMapper.class);
        SupportServerMapper serverMapper = mock(SupportServerMapper.class);
        ISupportChangeLogService changeLogService = mock(ISupportChangeLogService.class);
        ReflectionTestUtils.setField(service, "locationMapper", locationMapper);
        ReflectionTestUtils.setField(service, "topologyMapper", mock(SupportEquipmentTopologyMapper.class));
        ReflectionTestUtils.setField(service, "hardwareAssetMapper", mock(SupportHardwareAssetMapper.class));
        ReflectionTestUtils.setField(service, "serverMapper", serverMapper);
        ReflectionTestUtils.setField(service, "siteMapper", mock(SupportSiteMapper.class));
        ReflectionTestUtils.setField(service, "changeLogService", changeLogService);
        authenticate("tester");

        SupportServer server = new SupportServer();
        server.setServerId(31L);
        server.setSiteId(9L);
        server.setServerName("应用服务器");
        server.setServerAddress("10.0.0.31");
        when(serverMapper.selectSupportServerByServerId(31L)).thenReturn(server);

        SupportEquipmentRoom room = new SupportEquipmentRoom();
        room.setRoomId(11L);
        room.setSiteId(9L);
        room.setRoomName("核心机房");
        when(locationMapper.selectRoomByRoomId(11L)).thenReturn(room);

        SupportEquipmentCabinet cabinet = new SupportEquipmentCabinet();
        cabinet.setCabinetId(21L);
        cabinet.setRoomId(11L);
        cabinet.setSiteId(9L);
        cabinet.setCabinetNo("A01");
        cabinet.setUCapacity(45);
        cabinet.setPositionX(BigDecimal.ONE);
        cabinet.setPositionZ(BigDecimal.ONE);
        when(locationMapper.selectCabinetByCabinetId(21L)).thenReturn(cabinet);
        when(locationMapper.updateServerPlacement(eq(31L), eq("核心机房"), eq("A01"), eq(10), eq(12), eq("tester"), any()))
            .thenReturn(1);

        SupportEquipmentPlacementBo placement = new SupportEquipmentPlacementBo();
        placement.setSiteId(9L);
        placement.setSourceType("SERVER");
        placement.setSourceId(31L);
        placement.setRoomId(11L);
        placement.setCabinetId(21L);
        placement.setRackUStart(10);
        placement.setRackUEnd(12);

        assertEquals(1, service.updateDevicePlacement(placement));
        verify(locationMapper).updateServerPlacement(eq(31L), eq("核心机房"), eq("A01"), eq(10), eq(12), eq("tester"), any());
        verify(changeLogService).record(eq(9L), eq("UPDATE"), eq("SERVER"), eq(31L), eq("应用服务器"),
            eq("调整设备安装位置：应用服务器"), any(), any());
    }

    private void authenticate(String username)
    {
        SysUser user = new SysUser();
        user.setUserName(username);
        LoginUser loginUser = new LoginUser(user, Collections.emptySet());
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities()));
    }
}
