package com.hm.manage.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.hm.common.exception.ServiceException;
import com.hm.manage.domain.SupportHardwareAsset;
import com.hm.manage.domain.SupportServer;
import com.hm.manage.domain.SupportServerCredential;
import com.hm.manage.domain.bo.SupportEquipmentBatchBo;
import com.hm.manage.domain.bo.SupportEquipmentDeviceRefBo;
import com.hm.manage.service.ISupportHardwareAssetService;
import com.hm.manage.service.ISupportServerService;

@Service
public class SupportEquipmentCredentialService
{
    @Autowired
    private ISupportServerService serverService;
    @Autowired
    private ISupportHardwareAssetService hardwareAssetService;

    public List<Map<String, Object>> readPlain(SupportEquipmentBatchBo command)
    {
        if (command == null || command.getSiteId() == null || command.getDevices() == null
            || command.getDevices().isEmpty() || command.getDevices().size() > 100)
        {
            throw new ServiceException("请选择当前现场的1至100台设备");
        }
        Map<String, Object> devices = new LinkedHashMap<>();
        // Validate every reference before decrypting any credential.
        for (SupportEquipmentDeviceRefBo ref : command.getDevices())
        {
            if (ref == null || ref.getSourceId() == null)
                throw new ServiceException("设备标识不能为空");
            Object device;
            Long siteId;
            if ("SERVER".equals(ref.getSourceType()))
            {
                SupportServer server = serverService.selectSupportServerByServerId(ref.getSourceId());
                device = server;
                siteId = server == null ? null : server.getSiteId();
            }
            else if ("HARDWARE".equals(ref.getSourceType()))
            {
                SupportHardwareAsset asset = hardwareAssetService.selectSupportHardwareAssetByAssetId(ref.getSourceId());
                device = asset;
                siteId = asset == null ? null : asset.getSiteId();
            }
            else throw new ServiceException("不支持的设备类型");
            if (!Objects.equals(command.getSiteId(), siteId))
                throw new ServiceException("设备不存在或不属于当前现场");
            devices.put(ref.getSourceType() + ":" + ref.getSourceId(), device);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        devices.forEach((key, device) -> {
            List<Map<String, Object>> credentials = new ArrayList<>();
            if (device instanceof SupportServer server)
            {
                for (SupportServerCredential credential : serverService.selectServerCredentialList(server.getServerId()))
                {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("username", credential.getUsername());
                    item.put("name", credential.getCredentialName());
                    item.put("password", serverService.getServerCredentialPasswordPlain(credential.getCredentialId()));
                    credentials.add(item);
                }
                for (Map<String, Object> summary : serverService.selectServerCredentialPlainSummaries(new Long[] { server.getServerId() }))
                {
                    appendLegacy(credentials, "hik", summary.get("hikPassword"));
                    appendLegacy(credentials, "root", summary.get("rootPassword"));
                    appendLegacy(credentials, (String) summary.get("otherUsername"), summary.get("otherPassword"));
                }
            }
            else if (device instanceof SupportHardwareAsset asset)
            {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("username", asset.getLoginUsername());
                item.put("name", "设备登录");
                item.put("password", hardwareAssetService.getHardwareAssetPasswordPlain(asset.getAssetId()));
                credentials.add(item);
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("deviceKey", key);
            row.put("credentials", credentials);
            result.add(row);
        });
        return result;
    }

    private void appendLegacy(List<Map<String, Object>> credentials, String username, Object password)
    {
        if (username == null || username.isBlank() || password == null || password.toString().isEmpty()
            || credentials.stream().anyMatch(item -> username.equals(item.get("username")))) return;
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("username", username);
        item.put("name", "系统登录");
        item.put("password", password);
        credentials.add(item);
    }
}
