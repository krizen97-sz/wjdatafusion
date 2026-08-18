package com.hm.manage.service.impl;

import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.hm.common.exception.ServiceException;
import com.hm.common.utils.DateUtils;
import com.hm.common.utils.StringUtils;
import com.hm.common.utils.file.FileUtils;
import com.hm.manage.domain.SupportServer;
import com.hm.manage.domain.SupportServerCredential;
import com.hm.manage.mapper.SupportPlatformServerRelMapper;
import com.hm.manage.mapper.SupportServerCredentialMapper;
import com.hm.manage.mapper.SupportServerMapper;
import com.hm.manage.service.ISupportChangeLogService;
import com.hm.manage.service.ISupportServerService;
import com.hm.manage.service.support.CredentialCryptoService;

@Service
public class SupportServerServiceImpl implements ISupportServerService
{
    private static final int DEFAULT_SSH_PORT = 22;
    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 65535;
    private static final String IMPORT_SHEET_NAME = "服务器导入模板";
    private static final String[] IMPORT_HEADERS = {"服务器名称", "服务器IP", "SSH端口", "操作系统", "系统账号", "系统密码", "运行状态"};
    private static final String SERVER_LOGIN_HIK = "hik";
    private static final String SERVER_LOGIN_ROOT = "root";
    private static final String SERVER_LOGIN_OTHER_NAME = "其他账号";
    private static final String PASSWORD_MASK = "******";

    @Autowired
    private SupportServerMapper serverMapper;

    @Autowired
    private SupportPlatformServerRelMapper platformServerRelMapper;

    @Autowired
    private SupportServerCredentialMapper credentialMapper;

    @Autowired
    private CredentialCryptoService cryptoService;

    @Autowired
    private ISupportChangeLogService changeLogService;

    @Override
    public SupportServer selectSupportServerByServerId(Long serverId)
    {
        SupportServer server = serverMapper.selectSupportServerByServerId(serverId);
        maskPassword(server);
        attachFixedLoginCredentialStatus(server);
        return server;
    }

    @Override
    public List<SupportServer> selectSupportServerList(SupportServer server)
    {
        List<SupportServer> list = serverMapper.selectSupportServerList(server);
        for (SupportServer item : list)
        {
            maskPassword(item);
            attachFixedLoginCredentialStatus(item);
        }
        return list;
    }

    @Override
    @Transactional
    public int insertSupportServer(SupportServer server)
    {
        validateAndNormalizeServer(server, false);
        encryptPassword(server);
        server.setCreateTime(DateUtils.getNowDate());
        int rows = serverMapper.insertSupportServer(server);
        if (rows > 0)
        {
            syncFixedLoginCredentials(server);
            changeLogService.record(server.getSiteId(), "INSERT", "SERVER", server.getServerId(), server.getServerName(), "新增服务器 " + server.getServerName(), null, server);
        }
        return rows;
    }

    @Override
    @Transactional
    public int updateSupportServer(SupportServer server)
    {
        SupportServer original = serverMapper.selectSupportServerByServerId(server.getServerId());
        if (original == null)
        {
            throw new ServiceException("服务器不存在");
        }
        validateAndNormalizeServer(server, true);
        encryptPassword(server);
        server.setUpdateTime(DateUtils.getNowDate());
        int rows = serverMapper.updateSupportServer(server);
        if (rows > 0)
        {
            syncFixedLoginCredentials(server);
            changeLogService.record(server.getSiteId(), "UPDATE", "SERVER", server.getServerId(), server.getServerName(), "修改服务器 " + server.getServerName(), original, server);
        }
        return rows;
    }

    @Override
    public int deleteSupportServerByServerIds(Long[] serverIds)
    {
        List<SupportServer> deletedServers = new ArrayList<>();
        for (Long serverId : serverIds)
        {
            SupportServer server = serverMapper.selectSupportServerByServerId(serverId);
            if (server != null)
            {
                deletedServers.add(server);
            }
            platformServerRelMapper.deleteByServerId(serverId);
            credentialMapper.deleteCredentialsByServerId(serverId);
        }
        int rows = serverMapper.deleteSupportServerByServerIds(serverIds);
        if (rows > 0)
        {
            for (SupportServer server : deletedServers)
            {
                changeLogService.record(server.getSiteId(), "DELETE", "SERVER", server.getServerId(), server.getServerName(), "删除服务器 " + server.getServerName(), server, null);
            }
        }
        return rows;
    }

