package com.hm.manage.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.hm.manage.domain.SupportTimInspectionPlan;
import com.hm.manage.domain.SupportTimInspectionPlanItem;
import com.hm.manage.domain.SupportTimInspectionPlanTarget;
import com.hm.manage.domain.SupportTimInspectionTarget;

public interface SupportTimInspectionPlanMapper
{
    List<SupportTimInspectionPlan> selectPlanList(SupportTimInspectionPlan plan);

    SupportTimInspectionPlan selectPlanById(Long planId);

    int insertPlan(SupportTimInspectionPlan plan);

    int updatePlan(SupportTimInspectionPlan plan);

    int updatePlanJobId(@Param("planId") Long planId, @Param("jobId") Long jobId);

    int updatePlanStatus(@Param("planId") Long planId, @Param("status") String status, @Param("updateBy") String updateBy);

    int deletePlanById(Long planId);

    List<SupportTimInspectionPlanItem> selectItemsByPlanId(Long planId);

    int insertPlanItem(SupportTimInspectionPlanItem item);

    int deleteItemsByPlanId(Long planId);

    List<SupportTimInspectionPlanTarget> selectPlanTargetsByPlanId(Long planId);

    int insertPlanTarget(SupportTimInspectionPlanTarget target);

    int deleteTargetsByPlanId(Long planId);

    List<SupportTimInspectionTarget> selectEnabledTargetsByPlanAndItem(@Param("planId") Long planId, @Param("itemCode") String itemCode);
}
