package com.hm.manage.mapper;

import java.util.List;
import com.hm.manage.domain.SupportPlatform;

public interface SupportPlatformMapper
{
    SupportPlatform selectSupportPlatformByPlatformId(Long platformId);

    List<SupportPlatform> selectSupportPlatformList(SupportPlatform platform);

    List<SupportPlatform> selectPlatformsBySiteId(Long siteId);

    List<SupportPlatform> selectPlatformsByOrgId(Long orgId);

    List<SupportPlatform> selectPlatformsByContactId(Long contactId);

    int insertSupportPlatform(SupportPlatform platform);

    int updateSupportPlatform(SupportPlatform platform);

    int deleteSupportPlatformByPlatformId(Long platformId);

    int deleteSupportPlatformByPlatformIds(Long[] platformIds);

    int countChildPlatforms(Long platformId);
}
