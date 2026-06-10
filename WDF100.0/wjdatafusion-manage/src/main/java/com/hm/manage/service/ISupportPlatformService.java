package com.hm.manage.service;

import com.hm.manage.domain.SupportContact;
import java.util.List;
import com.hm.manage.domain.SupportPlatform;
import com.hm.manage.domain.SupportServer;

public interface ISupportPlatformService
{
    SupportPlatform selectSupportPlatformByPlatformId(Long platformId);

    List<SupportPlatform> selectSupportPlatformList(SupportPlatform platform);

    List<SupportPlatform> selectPlatformTreeBySiteId(Long siteId);

    int insertSupportPlatform(SupportPlatform platform);

    int updateSupportPlatform(SupportPlatform platform);

    int deleteSupportPlatformByPlatformIds(Long[] platformIds);

    int bindServer(Long platformId, Long serverId);

    int unbindServer(Long platformId, Long serverId);

    int bindContact(Long platformId, Long contactId);

    int unbindContact(Long platformId, Long contactId);

    List<SupportServer> listServersByPlatformId(Long platformId);

    List<SupportContact> listContactsByPlatformId(Long platformId);
}
