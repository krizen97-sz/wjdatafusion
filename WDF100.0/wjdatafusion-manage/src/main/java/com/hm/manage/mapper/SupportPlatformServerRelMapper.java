package com.hm.manage.mapper;

import org.apache.ibatis.annotations.Param;
import com.hm.manage.domain.SupportPlatformServerRel;

public interface SupportPlatformServerRelMapper
{
    int insertSupportPlatformServerRel(SupportPlatformServerRel rel);

    int deleteSupportPlatformServerRel(@Param("platformId") Long platformId, @Param("serverId") Long serverId);

    int countByPlatformAndServer(@Param("platformId") Long platformId, @Param("serverId") Long serverId);

    int deleteByPlatformId(Long platformId);

    int deleteByServerId(Long serverId);
}
