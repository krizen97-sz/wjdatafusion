package com.hm.manage.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.hm.common.exception.ServiceException;
import com.hm.common.utils.DateUtils;
import com.hm.common.utils.SecurityUtils;
import com.hm.common.utils.StringUtils;
import com.hm.manage.domain.SupportContact;
import com.hm.manage.domain.SupportPlatform;
import com.hm.manage.domain.SupportPlatformServerRel;
import com.hm.manage.domain.SupportServer;
import com.hm.manage.mapper.SupportContactMapper;
import com.hm.manage.mapper.SupportPlatformContactRelMapper;
import com.hm.manage.mapper.SupportPlatformMapper;
import com.hm.manage.mapper.SupportPlatformOrgRelMapper;
import com.hm.manage.mapper.SupportPlatformServerRelMapper;
import com.hm.manage.mapper.SupportServerMapper;
import com.hm.manage.service.ISupportChangeLogService;
import com.hm.manage.service.ISupportPlatformService;

@Service
public class SupportPlatformServiceImpl implements ISupportPlatformService
{
    private static final String LEVEL_MAIN = "MAIN";
    private static final String LEVEL_SUB = "SUB";

    @Autowired
    private SupportPlatformMapper platformMapper;

    @Autowired
    private SupportServerMapper serverMapper;

    @Autowired
    private SupportContactMapper contactMapper;

    @Autowired
    private SupportPlatformServerRelMapper platformServerRelMapper;

    @Autowired
    private SupportPlatformOrgRelMapper platformOrgRelMapper;

    @Autowired
    private SupportPlatformContactRelMapper platformContactRelMapper;

    @Autowired
    private ISupportChangeLogService changeLogService;

    @Override
    public SupportPlatform selectSupportPlatformByPlatformId(Long platformId)
    {
        return platformMapper.selectSupportPlatformByPlatformId(platformId);
    }

    @Override
    public List<SupportPlatform> selectSupportPlatformList(SupportPlatform platform)
    {
        return platformMapper.selectSupportPlatformList(platform);
    }

    @Override
    public List<SupportPlatform> selectPlatformTreeBySiteId(Long siteId)
    {
        return buildPlatformTree(platformMapper.selectPlatformsBySiteId(siteId));
    }

    @Override
    public int insertSupportPlatform(SupportPlatform platform)
    {
        validatePlatform(platform, false);
        platform.setCreateTime(DateUtils.getNowDate());
        int rows = platformMapper.insertSupportPlatform(platform);
        if (rows > 0)
        {
            changeLogService.record(platform.getSiteId(), "INSERT", "PLATFORM", platform.getPlatformId(), platform.getPlatformName(), "新增平台 " + platform.getPlatformName(), null, platform);
        }
        return rows;
    }

    @Override
    public int updateSupportPlatform(SupportPlatform platform)
    {
        SupportPlatform original = platformMapper.selectSupportPlatformByPlatformId(platform.getPlatformId());
        validatePlatform(platform, true);
        platform.setUpdateTime(DateUtils.getNowDate());
        int rows = platformMapper.updateSupportPlatform(platform);
        if (rows > 0)
        {
            changeLogService.record(platform.getSiteId(), "UPDATE", "PLATFORM", platform.getPlatformId(), platform.getPlatformName(), "修改平台 " + platform.getPlatformName(), original, platform);
        }
        return rows;
    }

    @Override
    public int deleteSupportPlatformByPlatformIds(Long[] platformIds)
    {
        List<SupportPlatform> deletedPlatforms = new ArrayList<>();
        for (Long platformId : platformIds)
        {
            SupportPlatform platform = platformMapper.selectSupportPlatformByPlatformId(platformId);
            if (platformMapper.countChildPlatforms(platformId) > 0)
            {
                throw new ServiceException("存在子平台，不能删除主平台");
            }
            if (platform != null)
            {
                deletedPlatforms.add(platform);
            }
            platformServerRelMapper.deleteByPlatformId(platformId);
            platformOrgRelMapper.deleteByPlatformId(platformId);
            platformContactRelMapper.deleteByPlatformId(platformId);
        }
        int rows = platformMapper.deleteSupportPlatformByPlatformIds(platformIds);
        if (rows > 0)
        {
            for (SupportPlatform platform : deletedPlatforms)
            {
                changeLogService.record(platform.getSiteId(), "DELETE", "PLATFORM", platform.getPlatformId(), platform.getPlatformName(), "删除平台 " + platform.getPlatformName(), platform, null);
            }
        }
        return rows;
    }

