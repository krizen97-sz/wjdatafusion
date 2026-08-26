package com.hm.manage.domain.bo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class KbVersionRestoreBo
{
    @NotNull(message = "当前版本号不能为空")
    @Min(value = 1, message = "当前版本号无效")
    private Integer expectedVersion;

    @Size(max = 500, message = "恢复说明不能超过500个字符")
    private String changeNote;

    public Integer getExpectedVersion() { return expectedVersion; }
    public void setExpectedVersion(Integer expectedVersion) { this.expectedVersion = expectedVersion; }
    public String getChangeNote() { return changeNote; }
    public void setChangeNote(String changeNote) { this.changeNote = changeNote; }
}
