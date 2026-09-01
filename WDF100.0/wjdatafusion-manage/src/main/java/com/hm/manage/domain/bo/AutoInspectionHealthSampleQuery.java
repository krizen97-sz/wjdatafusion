package com.hm.manage.domain.bo;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.hm.common.core.domain.BaseEntity;

public class AutoInspectionHealthSampleQuery extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long planId;
    private Long siteId;
    private Long mainPlatformId;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date healthDate;
    private String resultStatus;

    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public Long getSiteId() { return siteId; }
    public void setSiteId(Long siteId) { this.siteId = siteId; }
    public Long getMainPlatformId() { return mainPlatformId; }
    public void setMainPlatformId(Long mainPlatformId) { this.mainPlatformId = mainPlatformId; }
    public Date getHealthDate() { return healthDate; }
    public void setHealthDate(Date healthDate) { this.healthDate = healthDate; }
    public String getResultStatus() { return resultStatus; }
    public void setResultStatus(String resultStatus) { this.resultStatus = resultStatus; }
}
