package com.hm.manage.mapper;

import org.apache.ibatis.annotations.Param;

public interface SupportPlatformContactRelMapper
{
    int insertSupportPlatformContactRel(@Param("platformId") Long platformId, @Param("contactId") Long contactId,
        @Param("createBy") String createBy, @Param("createTime") java.util.Date createTime);

    int deleteSupportPlatformContactRel(@Param("platformId") Long platformId, @Param("contactId") Long contactId);

    int countByPlatformAndContact(@Param("platformId") Long platformId, @Param("contactId") Long contactId);

    int deleteByPlatformId(@Param("platformId") Long platformId);

    int deleteByContactId(@Param("contactId") Long contactId);

    int deleteByOrgId(@Param("orgId") Long orgId);
}
