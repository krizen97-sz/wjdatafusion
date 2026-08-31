package com.hm.manage.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import com.hm.common.exception.ServiceException;
import com.hm.common.core.domain.entity.SysUser;
import com.hm.common.core.domain.model.LoginUser;
import com.hm.manage.domain.SupportEquipmentCabinet;
import com.hm.manage.domain.SupportEquipmentLink;
import com.hm.manage.domain.SupportEquipmentRoom;
import com.hm.manage.domain.SupportSite;
import com.hm.manage.domain.vo.SupportEquipmentTopologyDeviceVo;
import com.hm.manage.mapper.SupportEquipmentLocationMapper;
import com.hm.manage.mapper.SupportEquipmentTopologyMapper;
import com.hm.manage.mapper.SupportSiteMapper;
import com.hm.manage.service.ISupportChangeLogService;
import com.hm.manage.service.ISupportEquipmentTopologyService;

class SupportEquipmentTopologyWorkbookServiceImplTest
{
    @AfterEach
    void clearSecurityContext()
    {
        SecurityContextHolder.clearContext();
    }

    @Test
    void exportMustContainEditableTopologySheetsAndStableReferences() throws Exception
    {
        SupportEquipmentTopologyWorkbookServiceImpl service = new SupportEquipmentTopologyWorkbookServiceImpl();
        SupportSiteMapper siteMapper = mock(SupportSiteMapper.class);
        ISupportEquipmentTopologyService topologyService = mock(ISupportEquipmentTopologyService.class);
        ReflectionTestUtils.setField(service, "siteMapper", siteMapper);
        ReflectionTestUtils.setField(service, "topologyService", topologyService);

        SupportSite site = new SupportSite();
        site.setSiteId(9L);
        site.setSiteName("测试现场");
        when(siteMapper.selectSupportSiteBySiteId(9L)).thenReturn(site);

        SupportEquipmentRoom room = new SupportEquipmentRoom();
        room.setRoomId(11L);
        room.setSiteId(9L);
        room.setRoomName("核心机房");
        room.setRoomWidth(new BigDecimal("12.00"));
        room.setRoomDepth(new BigDecimal("8.00"));
        room.setStatus("0");

        SupportEquipmentCabinet cabinet = new SupportEquipmentCabinet();
        cabinet.setCabinetId(21L);
        cabinet.setRoomId(11L);
        cabinet.setSiteId(9L);
        cabinet.setCabinetNo("A01");
        cabinet.setUCapacity(45);
        cabinet.setPositionX(new BigDecimal("1.20"));
        cabinet.setPositionZ(new BigDecimal("1.40"));
        cabinet.setRotationY(BigDecimal.ZERO);
        cabinet.setStatus("0");

        SupportEquipmentTopologyDeviceVo device = new SupportEquipmentTopologyDeviceVo();
        device.setSourceType("SERVER");
        device.setSourceId(31L);
        device.setAssetName("应用服务器");
        device.setIpAddress("10.0.0.31");
        device.setRoomId(11L);
        device.setCabinetId(21L);
        device.setRackUStart(10);
        device.setRackUEnd(12);

        SupportEquipmentLink link = new SupportEquipmentLink();
        link.setLinkId(41L);
        link.setSourceType("SERVER");
        link.setSourceId(31L);
        link.setSourceName("应用服务器");
        link.setTargetType("HARDWARE");
        link.setTargetId(32L);
        link.setTargetName("核心交换机");
        link.setMediumType("OPTICAL");
        link.setPortCount(2);
        link.setStatus("0");

        Map<String, Object> topology = new LinkedHashMap<>();
        topology.put("rooms", List.of(room));
        topology.put("cabinets", List.of(cabinet));
        topology.put("devices", List.of(device));
        topology.put("links", List.of(link));
        when(topologyService.selectTopology(9L)).thenReturn(topology);

        MockHttpServletResponse response = new MockHttpServletResponse();
        service.exportWorkbook(response, 9L);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray())))
        {
            assertEquals(5, workbook.getNumberOfSheets());
            assertEquals("ROOM-11", workbook.getSheet("机房").getRow(1).getCell(0).getStringCellValue());
            assertEquals("CABINET-21", workbook.getSheet("机柜").getRow(1).getCell(0).getStringCellValue());
            Row placement = workbook.getSheet("设备位置").getRow(1);
            assertEquals("ROOM-11", placement.getCell(4).getStringCellValue());
            assertEquals("CABINET-21", placement.getCell(5).getStringCellValue());
            assertEquals("保存", workbook.getSheet("设备链路").getRow(1).getCell(0).getStringCellValue());
        }
        assertTrue(response.getHeader("Content-Disposition").contains(".xlsx"));
    }

    @Test
    void importMustRejectWorkbookMissingRequiredSheetsBeforeWriting() throws Exception
    {
        SupportEquipmentTopologyWorkbookServiceImpl service = new SupportEquipmentTopologyWorkbookServiceImpl();
        SupportSiteMapper siteMapper = mock(SupportSiteMapper.class);
        ReflectionTestUtils.setField(service, "siteMapper", siteMapper);
        ReflectionTestUtils.setField(service, "locationMapper", mock(SupportEquipmentLocationMapper.class));
        ReflectionTestUtils.setField(service, "topologyMapper", mock(SupportEquipmentTopologyMapper.class));
        ReflectionTestUtils.setField(service, "topologyService", mock(ISupportEquipmentTopologyService.class));
        ReflectionTestUtils.setField(service, "changeLogService", mock(ISupportChangeLogService.class));
        SupportSite site = new SupportSite();
        site.setSiteId(9L);
        when(siteMapper.selectSupportSiteBySiteId(9L)).thenReturn(site);

        byte[] bytes;
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream())
        {
            Row header = workbook.createSheet("机房").createRow(0);
            String[] headers = {"机房标识", "机房名称", "机房编码", "宽度(米)", "深度(米)", "状态", "备注"};
            for (int index = 0; index < headers.length; index++) header.createCell(index).setCellValue(headers[index]);
            workbook.write(output);
            bytes = output.toByteArray();
        }
        MockMultipartFile file = new MockMultipartFile("file", "布局.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        ServiceException error = assertThrows(ServiceException.class, () -> service.importWorkbook(9L, file));
        assertTrue(error.getMessage().contains("缺少工作表：机柜"));
    }

    @Test
    void importMustResolveNewRoomAndCabinetReferencesBeforePlacingDevice() throws Exception
    {
        SupportEquipmentTopologyWorkbookServiceImpl service = new SupportEquipmentTopologyWorkbookServiceImpl();
        SupportSiteMapper siteMapper = mock(SupportSiteMapper.class);
        SupportEquipmentLocationMapper locationMapper = mock(SupportEquipmentLocationMapper.class);
        SupportEquipmentTopologyMapper topologyMapper = mock(SupportEquipmentTopologyMapper.class);
        ISupportEquipmentTopologyService topologyService = mock(ISupportEquipmentTopologyService.class);
        ISupportChangeLogService changeLogService = mock(ISupportChangeLogService.class);
        ReflectionTestUtils.setField(service, "siteMapper", siteMapper);
        ReflectionTestUtils.setField(service, "locationMapper", locationMapper);
        ReflectionTestUtils.setField(service, "topologyMapper", topologyMapper);
        ReflectionTestUtils.setField(service, "topologyService", topologyService);
        ReflectionTestUtils.setField(service, "changeLogService", changeLogService);
        authenticate("tester");

        SupportSite site = new SupportSite();
        site.setSiteId(9L);
        site.setSiteName("测试现场");
        when(siteMapper.selectSupportSiteBySiteId(9L)).thenReturn(site);

        List<SupportEquipmentRoom> roomStore = new ArrayList<>();
        List<SupportEquipmentCabinet> cabinetStore = new ArrayList<>();
        when(locationMapper.selectRoomsBySiteId(9L)).thenAnswer(invocation -> new ArrayList<>(roomStore));
        when(locationMapper.selectCabinetsBySiteId(9L)).thenAnswer(invocation -> new ArrayList<>(cabinetStore));
        doAnswer(invocation -> {
            SupportEquipmentRoom room = invocation.getArgument(0);
            room.setRoomId(101L);
            roomStore.add(room);
            return 1;
        }).when(locationMapper).insertRoom(any(SupportEquipmentRoom.class));
        doAnswer(invocation -> {
            SupportEquipmentCabinet cabinet = invocation.getArgument(0);
            cabinet.setCabinetId(201L);
            cabinetStore.add(cabinet);
            return 1;
        }).when(locationMapper).insertCabinet(any(SupportEquipmentCabinet.class));
        when(locationMapper.updateServerPlacement(eq(31L), eq("测试机房"), eq("A01"), eq(1), eq(2), eq("tester"), any()))
            .thenReturn(1);
        when(topologyMapper.selectLinksBySiteId(9L)).thenReturn(Collections.emptyList());

        SupportEquipmentTopologyDeviceVo device = new SupportEquipmentTopologyDeviceVo();
        device.setDeviceKey("SERVER:31");
        device.setSourceType("SERVER");
        device.setSourceId(31L);
        device.setSiteId(9L);
        device.setAssetType("SERVER");
        device.setAssetName("应用服务器");
        device.setIpAddress("10.0.0.31");
        Map<String, Object> topology = new LinkedHashMap<>();
        topology.put("devices", List.of(device));
        when(topologyService.selectTopology(9L)).thenReturn(topology);

        MockMultipartFile file = buildImportWorkbook();
        Map<String, Object> result = service.importWorkbook(9L, file);

        assertEquals(1, result.get("新增机房"));
        assertEquals(1, result.get("新增机柜"));
        assertEquals(1, result.get("更新设备位置"));
        assertEquals(3, result.get("变更总数"));
        verify(locationMapper).updateServerPlacement(eq(31L), eq("测试机房"), eq("A01"), eq(1), eq(2), eq("tester"), any());
    }

    private MockMultipartFile buildImportWorkbook() throws Exception
    {
        byte[] bytes;
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream())
        {
            writeSheet(workbook, "机房",
                new String[] {"机房标识", "机房名称", "机房编码", "宽度(米)", "深度(米)", "状态", "备注"},
                new String[] {"NEW-ROOM-1", "测试机房", "ROOM-T", "12", "8", "正常", ""});
            writeSheet(workbook, "机柜",
                new String[] {"机柜标识", "机房标识", "机柜编号", "机柜U数", "X坐标(米)", "Z坐标(米)", "朝向角度", "状态", "备注"},
                new String[] {"NEW-CABINET-1", "NEW-ROOM-1", "A01", "45", "1", "1", "0", "正常", ""});
            writeSheet(workbook, "设备位置",
                new String[] {"设备来源", "设备ID", "设备名称", "设备IP", "机房标识", "机柜标识", "起始U位", "结束U位"},
                new String[] {"SERVER", "31", "应用服务器", "10.0.0.31", "NEW-ROOM-1", "NEW-CABINET-1", "1", "2"});
            writeSheet(workbook, "设备链路",
                new String[] {"处理方式", "链路ID", "源设备来源", "源设备ID", "源设备名称", "目标设备来源", "目标设备ID", "目标设备名称", "连接介质", "端口数量", "设备端口", "交换机端口", "状态", "备注"},
                null);
            workbook.write(output);
            bytes = output.toByteArray();
        }
        return new MockMultipartFile("file", "机房布局.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
    }

    private void writeSheet(Workbook workbook, String name, String[] headers, String[] values)
    {
        Row header = workbook.createSheet(name).createRow(0);
        for (int index = 0; index < headers.length; index++) header.createCell(index).setCellValue(headers[index]);
        if (values == null) return;
        Row row = workbook.getSheet(name).createRow(1);
        for (int index = 0; index < values.length; index++) row.createCell(index).setCellValue(values[index]);
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
