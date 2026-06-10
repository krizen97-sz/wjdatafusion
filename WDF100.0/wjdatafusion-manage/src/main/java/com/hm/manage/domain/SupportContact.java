package com.hm.manage.domain;

import com.hm.common.annotation.Excel;
import com.hm.common.core.domain.BaseEntity;

public class SupportContact extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long contactId;
    private Long orgId;
    private String orgName;
    private String orgType;

    @Excel(name = "联系人")
    private String contactName;

    @Excel(name = "角色")
    private String roleType;

    @Excel(name = "手机")
    private String phone;

    @Excel(name = "邮箱")
    private String email;

    @Excel(name = "微信")
    private String wechat;

    @Excel(name = "主联系人", readConverterExp = "0=否,1=是")
    private String isPrimary;

    public Long getContactId()
    {
        return contactId;
    }

    public void setContactId(Long contactId)
    {
        this.contactId = contactId;
    }

    public Long getOrgId()
    {
        return orgId;
    }

    public void setOrgId(Long orgId)
    {
        this.orgId = orgId;
    }

    public String getOrgName()
    {
        return orgName;
    }

    public void setOrgName(String orgName)
    {
        this.orgName = orgName;
    }

    public String getOrgType()
    {
        return orgType;
    }

    public void setOrgType(String orgType)
    {
        this.orgType = orgType;
    }

    public String getContactName()
    {
        return contactName;
    }

    public void setContactName(String contactName)
    {
        this.contactName = contactName;
    }

    public String getRoleType()
    {
        return roleType;
    }

    public void setRoleType(String roleType)
    {
        this.roleType = roleType;
    }

    public String getPhone()
    {
        return phone;
    }

    public void setPhone(String phone)
    {
        this.phone = phone;
    }

    public String getEmail()
    {
        return email;
    }

    public void setEmail(String email)
    {
        this.email = email;
    }

    public String getWechat()
    {
        return wechat;
    }

    public void setWechat(String wechat)
    {
        this.wechat = wechat;
    }

    public String getIsPrimary()
    {
        return isPrimary;
    }

    public void setIsPrimary(String isPrimary)
    {
        this.isPrimary = isPrimary;
    }
}
