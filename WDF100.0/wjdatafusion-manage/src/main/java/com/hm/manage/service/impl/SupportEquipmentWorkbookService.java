package com.hm.manage.service.impl;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.util.*;
import com.alibaba.fastjson2.JSON;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.hm.common.exception.ServiceException;
import com.hm.common.utils.ip.IpUtils;
import com.hm.framework.web.service.PermissionService;
import com.hm.manage.domain.*;
import com.hm.manage.domain.bo.SupportEquipmentWorkbookBo;
import com.hm.manage.mapper.*;
import com.hm.manage.service.ISupportChangeLogService;
import com.hm.manage.service.support.CredentialCryptoService;

@Service
public class SupportEquipmentWorkbookService
{
    private static final Set<String> COMMON = Set.of("assetName", "ipAddress", "status", "remark",
        "platformIds", "roomId", "cabinetId", "rackUStart", "rackUEnd");
    private static final Set<String> SERVER = Set.of("sshPort", "rootDirectLogin", "osType", "hikPassword", "rootPassword", "otherUsername", "otherPassword");
    private static final Set<String> HARDWARE = Set.of("manageIp", "manufacturer", "assetModel", "loginUsername", "loginPassword",
        "networkEnv", "macAddress", "serialNo", "ownerOrg", "ownerContact", "channelCount", "outputType",
        "terminalType", "department", "useLocation", "switchLevel", "portCount", "uplinkDevice", "vlanInfo",
        "gatewayMode", "gatewayDirection", "gatewayBandwidth", "securityZone");
    private static final Set<String> LOCATION = Set.of("roomId", "cabinetId", "rackUStart", "rackUEnd");
    private static final Set<String> PASSWORDS = Set.of("hikPassword", "rootPassword", "otherPassword", "loginPassword");
    private static final Map<String, Integer> LENGTHS = Map.ofEntries(
        Map.entry("assetName", 120),
        Map.entry("ipAddress", 255),
        Map.entry("manageIp", 255),
        Map.entry("osType", 64),
        Map.entry("networkEnv", 100),
        Map.entry("macAddress", 64),
        Map.entry("manufacturer", 100),
        Map.entry("assetModel", 120),
        Map.entry("serialNo", 120),
        Map.entry("ownerOrg", 160),
        Map.entry("ownerContact", 80),
        Map.entry("loginUsername", 128),
        Map.entry("otherUsername", 128),
        Map.entry("remark", 500),
        Map.entry("outputType", 80),
        Map.entry("terminalType", 80),
        Map.entry("department", 120),
        Map.entry("useLocation", 200),
        Map.entry("switchLevel", 80),
        Map.entry("uplinkDevice", 160),
        Map.entry("vlanInfo", 500),
        Map.entry("gatewayMode", 80),
        Map.entry("gatewayDirection", 120),
        Map.entry("gatewayBandwidth", 80),
        Map.entry("securityZone", 200));
    @Autowired private SupportEquipmentWorkbookMapper mapper;
    @Autowired private SupportEquipmentLocationMapper locations;
    @Autowired private SupportSiteMapper sites;
    @Autowired private SupportPlatformServerRelMapper serverRelations;
    @Autowired private SupportPlatformAssetRelMapper hardwareRelations;
    @Autowired private SupportServerCredentialMapper credentials;
    @Autowired private CredentialCryptoService crypto;
    @Autowired private ISupportChangeLogService logs;
    @Autowired private PermissionService permissions;

