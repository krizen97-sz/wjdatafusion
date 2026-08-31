package com.hm.manage.service;

import java.util.List;
import java.util.Map;
import com.hm.manage.domain.SupportEquipmentCabinet;
import com.hm.manage.domain.SupportEquipmentLink;
import com.hm.manage.domain.bo.SupportEquipmentPlacementBo;

public interface ISupportEquipmentTopologyService
{
    Map<String, Object> selectTopology(Long siteId);

    List<SupportEquipmentLink> selectLinksBySiteId(Long siteId);

    int updateCabinetLayout(SupportEquipmentCabinet cabinet);

    int updateDevicePlacement(SupportEquipmentPlacementBo placement);

    int insertLink(SupportEquipmentLink link);

    int updateLink(SupportEquipmentLink link);

    int deleteLink(Long linkId);
}
