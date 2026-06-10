package com.hm.manage.service;

import java.util.List;
import java.util.Map;
import com.hm.manage.domain.WhitelistFilterData;
import com.hm.manage.domain.vo.WhitelistKafkaPullResultVo;

public interface IWhitelistFilterDataService
{
    WhitelistFilterData selectWhitelistFilterDataById(Long id);

    List<WhitelistFilterData> selectWhitelistFilterDataList(WhitelistFilterData whitelistFilterData);

    int insertWhitelistFilterData(WhitelistFilterData whitelistFilterData);

    int deleteWhitelistFilterDataByIds(Long[] ids);

    Map<String, Object> getDashboardSummary();

    WhitelistKafkaPullResultVo pullKafkaData();

    void publishKafkaData(String message);
}