    @Override
    public int bindServer(Long platformId, Long serverId)
    {
        SupportPlatform platform = requirePlatform(platformId);
        ensureSubPlatform(platform);
        if (platformServerRelMapper.countByPlatformAndServer(platformId, serverId) > 0)
        {
            throw new ServiceException("该子平台已绑定当前服务器");
        }
        SupportServer server = serverMapper.selectSupportServerByServerId(serverId);
        if (server == null)
        {
            throw new ServiceException("服务器不存在");
        }
        if (!platform.getSiteId().equals(server.getSiteId()))
        {
            throw new ServiceException("仅允许绑定同一现场下的服务器");
        }
        SupportPlatformServerRel rel = new SupportPlatformServerRel();
        rel.setPlatformId(platformId);
        rel.setServerId(serverId);
        rel.setCreateTime(DateUtils.getNowDate());
        int rows = platformServerRelMapper.insertSupportPlatformServerRel(rel);
        if (rows > 0)
        {
            changeLogService.record(platform.getSiteId(), "BIND", "SERVER", serverId, server.getServerName(), "子平台 " + platform.getPlatformName() + " 绑定服务器 " + server.getServerName());
        }
        return rows;
    }

    @Override
    public int unbindServer(Long platformId, Long serverId)
    {
        SupportPlatform platform = requirePlatform(platformId);
        SupportServer server = serverMapper.selectSupportServerByServerId(serverId);
        int rows = platformServerRelMapper.deleteSupportPlatformServerRel(platformId, serverId);
        if (rows > 0)
        {
            String serverName = server == null ? String.valueOf(serverId) : server.getServerName();
            changeLogService.record(platform.getSiteId(), "UNBIND", "SERVER", serverId, serverName, "平台 " + platform.getPlatformName() + " 解绑服务器 " + serverName);
        }
        return rows;
    }

    @Override
    public int bindContact(Long platformId, Long contactId)
    {
        SupportPlatform platform = requirePlatform(platformId);
        ensureMainPlatform(platform);
        if (platformContactRelMapper.countByPlatformAndContact(platformId, contactId) > 0)
        {
            throw new ServiceException("该主平台已绑定当前联系人");
        }
        SupportContact contact = contactMapper.selectSupportContactByContactId(contactId);
        if (contact == null)
        {
            throw new ServiceException("联系人不存在");
        }
        int rows = platformContactRelMapper.insertSupportPlatformContactRel(
            platformId,
            contactId,
            SecurityUtils.getUsername(),
            DateUtils.getNowDate()
        );
        if (rows > 0)
        {
            changeLogService.record(platform.getSiteId(), "BIND", "CONTACT", contactId, contact.getContactName(), "主平台 " + platform.getPlatformName() + " 关联人员 " + contact.getContactName());
        }
        return rows;
    }

    @Override
    public int unbindContact(Long platformId, Long contactId)
    {
        SupportPlatform platform = requirePlatform(platformId);
        SupportContact contact = contactMapper.selectSupportContactByContactId(contactId);
        int rows = platformContactRelMapper.deleteSupportPlatformContactRel(platformId, contactId);
        if (rows > 0)
        {
            String contactName = contact == null ? String.valueOf(contactId) : contact.getContactName();
            changeLogService.record(platform.getSiteId(), "UNBIND", "CONTACT", contactId, contactName, "主平台 " + platform.getPlatformName() + " 取消关联人员 " + contactName);
        }
        return rows;
    }

    @Override
    public List<SupportServer> listServersByPlatformId(Long platformId)
    {
        SupportPlatform platform = requirePlatform(platformId);
        if (LEVEL_MAIN.equalsIgnoreCase(platform.getPlatformLevel()))
        {
            return listServersByMainPlatform(platform);
        }
        return serverMapper.selectServersByPlatformId(platformId);
    }

