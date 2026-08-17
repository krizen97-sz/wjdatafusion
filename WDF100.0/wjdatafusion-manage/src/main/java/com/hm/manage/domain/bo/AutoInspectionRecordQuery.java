package com.hm.manage.domain.bo;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.hm.manage.domain.SupportAutoInspectionRecord;

public class AutoInspectionRecordQuery extends SupportAutoInspectionRecord
{
    private static final long serialVersionUID = 1L;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date beginTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date endTime;

    public Date getBeginTime() { return beginTime; }
    public void setBeginTime(Date beginTime) { this.beginTime = beginTime; }
    public Date getEndTime() { return endTime; }
    public void setEndTime(Date endTime) { this.endTime = endTime; }
}
