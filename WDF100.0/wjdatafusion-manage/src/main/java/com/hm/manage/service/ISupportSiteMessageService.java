package com.hm.manage.service;

import java.util.List;
import com.hm.manage.domain.SupportSiteMessage;

public interface ISupportSiteMessageService
{
    List<SupportSiteMessage> selectSupportSiteMessageList(SupportSiteMessage message);

    List<SupportSiteMessage> selectLatestSupportSiteMessages(Long siteId, Long afterMessageId, Integer limit);

    int insertSupportSiteMessage(SupportSiteMessage message);
}
