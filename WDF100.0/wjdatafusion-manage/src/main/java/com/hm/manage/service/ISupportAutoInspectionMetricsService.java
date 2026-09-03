package com.hm.manage.service;

import com.hm.manage.domain.bo.AutoInspectionMetricQuery;
import com.hm.manage.domain.vo.AutoInspectionMetricsVo;

public interface ISupportAutoInspectionMetricsService
{
    AutoInspectionMetricsVo selectMetrics(AutoInspectionMetricQuery query);
}
