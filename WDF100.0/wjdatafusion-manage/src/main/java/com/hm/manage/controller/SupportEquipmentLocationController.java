package com.hm.manage.controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.hm.common.annotation.Log;
import com.hm.common.core.controller.BaseController;
import com.hm.common.core.domain.AjaxResult;
import com.hm.common.exception.ServiceException;
import com.hm.common.enums.BusinessType;
import com.hm.common.utils.DateUtils;
import com.hm.common.utils.SecurityUtils;
import com.hm.common.utils.StringUtils;
import com.hm.manage.domain.SupportEquipmentCabinet;
import com.hm.manage.domain.SupportEquipmentLink;
import com.hm.manage.domain.SupportEquipmentRoom;
import com.hm.manage.domain.bo.SupportEquipmentPlacementBo;
import com.hm.manage.mapper.SupportEquipmentLocationMapper;
import com.hm.manage.mapper.SupportSiteMapper;
import com.hm.manage.service.ISupportChangeLogService;
import com.hm.manage.service.ISupportEquipmentTopologyService;
import com.hm.manage.service.ISupportEquipmentTopologyWorkbookService;
import com.hm.manage.util.SupportEquipmentLayoutUtils;

@RestController
@RequestMapping("/support/equipmentLocation")
public class SupportEquipmentLocationController extends BaseController
{
    private static final BigDecimal DEFAULT_ROOM_WIDTH = new BigDecimal("12.00");
    private static final BigDecimal DEFAULT_ROOM_DEPTH = new BigDecimal("8.00");

    @Autowired
    private SupportEquipmentLocationMapper locationMapper;

    @Autowired
    private SupportSiteMapper siteMapper;

    @Autowired
    private ISupportEquipmentTopologyService topologyService;

    @Autowired
    private ISupportEquipmentTopologyWorkbookService topologyWorkbookService;

    @Autowired
    private ISupportChangeLogService changeLogService;

    @PreAuthorize("@ss.hasAnyPermi('support:hardwareAsset:query,support:equipment:query')")
    @GetMapping("/layout/{siteId}")
    public AjaxResult layout(@PathVariable Long siteId)
    {
        requireSite(siteId);
        Map<String, Object> data = new HashMap<>();
        data.put("rooms", locationMapper.selectRoomsBySiteId(siteId));
        data.put("cabinets", locationMapper.selectCabinetsBySiteId(siteId));
        return success(data);
    }

    @PreAuthorize("@ss.hasAnyPermi('support:hardwareAsset:query,support:equipment:query')")
    @GetMapping("/topology/{siteId}")
    public AjaxResult topology(@PathVariable Long siteId)
    {
        return success(topologyService.selectTopology(siteId));
    }

    @PreAuthorize("@ss.hasAnyPermi('support:hardwareAsset:export,support:equipment:export')")
    @Log(title = "机房设备布局", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, @RequestParam Long siteId) throws Exception
    {
        topologyWorkbookService.exportWorkbook(response, siteId);
    }

    @PreAuthorize("@ss.hasPermi('support:equipment:edit') or (@ss.hasPermi('support:hardwareAsset:add') and @ss.hasPermi('support:hardwareAsset:edit') and @ss.hasPermi('support:hardwareAsset:remove'))")
    @Log(title = "机房设备布局", businessType = BusinessType.IMPORT)
    @PostMapping("/importData")
    public AjaxResult importData(@RequestParam Long siteId, MultipartFile file) throws Exception
    {
        Map<String, Object> result = topologyWorkbookService.importWorkbook(siteId, file);
        return AjaxResult.success("机房设备布局导入完成，共变更" + result.get("变更总数") + "项", result);
    }

    @PreAuthorize("@ss.hasAnyPermi('support:hardwareAsset:add,support:hardwareAsset:edit,support:equipment:add,support:equipment:edit')")
    @PostMapping("/room")
    public AjaxResult addRoom(@RequestBody SupportEquipmentRoom room)
    {
        normalizeRoom(room);
        room.setCreateBy(SecurityUtils.getUsername());
        room.setCreateTime(DateUtils.getNowDate());
        int rows = locationMapper.insertRoom(room);
        if (rows > 0)
        {
            changeLogService.record(room.getSiteId(), "INSERT", "EQUIPMENT_ROOM", room.getRoomId(), room.getRoomName(),
                "新增机房 " + room.getRoomName(), null, room);
        }
        return toAjax(rows);
    }

