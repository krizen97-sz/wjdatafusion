package com.hm.manage.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.hm.common.exception.ServiceException;
import com.hm.common.utils.DateUtils;
import com.hm.common.utils.SecurityUtils;
import com.hm.common.utils.StringUtils;
import com.hm.common.utils.file.FileUtils;
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
import com.hm.manage.service.ISupportEquipmentTopologyWorkbookService;
import com.hm.manage.util.SupportEquipmentLayoutUtils;

@Service
public class SupportEquipmentTopologyWorkbookServiceImpl implements ISupportEquipmentTopologyWorkbookService
{
    private static final long MAX_IMPORT_BYTES = 20L * 1024L * 1024L;
    private static final String SOURCE_SERVER = "SERVER";
    private static final String SOURCE_HARDWARE = "HARDWARE";
    private static final String SHEET_INSTRUCTION = "导入说明";
    private static final String SHEET_ROOM = "机房";
    private static final String SHEET_CABINET = "机柜";
    private static final String SHEET_PLACEMENT = "设备位置";
    private static final String SHEET_LINK = "设备链路";
    private static final String ROW_NUMBER = "__rowNumber";
    private static final String[] INSTRUCTION_HEADERS = {"项目", "内容"};
    private static final String[] ROOM_HEADERS = {"机房标识", "机房名称", "机房编码", "宽度(米)", "深度(米)", "状态", "备注"};
    private static final String[] CABINET_HEADERS = {"机柜标识", "机房标识", "机柜编号", "机柜U数", "X坐标(米)", "Z坐标(米)", "朝向角度", "状态", "备注"};
    private static final String[] PLACEMENT_HEADERS = {"设备来源", "设备ID", "设备名称", "设备IP", "机房标识", "机柜标识", "起始U位", "结束U位"};
    private static final String[] LINK_HEADERS = {"处理方式", "链路ID", "源设备来源", "源设备ID", "源设备名称", "目标设备来源", "目标设备ID", "目标设备名称", "连接介质", "端口数量", "设备端口", "交换机端口", "状态", "备注"};

    @Autowired
    private SupportSiteMapper siteMapper;

    @Autowired
    private SupportEquipmentLocationMapper locationMapper;

    @Autowired
    private SupportEquipmentTopologyMapper topologyMapper;

    @Autowired
    private ISupportEquipmentTopologyService topologyService;

    @Autowired
    private ISupportChangeLogService changeLogService;

    @Override
    public void exportWorkbook(HttpServletResponse response, Long siteId) throws Exception
    {
        SupportSite site = requireSite(siteId);
        Map<String, Object> topology = topologyService.selectTopology(siteId);
        List<SupportEquipmentRoom> rooms = listValue(topology.get("rooms"));
        List<SupportEquipmentCabinet> cabinets = listValue(topology.get("cabinets"));
        List<SupportEquipmentTopologyDeviceVo> devices = listValue(topology.get("devices"));
        List<SupportEquipmentLink> links = listValue(topology.get("links"));

        List<Object[]> instructionRows = new ArrayList<>();
        instructionRows.add(row("模板版本", "v3.18.0"));
        instructionRows.add(row("现场", site.getSiteName()));
        instructionRows.add(row("现场ID", site.getSiteId()));
        instructionRows.add(row("导出时间", DateUtils.getTime()));
        instructionRows.add(row("用途", "本文件用于维护当前现场的机房、机柜、设备位置和设备链路，导入不会新建现场。"));
        instructionRows.add(row("新增规则", "新增机房或机柜时填写自定义唯一标识，例如 NEW-ROOM-1、NEW-CABINET-1；其他工作表使用同一标识引用。"));
        instructionRows.add(row("更新规则", "更新已有数据时保留导出的标识或ID；缺失行不代表删除，设备位置四项留空表示清空位置。"));
        instructionRows.add(row("链路删除", "只有设备链路工作表的处理方式明确填写“删除”时才删除链路。"));

        List<Object[]> roomRows = new ArrayList<>();
        Map<Long, String> roomKeys = new HashMap<>();
        for (SupportEquipmentRoom room : rooms)
        {
            String key = roomKey(room.getRoomId());
            roomKeys.put(room.getRoomId(), key);
            roomRows.add(row(key, room.getRoomName(), room.getRoomCode(), room.getRoomWidth(), room.getRoomDepth(),
                statusLabel(room.getStatus()), room.getRemark()));
        }

        List<Object[]> cabinetRows = new ArrayList<>();
        Map<Long, String> cabinetKeys = new HashMap<>();
        for (SupportEquipmentCabinet cabinet : cabinets)
        {
            String key = cabinetKey(cabinet.getCabinetId());
            cabinetKeys.put(cabinet.getCabinetId(), key);
            cabinetRows.add(row(key, roomKeys.get(cabinet.getRoomId()), cabinet.getCabinetNo(), cabinet.getUCapacity(),
                cabinet.getPositionX(), cabinet.getPositionZ(), cabinet.getRotationY(), statusLabel(cabinet.getStatus()), cabinet.getRemark()));
        }

        List<Object[]> placementRows = new ArrayList<>();
        for (SupportEquipmentTopologyDeviceVo device : devices)
        {
            placementRows.add(row(device.getSourceType(), device.getSourceId(), device.getAssetName(), device.getIpAddress(),
                roomKeys.get(device.getRoomId()), cabinetKeys.get(device.getCabinetId()), device.getRackUStart(), device.getRackUEnd()));
        }

        List<Object[]> linkRows = new ArrayList<>();
        for (SupportEquipmentLink link : links)
        {
            linkRows.add(row("保存", link.getLinkId(), link.getSourceType(), link.getSourceId(), link.getSourceName(),
                link.getTargetType(), link.getTargetId(), link.getTargetName(), link.getMediumType(), link.getPortCount(),
                link.getSourcePort(), link.getTargetPort(), statusLabel(link.getStatus()), link.getRemark()));
        }

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        FileUtils.setAttachmentResponseHeader(response,
            "机房设备布局_" + safeFilename(site.getSiteName()) + "_" + DateUtils.dateTimeNow("yyyyMMddHHmmss") + ".xlsx");
        try (Workbook workbook = new XSSFWorkbook())
        {
            writeSheet(workbook, SHEET_INSTRUCTION, INSTRUCTION_HEADERS, instructionRows, false);
            writeSheet(workbook, SHEET_ROOM, ROOM_HEADERS, roomRows, true);
            writeSheet(workbook, SHEET_CABINET, CABINET_HEADERS, cabinetRows, true);
            writeSheet(workbook, SHEET_PLACEMENT, PLACEMENT_HEADERS, placementRows, true);
            writeSheet(workbook, SHEET_LINK, LINK_HEADERS, linkRows, true);
            workbook.write(response.getOutputStream());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importWorkbook(Long siteId, MultipartFile file) throws Exception
    {
        SupportSite site = requireSite(siteId);
        validateImportFile(file);
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream()))
        {
            List<Map<String, String>> roomRows = readRows(workbook, SHEET_ROOM, ROOM_HEADERS);
            List<Map<String, String>> cabinetRows = readRows(workbook, SHEET_CABINET, CABINET_HEADERS);
            List<Map<String, String>> placementRows = readRows(workbook, SHEET_PLACEMENT, PLACEMENT_HEADERS);
            List<Map<String, String>> linkRows = readRows(workbook, SHEET_LINK, LINK_HEADERS);

            ImportContext context = new ImportContext(site, SecurityUtils.getUsername(), DateUtils.getNowDate());
            importRooms(roomRows, context);
            importCabinets(cabinetRows, context);
            validateFinalRoomLayouts(context.site.getSiteId());
            importPlacements(placementRows, context);
            importLinks(linkRows, context);
            recordImportSummary(context, file.getOriginalFilename());
            return context.result();
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new ServiceException("机房布局Excel解析失败：" + StringUtils.defaultIfBlank(e.getMessage(), "请检查文件格式"));
        }
    }

