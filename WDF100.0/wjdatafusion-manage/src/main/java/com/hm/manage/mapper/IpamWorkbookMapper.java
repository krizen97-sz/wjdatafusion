package com.hm.manage.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.hm.manage.domain.IpamAddress;
import com.hm.manage.domain.vo.IpamCommunityOverviewVo;

public interface IpamWorkbookMapper
{
    List<IpamCommunityOverviewVo> selectCommunityCatalog();

    List<IpamAddress> selectCommunityAddressList(@Param("communityName") String communityName);
}
