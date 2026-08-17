package com.hm.manage.domain.bo;

import java.util.ArrayList;
import java.util.List;

public class AutoInspectionReportExportBo extends AutoInspectionRecordQuery
{
    private static final long serialVersionUID = 1L;

    private String exportMode;
    private String weekDate;
    private String reportType;
    private String reportMode;
    private String month;
    private String rangeType;
    private List<Long> recordIds = new ArrayList<>();

    public String getExportMode() { return exportMode; }
    public void setExportMode(String exportMode) { this.exportMode = exportMode; }
    public String getWeekDate() { return weekDate; }
    public void setWeekDate(String weekDate) { this.weekDate = weekDate; }
    public String getReportType() { return reportType; }
    public void setReportType(String reportType) { this.reportType = reportType; }
    public String getReportMode() { return reportMode; }
    public void setReportMode(String reportMode) { this.reportMode = reportMode; }
    public String getMonth() { return month; }
    public void setMonth(String month) { this.month = month; }
    public String getRangeType() { return rangeType; }
    public void setRangeType(String rangeType) { this.rangeType = rangeType; }
    public List<Long> getRecordIds() { return recordIds; }
    public void setRecordIds(List<Long> recordIds) { this.recordIds = recordIds; }
}
