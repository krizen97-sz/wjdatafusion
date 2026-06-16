package com.hm.manage.service;

import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletResponse;

public interface ISupportAutoInspectionService
{
    List<Map<String, Object>> selectToolList(Map<String, Object> params);

    List<Map<String, Object>> selectTargetList(Map<String, Object> target);

    Map<String, Object> selectTargetById(Long targetId);

    List<Map<String, Object>> selectServerAssetTree();

    Map<String, Object> selectServerCredentialPlain(Long serverId, String username);

    int insertTarget(Map<String, Object> target);

    int updateTarget(Map<String, Object> target);

    int deleteTargetById(Long targetId);

    String testTarget(Map<String, Object> target);

    String getTargetPasswordPlain(Long targetId);

    String getTargetSecretPlain(Long targetId);

    List<Map<String, Object>> selectTemplateList(Map<String, Object> template);

    Map<String, Object> selectTemplateById(Long templateId);

    Long saveTemplate(Map<String, Object> template);

    int deleteTemplateById(Long templateId);

    List<Map<String, Object>> selectPlanList(Map<String, Object> plan);

    Map<String, Object> selectPlanById(Long planId);

    Long savePlan(Map<String, Object> plan);

    int updatePlanJobId(Long planId, Long jobId);

    int updatePlanStatus(Long planId, String status);

    int deletePlanById(Long planId);

    List<Map<String, Object>> selectRecordList(Map<String, Object> record);

    Map<String, Object> selectRecordDetail(Long recordId);

    Map<String, Object> runManualTemplate(Long templateId);

    Map<String, Object> runManualPlan(Long planId);

    Map<String, Object> runScheduledPlan(Long planId, String executorName);

    void exportRecord(HttpServletResponse response, Map<String, Object> record);
}
