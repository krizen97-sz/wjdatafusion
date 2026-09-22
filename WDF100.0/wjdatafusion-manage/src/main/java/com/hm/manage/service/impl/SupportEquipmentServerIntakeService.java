package com.hm.manage.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.hm.common.exception.ServiceException;
import com.hm.common.utils.file.FileUtils;
import com.hm.manage.domain.SupportPlatform;
import com.hm.manage.domain.SupportServer;
import com.hm.manage.domain.bo.SupportEquipmentCreateBo;
import com.hm.manage.domain.bo.SupportEquipmentServerIntakeBo;
import com.hm.manage.domain.bo.SupportEquipmentServerIntakeBo.*;
import com.hm.manage.domain.bo.SupportEquipmentServerIntakeBo.Row;
import com.hm.manage.domain.vo.SupportEquipmentPlatformBindingVo;
import com.hm.manage.mapper.SupportEquipmentBindingMapper;
import com.hm.manage.mapper.SupportServerMapper;
import com.hm.manage.mapper.SupportSiteMapper;
import com.hm.manage.service.ISupportEquipmentService;
import com.hm.manage.service.ISupportPlatformService;

@Service
public class SupportEquipmentServerIntakeService
{
    public static final int LIMIT = 512;
    public static final long FILE_LIMIT = 5 * 1024 * 1024;
    private static final String[] HEADERS = {"服务器名称", "服务器IP", "SSH端口", "操作系统", "系统账号", "系统密码", "运行状态"};

    @Autowired private SupportSiteMapper siteMapper;
    @Autowired private SupportServerMapper serverMapper;
    @Autowired private SupportEquipmentBindingMapper bindingMapper;
    @Autowired private ISupportPlatformService platformService;
    @Autowired private ISupportEquipmentService equipmentService;