    public Map<String, Object> snapshot(Long siteId)
    {
        if (siteId == null || siteId <= 0) throw new ServiceException("请选择现场");
        Context context = load(siteId, false);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> row : context.rows.values())
        {
            Map<String, Object> visible = new LinkedHashMap<>();
            row.forEach((key, value) -> { if (!key.startsWith("_")) visible.put(key, value); });
            visible.put("revision", revision(row));
            visible.put("canEdit", canEdit(text(row.get("sourceType"))));
            rows.add(visible);
        }
        return Map.of("rows", rows, "rooms", context.rooms, "cabinets", context.cabinets, "platforms", context.platforms, "networks", context.networks);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> save(SupportEquipmentWorkbookBo command, String actor)
    {
        if (command == null || command.getSiteId() == null || command.getRows() == null
            || command.getRows().isEmpty() || command.getRows().size() > 500)
            throw new ServiceException("单次请选择1至500台设备保存");
        if (sites.selectSiteIdForUpdate(command.getSiteId()) == null) throw new ServiceException("现场不存在");
        mapper.lockRooms(command.getSiteId());
        mapper.lockCabinets(command.getSiteId());
        Context context = load(command.getSiteId(), true);
        List<Prepared> prepared = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (SupportEquipmentWorkbookBo.Row input : command.getRows())
        {
            if (input == null || input.getSourceId() == null || input.getSourceId() <= 0
                || !Set.of("SERVER", "HARDWARE").contains(input.getSourceType() == null ? "" : input.getSourceType()))
                throw new ServiceException("设备标识无效");
            if (!canEdit(input.getSourceType())) throw new AccessDeniedException("无权编辑清单中的设备类型");
            String key = input.getSourceType() + ":" + input.getSourceId();
            if (!seen.add(key)) throw new ServiceException("设备重复提交");
            Map<String, Object> original = context.rows.get(key);
            if (original == null) throw new ServiceException("设备不存在或不属于当前现场");
            if (!Objects.equals(revision(original), input.getRevision()))
                throw new ServiceException(label(original) + "已被修改，请保留草稿并重新加载后核对");
            Map<String, Object> fields = normalize(input.getChanges(), original);
            Map<String, Object> after = new LinkedHashMap<>(original);
            after.putAll(fields);
            validateReferences(context, after, fields);
            validateCredentials(after, fields);
            prepared.add(new Prepared(original, after, fields));
        }

        // Validate the final whole-site state so coordinated U-position changes can be saved together.
        Map<String, Map<String, Object>> finalRows = new LinkedHashMap<>(context.rows);
        prepared.forEach(row -> finalRows.put(text(row.after.get("deviceKey")), row.after));
        for (Prepared row : prepared) validateCollisions(row, finalRows.values());
        Date now = new Date();
        for (Prepared row : prepared)
        {
            Map<String, Object> persisted = new LinkedHashMap<>(row.fields);
            persistCredentials(row, persisted, actor, now);
            PASSWORDS.forEach(persisted::remove);
            Long id = number(row.after.get("sourceId"));
            int count = isServer(row.after)
                ? mapper.updateServer(command.getSiteId(), id, persisted, actor, now)
                : mapper.updateHardware(command.getSiteId(), id, persisted, actor, now);
            if (count != 1) throw new ServiceException("设备保存失败，本次修改未保存");
            if (row.fields.containsKey("platformIds")) persistPlatforms(row.after, actor, now);
            Map<String, Object> beforeDetail = audit(row.before, row.fields);
            Map<String, Object> afterDetail = audit(row.after, row.fields);
            List<String> credentialChanges = row.fields.keySet().stream().filter(PASSWORDS::contains)
                .map(key -> key + (row.fields.get(key) == null ? "：已清空" : "：已更新")).toList();
            if (!credentialChanges.isEmpty()) afterDetail.put("凭据变更", credentialChanges);
            logs.record(command.getSiteId(), "UPDATE", isServer(row.after) ? "SERVER" : "HARDWARE_ASSET",
                id, text(row.after.get("assetName")), "清单编辑设备 " + text(row.after.get("assetName")), beforeDetail, afterDetail);
        }
        return Map.of("updated", prepared.size());
    }

