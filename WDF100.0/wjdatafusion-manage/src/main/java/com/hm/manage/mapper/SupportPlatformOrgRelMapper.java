package com.hm.manage.mapper;

import org.apache.ibatis.annotations.Param;
import com.hm.manage.domain.SupportPlatformOrgRel;

public interface SupportPlatformOrgRelMapper
{
    int insertSupportPlatformOrgRel(SupportPlatformOrgRel rel);

    int deleteSupportPlatformOrgRel(@Param("platformId") Long platformId, @Param("orgId") Long orgId);

    int countByPlatformAndOrg(@Param("platformId") Long platformId, @Param("orgId") Long orgId);

    int deleteByPlatformId(Long platformId);

    int deleteByOrgId(Long orgId);
}