    private void importRooms(List<Map<String, String>> rows, ImportContext context)
    {
        List<SupportEquipmentRoom> current = locationMapper.selectRoomsBySiteId(context.site.getSiteId());
        Map<Long, SupportEquipmentRoom> byId = new LinkedHashMap<>();
        Map<String, SupportEquipmentRoom> byName = new LinkedHashMap<>();
        for (SupportEquipmentRoom room : current)
        {
            byId.put(room.getRoomId(), room);
            byName.put(normalizeKey(room.getRoomName()), room);
        }
        Set<String> seenKeys = new HashSet<>();
        Set<Long> touchedIds = new HashSet<>();
        for (Map<String, String> values : rows)
        {
            String key = required(values, "机房标识", SHEET_ROOM);
            if (!seenKeys.add(normalizeKey(key)))
            {
                throw rowError(values, SHEET_ROOM, "机房标识重复：" + key);
            }
            String name = required(values, "机房名称", SHEET_ROOM);
            SupportEquipmentRoom original = resolveExistingRoom(key, name, byId, byName, values);
            if (original != null && !touchedIds.add(original.getRoomId()))
            {
                throw rowError(values, SHEET_ROOM, "同一机房不能使用多个标识重复维护：" + original.getRoomName());
            }
            SupportEquipmentRoom room = new SupportEquipmentRoom();
            room.setRoomId(original == null ? null : original.getRoomId());
            room.setSiteId(context.site.getSiteId());
            room.setRoomName(name);
            room.setRoomCode(value(values, "机房编码"));
            room.setRoomWidth(decimal(values, "宽度(米)", original == null ? new BigDecimal("12.00") : original.getRoomWidth(), SHEET_ROOM));
            room.setRoomDepth(decimal(values, "深度(米)", original == null ? new BigDecimal("8.00") : original.getRoomDepth(), SHEET_ROOM));
            validateRoomSize(room, values);
            room.setStatus(status(value(values, "状态"), original == null ? "0" : original.getStatus(), values, SHEET_ROOM));
            room.setRemark(value(values, "备注"));
            SupportEquipmentRoom duplicate = byName.get(normalizeKey(name));
            if (duplicate != null && (original == null || !duplicate.getRoomId().equals(original.getRoomId())))
            {
                throw rowError(values, SHEET_ROOM, "机房名称已存在：" + name);
            }

            if (original == null)
            {
                room.setCreateBy(context.username);
                room.setCreateTime(context.now);
                locationMapper.insertRoom(room);
                context.roomsAdded++;
                changeLogService.record(context.site.getSiteId(), "INSERT", "EQUIPMENT_ROOM", room.getRoomId(), room.getRoomName(),
                    "Excel新增机房 " + room.getRoomName(), null, room);
                current.add(room);
                byId.put(room.getRoomId(), room);
                byName.put(normalizeKey(room.getRoomName()), room);
                touchedIds.add(room.getRoomId());
            }
            else
            {
                if (!sameRoom(original, room))
                {
                    room.setUpdateBy(context.username);
                    room.setUpdateTime(context.now);
                    locationMapper.updateRoom(room);
                    if (!Objects.equals(original.getRoomName(), room.getRoomName()))
                    {
                        locationMapper.updateHardwareRoomName(context.site.getSiteId(), original.getRoomName(), room.getRoomName());
                        locationMapper.updateServerRoomName(context.site.getSiteId(), original.getRoomName(), room.getRoomName());
                        byName.remove(normalizeKey(original.getRoomName()));
                    }
                    context.roomsUpdated++;
                    changeLogService.record(context.site.getSiteId(), "UPDATE", "EQUIPMENT_ROOM", room.getRoomId(), room.getRoomName(),
                        "Excel修改机房 " + room.getRoomName(), original, room);
                    byId.put(room.getRoomId(), room);
                    byName.put(normalizeKey(room.getRoomName()), room);
                }
                else
                {
                    room = original;
                }
            }
            context.roomKeys.put(normalizeKey(key), room);
            context.roomKeys.put(normalizeKey(roomKey(room.getRoomId())), room);
        }
    }

