package com.hm.manage.mapper;

import java.util.List;
import com.hm.manage.domain.SupportSubplatformEndpoint;

public interface SupportSubplatformEndpointMapper
{
    SupportSubplatformEndpoint selectSupportSubplatformEndpointByEndpointId(Long endpointId);

    List<SupportSubplatformEndpoint> selectSupportSubplatformEndpointList(SupportSubplatformEndpoint endpoint);

    int insertSupportSubplatformEndpoint(SupportSubplatformEndpoint endpoint);

    int updateSupportSubplatformEndpoint(SupportSubplatformEndpoint endpoint);

    int deleteSupportSubplatformEndpointByEndpointId(Long endpointId);

    int deleteSupportSubplatformEndpointByEndpointIds(Long[] endpointIds);
}
