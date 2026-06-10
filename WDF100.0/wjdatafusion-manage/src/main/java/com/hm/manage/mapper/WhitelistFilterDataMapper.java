package com.hm.manage.mapper;

import java.util.List;
import com.hm.manage.domain.WhitelistFilterData;

public interface WhitelistFilterDataMapper
{
    WhitelistFilterData selectWhitelistFilterDataById(Long id);

    List<WhitelistFilterData> selectWhitelistFilterDataByIds(Long[] ids);

    List<WhitelistFilterData> selectWhitelistFilterDataList(WhitelistFilterData whitelistFilterData);

    int countWhitelistFilterData(WhitelistFilterData whitelistFilterData);

    int countDistinctPlateNo(WhitelistFilterData whitelistFilterData);

    int insertWhitelistFilterData(WhitelistFilterData whitelistFilterData);

    int deleteWhitelistFilterDataByIds(Long[] ids);
}