    public Preview preview(SupportEquipmentServerIntakeBo command)
    {
        requireContext(command);
        List<Row> rows = resolveRows(command);
        SupportServer query = new SupportServer();
        query.setSiteId(command.siteId());
        Map<String, List<SupportServer>> existing = serverMapper.selectSupportServerList(query).stream()
            .collect(Collectors.groupingBy(server -> addressKey(server.getServerAddress())));
        Map<Long, List<SupportEquipmentPlatformBindingVo>> bindings = bindingMapper.selectServerBindingsBySiteId(command.siteId()).stream()
            .collect(Collectors.groupingBy(SupportEquipmentPlatformBindingVo::getSourceId));
        Map<String, Integer> occurrences = new HashMap<>();
        rows.forEach(row -> occurrences.merge(addressKey(row.serverAddress()), 1, Integer::sum));
        List<CheckedRow> checked = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++)
        {
            Row raw = rows.get(index);
            List<String> errors = new ArrayList<>();
            String address = addressKey(raw.serverAddress());
            if (ipv4(address) == null) errors.add("IP格式不正确");
            if (occurrences.get(address) > 1) errors.add("清单内IP重复");
            List<String> profileErrors = new ArrayList<>();
            String name = clean(raw.serverName()).isEmpty() ? "服务器-" + address : clean(raw.serverName());
            if (name.length() > 120) profileErrors.add("名称不能超过120个字符");
            String port = clean(raw.sshPort()).isEmpty() ? String.valueOf(SupportServer.DEFAULT_SSH_PORT) : clean(raw.sshPort());
            try
            {
                int value = Integer.parseInt(port);
                if (value < 1 || value > 65535) throw new NumberFormatException();
                port = String.valueOf(value);
            }
            catch (NumberFormatException invalid) { profileErrors.add("SSH端口须为1-65535的整数"); }
            if (clean(raw.osType()).length() > 64) profileErrors.add("操作系统不能超过64个字符");
            String status = normalizeStatus(raw.status());
            if (!List.of("0", "1").contains(status)) profileErrors.add("运行状态须为正常或停用");
            String username = clean(raw.otherUsername());
            if (username.length() > 128) profileErrors.add("账号不能超过128个字符");
            if (List.of("hik", "root").contains(username.toLowerCase(Locale.ROOT))) profileErrors.add("hik/root密码请填写到对应密码列");
            if (!username.isEmpty() && clean(raw.otherPassword()).isEmpty()) profileErrors.add("其他账号缺少密码");
            if (username.isEmpty() && !clean(raw.otherPassword()).isEmpty()) profileErrors.add("其他密码缺少账号");
            for (String password : new String[] {raw.hikPassword(), raw.rootPassword(), raw.otherPassword()})
                if (password != null && password.length() > 128) { profileErrors.add("密码不能超过128个字符"); break; }
            Row normalized = new Row(raw.rowNumber() == null ? index + 1 : raw.rowNumber(), name, address, port,
                clean(raw.osType()), raw.hikPassword(), raw.rootPassword(), username, raw.otherPassword(), status);
            List<SupportServer> matches = existing.getOrDefault(address, List.of());
            if (matches.size() > 1) errors.add("数据库存在多个同地址设备，请先整理重复资产");
            Long existingId = matches.size() == 1 ? matches.get(0).getServerId() : null;
            // Existing rows only change ownership; their imported profile and credentials are never saved.
            if (existingId == null) errors.addAll(profileErrors);
            List<SupportEquipmentPlatformBindingVo> owners = bindings.getOrDefault(existingId, List.of());
            boolean bound = owners.stream().anyMatch(owner -> Objects.equals(owner.getPlatformId(), command.platformId()));
            String scope = owners.stream().map(owner -> clean(owner.getMainPlatformName()) + " / " + clean(owner.getPlatformName()))
                .distinct().collect(Collectors.joining("；"));
            if (existingId != null && scope.isEmpty()) scope = "未归属子平台";
            String state = !errors.isEmpty() ? "ERROR" : existingId == null ? "CREATE" : command.reuseExisting() && !bound ? "REUSE" : "SKIP";
            checked.add(new CheckedRow(normalized, state, errors, existingId, scope, bound));
        }
        return new Preview(checked, count(checked, "CREATE"), count(checked, "REUSE"), count(checked, "SKIP"), count(checked, "ERROR"));
    }

    @Transactional(rollbackFor = Exception.class)
    public Result commit(SupportEquipmentServerIntakeBo command)
    {
        if (command == null || command.rows() == null || !clean(command.addressText()).isEmpty())
            throw new ServiceException("请先校验并确认服务器清单");
        // All server inserts take the same site lock, including single-device intake.
        if (command.siteId() == null || siteMapper.selectSiteIdForUpdate(command.siteId()) == null)
            throw new ServiceException("现场不存在");
        Preview preview = preview(command);
        if (preview.errorCount() > 0) throw new ServiceException("清单仍有" + preview.errorCount() + "条错误，请重新校验");
        int created = 0, bound = 0, skipped = 0;
        for (CheckedRow checked : preview.rows())
        {
            if ("SKIP".equals(checked.state())) { skipped++; continue; }
            if ("REUSE".equals(checked.state()))
            {
                if (platformService.bindServer(command.platformId(), checked.existingServerId()) < 1)
                    throw new ServiceException("已有服务器归属保存失败");
                bound++;
                continue;
            }
            Row row = checked.data();
            SupportServer server = new SupportServer();
            server.setServerName(row.serverName());
            server.setServerAddress(row.serverAddress());
            server.setSshPort(Integer.valueOf(row.sshPort()));
            server.setOsType(row.osType());
            server.setHikPassword(row.hikPassword());
            server.setRootPassword(row.rootPassword());
            server.setOtherUsername(row.otherUsername());
            server.setOtherPassword(row.otherPassword());
            server.setStatus(row.status());
            SupportEquipmentCreateBo create = new SupportEquipmentCreateBo();
            create.setSiteId(command.siteId());
            create.setPlatformId(command.platformId());
            create.setServer(server);
            equipmentService.createEquipment(create);
            created++;
            bound++;
        }
        return new Result(created, bound, skipped);
    }

    public Preview previewFile(Long siteId, Long platformId, MultipartFile file) throws Exception
    {
        return preview(new SupportEquipmentServerIntakeBo(siteId, platformId, null, null, null, parseFile(file), false));
    }

    public List<Row> parseFile(MultipartFile file) throws Exception
    {
        if (file == null || file.isEmpty()) throw new ServiceException("请选择XLSX模板文件");
        if (file.getSize() > FILE_LIMIT) throw new ServiceException("模板文件不能超过5MB");
        if (!clean(file.getOriginalFilename()).toLowerCase(Locale.ROOT).endsWith(".xlsx"))
            throw new ServiceException("仅支持XLSX模板文件");
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream()))
        {
            if (!(workbook instanceof XSSFWorkbook) || workbook.getNumberOfSheets() == 0)
                throw new ServiceException("文件不是有效的XLSX工作簿");
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            for (int column = 0; column < HEADERS.length; column++)
                if (!HEADERS[column].equals(clean(cell(sheet.getRow(0), column, formatter))))
                    throw new ServiceException("第" + (column + 1) + "列表头应为：" + HEADERS[column]);
            if (sheet.getLastRowNum() > LIMIT) throw new ServiceException("单次最多导入512行，请删除多余空行或分批导入");
            List<Row> rows = new ArrayList<>();
            for (int index = 1; index <= sheet.getLastRowNum(); index++)
            {
                org.apache.poi.ss.usermodel.Row row = sheet.getRow(index);
                List<String> values = new ArrayList<>();
                for (int column = 0; column < HEADERS.length; column++) values.add(cell(row, column, formatter));
                if (values.stream().allMatch(value -> clean(value).isEmpty())) continue;
                String username = clean(values.get(4));
                String password = values.get(5);
                boolean hik = "hik".equalsIgnoreCase(username), root = "root".equalsIgnoreCase(username);
                rows.add(new Row(index + 1, values.get(0), values.get(1), values.get(2), values.get(3),
                    hik ? password : null, root ? password : null, hik || root ? null : username,
                    hik || root ? null : password, values.get(6)));
            }
            if (rows.isEmpty()) throw new ServiceException("模板中没有服务器数据");
            return rows;
        }
        catch (ServiceException invalid) { throw invalid; }
        catch (Exception invalid) { throw new ServiceException("无法读取XLSX，请检查文件是否损坏、加密或格式不正确"); }
    }

    public void exportTemplate(HttpServletResponse response) throws Exception
    {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        FileUtils.setAttachmentResponseHeader(response, "服务器导入模板.xlsx");
        try (Workbook workbook = new XSSFWorkbook())
        {
            Sheet sheet = workbook.createSheet("服务器导入模板");
            CellStyle header = workbook.createCellStyle();
            header.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font font = workbook.createFont(); font.setBold(true); font.setColor(IndexedColors.WHITE.getIndex()); header.setFont(font);
            org.apache.poi.ss.usermodel.Row titles = sheet.createRow(0);
            for (int column = 0; column < HEADERS.length; column++)
            {
                Cell cell = titles.createCell(column); cell.setCellValue(HEADERS[column]); cell.setCellStyle(header);
                sheet.setColumnWidth(column, column == 0 ? 7000 : 5200);
            }
            String[] example = {"应用服务器A", "10.10.10.21", String.valueOf(SupportServer.DEFAULT_SSH_PORT), "Linux", "", "", "正常"};
            org.apache.poi.ss.usermodel.Row row = sheet.createRow(1);
            for (int column = 0; column < example.length; column++) row.createCell(column).setCellValue(example[column]);
            row.getCell(2).setCellValue(SupportServer.DEFAULT_SSH_PORT);
            sheet.createFreezePane(0, 1);
            Sheet notes = workbook.createSheet("填写说明");
            notes.setColumnWidth(0, 24000);
            notes.createRow(0).createCell(0).setCellValue("请保留表头；SSH端口为空默认55555；运行状态为正常/停用；系统密码按明文读取；最多512行。");
            notes.createRow(1).createCell(0).setCellValue("请替换示例数据后导入。上传校验只生成确认清单，确认上传后才保存设备。");
            workbook.write(response.getOutputStream());
        }
    }

    private void requireContext(SupportEquipmentServerIntakeBo command)
    {
        if (command == null || command.siteId() == null || command.platformId() == null
            || siteMapper.selectSupportSiteBySiteId(command.siteId()) == null)
            throw new ServiceException("请选择现场和目标子平台");
        SupportPlatform platform = platformService.selectSupportPlatformByPlatformId(command.platformId());
        if (platform == null || !Objects.equals(platform.getSiteId(), command.siteId()) || !"SUB".equals(platform.getPlatformLevel()))
            throw new ServiceException("目标必须是当前现场的子平台");
    }

    private List<Row> resolveRows(SupportEquipmentServerIntakeBo command)
    {
        List<Row> rows = command.rows();
        if (rows != null)
        {
            if (!clean(command.addressText()).isEmpty()) throw new ServiceException("不能同时提交IP文本和确认清单");
        }
        else
        {
            Row defaults = command.defaults();
            if (defaults == null) throw new ServiceException("请填写批量参数");
            List<String> addresses = new ArrayList<>();
            for (String token : clean(command.addressText()).replaceAll("\\s*-\\s*", "-").split("[\\s,;，；]+"))
            {
                if (token.isEmpty()) continue;
                String[] range = token.split("-", -1);
                Long start = ipv4(range[0]);
                String endText = range.length == 2 && range[1].matches("\\d{1,3}")
                    ? range[0].substring(0, range[0].lastIndexOf('.') + 1) + range[1] : range.length == 2 ? range[1] : range[0];
                Long end = ipv4(endText);
                if (range.length > 2 || start == null || end == null || end < start || end - start + 1 > LIMIT)
                    throw new ServiceException("IP或IP段格式不正确，或超过512台：" + token);
                for (long address = start; address <= end; address++) addresses.add(formatAddress(address));
                if (addresses.size() > LIMIT) throw new ServiceException("单次最多添加512台服务器");
            }
            rows = new ArrayList<>();
            String prefix = clean(command.namePrefix()).isEmpty() ? "服务器" : clean(command.namePrefix());
            for (String address : addresses) rows.add(new Row(rows.size() + 1, prefix + "-" + address, address,
                defaults.sshPort(), defaults.osType(), defaults.hikPassword(), defaults.rootPassword(),
                defaults.otherUsername(), defaults.otherPassword(), defaults.status()));
        }
        if (rows.isEmpty() || rows.size() > LIMIT || rows.stream().anyMatch(Objects::isNull))
            throw new ServiceException("服务器清单必须包含1-512条有效记录");
        return rows;
    }

    private static String cell(org.apache.poi.ss.usermodel.Row row, int column, DataFormatter formatter)
    {
        return row == null ? "" : formatter.formatCellValue(row.getCell(column));
    }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static String normalizeStatus(String value)
    {
        String status = clean(value);
        return status.isEmpty() || "正常".equals(status) ? "0" : "停用".equals(status) ? "1" : status;
    }
    private static int count(List<CheckedRow> rows, String state) { return (int) rows.stream().filter(row -> state.equals(row.state())).count(); }
    private static String addressKey(String value)
    {
        Long address = ipv4(clean(value));
        return address == null ? clean(value).toLowerCase(Locale.ROOT) : formatAddress(address);
    }
    private static Long ipv4(String value)
    {
        String[] parts = clean(value).split("\\.", -1);
        if (parts.length != 4) return null;
        long result = 0;
        for (String part : parts)
        {
            if (!part.matches("\\d{1,3}")) return null;
            int number = Integer.parseInt(part);
            if (number > 255) return null;
            result = (result << 8) | number;
        }
        return result;
    }
    private static String formatAddress(long value)
    {
        return ((value >> 24) & 255) + "." + ((value >> 16) & 255) + "." + ((value >> 8) & 255) + "." + (value & 255);
    }
}
