package com.hm.manage.domain.bo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public class IpamWorkbookCommitBo implements Serializable
{
    private static final long serialVersionUID = 1L;

    @Valid
    @NotEmpty(message = "没有需要保存的工作表数据")
    @Size(max = 16, message = "单次最多保存16个配置批次")
    private List<IpamConfigCommitBo> sheets = new ArrayList<>();

    public List<IpamConfigCommitBo> getSheets()
    {
        return sheets;
    }

    public void setSheets(List<IpamConfigCommitBo> sheets)
    {
        this.sheets = sheets;
    }

    public boolean containsIssued()
    {
        return sheets != null && sheets.stream()
            .anyMatch(sheet -> sheet != null && sheet.containsIssued());
    }
}
