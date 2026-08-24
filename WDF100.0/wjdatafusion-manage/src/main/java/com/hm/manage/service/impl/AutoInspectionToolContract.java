package com.hm.manage.service.impl;

import java.util.Map;
import com.hm.common.exception.ServiceException;
import com.hm.common.utils.StringUtils;

final class AutoInspectionToolContract
{
    private static final Map<String, String> TARGET_TYPE_BY_TOOL = Map.ofEntries(
            Map.entry("KAFKA_LAG", "KAFKA"),
            Map.entry("KAFKA_TOPIC_ACTIVITY", "KAFKA"),
            Map.entry("KAFKA_CONSUMER_PROGRESS", "KAFKA"),
            Map.entry("MQTT_TOPIC_ACTIVITY", "MQTT"),
            Map.entry("HTTP_COUNT", "HTTP"),
            Map.entry("HTTP_HEALTH", "HTTP"),
            Map.entry("HTTP_API_TEST", "HTTP"),
            Map.entry("DATABASE_QUERY", "DATABASE"),
            Map.entry("FTP_FILE_COUNT", "FTP"),
            Map.entry("SERVER_FILE_COUNT", "SERVER"),
            Map.entry("SERVER_DISK", "SERVER"),
            Map.entry("BIG_DATA_SERVER_DISK", "BIG_DATA_SERVER"),
            Map.entry("TCP_PORT_CHECK", "SERVER"),
            Map.entry("SERVER_SERVICE_STATUS", "SERVER")
    );

    private AutoInspectionToolContract()
    {
    }

    static String findTargetType(String toolCode)
    {
        return TARGET_TYPE_BY_TOOL.getOrDefault(StringUtils.trimToEmpty(toolCode).toUpperCase(), "");
    }

    static String requireTargetType(String toolCode)
    {
        String targetType = findTargetType(toolCode);
        if (StringUtils.isEmpty(targetType))
        {
            throw new ServiceException("不支持的巡检工具：" + toolCode);
        }
        return targetType;
    }
}
