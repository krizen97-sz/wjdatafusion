package com.hm.manage.service.impl;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.hm.common.exception.ServiceException;
import com.hm.common.utils.DateUtils;
import com.hm.common.utils.StringUtils;
import com.hm.manage.domain.SupportPlatform;
import com.hm.manage.domain.SupportSubplatformEndpoint;
import com.hm.manage.mapper.SupportPlatformMapper;
import com.hm.manage.mapper.SupportSubplatformEndpointMapper;
import com.hm.manage.service.ISupportChangeLogService;
import com.hm.manage.service.ISupportSubplatformEndpointService;
import com.hm.manage.service.support.CredentialCryptoService;

@Service
public class SupportSubplatformEndpointServiceImpl implements ISupportSubplatformEndpointService
{
    @Autowired
    private SupportSubplatformEndpointMapper endpointMapper;

    @Autowired
    private SupportPlatformMapper platformMapper;

    @Autowired
    private CredentialCryptoService cryptoService;

    @Autowired
    private ISupportChangeLogService changeLogService;

    @Override
    public SupportSubplatformEndpoint selectSupportSubplatformEndpointByEndpointId(Long endpointId)
    {
        SupportSubplatformEndpoint endpoint = endpointMapper.selectSupportSubplatformEndpointByEndpointId(endpointId);
        maskPassword(endpoint);
        return endpoint;
    }

    @Override
    public List<SupportSubplatformEndpoint> selectSupportSubplatformEndpointList(SupportSubplatformEndpoint endpoint)
    {
        List<SupportSubplatformEndpoint> list = endpointMapper.selectSupportSubplatformEndpointList(endpoint);
        for (SupportSubplatformEndpoint item : list)
        {
            maskPassword(item);
        }
        return list;
    }

    @Override
    public int insertSupportSubplatformEndpoint(SupportSubplatformEndpoint endpoint)
    {
        validateSubPlatform(endpoint.getSubPlatformId());
        encryptPassword(endpoint);
        endpoint.setCreateTime(DateUtils.getNowDate());
        int rows = endpointMapper.insertSupportSubplatformEndpoint(endpoint);
        if (rows > 0)
        {
            changeLogService.record(resolveSiteIdByPlatformId(endpoint.getSubPlatformId()), "INSERT", "ENDPOINT", endpoint.getEndpointId(), endpoint.getEndpointName(), "新增页面 " + endpoint.getEndpointName(), null, endpoint);
        }
        return rows;
    }

    @Override
    public int updateSupportSubplatformEndpoint(SupportSubplatformEndpoint endpoint)
    {
        SupportSubplatformEndpoint original = endpointMapper.selectSupportSubplatformEndpointByEndpointId(endpoint.getEndpointId());
        validateSubPlatform(endpoint.getSubPlatformId());
        encryptPassword(endpoint);
        endpoint.setUpdateTime(DateUtils.getNowDate());
        int rows = endpointMapper.updateSupportSubplatformEndpoint(endpoint);
        if (rows > 0)
        {
            changeLogService.record(resolveSiteIdByPlatformId(endpoint.getSubPlatformId()), "UPDATE", "ENDPOINT", endpoint.getEndpointId(), endpoint.getEndpointName(), "修改页面 " + endpoint.getEndpointName(), original, endpoint);
        }
        return rows;
    }

    @Override
    public int deleteSupportSubplatformEndpointByEndpointIds(Long[] endpointIds)
    {
        List<SupportSubplatformEndpoint> deletedEndpoints = new ArrayList<>();
        for (Long endpointId : endpointIds)
        {
            SupportSubplatformEndpoint endpoint = endpointMapper.selectSupportSubplatformEndpointByEndpointId(endpointId);
            if (endpoint != null)
            {
                deletedEndpoints.add(endpoint);
            }
        }
        int rows = endpointMapper.deleteSupportSubplatformEndpointByEndpointIds(endpointIds);
        if (rows > 0)
        {
            for (SupportSubplatformEndpoint endpoint : deletedEndpoints)
            {
                changeLogService.record(resolveSiteIdByPlatformId(endpoint.getSubPlatformId()), "DELETE", "ENDPOINT", endpoint.getEndpointId(), endpoint.getEndpointName(), "删除页面 " + endpoint.getEndpointName(), endpoint, null);
            }
        }
        return rows;
    }

    @Override
    public String getEndpointPasswordPlain(Long endpointId)
    {
        SupportSubplatformEndpoint endpoint = endpointMapper.selectSupportSubplatformEndpointByEndpointId(endpointId);
        if (endpoint == null)
        {
            return StringUtils.EMPTY;
        }
        return cryptoService.decrypt(endpoint.getLoginPasswordCipher());
    }

    private void validateSubPlatform(Long subPlatformId)
    {
        SupportPlatform platform = platformMapper.selectSupportPlatformByPlatformId(subPlatformId);
        if (platform == null)
        {
            throw new ServiceException("子平台不存在");
        }
        if (!"SUB".equalsIgnoreCase(platform.getPlatformLevel()))
        {
            throw new ServiceException("页面仅允许挂到子平台");
        }
    }

    private Long resolveSiteIdByPlatformId(Long platformId)
    {
        SupportPlatform platform = platformMapper.selectSupportPlatformByPlatformId(platformId);
        return platform == null ? null : platform.getSiteId();
    }

    private void encryptPassword(SupportSubplatformEndpoint endpoint)
    {
        if (StringUtils.isNotEmpty(endpoint.getLoginPassword()))
        {
            endpoint.setLoginPasswordCipher(cryptoService.encrypt(endpoint.getLoginPassword()));
        }
        endpoint.setLoginPassword(null);
    }

    private void maskPassword(SupportSubplatformEndpoint endpoint)
    {
        if (endpoint == null)
        {
            return;
        }
        if (StringUtils.isNotEmpty(endpoint.getLoginPasswordCipher()))
        {
            endpoint.setLoginPassword("******");
        }
    }
}