    private Context load(Long siteId, boolean lock)
    {
        Context context = new Context();
        context.rooms = locations.selectRoomsBySiteId(siteId);
        context.cabinets = locations.selectCabinetsBySiteId(siteId);
        context.platforms = mapper.selectPlatforms(siteId, lock);
        context.networks = mapper.selectNetworks();
        List<Map<String, Object>> all = new ArrayList<>(mapper.selectServers(siteId, lock));
        all.addAll(mapper.selectHardware(siteId, lock));
        List<Map<String, Object>> allCredentials = mapper.selectServerCredentials(siteId, lock);
        List<Map<String, Object>> serverBindings = mapper.selectServerBindings(siteId, lock);
        List<Map<String, Object>> hardwareBindings = mapper.selectHardwareBindings(siteId, lock);
        for (Map<String, Object> raw : all)
        {
            Map<String, Object> row = new LinkedHashMap<>(raw);
            Long id = number(row.get("sourceId"));
            String key = text(row.get("sourceType")) + ":" + id;
            row.put("deviceKey", key);
            if (isServer(row)) row.put("rootDirectLogin", Boolean.TRUE.equals(row.get("rootDirectLogin")) || "1".equals(text(row.get("rootDirectLogin"))));
            List<Long> ids = (isServer(row) ? serverBindings : hardwareBindings).stream()
                .filter(binding -> Objects.equals(id, number(binding.get("sourceId"))))
                .map(binding -> number(binding.get("platformId"))).distinct().sorted().toList();
            row.put("platformIds", ids);
            SupportEquipmentRoom room = context.rooms.stream()
                .filter(item -> same(item.getRoomName(), row.get("equipmentRoom"))).findFirst().orElse(null);
            SupportEquipmentCabinet cabinet = room == null ? null : context.cabinets.stream()
                .filter(item -> Objects.equals(item.getRoomId(), room.getRoomId()) && same(item.getCabinetNo(), row.get("cabinetNo")))
                .findFirst().orElse(null);
            row.put("roomId", room == null ? null : room.getRoomId());
            row.put("cabinetId", cabinet == null ? null : cabinet.getCabinetId());
            List<Map<String, Object>> accountRows = isServer(row) ? allCredentials.stream()
                .filter(item -> Objects.equals(id, number(item.get("serverId")))).toList() : List.of();
            row.put("_credentials", accountRows);
            for (String field : List.of("hikPassword", "rootPassword", "otherPassword"))
            {
                Map<String, Object> account = accountFor(row, field);
                row.put(field + "Configured", !text(account.get("cipher")).isEmpty());
                row.put(field + "AccountName", text(account.get("credentialName")));
                String username = "hikPassword".equals(field) ? "hik" : "root";
                row.put(field + "Editable", "otherPassword".equals(field)
                    || accountRows.stream().filter(item -> same(username, item.get("username"))).count() <= 1);
                if ("otherPassword".equals(field)) row.put("otherUsername", text(account.get("username")));
            }
            row.put("loginPasswordConfigured", !text(row.get("_passwordCipher")).isEmpty());
            context.rows.put(key, row);
        }
        return context;
    }

    private Map<String, Object> normalize(Map<String, Object> changes, Map<String, Object> row)
    {
        if (changes == null || changes.isEmpty()) throw new ServiceException("没有需要保存的修改");
        Map<String, Object> result = new LinkedHashMap<>();
        for (var entry : changes.entrySet())
        {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (!COMMON.contains(key) && !(isServer(row) ? SERVER : HARDWARE).contains(key))
                throw new ServiceException("该设备不支持所提交的字段");
            if (PASSWORDS.contains(key))
            {
                if (value != null && (!(value instanceof String) || ((String) value).isEmpty()
                    || ((String) value).length() > 128 || "******".equals(value)))
                    throw new ServiceException("新密码应为1至128个字符，不能使用占位符；清空请使用清空操作");
            }
            else if ("platformIds".equals(key))
            {
                if (!(value instanceof List<?> list) || list.size() > 100) throw new ServiceException("平台归属格式无效");
                List<Long> ids = list.stream().map(SupportEquipmentWorkbookService::number).toList();
                if (ids.stream().anyMatch(id -> id == null || id <= 0)) throw new ServiceException("请选择有效平台");
                value = ids.stream().distinct().sorted().toList();
            }
            else if ("rootDirectLogin".equals(key))
            {
                if (!(value instanceof Boolean)) throw new ServiceException("root直接登录必须是开启或关闭");
            }
            else if (LOCATION.contains(key) || Set.of("sshPort", "channelCount", "portCount").contains(key)) value = number(value);
            else
            {
                if (value != null && !(value instanceof String)) throw new ServiceException("文本字段格式无效");
                value = text(value).trim();
                int max = LENGTHS.getOrDefault(key, 128);
                if (value.toString().length() > max) throw new ServiceException("字段长度超过限制：" + key);
            }
            if ("assetName".equals(key) && text(value).isEmpty()) throw new ServiceException("设备名称不能为空");
            if ("ipAddress".equals(key) && text(value).isEmpty()) throw new ServiceException("设备IP/地址不能为空");
            if ("ipAddress".equals(key) && text(value).matches("[0-9.]+") && !IpUtils.isIP(text(value)))
                throw new ServiceException("IPv4地址格式不正确");
            if (Set.of("ipAddress", "manageIp").contains(key) && text(value).matches(".*\\s+.*"))
                throw new ServiceException("IP/地址不能包含空白字符");
            if ("status".equals(key) && !Set.of("0", "1").contains(text(value))) throw new ServiceException("请选择正常或停用状态");
            if ("sshPort".equals(key) && (value == null || (Long) value < 1 || (Long) value > 65535))
                throw new ServiceException("SSH端口必须是1至65535的整数");
            if (Set.of("channelCount", "portCount").contains(key) && value != null && ((Long) value < 0 || (Long) value > 65535))
                throw new ServiceException("端口数、通道数必须是0至65535的整数");
            result.put(key, value);
        }
        return result;
    }

