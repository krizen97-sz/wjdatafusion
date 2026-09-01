package com.hm.manage.domain.bo;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.hm.common.core.domain.BaseEntity;

public class AutoInspectionHealthQuery extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long planId;
    private Long siteId;
    private Long mainPlatformId;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date beginDate;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date endDate;

    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public Long getSiteId() { return siteId; }
    public void setSiteId(Long siteId) { this.siteId = siteId; }
    public Long getMainPlatformId() { return mainPlatformId; }
    public void setMainPlatformId(Long mainPlatformId) { this.mainPlatformId = mainPlatformId; }
    public Date getBeginDate() { return beginDate; }
    public void setBeginDate(Date beginDate) { this.beginDate = beginDate; }
    public Date getEndDate() { return endDate; }
    public void setEndDate(Date endDate) { this.endDate = endDate; }
}
