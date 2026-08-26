package com.hm.manage.domain.bo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class KbPageStatusBo
{
    @NotBlank(message = "知识状态不能为空")
    private String lifecycleStatus;

    @NotNull(message = "当前版本号不能为空")
    @Min(value = 1, message = "当前版本号无效")
    private Integer expectedVersion;

    @Size(max = 500, message = "修改说明不能超过500个字符")
    private String changeNote;

    public String getLifecycleStatus() { return lifecycleStatus; }
    public void setLifecycleStatus(String lifecycleStatus) { this.lifecycleStatus = lifecycleStatus; }
    public Integer getExpectedVersion() { return expectedVersion; }
    public void setExpectedVersion(Integer expectedVersion) { this.expectedVersion = expectedVersion; }
    public String getChangeNote() { return changeNote; }
    public void setChangeNote(String changeNote) { this.changeNote = changeNote; }
}