    private void importCabinets(List<Map<String, String>> rows, ImportContext context)
    {
        List<SupportEquipmentCabinet> current = locationMapper.selectCabinetsBySiteId(context.site.getSiteId());
        Map<Long, SupportEquipmentCabinet> byId = new LinkedHashMap<>();
        for (SupportEquipmentCabinet cabinet : current)
        {
            byId.put(cabinet.getCabinetId(), cabinet);
        }
        Set<String> seenKeys = new HashSet<>();
        Set<Long> touchedIds = new HashSet<>();
        for (Map<String, String> values : rows)
        {
            String key = required(values, "机柜标识", SHEET_CABINET);
            if (!seenKeys.add(normalizeKey(key)))
            {
                throw rowError(values, SHEET_CABINET, "机柜标识重复：" + key);
            }
            SupportEquipmentRoom room = resolveRoomReference(required(values, "机房标识", SHEET_CABINET), context, values, SHEET_CABINET);
            String cabinetNo = required(values, "机柜编号", SHEET_CABINET);
            SupportEquipmentCabinet original = resolveExistingCabinet(key, room, cabinetNo, byId, current, values);
            if (original != null && !touchedIds.add(original.getCabinetId()))
            {
                throw rowError(values, SHEET_CABINET, "同一机柜不能使用多个标识重复维护：" + original.getCabinetNo());
            }
            if (original != null && !original.getRoomId().equals(room.getRoomId()))
            {
                throw rowError(values, SHEET_CABINET, "已有机柜不支持跨机房移动，请在图中新增机柜后再调整设备位置");
            }
            SupportEquipmentCabinet cabinet = new SupportEquipmentCabinet();
            cabinet.setCabinetId(original == null ? null : original.getCabinetId());
            cabinet.setRoomId(room.getRoomId());
            cabinet.setSiteId(context.site.getSiteId());
            cabinet.setCabinetNo(cabinetNo);
            cabinet.setUCapacity(integer(values, "机柜U数", original == null ? 45 : original.getUCapacity(), SHEET_CABINET));
            if (cabinet.getUCapacity() < 1 || cabinet.getUCapacity() > 45)
            {
                throw rowError(values, SHEET_CABINET, "机柜U数必须在1到45之间");
            }
            cabinet.setPositionX(decimal(values, "X坐标(米)", original == null ? null : original.getPositionX(), SHEET_CABINET));
            cabinet.setPositionZ(decimal(values, "Z坐标(米)", original == null ? null : original.getPositionZ(), SHEET_CABINET));
            cabinet.setRotationY(decimal(values, "朝向角度", original == null ? BigDecimal.ZERO : original.getRotationY(), SHEET_CABINET));
            cabinet.setStatus(status(value(values, "状态"), original == null ? "0" : original.getStatus(), values, SHEET_CABINET));
            cabinet.setRemark(value(values, "备注"));

            SupportEquipmentCabinet duplicate = findCabinet(current, room.getRoomId(), cabinetNo, original == null ? null : original.getCabinetId());
            if (duplicate != null)
            {
                throw rowError(values, SHEET_CABINET, "当前机房已存在机柜编号：" + cabinetNo);
            }
            if (original != null && locationMapper.countRackPlacementsAboveCapacity(context.site.getSiteId(), room.getRoomName(),
                original.getCabinetNo(), cabinet.getUCapacity()) > 0)
            {
                throw rowError(values, SHEET_CABINET, "机柜内存在超出新容量的设备");
            }
            normalizeCabinetLayout(cabinet, room, current, values);

            if (original == null)
            {
                cabinet.setCreateBy(context.username);
                cabinet.setCreateTime(context.now);
                locationMapper.insertCabinet(cabinet);
                context.cabinetsAdded++;
                changeLogService.record(context.site.getSiteId(), "INSERT", "EQUIPMENT_CABINET", cabinet.getCabinetId(), cabinet.getCabinetNo(),
                    "Excel新增机柜 " + cabinet.getCabinetNo(), null, cabinet);
                current.add(cabinet);
                byId.put(cabinet.getCabinetId(), cabinet);
                touchedIds.add(cabinet.getCabinetId());
            }
            else
            {
                if (!sameCabinet(original, cabinet))
                {
                    cabinet.setUpdateBy(context.username);
                    cabinet.setUpdateTime(context.now);
                    locationMapper.updateCabinet(cabinet);
                    if (!Objects.equals(original.getCabinetNo(), cabinet.getCabinetNo()))
                    {
                        locationMapper.updateHardwareCabinetNo(context.site.getSiteId(), room.getRoomName(), original.getCabinetNo(), cabinet.getCabinetNo());
                        locationMapper.updateServerCabinetNo(context.site.getSiteId(), room.getRoomName(), original.getCabinetNo(), cabinet.getCabinetNo());
                    }
                    context.cabinetsUpdated++;
                    changeLogService.record(context.site.getSiteId(), "UPDATE", "EQUIPMENT_CABINET", cabinet.getCabinetId(), cabinet.getCabinetNo(),
                        "Excel修改机柜 " + cabinet.getCabinetNo(), original, cabinet);
                    replaceCabinet(current, cabinet);
                    byId.put(cabinet.getCabinetId(), cabinet);
                }
                else
                {
                    cabinet = original;
                }
            }
            context.cabinetKeys.put(normalizeKey(key), cabinet);
            context.cabinetKeys.put(normalizeKey(cabinetKey(cabinet.getCabinetId())), cabinet);
        }
    }

