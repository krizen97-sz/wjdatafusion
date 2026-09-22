package com.hm.manage.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;
import com.hm.common.exception.ServiceException;
import com.hm.manage.domain.SupportPlatform;
import com.hm.manage.domain.SupportServer;
import com.hm.manage.domain.SupportSite;
import com.hm.manage.domain.bo.SupportEquipmentCreateBo;
import com.hm.manage.domain.bo.SupportEquipmentDeviceRefBo;
import com.hm.manage.domain.bo.SupportEquipmentServerIntakeBo;
import com.hm.manage.domain.bo.SupportEquipmentServerIntakeBo.Row;
import com.hm.manage.domain.vo.SupportEquipmentPlatformBindingVo;
import com.hm.manage.mapper.SupportEquipmentBindingMapper;
import com.hm.manage.mapper.SupportServerMapper;
import com.hm.manage.mapper.SupportSiteMapper;
import com.hm.manage.service.ISupportEquipmentService;
import com.hm.manage.service.ISupportPlatformService;
import com.hm.manage.controller.SupportEquipmentController;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class SupportEquipmentServerIntakeTest
{
    private SupportEquipmentServerIntakeService service;
    private SupportSiteMapper sites;
    private SupportServerMapper servers;
    private SupportEquipmentBindingMapper bindings;
    private ISupportPlatformService platforms;
    private ISupportEquipmentService equipment;

    @BeforeEach
    void setup()
    {
        service = new SupportEquipmentServerIntakeService();
        sites = mock(SupportSiteMapper.class);
        servers = mock(SupportServerMapper.class);
        bindings = mock(SupportEquipmentBindingMapper.class);
        platforms = mock(ISupportPlatformService.class);
        equipment = mock(ISupportEquipmentService.class);
        ReflectionTestUtils.setField(service, "siteMapper", sites);
        ReflectionTestUtils.setField(service, "serverMapper", servers);
        ReflectionTestUtils.setField(service, "bindingMapper", bindings);
        ReflectionTestUtils.setField(service, "platformService", platforms);
        ReflectionTestUtils.setField(service, "equipmentService", equipment);
        when(sites.selectSupportSiteBySiteId(1L)).thenReturn(new SupportSite());
        when(sites.selectSiteIdForUpdate(1L)).thenReturn(1L);
        SupportPlatform platform = new SupportPlatform();
        platform.setSiteId(1L);
        platform.setPlatformId(2L);
        platform.setPlatformLevel("SUB");
        when(platforms.selectSupportPlatformByPlatformId(2L)).thenReturn(platform);
        when(servers.selectSupportServerList(any())).thenReturn(List.of());
        when(bindings.selectServerBindingsBySiteId(1L)).thenReturn(List.of());
        when(equipment.createEquipment(any())).thenReturn(new SupportEquipmentDeviceRefBo());
    }

    private Row row(String address, String port)
    {
        return new Row(2, "设备-" + address, address, port, "Linux", null, null, null, null, "0");
    }

    private SupportEquipmentServerIntakeBo command(List<Row> rows, boolean reuse)
    {
        return new SupportEquipmentServerIntakeBo(1L, 2L, null, null, null, rows, reuse);
    }

    private SupportEquipmentServerIntakeBo range(String text)
    {
        return new SupportEquipmentServerIntakeBo(1L, 2L, text, "服务器", row("", ""), null, false);
    }

    private SupportServer existing(Long id, String address)
    {
        SupportServer server = new SupportServer();
        server.setServerId(id); server.setSiteId(1L); server.setServerAddress(address); server.setServerName("已有设备");
        return server;
    }

    private byte[] template() throws Exception
    {
        MockHttpServletResponse response = new MockHttpServletResponse();
        service.exportTemplate(response);
        return response.getContentAsByteArray();
    }

    @Test
    void omittedReusePolicyHasSafeDefaultInActualJsonBinding() throws Exception
    {
        var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        var command = mapper.readValue("{\"siteId\":1,\"platformId\":2,\"addressText\":\"198.18.0.1\",\"defaults\":{\"sshPort\":\"55555\"}}",
            SupportEquipmentServerIntakeBo.class);
        assertEquals(Boolean.FALSE, command.reuseExisting());
        assertEquals(1, service.preview(command).createCount());
    }

    @Test
    void previewExpandsListsAndRangesWithoutWrites()
    {
        var preview = service.preview(range("198.18.0.1;198.18.0.2\n198.18.0.10-12"));
        assertEquals(5, preview.createCount());
        assertEquals("55555", preview.rows().get(0).data().sshPort());
        assertEquals("198.18.0.12", preview.rows().get(4).data().serverAddress());
        verifyNoInteractions(equipment);
        verify(sites, never()).selectSiteIdForUpdate(any());
    }

    @Test
    void malformedOversizedOrReverseRangesAreRejected()
    {
        for (String text : List.of("", "garbage", "198.18.0.256", "198.18.0.2-1", "198.18.0.1-198.18.3.1"))
            assertThrows(ServiceException.class, () -> service.preview(range(text)));
        verifyNoInteractions(equipment);
    }

    @Test
    void overlapAndEditedDuplicateAddressesRemainVisibleAsErrors()
    {
        var overlap = service.preview(range("198.18.0.1;198.18.0.1"));
        assertEquals(2, overlap.rows().size());
        assertEquals(2, overlap.errorCount());
        var edited = service.preview(command(List.of(row("198.18.0.01", "22"), row("198.18.0.1", "22")), false));
        assertEquals(2, edited.errorCount());
    }

    @Test
    void everyInvalidRowIsReturnedAndNoWriteOccurs()
    {
        var preview = service.preview(command(List.of(row("bad-ip", "abc"), row("198.18.0.1", "0"), row("198.18.0.2", "22")), false));
        assertEquals(2, preview.errorCount());
        assertEquals(1, preview.createCount());
        assertTrue(preview.rows().get(0).errors().size() >= 2);
        assertEquals("abc", preview.rows().get(0).data().sshPort());
        verifyNoInteractions(equipment);
    }

    @Test
    void foreignOrMainPlatformIsRejected()
    {
        var target = platforms.selectSupportPlatformByPlatformId(2L);
        target.setSiteId(9L);
        assertThrows(ServiceException.class, () -> service.preview(range("198.18.0.1")));
        target.setSiteId(1L); target.setPlatformLevel("MAIN");
        assertThrows(ServiceException.class, () -> service.preview(range("198.18.0.1")));
        verifyNoInteractions(servers, equipment);
    }

    @Test
    void duplicatePreviewShowsOwnershipAndExplicitReusePolicy()
    {
        when(servers.selectSupportServerList(any())).thenReturn(List.of(existing(3L, "198.18.0.1")));
        var owner = new SupportEquipmentPlatformBindingVo();
        owner.setSourceId(3L); owner.setPlatformId(9L); owner.setMainPlatformName("主平台"); owner.setPlatformName("原子平台");
        when(bindings.selectServerBindingsBySiteId(1L)).thenReturn(List.of(owner));
        var rows = List.of(row("198.18.0.1", "22"));
        var skipped = service.preview(command(rows, false));
        assertEquals(1, skipped.skipCount());
        assertEquals("主平台 / 原子平台", skipped.rows().get(0).existingScope());
        assertEquals(1, service.preview(command(rows, true)).reuseCount());
        owner.setPlatformId(2L);
        assertEquals(1, service.preview(command(rows, true)).skipCount());
    }

    @Test
    void ambiguousExistingDuplicatesCannotBeAutomaticallyReused()
    {
        when(servers.selectSupportServerList(any())).thenReturn(List.of(existing(3L, "198.18.0.1"), existing(4L, "198.18.0.1")));
        assertEquals(1, service.preview(command(List.of(row("198.18.0.1", "22")), true)).errorCount());
    }

    @Test
    void commitRevalidatesDatabaseInsteadOfTrustingPreview()
    {
        var command = command(List.of(row("198.18.0.1", "22")), false);
        assertEquals(1, service.preview(command).createCount());
        when(servers.selectSupportServerList(any())).thenReturn(List.of(existing(3L, "198.18.0.1")));
        var result = service.commit(command);
        assertEquals(0, result.createdCount());
        assertEquals(1, result.skippedCount());
        verifyNoInteractions(equipment);
    }

    @Test
    void commitCreatesThroughUnifiedServiceAfterSiteLock()
    {
        var result = service.commit(command(List.of(row("198.18.0.1", ""), row("198.18.0.2", "22")), false));
        assertEquals(2, result.createdCount());
        var order = inOrder(sites, servers, equipment);
        order.verify(sites).selectSiteIdForUpdate(1L);
        order.verify(sites).selectSupportSiteBySiteId(1L);
        order.verify(servers).selectSupportServerList(any());
        var capture = org.mockito.ArgumentCaptor.forClass(SupportEquipmentCreateBo.class);
        verify(equipment, times(2)).createEquipment(capture.capture());
        assertEquals(55555, capture.getAllValues().get(0).getServer().getSshPort());
        assertEquals(22, capture.getAllValues().get(1).getServer().getSshPort());
        assertEquals(1L, capture.getValue().getSiteId());
        assertEquals(2L, capture.getValue().getPlatformId());
        assertNull(capture.getValue().getServer().getServerId());
    }

    @Test
    void invalidSecondRowBlocksEntireBatchBeforeInsert()
    {
        assertThrows(ServiceException.class, () -> service.commit(command(List.of(row("198.18.0.1", "22"), row("bad", "22")), false)));
        verifyNoInteractions(equipment);
    }

    @Test
    void failureInSecondCreateRollsBackTheOuterSpringTransaction()
    {
        var manager = mock(PlatformTransactionManager.class);
        var transaction = new SimpleTransactionStatus();
        when(manager.getTransaction(any())).thenReturn(transaction);
        ProxyFactory factory = new ProxyFactory(service);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        var transactional = (SupportEquipmentServerIntakeService) factory.getProxy();
        when(equipment.createEquipment(any())).thenReturn(new SupportEquipmentDeviceRefBo()).thenThrow(new ServiceException("归属失败"));
        assertThrows(ServiceException.class, () -> transactional.commit(command(List.of(row("198.18.0.1", "22"), row("198.18.0.2", "22")), false)));
        verify(manager).rollback(transaction);
        verify(manager, never()).commit(any());
    }

    @Test
    void existingReuseDoesNotOverwriteCredentialsOrCreateAnotherServer()
    {
        when(servers.selectSupportServerList(any())).thenReturn(List.of(existing(3L, "198.18.0.1")));
        when(platforms.bindServer(2L, 3L)).thenReturn(1);
        var result = service.commit(command(List.of(row("198.18.0.1", "55555")), true));
        assertEquals(0, result.createdCount());
        assertEquals(1, result.boundCount());
        verifyNoInteractions(equipment);
        verify(servers, never()).updateSupportServer(any());
    }

    @Test
    void reusingExistingServerDoesNotRequireUnusedImportedCredentials()
    {
        when(servers.selectSupportServerList(any())).thenReturn(List.of(existing(3L, "198.18.0.1")));
        when(platforms.bindServer(2L, 3L)).thenReturn(1);
        Row imported = new Row(2, "ignored", "198.18.0.1", "ignored", "", null, null, "ops", "", "ignored");
        assertEquals(0, service.preview(command(List.of(imported), true)).errorCount());
        assertEquals(1, service.commit(command(List.of(imported), true)).boundCount());
        verifyNoInteractions(equipment);
    }

    @Test
    void downloadedTemplateRoundTripsAndKeepsInstructionsSeparate() throws Exception
    {
        byte[] bytes = template();
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes)))
        {
            assertEquals(55555, workbook.getSheetAt(0).getRow(1).getCell(2).getNumericCellValue());
            assertEquals(1, workbook.getSheetAt(0).getLastRowNum());
            assertNotNull(workbook.getSheet("填写说明"));
        }
        var result = service.previewFile(1L, 2L, new MockMultipartFile("file", "template.XLSX", null, bytes));
        assertEquals(1, result.createCount());
        assertEquals("55555", result.rows().get(0).data().sshPort());
        verifyNoInteractions(equipment);
    }

    @Test
    void spreadsheetInvalidFieldsProduceReviewRowsRatherThanPartialWrites() throws Exception
    {
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(template()));
             var output = new ByteArrayOutputStream())
        {
            var sheet = workbook.getSheetAt(0);
            sheet.getRow(1).getCell(2).setBlank();
            var invalid = sheet.createRow(2);
            invalid.createCell(0).setCellValue("错误行");
            invalid.createCell(1).setCellValue("not-an-ip");
            invalid.createCell(2).setCellValue("bad-port");
            workbook.write(output);
            var preview = service.previewFile(1L, 2L, new MockMultipartFile("file", "rows.xlsx", null, output.toByteArray()));
            assertEquals(2, preview.rows().size());
            assertEquals("55555", preview.rows().get(0).data().sshPort());
            assertEquals(1, preview.errorCount());
            assertEquals(3, preview.rows().get(1).data().rowNumber());
            verifyNoInteractions(equipment);
        }
    }

    @Test
    void importRejectsWrongHeadersOversizeAndNonXlsx() throws Exception
    {
        assertThrows(ServiceException.class, () -> service.parseFile(new MockMultipartFile("file", "file.csv", null, new byte[] {1})));
        assertThrows(ServiceException.class, () -> service.parseFile(new MockMultipartFile("file", "file.xlsx", null, new byte[(int) SupportEquipmentServerIntakeService.FILE_LIMIT + 1])));
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(template()));
             var output = new ByteArrayOutputStream())
        {
            workbook.getSheetAt(0).getRow(0).getCell(0).setCellValue("错误表头");
            workbook.write(output);
            assertThrows(ServiceException.class, () -> service.parseFile(new MockMultipartFile("file", "header.xlsx", null, output.toByteArray())));
        }
    }

    @Test
    void multipartEndpointParsesValidWorkbookWithoutPersisting() throws Exception
    {
        var controller = new SupportEquipmentController();
        ReflectionTestUtils.setField(controller, "serverIntakeService", service);
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        var response = mvc.perform(multipart("/support/equipment/servers/importPreview")
            .file(new MockMultipartFile("file", "template.xlsx", null, template()))
            .param("siteId", "1").param("platformId", "2")).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        var json = tools.jackson.databind.json.JsonMapper.builder().build().readValue(response.getContentAsString(), java.util.Map.class);
        assertEquals(200, json.get("code"));
        var data = (java.util.Map<?, ?>) json.get("data");
        assertEquals(1, data.get("createCount"));
        verifyNoInteractions(equipment);
    }

    @Test
    void jsonPreviewAndCommitEndpointsShareTheSameRowContract() throws Exception
    {
        var controller = new SupportEquipmentController();
        ReflectionTestUtils.setField(controller, "serverIntakeService", service);
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        var previewResponse = mvc.perform(post("/support/equipment/servers/preview")
            .contentType("application/json")
            .content("{\"siteId\":1,\"platformId\":2,\"addressText\":\"198.18.0.1-2\",\"defaults\":{\"sshPort\":\"55555\"}}"))
            .andReturn().getResponse();
        assertEquals(200, previewResponse.getStatus());
        verifyNoInteractions(equipment);
        var commitResponse = mvc.perform(post("/support/equipment/servers/commit")
            .contentType("application/json")
            .content(mapper.writeValueAsString(command(List.of(row("198.18.0.1", "55555"), row("198.18.0.2", "22")), false))))
            .andReturn().getResponse();
        assertEquals(200, commitResponse.getStatus());
        var json = mapper.readValue(commitResponse.getContentAsString(), java.util.Map.class);
        assertEquals(2, ((java.util.Map<?, ?>) json.get("data")).get("createdCount"));
        verify(equipment, times(2)).createEquipment(any());
    }

    @Test
    void credentialWhitespaceIsKeptAndUnpairedAccountIsRejected()
    {
        Row spaced = new Row(1, "设备", "198.18.0.1", "22", "", null, " secret ", null, null, "0");
        assertEquals(" secret ", service.preview(command(List.of(spaced), false)).rows().get(0).data().rootPassword());
        Row unpaired = new Row(1, "设备", "198.18.0.1", "22", "", null, null, "ops", "", "0");
        assertEquals(1, service.preview(command(List.of(unpaired), false)).errorCount());
    }
}
