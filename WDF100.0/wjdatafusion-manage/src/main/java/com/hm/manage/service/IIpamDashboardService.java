package com.hm.manage.service;

import java.util.List;
import com.hm.manage.domain.vo.IpamCommunityAddressVo;
import com.hm.manage.domain.vo.IpamDashboardVo;

public interface IIpamDashboardService
{
    IpamDashboardVo getDashboard(String policeStationName);

    List<IpamCommunityAddressVo> selectCommunityAddressList(String communityName);
}