    private void importPlacements(List<Map<String, String>> rows, ImportContext context)
    {
        Map<String, Object> topology = topologyService.selectTopology(context.site.getSiteId());
        List<SupportEquipmentTopologyDeviceVo> devices = listValue(topology.get("devices"));
        Map<String, SupportEquipmentTopologyDeviceVo> byKey = new LinkedHashMap<>();
        Map<String, PlacementState> desired = new LinkedHashMap<>();
        for (SupportEquipmentTopologyDeviceVo device : devices)
        {
            String key = deviceKey(device.getSourceType(), device.getSourceId());
            byKey.put(key, device);
            SupportEquipmentRoom room = device.getRoomId() == null ? null : locationMapper.selectRoomByRoomId(device.getRoomId());
            SupportEquipmentCabinet cabinet = device.getCabinetId() == null ? null : locationMapper.selectCabinetByCabinetId(device.getCabinetId());
            desired.put(key, new PlacementState(device, room, cabinet, device.getRackUStart(), device.getRackUEnd(), null));
        }
        Set<String> seenDevices = new HashSet<>();
        for (Map<String, String> values : rows)
        {
            String sourceType = sourceType(required(values, "设备来源", SHEET_PLACEMENT), values, SHEET_PLACEMENT);
            Long sourceId = longValue(required(values, "设备ID", SHEET_PLACEMENT), values, SHEET_PLACEMENT, "设备ID");
            String key = deviceKey(sourceType, sourceId);
            if (!seenDevices.add(key))
            {
                throw rowError(values, SHEET_PLACEMENT, "设备重复：" + key);
            }
            SupportEquipmentTopologyDeviceVo device = byKey.get(key);
            if (device == null)
            {
                throw rowError(values, SHEET_PLACEMENT, "当前现场不存在设备：" + key);
            }
            String roomRef = value(values, "机房标识");
            String cabinetRef = value(values, "机柜标识");
            String startText = value(values, "起始U位");
            String endText = value(values, "结束U位");
            boolean clear = StringUtils.isAllBlank(roomRef, cabinetRef, startText, endText);
            if (!clear && (StringUtils.isAnyBlank(roomRef, cabinetRef, startText, endText)))
            {
                throw rowError(values, SHEET_PLACEMENT, "机房、机柜、起始U位和结束U位需要同时填写，或全部留空以清空位置");
            }
            SupportEquipmentRoom room = null;
            SupportEquipmentCabinet cabinet = null;
            Integer rackUStart = null;
            Integer rackUEnd = null;
            if (!clear)
            {
                room = resolveRoomReference(roomRef, context, values, SHEET_PLACEMENT);
                cabinet = resolveCabinetReference(cabinetRef, context, values, SHEET_PLACEMENT);
                if (!room.getRoomId().equals(cabinet.getRoomId()))
                {
                    throw rowError(values, SHEET_PLACEMENT, "设备引用的机柜不属于所选机房");
                }
                rackUStart = integer(values, "起始U位", null, SHEET_PLACEMENT);
                rackUEnd = integer(values, "结束U位", null, SHEET_PLACEMENT);
            }
            desired.put(key, new PlacementState(device, room, cabinet, rackUStart, rackUEnd, values));
        }

        validateFinalPlacements(desired);
        for (String key : seenDevices)
        {
            PlacementState state = desired.get(key);
            SupportEquipmentTopologyDeviceVo device = state.device;
            Long roomId = state.room == null ? null : state.room.getRoomId();
            Long cabinetId = state.cabinet == null ? null : state.cabinet.getCabinetId();
            if (Objects.equals(device.getRoomId(), roomId) && Objects.equals(device.getCabinetId(), cabinetId)
                && Objects.equals(device.getRackUStart(), state.rackUStart) && Objects.equals(device.getRackUEnd(), state.rackUEnd))
            {
                continue;
            }
            String roomName = state.room == null ? null : state.room.getRoomName();
            String cabinetNo = state.cabinet == null ? null : state.cabinet.getCabinetNo();
            int changed = SOURCE_SERVER.equals(device.getSourceType())
                ? locationMapper.updateServerPlacement(device.getSourceId(), roomName, cabinetNo, state.rackUStart, state.rackUEnd,
                    context.username, context.now)
                : locationMapper.updateHardwarePlacement(device.getSourceId(), roomName, cabinetNo, state.rackUStart, state.rackUEnd,
                    context.username, context.now);
            if (changed > 0)
            {
                boolean clear = state.room == null;
                if (clear) context.placementsCleared++;
                else context.placementsUpdated++;
                changeLogService.record(context.site.getSiteId(), "UPDATE",
                    SOURCE_SERVER.equals(device.getSourceType()) ? "SERVER" : "HARDWARE_ASSET", device.getSourceId(), device.getAssetName(),
                    (clear ? "Excel清空" : "Excel调整") + "设备安装位置：" + device.getAssetName(),
                    locationDetail(device.getEquipmentRoom(), device.getCabinetNo(), device.getRackUStart(), device.getRackUEnd()),
                    locationDetail(roomName, cabinetNo, state.rackUStart, state.rackUEnd));
            }
        }
    }

    private void validateFinalPlacements(Map<String, PlacementState> desired)
    {
        Map<Long, Map<Integer, PlacementState>> occupied = new LinkedHashMap<>();
        for (PlacementState state : desired.values())
        {
            if (state.room == null && state.cabinet == null && state.rackUStart == null && state.rackUEnd == null)
            {
                continue;
            }
            Map<String, String> values = state.row == null ? new LinkedHashMap<>() : state.row;
            if (state.room == null || state.cabinet == null || state.rackUStart == null || state.rackUEnd == null)
            {
                throw rowError(values, SHEET_PLACEMENT, "设备位置数据不完整：" + state.device.getAssetName());
            }
            int capacity = state.cabinet.getUCapacity() == null ? 45 : state.cabinet.getUCapacity();
            if (state.rackUStart < 1 || state.rackUEnd < state.rackUStart || state.rackUEnd > capacity)
            {
                throw rowError(values, SHEET_PLACEMENT, state.device.getAssetName() + "的U位必须在1到" + capacity + "之间");
            }
            Map<Integer, PlacementState> cabinetSlots = occupied.computeIfAbsent(state.cabinet.getCabinetId(), ignored -> new LinkedHashMap<>());
            for (int u = state.rackUStart; u <= state.rackUEnd; u++)
            {
                PlacementState owner = cabinetSlots.putIfAbsent(u, state);
                if (owner != null)
                {
                    Map<String, String> errorRow = state.row != null ? state.row : owner.row;
                    throw rowError(errorRow == null ? new LinkedHashMap<>() : errorRow, SHEET_PLACEMENT,
                        state.device.getAssetName() + "与" + owner.device.getAssetName() + "在" + u + "U发生冲突");
                }
            }
        }
    }

