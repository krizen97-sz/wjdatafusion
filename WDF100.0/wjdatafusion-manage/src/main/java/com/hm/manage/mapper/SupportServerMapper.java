package com.hm.manage.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.hm.manage.domain.SupportServer;

public interface SupportServerMapper
{
    SupportServer selectSupportServerByServerId(Long serverId);
    SupportServer selectSupportServerByServerIdForUpdate(Long serverId);

    List<SupportServer> selectSupportServerList(SupportServer server);

    SupportServer selectSupportServerBySiteAndAddress(@Param("siteId") Long siteId, @Param("serverAddress") String serverAddress);

    List<SupportServer> selectServersByPlatformId(Long platformId);

    int insertSupportServer(SupportServer server);

    int updateSupportServer(SupportServer server);

    int clearLegacyCredential(@Param("serverId") Long serverId, @Param("username") String username,
        @Param("actor") String actor, @Param("now") java.util.Date now);

    int deleteSupportServerByServerId(Long serverId);

    int deleteSupportServerByServerIds(Long[] serverIds);
}
