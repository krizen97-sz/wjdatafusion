package com.hm.manage.mapper;

import java.util.List;
import com.hm.manage.domain.SupportOrg;

public interface SupportOrgMapper
{
    SupportOrg selectSupportOrgByOrgId(Long orgId);

    List<SupportOrg> selectSupportOrgList(SupportOrg org);

    List<SupportOrg> selectOrgsByPlatformId(Long platformId);

    List<SupportOrg> selectOrgsBySiteId(Long siteId);

    List<Long> selectSiteIdsByOrgId(Long orgId);

    int countBySiteId(Long siteId);

    int insertSupportOrg(SupportOrg org);

    int updateSupportOrg(SupportOrg org);

    int deleteSupportOrgByOrgId(Long orgId);

    int deleteSupportOrgByOrgIds(Long[] orgIds);
}
