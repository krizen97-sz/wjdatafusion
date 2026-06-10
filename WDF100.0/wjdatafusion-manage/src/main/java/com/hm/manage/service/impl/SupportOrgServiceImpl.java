package com.hm.manage.service.impl;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.hm.common.core.domain.BaseEntity;
import com.hm.common.utils.DateUtils;
import com.hm.manage.domain.SupportOrg;
import com.hm.manage.domain.SupportPlatform;
import com.hm.manage.mapper.SupportContactMapper;
import com.hm.manage.mapper.SupportPlatformContactRelMapper;
import com.hm.manage.mapper.SupportOrgMapper;
import com.hm.manage.mapper.SupportPlatformMapper;
import com.hm.manage.mapper.SupportPlatformOrgRelMapper;
import com.hm.manage.service.ISupportChangeLogService;
import com.hm.manage.service.ISupportOrgService;

@Service
public class SupportOrgServiceImpl implements ISupportOrgService
{
    @Autowired
    private SupportOrgMapper orgMapper;

    @Autowired
    private SupportPlatformMapper platformMapper;

    @Autowired
    private SupportPlatformOrgRelMapper platformOrgRelMapper;

    @Autowired
    private SupportPlatformContactRelMapper platformContactRelMapper;

    @Autowired
    private SupportContactMapper contactMapper;

    @Autowired
    private ISupportChangeLogService changeLogService;

    @Override
    public SupportOrg selectSupportOrgByOrgId(Long orgId)
    {
        return orgMapper.selectSupportOrgByOrgId(orgId);
    }

    @Override
    public List<SupportOrg> selectSupportOrgList(SupportOrg org)
    {
        return orgMapper.selectSupportOrgList(org);
    }

    @Override
    public int insertSupportOrg(SupportOrg org)
    {
        org.setCreateTime(DateUtils.getNowDate());
        int rows = orgMapper.insertSupportOrg(org);
        if (rows > 0)
        {
            recordForSites(resolveSiteIds(org, org.getOrgId()), "INSERT", org.getOrgId(), org.getOrgName(), "新增组织 " + org.getOrgName(), null, org);
        }
        return rows;
    }

    @Override
    public int updateSupportOrg(SupportOrg org)
    {
        SupportOrg original = orgMapper.selectSupportOrgByOrgId(org.getOrgId());
        org.setUpdateTime(DateUtils.getNowDate());
        int rows = orgMapper.updateSupportOrg(org);
        if (rows > 0)
        {
            recordForSites(resolveSiteIds(org, org.getOrgId()), "UPDATE", org.getOrgId(), org.getOrgName(), "修改组织 " + org.getOrgName(), original, org);
        }
        return rows;
    }

    @Override
    public int deleteSupportOrgByOrgIds(Long[] orgIds)
    {
        List<SupportOrg> deletedOrgs = new ArrayList<>();
        Map<Long, List<Long>> siteIdMap = new HashMap<>();
        for (Long orgId : orgIds)
        {
            SupportOrg org = orgMapper.selectSupportOrgByOrgId(orgId);
            if (org != null)
            {
                deletedOrgs.add(org);
                siteIdMap.put(orgId, resolveSiteIds(org, orgId));
            }
            platformOrgRelMapper.deleteByOrgId(orgId);
            platformContactRelMapper.deleteByOrgId(orgId);
            contactMapper.deleteSupportContactByOrgId(orgId);
        }
        int rows = orgMapper.deleteSupportOrgByOrgIds(orgIds);
        if (rows > 0)
        {
            for (SupportOrg org : deletedOrgs)
            {
                recordForSites(siteIdMap.get(org.getOrgId()), "DELETE", org.getOrgId(), org.getOrgName(), "删除组织 " + org.getOrgName(), org, null);
            }
        }
        return rows;
    }

    @Override
    public List<SupportPlatform> listPlatformsByOrgId(Long orgId)
    {
        return platformMapper.selectPlatformsByOrgId(orgId);
    }

    private List<Long> resolveSiteIds(BaseEntity entity, Long orgId)
    {
        Long requestedSiteId = getRequestedSiteId(entity);
        if (requestedSiteId != null)
        {
            List<Long> siteIds = new ArrayList<>();
            siteIds.add(requestedSiteId);
            return siteIds;
        }
        return orgId == null ? new ArrayList<>() : orgMapper.selectSiteIdsByOrgId(orgId);
    }

    private void recordForSites(List<Long> siteIds, String actionType, Long orgId, String orgName, String summary, Object beforeData, Object afterData)
    {
        if (siteIds == null || siteIds.isEmpty())
        {
            changeLogService.record(null, actionType, "ORG", orgId, orgName, summary, beforeData, afterData);
            return;
        }
        for (Long siteId : siteIds)
        {
            changeLogService.record(siteId, actionType, "ORG", orgId, orgName, summary, beforeData, afterData);
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
