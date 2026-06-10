package com.hm.manage.mapper;

import java.util.List;
import com.hm.manage.domain.SupportChangeLog;

public interface SupportChangeLogMapper
{
    SupportChangeLog selectSupportChangeLogByLogId(Long logId);

    List<SupportChangeLog> selectSupportChangeLogList(SupportChangeLog changeLog);

    int insertSupportChangeLog(SupportChangeLog changeLog);
}
