package com.hm.manage.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import com.hm.common.exception.ServiceException;
import com.hm.manage.domain.SupportEquipmentCabinet;
import com.hm.manage.domain.SupportEquipmentRoom;

public final class SupportEquipmentLayoutUtils
{
    private static final BigDecimal DEFAULT_ROOM_WIDTH = new BigDecimal("12.00");
    private static final BigDecimal DEFAULT_ROOM_DEPTH = new BigDecimal("8.00");
    private static final BigDecimal CABINET_WIDTH = new BigDecimal("0.80");
    private static final BigDecimal CABINET_DEPTH = new BigDecimal("1.10");
    private static final double CLEARANCE = 0.16D;

    private SupportEquipmentLayoutUtils()
    {
    }

    public static void applyDefaultLayout(SupportEquipmentCabinet cabinet, int index, SupportEquipmentRoom room)
    {
        BigDecimal roomWidth = roomWidth(room);
        if (cabinet.getPositionX() == null || cabinet.getPositionZ() == null)
        {
            int columns = Math.max(1, Math.min(8, roomWidth.divide(new BigDecimal("1.40"), 0, RoundingMode.DOWN).intValue()));
            cabinet.setPositionX(new BigDecimal("0.80").add(new BigDecimal(index % columns).multiply(new BigDecimal("1.40"))));
            cabinet.setPositionZ(new BigDecimal("0.90").add(new BigDecimal(index / columns).multiply(new BigDecimal("1.60"))));
        }
        normalizeAndValidate(cabinet, room);
    }

    public static void normalizeAndValidate(SupportEquipmentCabinet cabinet, SupportEquipmentRoom room)
    {
        if (cabinet.getPositionX() == null || cabinet.getPositionZ() == null)
        {
            throw new ServiceException("机柜三维坐标不能为空");
        }
        BigDecimal rotation = normalizeRotation(cabinet.getRotationY());
        cabinet.setRotationY(rotation);
        BigDecimal[] extents = halfExtents(rotation);
        BigDecimal roomWidth = roomWidth(room);
        BigDecimal roomDepth = roomDepth(room);
        cabinet.setPositionX(cabinet.getPositionX().setScale(2, RoundingMode.HALF_UP));
        cabinet.setPositionZ(cabinet.getPositionZ().setScale(2, RoundingMode.HALF_UP));
        if (cabinet.getPositionX().compareTo(extents[0]) < 0 || cabinet.getPositionX().compareTo(roomWidth.subtract(extents[0])) > 0 ||
            cabinet.getPositionZ().compareTo(extents[1]) < 0 || cabinet.getPositionZ().compareTo(roomDepth.subtract(extents[1])) > 0)
        {
            throw new ServiceException("机柜摆放位置超出机房边界");
        }
    }

    public static SupportEquipmentCabinet findCollision(SupportEquipmentCabinet candidate, List<SupportEquipmentCabinet> cabinets, SupportEquipmentRoom room)
    {
        BigDecimal[] candidateExtents = halfExtents(normalizeRotation(candidate.getRotationY()));
        for (int index = 0; index < cabinets.size(); index++)
        {
            SupportEquipmentCabinet other = cabinets.get(index);
            if (candidate.getCabinetId() != null && candidate.getCabinetId().equals(other.getCabinetId()))
            {
                continue;
            }
            applyDefaultLayout(other, index, room);
            BigDecimal[] otherExtents = halfExtents(normalizeRotation(other.getRotationY()));
            double distanceX = Math.abs(candidate.getPositionX().doubleValue() - other.getPositionX().doubleValue());
            double distanceZ = Math.abs(candidate.getPositionZ().doubleValue() - other.getPositionZ().doubleValue());
            if (distanceX < candidateExtents[0].doubleValue() + otherExtents[0].doubleValue() + CLEARANCE &&
                distanceZ < candidateExtents[1].doubleValue() + otherExtents[1].doubleValue() + CLEARANCE)
            {
                return other;
            }
        }
        return null;
    }

    private static BigDecimal normalizeRotation(BigDecimal value)
    {
        BigDecimal rotation = value == null ? BigDecimal.ZERO : value.remainder(new BigDecimal("360"));
        if (rotation.signum() < 0)
        {
            rotation = rotation.add(new BigDecimal("360"));
        }
        return rotation.setScale(1, RoundingMode.HALF_UP);
    }

    private static BigDecimal[] halfExtents(BigDecimal rotation)
    {
        double radians = Math.toRadians(rotation.doubleValue());
        double cosine = Math.abs(Math.cos(radians));
        double sine = Math.abs(Math.sin(radians));
        double halfX = cosine * CABINET_WIDTH.doubleValue() / 2D + sine * CABINET_DEPTH.doubleValue() / 2D;
        double halfZ = sine * CABINET_WIDTH.doubleValue() / 2D + cosine * CABINET_DEPTH.doubleValue() / 2D;
        return new BigDecimal[] {BigDecimal.valueOf(halfX), BigDecimal.valueOf(halfZ)};
    }

    private static BigDecimal roomWidth(SupportEquipmentRoom room)
    {
        return room == null || room.getRoomWidth() == null ? DEFAULT_ROOM_WIDTH : room.getRoomWidth();
    }

    private static BigDecimal roomDepth(SupportEquipmentRoom room)
    {
        return room == null || room.getRoomDepth() == null ? DEFAULT_ROOM_DEPTH : room.getRoomDepth();
    }
}
