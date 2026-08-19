package com.hm.manage.service.impl;

import java.util.regex.Pattern;
import com.hm.common.exception.ServiceException;
import com.hm.common.utils.StringUtils;

final class AutoInspectionReadOnlyQueryGuard
{
    private static final Pattern UNSAFE_QUERY = Pattern.compile(
            "(?is)\\b(insert|update|delete|drop|alter|truncate|create|grant|revoke|call|copy|vacuum|analyze|refresh|set|reset|replace|merge|execute|prepare)\\b|for\\s+update|into\\s+(outfile|dumpfile)|load_file\\s*\\(|pg_(read|write)_file\\s*\\(");

    private AutoInspectionReadOnlyQueryGuard()
    {
    }

    static String normalize(String query)
    {
        String normalized = StringUtils.trimToEmpty(query);
        while (normalized.endsWith(";"))
        {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }
        if (StringUtils.isBlank(normalized))
        {
            throw new ServiceException("只读查询SQL不能为空");
        }
        String inspected = maskLiteralsAndComments(normalized);
        if (inspected.contains(";"))
        {
            throw new ServiceException("一次只能执行一条只读查询SQL");
        }
        if (!inspected.matches("(?is)^\\s*(select|with)\\b.*"))
        {
            throw new ServiceException("数据库巡检只允许SELECT或WITH查询");
        }
        if (UNSAFE_QUERY.matcher(inspected).find())
        {
            throw new ServiceException("查询包含写入、锁表或高风险数据库操作，已拒绝执行");
        }
        return normalized;
    }

    private static String maskLiteralsAndComments(String sql)
    {
        StringBuilder masked = new StringBuilder(sql.length());
        char quote = 0;
        boolean lineComment = false;
        boolean blockComment = false;
        for (int index = 0; index < sql.length(); index++)
        {
            char current = sql.charAt(index);
            char next = index + 1 < sql.length() ? sql.charAt(index + 1) : 0;
            if (lineComment)
            {
                if (current == '\n' || current == '\r')
                {
                    lineComment = false;
                    masked.append(current);
                }
                else
                {
                    masked.append(' ');
                }
                continue;
            }
            if (blockComment)
            {
                if (current == '*' && next == '/')
                {
                    blockComment = false;
                    masked.append("  ");
                    index++;
                }
                else
                {
                    masked.append(' ');
                }
                continue;
            }
            if (quote != 0)
            {
                masked.append(' ');
                if (current == quote)
                {
                    if (next == quote)
                    {
                        masked.append(' ');
                        index++;
                    }
                    else
                    {
                        quote = 0;
                    }
                }
                continue;
            }
            if (current == '-' && next == '-')
            {
                lineComment = true;
                masked.append("  ");
                index++;
            }
            else if (current == '/' && next == '*')
            {
                blockComment = true;
                masked.append("  ");
                index++;
            }
            else if (current == '\'' || current == '"' || current == '`')
            {
                quote = current;
                masked.append(' ');
            }
            else
            {
                masked.append(current);
            }
        }
        return masked.toString();
    }
}
