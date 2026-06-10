package com.hm.manage.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.hm.manage.domain.SupportTimInspection;
import com.hm.manage.domain.SupportTimInspectionItem;
import com.hm.manage.domain.SupportTimInspectionItemConfig;
import com.hm.manage.domain.SupportTimInspectionTarget;
import com.hm.manage.domain.SupportTimInspectionTargetResult;

public interface SupportTimInspectionMapper
{
    List<SupportTimInspection> selectInspectionList(SupportTimInspection inspection);

    SupportTimInspection selectInspectionById(Long inspectionId);

    int insertInspection(SupportTimInspection inspection);

    int updateInspection(SupportTimInspection inspection);

    List<SupportTimInspectionItem> selectItemsByInspectionId(Long inspectionId);

    int insertInspectionItem(SupportTimInspectionItem item);

    List<SupportTimInspectionTargetResult> selectTargetResultsByInspectionId(Long inspectionId);

    int insertTargetResult(SupportTimInspectionTargetResult result);

    List<SupportTimInspectionItemConfig> selectItemConfigList();

    SupportTimInspectionItemConfig selectItemConfigByCode(String itemCode);

    int insertItemConfig(SupportTimInspectionItemConfig config);

    int updateItemConfig(SupportTimInspectionItemConfig config);

    List<SupportTimInspectionTarget> selectTargetList(SupportTimInspectionTarget target);

    List<SupportTimInspectionTarget> selectEnabledTargetsByItemCode(String itemCode);

    SupportTimInspectionTarget selectTargetById(Long targetId);

    int insertTarget(SupportTimInspectionTarget target);

    int updateTarget(SupportTimInspectionTarget target);

    int deleteTargetById(Long targetId);

    int countTargetsByItemCode(@Param("itemCode") String itemCode);
}
