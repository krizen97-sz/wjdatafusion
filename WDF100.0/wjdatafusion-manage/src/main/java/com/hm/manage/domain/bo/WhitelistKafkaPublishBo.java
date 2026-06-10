package com.hm.manage.domain.bo;

import jakarta.validation.constraints.NotBlank;

public class WhitelistKafkaPublishBo
{
    @NotBlank(message = "消息内容不能为空")
    private String message;

    public String getMessage()
    {
        return message;
    }

    public void setMessage(String message)
    {
        this.message = message;
    }
}
