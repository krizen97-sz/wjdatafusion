package com.hm.manage.domain;

import com.hm.common.core.domain.BaseEntity;

public class SupportSiteMessage extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long messageId;
    private Long siteId;
    private String messageContent;
    private Long publisherId;
    private String publisherName;
    private String status;

    public Long getMessageId()
    {
        return messageId;
    }

    public void setMessageId(Long messageId)
    {
        this.messageId = messageId;
    }

    public Long getSiteId()
    {
        return siteId;
    }

    public void setSiteId(Long siteId)
    {
        this.siteId = siteId;
    }

    public String getMessageContent()
    {
        return messageContent;
    }

    public void setMessageContent(String messageContent)
    {
        this.messageContent = messageContent;
    }

    public Long getPublisherId()
    {
        return publisherId;
    }

    public void setPublisherId(Long publisherId)
    {
        this.publisherId = publisherId;
    }

    public String getPublisherName()
    {
        return publisherName;
    }

    public void setPublisherName(String publisherName)
    {
        this.publisherName = publisherName;
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
