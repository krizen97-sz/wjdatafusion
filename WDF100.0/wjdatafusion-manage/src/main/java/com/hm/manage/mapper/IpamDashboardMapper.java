package com.hm.manage.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.hm.manage.domain.vo.IpamCommunityAddressVo;
import com.hm.manage.domain.vo.IpamCommunityOverviewVo;
import com.hm.manage.domain.vo.IpamDashboardDimensionVo;

public interface IpamDashboardMapper
{
    List<IpamCommunityOverviewVo> selectCommunityOverview(@Param("policeStationName") String policeStationName);

    List<IpamDashboardDimensionVo> selectTargetTypeStats(@Param("policeStationName") String policeStationName);

    List<IpamDashboardDimensionVo> selectManufacturerStats(@Param("policeStationName") String policeStationName);

    List<IpamCommunityAddressVo> selectCommunityAddressList(@Param("communityName") String communityName);
}