    private void validateReferences(Context context, Map<String, Object> row, Map<String, Object> fields)
    {
        if (fields.containsKey("networkEnv") && context.networks.stream().noneMatch(item -> Objects.equals(text(item.get("value")), row.get("networkEnv"))))
            throw new ServiceException("请选择有效的网络环境");
        if (fields.containsKey("platformIds"))
        {
            if (!permissions.hasPermi("support:equipment:edit")) throw new AccessDeniedException("无权修改设备平台归属");
            for (Long id : ids(row))
            {
                Map<String, Object> platform = context.platforms.stream()
                    .filter(item -> Objects.equals(number(item.get("platformId")), id)).findFirst().orElse(null);
                if (platform == null || (isServer(row) && !"SUB".equals(platform.get("platformLevel"))))
                    throw new ServiceException("请选择当前现场的平台；服务器只能归属子平台");
            }
        }
        if (Collections.disjoint(fields.keySet(), LOCATION)) return;
        if (!permissions.hasPermi("support:equipment:edit") && !permissions.hasPermi("support:hardwareAsset:edit"))
            throw new AccessDeniedException("无权修改设备安装位置");
        Long roomId = number(row.get("roomId")), cabinetId = number(row.get("cabinetId"));
        Long start = number(row.get("rackUStart")), end = number(row.get("rackUEnd"));
        if (roomId == null && cabinetId == null && start == null && end == null)
        {
            for (String field : List.of("equipmentRoom", "cabinetNo", "rackUStart", "rackUEnd"))
            { fields.put(field, null); row.put(field, null); }
            return;
        }
        SupportEquipmentRoom room = context.rooms.stream().filter(item -> Objects.equals(item.getRoomId(), roomId)).findFirst().orElse(null);
        SupportEquipmentCabinet cabinet = context.cabinets.stream().filter(item -> Objects.equals(item.getCabinetId(), cabinetId)).findFirst().orElse(null);
        if (room == null || cabinet == null || !Objects.equals(cabinet.getRoomId(), roomId) || start == null || end == null)
            throw new ServiceException("请完整选择当前现场的机房、机柜和U位");
        int capacity = cabinet.getUCapacity() == null ? 45 : cabinet.getUCapacity();
        if (start < 1 || end < start || end > capacity) throw new ServiceException("U位必须在1至" + capacity + "之间且起点不大于终点");
        fields.put("equipmentRoom", room.getRoomName()); fields.put("cabinetNo", cabinet.getCabinetNo());
        fields.put("rackUStart", start); fields.put("rackUEnd", end);
        row.putAll(fields);
    }

