package com.hm.manage.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.hm.manage.domain.SupportEquipmentCabinet;
import com.hm.manage.domain.SupportEquipmentRoom;

class SupportEquipmentLayoutUtilsTest
{
    @Test
    void defaultLayoutIsStableAndCollisionAware()
    {
        SupportEquipmentRoom room = room(10, 8);
        SupportEquipmentCabinet first = cabinet(1L, null, null, 0);
        SupportEquipmentCabinet second = cabinet(2L, null, null, 0);
        SupportEquipmentLayoutUtils.applyDefaultLayout(first, 0, room);
        SupportEquipmentLayoutUtils.applyDefaultLayout(second, 1, room);

        assertEquals(new BigDecimal("0.80"), first.getPositionX());
        assertEquals(new BigDecimal("2.20"), second.getPositionX());
        assertNull(SupportEquipmentLayoutUtils.findCollision(first, List.of(first, second), room));
    }

    @Test
    void overlappingRotatedCabinetIsDetected()
    {
        SupportEquipmentRoom room = room(10, 8);
        SupportEquipmentCabinet existing = cabinet(1L, 3.0, 3.0, 90);
        SupportEquipmentCabinet candidate = cabinet(2L, 3.7, 3.0, 0);
        assertEquals(existing, SupportEquipmentLayoutUtils.findCollision(candidate, List.of(existing), room));
    }

    private SupportEquipmentRoom room(double width, double depth)
    {
        SupportEquipmentRoom room = new SupportEquipmentRoom();
        room.setRoomWidth(BigDecimal.valueOf(width));
        room.setRoomDepth(BigDecimal.valueOf(depth));
        return room;
    }

    private SupportEquipmentCabinet cabinet(Long id, Double x, Double z, double rotation)
    {
        SupportEquipmentCabinet cabinet = new SupportEquipmentCabinet();
        cabinet.setCabinetId(id);
        if (x != null) cabinet.setPositionX(BigDecimal.valueOf(x));
        if (z != null) cabinet.setPositionZ(BigDecimal.valueOf(z));
        cabinet.setRotationY(BigDecimal.valueOf(rotation));
        return cabinet;
    }
}
