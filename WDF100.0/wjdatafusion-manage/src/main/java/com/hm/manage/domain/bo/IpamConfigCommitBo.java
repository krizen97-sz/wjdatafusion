package com.hm.manage.domain.bo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class IpamConfigCommitBo implements Serializable
{
    private static final long serialVersionUID = 1L;

    @NotNull(message = "请选择要配置的网段")
    private Long networkId;
    @Valid
    @NotEmpty(message = "没有需要保存的配置行")
    @Size(max = 256, message = "单次最多配置256个IP，请分批保存")
    private List<IpamConfigRowBo> rows = new ArrayList<>();

    public Long getNetworkId()
    {
        return networkId;
    }

    public void setNetworkId(Long networkId)
    {
        this.networkId = networkId;
    }

    public List<IpamConfigRowBo> getRows()
    {
        return rows;
    }

    public void setRows(List<IpamConfigRowBo> rows)
    {
        this.rows = rows;
    }

    public boolean containsIssued()
    {
        return rows != null && rows.stream()
            .anyMatch(row -> row != null && row.getStatus() != null
                && "ISSUED".equalsIgnoreCase(row.getStatus().trim()));
    }
}
