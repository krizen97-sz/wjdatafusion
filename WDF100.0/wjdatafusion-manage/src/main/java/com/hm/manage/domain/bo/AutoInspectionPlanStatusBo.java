package com.hm.manage.domain.bo;

import java.io.Serializable;

public class AutoInspectionPlanStatusBo implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long planId;
    private String status;

    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
