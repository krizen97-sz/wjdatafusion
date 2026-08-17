package com.hm.manage.domain.vo;

import java.util.ArrayList;
import java.util.List;
import com.hm.manage.domain.SupportAutoInspectionRecord;
import com.hm.manage.domain.SupportAutoInspectionStepResult;
import com.hm.manage.domain.SupportAutoInspectionTargetResult;

public class AutoInspectionRecordDetailVo extends SupportAutoInspectionRecord
{
    private static final long serialVersionUID = 1L;

    private List<SupportAutoInspectionStepResult> steps = new ArrayList<>();
    private List<SupportAutoInspectionTargetResult> targetResults = new ArrayList<>();

    public List<SupportAutoInspectionStepResult> getSteps() { return steps; }
    public void setSteps(List<SupportAutoInspectionStepResult> steps) { this.steps = steps; }
    public List<SupportAutoInspectionTargetResult> getTargetResults() { return targetResults; }
    public void setTargetResults(List<SupportAutoInspectionTargetResult> targetResults) { this.targetResults = targetResults; }
}