    @PreAuthorize("@ss.hasAnyPermi('support:hardwareAsset:edit,support:equipment:edit')")
    @PutMapping("/room")
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult editRoom(@RequestBody SupportEquipmentRoom room)
    {
        if (room == null || room.getRoomId() == null)
        {
            throw new ServiceException("机房ID不能为空");
        }
        SupportEquipmentRoom original = locationMapper.selectRoomByRoomId(room.getRoomId());
        if (original == null)
        {
            throw new ServiceException("机房不存在");
        }
        room.setSiteId(original.getSiteId());
        normalizeRoom(room);
        ensureRoomContainsCabinets(room);
        room.setUpdateBy(SecurityUtils.getUsername());
        room.setUpdateTime(DateUtils.getNowDate());
        int rows = locationMapper.updateRoom(room);
        if (rows > 0)
        {
            if (!Objects.equals(original.getRoomName(), room.getRoomName()))
            {
                locationMapper.updateHardwareRoomName(room.getSiteId(), original.getRoomName(), room.getRoomName());
                locationMapper.updateServerRoomName(room.getSiteId(), original.getRoomName(), room.getRoomName());
            }
            changeLogService.record(room.getSiteId(), "UPDATE", "EQUIPMENT_ROOM", room.getRoomId(), room.getRoomName(),
                "修改机房 " + room.getRoomName(), original, room);
        }
        return toAjax(rows);
    }

    @PreAuthorize("@ss.hasAnyPermi('support:hardwareAsset:remove,support:equipment:remove')")
    @DeleteMapping("/room/{roomId}")
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult removeRoom(@PathVariable Long roomId)
    {
        SupportEquipmentRoom room = locationMapper.selectRoomByRoomId(roomId);
        if (room == null)
        {
            return toAjax(0);
        }
        locationMapper.clearHardwareRoomLocation(room.getSiteId(), room.getRoomName());
        locationMapper.clearServerRoomLocation(room.getSiteId(), room.getRoomName());
        locationMapper.deleteCabinetsByRoomId(roomId);
        int rows = locationMapper.deleteRoomByRoomId(roomId);
        if (rows > 0)
        {
            changeLogService.record(room.getSiteId(), "DELETE", "EQUIPMENT_ROOM", roomId, room.getRoomName(),
                "删除机房 " + room.getRoomName() + " 并清空设备安装位置", room, null);
        }
        return toAjax(rows);
    }

    @PreAuthorize("@ss.hasAnyPermi('support:hardwareAsset:add,support:hardwareAsset:edit,support:equipment:add,support:equipment:edit')")
    @PostMapping("/cabinet")
    public AjaxResult addCabinet(@RequestBody SupportEquipmentCabinet cabinet)
    {
        normalizeCabinet(cabinet);
        cabinet.setCreateBy(SecurityUtils.getUsername());
        cabinet.setCreateTime(DateUtils.getNowDate());
        int rows = locationMapper.insertCabinet(cabinet);
        if (rows > 0)
        {
            changeLogService.record(cabinet.getSiteId(), "INSERT", "EQUIPMENT_CABINET", cabinet.getCabinetId(), cabinet.getCabinetNo(),
                "新增机柜 " + cabinet.getCabinetNo(), null, cabinet);
        }
        return toAjax(rows);
    }

    @PreAuthorize("@ss.hasAnyPermi('support:hardwareAsset:edit,support:equipment:edit')")
    @PutMapping("/cabinet")
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult editCabinet(@RequestBody SupportEquipmentCabinet cabinet)
    {
        if (cabinet == null || cabinet.getCabinetId() == null)
        {
            throw new ServiceException("机柜ID不能为空");
        }
        SupportEquipmentCabinet original = locationMapper.selectCabinetByCabinetId(cabinet.getCabinetId());
        if (original == null)
        {
            throw new ServiceException("机柜不存在");
        }
        cabinet.setRoomId(original.getRoomId());
        normalizeCabinet(cabinet);
        SupportEquipmentRoom cabinetRoom = locationMapper.selectRoomByRoomId(cabinet.getRoomId());
        if (locationMapper.countRackPlacementsAboveCapacity(cabinet.getSiteId(), cabinetRoom.getRoomName(), original.getCabinetNo(), cabinet.getUCapacity()) > 0)
        {
            throw new ServiceException("机柜内存在超出新容量的设备，请先调整设备U位");
        }
        cabinet.setUpdateBy(SecurityUtils.getUsername());
        cabinet.setUpdateTime(DateUtils.getNowDate());
        int rows = locationMapper.updateCabinet(cabinet);
        if (rows > 0)
        {
            if (!Objects.equals(original.getCabinetNo(), cabinet.getCabinetNo()))
            {
                locationMapper.updateHardwareCabinetNo(cabinet.getSiteId(), cabinetRoom.getRoomName(), original.getCabinetNo(), cabinet.getCabinetNo());
                locationMapper.updateServerCabinetNo(cabinet.getSiteId(), cabinetRoom.getRoomName(), original.getCabinetNo(), cabinet.getCabinetNo());
            }
            changeLogService.record(cabinet.getSiteId(), "UPDATE", "EQUIPMENT_CABINET", cabinet.getCabinetId(), cabinet.getCabinetNo(),
                "修改机柜 " + cabinet.getCabinetNo(), original, cabinet);
        }
        return toAjax(rows);
    }

