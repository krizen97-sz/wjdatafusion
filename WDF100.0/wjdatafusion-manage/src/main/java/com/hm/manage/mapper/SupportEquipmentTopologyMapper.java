package com.hm.manage.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.hm.manage.domain.SupportEquipmentLink;

public interface SupportEquipmentTopologyMapper
{
    List<SupportEquipmentLink> selectLinksBySiteId(Long siteId);

    SupportEquipmentLink selectLinkByLinkId(Long linkId);

    int insertLink(SupportEquipmentLink link);

    int updateLink(SupportEquipmentLink link);

    int deleteLinkByLinkId(Long linkId);

    int deleteLinksBySiteIds(Long[] siteIds);

    int deleteLinksByDevice(@Param("sourceType") String sourceType, @Param("sourceId") Long sourceId);

    int countDuplicateLink(SupportEquipmentLink link);
}
