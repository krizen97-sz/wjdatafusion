package com.hm.manage.service.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import com.hm.common.exception.ServiceException;
import com.hm.manage.config.DocumentManagementProperties;

class DocumentStorageServiceTest
{
    @TempDir
    Path tempDirectory;

    private DocumentStorageService service;

    @BeforeEach
    void setUp()
    {
        DocumentManagementProperties properties = new DocumentManagementProperties();
        properties.setStorageRoot(tempDirectory.toString());
        service = new DocumentStorageService();
        ReflectionTestUtils.setField(service, "properties", properties);
    }

    @Test
    void shouldCreateAndValidateBlankOfficeFiles() throws Exception
    {
        service.createBlank("documents/1/v1.docx", "docx");
        service.createBlank("documents/2/v1.xlsx", "xlsx");

        Path word = service.resolve("documents/1/v1.docx");
        Path sheet = service.resolve("documents/2/v1.xlsx");
        service.validateOfficeFile(word, "docx");
        service.validateOfficeFile(sheet, "xlsx");

        assertTrue(Files.size(word) > 0);
        assertEquals(64, service.checksum(sheet).length());
        try (ZipFile archive = new ZipFile(word.toFile()))
        {
            String styles = new String(archive.getInputStream(archive.getEntry("word/styles.xml")).readAllBytes(),
                StandardCharsets.UTF_8);
            assertTrue(styles.contains("w:val=\"zh-CN\""));
            assertTrue(styles.contains("w:eastAsia=\"zh-CN\""));
        }
    }

    @Test
    void shouldRejectStorageTraversal()
    {
        assertThrows(ServiceException.class, () -> service.resolve("../../outside.docx"));
        assertThrows(ServiceException.class, () -> service.resolve(tempDirectory.resolve("absolute.docx").toString()));
    }

    @Test
    void shouldRejectAFileWhoseExtensionDoesNotMatchItsRealContent() throws Exception
    {
        Path fakeDocument = Files.writeString(tempDirectory.resolve("fake.docx"), "not an office document");

        ServiceException exception = assertThrows(ServiceException.class,
            () -> service.validateUploadedOfficeFile(fakeDocument, "docx"));

        assertTrue(exception.getMessage().contains("实际内容"));
    }

    @Test
    void shouldRejectMacrosBeforePersistingAnOpenXmlUpload() throws Exception
    {
        Path macroDocument = tempDirectory.resolve("macro.docx");
        writeZip(macroDocument, List.of(
            entry("[Content_Types].xml", "<Types/>"),
            entry("word/document.xml", "<w:document xmlns:w=\"urn:test\"/>"),
            entry("word/vbaProject.bin", "macro")));

        ServiceException exception = assertThrows(ServiceException.class,
            () -> service.validateUploadedOfficeFile(macroDocument, "docx"));

        assertTrue(exception.getMessage().contains("宏代码"));
    }

