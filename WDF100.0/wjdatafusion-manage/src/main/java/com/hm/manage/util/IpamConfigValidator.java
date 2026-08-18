package com.hm.manage.util;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import com.hm.common.exception.ServiceException;
import com.hm.manage.domain.bo.IpamConfigCommitBo;
import com.hm.manage.domain.bo.IpamConfigRowBo;
import com.hm.manage.util.IpamAddressUtils.CidrRange;

public final class IpamConfigValidator
{
    public static final int MAX_CONFIG_ROWS = 256;

    private static final Set<String> ALLOWED_STATUSES = Set.of(
        "FREE", "RESERVED", "ALLOCATED", "ISSUED", "DISABLED");

    private IpamConfigValidator()
    {
    }

    public static void validate(IpamConfigCommitBo commit, CidrRange range, String gatewayIp)
    {
        if (commit == null || commit.getNetworkId() == null)
        {
            throw new ServiceException("请选择要配置的网段");
        }
        List<IpamConfigRowBo> rows = commit.getRows();
        if (rows == null || rows.isEmpty())
        {
            throw new ServiceException("没有需要保存的配置行");
        }
        if (rows.size() > MAX_CONFIG_ROWS)
        {
            throw new ServiceException("单次最多配置" + MAX_CONFIG_ROWS + "个IP，请分批保存");
        }

        Set<String> seen = new HashSet<>();
        for (int index = 0; index < rows.size(); index++)
        {
            IpamConfigRowBo row = rows.get(index);
            if (row == null)
            {
                throw new ServiceException("第" + (index + 1) + "行配置不能为空");
            }
            String ip = IpamAddressUtils.longToIp(IpamAddressUtils.ipToLong(row.getIpAddress()));
            if (!seen.add(ip))
            {
                throw new ServiceException("存在重复IP：" + ip);
            }
            if (!range.contains(ip))
            {
                throw new ServiceException("IP地址不在当前网段内：" + ip);
            }

            String status = normalizeStatus(row.getStatus());
            if (!ALLOWED_STATUSES.contains(status))
            {
                throw new ServiceException("地址状态不合法：" + status);
            }
            if ((range.isBoundary(ip) || ip.equals(gatewayIp)) && !"RESERVED".equals(status))
            {
                throw new ServiceException("网络地址、广播地址或网关地址只能保留：" + ip);
            }
            if (("ALLOCATED".equals(status) || "ISSUED".equals(status)) && isBlank(row.getCommunityName()))
            {
                throw new ServiceException("第" + (index + 1) + "行小区名称或项目名称不能为空");
            }
            if (!isBlank(row.getInternalIpAddress()))
            {
                IpamAddressUtils.ipToLong(row.getInternalIpAddress());
            }

            row.setIpAddress(ip);
            row.setStatus(status);
            row.setCommunityName(trimToNull(row.getCommunityName()));
        }
    }

    private static String normalizeStatus(String status)
    {
        return isBlank(status) ? "ALLOCATED" : status.trim().toUpperCase(Locale.ROOT);
    }

    private static String trimToNull(String value)
    {
        return isBlank(value) ? null : value.trim();
    }

    private static boolean isBlank(String value)
    {
        return value == null || value.trim().isEmpty();
    }
}
