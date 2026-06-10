package com.hm.manage.service;

import java.util.List;
import com.hm.manage.domain.SupportSubplatformEndpoint;

public interface ISupportSubplatformEndpointService
{
    SupportSubplatformEndpoint selectSupportSubplatformEndpointByEndpointId(Long endpointId);

    List<SupportSubplatformEndpoint> selectSupportSubplatformEndpointList(SupportSubplatformEndpoint endpoint);

    int insertSupportSubplatformEndpoint(SupportSubplatformEndpoint endpoint);

    int updateSupportSubplatformEndpoint(SupportSubplatformEndpoint endpoint);

    int deleteSupportSubplatformEndpointByEndpointIds(Long[] endpointIds);

    String getEndpointPasswordPlain(Long endpointId);
}