    @Override
    public String getServerPasswordPlain(Long serverId)
    {
        SupportServer server = serverMapper.selectSupportServerByServerId(serverId);
        if (server == null)
        {
            return StringUtils.EMPTY;
        }
        if (StringUtils.isBlank(server.getOsPasswordCipher()))
        {
            return StringUtils.EMPTY;
        }
        return cryptoService.decrypt(server.getOsPasswordCipher());
    }

    @Override
    public List<SupportServerCredential> selectServerCredentialList(Long serverId)
    {
        requireServer(serverId);
        List<SupportServerCredential> list = credentialMapper.selectCredentialsByServerId(serverId);
        for (SupportServerCredential credential : list)
        {
            maskCredentialPassword(credential);
        }
        return list;
    }

    @Override
    public int insertServerCredential(SupportServerCredential credential)
    {
        validateAndNormalizeCredential(credential, false);
        encryptCredentialPassword(credential);
        credential.setCreateTime(DateUtils.getNowDate());
        credential.setUpdateTime(DateUtils.getNowDate());
        if ("1".equals(credential.getIsDefault()))
        {
            credentialMapper.clearDefaultByServerId(credential.getServerId(), null);
        }
        int rows = credentialMapper.insertCredential(credential);
        if (rows > 0)
        {
            SupportServer server = requireServer(credential.getServerId());
            changeLogService.record(server.getSiteId(), "INSERT", "SERVER_CREDENTIAL", credential.getCredentialId(), credential.getCredentialName(),
                    "新增服务器凭据档案 " + credential.getCredentialName(), null, credential);
        }
        return rows;
    }

    @Override
    public int updateServerCredential(SupportServerCredential credential)
    {
        SupportServerCredential original = credentialMapper.selectCredentialById(credential.getCredentialId());
        if (original == null)
        {
            throw new ServiceException("服务器凭据不存在");
        }
        credential.setServerId(original.getServerId());
        validateAndNormalizeCredential(credential, true);
        encryptCredentialPassword(credential);
        if (StringUtils.isBlank(credential.getPasswordCipher()))
        {
            credential.setPasswordCipher(original.getPasswordCipher());
        }
        credential.setUpdateTime(DateUtils.getNowDate());
        if ("1".equals(credential.getIsDefault()))
        {
            credentialMapper.clearDefaultByServerId(credential.getServerId(), credential.getCredentialId());
        }
        int rows = credentialMapper.updateCredential(credential);
        if (rows > 0)
        {
            SupportServer server = requireServer(credential.getServerId());
            changeLogService.record(server.getSiteId(), "UPDATE", "SERVER_CREDENTIAL", credential.getCredentialId(), credential.getCredentialName(),
                    "修改服务器凭据档案 " + credential.getCredentialName(), original, credential);
        }
        return rows;
    }

    @Override
    public int deleteServerCredentialById(Long credentialId)
    {
        SupportServerCredential credential = credentialMapper.selectCredentialById(credentialId);
        if (credential == null)
        {
            return 0;
        }
        int rows = credentialMapper.deleteCredentialById(credentialId);
        if (rows > 0)
        {
            SupportServer server = requireServer(credential.getServerId());
            changeLogService.record(server.getSiteId(), "DELETE", "SERVER_CREDENTIAL", credentialId, credential.getCredentialName(),
                    "删除服务器凭据档案 " + credential.getCredentialName(), credential, null);
        }
        return rows;
    }

    @Override
    public String getServerCredentialPasswordPlain(Long credentialId)
    {
        SupportServerCredential credential = credentialMapper.selectCredentialById(credentialId);
        if (credential == null)
        {
            return StringUtils.EMPTY;
        }
        if (StringUtils.isBlank(credential.getPasswordCipher()))
        {
            return StringUtils.EMPTY;
        }
        return cryptoService.decrypt(credential.getPasswordCipher());
    }

