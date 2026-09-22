package com.hm.manage.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import com.hm.common.exception.ServiceException;
import com.hm.manage.domain.SupportServer;
import com.hm.manage.mapper.SupportServerMapper;

class SupportServerIntakeDefaultsTest
{
    private final SupportServerServiceImpl service = new SupportServerServiceImpl();
    private SupportServerMapper mapper;

    @BeforeEach
    void setup()
    {
        mapper = mock(SupportServerMapper.class);
        ReflectionTestUtils.setField(service, "serverMapper", mapper);
    }

    private SupportServer server(Integer port)
    {
        SupportServer server = new SupportServer();
        server.setSiteId(1L);
        server.setServerName("intake-test");
        server.setServerAddress("198.18.0.1");
        server.setSshPort(port);
        return server;
    }

    private byte[] template() throws Exception
    {
        MockHttpServletResponse response = new MockHttpServletResponse();
        service.exportImportTemplate(response);
        return response.getContentAsByteArray();
    }

    @Test
    void newServerDefaultsTo55555AndPreservesExplicit22()
    {
        SupportServer defaultPort = server(null);
        service.insertSupportServer(defaultPort);
        assertEquals(55555, defaultPort.getSshPort());
        SupportServer explicitPort = server(22);
        service.insertSupportServer(explicitPort);
        assertEquals(22, explicitPort.getSshPort());
    }

    @Test
    void updateWithoutPortPreservesStoredPort()
    {
        SupportServer original = server(2222);
        original.setServerId(9L);
        when(mapper.selectSupportServerByServerId(9L)).thenReturn(original);
        SupportServer update = server(null);
        update.setServerId(9L);
        service.updateSupportServer(update);
        assertEquals(2222, update.getSshPort());
        original.setSshPort(22);
        update.setSshPort(null);
        service.updateSupportServer(update);
        assertEquals(22, update.getSshPort());
    }

    @Test
    void downloadedTemplateCanBeParsedWithoutTreatingInstructionsAsServer() throws Exception
    {
        byte[] bytes = template();
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes)))
        {
            assertEquals(55555, workbook.getSheetAt(0).getRow(1).getCell(2).getNumericCellValue());
            assertEquals(1, workbook.getSheetAt(0).getLastRowNum());
            assertNotNull(workbook.getSheet("填写说明"));
        }
        var rows = service.parseImportFile(new MockMultipartFile("file", "template.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes));
        assertEquals(1, rows.size());
        assertEquals(55555, rows.get(0).getSshPort());
    }

    @Test
    void importBlankPortDefaultsButExplicitPortIsKept() throws Exception
    {
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(template()));
             var output = new ByteArrayOutputStream())
        {
            var row = workbook.getSheetAt(0).getRow(1);
            row.getCell(2).setBlank();
            workbook.write(output);
            assertEquals(55555, service.parseImportFile(new MockMultipartFile("file", "blank.xlsx", null,
                output.toByteArray())).get(0).getSshPort());
            output.reset();
            row.getCell(2).setCellValue(22);
            workbook.write(output);
            assertEquals(22, service.parseImportFile(new MockMultipartFile("file", "explicit.xlsx", null,
                output.toByteArray())).get(0).getSshPort());
        }
    }

    @Test
    void invalidPortStillRejectsInsteadOfUsingDefault()
    {
        assertThrows(ServiceException.class, () -> service.insertSupportServer(server(0)));
        assertThrows(ServiceException.class, () -> service.insertSupportServer(server(65536)));
        verifyNoInteractions(mapper);
    }
}
