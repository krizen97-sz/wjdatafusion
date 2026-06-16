package com.hm.manage.service.impl;

import com.alibaba.fastjson2.JSON;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.hm.common.core.domain.entity.SysUser;
import com.hm.common.core.domain.model.LoginUser;
import com.hm.common.filter.PropertyPreExcludeFilter;
import com.hm.common.utils.DateUtils;
import com.hm.common.utils.SecurityUtils;
import com.hm.common.utils.StringUtils;
import com.hm.common.utils.ip.IpUtils;
import com.hm.manage.domain.SupportChangeLog;
import com.hm.manage.mapper.SupportChangeLogMapper;
import com.hm.manage.service.ISupportChangeLogService;

@Service
public class SupportChangeLogServiceImpl implements ISupportChangeLogService
{
    public static final String ACTION_QUERY = "QUERY";
    public static final String ACTION_INSERT = "INSERT";
    public static final String ACTION_UPDATE = "UPDATE";
    public static final String ACTION_DELETE = "DELETE";
    public static final String ACTION_BIND = "BIND";
    public static final String ACTION_UNBIND = "UNBIND";

    @Autowired
    private SupportChangeLogMapper changeLogMapper;

    private static final int DETAIL_MAX_LENGTH = 8000;

    @Override
    public List<SupportChangeLog> selectSupportChangeLogList(SupportChangeLog changeLog)
    {
        return changeLogMapper.selectSupportChangeLogList(changeLog);
    }

    @Override
    public int insertSupportChangeLog(SupportChangeLog changeLog)
    {
        changeLog.setCreateTime(DateUtils.getNowDate());
        if (StringUtils.isBlank(changeLog.getOperatorName()))
        {
            changeLog.setOperatorName(resolveUsername());
        }
        if (StringUtils.isBlank(changeLog.getOperatorIp()))
        {
            changeLog.setOperatorIp(IpUtils.getIpAddr());
        }
        return changeLogMapper.insertSupportChangeLog(changeLog);
    }

    @Override
    public void record(Long siteId, String actionType, String targetType, Long targetId, String targetName, String summary)
    {
        record(siteId, actionType, targetType, targetId, targetName, summary, null, null);
    }

    @Override
    public void record(Long siteId, String actionType, String targetType, Long targetId, String targetName, String summary, Object beforeData, Object afterData)
    {
        SupportChangeLog changeLog = new SupportChangeLog();
        changeLog.setSiteId(siteId);
        changeLog.setActionType(actionType);
        changeLog.setTargetType(targetType);
        changeLog.setTargetId(targetId);
        changeLog.setTargetName(targetName);
        changeLog.setSummary(summary);
        changeLog.setDetailContent(buildDetailContent(summary, beforeData, afterData));
        insertSupportChangeLog(changeLog);
    }

    @Override
    public void recordQuery(Long siteId, String targetType, Long targetId, String targetName, String summary)
    {
        // 查询动作不再写入现场融合操作记录，避免最近操作被查看类动作淹没。
    }

    private String resolveUsername()
    {
        try
        {
            LoginUser loginUser = SecurityUtils.getLoginUser();
            SysUser user = loginUser.getUser();
            if (user != null && StringUtils.isNotBlank(user.getNickName()))
            {
                return user.getNickName();
            }
            return loginUser.getUsername();
        }
        catch (Exception e)
        {
            return "anonymous";
        }
    }

    private String buildDetailContent(String summary, Object beforeData, Object afterData)
    {
        StringBuilder content = new StringBuilder();
        if (StringUtils.isNotBlank(summary))
        {
            content.append("操作摘要：").append(summary);
        }
        if (beforeData != null)
        {
            appendBlock(content, "变更前", beforeData);
        }
        if (afterData != null)
        {
            appendBlock(content, "变更后", afterData);
        }
        if (content.length() == 0)
        {
            return summary;
        }
        return StringUtils.substring(content.toString(), 0, DETAIL_MAX_LENGTH);
    }

    private void appendBlock(StringBuilder content, String title, Object data)
    {
        if (content.length() > 0)
        {
            content.append("\n\n");
        }
        content.append(title).append("：\n").append(toSafeJson(data));
    }

    private String toSafeJson(Object data)
    {
        PropertyPreExcludeFilter filter = new PropertyPreExcludeFilter();
        filter.addExcludes("osPassword", "osPasswordCipher", "loginPassword", "loginPasswordCipher",
                "password", "passwordCipher", "secretCipher", "children");
        return JSON.toJSONString(data, filter);
    }
}
