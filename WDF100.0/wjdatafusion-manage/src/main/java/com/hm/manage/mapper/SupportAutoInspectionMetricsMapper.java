package com.hm.manage.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.hm.manage.domain.vo.AutoInspectionMetricSampleVo;
import com.hm.manage.domain.vo.AutoInspectionMetricsVo.ExecutionPoint;
import com.hm.manage.domain.vo.AutoInspectionMetricsVo.PlanOption;

public interface SupportAutoInspectionMetricsMapper
{
    List<PlanOption> selectPlans(@Param("beginTime") Date beginTime, @Param("endTime") Date endTime);

    List<AutoInspectionMetricSampleVo> selectSamples(@Param("planId") Long planId,
            @Param("beginTime") Date beginTime, @Param("endTime") Date endTime, @Param("limit") int limit);

    List<ExecutionPoint> selectExecutions(@Param("planId") Long planId,
            @Param("beginTime") Date beginTime, @Param("endTime") Date endTime, @Param("limit") int limit);
}
