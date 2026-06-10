package com.hm.manage.domain;

import com.hm.common.core.domain.BaseEntity;

public class SupportPlatformOrgRel extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long relId;
    private Long platformId;
    private Long orgId;

    public Long getRelId()
    {
        return relId;
    }

    public void setRelId(Long relId)
    {
        this.relId = relId;
    }

    public Long getPlatformId()
    {
        return platformId;
    }

    public void setPlatformId(Long platformId)
    {
        this.platformId = platformId;
    }

    public Long getOrgId()
    {
        return orgId;
    }

    public void setOrgId(Long orgId)
    {
        this.orgId = orgId;
    }
}
