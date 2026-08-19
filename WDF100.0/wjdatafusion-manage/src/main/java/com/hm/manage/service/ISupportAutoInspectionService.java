package com.hm.manage.service;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import com.hm.manage.domain.SupportAutoInspectionPlan;
import com.hm.manage.domain.SupportAutoInspectionRecord;
import com.hm.manage.domain.SupportAutoInspectionTarget;
import com.hm.manage.domain.SupportAutoInspectionTemplate;
import com.hm.manage.domain.SupportAutoInspectionTool;
import com.hm.manage.domain.bo.AutoInspectionDashboardQuery;
import com.hm.manage.domain.bo.AutoInspectionPlanQuery;
import com.hm.manage.domain.bo.AutoInspectionPlanSaveBo;
import com.hm.manage.domain.bo.AutoInspectionRecordQuery;
import com.hm.manage.domain.bo.AutoInspectionReportExportBo;
import com.hm.manage.domain.bo.AutoInspectionServerCredentialBatchBo;
import com.hm.manage.domain.bo.AutoInspectionTargetQuery;
import com.hm.manage.domain.bo.AutoInspectionTargetSaveBo;
import com.hm.manage.domain.bo.AutoInspectionTemplateQuery;
import com.hm.manage.domain.bo.AutoInspectionTemplateSaveBo;
import com.hm.manage.domain.vo.AutoInspectionCredentialVo;
import com.hm.manage.domain.vo.AutoInspectionDashboardVo;
import com.hm.manage.domain.vo.AutoInspectionRecordDetailVo;
import com.hm.manage.domain.vo.AutoInspectionRunResultVo;
import com.hm.manage.domain.vo.AutoInspectionServerAssetNodeVo;
import com.hm.manage.domain.vo.AutoInspectionTargetPreviewVo;

public interface ISupportAutoInspectionService
{
    List<SupportAutoInspectionTool> selectToolList(SupportAutoInspectionTool query);

    List<SupportAutoInspectionTarget> selectTargetList(AutoInspectionTargetQuery query);

    SupportAutoInspectionTarget selectTargetById(Long targetId);

    List<AutoInspectionServerAssetNodeVo> selectServerAssetTree();

    AutoInspectionCredentialVo selectServerCredentialPlain(Long serverId, String username);

    List<AutoInspectionCredentialVo> selectServerCredentialPlainBatch(AutoInspectionServerCredentialBatchBo query);

    int insertTarget(AutoInspectionTargetSaveBo target);

    int updateTarget(AutoInspectionTargetSaveBo target);

    int deleteTargetById(Long targetId);

    String testTarget(AutoInspectionTargetSaveBo target);

    AutoInspectionTargetPreviewVo previewTarget(AutoInspectionTargetSaveBo target);

    String getTargetPasswordPlain(Long targetId);

    String getTargetSecretPlain(Long targetId);

    List<SupportAutoInspectionTemplate> selectTemplateList(AutoInspectionTemplateQuery query);

    SupportAutoInspectionTemplate selectTemplateById(Long templateId);

    Long saveTemplate(AutoInspectionTemplateSaveBo template);

    Long copyTemplate(Long templateId);

    int deleteTemplateById(Long templateId);

    List<SupportAutoInspectionPlan> selectPlanList(AutoInspectionPlanQuery query);

    SupportAutoInspectionPlan selectPlanById(Long planId);

    Long savePlan(AutoInspectionPlanSaveBo plan);

    int updatePlanJobId(Long planId, Long jobId);

    int updatePlanStatus(Long planId, String status);

    int deletePlanById(Long planId);

    List<SupportAutoInspectionRecord> selectRecordList(AutoInspectionRecordQuery query);

    AutoInspectionRecordDetailVo selectRecordDetail(Long recordId);

    AutoInspectionDashboardVo selectDashboard(AutoInspectionDashboardQuery query);

    AutoInspectionRunResultVo runManualTemplate(Long templateId);

    AutoInspectionRunResultVo runManualPlan(Long planId);

    AutoInspectionRunResultVo runScheduledPlan(Long planId, String executorName);

    void exportRecord(HttpServletResponse response, AutoInspectionReportExportBo query);
}
