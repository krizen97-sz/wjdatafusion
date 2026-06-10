package com.hm.manage.domain;

import com.hm.common.annotation.Excel;
import com.hm.common.core.domain.BaseEntity;

public class SupportOrg extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long orgId;

    @Excel(name = "组织类型", readConverterExp = "CUSTOMER=客户,USER=用户,THIRD_VENDOR=第三方厂家")
    private String orgType;

    @Excel(name = "组织名称")
    private String orgName;

    @Excel(name = "组织简称")
    private String shortName;

    @Excel(name = "状态", readConverterExp = "0=正常,1=停用")
    private String status;

    public Long getOrgId()
    {
        return orgId;
    }

    public void setOrgId(Long orgId)
    {
        this.orgId = orgId;
    }

    public String getOrgType()
    {
        return orgType;
    }

    public void setOrgType(String orgType)
    {
        this.orgType = orgType;
    }

    public String getOrgName()
    {
        return orgName;
    }

    public void setOrgName(String orgName)
    {
        this.orgName = orgName;
    }

    public String getShortName()
    {
        return shortName;
    }

    public void setShortName(String shortName)
    {
        this.shortName = shortName;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }
}
