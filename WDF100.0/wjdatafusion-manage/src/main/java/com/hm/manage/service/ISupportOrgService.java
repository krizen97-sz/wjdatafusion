package com.hm.manage.service;

import java.util.List;
import com.hm.manage.domain.SupportOrg;
import com.hm.manage.domain.SupportPlatform;

public interface ISupportOrgService
{
    SupportOrg selectSupportOrgByOrgId(Long orgId);

    List<SupportOrg> selectSupportOrgList(SupportOrg org);

    int insertSupportOrg(SupportOrg org);

    int updateSupportOrg(SupportOrg org);

    int deleteSupportOrgByOrgIds(Long[] orgIds);

    List<SupportPlatform> listPlatformsByOrgId(Long orgId);
}
