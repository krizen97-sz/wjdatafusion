package com.hm.manage.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.hm.common.core.domain.entity.SysUser;
import com.hm.common.core.domain.model.LoginUser;
import com.hm.common.exception.ServiceException;
import com.hm.common.utils.DateUtils;
import com.hm.common.utils.SecurityUtils;
import com.hm.common.utils.StringUtils;
import com.hm.manage.domain.SupportSiteMessage;
import com.hm.manage.mapper.SupportSiteMessageMapper;
import com.hm.manage.mapper.SupportSiteMapper;
import com.hm.manage.service.ISupportSiteMessageService;

@Service
public class SupportSiteMessageServiceImpl implements ISupportSiteMessageService
{
    private static final int MAX_MESSAGE_LENGTH = 300;
    private static final int DEFAULT_LATEST_LIMIT = 8;
    private static final int MAX_LATEST_LIMIT = 20;

    @Autowired
    private SupportSiteMessageMapper messageMapper;

    @Autowired
    private SupportSiteMapper siteMapper;

    @Override
    public List<SupportSiteMessage> selectSupportSiteMessageList(SupportSiteMessage message)
    {
        Long siteId = requireSiteId(message);
        assertSiteExists(siteId);
        return messageMapper.selectSupportSiteMessageList(message);
    }

    @Override
    public List<SupportSiteMessage> selectLatestSupportSiteMessages(Long siteId, Long afterMessageId, Integer limit)
    {
        if (siteId == null)
        {
            throw new ServiceException("现场不能为空");
        }
        assertSiteExists(siteId);
        return messageMapper.selectLatestSupportSiteMessages(siteId, afterMessageId, normalizeLatestLimit(limit));
    }

    @Override
    public int insertSupportSiteMessage(SupportSiteMessage message)
    {
        Long siteId = requireSiteId(message);
        assertSiteExists(siteId);
        String content = StringUtils.trim(message.getMessageContent());
        if (StringUtils.isBlank(content))
        {
            throw new ServiceException("留言内容不能为空");
        }
        if (content.length() > MAX_MESSAGE_LENGTH)
        {
            throw new ServiceException("留言内容不能超过300个字");
        }

        LoginUser loginUser = SecurityUtils.getLoginUser();
        SysUser user = loginUser.getUser();
        String publisherName = user == null ? null : StringUtils.trim(user.getNickName());
        if (StringUtils.isBlank(publisherName))
        {
            publisherName = loginUser.getUsername();
        }

        message.setMessageContent(content);
        message.setPublisherId(loginUser.getUserId());
        message.setPublisherName(publisherName);
        message.setStatus("0");
        message.setCreateBy(loginUser.getUsername());
        message.setCreateTime(DateUtils.getNowDate());
        return messageMapper.insertSupportSiteMessage(message);
    }

    private Long requireSiteId(SupportSiteMessage message)
    {
        if (message == null || message.getSiteId() == null)
        {
            throw new ServiceException("现场不能为空");
        }
        return message.getSiteId();
    }

    private void assertSiteExists(Long siteId)
    {
        if (siteMapper.selectSupportSiteBySiteId(siteId) == null)
        {
            throw new ServiceException("现场不存在");
        }
    }

    private int normalizeLatestLimit(Integer limit)
    {
        if (limit == null || limit <= 0)
        {
            return DEFAULT_LATEST_LIMIT;
        }
        return Math.min(limit, MAX_LATEST_LIMIT);
    }
}
