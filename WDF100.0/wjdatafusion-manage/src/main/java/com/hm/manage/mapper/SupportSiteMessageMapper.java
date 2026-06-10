package com.hm.manage.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.hm.manage.domain.SupportSiteMessage;

public interface SupportSiteMessageMapper
{
    List<SupportSiteMessage> selectSupportSiteMessageList(SupportSiteMessage message);

    List<SupportSiteMessage> selectLatestSupportSiteMessages(@Param("siteId") Long siteId, @Param("afterMessageId") Long afterMessageId, @Param("limit") Integer limit);

    List<SupportSiteMessage> selectMessagesBySiteId(@Param("siteId") Long siteId);

    int insertSupportSiteMessage(SupportSiteMessage message);

    int deleteSupportSiteMessagesBySiteIds(@Param("siteIds") Long[] siteIds);
}
