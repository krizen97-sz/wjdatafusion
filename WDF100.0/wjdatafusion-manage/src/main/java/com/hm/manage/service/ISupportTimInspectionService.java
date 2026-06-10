package com.hm.manage.service;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import com.hm.manage.domain.SupportTimInspection;
import com.hm.manage.domain.SupportTimInspectionItemConfig;
import com.hm.manage.domain.SupportTimInspectionTarget;
import com.hm.manage.domain.vo.SupportTimInspectionDetailVo;

public interface ISupportTimInspectionService
{
    List<SupportTimInspection> selectInspectionList(SupportTimInspection inspection);

    SupportTimInspectionDetailVo selectInspectionDetail(Long inspectionId);

    SupportTimInspectionDetailVo runManualInspection();

    SupportTimInspectionDetailVo runScheduledInspection(String executorName);

    void exportInspection(HttpServletResponse response, SupportTimInspection inspection);

    List<SupportTimInspectionItemConfig> selectConfigList();

    int updateItemConfig(SupportTimInspectionItemConfig config);

    List<SupportTimInspectionTarget> selectTargetList(SupportTimInspectionTarget target);

    SupportTimInspectionTarget selectTargetById(Long targetId);

    int insertTarget(SupportTimInspectionTarget target);

    int updateTarget(SupportTimInspectionTarget target);

    int deleteTargetById(Long targetId);

    String testTarget(SupportTimInspectionTarget target);

    String getTargetPasswordPlain(Long targetId);

    String getTargetSecretPlain(Long targetId);
}