    private void validateCredentials(Map<String, Object> row, Map<String, Object> fields)
    {
        if (!isServer(row)) return;
        for (String field : List.of("hikPassword", "rootPassword"))
            if (fields.containsKey(field) && Boolean.FALSE.equals(row.get(field + "Editable")))
                throw new ServiceException("同一账号存在多份凭据，请在设备配置的凭据管理中选择具体账号");
        if (fields.containsKey("otherUsername") || fields.containsKey("otherPassword"))
        {
            String username = text(row.get("otherUsername")).trim();
            if (username.isEmpty() || Set.of("hik", "root").contains(username.toLowerCase(Locale.ROOT)))
                throw new ServiceException("其他账号必须填写非hik/root的用户名");
            Map<String, Object> existing = accountFor(row, "otherPassword");
            if (!fields.containsKey("otherPassword") && text(existing.get("cipher")).isEmpty())
                throw new ServiceException("新增其他账号时请同时设置密码");
        }
    }

    private void validateCollisions(Prepared candidate, Collection<Map<String, Object>> all)
    {
        Map<String, Object> row = candidate.after;
        for (Map<String, Object> other : all)
        {
            if (Objects.equals(row.get("deviceKey"), other.get("deviceKey"))) continue;
            if (candidate.fields.containsKey("ipAddress") && !text(row.get("ipAddress")).isEmpty()
                && Objects.equals(row.get("sourceType"), other.get("sourceType")) && same(row.get("ipAddress"), other.get("ipAddress")))
                throw new ServiceException(label(row) + "的IP/地址与其他同类设备重复");
            if (!Collections.disjoint(candidate.fields.keySet(), LOCATION) && number(row.get("rackUStart")) != null
                && number(other.get("rackUStart")) != null && number(other.get("rackUEnd")) != null
                && same(row.get("equipmentRoom"), other.get("equipmentRoom")) && same(row.get("cabinetNo"), other.get("cabinetNo"))
                && number(row.get("rackUStart")) <= number(other.get("rackUEnd"))
                && number(other.get("rackUStart")) <= number(row.get("rackUEnd")))
                throw new ServiceException(label(row) + "与" + label(other) + "的U位冲突");
        }
    }

    private void persistCredentials(Prepared row, Map<String, Object> fields, String actor, Date now)
    {
        if (!isServer(row.after))
        {
            if (fields.containsKey("loginPassword")) fields.put("_passwordCipher", crypto.encrypt((String) fields.get("loginPassword")));
            return;
        }
        for (String field : List.of("hikPassword", "rootPassword", "otherPassword"))
        {
            boolean passwordChanged = fields.containsKey(field);
            if (!passwordChanged && !("otherPassword".equals(field) && fields.containsKey("otherUsername"))) continue;
            Map<String, Object> existing = accountFor(row.before, field);
            String username = "hikPassword".equals(field) ? "hik" : "rootPassword".equals(field) ? "root" : text(row.after.get("otherUsername")).trim();
            String cipher = passwordChanged ? crypto.encrypt((String) fields.get(field)) : text(existing.get("cipher"));
            Long credentialId = number(existing.get("credentialId"));
            if (credentialId != null)
            {
                if (mapper.updateCredential(number(row.after.get("sourceId")), credentialId, username, cipher, actor, now) != 1)
                    throw new ServiceException("账号保存失败");
            }
            else if (!cipher.isEmpty())
            {
                SupportServerCredential credential = new SupportServerCredential();
                credential.setServerId(number(row.after.get("sourceId")));
                credential.setCredentialName("otherPassword".equals(field) ? "其他账号" : username + "账号");
                credential.setUsername(username); credential.setPasswordCipher(cipher);
                credential.setStatus("0"); credential.setIsDefault("0");
                credential.setCreateBy(actor); credential.setCreateTime(now);
                credential.setUpdateBy(actor); credential.setUpdateTime(now);
                if (credentials.insertCredential(credential) != 1) throw new ServiceException("账号保存失败");
            }
            String legacyName = text(row.before.get("_legacyUsername"));
            if (!legacyName.isEmpty() && same(legacyName, existing.get("username")))
            {
                fields.put("_legacyCipher", cipher);
                fields.put("_legacyUsername", username);
            }
        }
    }