    @PreAuthorize("@ss.hasAnyPermi('support:hardwareAsset:edit,support:equipment:edit')")
    @PutMapping("/cabinet/layout")
    public AjaxResult updateCabinetLayout(@RequestBody SupportEquipmentCabinet cabinet)
    {
        return toAjax(topologyService.updateCabinetLayout(cabinet));
    }

    @PreAuthorize("@ss.hasAnyPermi('support:hardwareAsset:edit,support:equipment:edit')")
    @PutMapping("/device/placement")
    public AjaxResult updateDevicePlacement(@RequestBody SupportEquipmentPlacementBo placement)
    {
        int rows = topologyService.updateDevicePlacement(placement);
        return rows > 0 ? success("设备安装位置已更新") : success("设备安装位置未发生变化");
    }

    @PreAuthorize("@ss.hasAnyPermi('support:hardwareAsset:remove,support:equipment:remove')")
    @DeleteMapping("/cabinet/{cabinetId}")
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult removeCabinet(@PathVariable Long cabinetId)
    {
        SupportEquipmentCabinet cabinet = locationMapper.selectCabinetByCabinetId(cabinetId);
        if (cabinet == null)
        {
            return toAjax(0);
        }
        SupportEquipmentRoom room = locationMapper.selectRoomByRoomId(cabinet.getRoomId());
        if (room != null)
        {
            locationMapper.clearHardwareCabinetLocation(cabinet.getSiteId(), room.getRoomName(), cabinet.getCabinetNo());
            locationMapper.clearServerCabinetLocation(cabinet.getSiteId(), room.getRoomName(), cabinet.getCabinetNo());
        }
        int rows = locationMapper.deleteCabinetByCabinetId(cabinetId);
        if (rows > 0)
        {
            changeLogService.record(cabinet.getSiteId(), "DELETE", "EQUIPMENT_CABINET", cabinetId, cabinet.getCabinetNo(),
                "删除机柜 " + cabinet.getCabinetNo() + " 并清空设备U位", cabinet, null);
        }
        return toAjax(rows);
    }

    @PreAuthorize("@ss.hasAnyPermi('support:hardwareAsset:add,support:hardwareAsset:edit,support:equipment:add,support:equipment:edit')")
    @PostMapping("/link")
    public AjaxResult addLink(@RequestBody SupportEquipmentLink link)
    {
        return toAjax(topologyService.insertLink(link));
    }

    @PreAuthorize("@ss.hasAnyPermi('support:hardwareAsset:edit,support:equipment:edit')")
    @PutMapping("/link")
    public AjaxResult editLink(@RequestBody SupportEquipmentLink link)
    {
        return toAjax(topologyService.updateLink(link));
    }

    @PreAuthorize("@ss.hasAnyPermi('support:hardwareAsset:remove,support:equipment:remove')")
    @DeleteMapping("/link/{linkId}")
    public AjaxResult removeLink(@PathVariable Long linkId)
    {
        return toAjax(topologyService.deleteLink(linkId));
    }

