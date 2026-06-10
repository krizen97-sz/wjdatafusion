package com.hm.manage.service;

import java.util.List;
import com.hm.manage.domain.SupportContact;

public interface ISupportContactService
{
    SupportContact selectSupportContactByContactId(Long contactId);

    List<SupportContact> selectSupportContactList(SupportContact contact);

    int insertSupportContact(SupportContact contact);

    int updateSupportContact(SupportContact contact);

    int deleteSupportContactByContactIds(Long[] contactIds);
}
