package com.hm.manage.service;

import java.util.List;
import java.util.Map;
import com.hm.manage.domain.bo.IpamConfigCommitBo;
import com.hm.manage.domain.bo.IpamScenarioSettingBo;
import com.hm.manage.domain.IpamAddress;
import com.hm.manage.domain.IpamNetwork;
import com.hm.manage.domain.vo.IpamSegmentOverviewVo;

public interface IIpamService
{
    List<IpamNetwork> selectNetworkList(IpamNetwork network);

    IpamNetwork selectNetworkById(Long networkId);

    String getScenarioType();

    int updateScenarioType(IpamScenarioSettingBo setting);

    int insertNetwork(IpamNetwork network);

    int updateNetwork(IpamNetwork network);

    int deleteNetworkByIds(Long[] networkIds);

    Map<String, Object> getAddressGridByNetworkId(Long networkId, Integer pageNum, Integer pageSize);

    List<IpamAddress> selectAddressList(IpamAddress address);

    IpamAddress selectAddressById(Long addressId);

    String getAddressCredential(Long addressId);

    int allocateAddress(IpamAddress address);

    int updateAddress(IpamAddress address);

    int releaseAddress(Long addressId);

    int commitConfigSheet(IpamConfigCommitBo commitBo);

    IpamSegmentOverviewVo getNetworkOverview(Long networkId, String keyword, String targetType, String manufacturer);
}
