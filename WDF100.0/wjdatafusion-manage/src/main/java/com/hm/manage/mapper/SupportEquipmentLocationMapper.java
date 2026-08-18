package com.hm.manage.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.hm.manage.domain.SupportEquipmentCabinet;
import com.hm.manage.domain.SupportEquipmentRoom;

public interface SupportEquipmentLocationMapper
{
    List<SupportEquipmentRoom> selectRoomsBySiteId(Long siteId);

    SupportEquipmentRoom selectRoomByRoomId(Long roomId);

    int insertRoom(SupportEquipmentRoom room);

    int updateRoom(SupportEquipmentRoom room);

    int deleteRoomByRoomId(Long roomId);

    int deleteRoomsBySiteIds(Long[] siteIds);

    List<SupportEquipmentCabinet> selectCabinetsBySiteId(Long siteId);

    List<SupportEquipmentCabinet> selectCabinetsByRoomId(Long roomId);

    SupportEquipmentCabinet selectCabinetByCabinetId(Long cabinetId);

    int insertCabinet(SupportEquipmentCabinet cabinet);

    int updateCabinet(SupportEquipmentCabinet cabinet);

    int deleteCabinetByCabinetId(Long cabinetId);

    int deleteCabinetsByRoomId(Long roomId);

    int deleteCabinetsBySiteIds(Long[] siteIds);

    int countCabinetNo(@Param("roomId") Long roomId, @Param("cabinetNo") String cabinetNo, @Param("cabinetId") Long cabinetId);
}