    private void normalizeRoom(SupportEquipmentRoom room)
    {
        if (room == null || room.getSiteId() == null)
        {
            throw new ServiceException("现场ID不能为空");
        }
        requireSite(room.getSiteId());
        room.setRoomName(StringUtils.trimToEmpty(room.getRoomName()));
        room.setRoomCode(StringUtils.trimToEmpty(room.getRoomCode()));
        if (room.getRoomWidth() == null)
        {
            room.setRoomWidth(DEFAULT_ROOM_WIDTH);
        }
        if (room.getRoomDepth() == null)
        {
            room.setRoomDepth(DEFAULT_ROOM_DEPTH);
        }
        room.setRoomWidth(room.getRoomWidth().setScale(2, RoundingMode.HALF_UP));
        room.setRoomDepth(room.getRoomDepth().setScale(2, RoundingMode.HALF_UP));
        if (room.getRoomWidth().compareTo(new BigDecimal("2.00")) < 0 || room.getRoomWidth().compareTo(new BigDecimal("100.00")) > 0 ||
            room.getRoomDepth().compareTo(new BigDecimal("2.00")) < 0 || room.getRoomDepth().compareTo(new BigDecimal("100.00")) > 0)
        {
            throw new ServiceException("机房长宽必须在2到100米之间");
        }
        if (StringUtils.isBlank(room.getRoomName()))
        {
            throw new ServiceException("机房名称不能为空");
        }
        if (StringUtils.isBlank(room.getStatus()))
        {
            room.setStatus("0");
        }
    }

    private void normalizeCabinet(SupportEquipmentCabinet cabinet)
    {
        if (cabinet == null || cabinet.getRoomId() == null)
        {
            throw new ServiceException("所属机房不能为空");
        }
        SupportEquipmentRoom room = locationMapper.selectRoomByRoomId(cabinet.getRoomId());
        if (room == null)
        {
            throw new ServiceException("机房不存在");
        }
        cabinet.setSiteId(room.getSiteId());
        cabinet.setCabinetNo(StringUtils.trimToEmpty(cabinet.getCabinetNo()));
        if (StringUtils.isBlank(cabinet.getCabinetNo()))
        {
            throw new ServiceException("机柜编号不能为空");
        }
        if (cabinet.getUCapacity() == null)
        {
            cabinet.setUCapacity(45);
        }
        if (cabinet.getUCapacity() < 1 || cabinet.getUCapacity() > 45)
        {
            throw new ServiceException("机柜U数必须在1到45之间");
        }
        if (locationMapper.countCabinetNo(cabinet.getRoomId(), cabinet.getCabinetNo(), cabinet.getCabinetId()) > 0)
        {
            throw new ServiceException("当前机房已存在相同机柜编号");
        }
        applyCabinetLayout(cabinet, room);
        if (StringUtils.isBlank(cabinet.getStatus()))
        {
            cabinet.setStatus("0");
        }
    }

    private void applyCabinetLayout(SupportEquipmentCabinet cabinet, SupportEquipmentRoom room)
    {
        List<SupportEquipmentCabinet> cabinets = locationMapper.selectCabinetsByRoomId(room.getRoomId());
        int index = cabinets.size();
        for (int i = 0; i < cabinets.size(); i++)
        {
            if (cabinet.getCabinetId() != null && cabinet.getCabinetId().equals(cabinets.get(i).getCabinetId()))
            {
                index = i;
                break;
            }
        }
        SupportEquipmentLayoutUtils.applyDefaultLayout(cabinet, index, room);
        SupportEquipmentCabinet collision = SupportEquipmentLayoutUtils.findCollision(cabinet, cabinets, room);
        if (collision != null)
        {
            throw new ServiceException("机柜位置与" + collision.getCabinetNo() + "重叠，请重新摆放");
        }
    }

    private void ensureRoomContainsCabinets(SupportEquipmentRoom room)
    {
        List<SupportEquipmentCabinet> cabinets = locationMapper.selectCabinetsByRoomId(room.getRoomId());
        for (int index = 0; index < cabinets.size(); index++)
        {
            SupportEquipmentCabinet cabinet = cabinets.get(index);
            try
            {
                SupportEquipmentLayoutUtils.applyDefaultLayout(cabinet, index, room);
            }
            catch (ServiceException e)
            {
                throw new ServiceException("机房尺寸调整后机柜" + cabinet.getCabinetNo() + "将超出边界");
            }
            SupportEquipmentCabinet collision = SupportEquipmentLayoutUtils.findCollision(cabinet, cabinets, room);
            if (collision != null)
            {
                throw new ServiceException("机房内机柜" + cabinet.getCabinetNo() + "与" + collision.getCabinetNo() + "发生重叠");
            }
        }
    }

    private void requireSite(Long siteId)
    {
        if (siteMapper.selectSupportSiteBySiteId(siteId) == null)
        {
            throw new ServiceException("现场不存在");
        }
    }
}