    private void importLinks(List<Map<String, String>> rows, ImportContext context)
    {
        Map<Long, SupportEquipmentLink> current = new LinkedHashMap<>();
        for (SupportEquipmentLink link : topologyMapper.selectLinksBySiteId(context.site.getSiteId()))
        {
            current.put(link.getLinkId(), link);
        }
        Set<Long> seenIds = new HashSet<>();
        for (Map<String, String> values : rows)
        {
            String operation = StringUtils.defaultIfBlank(value(values, "处理方式"), "保存");
            String linkIdText = value(values, "链路ID");
            Long linkId = StringUtils.isBlank(linkIdText) ? null : longValue(linkIdText, values, SHEET_LINK, "链路ID");
            if (linkId != null && !seenIds.add(linkId))
            {
                throw rowError(values, SHEET_LINK, "链路ID重复：" + linkId);
            }
            if ("删除".equals(operation) || "DELETE".equalsIgnoreCase(operation))
            {
                SupportEquipmentLink original = requireCurrentLink(linkId, current, values);
                topologyService.deleteLink(original.getLinkId());
                current.remove(original.getLinkId());
                context.linksDeleted++;
                continue;
            }

            SupportEquipmentLink link = new SupportEquipmentLink();
            link.setLinkId(linkId);
            link.setSiteId(context.site.getSiteId());
            link.setSourceType(sourceType(required(values, "源设备来源", SHEET_LINK), values, SHEET_LINK));
            link.setSourceId(longValue(required(values, "源设备ID", SHEET_LINK), values, SHEET_LINK, "源设备ID"));
            link.setTargetType(sourceType(required(values, "目标设备来源", SHEET_LINK), values, SHEET_LINK));
            link.setTargetId(longValue(required(values, "目标设备ID", SHEET_LINK), values, SHEET_LINK, "目标设备ID"));
            link.setMediumType(required(values, "连接介质", SHEET_LINK).toUpperCase(Locale.ROOT));
            link.setPortCount(integer(values, "端口数量", 1, SHEET_LINK));
            link.setSourcePort(value(values, "设备端口"));
            link.setTargetPort(value(values, "交换机端口"));
            link.setStatus(status(value(values, "状态"), "0", values, SHEET_LINK));
            link.setRemark(value(values, "备注"));
            if (linkId == null)
            {
                topologyService.insertLink(link);
                current.put(link.getLinkId(), link);
                context.linksAdded++;
            }
            else
            {
                SupportEquipmentLink original = requireCurrentLink(linkId, current, values);
                if (!sameLink(original, link))
                {
                    topologyService.updateLink(link);
                    current.put(linkId, link);
                    context.linksUpdated++;
                }
            }
        }
    }

    private SupportEquipmentRoom resolveExistingRoom(String key, String name, Map<Long, SupportEquipmentRoom> byId,
        Map<String, SupportEquipmentRoom> byName, Map<String, String> values)
    {
        Long id = prefixedId(key, "ROOM-");
        if (id != null)
        {
            SupportEquipmentRoom room = byId.get(id);
            if (room == null)
            {
                throw rowError(values, SHEET_ROOM, "机房标识不属于当前现场：" + key);
            }
            return room;
        }
        return byName.get(normalizeKey(name));
    }

    private SupportEquipmentCabinet resolveExistingCabinet(String key, SupportEquipmentRoom room, String cabinetNo,
        Map<Long, SupportEquipmentCabinet> byId, List<SupportEquipmentCabinet> current, Map<String, String> values)
    {
        Long id = prefixedId(key, "CABINET-");
        if (id != null)
        {
            SupportEquipmentCabinet cabinet = byId.get(id);
            if (cabinet == null)
            {
                throw rowError(values, SHEET_CABINET, "机柜标识不属于当前现场：" + key);
            }
            return cabinet;
        }
        return findCabinet(current, room.getRoomId(), cabinetNo, null);
    }

    private SupportEquipmentRoom resolveRoomReference(String key, ImportContext context, Map<String, String> values, String sheet)
    {
        SupportEquipmentRoom room = context.roomKeys.get(normalizeKey(key));
        if (room != null)
        {
            return room;
        }
        Long id = prefixedId(key, "ROOM-");
        if (id != null)
        {
            room = locationMapper.selectRoomByRoomId(id);
            if (room != null && context.site.getSiteId().equals(room.getSiteId()))
            {
                context.roomKeys.put(normalizeKey(key), room);
                return room;
            }
        }
        throw rowError(values, sheet, "引用了不存在的机房标识：" + key);
    }

    private SupportEquipmentCabinet resolveCabinetReference(String key, ImportContext context, Map<String, String> values, String sheet)
    {
        SupportEquipmentCabinet cabinet = context.cabinetKeys.get(normalizeKey(key));
        if (cabinet != null)
        {
            return cabinet;
        }
        Long id = prefixedId(key, "CABINET-");
        if (id != null)
        {
            cabinet = locationMapper.selectCabinetByCabinetId(id);
            if (cabinet != null && context.site.getSiteId().equals(cabinet.getSiteId()))
            {
                context.cabinetKeys.put(normalizeKey(key), cabinet);
                return cabinet;
            }
        }
        throw rowError(values, sheet, "引用了不存在的机柜标识：" + key);
    }

    private void validateRoomSize(SupportEquipmentRoom room, Map<String, String> values)
    {
        BigDecimal minimum = new BigDecimal("2.00");
        BigDecimal maximum = new BigDecimal("100.00");
        if (room.getRoomWidth() == null || room.getRoomDepth() == null
            || room.getRoomWidth().compareTo(minimum) < 0 || room.getRoomWidth().compareTo(maximum) > 0
            || room.getRoomDepth().compareTo(minimum) < 0 || room.getRoomDepth().compareTo(maximum) > 0)
        {
            throw rowError(values, SHEET_ROOM, "机房长宽必须在2到100米之间");
        }
        room.setRoomWidth(room.getRoomWidth().setScale(2, RoundingMode.HALF_UP));
        room.setRoomDepth(room.getRoomDepth().setScale(2, RoundingMode.HALF_UP));
    }

