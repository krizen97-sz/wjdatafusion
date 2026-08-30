package com.hm.manage.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.hm.manage.domain.SupportEquipmentCabinet;
import com.hm.manage.domain.SupportEquipmentRoom;

public interface SupportEquipmentLocationMapper
{
    List<SupportEquipmentRoom> selectRoomsBySiteId(Long siteId);

    SupportEquipmentRoom selectRoomByRoomId(Long roomId);

    SupportEquipmentRoom selectRoomBySiteAndName(@Param("siteId") Long siteId, @Param("roomName") String roomName);

    int insertRoom(SupportEquipmentRoom room);

    int updateRoom(SupportEquipmentRoom room);

    int deleteRoomByRoomId(Long roomId);

    int deleteRoomsBySiteIds(Long[] siteIds);

    List<SupportEquipmentCabinet> selectCabinetsBySiteId(Long siteId);

    List<SupportEquipmentCabinet> selectCabinetsByRoomId(Long roomId);

    SupportEquipmentCabinet selectCabinetByCabinetId(Long cabinetId);

    SupportEquipmentCabinet selectCabinetByRoomAndNo(@Param("roomId") Long roomId, @Param("cabinetNo") String cabinetNo);

    int insertCabinet(SupportEquipmentCabinet cabinet);

    int updateCabinet(SupportEquipmentCabinet cabinet);

    int updateCabinetLayout(SupportEquipmentCabinet cabinet);

    int deleteCabinetByCabinetId(Long cabinetId);

    int deleteCabinetsByRoomId(Long roomId);

    int deleteCabinetsBySiteIds(Long[] siteIds);

    int countCabinetNo(@Param("roomId") Long roomId, @Param("cabinetNo") String cabinetNo, @Param("cabinetId") Long cabinetId);

    int updateHardwareRoomName(@Param("siteId") Long siteId, @Param("oldRoomName") String oldRoomName, @Param("newRoomName") String newRoomName);

    int updateServerRoomName(@Param("siteId") Long siteId, @Param("oldRoomName") String oldRoomName, @Param("newRoomName") String newRoomName);

    int updateHardwareCabinetNo(@Param("siteId") Long siteId, @Param("roomName") String roomName, @Param("oldCabinetNo") String oldCabinetNo, @Param("newCabinetNo") String newCabinetNo);

    int updateServerCabinetNo(@Param("siteId") Long siteId, @Param("roomName") String roomName, @Param("oldCabinetNo") String oldCabinetNo, @Param("newCabinetNo") String newCabinetNo);

    int clearHardwareRoomLocation(@Param("siteId") Long siteId, @Param("roomName") String roomName);

    int clearServerRoomLocation(@Param("siteId") Long siteId, @Param("roomName") String roomName);

    int clearHardwareCabinetLocation(@Param("siteId") Long siteId, @Param("roomName") String roomName, @Param("cabinetNo") String cabinetNo);

    int clearServerCabinetLocation(@Param("siteId") Long siteId, @Param("roomName") String roomName, @Param("cabinetNo") String cabinetNo);

    int countHardwareRackConflicts(@Param("siteId") Long siteId, @Param("roomName") String roomName, @Param("cabinetNo") String cabinetNo,
        @Param("rackUStart") Integer rackUStart, @Param("rackUEnd") Integer rackUEnd, @Param("excludeAssetId") Long excludeAssetId);

    int countServerRackConflicts(@Param("siteId") Long siteId, @Param("roomName") String roomName, @Param("cabinetNo") String cabinetNo,
        @Param("rackUStart") Integer rackUStart, @Param("rackUEnd") Integer rackUEnd, @Param("excludeServerId") Long excludeServerId);

    int countRackPlacementsAboveCapacity(@Param("siteId") Long siteId, @Param("roomName") String roomName,
        @Param("cabinetNo") String cabinetNo, @Param("uCapacity") Integer uCapacity);
}
