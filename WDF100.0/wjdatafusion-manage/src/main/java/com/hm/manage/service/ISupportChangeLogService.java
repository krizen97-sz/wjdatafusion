package com.hm.manage.service;

import java.util.List;
import com.hm.manage.domain.SupportChangeLog;

public interface ISupportChangeLogService
{
    List<SupportChangeLog> selectSupportChangeLogList(SupportChangeLog changeLog);

    int insertSupportChangeLog(SupportChangeLog changeLog);

    void record(Long siteId, String actionType, String targetType, Long targetId, String targetName, String summary);

    void record(Long siteId, String actionType, String targetType, Long targetId, String targetName, String summary, Object beforeData, Object afterData);

    void recordQuery(Long siteId, String targetType, Long targetId, String targetName, String summary);
}
