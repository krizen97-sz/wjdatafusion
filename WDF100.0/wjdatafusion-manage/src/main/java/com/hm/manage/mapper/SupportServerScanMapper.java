package com.hm.manage.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

public interface SupportServerScanMapper
{
    List<Map<String, Object>> selectCredentials(Long siteId);
    Map<String, Object> selectCredential(@Param("siteId") Long siteId, @Param("id") Long id);
    int insertCredential(Map<String, Object> row);
    int updateCredential(Map<String, Object> row);
    int deleteCredential(@Param("siteId") Long siteId, @Param("id") Long id);
    List<Map<String, Object>> selectProfiles(Long siteId);
    int countAddressOwners(@Param("siteId") Long siteId, @Param("address") String address);
    int saveProfile(Map<String, Object> row);
}