    private void validateFinalRoomLayouts(Long siteId)
    {
        List<SupportEquipmentCabinet> cabinets = locationMapper.selectCabinetsBySiteId(siteId);
        for (SupportEquipmentRoom room : locationMapper.selectRoomsBySiteId(siteId))
        {
            List<SupportEquipmentCabinet> roomCabinets = new ArrayList<>();
            for (SupportEquipmentCabinet cabinet : cabinets)
            {
                if (room.getRoomId().equals(cabinet.getRoomId()))
                {
                    roomCabinets.add(copyCabinet(cabinet));
                }
            }
            for (int index = 0; index < roomCabinets.size(); index++)
            {
                SupportEquipmentCabinet cabinet = roomCabinets.get(index);
                try
                {
                    SupportEquipmentLayoutUtils.applyDefaultLayout(cabinet, index, room);
                }
                catch (ServiceException e)
                {
                    throw new ServiceException("机房" + room.getRoomName() + "中的机柜" + cabinet.getCabinetNo() + "超出地板边界");
                }
                SupportEquipmentCabinet collision = SupportEquipmentLayoutUtils.findCollision(cabinet, roomCabinets, room);
                if (collision != null)
                {
                    throw new ServiceException("机房" + room.getRoomName() + "中的机柜" + cabinet.getCabinetNo()
                        + "与" + collision.getCabinetNo() + "发生重叠");
                }
            }
        }
    }

    private void normalizeCabinetLayout(SupportEquipmentCabinet cabinet, SupportEquipmentRoom room,
        List<SupportEquipmentCabinet> current, Map<String, String> values)
    {
        try
        {
            List<SupportEquipmentCabinet> roomCabinets = new ArrayList<>();
            for (SupportEquipmentCabinet item : current)
            {
                if (room.getRoomId().equals(item.getRoomId()))
                {
                    roomCabinets.add(copyCabinet(item));
                }
            }
            int index = Math.max(0, roomCabinets.size());
            SupportEquipmentLayoutUtils.applyDefaultLayout(cabinet, index, room);
        }
        catch (ServiceException e)
        {
            if (e.getMessage() != null && e.getMessage().startsWith(SHEET_CABINET))
            {
                throw e;
            }
            throw rowError(values, SHEET_CABINET, e.getMessage());
        }
    }

    private void recordImportSummary(ImportContext context, String filename)
    {
        if (context.totalChanges() == 0)
        {
            return;
        }
        Map<String, Object> detail = context.result();
        detail.put("导入文件", filename);
        changeLogService.record(context.site.getSiteId(), "UPDATE", "EQUIPMENT_TOPOLOGY", context.site.getSiteId(),
            context.site.getSiteName(), "Excel导入并更新机房设备布局", null, detail);
    }

    private SupportEquipmentLink requireCurrentLink(Long linkId, Map<Long, SupportEquipmentLink> current, Map<String, String> values)
    {
        if (linkId == null || !current.containsKey(linkId))
        {
            throw rowError(values, SHEET_LINK, "链路ID不存在或不属于当前现场：" + linkId);
        }
        return current.get(linkId);
    }

    private void validateImportFile(MultipartFile file)
    {
        if (file == null || file.isEmpty())
        {
            throw new ServiceException("请选择需要导入的xlsx文件");
        }
        String filename = StringUtils.defaultString(file.getOriginalFilename()).toLowerCase(Locale.ROOT);
        if (!filename.endsWith(".xlsx"))
        {
            throw new ServiceException("机房布局导入仅支持xlsx格式");
        }
        if (file.getSize() > MAX_IMPORT_BYTES)
        {
            throw new ServiceException("导入文件不能超过20MB");
        }
    }

