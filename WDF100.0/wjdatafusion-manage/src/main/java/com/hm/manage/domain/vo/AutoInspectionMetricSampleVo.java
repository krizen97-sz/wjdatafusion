package com.hm.manage.domain.vo;

import java.util.Date;
import com.hm.manage.domain.SupportAutoInspectionTargetResult;

public class AutoInspectionMetricSampleVo extends SupportAutoInspectionTargetResult
{
    private static final long serialVersionUID = 1L;
    private Long stepId;
    private Date sampleTime;

    public Long getStepId() { return stepId; }
    public void setStepId(Long stepId) { this.stepId = stepId; }
    public Date getSampleTime() { return sampleTime; }
    public void setSampleTime(Date sampleTime) { this.sampleTime = sampleTime; }
}
