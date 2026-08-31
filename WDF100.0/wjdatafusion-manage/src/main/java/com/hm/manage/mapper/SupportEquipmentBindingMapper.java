package com.hm.manage.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.hm.manage.domain.vo.SupportEquipmentPlatformBindingVo;

public interface SupportEquipmentBindingMapper
{
    List<SupportEquipmentPlatformBindingVo> selectServerBindingsBySiteId(@Param("siteId") Long siteId);

    List<SupportEquipmentPlatformBindingVo> selectHardwareBindingsBySiteId(@Param("siteId") Long siteId);
}
