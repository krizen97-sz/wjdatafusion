package com.hm.manage.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.hm.common.exception.ServiceException;
import com.hm.manage.domain.bo.SupportServerScanBo;
import com.hm.manage.mapper.SupportServerScanMapper;
import com.hm.manage.mapper.SupportSiteMapper;
import com.hm.manage.service.ISupportChangeLogService;
import com.hm.manage.service.support.CredentialCryptoService;

@Service
public class SupportServerScanCredentialService
{
    private final SupportServerScanMapper mapper;
    private final SupportSiteMapper sites;
    private final CredentialCryptoService crypto;
    private final ISupportChangeLogService logs;
    public SupportServerScanCredentialService(SupportServerScanMapper mapper, SupportSiteMapper sites,
        CredentialCryptoService crypto, ISupportChangeLogService logs)
    { this.mapper = mapper; this.sites = sites; this.crypto = crypto; this.logs = logs; }

    public List<Map<String, Object>> list(Long siteId)
    {
        if (siteId == null || siteId <= 0) throw new ServiceException("请选择现场");
        return mapper.selectCredentials(siteId).stream().map(row -> {
            Map<String, Object> visible = new LinkedHashMap<>();
            for (String key : List.of("credentialId", "siteId", "name")) visible.put(key, row.get(key));
            visible.put("passwordConfigured", !text(row.get("passwordCipher")).isEmpty());
            return visible;
        }).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void save(SupportServerScanBo.Credential input, String actor)
    {
        if (input == null || input.siteId() == null || sites.selectSiteIdForUpdate(input.siteId()) == null)
            throw new ServiceException("现场不存在");
        if (input.password() == null) throw new ServiceException("密码池配置已更新，请刷新页面后再保存");
        String name = text(input.name()).trim();
        if (name.isEmpty() || name.length() > 80) throw new ServiceException("请填写80字以内的密码名称");
        Map<String, Object> original = input.credentialId() == null ? null : mapper.selectCredential(input.siteId(), input.credentialId());
        if (input.credentialId() != null && original == null) throw new ServiceException("凭据不存在或不属于当前现场");
        if (original == null && mapper.selectCredentials(input.siteId()).size() >= 40) throw new ServiceException("每个现场最多配置40条候选密码");
        Map<String, Object> row = original == null ? new LinkedHashMap<>() : new LinkedHashMap<>(original);
        row.put("siteId", input.siteId()); row.put("name", name); row.put("actor", actor);
        row.put("passwordCipher", cipher(input.password(), text(row.get("passwordCipher"))));
        if (text(row.get("passwordCipher")).isEmpty()) throw new ServiceException("请填写候选密码");
        if (original == null) mapper.insertCredential(row); else mapper.updateCredential(row);
        logs.record(input.siteId(), original == null ? "INSERT" : "UPDATE", "SERVER_SCAN_CREDENTIAL",
            ((Number) row.get("credentialId")).longValue(), name, "维护扫描密码池 " + name,
            original == null ? null : Map.of("名称", text(original.get("name"))),
            Map.of("名称", name, "密码变更", "仅记录维护动作，不记录密码内容"));
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long siteId, Long id)
    {
        if (siteId == null || sites.selectSiteIdForUpdate(siteId) == null) throw new ServiceException("现场不存在");
        Map<String, Object> original = mapper.selectCredential(siteId, id);
        if (original == null) throw new ServiceException("凭据不存在或不属于当前现场");
        mapper.deleteCredential(siteId, id);
        logs.record(siteId, "DELETE", "SERVER_SCAN_CREDENTIAL", id, text(original.get("name")), "删除扫描候选密码");
    }

    public List<Map<String, Object>> selected(Long siteId, List<Long> ids)
    {
        if (ids == null || ids.isEmpty() || ids.size() > 40 || ids.stream().anyMatch(id -> id == null || id <= 0)
            || ids.stream().distinct().count() != ids.size())
            throw new ServiceException("每次请选择1至40条候选密码，不允许重复");
        List<Map<String, Object>> selected = new ArrayList<>();
        for (Long id : ids)
        {
            Map<String, Object> row = mapper.selectCredential(siteId, id);
            if (row == null) throw new ServiceException("候选凭据不存在或不属于当前现场");
            if (text(row.get("passwordCipher")).isEmpty())
                throw new ServiceException("密码池配置不完整，请确认已执行v4.17.0升级脚本");
            selected.add(new LinkedHashMap<>(row));
        }
        return selected;
    }

    private String cipher(String value, String previous)
    {
        if (value == null || value.isEmpty()) return previous;
        if (value.length() > 128 || value.chars().anyMatch(Character::isISOControl)) throw new ServiceException("密码长度不能超过128，且不能包含终端控制字符");
        return crypto.encrypt(value);
    }
    private static String text(Object value) { return value == null ? "" : String.valueOf(value); }
}
