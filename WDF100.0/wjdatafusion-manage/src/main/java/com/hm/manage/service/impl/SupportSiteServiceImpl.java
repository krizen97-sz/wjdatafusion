package com.hm.manage.service.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
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
import com.hm.common.core.domain.BaseEntity;
import com.hm.common.exception.ServiceException;
import com.hm.common.utils.DateUtils;
import com.hm.common.utils.SecurityUtils;
import com.hm.common.utils.StringUtils;
import com.hm.common.utils.file.FileUtils;
import com.hm.manage.domain.SupportOrg;
import com.hm.manage.domain.SupportPlatform;
import com.hm.manage.domain.SupportPlatformServerRel;
import com.hm.manage.domain.SupportServer;
import com.hm.manage.domain.SupportSite;
import com.hm.manage.domain.SupportContact;
import com.hm.manage.domain.SupportSiteMessage;
import com.hm.manage.domain.SupportSubplatformEndpoint;
import com.hm.manage.domain.vo.SupportSiteOverviewVo;
import com.hm.manage.mapper.SupportContactMapper;
import com.hm.manage.mapper.SupportOrgMapper;
import com.hm.manage.mapper.SupportPlatformContactRelMapper;
import com.hm.manage.mapper.SupportPlatformMapper;
import com.hm.manage.mapper.SupportPlatformServerRelMapper;
import com.hm.manage.mapper.SupportServerMapper;
import com.hm.manage.mapper.SupportSiteMapper;
import com.hm.manage.mapper.SupportSiteMessageMapper;
import com.hm.manage.mapper.SupportSubplatformEndpointMapper;
import com.hm.manage.service.ISupportChangeLogService;
import com.hm.manage.service.ISupportSiteService;
import com.hm.manage.service.support.CredentialCryptoService;
import com.hm.manage.util.SupportSiteCodeUtils;

@Service
public class SupportSiteServiceImpl implements ISupportSiteService
{
    private static final String ZIP_FILE_PREFIX = "现场融合数据_";
    private static final String IMPORT_COPY_SUFFIX = "导入副本";

    private static final String SHEET_INSTRUCTION = "导出说明";
    private static final String SHEET_SITE = "现场信息";
    private static final String SHEET_MAIN_PLATFORM = "主平台";
    private static final String SHEET_SUB_PLATFORM = "子平台";
    private static final String SHEET_ENDPOINT = "页面信息";
    private static final String SHEET_SERVER = "服务器";
    private static final String SHEET_ORG = "组织";
    private static final String SHEET_CONTACT = "人员";
    private static final String SHEET_MESSAGE = "留言";
    private static final String SHEET_PLATFORM_CONTACT_REL = "主平台人员关系";
    private static final String SHEET_PLATFORM_SERVER_REL = "子平台服务器关系";

    private static final String[] INSTRUCTION_HEADERS = {"项目", "内容"};
    private static final String[] SITE_HEADERS = {"源现场ID", "现场名称", "现场编码", "省编码", "省名称", "市编码", "市名称", "区编码", "区名称", "详细地址", "现场描述", "状态", "备注"};
    private static final String[] MAIN_PLATFORM_HEADERS = {"源平台ID", "平台名称", "网络环境", "状态", "备注"};
    private static final String[] SUB_PLATFORM_HEADERS = {"源平台ID", "源父平台ID", "子平台名称", "网络环境", "状态", "备注"};
    private static final String[] ENDPOINT_HEADERS = {"源页面ID", "源子平台ID", "页面名称", "访问URL", "登录账号", "登录密码", "备注"};
    private static final String[] SERVER_HEADERS = {"源服务器ID", "服务器名称", "服务器IP", "SSH端口", "操作系统", "系统账号", "系统密码", "状态", "备注"};
    private static final String[] ORG_HEADERS = {"源组织ID", "组织类型", "组织名称", "组织简称", "状态", "备注"};
    private static final String[] CONTACT_HEADERS = {"源人员ID", "源组织ID", "联系人", "角色", "手机", "邮箱", "微信", "主联系人", "备注"};
    private static final String[] MESSAGE_HEADERS = {"源留言ID", "留言内容", "发布用户ID", "发布用户昵称", "状态", "创建时间", "备注"};
    private static final String[] PLATFORM_CONTACT_REL_HEADERS = {"源主平台ID", "源人员ID"};
    private static final String[] PLATFORM_SERVER_REL_HEADERS = {"源子平台ID", "源服务器ID"};

    private static final Set<String> GENERIC_CITY_NAMES = Set.of(
        "市辖区",
        "县",
        "自治区直辖县级行政区划",
        "省直辖县级行政区划"
    );

    @Autowired
    private SupportSiteMapper siteMapper;

    @Autowired
    private SupportSiteMessageMapper siteMessageMapper;

    @Autowired
    private SupportPlatformMapper platformMapper;

    @Autowired
    private SupportOrgMapper orgMapper;

    @Autowired
    private SupportContactMapper contactMapper;

    @Autowired
    private SupportServerMapper serverMapper;

    @Autowired
    private SupportSubplatformEndpointMapper endpointMapper;

    @Autowired
    private SupportPlatformServerRelMapper platformServerRelMapper;

    @Autowired
    private SupportPlatformContactRelMapper platformContactRelMapper;

    @Autowired
    private CredentialCryptoService cryptoService;

    @Autowired
    private ISupportChangeLogService changeLogService;

    @Override
    public SupportSite selectSupportSiteBySiteId(Long siteId)
    {
        return siteMapper.selectSupportSiteBySiteId(siteId);
    }

    @Override
    public List<SupportSite> selectSupportSiteList(SupportSite site)
    {
        return siteMapper.selectSupportSiteList(site);
    }

    @Override
    public int insertSupportSite(SupportSite site)
    {
        prepareSiteForSave(site);
        site.setSiteCode(generateOrReuseSiteCode(site, null));
        site.setCreateTime(DateUtils.getNowDate());
        int rows = siteMapper.insertSupportSite(site);
        if (rows > 0)
        {
            changeLogService.record(site.getSiteId(), "INSERT", "SITE", site.getSiteId(), site.getSiteName(), "新增现场 " + site.getSiteName(), null, site);
        }
        return rows;
    }

