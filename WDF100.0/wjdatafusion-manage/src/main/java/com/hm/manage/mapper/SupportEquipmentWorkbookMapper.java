package com.hm.manage.mapper;

import java.util.Date;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

public interface SupportEquipmentWorkbookMapper
{
    List<Map<String, Object>> selectServers(@Param("siteId") Long siteId, @Param("lock") boolean lock);
    List<Map<String, Object>> selectHardware(@Param("siteId") Long siteId, @Param("lock") boolean lock);
    List<Map<String, Object>> selectServerCredentials(@Param("siteId") Long siteId, @Param("lock") boolean lock);
    List<Map<String, Object>> selectPlatforms(@Param("siteId") Long siteId, @Param("lock") boolean lock);
    List<Map<String, Object>> selectNetworks();
    List<Long> lockRooms(Long siteId);
    List<Long> lockCabinets(Long siteId);
    List<Map<String, Object>> selectServerBindings(@Param("siteId") Long siteId, @Param("lock") boolean lock);
    List<Map<String, Object>> selectHardwareBindings(@Param("siteId") Long siteId, @Param("lock") boolean lock);
    int updateServer(@Param("siteId") Long siteId, @Param("id") Long id, @Param("fields") Map<String, Object> fields,
        @Param("actor") String actor, @Param("now") Date now);
    int updateHardware(@Param("siteId") Long siteId, @Param("id") Long id, @Param("fields") Map<String, Object> fields,
        @Param("actor") String actor, @Param("now") Date now);
    int updateCredential(@Param("serverId") Long serverId, @Param("credentialId") Long credentialId,
        @Param("username") String username, @Param("cipher") String cipher,
        @Param("actor") String actor, @Param("now") Date now);
}