    @Override
    public List<Map<String, Object>> selectServerCredentialPlainSummaries(Long[] serverIds)
    {
        List<Map<String, Object>> result = new ArrayList<>();
        if (serverIds == null || serverIds.length == 0)
        {
            return result;
        }
        HashSet<Long> seen = new HashSet<>();
        for (Long serverId : serverIds)
        {
            if (serverId == null || !seen.add(serverId))
            {
                continue;
            }
            SupportServer server = serverMapper.selectSupportServerByServerId(serverId);
            if (server != null)
            {
                result.add(buildServerCredentialPlainSummary(server));
            }
        }
        return result;
    }

    @Override
    public void exportImportTemplate(HttpServletResponse response) throws Exception
    {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        FileUtils.setAttachmentResponseHeader(response, "服务器导入模板.xlsx");
        try (Workbook workbook = new XSSFWorkbook())
        {
            Sheet sheet = workbook.createSheet(IMPORT_SHEET_NAME);
            CellStyle headerStyle = buildImportHeaderStyle(workbook);
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < IMPORT_HEADERS.length; i++)
            {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(IMPORT_HEADERS[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, Math.max(IMPORT_HEADERS[i].length() * 900, 4200));
            }

            Row exampleRow = sheet.createRow(1);
            exampleRow.createCell(0).setCellValue("应用服务器A");
            exampleRow.createCell(1).setCellValue("10.10.10.21");
            exampleRow.createCell(2).setCellValue(22);
            exampleRow.createCell(3).setCellValue("CentOS");
            exampleRow.createCell(4).setCellValue("root");
            exampleRow.createCell(5).setCellValue("明文密码");
            exampleRow.createCell(6).setCellValue("正常");

            Row tipRow = sheet.createRow(2);
            tipRow.createCell(0).setCellValue("填写说明：请保持表头不变；SSH端口为空时默认22；运行状态可填写正常/停用或0/1；系统密码按明文读取。");
            workbook.write(response.getOutputStream());
        }
    }

