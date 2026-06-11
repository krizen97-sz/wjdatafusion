package com.hm.manage.service;

import java.util.List;
import com.hm.manage.domain.SupportTimInspectionPlan;
import com.hm.manage.domain.SupportTimInspectionPlanItem;

public interface ISupportTimInspectionPlanService
{
    List<SupportTimInspectionPlan> selectPlanList(SupportTimInspectionPlan plan);

    SupportTimInspectionPlan selectPlanById(Long planId);

    SupportTimInspectionPlan buildPlanTemplate();

    Long savePlan(SupportTimInspectionPlan plan);

    int updatePlanJobId(Long planId, Long jobId);

    int updatePlanStatus(Long planId, String status);

    int deletePlanById(Long planId);

    List<SupportTimInspectionPlanItem> selectPlanItems(Long planId);
}