    @Test
    void shouldReportTheMissingInternalRelationshipTarget() throws Exception
    {
        Path brokenDocument = tempDirectory.resolve("broken-reference.docx");
        String relationship = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="urn:test" Target="media/missing.png"/>
            </Relationships>
            """;
        writeZip(brokenDocument, List.of(
            entry("[Content_Types].xml", "<Types/>"),
            entry("word/document.xml", "<w:document xmlns:w=\"urn:test\"/>"),
            entry("word/_rels/document.xml.rels", relationship)));

        ServiceException exception = assertThrows(ServiceException.class,
            () -> service.validateUploadedOfficeFile(brokenDocument, "docx"));

        assertTrue(exception.getMessage().contains("文件内部引用缺失"));
        assertTrue(exception.getMessage().contains("word/media/missing.png"));
    }

    @Test
    void shouldAllowAParentSegmentThatStaysInsideTheOpenXmlPackage() throws Exception
    {
        service.createBlank("relative-source.docx", "docx");
        Path document = tempDirectory.resolve("relative-reference.docx");
        copyWordDocumentWithRelationship(service.resolve("relative-source.docx"), document, "../docProps/core.xml");

        DocumentStorageService.UploadValidationResult result =
            service.validateUploadedOfficeFile(document, "docx");

        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void shouldRejectARelationshipThatEscapesTheOpenXmlPackage() throws Exception
    {
        service.createBlank("unsafe-reference-source.docx", "docx");
        Path document = tempDirectory.resolve("unsafe-reference.docx");
        copyWordDocumentWithRelationship(service.resolve("unsafe-reference-source.docx"), document,
            "../../../outside.xml");

        ServiceException exception = assertThrows(ServiceException.class,
            () -> service.validateUploadedOfficeFile(document, "docx"));

        assertTrue(exception.getMessage().contains("越界路径"));
    }

    @Test
    void shouldValidateLegacyExcelAndReturnCompatibilityWarning() throws Exception
    {
        Path legacyWorkbook = tempDirectory.resolve("legacy.xls");
        try (HSSFWorkbook workbook = new HSSFWorkbook(); var output = Files.newOutputStream(legacyWorkbook))
        {
            workbook.createSheet("数据").createRow(0).createCell(0).setCellValue("测试");
            workbook.write(output);
        }

        DocumentStorageService.UploadValidationResult result =
            service.validateUploadedOfficeFile(legacyWorkbook, "xls");

        assertTrue(result.warnings().stream().anyMatch(item -> item.contains("旧版 XLS")));
    }

    @Test
    void shouldRecognizeOnlyOfficeOoxmlCallbacksForLegacyDocuments() throws Exception
    {
        service.createBlank("documents/11/converted.docx", "docx");
        service.createBlank("documents/12/converted.xlsx", "xlsx");

        assertEquals("docx", service.validateEditorOfficeFile(
            service.resolve("documents/11/converted.docx"), "doc"));
        assertEquals("xlsx", service.validateEditorOfficeFile(
            service.resolve("documents/12/converted.xlsx"), "xls"));
    }

    @Test
    void shouldValidateZipAndRarAsTransferOnlyArchives() throws Exception
    {
        Path zip = tempDirectory.resolve("materials.zip");
        writeZip(zip, List.of(entry("目录/说明.txt", "archive")));
        Path rar4 = Files.write(tempDirectory.resolve("materials.rar"), new byte[] {
            0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00, 0x01
        });

        assertTrue(service.validateUploadedArchiveFile(zip, "zip").warnings().isEmpty());
        assertTrue(service.validateUploadedArchiveFile(rar4, "rar").warnings().isEmpty());
    }

    @Test
    void shouldParseARealPdfBeforeAllowingPreviewUpload() throws Exception
    {
        Path pdf = tempDirectory.resolve("preview.pdf");
        try (PDDocument document = new PDDocument())
        {
            document.addPage(new PDPage());
            document.save(pdf.toFile());
        }

        DocumentStorageService.UploadValidationResult result = service.validateUploadedPdfFile(pdf);

        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void shouldRejectDisguisedOrStructurallyBrokenPdfFiles() throws Exception
    {
        Path disguised = Files.writeString(tempDirectory.resolve("disguised.pdf"), "not-a-pdf");
        Path broken = Files.writeString(tempDirectory.resolve("broken.pdf"), "%PDF-1.7\nnot-a-real-document");

        ServiceException disguisedError = assertThrows(ServiceException.class,
            () -> service.validateUploadedPdfFile(disguised));
        ServiceException brokenError = assertThrows(ServiceException.class,
            () -> service.validateUploadedPdfFile(broken));

        assertTrue(disguisedError.getMessage().contains("实际内容"));
        assertTrue(brokenError.getMessage().contains("结构损坏"));
    }

    @Test
    void shouldRejectDisguisedOrUnsafeTransferArchives() throws Exception
    {
        Path fakeZip = Files.writeString(tempDirectory.resolve("fake.zip"), "not-a-zip");
        Path unsafeZip = tempDirectory.resolve("unsafe.zip");
        writeZip(unsafeZip, List.of(entry("../outside.txt", "unsafe")));

        assertThrows(ServiceException.class, () -> service.validateUploadedArchiveFile(fakeZip, "zip"));
        ServiceException unsafe = assertThrows(ServiceException.class,
            () -> service.validateUploadedArchiveFile(unsafeZip, "zip"));
        assertTrue(unsafe.getMessage().contains("越界路径"));
    }

    @Test
    void shouldEnforceThePerUserLimitWhileStreamingAnUpload() throws Exception
    {
        MockMultipartFile upload = new MockMultipartFile("file", "payload.zip", "application/zip",
            "123456".getBytes(StandardCharsets.UTF_8));

        ServiceException exception = assertThrows(ServiceException.class,
            () -> service.copyUploadToTemp(upload, 5L));

        assertTrue(exception.getMessage().contains("限制"));
    }

    private static ZipContent entry(String name, String value)
    {
        return new ZipContent(name, value.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeZip(Path target, List<ZipContent> entries) throws Exception
    {
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(target)))
        {
            for (ZipContent content : entries)
            {
                output.putNextEntry(new ZipEntry(content.name()));
                output.write(content.value());
                output.closeEntry();
            }
        }
    }

    private static void copyWordDocumentWithRelationship(Path source, Path target, String relationshipTarget)
        throws Exception
    {
        String relationshipEntry = "word/_rels/document.xml.rels";
        try (ZipFile archive = new ZipFile(source.toFile());
            ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(target)))
        {
            Enumeration<? extends ZipEntry> entries = archive.entries();
            while (entries.hasMoreElements())
            {
                ZipEntry sourceEntry = entries.nextElement();
                output.putNextEntry(new ZipEntry(sourceEntry.getName()));
                byte[] content = archive.getInputStream(sourceEntry).readAllBytes();
                if (relationshipEntry.equals(sourceEntry.getName()))
                {
                    String xml = new String(content, StandardCharsets.UTF_8);
                    String injected = "<Relationship Id=\"rIdCodex\" Type=\"urn:codex:test\" Target=\""
                        + relationshipTarget + "\"/>";
                    content = xml.replace("</Relationships>", injected + "</Relationships>")
                        .getBytes(StandardCharsets.UTF_8);
                }
                output.write(content);
                output.closeEntry();
            }
        }
    }

    private record ZipContent(String name, byte[] value) { }

}
