package com.hm.manage.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.hm.manage.domain.SupportChangeLog;
import com.hm.manage.domain.vo.SupportSiteDashboardChangeVo;

public interface SupportChangeLogMapper
{
    SupportChangeLog selectSupportChangeLogByLogId(Long logId);

    List<SupportChangeLog> selectSupportChangeLogList(SupportChangeLog changeLog);

    List<SupportSiteDashboardChangeVo> selectDashboardLatestChanges(@Param("limit") Integer limit);

    int countDashboardTodayChanges();

    int insertSupportChangeLog(SupportChangeLog changeLog);
}
