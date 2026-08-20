package com.hm.manage.domain;

import java.util.ArrayList;
import java.util.List;
import com.hm.common.core.domain.BaseEntity;

public class SupportAutoInspectionTemplate extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long templateId;
    private String templateName;
    private String labelName;
    private String templateDesc;
    private String status;
    private Integer stepCount;
    private Integer targetCount;
    private List<SupportAutoInspectionTemplateStep> steps = new ArrayList<>();

    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }
    public String getTemplateName() { return templateName; }
    public void setTemplateName(String templateName) { this.templateName = templateName; }
    public String getLabelName() { return labelName; }
    public void setLabelName(String labelName) { this.labelName = labelName; }
    public String getTemplateDesc() { return templateDesc; }
    public void setTemplateDesc(String templateDesc) { this.templateDesc = templateDesc; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getStepCount() { return stepCount; }
    public void setStepCount(Integer stepCount) { this.stepCount = stepCount; }
    public Integer getTargetCount() { return targetCount; }
    public void setTargetCount(Integer targetCount) { this.targetCount = targetCount; }
    public List<SupportAutoInspectionTemplateStep> getSteps() { return steps; }
    public void setSteps(List<SupportAutoInspectionTemplateStep> steps) { this.steps = steps; }
}
