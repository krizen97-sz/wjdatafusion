package com.hm.manage.domain.bo;

import com.hm.manage.domain.SupportAutoInspectionTarget;

public class AutoInspectionTargetSaveBo extends SupportAutoInspectionTarget
{
    private static final long serialVersionUID = 1L;

    private String toolCode;

    public String getToolCode() { return toolCode; }
    public void setToolCode(String toolCode) { this.toolCode = toolCode; }
}
