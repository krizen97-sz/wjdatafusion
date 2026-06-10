package com.hm.manage.domain.vo;

import com.hm.common.annotation.Excel;

public class SupportTimInspectionExportVo
{
    @Excel(name = "巡检ID")
    private Long inspectionId;
    @Excel(name = "巡检时间")
    private String inspectionTime;
    @Excel(name = "执行来源")
    private String sourceType;
    @Excel(name = "巡检结果")
    private String resultStatus;
    @Excel(name = "执行人")
    private String executorName;
    @Excel(name = "巡检摘要", width = 40)
    private String summary;
    @Excel(name = "异常摘要", width = 40)
    private String abnormalSummary;
    @Excel(name = "过车数量", width = 32)
    private String vehiclePass;
    @Excel(name = "FTP文件数量", width = 32)
    private String ftpFile;
    @Excel(name = "DataI文件数量", width = 32)
    private String dataiFile;
    @Excel(name = "原始Kafka积压", width = 32)
    private String kafkaOrigin;
    @Excel(name = "二次分析Kafka积压", width = 32)
    private String kafkaSecond;
    @Excel(name = "大数据服务器磁盘", width = 32)
    private String diskUsage;
    @Excel(name = "违法数量", width = 32)
    private String vehicleAlarm;

    public Long getInspectionId()
    {
        return inspectionId;
    }

    public void setInspectionId(Long inspectionId)
    {
        this.inspectionId = inspectionId;
    }

    public String getInspectionTime()
    {
        return inspectionTime;
    }

    public void setInspectionTime(String inspectionTime)
    {
        this.inspectionTime = inspectionTime;
    }

    public String getSourceType()
    {
        return sourceType;
    }

    public void setSourceType(String sourceType)
    {
        this.sourceType = sourceType;
    }

    public String getResultStatus()
    {
        return resultStatus;
    }

    public void setResultStatus(String resultStatus)
    {
        this.resultStatus = resultStatus;
    }

    public String getExecutorName()
    {
        return executorName;
    }

    public void setExecutorName(String executorName)
    {
        this.executorName = executorName;
    }

    public String getSummary()
    {
        return summary;
    }

    public void setSummary(String summary)
    {
        this.summary = summary;
    }

    public String getAbnormalSummary()
    {
        return abnormalSummary;
    }

    public void setAbnormalSummary(String abnormalSummary)
    {
        this.abnormalSummary = abnormalSummary;
    }

    public String getVehiclePass()
    {
        return vehiclePass;
    }

    public void setVehiclePass(String vehiclePass)
    {
        this.vehiclePass = vehiclePass;
    }

    public String getFtpFile()
    {
        return ftpFile;
    }

    public void setFtpFile(String ftpFile)
    {
        this.ftpFile = ftpFile;
    }

    public String getDataiFile()
    {
        return dataiFile;
    }

    public void setDataiFile(String dataiFile)
    {
        this.dataiFile = dataiFile;
    }

    public String getKafkaOrigin()
    {
        return kafkaOrigin;
    }

    public void setKafkaOrigin(String kafkaOrigin)
    {
        this.kafkaOrigin = kafkaOrigin;
    }

    public String getKafkaSecond()
    {
        return kafkaSecond;
    }

    public void setKafkaSecond(String kafkaSecond)
    {
        this.kafkaSecond = kafkaSecond;
    }

    public String getDiskUsage()
    {
        return diskUsage;
    }

    public void setDiskUsage(String diskUsage)
    {
        this.diskUsage = diskUsage;
    }

    public String getVehicleAlarm()
    {
        return vehicleAlarm;
    }

    public void setVehicleAlarm(String vehicleAlarm)
    {
        this.vehicleAlarm = vehicleAlarm;
    }
}
