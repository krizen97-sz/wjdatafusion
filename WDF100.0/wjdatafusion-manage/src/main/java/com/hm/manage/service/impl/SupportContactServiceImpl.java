package com.hm.manage.service.impl;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.hm.common.core.domain.BaseEntity;
import com.hm.common.utils.DateUtils;
import com.hm.manage.domain.SupportContact;
import com.hm.manage.mapper.SupportContactMapper;
import com.hm.manage.mapper.SupportPlatformContactRelMapper;
import com.hm.manage.service.ISupportChangeLogService;
import com.hm.manage.service.ISupportContactService;

@Service
public class SupportContactServiceImpl implements ISupportContactService
{
    @Autowired
    private SupportContactMapper contactMapper;

    @Autowired
    private SupportPlatformContactRelMapper platformContactRelMapper;

    @Autowired
    private ISupportChangeLogService changeLogService;

    @Override
    public SupportContact selectSupportContactByContactId(Long contactId)
    {
        return contactMapper.selectSupportContactByContactId(contactId);
    }

    @Override
    public List<SupportContact> selectSupportContactList(SupportContact contact)
    {
        return contactMapper.selectSupportContactList(contact);
    }

    @Override
    public int insertSupportContact(SupportContact contact)
    {
        contact.setCreateTime(DateUtils.getNowDate());
        int rows = contactMapper.insertSupportContact(contact);
        if (rows > 0)
        {
            recordForSites(resolveSiteIds(contact, contact.getContactId()), "INSERT", contact.getContactId(), contact.getContactName(), "新增人员 " + contact.getContactName(), null, contact);
        }
        return rows;
    }

    @Override
    public int updateSupportContact(SupportContact contact)
    {
        SupportContact original = contactMapper.selectSupportContactByContactId(contact.getContactId());
        contact.setUpdateTime(DateUtils.getNowDate());
        int rows = contactMapper.updateSupportContact(contact);
        if (rows > 0)
        {
            recordForSites(resolveSiteIds(contact, contact.getContactId()), "UPDATE", contact.getContactId(), contact.getContactName(), "修改人员 " + contact.getContactName(), original, contact);
        }
        return rows;
    }

    @Override
    public int deleteSupportContactByContactIds(Long[] contactIds)
    {
        List<SupportContact> deletedContacts = new ArrayList<>();
        Map<Long, List<Long>> siteIdMap = new HashMap<>();
        for (Long contactId : contactIds)
        {
            SupportContact contact = contactMapper.selectSupportContactByContactId(contactId);
            if (contact != null)
            {
                deletedContacts.add(contact);
                siteIdMap.put(contactId, resolveSiteIds(contact, contactId));
            }
            platformContactRelMapper.deleteByContactId(contactId);
        }
        int rows = contactMapper.deleteSupportContactByContactIds(contactIds);
        if (rows > 0)
        {
            for (SupportContact contact : deletedContacts)
            {
                recordForSites(siteIdMap.get(contact.getContactId()), "DELETE", contact.getContactId(), contact.getContactName(), "删除人员 " + contact.getContactName(), contact, null);
            }
        }
        return rows;
    }

    private List<Long> resolveSiteIds(BaseEntity entity, Long contactId)
    {
        Long requestedSiteId = getRequestedSiteId(entity);
        if (requestedSiteId != null)
        {
            List<Long> siteIds = new ArrayList<>();
            siteIds.add(requestedSiteId);
            return siteIds;
        }
        return contactId == null ? new ArrayList<>() : contactMapper.selectSiteIdsByContactId(contactId);
    }

    private void recordForSites(List<Long> siteIds, String actionType, Long contactId, String contactName, String summary, Object beforeData, Object afterData)
    {
        if (siteIds == null || siteIds.isEmpty())
        {
            changeLogService.record(null, actionType, "CONTACT", contactId, contactName, summary, beforeData, afterData);
            return;
        }
        for (Long siteId : siteIds)
        {
            changeLogService.record(siteId, actionType, "CONTACT", contactId, contactName, summary, beforeData, afterData);
        }
    }

    private Long getRequestedSiteId(BaseEntity entity)
    {
        if (entity == null || entity.getParams() == null)
        {
            return null;
        }
        Object value = entity.getParams().get("siteId");
        if (value instanceof Number)
        {
            return ((Number) value).longValue();
        }
        if (value instanceof String && !((String) value).isBlank())
        {
            return Long.valueOf((String) value);
        }
        return null;
    }
}
