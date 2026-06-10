package com.hm.manage.domain;

import com.hm.common.annotation.Excel;
import com.hm.common.core.domain.BaseEntity;

public class WhitelistPlate extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    @Excel(
            sort = 1,
            name = "车牌号码",
            width = 22,
            prompt = "请输入需要管控的车牌号码，例如：苏D12345、苏D12345警、苏AD12345、WJ320001")
    private String vehiclePlate;

    private String originalVehiclePlate;

    private String alarmType;

    @Excel(
            sort = 2,
            name = "状态",
            readConverterExp = "1=停用,2=启用",
            combo = { "停用", "启用" },
            defaultValue = "启用",
            prompt = "可选值：启用 或 停用；为空时默认按启用处理")
    private Integer statusFlag;

    @Excel(
            sort = 3,
            name = "备注",
            width = 28,
            prompt = "可选填写备注信息，长度建议不超过200字符")
    private String remark;

    private String createTimeText;

    public String getVehiclePlate()
    {
        return vehiclePlate;
    }

    public void setVehiclePlate(String vehiclePlate)
    {
        this.vehiclePlate = vehiclePlate;
    }

    public String getOriginalVehiclePlate()
    {
        return originalVehiclePlate;
    }

    public void setOriginalVehiclePlate(String originalVehiclePlate)
    {
        this.originalVehiclePlate = originalVehiclePlate;
    }

    public String getAlarmType()
    {
        return alarmType;
    }

    public void setAlarmType(String alarmType)
    {
        this.alarmType = alarmType;
    }

    public Integer getStatusFlag()
    {
        return statusFlag;
    }

    public void setStatusFlag(Integer statusFlag)
    {
        this.statusFlag = statusFlag;
    }

    public String getCreateTimeText()
    {
        return createTimeText;
    }

    public void setCreateTimeText(String createTimeText)
    {
        this.createTimeText = createTimeText;
    }

    @Override
    public String getRemark()
    {
        return remark;
    }

    @Override
    public void setRemark(String remark)
    {
        this.remark = remark;
    }
}