    @Override
    public int updateSupportSite(SupportSite site)
    {
        SupportSite original = siteMapper.selectSupportSiteBySiteId(site.getSiteId());
        if (original == null)
        {
            throw new ServiceException("现场不存在");
        }
        prepareSiteForSave(site);
        site.setSiteCode(generateOrReuseSiteCode(site, original));
        site.setUpdateTime(DateUtils.getNowDate());
        int rows = siteMapper.updateSupportSite(site);
        if (rows > 0)
        {
            changeLogService.record(site.getSiteId(), "UPDATE", "SITE", site.getSiteId(), site.getSiteName(), "修改现场 " + site.getSiteName(), original, site);
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteSupportSiteBySiteIds(Long[] siteIds)
    {
        List<SupportSite> deletedSites = new ArrayList<>();
        for (Long siteId : siteIds)
        {
            SupportSite site = siteMapper.selectSupportSiteBySiteId(siteId);
            if (site != null)
            {
                deletedSites.add(site);
            }
            List<SupportPlatform> platforms = platformMapper.selectPlatformsBySiteId(siteId);
            if (!platforms.isEmpty())
            {
                throw new ServiceException("现场下存在平台数据，禁止删除，请先清理关联数据");
            }
        }
        int rows = siteMapper.deleteSupportSiteBySiteIds(siteIds);
        if (rows > 0)
        {
            siteMessageMapper.deleteSupportSiteMessagesBySiteIds(siteIds);
            for (SupportSite site : deletedSites)
            {
                changeLogService.record(site.getSiteId(), "DELETE", "SITE", site.getSiteId(), site.getSiteName(), "删除现场 " + site.getSiteName(), site, null);
            }
        }
        return rows;
    }

    @Override
    public SupportSiteOverviewVo getSiteOverview(Long siteId)
    {
        SupportSite site = siteMapper.selectSupportSiteBySiteId(siteId);
        if (site == null)
        {
            throw new ServiceException("现场不存在");
        }
        List<SupportPlatform> platforms = platformMapper.selectPlatformsBySiteId(siteId);
        List<SupportPlatform> platformTree = buildPlatformTree(platforms);

        SupportServer serverQuery = new SupportServer();
        serverQuery.setSiteId(siteId);
        List<SupportServer> servers = serverMapper.selectSupportServerList(serverQuery);
        List<SupportOrg> orgs = orgMapper.selectOrgsBySiteId(siteId);

        SupportSiteOverviewVo vo = new SupportSiteOverviewVo();
        vo.setSiteId(site.getSiteId());
        vo.setSiteName(site.getSiteName());
        vo.setPlatformCount(platforms.size());
        vo.setServerCount(servers.size());
        vo.setOrgCount(orgs.size());
        vo.setContactCount(contactMapper.countContactsBySiteId(siteId));
        vo.setPlatformTree(platformTree);
        vo.setServers(servers);
        vo.setOrgs(orgs);
        return vo;
    }

    @Override
    public SupportSiteOverviewVo getSiteWorkbench(Long siteId)
    {
        SupportSiteOverviewVo vo = getSiteOverview(siteId);

        List<SupportPlatform> platforms = platformMapper.selectPlatformsBySiteId(siteId);
        SupportOrg orgQuery = new SupportOrg();
        SupportContact contactQuery = new SupportContact();
        Map<Long, List<SupportServer>> platformServers = new LinkedHashMap<>();
        Map<Long, List<SupportContact>> platformContacts = new LinkedHashMap<>();
        List<SupportContact> contacts = contactMapper.selectSupportContactList(contactQuery);
        List<SupportSubplatformEndpoint> endpoints = new ArrayList<>();

        for (SupportPlatform platform : platforms)
        {
            platformServers.put(platform.getPlatformId(), listServersForWorkbenchPlatform(platform, platforms));
            platformContacts.put(platform.getPlatformId(), contactMapper.selectContactsByPlatformId(platform.getPlatformId()));
            if (platform.getParentPlatformId() != null)
            {
                SupportSubplatformEndpoint endpointQuery = new SupportSubplatformEndpoint();
                endpointQuery.setSubPlatformId(platform.getPlatformId());
                endpoints.addAll(endpointMapper.selectSupportSubplatformEndpointList(endpointQuery));
            }
        }
        vo.setOrgs(orgMapper.selectSupportOrgList(orgQuery));
        vo.setContacts(contacts);
        vo.setEndpoints(endpoints);
        vo.setPlatformServers(platformServers);
        vo.setPlatformContacts(platformContacts);
        return vo;
    }

    @Override
    public void exportSitePackage(HttpServletResponse response, Long[] siteIds) throws Exception
    {
        List<SupportSite> sites = resolveExportSites(siteIds);
        String fileName = ZIP_FILE_PREFIX + DateUtils.dateTimeNow() + ".zip";
        response.setContentType("application/zip");
        response.setCharacterEncoding("utf-8");
        FileUtils.setAttachmentResponseHeader(response, fileName);

        Set<String> usedEntryNames = new HashSet<>();
        try (ZipOutputStream zip = new ZipOutputStream(response.getOutputStream()))
        {
            for (SupportSite site : sites)
            {
                String sourceName = StringUtils.defaultIfBlank(site.getSiteCode(), "site_" + site.getSiteId()) + "_" + site.getSiteName();
                String entryName = uniqueZipEntryName(safeFileName(sourceName) + ".xlsx", usedEntryNames);
                zip.putNextEntry(new ZipEntry(entryName));
                zip.write(buildSiteWorkbook(site));
                zip.closeEntry();
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String importSitePackage(MultipartFile file) throws Exception
    {
        if (file == null || file.isEmpty())
        {
            throw new ServiceException("请选择需要导入的现场数据压缩包");
        }
        String originalFilename = StringUtils.defaultString(file.getOriginalFilename());
        if (!StringUtils.endsWithIgnoreCase(originalFilename, ".zip"))
        {
            throw new ServiceException("仅允许导入zip格式的现场数据压缩包");
        }

        List<WorkbookZipEntry> entries = readWorkbookEntries(file);
        if (entries.isEmpty())
        {
            throw new ServiceException("压缩包中未找到xlsx现场数据文件");
        }

        String username = resolveCurrentUsername();
        String importTime = DateUtils.dateTimeNow();
        Date now = DateUtils.getNowDate();
        List<ImportedSiteResult> results = new ArrayList<>();
        for (WorkbookZipEntry entry : entries)
        {
            results.add(importSiteWorkbook(entry, username, importTime, now));
        }
        return buildImportResultMessage(results);
    }

    private List<SupportSite> resolveExportSites(Long[] siteIds)
    {
        if (siteIds == null || siteIds.length == 0)
        {
            throw new ServiceException("请先选择需要导出的现场");
        }
        List<SupportSite> sites = new ArrayList<>();
        Set<Long> uniqueIds = new LinkedHashSet<>();
        for (Long siteId : siteIds)
        {
            if (siteId != null)
            {
                uniqueIds.add(siteId);
            }
        }
        if (uniqueIds.isEmpty())
        {
            throw new ServiceException("请先选择需要导出的现场");
        }
        for (Long siteId : uniqueIds)
        {
            SupportSite site = siteMapper.selectSupportSiteBySiteId(siteId);
            if (site == null)
            {
                throw new ServiceException("现场不存在，ID：" + siteId);
            }
            sites.add(site);
        }
        return sites;
    }

    private byte[] buildSiteWorkbook(SupportSite site) throws Exception
    {
        List<SupportPlatform> platforms = platformMapper.selectPlatformsBySiteId(site.getSiteId());
        List<SupportPlatform> mainPlatforms = new ArrayList<>();
        List<SupportPlatform> subPlatforms = new ArrayList<>();
        for (SupportPlatform platform : platforms)
        {
            if (platform.getParentPlatformId() == null || "MAIN".equalsIgnoreCase(platform.getPlatformLevel()))
            {
                mainPlatforms.add(platform);
            }
            else
            {
                subPlatforms.add(platform);
            }
        }

        SupportServer serverQuery = new SupportServer();
        serverQuery.setSiteId(site.getSiteId());
        List<SupportServer> servers = serverMapper.selectSupportServerList(serverQuery);

        Map<Long, SupportContact> contactMap = new LinkedHashMap<>();
        Map<Long, SupportOrg> orgMap = new LinkedHashMap<>();
        List<String[]> platformContactRelRows = new ArrayList<>();
        Set<String> contactRelKeys = new HashSet<>();
        for (SupportPlatform mainPlatform : mainPlatforms)
        {
            List<SupportContact> contacts = contactMapper.selectContactsByPlatformId(mainPlatform.getPlatformId());
            for (SupportContact contact : contacts)
            {
                contactMap.putIfAbsent(contact.getContactId(), contact);
                if (contact.getOrgId() != null && !orgMap.containsKey(contact.getOrgId()))
                {
                    SupportOrg org = orgMapper.selectSupportOrgByOrgId(contact.getOrgId());
                    if (org != null)
                    {
                        orgMap.put(org.getOrgId(), org);
                    }
                }
                String relKey = mainPlatform.getPlatformId() + ":" + contact.getContactId();
                if (contactRelKeys.add(relKey))
                {
                    platformContactRelRows.add(row(mainPlatform.getPlatformId(), contact.getContactId()));
                }
            }
        }

        List<SupportSubplatformEndpoint> endpoints = new ArrayList<>();
        List<String[]> platformServerRelRows = new ArrayList<>();
        Set<String> serverRelKeys = new HashSet<>();
        for (SupportPlatform subPlatform : subPlatforms)
        {
            SupportSubplatformEndpoint endpointQuery = new SupportSubplatformEndpoint();
            endpointQuery.setSubPlatformId(subPlatform.getPlatformId());
            endpoints.addAll(endpointMapper.selectSupportSubplatformEndpointList(endpointQuery));

            List<SupportServer> platformServers = serverMapper.selectServersByPlatformId(subPlatform.getPlatformId());
            for (SupportServer server : platformServers)
            {
                String relKey = subPlatform.getPlatformId() + ":" + server.getServerId();
                if (serverRelKeys.add(relKey))
                {
                    platformServerRelRows.add(row(subPlatform.getPlatformId(), server.getServerId()));
                }
            }
        }

        List<String[]> siteRows = new ArrayList<>();
        siteRows.add(row(site.getSiteId(), site.getSiteName(), site.getSiteCode(), site.getProvinceCode(), site.getProvinceName(),
            site.getCityCode(), site.getCityName(), site.getDistrictCode(), site.getDistrictName(), site.getLocation(), site.getDescription(),
            site.getStatus(), site.getRemark()));
        List<String[]> mainPlatformRows = new ArrayList<>();
        for (SupportPlatform platform : mainPlatforms)
        {
            mainPlatformRows.add(row(platform.getPlatformId(), platform.getPlatformName(), platform.getNetworkEnv(), platform.getStatus(), platform.getRemark()));
        }
        List<String[]> subPlatformRows = new ArrayList<>();
        for (SupportPlatform platform : subPlatforms)
        {
            subPlatformRows.add(row(platform.getPlatformId(), platform.getParentPlatformId(), platform.getPlatformName(), platform.getNetworkEnv(), platform.getStatus(), platform.getRemark()));
        }
        List<String[]> endpointRows = new ArrayList<>();
        for (SupportSubplatformEndpoint endpoint : endpoints)
        {
            endpointRows.add(row(endpoint.getEndpointId(), endpoint.getSubPlatformId(), endpoint.getEndpointName(), endpoint.getAccessUrl(),
                endpoint.getLoginUsername(), safeDecrypt(endpoint.getLoginPasswordCipher()), endpoint.getRemark()));
        }
        List<String[]> serverRows = new ArrayList<>();
        for (SupportServer server : servers)
        {
            serverRows.add(row(server.getServerId(), server.getServerName(), server.getServerAddress(), server.getSshPort(), server.getOsType(),
                server.getOsUsername(), safeDecrypt(server.getOsPasswordCipher()), server.getStatus(), server.getRemark()));
        }
        List<String[]> orgRows = new ArrayList<>();
        for (SupportOrg org : orgMap.values())
        {
            orgRows.add(row(org.getOrgId(), org.getOrgType(), org.getOrgName(), org.getShortName(), org.getStatus(), org.getRemark()));
        }
        List<String[]> contactRows = new ArrayList<>();
        for (SupportContact contact : contactMap.values())
        {
            contactRows.add(row(contact.getContactId(), contact.getOrgId(), contact.getContactName(), contact.getRoleType(), contact.getPhone(),
                contact.getEmail(), contact.getWechat(), contact.getIsPrimary(), contact.getRemark()));
        }
        List<String[]> messageRows = new ArrayList<>();
        for (SupportSiteMessage message : siteMessageMapper.selectMessagesBySiteId(site.getSiteId()))
        {
            messageRows.add(row(message.getMessageId(), message.getMessageContent(), message.getPublisherId(), message.getPublisherName(),
                message.getStatus(), formatDateTime(message.getCreateTime()), message.getRemark()));
        }
        List<String[]> instructionRows = new ArrayList<>();
        instructionRows.add(row("导出时间", DateUtils.getTime()));
        instructionRows.add(row("现场名称", site.getSiteName()));
        instructionRows.add(row("现场编码", site.getSiteCode()));
        instructionRows.add(row("说明", "每个xlsx代表一个现场，导入时会新建现场并重新生成现场编码；源ID仅用于重建关系。"));
        instructionRows.add(row("数据统计", "主平台" + mainPlatformRows.size() + "个，子平台" + subPlatformRows.size() + "个，页面" + endpointRows.size() + "个，服务器" + serverRows.size() + "台，组织" + orgRows.size() + "个，人员" + contactRows.size() + "位，留言" + messageRows.size() + "条"));

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream())
        {
            writeSheet(workbook, SHEET_INSTRUCTION, INSTRUCTION_HEADERS, instructionRows);
            writeSheet(workbook, SHEET_SITE, SITE_HEADERS, siteRows);
            writeSheet(workbook, SHEET_MAIN_PLATFORM, MAIN_PLATFORM_HEADERS, mainPlatformRows);
            writeSheet(workbook, SHEET_SUB_PLATFORM, SUB_PLATFORM_HEADERS, subPlatformRows);
            writeSheet(workbook, SHEET_ENDPOINT, ENDPOINT_HEADERS, endpointRows);
            writeSheet(workbook, SHEET_SERVER, SERVER_HEADERS, serverRows);
            writeSheet(workbook, SHEET_ORG, ORG_HEADERS, orgRows);
            writeSheet(workbook, SHEET_CONTACT, CONTACT_HEADERS, contactRows);
            writeSheet(workbook, SHEET_MESSAGE, MESSAGE_HEADERS, messageRows);
            writeSheet(workbook, SHEET_PLATFORM_CONTACT_REL, PLATFORM_CONTACT_REL_HEADERS, platformContactRelRows);
            writeSheet(workbook, SHEET_PLATFORM_SERVER_REL, PLATFORM_SERVER_REL_HEADERS, platformServerRelRows);
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private ImportedSiteResult importSiteWorkbook(WorkbookZipEntry entry, String username, String importTime, Date now) throws Exception
    {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(entry.bytes)))
        {
            ensureRequiredSheet(workbook, SHEET_INSTRUCTION, entry.name);
            List<Map<String, String>> siteRows = readSheetRows(workbook, SHEET_SITE, entry.name);
            if (siteRows.isEmpty())
            {
                throw new ServiceException("文件" + entry.name + "缺少现场信息");
            }

            SupportSite site = buildImportedSite(siteRows.get(0), entry.name, importTime, username, now);
            siteMapper.insertSupportSite(site);

            Map<Long, Long> platformIdMap = new LinkedHashMap<>();
            int mainPlatformCount = importMainPlatforms(workbook, entry.name, site.getSiteId(), platformIdMap, username, now);
            int subPlatformCount = importSubPlatforms(workbook, entry.name, site.getSiteId(), platformIdMap, username, now);

            Map<Long, Long> orgIdMap = importOrgs(workbook, entry.name, username, now);
            Map<Long, Long> contactIdMap = importContacts(workbook, entry.name, orgIdMap, username, now);
            Map<Long, Long> serverIdMap = importServers(workbook, entry.name, site.getSiteId(), username, now);
            int endpointCount = importEndpoints(workbook, entry.name, platformIdMap, username, now);
            int contactRelCount = importPlatformContactRelations(workbook, entry.name, platformIdMap, contactIdMap, username, now);
            int serverRelCount = importPlatformServerRelations(workbook, entry.name, platformIdMap, serverIdMap, username, now);
            int messageCount = importMessages(workbook, entry.name, site.getSiteId(), username, now);

            ImportedSiteResult result = new ImportedSiteResult();
            result.siteId = site.getSiteId();
            result.siteName = site.getSiteName();
            result.sourceFile = entry.name;
            result.mainPlatformCount = mainPlatformCount;
            result.subPlatformCount = subPlatformCount;
            result.endpointCount = endpointCount;
            result.serverCount = serverIdMap.size();
            result.orgCount = orgIdMap.size();
            result.contactCount = contactIdMap.size();
            result.contactRelCount = contactRelCount;
            result.serverRelCount = serverRelCount;
            result.messageCount = messageCount;

            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("导入文件", entry.name);
            detail.put("新现场ID", site.getSiteId());
            detail.put("新现场名称", site.getSiteName());
            detail.put("主平台数量", mainPlatformCount);
            detail.put("子平台数量", subPlatformCount);
            detail.put("页面数量", endpointCount);
            detail.put("服务器数量", serverIdMap.size());
            detail.put("组织数量", orgIdMap.size());
            detail.put("人员数量", contactIdMap.size());
            detail.put("主平台人员关系数量", contactRelCount);
            detail.put("子平台服务器关系数量", serverRelCount);
            detail.put("留言数量", messageCount);
            changeLogService.record(site.getSiteId(), "INSERT", "SITE", site.getSiteId(), site.getSiteName(), "导入新建现场 " + site.getSiteName(), null, detail);
            return result;
        }
    }

    private SupportSite buildImportedSite(Map<String, String> row, String fileName, String importTime, String username, Date now)
    {
        SupportSite site = new SupportSite();
        String originalName = value(row, "现场名称");
        if (StringUtils.isBlank(originalName))
        {
            originalName = StringUtils.substringBeforeLast(fileName, ".");
        }
        site.setSiteName(limitText(originalName + "（" + IMPORT_COPY_SUFFIX + " " + importTime + "）", 100));
        site.setProvinceCode(value(row, "省编码"));
        site.setProvinceName(value(row, "省名称"));
        site.setCityCode(value(row, "市编码"));
        site.setCityName(value(row, "市名称"));
        site.setDistrictCode(value(row, "区编码"));
        site.setDistrictName(value(row, "区名称"));
        site.setLocation(value(row, "详细地址"));
        site.setDescription(value(row, "现场描述"));
        site.setStatus(defaultStatus(value(row, "状态")));
        site.setRemark(value(row, "备注"));
        prepareSiteForSave(site);
        site.setSiteCode(generateOrReuseSiteCode(site, null));
        applyImportMeta(site, username, now);
        return site;
    }

    private int importMainPlatforms(Workbook workbook, String fileName, Long siteId, Map<Long, Long> platformIdMap, String username, Date now)
    {
        int count = 0;
        for (Map<String, String> row : readSheetRows(workbook, SHEET_MAIN_PLATFORM, fileName))
        {
            Long sourceId = requiredLong(row, "源平台ID", SHEET_MAIN_PLATFORM, fileName);
            assertUniqueSourceId(platformIdMap, sourceId, SHEET_MAIN_PLATFORM, fileName);
            SupportPlatform platform = new SupportPlatform();
            platform.setSiteId(siteId);
            platform.setPlatformName(requiredValue(row, "平台名称", SHEET_MAIN_PLATFORM, fileName));
            platform.setPlatformLevel("MAIN");
            platform.setNetworkEnv(value(row, "网络环境"));
            platform.setStatus(defaultStatus(value(row, "状态")));
            platform.setRemark(value(row, "备注"));
            applyImportMeta(platform, username, now);
            platformMapper.insertSupportPlatform(platform);
            platformIdMap.put(sourceId, platform.getPlatformId());
            count++;
        }
        return count;
    }

    private int importSubPlatforms(Workbook workbook, String fileName, Long siteId, Map<Long, Long> platformIdMap, String username, Date now)
    {
        int count = 0;
        for (Map<String, String> row : readSheetRows(workbook, SHEET_SUB_PLATFORM, fileName))
        {
            Long sourceId = requiredLong(row, "源平台ID", SHEET_SUB_PLATFORM, fileName);
            Long sourceParentId = requiredLong(row, "源父平台ID", SHEET_SUB_PLATFORM, fileName);
            assertUniqueSourceId(platformIdMap, sourceId, SHEET_SUB_PLATFORM, fileName);
            Long parentPlatformId = platformIdMap.get(sourceParentId);
            if (parentPlatformId == null)
            {
                throw new ServiceException("文件" + fileName + "的子平台引用了不存在的源父平台ID：" + sourceParentId);
            }
            SupportPlatform platform = new SupportPlatform();
            platform.setSiteId(siteId);
            platform.setPlatformName(requiredValue(row, "子平台名称", SHEET_SUB_PLATFORM, fileName));
            platform.setPlatformLevel("SUB");
            platform.setNetworkEnv(value(row, "网络环境"));
            platform.setParentPlatformId(parentPlatformId);
            platform.setStatus(defaultStatus(value(row, "状态")));
            platform.setRemark(value(row, "备注"));
            applyImportMeta(platform, username, now);
            platformMapper.insertSupportPlatform(platform);
            platformIdMap.put(sourceId, platform.getPlatformId());
            count++;
        }
        return count;
    }

    private Map<Long, Long> importOrgs(Workbook workbook, String fileName, String username, Date now)
    {
        Map<Long, Long> orgIdMap = new LinkedHashMap<>();
        for (Map<String, String> row : readSheetRows(workbook, SHEET_ORG, fileName))
        {
            Long sourceId = requiredLong(row, "源组织ID", SHEET_ORG, fileName);
            assertUniqueSourceId(orgIdMap, sourceId, SHEET_ORG, fileName);
            SupportOrg org = new SupportOrg();
            org.setOrgType(requiredValue(row, "组织类型", SHEET_ORG, fileName));
            org.setOrgName(requiredValue(row, "组织名称", SHEET_ORG, fileName));
            org.setShortName(value(row, "组织简称"));
            org.setStatus(defaultStatus(value(row, "状态")));
            org.setRemark(value(row, "备注"));
            applyImportMeta(org, username, now);
            orgMapper.insertSupportOrg(org);
            orgIdMap.put(sourceId, org.getOrgId());
        }
        return orgIdMap;
    }

    private Map<Long, Long> importContacts(Workbook workbook, String fileName, Map<Long, Long> orgIdMap, String username, Date now)
    {
        Map<Long, Long> contactIdMap = new LinkedHashMap<>();
        for (Map<String, String> row : readSheetRows(workbook, SHEET_CONTACT, fileName))
        {
            Long sourceId = requiredLong(row, "源人员ID", SHEET_CONTACT, fileName);
            Long sourceOrgId = requiredLong(row, "源组织ID", SHEET_CONTACT, fileName);
            assertUniqueSourceId(contactIdMap, sourceId, SHEET_CONTACT, fileName);
            Long orgId = orgIdMap.get(sourceOrgId);
            if (orgId == null)
            {
                throw new ServiceException("文件" + fileName + "的人员引用了不存在的源组织ID：" + sourceOrgId);
            }
            SupportContact contact = new SupportContact();
            contact.setOrgId(orgId);
            contact.setContactName(requiredValue(row, "联系人", SHEET_CONTACT, fileName));
            contact.setRoleType(value(row, "角色"));
            contact.setPhone(value(row, "手机"));
            contact.setEmail(value(row, "邮箱"));
            contact.setWechat(value(row, "微信"));
            contact.setIsPrimary(defaultFlag(value(row, "主联系人")));
            contact.setRemark(value(row, "备注"));
            applyImportMeta(contact, username, now);
            contactMapper.insertSupportContact(contact);
            contactIdMap.put(sourceId, contact.getContactId());
        }
        return contactIdMap;
    }

    private Map<Long, Long> importServers(Workbook workbook, String fileName, Long siteId, String username, Date now)
    {
        Map<Long, Long> serverIdMap = new LinkedHashMap<>();
        for (Map<String, String> row : readSheetRows(workbook, SHEET_SERVER, fileName))
        {
            Long sourceId = requiredLong(row, "源服务器ID", SHEET_SERVER, fileName);
            assertUniqueSourceId(serverIdMap, sourceId, SHEET_SERVER, fileName);
            SupportServer server = new SupportServer();
            server.setSiteId(siteId);
            server.setServerName(requiredValue(row, "服务器名称", SHEET_SERVER, fileName));
            server.setServerAddress(requiredValue(row, "服务器IP", SHEET_SERVER, fileName));
            server.setSshPort(defaultSshPort(value(row, "SSH端口"), fileName));
            server.setOsType(value(row, "操作系统"));
            server.setOsUsername(value(row, "系统账号"));
            server.setOsPasswordCipher(cryptoService.encrypt(value(row, "系统密码")));
            server.setStatus(defaultStatus(value(row, "状态")));
            server.setRemark(value(row, "备注"));
            applyImportMeta(server, username, now);
            serverMapper.insertSupportServer(server);
            serverIdMap.put(sourceId, server.getServerId());
        }
        return serverIdMap;
    }

    private int importEndpoints(Workbook workbook, String fileName, Map<Long, Long> platformIdMap, String username, Date now)
    {
        int count = 0;
        for (Map<String, String> row : readSheetRows(workbook, SHEET_ENDPOINT, fileName))
        {
            Long sourceSubPlatformId = requiredLong(row, "源子平台ID", SHEET_ENDPOINT, fileName);
            Long subPlatformId = platformIdMap.get(sourceSubPlatformId);
            if (subPlatformId == null)
            {
                throw new ServiceException("文件" + fileName + "的页面引用了不存在的源子平台ID：" + sourceSubPlatformId);
            }
            SupportSubplatformEndpoint endpoint = new SupportSubplatformEndpoint();
            endpoint.setSubPlatformId(subPlatformId);
            endpoint.setEndpointName(value(row, "页面名称"));
            endpoint.setAccessUrl(requiredValue(row, "访问URL", SHEET_ENDPOINT, fileName));
            endpoint.setLoginUsername(value(row, "登录账号"));
            endpoint.setLoginPasswordCipher(cryptoService.encrypt(value(row, "登录密码")));
            endpoint.setRemark(value(row, "备注"));
            applyImportMeta(endpoint, username, now);
            endpointMapper.insertSupportSubplatformEndpoint(endpoint);
            count++;
        }
        return count;
    }

    private int importPlatformContactRelations(Workbook workbook, String fileName, Map<Long, Long> platformIdMap, Map<Long, Long> contactIdMap, String username, Date now)
    {
        int count = 0;
        Set<String> relKeys = new HashSet<>();
        for (Map<String, String> row : readSheetRows(workbook, SHEET_PLATFORM_CONTACT_REL, fileName))
        {
            Long sourcePlatformId = requiredLong(row, "源主平台ID", SHEET_PLATFORM_CONTACT_REL, fileName);
            Long sourceContactId = requiredLong(row, "源人员ID", SHEET_PLATFORM_CONTACT_REL, fileName);
            Long platformId = platformIdMap.get(sourcePlatformId);
            Long contactId = contactIdMap.get(sourceContactId);
            if (platformId == null)
            {
                throw new ServiceException("文件" + fileName + "的主平台人员关系引用了不存在的源主平台ID：" + sourcePlatformId);
            }
            if (contactId == null)
            {
                throw new ServiceException("文件" + fileName + "的主平台人员关系引用了不存在的源人员ID：" + sourceContactId);
            }
            String relKey = platformId + ":" + contactId;
            if (relKeys.add(relKey))
            {
                platformContactRelMapper.insertSupportPlatformContactRel(platformId, contactId, username, now);
                count++;
            }
        }
        return count;
    }

    private int importPlatformServerRelations(Workbook workbook, String fileName, Map<Long, Long> platformIdMap, Map<Long, Long> serverIdMap, String username, Date now)
    {
        int count = 0;
        Set<String> relKeys = new HashSet<>();
        for (Map<String, String> row : readSheetRows(workbook, SHEET_PLATFORM_SERVER_REL, fileName))
        {
            Long sourcePlatformId = requiredLong(row, "源子平台ID", SHEET_PLATFORM_SERVER_REL, fileName);
            Long sourceServerId = requiredLong(row, "源服务器ID", SHEET_PLATFORM_SERVER_REL, fileName);
            Long platformId = platformIdMap.get(sourcePlatformId);
            Long serverId = serverIdMap.get(sourceServerId);
            if (platformId == null)
            {
                throw new ServiceException("文件" + fileName + "的子平台服务器关系引用了不存在的源子平台ID：" + sourcePlatformId);
            }
            if (serverId == null)
            {
                throw new ServiceException("文件" + fileName + "的子平台服务器关系引用了不存在的源服务器ID：" + sourceServerId);
            }
            String relKey = platformId + ":" + serverId;
            if (relKeys.add(relKey))
            {
                SupportPlatformServerRel rel = new SupportPlatformServerRel();
                rel.setPlatformId(platformId);
                rel.setServerId(serverId);
                rel.setCreateBy(username);
                rel.setCreateTime(now);
                platformServerRelMapper.insertSupportPlatformServerRel(rel);
                count++;
            }
        }
        return count;
    }

    private int importMessages(Workbook workbook, String fileName, Long siteId, String username, Date now)
    {
        int count = 0;
        Set<Long> sourceIds = new HashSet<>();
        for (Map<String, String> row : readOptionalSheetRows(workbook, SHEET_MESSAGE, fileName))
        {
            Long sourceId = requiredLong(row, "源留言ID", SHEET_MESSAGE, fileName);
            if (!sourceIds.add(sourceId))
            {
                throw new ServiceException("文件" + fileName + "的工作表" + SHEET_MESSAGE + "存在重复源留言ID：" + sourceId);
            }
            String content = requiredValue(row, "留言内容", SHEET_MESSAGE, fileName);
            if (content.length() > 300)
            {
                throw new ServiceException("文件" + fileName + "的工作表" + SHEET_MESSAGE + "留言内容不能超过300个字");
            }

            SupportSiteMessage message = new SupportSiteMessage();
            message.setSiteId(siteId);
            message.setMessageContent(content);
            message.setPublisherId(null);
            message.setPublisherName(limitText(value(row, "发布用户昵称"), 64));
            message.setStatus(defaultStatus(value(row, "状态")));
            message.setCreateBy(username);
            message.setCreateTime(parseImportDate(value(row, "创建时间"), now));
            message.setRemark(limitText(value(row, "备注"), 500));
            siteMessageMapper.insertSupportSiteMessage(message);
            count++;
        }
        return count;
    }

    private List<WorkbookZipEntry> readWorkbookEntries(MultipartFile file) throws Exception
    {
        List<WorkbookZipEntry> entries = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(file.getInputStream()))
        {
            ZipEntry zipEntry;
            while ((zipEntry = zip.getNextEntry()) != null)
            {
                if (!zipEntry.isDirectory() && isWorkbookEntry(zipEntry.getName()))
                {
                    entries.add(new WorkbookZipEntry(extractZipEntryFileName(zipEntry.getName()), readCurrentZipEntry(zip)));
                }
                zip.closeEntry();
            }
        }
        return entries;
    }

    private String extractZipEntryFileName(String entryName)
    {
        String normalized = StringUtils.defaultString(entryName).replace("\\", "/");
        String fileName = StringUtils.substringAfterLast(normalized, "/");
        return StringUtils.defaultIfBlank(fileName, normalized);
    }

    private byte[] readCurrentZipEntry(ZipInputStream zip) throws Exception
    {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream())
        {
            byte[] buffer = new byte[4096];
            int length;
            while ((length = zip.read(buffer)) > -1)
            {
                output.write(buffer, 0, length);
            }
            return output.toByteArray();
        }
    }

    private boolean isWorkbookEntry(String name)
    {
        if (StringUtils.isBlank(name))
        {
            return false;
        }
        String normalized = name.replace("\\", "/");
        return StringUtils.endsWithIgnoreCase(normalized, ".xlsx")
            && !StringUtils.startsWith(normalized, "__MACOSX/")
            && !StringUtils.contains(normalized, "/.");
    }

    private void writeSheet(Workbook workbook, String sheetName, String[] headers, List<String[]> rows)
    {
        Sheet sheet = workbook.createSheet(sheetName);
        CellStyle headerStyle = buildHeaderStyle(workbook);
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++)
        {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, Math.min(Math.max(headers[i].length() * 768, 3600), 9000));
        }
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++)
        {
            Row dataRow = sheet.createRow(rowIndex + 1);
            String[] row = rows.get(rowIndex);
            for (int cellIndex = 0; cellIndex < headers.length; cellIndex++)
            {
                dataRow.createCell(cellIndex).setCellValue(cellIndex < row.length ? row[cellIndex] : StringUtils.EMPTY);
            }
        }
    }

    private CellStyle buildHeaderStyle(Workbook workbook)
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

    private List<Map<String, String>> readSheetRows(Workbook workbook, String sheetName, String fileName)
    {
        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet == null)
        {
            throw new ServiceException("文件" + fileName + "缺少工作表：" + sheetName);
        }
        DataFormatter formatter = new DataFormatter();
        Row headerRow = sheet.getRow(0);
        if (headerRow == null)
        {
            throw new ServiceException("文件" + fileName + "的工作表" + sheetName + "缺少表头");
        }
        List<String> headers = new ArrayList<>();
        for (int i = 0; i < headerRow.getLastCellNum(); i++)
        {
            headers.add(StringUtils.trimToEmpty(formatter.formatCellValue(headerRow.getCell(i))));
        }
        List<Map<String, String>> rows = new ArrayList<>();
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++)
        {
            Row row = sheet.getRow(rowIndex);
            if (row == null)
            {
                continue;
            }
            Map<String, String> values = new LinkedHashMap<>();
            boolean hasValue = false;
            for (int cellIndex = 0; cellIndex < headers.size(); cellIndex++)
            {
                String header = headers.get(cellIndex);
                if (StringUtils.isBlank(header))
                {
                    continue;
                }
                String cellValue = StringUtils.trimToEmpty(formatter.formatCellValue(row.getCell(cellIndex)));
                values.put(header, cellValue);
                hasValue = hasValue || StringUtils.isNotBlank(cellValue);
            }
            if (hasValue)
            {
                rows.add(values);
            }
        }
        return rows;
    }

    private List<Map<String, String>> readOptionalSheetRows(Workbook workbook, String sheetName, String fileName)
    {
        if (workbook.getSheet(sheetName) == null)
        {
            return new ArrayList<>();
        }
        return readSheetRows(workbook, sheetName, fileName);
    }

    private void ensureRequiredSheet(Workbook workbook, String sheetName, String fileName)
    {
        if (workbook.getSheet(sheetName) == null)
        {
            throw new ServiceException("文件" + fileName + "缺少工作表：" + sheetName);
        }
    }

    private String buildImportResultMessage(List<ImportedSiteResult> results)
    {
        StringBuilder message = new StringBuilder("导入成功，共新建 ").append(results.size()).append(" 个现场：");
        for (int i = 0; i < results.size(); i++)
        {
            ImportedSiteResult result = results.get(i);
            message.append("<br/>").append(i + 1).append(". ").append(result.siteName)
                .append("（主平台").append(result.mainPlatformCount)
                .append("，子平台").append(result.subPlatformCount)
                .append("，服务器").append(result.serverCount)
                .append("，人员").append(result.contactCount)
                .append("，留言").append(result.messageCount)
                .append("）");
        }
        return message.toString();
    }

    private String safeDecrypt(String cipherText)
    {
        return StringUtils.isBlank(cipherText) ? StringUtils.EMPTY : cryptoService.decrypt(cipherText);
    }

    private String formatDateTime(Date date)
    {
        return date == null ? StringUtils.EMPTY : DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD_HH_MM_SS, date);
    }

    private Date parseImportDate(String value, Date defaultDate)
    {
        Date parsedDate = DateUtils.parseDate(value);
        return parsedDate == null ? defaultDate : parsedDate;
    }

    private void applyImportMeta(BaseEntity entity, String username, Date now)
    {
        entity.setCreateBy(username);
        entity.setCreateTime(now);
        entity.setUpdateBy(null);
        entity.setUpdateTime(null);
    }

    private String resolveCurrentUsername()
    {
        try
        {
            return SecurityUtils.getUsername();
        }
        catch (Exception e)
        {
            return "system";
        }
    }

    private Long requiredLong(Map<String, String> row, String column, String sheetName, String fileName)
    {
        String value = requiredValue(row, column, sheetName, fileName);
        try
        {
            return new BigDecimal(value).longValueExact();
        }
        catch (Exception e)
        {
            throw new ServiceException("文件" + fileName + "的工作表" + sheetName + "字段" + column + "必须是整数");
        }
    }

    private String requiredValue(Map<String, String> row, String column, String sheetName, String fileName)
    {
        String value = value(row, column);
        if (StringUtils.isBlank(value))
        {
            throw new ServiceException("文件" + fileName + "的工作表" + sheetName + "字段" + column + "不能为空");
        }
        return value;
    }

    private String value(Map<String, String> row, String column)
    {
        return StringUtils.trimToNull(row.get(column));
    }

    private void assertUniqueSourceId(Map<Long, Long> idMap, Long sourceId, String sheetName, String fileName)
    {
        if (idMap.containsKey(sourceId))
        {
            throw new ServiceException("文件" + fileName + "的工作表" + sheetName + "存在重复源ID：" + sourceId);
        }
    }

    private Integer defaultSshPort(String value, String fileName)
    {
        if (StringUtils.isBlank(value))
        {
            return 22;
        }
        try
        {
            int port = new BigDecimal(value).intValueExact();
            if (port < 1 || port > 65535)
            {
                throw new ServiceException("文件" + fileName + "存在超出范围的SSH端口：" + value);
            }
            return port;
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new ServiceException("文件" + fileName + "存在无效的SSH端口：" + value);
        }
    }

    private String defaultStatus(String status)
    {
        return StringUtils.defaultIfBlank(status, "0");
    }

    private String defaultFlag(String flag)
    {
        return StringUtils.defaultIfBlank(flag, "0");
    }

    private String[] row(Object... values)
    {
        String[] row = new String[values.length];
        for (int i = 0; i < values.length; i++)
        {
            row[i] = text(values[i]);
        }
        return row;
    }

    private String text(Object value)
    {
        return value == null ? StringUtils.EMPTY : String.valueOf(value);
    }

    private String safeFileName(String fileName)
    {
        String safeName = StringUtils.defaultIfBlank(fileName, "现场数据").replaceAll("[\\\\/:*?\"<>|]", "_");
        safeName = safeName.replaceAll("\\s+", "_");
        return StringUtils.substring(safeName, 0, 120);
    }

    private String uniqueZipEntryName(String fileName, Set<String> usedEntryNames)
    {
        String baseName = StringUtils.substringBeforeLast(fileName, ".");
        String suffix = "." + StringUtils.substringAfterLast(fileName, ".");
        String candidate = fileName;
        int index = 2;
        while (!usedEntryNames.add(candidate))
        {
            candidate = baseName + "_" + index + suffix;
            index++;
        }
        return candidate;
    }

    private String limitText(String value, int maxLength)
    {
        return value == null ? null : StringUtils.substring(value, 0, maxLength);
    }

    private static class WorkbookZipEntry
    {
        private final String name;
        private final byte[] bytes;

        private WorkbookZipEntry(String name, byte[] bytes)
        {
            this.name = name;
            this.bytes = bytes;
        }
    }

    private static class ImportedSiteResult
    {
        private Long siteId;
        private String siteName;
        private String sourceFile;
        private int mainPlatformCount;
        private int subPlatformCount;
        private int endpointCount;
        private int serverCount;
        private int orgCount;
        private int contactCount;
        private int contactRelCount;
        private int serverRelCount;
        private int messageCount;
    }

    private List<SupportServer> listServersForWorkbenchPlatform(SupportPlatform platform, List<SupportPlatform> platforms)
    {
        if (platform.getParentPlatformId() != null)
        {
            return serverMapper.selectServersByPlatformId(platform.getPlatformId());
        }
        Map<Long, SupportServer> serverMap = new LinkedHashMap<>();
        for (SupportPlatform item : platforms)
        {
            if (!platform.getPlatformId().equals(item.getParentPlatformId()))
            {
                continue;
            }
            List<SupportServer> servers = serverMapper.selectServersByPlatformId(item.getPlatformId());
            for (SupportServer server : servers)
            {
                serverMap.putIfAbsent(server.getServerId(), server);
            }
        }
        return new ArrayList<>(serverMap.values());
    }

    @Override
    public String previewSiteCode(SupportSite site)
    {
        SupportSite original = null;
        if (site.getSiteId() != null)
        {
            original = siteMapper.selectSupportSiteBySiteId(site.getSiteId());
            if (original == null)
            {
                throw new ServiceException("现场不存在");
            }
        }
        prepareSiteRegion(site);
        return generateOrReuseSiteCode(site, original);
    }

    private void prepareSiteForSave(SupportSite site)
    {
        if (site == null)
        {
            throw new ServiceException("现场数据不能为空");
        }
        prepareSiteRegion(site);
        if (StringUtils.isBlank(site.getSiteName()))
        {
            throw new ServiceException("现场名称不能为空");
        }

        site.setSiteName(site.getSiteName().trim());
    }

    private void prepareSiteRegion(SupportSite site)
    {
        if (site == null)
        {
            throw new ServiceException("现场数据不能为空");
        }
        if (StringUtils.isBlank(site.getProvinceCode()) || StringUtils.isBlank(site.getProvinceName()))
        {
            throw new ServiceException("请选择现场所属省份");
        }
        if (StringUtils.isBlank(site.getCityCode()) || StringUtils.isBlank(site.getCityName()))
        {
            throw new ServiceException("请选择现场所属城市");
        }
        if (StringUtils.isBlank(site.getDistrictCode()) || StringUtils.isBlank(site.getDistrictName()))
        {
            throw new ServiceException("请选择现场所属区县");
        }

        site.setProvinceCode(site.getProvinceCode().trim());
        site.setProvinceName(site.getProvinceName().trim());
        site.setCityCode(site.getCityCode().trim());
        site.setCityName(site.getCityName().trim());
        site.setDistrictCode(site.getDistrictCode().trim());
        site.setDistrictName(site.getDistrictName().trim());
        if (site.getLocation() != null)
        {
            site.setLocation(site.getLocation().trim());
        }
        if (site.getDescription() != null)
        {
            site.setDescription(site.getDescription().trim());
        }
        if (site.getRemark() != null)
        {
            site.setRemark(site.getRemark().trim());
        }
    }

    private String generateOrReuseSiteCode(SupportSite site, SupportSite original)
    {
        if (original != null && !isRegionChanged(original, site) && StringUtils.isNotBlank(original.getSiteCode()))
        {
            return original.getSiteCode();
        }

        Integer currentMax = siteMapper.selectMaxSiteSequenceByRegion(site);
        int nextSequence = (currentMax == null ? 0 : currentMax) + 1;
        return buildSiteCodePrefix(site) + "_" + String.format("%04d", nextSequence);
    }

    private boolean isRegionChanged(SupportSite original, SupportSite incoming)
    {
        return !StringUtils.equals(original.getProvinceCode(), incoming.getProvinceCode())
            || !StringUtils.equals(original.getCityCode(), incoming.getCityCode())
            || !StringUtils.equals(original.getDistrictCode(), incoming.getDistrictCode());
    }

    private String buildSiteCodePrefix(SupportSite site)
    {
        String provinceAbbr = SupportSiteCodeUtils.toAreaAbbreviation(site.getProvinceName());
        String citySource = GENERIC_CITY_NAMES.contains(site.getCityName()) ? site.getProvinceName() : site.getCityName();
        String cityAbbr = SupportSiteCodeUtils.toAreaAbbreviation(citySource);
        String districtAbbr = SupportSiteCodeUtils.toAreaAbbreviation(site.getDistrictName());
        return provinceAbbr + "_" + cityAbbr + "_" + districtAbbr;
    }

    private List<SupportPlatform> buildPlatformTree(List<SupportPlatform> platforms)
    {
        List<SupportPlatform> roots = new ArrayList<>();
        for (SupportPlatform platform : platforms)
        {
            if (platform.getParentPlatformId() == null)
            {
                roots.add(platform);
            }
            platform.setChildren(new ArrayList<>());
        }
        for (SupportPlatform platform : platforms)
        {
            if (platform.getParentPlatformId() == null)
            {
                continue;
            }
            for (SupportPlatform parent : platforms)
            {
                if (platform.getParentPlatformId().equals(parent.getPlatformId()))
                {
                    parent.getChildren().add(platform);
                    break;
                }
            }
        }
        return roots;
    }
}
