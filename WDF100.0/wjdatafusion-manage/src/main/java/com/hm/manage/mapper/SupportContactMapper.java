package com.hm.manage.mapper;

import java.util.List;
import com.hm.manage.domain.SupportContact;

public interface SupportContactMapper
{
    SupportContact selectSupportContactByContactId(Long contactId);

    List<SupportContact> selectSupportContactList(SupportContact contact);

    List<SupportContact> selectContactsByPlatformId(Long platformId);

    List<Long> selectSiteIdsByContactId(Long contactId);

    int insertSupportContact(SupportContact contact);

    int updateSupportContact(SupportContact contact);

    int deleteSupportContactByContactId(Long contactId);

    int deleteSupportContactByContactIds(Long[] contactIds);

    int deleteSupportContactByOrgId(Long orgId);

    int countContactsBySiteId(Long siteId);
}