    private List<Map<String, String>> readRows(Workbook workbook, String sheetName, String[] requiredHeaders)
    {
        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet == null)
        {
            throw new ServiceException("导入文件缺少工作表：" + sheetName);
        }
        Row headerRow = sheet.getRow(0);
        if (headerRow == null)
        {
            throw new ServiceException("工作表" + sheetName + "缺少表头");
        }
        DataFormatter formatter = new DataFormatter();
        List<String> headers = new ArrayList<>();
        for (int index = 0; index < headerRow.getLastCellNum(); index++)
        {
            headers.add(StringUtils.trimToEmpty(formatter.formatCellValue(headerRow.getCell(index))));
        }
        for (String requiredHeader : requiredHeaders)
        {
            if (!headers.contains(requiredHeader))
            {
                throw new ServiceException("工作表" + sheetName + "缺少列：" + requiredHeader);
            }
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
                values.put(ROW_NUMBER, String.valueOf(rowIndex + 1));
                rows.add(values);
            }
        }
        return rows;
    }

    private void writeSheet(Workbook workbook, String sheetName, String[] headers, List<Object[]> rows, boolean filter)
    {
        Sheet sheet = workbook.createSheet(sheetName);
        CellStyle headerStyle = buildHeaderStyle(workbook);
        CellStyle bodyStyle = buildBodyStyle(workbook);
        Row headerRow = sheet.createRow(0);
        headerRow.setHeightInPoints(24);
        for (int index = 0; index < headers.length; index++)
        {
            Cell cell = headerRow.createCell(index);
            cell.setCellValue(headers[index]);
            cell.setCellStyle(headerStyle);
        }
        int[] widths = new int[headers.length];
        for (int index = 0; index < headers.length; index++)
        {
            widths[index] = headers[index].length();
        }
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++)
        {
            Row dataRow = sheet.createRow(rowIndex + 1);
            Object[] values = rows.get(rowIndex);
            for (int cellIndex = 0; cellIndex < headers.length; cellIndex++)
            {
                Object value = cellIndex < values.length ? values[cellIndex] : null;
                Cell cell = dataRow.createCell(cellIndex);
                setCellValue(cell, value);
                cell.setCellStyle(bodyStyle);
                widths[cellIndex] = Math.max(widths[cellIndex], String.valueOf(value == null ? "" : value).length());
            }
        }
        for (int index = 0; index < headers.length; index++)
        {
            sheet.setColumnWidth(index, Math.min(Math.max(widths[index] * 512 + 1024, 3200), 14000));
        }
        sheet.createFreezePane(0, 1);
        sheet.setAutobreaks(true);
        sheet.setFitToPage(true);
        PrintSetup printSetup = sheet.getPrintSetup();
        printSetup.setLandscape(headers.length > 7);
        printSetup.setFitWidth((short) 1);
        printSetup.setFitHeight((short) 0);
        sheet.setMargin(Sheet.LeftMargin, 0.3D);
        sheet.setMargin(Sheet.RightMargin, 0.3D);
        sheet.setMargin(Sheet.TopMargin, 0.4D);
        sheet.setMargin(Sheet.BottomMargin, 0.4D);
        if (filter && headers.length > 0)
        {
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, Math.max(0, rows.size()), 0, headers.length - 1));
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
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setFont(font);
        return style;
    }

    private CellStyle buildBodyStyle(Workbook workbook)
    {
        CellStyle style = workbook.createCellStyle();
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        return style;
    }

    private void setCellValue(Cell cell, Object value)
    {
        if (value == null)
        {
            cell.setBlank();
        }
        else if (value instanceof Number)
        {
            cell.setCellValue(((Number) value).doubleValue());
        }
        else
        {
            cell.setCellValue(String.valueOf(value));
        }
    }

    private SupportSite requireSite(Long siteId)
    {
        SupportSite site = siteId == null ? null : siteMapper.selectSupportSiteBySiteId(siteId);
        if (site == null)
        {
            throw new ServiceException("现场不存在");
        }
        return site;
    }

    private String required(Map<String, String> values, String field, String sheet)
    {
        String value = value(values, field);
        if (StringUtils.isBlank(value))
        {
            throw rowError(values, sheet, field + "不能为空");
        }
        return value;
    }

    private String value(Map<String, String> values, String field)
    {
        return StringUtils.trimToEmpty(values.get(field));
    }

    private Integer integer(Map<String, String> values, String field, Integer fallback, String sheet)
    {
        String text = value(values, field);
        if (StringUtils.isBlank(text))
        {
            return fallback;
        }
        try
        {
            return new BigDecimal(text).setScale(0, RoundingMode.UNNECESSARY).intValueExact();
        }
        catch (Exception e)
        {
            throw rowError(values, sheet, field + "必须是整数");
        }
    }

    private BigDecimal decimal(Map<String, String> values, String field, BigDecimal fallback, String sheet)
    {
        String text = value(values, field);
        if (StringUtils.isBlank(text))
        {
            return fallback;
        }
        try
        {
            return new BigDecimal(text);
        }
        catch (NumberFormatException e)
        {
            throw rowError(values, sheet, field + "必须是数字");
        }
    }

    private Long longValue(String text, Map<String, String> values, String sheet, String field)
    {
        try
        {
            return new BigDecimal(text).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        }
        catch (Exception e)
        {
            throw rowError(values, sheet, field + "格式不正确");
        }
    }

    private String status(String text, String fallback, Map<String, String> values, String sheet)
    {
        if (StringUtils.isBlank(text)) return StringUtils.defaultIfBlank(fallback, "0");
        if ("0".equals(text) || "正常".equals(text) || "启用".equals(text)) return "0";
        if ("1".equals(text) || "停用".equals(text) || "禁用".equals(text)) return "1";
        throw rowError(values, sheet, "状态只能填写正常、停用、0或1");
    }

    private String sourceType(String value, Map<String, String> values, String sheet)
    {
        String normalized = StringUtils.trimToEmpty(value).toUpperCase(Locale.ROOT);
        if (!SOURCE_SERVER.equals(normalized) && !SOURCE_HARDWARE.equals(normalized))
        {
            throw rowError(values, sheet, "设备来源只能填写SERVER或HARDWARE");
        }
        return normalized;
    }

    private ServiceException rowError(Map<String, String> values, String sheet, String message)
    {
        return new ServiceException(sheet + "第" + values.getOrDefault(ROW_NUMBER, "?") + "行：" + message);
    }

    private Long prefixedId(String value, String prefix)
    {
        if (value == null || !value.regionMatches(true, 0, prefix, 0, prefix.length()))
        {
            return null;
        }
        String id = value.substring(prefix.length());
        if (!id.matches("\\d+"))
        {
            return null;
        }
        return Long.valueOf(id);
    }

    private SupportEquipmentCabinet findCabinet(List<SupportEquipmentCabinet> cabinets, Long roomId, String cabinetNo, Long excludeId)
    {
        for (SupportEquipmentCabinet cabinet : cabinets)
        {
            if (roomId.equals(cabinet.getRoomId()) && normalizeKey(cabinetNo).equals(normalizeKey(cabinet.getCabinetNo()))
                && (excludeId == null || !excludeId.equals(cabinet.getCabinetId())))
            {
                return cabinet;
            }
        }
        return null;
    }

    private void replaceCabinet(List<SupportEquipmentCabinet> cabinets, SupportEquipmentCabinet replacement)
    {
        for (int index = 0; index < cabinets.size(); index++)
        {
            if (replacement.getCabinetId().equals(cabinets.get(index).getCabinetId()))
            {
                cabinets.set(index, replacement);
                return;
            }
        }
        cabinets.add(replacement);
    }

    private SupportEquipmentCabinet copyCabinet(SupportEquipmentCabinet source)
    {
        SupportEquipmentCabinet copy = new SupportEquipmentCabinet();
        copy.setCabinetId(source.getCabinetId());
        copy.setRoomId(source.getRoomId());
        copy.setSiteId(source.getSiteId());
        copy.setCabinetNo(source.getCabinetNo());
        copy.setUCapacity(source.getUCapacity());
        copy.setPositionX(source.getPositionX());
        copy.setPositionZ(source.getPositionZ());
        copy.setRotationY(source.getRotationY());
        copy.setStatus(source.getStatus());
        copy.setRemark(source.getRemark());
        return copy;
    }

    private boolean sameRoom(SupportEquipmentRoom left, SupportEquipmentRoom right)
    {
        return Objects.equals(left.getRoomName(), right.getRoomName()) && textEquals(left.getRoomCode(), right.getRoomCode())
            && numberEquals(left.getRoomWidth(), right.getRoomWidth()) && numberEquals(left.getRoomDepth(), right.getRoomDepth())
            && Objects.equals(left.getStatus(), right.getStatus()) && textEquals(left.getRemark(), right.getRemark());
    }

    private boolean sameCabinet(SupportEquipmentCabinet left, SupportEquipmentCabinet right)
    {
        return Objects.equals(left.getRoomId(), right.getRoomId()) && Objects.equals(left.getCabinetNo(), right.getCabinetNo())
            && Objects.equals(left.getUCapacity(), right.getUCapacity()) && numberEquals(left.getPositionX(), right.getPositionX())
            && numberEquals(left.getPositionZ(), right.getPositionZ()) && numberEquals(left.getRotationY(), right.getRotationY())
            && Objects.equals(left.getStatus(), right.getStatus()) && textEquals(left.getRemark(), right.getRemark());
    }

    private boolean sameLink(SupportEquipmentLink left, SupportEquipmentLink right)
    {
        return Objects.equals(left.getSourceType(), right.getSourceType()) && Objects.equals(left.getSourceId(), right.getSourceId())
            && Objects.equals(left.getTargetType(), right.getTargetType()) && Objects.equals(left.getTargetId(), right.getTargetId())
            && Objects.equals(left.getMediumType(), right.getMediumType()) && Objects.equals(left.getPortCount(), right.getPortCount())
            && Objects.equals(StringUtils.trimToEmpty(left.getSourcePort()), StringUtils.trimToEmpty(right.getSourcePort()))
            && Objects.equals(StringUtils.trimToEmpty(left.getTargetPort()), StringUtils.trimToEmpty(right.getTargetPort()))
            && Objects.equals(left.getStatus(), right.getStatus())
            && Objects.equals(StringUtils.trimToEmpty(left.getRemark()), StringUtils.trimToEmpty(right.getRemark()));
    }

    private boolean numberEquals(BigDecimal left, BigDecimal right)
    {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }

    private boolean textEquals(String left, String right)
    {
        return Objects.equals(StringUtils.trimToEmpty(left), StringUtils.trimToEmpty(right));
    }

    private String roomKey(Long roomId)
    {
        return roomId == null ? null : "ROOM-" + roomId;
    }

    private String cabinetKey(Long cabinetId)
    {
        return cabinetId == null ? null : "CABINET-" + cabinetId;
    }

    private String deviceKey(String sourceType, Long sourceId)
    {
        return StringUtils.trimToEmpty(sourceType).toUpperCase(Locale.ROOT) + ":" + sourceId;
    }

    private String normalizeKey(String value)
    {
        return StringUtils.trimToEmpty(value).toLowerCase(Locale.ROOT);
    }

    private String statusLabel(String status)
    {
        return "1".equals(status) ? "停用" : "正常";
    }

    private String safeFilename(String value)
    {
        String safe = StringUtils.defaultIfBlank(value, "现场").replaceAll("[\\\\/:*?\"<>|]", "_");
        return safe.length() > 60 ? safe.substring(0, 60) : safe;
    }

    private Object[] row(Object... values)
    {
        return values;
    }

    private Map<String, Object> locationDetail(String roomName, String cabinetNo, Integer rackUStart, Integer rackUEnd)
    {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("所属机房", roomName);
        detail.put("机柜编号", cabinetNo);
        detail.put("起始U位", rackUStart);
        detail.put("结束U位", rackUEnd);
        return detail;
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> listValue(Object value)
    {
        return value instanceof List ? (List<T>) value : new ArrayList<>();
    }

    private static class ImportContext
    {
        private final SupportSite site;
        private final String username;
        private final Date now;
        private final Map<String, SupportEquipmentRoom> roomKeys = new LinkedHashMap<>();
        private final Map<String, SupportEquipmentCabinet> cabinetKeys = new LinkedHashMap<>();
        private int roomsAdded;
        private int roomsUpdated;
        private int cabinetsAdded;
        private int cabinetsUpdated;
        private int placementsUpdated;
        private int placementsCleared;
        private int linksAdded;
        private int linksUpdated;
        private int linksDeleted;

        private ImportContext(SupportSite site, String username, Date now)
        {
            this.site = site;
            this.username = username;
            this.now = now;
        }

        private int totalChanges()
        {
            return roomsAdded + roomsUpdated + cabinetsAdded + cabinetsUpdated + placementsUpdated + placementsCleared
                + linksAdded + linksUpdated + linksDeleted;
        }

        private Map<String, Object> result()
        {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("现场名称", site.getSiteName());
            result.put("新增机房", roomsAdded);
            result.put("修改机房", roomsUpdated);
            result.put("新增机柜", cabinetsAdded);
            result.put("修改机柜", cabinetsUpdated);
            result.put("更新设备位置", placementsUpdated);
            result.put("清空设备位置", placementsCleared);
            result.put("新增设备链路", linksAdded);
            result.put("修改设备链路", linksUpdated);
            result.put("删除设备链路", linksDeleted);
            result.put("变更总数", totalChanges());
            return result;
        }
    }

    private static class PlacementState
    {
        private final SupportEquipmentTopologyDeviceVo device;
        private final SupportEquipmentRoom room;
        private final SupportEquipmentCabinet cabinet;
        private final Integer rackUStart;
        private final Integer rackUEnd;
        private final Map<String, String> row;

        private PlacementState(SupportEquipmentTopologyDeviceVo device, SupportEquipmentRoom room,
            SupportEquipmentCabinet cabinet, Integer rackUStart, Integer rackUEnd, Map<String, String> row)
        {
            this.device = device;
            this.room = room;
            this.cabinet = cabinet;
            this.rackUStart = rackUStart;
            this.rackUEnd = rackUEnd;
            this.row = row;
        }
    }
}
