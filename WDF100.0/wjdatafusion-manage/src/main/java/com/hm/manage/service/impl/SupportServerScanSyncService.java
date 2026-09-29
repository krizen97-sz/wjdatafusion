package com.hm.manage.service.impl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.hm.common.exception.ServiceException;
import com.hm.manage.domain.SupportServer;
import com.hm.manage.domain.bo.SupportEquipmentCreateBo;
import com.hm.manage.domain.bo.SupportEquipmentWorkbookBo;
import com.hm.manage.mapper.SupportServerMapper;
import com.hm.manage.mapper.SupportServerScanMapper;
import com.hm.manage.mapper.SupportSiteMapper;
import com.hm.manage.service.ISupportChangeLogService;
import com.hm.manage.service.ISupportEquipmentService;
import com.hm.manage.service.support.SupportServerScanProbe.Facts;
import com.hm.manage.service.support.SupportServerScanTargets;

@Service
public class SupportServerScanSyncService
{
    private final SupportSiteMapper sites;
    private final SupportServerMapper servers;
    private final ISupportEquipmentService equipment;
    private final SupportEquipmentWorkbookService workbook;
    private final SupportServerScanMapper profiles;
    private final ISupportChangeLogService logs;
    public SupportServerScanSyncService(SupportSiteMapper sites, SupportServerMapper servers,
        ISupportEquipmentService equipment, SupportEquipmentWorkbookService workbook, SupportServerScanMapper profiles, ISupportChangeLogService logs)
    { this.sites = sites; this.servers = servers; this.equipment = equipment; this.workbook = workbook; this.profiles = profiles; this.logs = logs; }

    @Transactional(rollbackFor = Exception.class)
    public Long sync(Long siteId, Long platformId, String address, Long expectedId, String revision,
        int port, boolean direct, String hikPassword, String rootPassword, Facts facts, String detectedPorts, String actor)
    {
        if (sites.selectSiteIdForUpdate(siteId) == null) throw new ServiceException("现场已不存在，停止同步");
        SupportServer current = expectedId == null ? servers.selectSupportServerBySiteAndAddress(siteId, address)
            : servers.selectSupportServerByServerIdForUpdate(expectedId);
        Long id;
        if (expectedId == null)
        {
            if (current != null || profiles.countAddressOwners(siteId, address) > 0)
                throw new ServiceException("扫描期间同IP设备已新增，请重新扫描核对，未覆盖该记录");
            SupportServer server = new SupportServer();
            server.setServerName(facts.hostName().isEmpty() ? "服务器-" + address : facts.hostName().substring(0, Math.min(120, facts.hostName().length())));
            server.setServerAddress(address); server.setSshPort(port); server.setRootDirectLogin(direct);
            server.setOsType(facts.osType()); server.setStatus("0");
            server.setHikPassword(direct ? null : hikPassword); server.setRootPassword(rootPassword);
            SupportEquipmentCreateBo create = new SupportEquipmentCreateBo();
            create.setSiteId(siteId); create.setPlatformId(platformId); create.setServer(server);
            id = equipment.createEquipment(create).getSourceId();
        }
        else
        {
            if (current == null || !siteId.equals(current.getSiteId()) || !expectedId.equals(current.getServerId())
                || !address.equals(SupportServerScanTargets.canonical(current.getServerAddress())))
                throw new ServiceException("服务器身份已变化，请重新扫描，未覆盖原记录");
            if (profiles.countAddressOwners(siteId, address) > 1) throw new ServiceException("当前现场同IP存在多条设备记录，未自动覆盖");
            Map<String, Object> changes = new LinkedHashMap<>();
            changes.put("sshPort", port); changes.put("rootDirectLogin", direct); changes.put("rootPassword", rootPassword);
            if (!direct) changes.put("hikPassword", hikPassword);
            if (!facts.osType().isEmpty()) changes.put("osType", facts.osType());
            SupportEquipmentWorkbookBo.Row row = new SupportEquipmentWorkbookBo.Row();
            row.setSourceId(expectedId); row.setSourceType("SERVER"); row.setRevision(revision); row.setChanges(changes);
            SupportEquipmentWorkbookBo update = new SupportEquipmentWorkbookBo();
            update.setSiteId(siteId); update.setRows(List.of(row)); workbook.save(update, actor);
            id = expectedId;
        }
        Map<String, Object> profile = new LinkedHashMap<>(facts.values());
        profile.put("serverId", id); profile.put("siteId", siteId); profile.put("ports", detectedPorts);
        if (facts.warning().isEmpty()) profiles.saveProfile(profile);
        logs.record(siteId, "UPDATE", "SERVER_SCAN", id, address, "扫描验证后自动同步服务器资料",
            null, Map.of("地址", address, "SSH端口", port, "登录路径", direct ? "root直接登录" : "hik切换root",
                "系统资料", facts.values(), "密码", "仅同步验证成功的凭据，不记录内容"));
        return id;
    }
}