    @Override
    public List<SupportServer> parseImportFile(MultipartFile file) throws Exception
    {
        if (file == null || file.isEmpty())
        {
            throw new ServiceException("请选择需要导入的服务器xlsx文件");
        }
        String filename = StringUtils.defaultString(file.getOriginalFilename());
        if (!StringUtils.endsWithIgnoreCase(filename, ".xlsx"))
        {
            throw new ServiceException("服务器批量导入仅支持xlsx格式，请先下载模板并按模板填写");
        }

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream()))
        {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null)
            {
                throw new ServiceException("导入文件没有可读取的工作表");
            }
            DataFormatter formatter = new DataFormatter();
            validateImportHeaders(sheet, formatter);
            List<SupportServer> servers = new ArrayList<>();
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++)
            {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isBlankRow(row, formatter))
                {
                    continue;
                }
                String serverAddress = readCell(row, 1, formatter);
                if (StringUtils.isBlank(serverAddress))
                {
                    throw new ServiceException("第" + (rowIndex + 1) + "行服务器IP不能为空");
                }
                SupportServer server = new SupportServer();
                server.setServerName(StringUtils.defaultIfBlank(readCell(row, 0, formatter), "服务器-" + serverAddress));
                server.setServerAddress(serverAddress);
                server.setSshPort(parseImportSshPort(readCell(row, 2, formatter), rowIndex + 1));
                server.setOsType(readCell(row, 3, formatter));
                server.setOsUsername(readCell(row, 4, formatter));
                server.setOsPassword(readCell(row, 5, formatter));
                applyFixedLoginPasswordFromImport(server);
                server.setStatus(parseImportStatus(readCell(row, 6, formatter)));
                servers.add(server);
            }
            return servers;
        }
    }

    private CellStyle buildImportHeaderStyle(Workbook workbook)
    {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setFont(font);
        return style;
    }

    private void validateImportHeaders(Sheet sheet, DataFormatter formatter)
    {
        Row headerRow = sheet.getRow(0);
        if (headerRow == null)
        {
            throw new ServiceException("导入文件缺少表头，请使用服务器导入模板");
        }
        for (int i = 0; i < IMPORT_HEADERS.length; i++)
        {
            String actual = readCell(headerRow, i, formatter);
            if (!IMPORT_HEADERS[i].equals(actual))
            {
                throw new ServiceException("导入文件表头不一致，请使用服务器导入模板。第" + (i + 1) + "列应为：" + IMPORT_HEADERS[i]);
            }
        }
    }

    private boolean isBlankRow(Row row, DataFormatter formatter)
    {
        for (int i = 0; i < IMPORT_HEADERS.length; i++)
        {
            if (StringUtils.isNotBlank(readCell(row, i, formatter)))
            {
                return false;
            }
        }
        return true;
    }

    private String readCell(Row row, int cellIndex, DataFormatter formatter)
    {
        if (row == null)
        {
            return StringUtils.EMPTY;
        }
        return StringUtils.trimToEmpty(formatter.formatCellValue(row.getCell(cellIndex)));
    }

    private Integer parseImportSshPort(String value, int rowNumber)
    {
        if (StringUtils.isBlank(value))
        {
            return DEFAULT_SSH_PORT;
        }
        try
        {
            int port = Integer.parseInt(value.replaceAll("\\.0$", ""));
            if (port < MIN_PORT || port > MAX_PORT)
            {
                throw new ServiceException("第" + rowNumber + "行SSH端口范围必须在1-65535之间");
            }
            return port;
        }
        catch (NumberFormatException e)
        {
            throw new ServiceException("第" + rowNumber + "行SSH端口必须是数字");
        }
    }

    private String parseImportStatus(String value)
    {
        String text = StringUtils.trimToEmpty(value);
        return "1".equals(text) || "停用".equals(text) || "禁用".equals(text) ? "1" : "0";
    }

    private void encryptPassword(SupportServer server)
    {
        if (StringUtils.isNotEmpty(server.getOsPassword()))
        {
            server.setOsPasswordCipher(cryptoService.encrypt(server.getOsPassword()));
        }
        server.setOsPassword(null);
    }

    private void applyFixedLoginPasswordFromImport(SupportServer server)
    {
        if (server == null || StringUtils.isBlank(server.getOsUsername()) || StringUtils.isBlank(server.getOsPassword()))
        {
            return;
        }
        String username = server.getOsUsername().trim();
        String normalizedUsername = normalizeFixedLoginUsername(username);
        if (SERVER_LOGIN_HIK.equals(normalizedUsername))
        {
            server.setHikPassword(server.getOsPassword());
        }
        else if (SERVER_LOGIN_ROOT.equals(normalizedUsername))
        {
            server.setRootPassword(server.getOsPassword());
        }
        else
        {
            server.setOtherUsername(username);
            server.setOtherPassword(server.getOsPassword());
        }
    }

    private Map<String, Object> buildServerCredentialPlainSummary(SupportServer server)
    {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("serverId", server.getServerId());
        summary.put("hikPassword", StringUtils.EMPTY);
        summary.put("rootPassword", StringUtils.EMPTY);
        summary.put("otherUsername", StringUtils.EMPTY);
        summary.put("otherPassword", StringUtils.EMPTY);

        List<SupportServerCredential> credentials = credentialMapper.selectCredentialsByServerId(server.getServerId());
        for (SupportServerCredential credential : credentials)
        {
            if (StringUtils.isBlank(credential.getPasswordCipher()))
            {
                continue;
            }
            String plain = cryptoService.decrypt(credential.getPasswordCipher());
            String username = StringUtils.trimToEmpty(credential.getUsername());
            String normalizedUsername = normalizeFixedLoginUsername(username);
            if (SERVER_LOGIN_HIK.equals(normalizedUsername))
            {
                summary.put("hikPassword", plain);
            }
            else if (SERVER_LOGIN_ROOT.equals(normalizedUsername))
            {
                summary.put("rootPassword", plain);
            }
            else if (StringUtils.isBlank((String) summary.get("otherUsername")) || SERVER_LOGIN_OTHER_NAME.equals(credential.getCredentialName()))
            {
                summary.put("otherUsername", username);
                summary.put("otherPassword", plain);
            }
        }

        if (StringUtils.isNotBlank(server.getOsPasswordCipher()) && StringUtils.isNotBlank(server.getOsUsername()))
        {
            String legacyPlain = cryptoService.decrypt(server.getOsPasswordCipher());
            String legacyUsername = StringUtils.trimToEmpty(server.getOsUsername());
            String normalizedLegacyUsername = normalizeFixedLoginUsername(legacyUsername);
            if (SERVER_LOGIN_HIK.equals(normalizedLegacyUsername) && StringUtils.isBlank((String) summary.get("hikPassword")))
            {
                summary.put("hikPassword", legacyPlain);
            }
            else if (SERVER_LOGIN_ROOT.equals(normalizedLegacyUsername) && StringUtils.isBlank((String) summary.get("rootPassword")))
            {
                summary.put("rootPassword", legacyPlain);
            }
            else if (StringUtils.isBlank((String) summary.get("otherPassword")))
            {
                summary.put("otherUsername", legacyUsername);
                summary.put("otherPassword", legacyPlain);
            }
        }
        return summary;
    }

    private void attachFixedLoginCredentialStatus(SupportServer server)
    {
        if (server == null || server.getServerId() == null)
        {
            return;
        }
        SupportServerCredential hikCredential = selectCredentialByUsernameIgnoreCase(server.getServerId(), SERVER_LOGIN_HIK);
        SupportServerCredential rootCredential = selectCredentialByUsernameIgnoreCase(server.getServerId(), SERVER_LOGIN_ROOT);
        SupportServerCredential otherCredential = credentialMapper.selectCredentialByServerIdAndName(server.getServerId(), SERVER_LOGIN_OTHER_NAME);
        boolean hikConfigured = hikCredential != null && StringUtils.isNotBlank(hikCredential.getPasswordCipher());
        boolean rootConfigured = rootCredential != null && StringUtils.isNotBlank(rootCredential.getPasswordCipher());
        boolean otherConfigured = otherCredential != null && StringUtils.isNotBlank(otherCredential.getPasswordCipher());
        String otherUsername = otherCredential == null ? null : otherCredential.getUsername();

        if (StringUtils.isNotBlank(server.getOsPasswordCipher()) && StringUtils.isNotBlank(server.getOsUsername()))
        {
            String legacyUsername = server.getOsUsername().trim();
            if (SERVER_LOGIN_HIK.equalsIgnoreCase(legacyUsername))
            {
                hikConfigured = true;
            }
            else if (SERVER_LOGIN_ROOT.equalsIgnoreCase(legacyUsername))
            {
                rootConfigured = true;
            }
            else if (!otherConfigured)
            {
                otherConfigured = true;
                otherUsername = legacyUsername;
            }
        }

        server.setHikCredentialConfigured(hikConfigured);
        server.setRootCredentialConfigured(rootConfigured);
        server.setOtherCredentialConfigured(otherConfigured);
        server.setOtherUsername(otherUsername);
        server.setHikPassword(null);
        server.setRootPassword(null);
        server.setOtherPassword(null);
    }

    private void syncFixedLoginCredentials(SupportServer server)
    {
        if (server == null || server.getServerId() == null)
        {
            return;
        }
        upsertFixedLoginCredential(server, SERVER_LOGIN_HIK, server.getHikPassword(), "hik账号", "现场常用hik登录账号", "1");
        upsertFixedLoginCredential(server, SERVER_LOGIN_ROOT, server.getRootPassword(), "root账号", "现场常用root登录账号", "0");
        upsertOtherLoginCredential(server);
        server.setHikPassword(null);
        server.setRootPassword(null);
        server.setOtherPassword(null);
    }

    private void upsertFixedLoginCredential(SupportServer server, String username, String password, String credentialName, String purpose, String isDefault)
    {
        if (StringUtils.isBlank(password) || PASSWORD_MASK.equals(password))
        {
            return;
        }
        SupportServerCredential credential = selectCredentialByUsernameIgnoreCase(server.getServerId(), username);
        boolean insert = credential == null;
        if (insert)
        {
            credential = new SupportServerCredential();
            credential.setServerId(server.getServerId());
            credential.setCreateTime(DateUtils.getNowDate());
        }
        credential.setUsername(username);
        credential.setCredentialName(credentialName);
        credential.setPurpose(purpose);
        credential.setIsDefault(isDefault);
        credential.setStatus("0");
        credential.setPasswordCipher(cryptoService.encrypt(password));
        credential.setUpdateTime(DateUtils.getNowDate());
        if (insert)
        {
            credentialMapper.insertCredential(credential);
        }
        else
        {
            credentialMapper.updateCredential(credential);
        }
        changeLogService.record(server.getSiteId(), insert ? "INSERT" : "UPDATE", "SERVER_CREDENTIAL", credential.getCredentialId(), credentialName,
                "配置服务器" + username + "登录信息");
    }

    private void upsertOtherLoginCredential(SupportServer server)
    {
        String username = StringUtils.trimToEmpty(server.getOtherUsername());
        String password = server.getOtherPassword();
        if (StringUtils.isBlank(username) && StringUtils.isBlank(password))
        {
            return;
        }
        if (StringUtils.isBlank(username))
        {
            throw new ServiceException("请填写其他账号用户名");
        }
        if (isFixedLoginUsername(username))
        {
            throw new ServiceException("其他账号不能填写hik或root，请使用对应的固定账号栏");
        }
        SupportServerCredential credential = credentialMapper.selectCredentialByServerIdAndName(server.getServerId(), SERVER_LOGIN_OTHER_NAME);
        boolean insert = credential == null;
        if (insert && StringUtils.isBlank(password))
        {
            throw new ServiceException("请填写其他账号密码");
        }
        if (insert)
        {
            credential = new SupportServerCredential();
            credential.setServerId(server.getServerId());
            credential.setCreateTime(DateUtils.getNowDate());
        }
        credential.setCredentialName(SERVER_LOGIN_OTHER_NAME);
        credential.setUsername(username);
        credential.setPurpose("现场补充登录账号");
        credential.setIsDefault("0");
        credential.setStatus("0");
        if (StringUtils.isNotBlank(password) && !PASSWORD_MASK.equals(password))
        {
            credential.setPasswordCipher(cryptoService.encrypt(password));
        }
        credential.setUpdateTime(DateUtils.getNowDate());
        if (insert)
        {
            credentialMapper.insertCredential(credential);
        }
        else
        {
            credentialMapper.updateCredential(credential);
        }
        changeLogService.record(server.getSiteId(), insert ? "INSERT" : "UPDATE", "SERVER_CREDENTIAL", credential.getCredentialId(), SERVER_LOGIN_OTHER_NAME,
                "配置服务器其他账号登录信息");
    }

    private void validateAndNormalizeServer(SupportServer server, boolean update)
    {
        if (server == null)
        {
            throw new ServiceException("服务器数据不能为空");
        }
        if (update && server.getServerId() == null)
        {
            throw new ServiceException("服务器ID不能为空");
        }
        if (server.getSiteId() == null)
        {
            throw new ServiceException("现场ID不能为空");
        }
        if (StringUtils.isBlank(server.getServerName()))
        {
            throw new ServiceException("服务器名称不能为空");
        }
        if (StringUtils.isBlank(server.getServerAddress()))
        {
            throw new ServiceException("服务器地址不能为空");
        }
        server.setServerName(server.getServerName().trim());
        server.setServerAddress(server.getServerAddress().trim());
        if (server.getSshPort() == null)
        {
            server.setSshPort(DEFAULT_SSH_PORT);
        }
        if (server.getSshPort() < MIN_PORT || server.getSshPort() > MAX_PORT)
        {
            throw new ServiceException("SSH端口范围必须在1-65535之间");
        }
        server.setEquipmentRoom(StringUtils.trimToNull(server.getEquipmentRoom()));
        server.setCabinetNo(StringUtils.trimToNull(server.getCabinetNo()));
        validateRackRange(server.getRackUStart(), server.getRackUEnd());
        SupportServer sameAddressServer = serverMapper.selectSupportServerBySiteAndAddress(server.getSiteId(), server.getServerAddress());
        if (sameAddressServer != null && (!update || !sameAddressServer.getServerId().equals(server.getServerId())))
        {
            throw new ServiceException("当前现场已存在相同地址的服务器");
        }
    }

    private void validateRackRange(Integer rackUStart, Integer rackUEnd)
    {
        if (rackUStart == null && rackUEnd == null)
        {
            return;
        }
        if (rackUStart == null || rackUEnd == null)
        {
            throw new ServiceException("起始U位和结束U位需要同时填写");
        }
        if (rackUStart < 1 || rackUStart > 45 || rackUEnd < 1 || rackUEnd > 45)
        {
            throw new ServiceException("U位范围必须在1到45之间");
        }
        if (rackUStart > rackUEnd)
        {
            throw new ServiceException("起始U位不能大于结束U位");
        }
    }

    private SupportServer requireServer(Long serverId)
    {
        if (serverId == null)
        {
            throw new ServiceException("服务器ID不能为空");
        }
        SupportServer server = serverMapper.selectSupportServerByServerId(serverId);
        if (server == null)
        {
            throw new ServiceException("服务器不存在");
        }
        return server;
    }

    private void validateAndNormalizeCredential(SupportServerCredential credential, boolean update)
    {
        if (credential == null)
        {
            throw new ServiceException("服务器凭据不能为空");
        }
        if (update && credential.getCredentialId() == null)
        {
            throw new ServiceException("凭据ID不能为空");
        }
        requireServer(credential.getServerId());
        if (StringUtils.isBlank(credential.getCredentialName()))
        {
            throw new ServiceException("凭据名称不能为空");
        }
        if (StringUtils.isBlank(credential.getUsername()))
        {
            throw new ServiceException("登录账号不能为空");
        }
        if (!update && StringUtils.isBlank(credential.getPassword()))
        {
            throw new ServiceException("登录密码不能为空");
        }
        credential.setCredentialName(credential.getCredentialName().trim());
        credential.setUsername(isFixedLoginUsername(credential.getUsername()) ? normalizeFixedLoginUsername(credential.getUsername()) : credential.getUsername().trim());
        credential.setStatus("1".equals(credential.getStatus()) ? "1" : "0");
        credential.setIsDefault("1".equals(credential.getIsDefault()) ? "1" : "0");
    }

    private boolean isFixedLoginUsername(String username)
    {
        String normalized = normalizeFixedLoginUsername(username);
        return SERVER_LOGIN_HIK.equals(normalized) || SERVER_LOGIN_ROOT.equals(normalized);
    }

    private String normalizeFixedLoginUsername(String username)
    {
        return StringUtils.trimToEmpty(username).toLowerCase();
    }

    private SupportServerCredential selectCredentialByUsernameIgnoreCase(Long serverId, String username)
    {
        List<SupportServerCredential> credentials = credentialMapper.selectCredentialsByServerId(serverId);
        String normalizedUsername = normalizeFixedLoginUsername(username);
        for (SupportServerCredential credential : credentials)
        {
            if (normalizedUsername.equals(normalizeFixedLoginUsername(credential.getUsername())))
            {
                return credential;
            }
        }
        return null;
    }

    private void encryptCredentialPassword(SupportServerCredential credential)
    {
        if (StringUtils.isNotBlank(credential.getPassword()) && !"******".equals(credential.getPassword()))
        {
            credential.setPasswordCipher(cryptoService.encrypt(credential.getPassword()));
        }
        credential.setPassword(null);
    }

    private void maskCredentialPassword(SupportServerCredential credential)
    {
        if (credential != null && StringUtils.isNotBlank(credential.getPasswordCipher()))
        {
            credential.setPassword("******");
        }
    }

    private void maskPassword(SupportServer server)
    {
        if (server == null)
        {
            return;
        }
        if (StringUtils.isNotEmpty(server.getOsPasswordCipher()))
        {
            server.setOsPassword("******");
        }
    }
}
