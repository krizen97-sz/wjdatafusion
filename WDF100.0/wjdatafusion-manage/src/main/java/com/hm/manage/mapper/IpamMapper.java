package com.hm.manage.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.hm.manage.domain.IpamAddress;
import com.hm.manage.domain.IpamNetwork;
import com.hm.manage.domain.IpamOperationLog;
import com.hm.manage.domain.IpamSegment;
import com.hm.manage.domain.vo.IpamCommunityOverviewVo;

public interface IpamMapper
{
    List<IpamNetwork> selectNetworkList(IpamNetwork network);

    IpamNetwork selectNetworkById(Long networkId);

    IpamNetwork selectNetworkByCidr(String cidrBlock);

    IpamNetwork selectOverlappingNetwork(@Param("startValue") Long startValue,
                                         @Param("endValue") Long endValue,
                                         @Param("excludeNetworkId") Long excludeNetworkId);

    String selectSettingValue(String settingKey);

    String lockSettingRow(String settingKey);

    String selectFirstNetworkScenario();

    int upsertSetting(@Param("settingKey") String settingKey,
                      @Param("settingValue") String settingValue,
                      @Param("settingName") String settingName,
                      @Param("username") String username,
                      @Param("now") Date now);

    int updateAllNetworkScenario(@Param("scenarioType") String scenarioType,
                                 @Param("username") String username,
                                 @Param("now") Date now);

    int insertNetwork(IpamNetwork network);

    int updateNetwork(IpamNetwork network);

    int deleteNetworkByIds(Long[] networkIds);

    int countActiveAddressesByNetworkIds(Long[] networkIds);

    int deleteFreeAddressesByNetworkIds(Long[] networkIds);

    int deleteSegmentsByNetworkIds(Long[] networkIds);

    List<IpamSegment> selectSegmentList(IpamSegment segment);

    IpamSegment selectSegmentById(Long segmentId);

    IpamSegment selectSegmentByCidr(String cidrBlock);

    int insertSegment(IpamSegment segment);

    int updateSegment(IpamSegment segment);

    List<IpamAddress> selectAddressList(IpamAddress address);

    List<IpamAddress> selectAddressesBySegmentId(Long segmentId);

    List<IpamAddress> selectAddressesBySegmentIdAndRange(@Param("segmentId") Long segmentId,
                                                         @Param("startValue") Long startValue,
                                                         @Param("endValue") Long endValue);

    IpamAddress selectAddressById(Long addressId);

    IpamAddress selectAddressByIp(String ipAddress);

    int insertAddress(IpamAddress address);

    int updateAddress(IpamAddress address);

    int updateAddressIfFree(IpamAddress address);

    int markAddressFree(IpamAddress address);

    List<IpamCommunityOverviewVo> selectCommunityOverview(@Param("networkId") Long networkId,
                                                          @Param("segmentId") Long segmentId,
                                                          @Param("keyword") String keyword,
                                                          @Param("targetType") String targetType,
                                                          @Param("manufacturer") String manufacturer);

    int insertOperationLog(IpamOperationLog operationLog);
}