    private void persistPlatforms(Map<String, Object> row, String actor, Date now)
    {
        Long id = number(row.get("sourceId"));
        if (isServer(row)) serverRelations.deleteByServerId(id);
        else hardwareRelations.deleteByAssetId(id);
        for (Long platformId : ids(row))
        {
            if (isServer(row))
            {
                SupportPlatformServerRel rel = new SupportPlatformServerRel();
                rel.setServerId(id); rel.setPlatformId(platformId); rel.setCreateBy(actor); rel.setCreateTime(now);
                if (serverRelations.insertSupportPlatformServerRel(rel) != 1) throw new ServiceException("平台归属保存失败");
            }
            else
            {
                SupportPlatformAssetRel rel = new SupportPlatformAssetRel();
                rel.setAssetId(id); rel.setPlatformId(platformId); rel.setCreateBy(actor); rel.setCreateTime(now);
                if (hardwareRelations.insertSupportPlatformAssetRel(rel) != 1) throw new ServiceException("平台归属保存失败");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> accountFor(Map<String, Object> row, String field)
    {
        List<Map<String, Object>> accounts = (List<Map<String, Object>>) row.getOrDefault("_credentials", List.of());
        boolean other = "otherPassword".equals(field);
        String username = "hikPassword".equals(field) ? "hik" : "root";
        Map<String, Object> account = accounts.stream().filter(item -> other
            ? "其他账号".equals(item.get("credentialName")) : same(username, item.get("username"))).findFirst().orElse(null);
        String legacyName = text(row.get("_legacyUsername"));
        if (account != null) return account;
        if (other) account = accounts.stream().filter(item -> !Set.of("hik", "root").contains(text(item.get("username")).toLowerCase(Locale.ROOT)))
            .findFirst().orElse(null);
        if (account != null) return account;
        if (!legacyName.isEmpty() && (other ? !Set.of("hik", "root").contains(legacyName.toLowerCase(Locale.ROOT)) : same(username, legacyName)))
            return Map.of("username", legacyName, "cipher", text(row.get("_legacyCipher")));
        return Map.of();
    }

    private boolean canEdit(String type)
    {
        return permissions.hasPermi("support:equipment:edit")
            || permissions.hasPermi("support:" + ("SERVER".equals(type) ? "server" : "hardwareAsset") + ":edit");
    }

    private String revision(Map<String, Object> row)
    {
        try
        {
            byte[] bytes = JSON.toJSONBytes(new TreeMap<>(row));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        }
        catch (Exception exception) { throw new ServiceException("无法生成设备编辑版本"); }
    }

    private Map<String, Object> audit(Map<String, Object> row, Map<String, Object> fields)
    {
        Map<String, Object> detail = new LinkedHashMap<>();
        fields.keySet().stream().filter(key -> !PASSWORDS.contains(key) && !key.startsWith("_"))
            .forEach(key -> detail.put(key, row.get(key)));
        return detail;
    }

    @SuppressWarnings("unchecked")
    private static List<Long> ids(Map<String, Object> row) { return (List<Long>) row.getOrDefault("platformIds", List.of()); }
    private static boolean isServer(Map<String, Object> row) { return "SERVER".equals(row.get("sourceType")); }
    private static String label(Map<String, Object> row) { return text(row.get("assetName")) + " (" + text(row.get("ipAddress")) + ")"; }
    private static String text(Object value) { return value == null ? "" : value.toString(); }
    private static boolean same(Object a, Object b) { return text(a).trim().equalsIgnoreCase(text(b).trim()); }
    private static Long number(Object value)
    {
        if (value == null || "".equals(value)) return null;
        try { return new BigDecimal(value.toString()).longValueExact(); }
        catch (RuntimeException exception) { throw new ServiceException("编号、端口与U位必须为整数"); }
    }

    private record Prepared(Map<String, Object> before, Map<String, Object> after, Map<String, Object> fields) {}
    private static class Context
    {
        Map<String, Map<String, Object>> rows = new LinkedHashMap<>();
        List<SupportEquipmentRoom> rooms;
        List<SupportEquipmentCabinet> cabinets;
        List<Map<String, Object>> platforms;
        List<Map<String, Object>> networks;
    }
}
