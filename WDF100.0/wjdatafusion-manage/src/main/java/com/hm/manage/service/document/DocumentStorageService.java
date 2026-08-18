package com.hm.manage.service.document;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionLaunch;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.poifs.filesystem.DirectoryEntry;
import org.apache.poi.poifs.filesystem.Entry;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import com.hm.common.config.RuoYiConfig;
import com.hm.common.exception.ServiceException;
import com.hm.common.utils.StringUtils;
import com.hm.manage.config.DocumentManagementProperties;

@Component
public class DocumentStorageService
{
    private static final String DEFAULT_DOCUMENT_LANGUAGE = "zh-CN";
    private static final Set<String> SUPPORTED_FILE_TYPES = Set.of("doc", "docx", "xls", "xlsx");
    private static final Set<String> OPEN_XML_FILE_TYPES = Set.of("docx", "xlsx");
    private static final Set<String> ARCHIVE_FILE_TYPES = Set.of("zip", "rar");
    private static final byte[] OLE_SIGNATURE = new byte[] {
        (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1
    };
    private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final Pattern EXTERNAL_TARGET = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:.*");

    @Autowired
    private DocumentManagementProperties properties;

    public Path resolve(String storageKey)
    {
        if (StringUtils.isBlank(storageKey) || storageKey.indexOf('\0') >= 0)
        {
            throw new ServiceException("文档存储标识无效");
        }
        Path relative;
        try
        {
            relative = Path.of(storageKey);
        }
        catch (Exception exception)
        {
            throw new ServiceException("文档存储标识无效");
        }
        if (relative.isAbsolute())
        {
            throw new ServiceException("文档存储标识无效");
        }
        Path root = root();
        Path resolved = root.resolve(relative).normalize();
        if (!resolved.startsWith(root))
        {
            throw new ServiceException("文档存储标识越界");
        }
        return resolved;
    }

    public Path createTempFile() throws IOException
    {
        return createTempFile("doc-callback-");
    }

    public Path copyUploadToTemp(MultipartFile upload) throws IOException
    {
        return copyUploadToTemp(upload, properties.getMaxFileSize());
    }

    public Path copyUploadToTemp(MultipartFile upload, long maximumBytes) throws IOException
    {
        if (upload == null || upload.isEmpty())
        {
            throw new ServiceException("请选择需要上传的文件");
        }
        long effectiveMaximum = Math.min(properties.getMaxFileSize(), maximumBytes);
        if (effectiveMaximum <= 0L)
        {
            throw new ServiceException("当前账号未配置可用的单文件上传额度");
        }
        if (upload.getSize() > effectiveMaximum)
        {
            throw new ServiceException("文件大小超过" + readableMegabytes(effectiveMaximum) + "MB限制");
        }
        Path temporary = createTempFile("doc-upload-");
        try (InputStream input = upload.getInputStream(); OutputStream output = Files.newOutputStream(temporary))
        {
            byte[] buffer = new byte[16 * 1024];
            long total = 0L;
            int length;
            while ((length = input.read(buffer)) >= 0)
            {
                if (length == 0)
                {
                    continue;
                }
                total += length;
                if (total > effectiveMaximum)
                {
                    throw new ServiceException("文件大小超过" + readableMegabytes(effectiveMaximum) + "MB限制");
                }
                output.write(buffer, 0, length);
            }
            if (total == 0L)
            {
                throw new ServiceException("上传文件为空");
            }
            return temporary;
        }
        catch (IOException | RuntimeException exception)
        {
            deleteQuietly(temporary);
            throw exception;
        }
    }

    private Path createTempFile(String prefix) throws IOException
    {
        Path tempRoot = root().resolve(".tmp");
        Files.createDirectories(tempRoot);
        return Files.createTempFile(tempRoot, prefix, ".tmp");
    }

    public void createBlank(String storageKey, String fileType) throws IOException
    {
        Path target = resolve(storageKey);
        Files.createDirectories(target.getParent());
        if ("docx".equals(fileType))
        {
            try (XWPFDocument document = new XWPFDocument(); OutputStream output = Files.newOutputStream(target))
            {
                document.createStyles().setSpellingLanguage(DEFAULT_DOCUMENT_LANGUAGE);
                document.getStyles().setEastAsia(DEFAULT_DOCUMENT_LANGUAGE);
                document.createParagraph();
                document.write(output);
            }
            return;
        }
        if ("xlsx".equals(fileType))
        {
            try (XSSFWorkbook workbook = new XSSFWorkbook(); OutputStream output = Files.newOutputStream(target))
            {
                workbook.createSheet("Sheet1");
                workbook.write(output);
            }
            return;
        }
        throw new ServiceException("不支持的文档类型");
    }

    public void validateOfficeFile(Path file, String fileType) throws IOException
    {
        validateEditorOfficeFile(file, fileType);
    }

    /**
     * ONLYOFFICE internally edits legacy DOC/XLS files as OOXML and can therefore
     * return DOCX/XLSX bytes on the first save. Validate the real callback content
     * and report the format that must be persisted instead of trusting the old
     * filename extension.
     */
    public String validateEditorOfficeFile(Path file, String expectedFileType) throws IOException
    {
        String normalizedType = normalizeFileType(expectedFileType);
        byte[] signature = readSignatureAfterSizeValidation(file, "编辑器返回的文件");
        if (isZipSignature(signature) && "doc".equals(normalizedType))
        {
            validate(file, "docx", "编辑器返回的文件");
            return "docx";
        }
        if (isZipSignature(signature) && "xls".equals(normalizedType))
        {
            validate(file, "xlsx", "编辑器返回的文件");
            return "xlsx";
        }
        validate(file, normalizedType, "编辑器返回的文件");
        return normalizedType;
    }

    public UploadValidationResult validateUploadedOfficeFile(Path file, String fileType) throws IOException
    {
        return validate(file, fileType, "上传文件");
    }

    /**
     * Archives are transfer-only assets. Validate their actual container type
     * without extracting or trying to preview their contents.
     */
    public UploadValidationResult validateUploadedArchiveFile(Path file, String fileType) throws IOException
    {
        String normalizedType = StringUtils.trimToEmpty(fileType).toLowerCase(Locale.ROOT);
        if (!ARCHIVE_FILE_TYPES.contains(normalizedType))
        {
            throw new ServiceException("仅支持 ZIP 和 RAR 压缩包");
        }
        byte[] signature = readSignatureAfterSizeValidation(file, "上传文件");
        if ("rar".equals(normalizedType))
        {
            if (!isRarSignature(signature))
            {
                throw formatMismatch(normalizedType);
            }
            return new UploadValidationResult(List.of());
        }
        if (!isZipSignature(signature))
        {
            throw formatMismatch(normalizedType);
        }
        validateTransferZip(file);
        return new UploadValidationResult(List.of());
    }

    /**
     * PDFs are preview-only assets. Validate their real signature and parse the
     * complete page tree without rendering content before the file is persisted.
     */
    public UploadValidationResult validateUploadedPdfFile(Path file) throws IOException
    {
        byte[] signature = readSignatureAfterSizeValidation(file, "上传文件");
        if (!matchesSignature(signature, PDF_SIGNATURE))
        {
            throw formatMismatch("pdf");
        }
        List<String> warnings = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(file.toFile(), IOUtils.createTempFileOnlyStreamCache()))
        {
            if (document.isEncrypted())
            {
                throw new ServiceException("PDF 已加密或受密码保护，请解除密码后再上传");
            }
            int pageCount = document.getNumberOfPages();
            if (pageCount <= 0)
            {
                throw new ServiceException("PDF 不包含可预览页面");
            }
            if (pageCount > properties.getMaxPdfPages())
            {
                throw new ServiceException("PDF 页数超过" + properties.getMaxPdfPages() + "页限制");
            }
            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++)
            {
                PDRectangle mediaBox = document.getPage(pageIndex).getMediaBox();
                if (mediaBox == null || !Float.isFinite(mediaBox.getWidth()) || !Float.isFinite(mediaBox.getHeight())
                    || mediaBox.getWidth() <= 0F || mediaBox.getHeight() <= 0F)
                {
                    throw new ServiceException("PDF 第" + (pageIndex + 1) + "页的页面尺寸无效");
                }
            }
            if (document.getDocumentCatalog().getOpenAction() instanceof PDActionJavaScript
                || document.getDocumentCatalog().getOpenAction() instanceof PDActionLaunch)
            {
                throw new ServiceException("PDF 包含自动执行脚本或外部程序动作，已拒绝上传");
            }
            PDDocumentNameDictionary names = document.getDocumentCatalog().getNames();
            if (names != null && names.getJavaScript() != null)
            {
                throw new ServiceException("PDF 包含 JavaScript，出于内网安全考虑不允许上传");
            }
            if (names != null && names.getEmbeddedFiles() != null)
            {
                throw new ServiceException("PDF 包含嵌入附件，请移除附件后再上传");
            }
            if (document.getDocumentCatalog().getAcroForm() != null
                && !document.getDocumentCatalog().getAcroForm().getFields().isEmpty())
            {
                warnings.add("检测到 PDF 交互表单；在线预览为只读，填写内容不会保存到服务器");
            }
        }
        catch (InvalidPasswordException exception)
        {
            throw new ServiceException("PDF 已加密或受密码保护，请解除密码后再上传");
        }
        catch (ServiceException exception)
        {
            throw exception;
        }
        catch (Exception exception)
        {
            throw new ServiceException("PDF 文件结构损坏或无法安全解析")
                .setDetailMessage(exception.getMessage());
        }
        return new UploadValidationResult(List.copyOf(new LinkedHashSet<>(warnings)));
    }

    public void moveIntoStorage(Path temporaryFile, String storageKey) throws IOException
    {
        Path target = resolve(storageKey);
        Files.createDirectories(target.getParent());
        try
        {
            Files.move(temporaryFile, target, StandardCopyOption.ATOMIC_MOVE);
        }
        catch (AtomicMoveNotSupportedException exception)
        {
            Files.move(temporaryFile, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public Path copyIntoStorage(Path sourceFile, String storageKey) throws IOException
    {
        if (sourceFile == null || !Files.isRegularFile(sourceFile))
        {
            throw new ServiceException("源文档文件不存在，无法复制");
        }
        Path target = resolve(storageKey);
        Files.createDirectories(target.getParent());
        Files.copy(sourceFile, target);
        return target;
    }

    public String checksum(Path file) throws IOException
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(file))
            {
                byte[] buffer = new byte[16 * 1024];
                int length;
                while ((length = input.read(buffer)) >= 0)
                {
                    if (length > 0)
                    {
                        digest.update(buffer, 0, length);
                    }
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        }
        catch (IOException exception)
        {
            throw exception;
        }
        catch (Exception exception)
        {
            throw new IllegalStateException("无法计算文档摘要", exception);
        }
    }

    public void deleteQuietly(Path path)
    {
        if (path == null)
        {
            return;
        }
        try
        {
            Files.deleteIfExists(path);
        }
        catch (IOException ignored)
        {
        }
    }

    private UploadValidationResult validate(Path file, String fileType, String subject) throws IOException
    {
        String normalizedType = normalizeFileType(fileType);
        byte[] signature = readSignatureAfterSizeValidation(file, subject);
        List<String> warnings = new ArrayList<>();
        if (OPEN_XML_FILE_TYPES.contains(normalizedType))
        {
            if (matchesSignature(signature, OLE_SIGNATURE) && isEncryptedOle(file))
            {
                throw new ServiceException("文件已加密或受密码保护，请解除密码后再上传");
            }
            if (!isZipSignature(signature))
            {
                throw formatMismatch(normalizedType);
            }
            validateOpenXml(file, normalizedType, warnings);
        }
        else
        {
            if (!matchesSignature(signature, OLE_SIGNATURE))
            {
                throw formatMismatch(normalizedType);
            }
            validateLegacyOffice(file, normalizedType, warnings);
        }
        return new UploadValidationResult(List.copyOf(new LinkedHashSet<>(warnings)));
    }

    private String normalizeFileType(String fileType)
    {
        String normalizedType = StringUtils.trimToEmpty(fileType).toLowerCase(Locale.ROOT);
        if (!SUPPORTED_FILE_TYPES.contains(normalizedType))
        {
            throw new ServiceException("仅支持 DOC、DOCX、XLS 和 XLSX 文档");
        }
        return normalizedType;
    }

    private byte[] readSignatureAfterSizeValidation(Path file, String subject) throws IOException
    {
        ensureFileSize(file, subject);
        return readSignature(file);
    }

    private void ensureFileSize(Path file, String subject) throws IOException
    {
        if (!Files.isRegularFile(file))
        {
            throw new ServiceException(subject + "不存在");
        }
        long size = Files.size(file);
        if (size <= 0L)
        {
            throw new ServiceException(subject + "为空");
        }
        if (size > properties.getMaxFileSize())
        {
            throw new ServiceException(subject + "大小超过" + readableMegabytes(properties.getMaxFileSize()) + "MB限制");
        }
    }

    private byte[] readSignature(Path file) throws IOException
    {
        byte[] signature = new byte[8];
        try (InputStream input = Files.newInputStream(file))
        {
            int offset = 0;
            while (offset < signature.length)
            {
                int length = input.read(signature, offset, signature.length - offset);
                if (length < 0)
                {
                    break;
                }
                offset += length;
            }
        }
        return signature;
    }

    private boolean isZipSignature(byte[] signature)
    {
        return signature.length >= 4 && signature[0] == 0x50 && signature[1] == 0x4B
            && ((signature[2] == 0x03 && signature[3] == 0x04)
                || (signature[2] == 0x05 && signature[3] == 0x06)
                || (signature[2] == 0x07 && signature[3] == 0x08));
    }

    private boolean isRarSignature(byte[] signature)
    {
        byte[] prefix = new byte[] { 0x52, 0x61, 0x72, 0x21, 0x1A, 0x07 };
        if (!matchesSignature(signature, prefix) || signature.length < 7)
        {
            return false;
        }
        return signature[6] == 0x00
            || (signature[6] == 0x01 && signature.length >= 8 && signature[7] == 0x00);
    }

    private void validateTransferZip(Path file) throws IOException
    {
        Set<String> entryNames = new LinkedHashSet<>();
        try (ZipFile zipFile = new ZipFile(file.toFile()))
        {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            int entryCount = 0;
            while (entries.hasMoreElements())
            {
                ZipEntry entry = entries.nextElement();
                entryCount++;
                if (entryCount > properties.getMaxArchiveEntries())
                {
                    throw new ServiceException("压缩包内部条目超过" + properties.getMaxArchiveEntries() + "个，已拒绝上传");
                }
                String entryName = normalizeArchiveEntry(entry.getName());
                if (!entryNames.add(entryName))
                {
                    throw new ServiceException("压缩包内部存在重复条目：" + entryName);
                }
            }
        }
        catch (ZipException exception)
        {
            throw new ServiceException("ZIP 压缩结构已损坏或与扩展名不一致")
                .setDetailMessage(exception.getMessage());
        }
    }

    private boolean matchesSignature(byte[] actual, byte[] expected)
    {
        if (actual.length < expected.length)
        {
            return false;
        }
        for (int index = 0; index < expected.length; index++)
        {
            if (actual[index] != expected[index])
            {
                return false;
            }
        }
        return true;
    }

    private void validateOpenXml(Path file, String fileType, List<String> warnings) throws IOException
    {
        String requiredEntry = "docx".equals(fileType) ? "word/document.xml" : "xl/workbook.xml";
        Set<String> entryNames = new LinkedHashSet<>();
        try (ZipFile zipFile = new ZipFile(file.toFile()))
        {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            long expandedTotal = 0L;
            int entryCount = 0;
            while (entries.hasMoreElements())
            {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory())
                {
                    continue;
                }
                entryCount++;
                if (entryCount > properties.getMaxArchiveEntries())
                {
                    throw new ServiceException("文件内部条目过多，可能已损坏或包含异常压缩内容");
                }
                String entryName = normalizeArchiveEntry(entry.getName());
                if (!entryNames.add(entryName))
                {
                    throw new ServiceException("文件内部存在重复条目：" + entryName);
                }
                String lowerName = entryName.toLowerCase(Locale.ROOT);
                if (lowerName.endsWith("vbaproject.bin") || lowerName.contains("/_vba_project"))
                {
                    throw new ServiceException("文件包含宏代码，出于内网安全考虑不允许上传");
                }
                if (lowerName.contains("/embeddings/"))
                {
                    warnings.add("检测到嵌入对象，在线编辑后请确认对象内容与版式");
                }
                if (lowerName.startsWith("xl/externallinks/"))
                {
                    warnings.add("检测到 Excel 外部数据链接，上传后请确认引用源在内网可用");
                }
                expandedTotal = countExpandedBytes(zipFile, entry, expandedTotal);
            }
            if (!entryNames.contains("[Content_Types].xml") || !entryNames.contains(requiredEntry))
            {
                throw new ServiceException("文件缺少必要的 Office 内容，格式与 ." + fileType.toUpperCase(Locale.ROOT) + " 不一致");
            }
            validateRelationships(zipFile, entryNames, warnings);
        }
        catch (ZipException exception)
        {
            throw new ServiceException("文件压缩结构已损坏，无法作为有效的 "
                + fileType.toUpperCase(Locale.ROOT) + " 文档读取").setDetailMessage(exception.getMessage());
        }

        try (InputStream input = Files.newInputStream(file))
        {
            if ("docx".equals(fileType))
            {
                try (XWPFDocument document = new XWPFDocument(input))
                {
                    document.getDocument();
                }
            }
            else
            {
                try (XSSFWorkbook workbook = new XSSFWorkbook(input))
                {
                    workbook.getNumberOfSheets();
                }
            }
        }
        catch (EncryptedDocumentException exception)
        {
            throw new ServiceException("文件已加密或受密码保护，请解除密码后再上传");
        }
        catch (ServiceException exception)
        {
            throw exception;
        }
        catch (Exception exception)
        {
            throw new ServiceException("文件内容损坏，或实际格式与 ."
                + fileType.toUpperCase(Locale.ROOT) + " 扩展名不一致").setDetailMessage(exception.getMessage());
        }
    }

    private long countExpandedBytes(ZipFile zipFile, ZipEntry entry, long expandedTotal) throws IOException
    {
        long maximum = properties.getMaxExpandedFileSize();
        if (entry.getSize() > maximum || (entry.getSize() >= 0L && expandedTotal > maximum - entry.getSize()))
        {
            throw new ServiceException("文件解压后的内容超过" + readableMegabytes(maximum) + "MB限制");
        }
        try (InputStream input = zipFile.getInputStream(entry))
        {
            byte[] buffer = new byte[16 * 1024];
            int length;
            while ((length = input.read(buffer)) >= 0)
            {
                if (length == 0)
                {
                    continue;
                }
                if (expandedTotal > maximum - length)
                {
                    throw new ServiceException("文件解压后的内容超过" + readableMegabytes(maximum) + "MB限制");
                }
                expandedTotal += length;
            }
        }
        return expandedTotal;
    }

    private String normalizeArchiveEntry(String entryName)
    {
        String normalized = StringUtils.defaultString(entryName).replace('\\', '/');
        if (StringUtils.isBlank(normalized) || normalized.startsWith("/") || normalized.indexOf('\0') >= 0)
        {
            throw new ServiceException("文件内部包含无效路径");
        }
        for (String segment : normalized.split("/"))
        {
            if (".".equals(segment) || "..".equals(segment))
            {
                throw new ServiceException("文件内部包含越界路径，已拒绝上传");
            }
        }
        Path path;
        try
        {
            path = Path.of(normalized).normalize();
        }
        catch (Exception exception)
        {
            throw new ServiceException("文件内部包含无效路径");
        }
        String result = path.toString().replace('\\', '/');
        if (result.equals("..") || result.startsWith("../"))
        {
            throw new ServiceException("文件内部包含越界路径，已拒绝上传");
        }
        return result;
    }

    private void validateRelationships(ZipFile zipFile, Set<String> entryNames, List<String> warnings)
    {
        int externalCount = 0;
        for (String relationshipName : entryNames)
        {
            if (!relationshipName.endsWith(".rels"))
            {
                continue;
            }
            ZipEntry relationshipEntry = zipFile.getEntry(relationshipName);
            try (InputStream input = zipFile.getInputStream(relationshipEntry))
            {
                DocumentBuilderFactory factory = secureDocumentBuilderFactory();
                NodeList relationships = factory.newDocumentBuilder().parse(input)
                    .getElementsByTagNameNS("*", "Relationship");
                for (int index = 0; index < relationships.getLength(); index++)
                {
                    Element relationship = (Element) relationships.item(index);
                    String target = relationship.getAttribute("Target");
                    if (StringUtils.isBlank(target))
                    {
                        throw new ServiceException("文件内部存在空引用：" + relationshipName);
                    }
                    boolean external = "External".equalsIgnoreCase(relationship.getAttribute("TargetMode"))
                        || EXTERNAL_TARGET.matcher(target).matches();
                    if (external)
                    {
                        externalCount++;
                        continue;
                    }
                    String resolvedTarget = resolveRelationshipTarget(relationshipName, target);
                    if (StringUtils.isNotBlank(resolvedTarget) && !entryNames.contains(resolvedTarget))
                    {
                        throw new ServiceException("文件内部引用缺失：" + resolvedTarget);
                    }
                }
            }
            catch (ServiceException exception)
            {
                throw exception;
            }
            catch (Exception exception)
            {
                throw new ServiceException("文件内部引用关系损坏：" + relationshipName)
                    .setDetailMessage(exception.getMessage());
            }
        }
        if (externalCount > 0)
        {
            warnings.add("检测到" + externalCount + "个外部引用，上传后请确认引用地址在内网可访问");
        }
    }

    private DocumentBuilderFactory secureDocumentBuilderFactory() throws Exception
    {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory;
    }

    private String resolveRelationshipTarget(String relationshipName, String rawTarget)
    {
        String target = rawTarget.replace('\\', '/');
        int fragment = target.indexOf('#');
        if (fragment >= 0)
        {
            target = target.substring(0, fragment);
        }
        int query = target.indexOf('?');
        if (query >= 0)
        {
            target = target.substring(0, query);
        }
        if (StringUtils.isBlank(target))
        {
            return "";
        }
        String base = "";
        int relationshipMarker = relationshipName.lastIndexOf("/_rels/");
        if (relationshipMarker >= 0)
        {
            String sourcePart = relationshipName.substring(0, relationshipMarker + 1)
                + relationshipName.substring(relationshipMarker + "/_rels/".length(), relationshipName.length() - 5);
            int separator = sourcePart.lastIndexOf('/');
            base = separator < 0 ? "" : sourcePart.substring(0, separator + 1);
        }
        String combined = target.startsWith("/") ? target.substring(1) : base + target;
        return normalizeRelationshipTarget(combined);
    }

    /**
     * OOXML relationship targets are URI references relative to the source part. Parent segments are therefore
     * legitimate as long as the normalized target remains inside the package root. ZIP entry names use the stricter
     * {@link #normalizeArchiveEntry(String)} validation and must never contain parent segments themselves.
     */
    private String normalizeRelationshipTarget(String target)
    {
        String normalized = StringUtils.defaultString(target).replace('\\', '/');
        if (StringUtils.isBlank(normalized) || normalized.startsWith("/") || normalized.indexOf('\0') >= 0)
        {
            throw new ServiceException("文件内部包含无效路径");
        }
        Path path;
        try
        {
            path = Path.of(normalized).normalize();
        }
        catch (Exception exception)
        {
            throw new ServiceException("文件内部包含无效路径");
        }
        String result = path.toString().replace('\\', '/');
        if (path.isAbsolute() || result.equals("..") || result.startsWith("../"))
        {
            throw new ServiceException("文件内部包含越界路径，已拒绝上传");
        }
        return result;
    }

    private void validateLegacyOffice(Path file, String fileType, List<String> warnings) throws IOException
    {
        try (POIFSFileSystem fileSystem = new POIFSFileSystem(file.toFile(), true))
        {
            DirectoryEntry root = fileSystem.getRoot();
            if (root.hasEntry("EncryptedPackage") || root.hasEntry("EncryptionInfo"))
            {
                throw new ServiceException("文件已加密或受密码保护，请解除密码后再上传");
            }
            Set<String> compoundNames = new LinkedHashSet<>();
            collectCompoundEntryNames(root, compoundNames);
            if (compoundNames.stream().anyMatch(this::isMacroEntry))
            {
                throw new ServiceException("文件包含宏代码，出于内网安全考虑不允许上传");
            }
            if (compoundNames.stream().anyMatch(name -> name.contains("objectpool") || name.contains("mso_")))
            {
                warnings.add("检测到嵌入对象，在线编辑后请确认对象内容与版式");
            }
            if ("doc".equals(fileType))
            {
                if (!root.hasEntry("WordDocument") || (!root.hasEntry("0Table") && !root.hasEntry("1Table")))
                {
                    throw formatMismatch(fileType);
                }
            }
            else if (!root.hasEntry("Workbook") && !root.hasEntry("Book"))
            {
                throw formatMismatch(fileType);
            }
        }
        catch (EncryptedDocumentException exception)
        {
            throw new ServiceException("文件已加密或受密码保护，请解除密码后再上传");
        }
        catch (ServiceException exception)
        {
            throw exception;
        }
        catch (Exception exception)
        {
            throw new ServiceException("文件内容损坏，或实际格式与 ."
                + fileType.toUpperCase(Locale.ROOT) + " 扩展名不一致").setDetailMessage(exception.getMessage());
        }

        try (InputStream input = Files.newInputStream(file))
        {
            if ("doc".equals(fileType))
            {
                try (HWPFDocument document = new HWPFDocument(input))
                {
                    document.getRange().numParagraphs();
                }
            }
            else
            {
                try (HSSFWorkbook workbook = new HSSFWorkbook(input))
                {
                    workbook.getNumberOfSheets();
                }
            }
        }
        catch (EncryptedDocumentException exception)
        {
            throw new ServiceException("文件已加密或受密码保护，请解除密码后再上传");
        }
        catch (Exception exception)
        {
            throw new ServiceException("文件内容损坏，或实际格式与 ."
                + fileType.toUpperCase(Locale.ROOT) + " 扩展名不一致").setDetailMessage(exception.getMessage());
        }
        warnings.add("旧版 " + fileType.toUpperCase(Locale.ROOT)
            + " 将由在线编辑器进行兼容转换，请确认字体、版式和外部引用");
    }

    private void collectCompoundEntryNames(DirectoryEntry directory, Set<String> names)
    {
        for (Entry entry : directory)
        {
            names.add(entry.getName().toLowerCase(Locale.ROOT));
            if (entry.isDirectoryEntry())
            {
                collectCompoundEntryNames((DirectoryEntry) entry, names);
            }
        }
    }

    private boolean isMacroEntry(String name)
    {
        return name.contains("_vba_project") || name.equals("vba") || name.contains("macros");
    }

    private boolean isEncryptedOle(Path file)
    {
        try (POIFSFileSystem fileSystem = new POIFSFileSystem(file.toFile(), true))
        {
            DirectoryEntry root = fileSystem.getRoot();
            return root.hasEntry("EncryptedPackage") || root.hasEntry("EncryptionInfo");
        }
        catch (Exception ignored)
        {
            return false;
        }
    }

    private ServiceException formatMismatch(String fileType)
    {
        return new ServiceException("文件实际内容与 ." + fileType.toUpperCase(Locale.ROOT)
            + " 扩展名不一致，请选择真实格式正确的文件");
    }

    private long readableMegabytes(long bytes)
    {
        return Math.max(1L, bytes / (1024L * 1024L));
    }

    private Path root()
    {
        String configured = properties.getStorageRoot();
        String value = StringUtils.isBlank(configured)
            ? Path.of(RuoYiConfig.getProfile(), "documents").toString()
            : configured;
        Path root = Path.of(value).toAbsolutePath().normalize();
        try
        {
            Files.createDirectories(root);
        }
        catch (IOException exception)
        {
            throw new ServiceException("无法创建文档存储目录");
        }
        return root;
    }

    public record UploadValidationResult(List<String> warnings) { }
}
