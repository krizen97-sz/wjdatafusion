package com.hm.manage.domain.vo;

import java.util.List;
import com.hm.manage.domain.SupportTimInspection;
import com.hm.manage.domain.SupportTimInspectionItem;
import com.hm.manage.domain.SupportTimInspectionTargetResult;

public class SupportTimInspectionDetailVo
{
    private SupportTimInspection inspection;
    private List<SupportTimInspectionItem> items;
    private List<SupportTimInspectionTargetResult> targetResults;

    public SupportTimInspection getInspection()
    {
        return inspection;
    }

    public void setInspection(SupportTimInspection inspection)
    {
        this.inspection = inspection;
    }

    public List<SupportTimInspectionItem> getItems()
    {
        return items;
    }

    public void setItems(List<SupportTimInspectionItem> items)
    {
        this.items = items;
    }

    public List<SupportTimInspectionTargetResult> getTargetResults()
    {
        return targetResults;
    }

    public void setTargetResults(List<SupportTimInspectionTargetResult> targetResults)
    {
        this.targetResults = targetResults;
    }
}
