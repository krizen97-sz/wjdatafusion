package com.hm.manage.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.hm.common.core.controller.BaseController;
import com.hm.common.core.domain.AjaxResult;
import com.hm.common.exception.ServiceException;
import com.hm.common.utils.DateUtils;
import com.hm.common.utils.SecurityUtils;
import com.hm.common.utils.StringUtils;
import com.hm.manage.domain.SupportEquipmentCabinet;
import com.hm.manage.domain.SupportEquipmentRoom;
import com.hm.manage.mapper.SupportEquipmentLocationMapper;
import com.hm.manage.mapper.SupportSiteMapper;

@RestController
@RequestMapping("/support/equipmentLocation")
public class SupportEquipmentLocationController extends BaseController
{
    @Autowired
    private SupportEquipmentLocationMapper locationMapper;

    @Autowired
    private SupportSiteMapper siteMapper;

    @PreAuthorize("@ss.hasPermi('support:hardwareAsset:query')")
    @GetMapping("/layout/{siteId}")
    public AjaxResult layout(@PathVariable Long siteId)
    {
        requireSite(siteId);
        Map<String, Object> data = new HashMap<>();
        data.put("rooms", locationMapper.selectRoomsBySiteId(siteId));
        data.put("cabinets", locationMapper.selectCabinetsBySiteId(siteId));
        return success(data);
    }

    @PreAuthorize("@ss.hasPermi('support:hardwareAsset:add') or @ss.hasPermi('support:hardwareAsset:edit')")
    @PostMapping("/room")
    public AjaxResult addRoom(@RequestBody SupportEquipmentRoom room)
    {
        normalizeRoom(room);
        room.setCreateBy(SecurityUtils.getUsername());
        room.setCreateTime(DateUtils.getNowDate());
        return toAjax(locationMapper.insertRoom(room));
    }

    @PreAuthorize("@ss.hasPermi('support:hardwareAsset:edit')")
    @PutMapping("/room")
    public AjaxResult editRoom(@RequestBody SupportEquipmentRoom room)
    {
        if (room == null || room.getRoomId() == null)
        {
            throw new ServiceException("机房ID不能为空");
        }
        normalizeRoom(room);
        room.setUpdateBy(SecurityUtils.getUsername());
        room.setUpdateTime(DateUtils.getNowDate());
        return toAjax(locationMapper.updateRoom(room));
    }

    @PreAuthorize("@ss.hasPermi('support:hardwareAsset:remove')")
    @DeleteMapping("/room/{roomId}")
    public AjaxResult removeRoom(@PathVariable Long roomId)
    {
        locationMapper.deleteCabinetsByRoomId(roomId);
        return toAjax(locationMapper.deleteRoomByRoomId(roomId));
    }

    @PreAuthorize("@ss.hasPermi('support:hardwareAsset:add') or @ss.hasPermi('support:hardwareAsset:edit')")
    @PostMapping("/cabinet")
    public AjaxResult addCabinet(@RequestBody SupportEquipmentCabinet cabinet)
    {
        normalizeCabinet(cabinet);
        cabinet.setCreateBy(SecurityUtils.getUsername());
        cabinet.setCreateTime(DateUtils.getNowDate());
        return toAjax(locationMapper.insertCabinet(cabinet));
    }

    @PreAuthorize("@ss.hasPermi('support:hardwareAsset:edit')")
    @PutMapping("/cabinet")
    public AjaxResult editCabinet(@RequestBody SupportEquipmentCabinet cabinet)
    {
        if (cabinet == null || cabinet.getCabinetId() == null)
        {
            throw new ServiceException("机柜ID不能为空");
        }
        normalizeCabinet(cabinet);
        cabinet.setUpdateBy(SecurityUtils.getUsername());
        cabinet.setUpdateTime(DateUtils.getNowDate());
        return toAjax(locationMapper.updateCabinet(cabinet));
    }

    @PreAuthorize("@ss.hasPermi('support:hardwareAsset:remove')")
    @DeleteMapping("/cabinet/{cabinetId}")
    public AjaxResult removeCabinet(@PathVariable Long cabinetId)
    {
        return toAjax(locationMapper.deleteCabinetByCabinetId(cabinetId));
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
        if (StringUtils.isBlank(cabinet.getStatus()))
        {
            cabinet.setStatus("0");
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
