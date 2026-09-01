package com.hm.manage.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

public interface SupportAutoInspectionMapper
{
    List<Map<String, Object>> selectToolList(Map<String, Object> params);

    Map<String, Object> selectToolByCode(String toolCode);

    int insertTool(Map<String, Object> tool);

    List<Map<String, Object>> selectTargetList(Map<String, Object> target);

    Map<String, Object> selectTargetById(Long targetId);

    List<Map<String, Object>> selectEnabledTargetsByStepId(Long stepId);

    List<Map<String, Object>> selectServerAssetTreeRows();

    List<Map<String, Object>> selectScopeTreeRows();

    Map<String, Object> selectSiteScopeById(Long siteId);

    Map<String, Object> selectMainPlatformScopeById(Long mainPlatformId);

    int insertTarget(Map<String, Object> target);

    int updateTarget(Map<String, Object> target);

    int deleteTargetById(Long targetId);

    List<Map<String, Object>> selectTemplateList(Map<String, Object> template);

    Map<String, Object> selectTemplateById(Long templateId);

    int insertTemplate(Map<String, Object> template);

    int updateTemplate(Map<String, Object> template);

    int deleteTemplateById(Long templateId);

    int countPlanByTemplateId(Long templateId);

    List<Map<String, Object>> selectStepsByTemplateId(Long templateId);

    int insertStep(Map<String, Object> step);

    int deleteStepsByTemplateId(Long templateId);

    int deleteProbeStatesByTemplateId(Long templateId);

    List<Map<String, Object>> selectStepTargetsByTemplateId(Long templateId);

    int insertStepTarget(@Param("stepId") Long stepId, @Param("targetId") Long targetId,
                         @Param("createBy") String createBy);

    int deleteStepTargetsByTemplateId(Long templateId);

    List<Map<String, Object>> selectPlanList(Map<String, Object> plan);

    Map<String, Object> selectPlanById(Long planId);

    int insertPlan(Map<String, Object> plan);

    int updatePlan(Map<String, Object> plan);

    int updatePlanJobId(@Param("planId") Long planId, @Param("jobId") Long jobId);

    int updatePlanStatus(@Param("planId") Long planId, @Param("status") String status,
                         @Param("updateBy") String updateBy);

    int deletePlanById(Long planId);

    int deleteProbeStatesByPlanId(Long planId);

    int deleteDailyHealthByPlanId(Long planId);

    List<Map<String, Object>> selectRecordList(Map<String, Object> record);

    Map<String, Object> selectRecordById(Long recordId);

    Map<String, Object> selectRecordByPlanSlot(@Param("planId") Long planId,
                                                @Param("scheduleSlotTime") Object scheduleSlotTime);

    int insertRecord(Map<String, Object> record);

    int insertFrequentRecord(Map<String, Object> record);

    int updateRecord(Map<String, Object> record);

    List<Map<String, Object>> selectStepResultsByRecordId(Long recordId);

    List<Map<String, Object>> selectStepResultsByRecordIds(@Param("recordIds") List<Long> recordIds);

    int insertStepResult(Map<String, Object> stepResult);

    List<Map<String, Object>> selectTargetResultsByRecordId(Long recordId);

    List<Map<String, Object>> selectTargetResultsByRecordIds(@Param("recordIds") List<Long> recordIds);

    int insertTargetResult(Map<String, Object> targetResult);

    Map<String, Object> selectProbeState(@Param("planId") Long planId, @Param("stepId") Long stepId,
                                          @Param("targetId") Long targetId, @Param("toolCode") String toolCode);

    int upsertProbeState(Map<String, Object> state);

    Map<String, Object> selectDailyHealthStats(Map<String, Object> params);

    int upsertDailyHealth(Map<String, Object> summary);

    List<Map<String, Object>> selectDailyHealthList(Map<String, Object> params);

    List<Long> selectExpiredFrequentRecordIds(@Param("normalBefore") Object normalBefore,
                                               @Param("abnormalBefore") Object abnormalBefore);

    int deleteTargetResultsByRecordIds(@Param("recordIds") List<Long> recordIds);

    int deleteStepResultsByRecordIds(@Param("recordIds") List<Long> recordIds);

    int deleteRecordsByIds(@Param("recordIds") List<Long> recordIds);
}