    private List<SupportServer> listServersByMainPlatform(SupportPlatform mainPlatform)
    {
        Map<Long, SupportServer> serverMap = new LinkedHashMap<>();
        List<SupportPlatform> platforms = platformMapper.selectPlatformsBySiteId(mainPlatform.getSiteId());
        for (SupportPlatform item : platforms)
        {
            if (!mainPlatform.getPlatformId().equals(item.getParentPlatformId()))
            {
                continue;
            }
            List<SupportServer> servers = serverMapper.selectServersByPlatformId(item.getPlatformId());
            for (SupportServer server : servers)
            {
                serverMap.putIfAbsent(server.getServerId(), server);
            }
        }
        return new ArrayList<>(serverMap.values());
    }

    @Override
    public List<SupportContact> listContactsByPlatformId(Long platformId)
    {
        SupportPlatform platform = requirePlatform(platformId);
        if (!LEVEL_MAIN.equalsIgnoreCase(platform.getPlatformLevel()))
        {
            return Collections.emptyList();
        }
        return contactMapper.selectContactsByPlatformId(platformId);
    }

    private void validatePlatform(SupportPlatform platform, boolean update)
    {
        if (update)
        {
            requirePlatform(platform.getPlatformId());
        }
        if (!LEVEL_MAIN.equalsIgnoreCase(platform.getPlatformLevel()) && !LEVEL_SUB.equalsIgnoreCase(platform.getPlatformLevel()))
        {
            throw new ServiceException("platformLevel 仅允许 MAIN 或 SUB");
        }
        if (LEVEL_MAIN.equalsIgnoreCase(platform.getPlatformLevel()))
        {
            if (StringUtils.isBlank(platform.getNetworkEnv()))
            {
                throw new ServiceException("主平台必须选择网络环境");
            }
            platform.setNetworkEnv(platform.getNetworkEnv().trim());
            platform.setParentPlatformId(null);
            return;
        }
        platform.setNetworkEnv(null);
        if (platform.getParentPlatformId() == null)
        {
            throw new ServiceException("子平台必须指定父平台");
        }
        if (update && platform.getPlatformId().equals(platform.getParentPlatformId()))
        {
            throw new ServiceException("父平台不能为自己");
        }
        SupportPlatform parent = platformMapper.selectSupportPlatformByPlatformId(platform.getParentPlatformId());
        if (parent == null)
        {
            throw new ServiceException("父平台不存在");
        }
        if (!LEVEL_MAIN.equalsIgnoreCase(parent.getPlatformLevel()))
        {
            throw new ServiceException("父平台必须是主平台");
        }
        if (!platform.getSiteId().equals(parent.getSiteId()))
        {
            throw new ServiceException("父子平台必须属于同一现场");
        }
    }

    private SupportPlatform requirePlatform(Long platformId)
    {
        SupportPlatform platform = platformMapper.selectSupportPlatformByPlatformId(platformId);
        if (platform == null)
        {
            throw new ServiceException("平台不存在");
        }
        return platform;
    }

    private void ensureMainPlatform(SupportPlatform platform)
    {
        if (!LEVEL_MAIN.equalsIgnoreCase(platform.getPlatformLevel()))
        {
            throw new ServiceException("仅主平台允许关联联系人");
        }
    }

    private void ensureSubPlatform(SupportPlatform platform)
    {
        if (!LEVEL_SUB.equalsIgnoreCase(platform.getPlatformLevel()))
        {
            throw new ServiceException("服务器仅允许绑定到子平台，主平台展示其下子平台服务器汇总");
        }
    }

    private List<SupportPlatform> buildPlatformTree(List<SupportPlatform> list)
    {
        List<SupportPlatform> roots = new ArrayList<>();
        for (SupportPlatform item : list)
        {
            item.setChildren(new ArrayList<>());
            if (item.getParentPlatformId() == null)
            {
                roots.add(item);
            }
        }
        for (SupportPlatform item : list)
        {
            if (item.getParentPlatformId() == null)
            {
                continue;
            }
            for (SupportPlatform parent : list)
            {
                if (item.getParentPlatformId().equals(parent.getPlatformId()))
                {
                    parent.getChildren().add(item);
                    break;
                }
            }
        }
        return roots;
    }
}
