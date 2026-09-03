package com.hm.manage.domain.bo;

public class AutoInspectionMetricQuery
{
    private Long planId;
    private Integer days = 7;

    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public Integer getDays() { return days; }
    public void setDays(Integer days) { this.days = days; }
}
