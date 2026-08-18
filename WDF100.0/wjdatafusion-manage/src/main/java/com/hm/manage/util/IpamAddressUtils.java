package com.hm.manage.util;

import java.util.ArrayList;
import java.util.List;
import com.hm.common.exception.ServiceException;

public class IpamAddressUtils
{
    private static final long IPV4_MAX = 0xffffffffL;
    private static final int OCTET_COUNT = 4;

    private IpamAddressUtils()
    {
    }

    public static CidrRange parseCidr(String cidrBlock)
    {
        if (isBlank(cidrBlock) || !cidrBlock.contains("/"))
        {
            throw new ServiceException("网段格式必须为CIDR，例如 2.57.0.0/16");
        }
        String[] parts = cidrBlock.trim().split("/");
        if (parts.length != 2)
        {
            throw new ServiceException("网段格式必须为CIDR，例如 2.57.0.0/16");
        }
        int prefixLength;
        try
        {
            prefixLength = Integer.parseInt(parts[1]);
        }
        catch (NumberFormatException e)
        {
            throw new ServiceException("网段掩码必须是0-32之间的数字");
        }
        if (prefixLength < 0 || prefixLength > 32)
        {
            throw new ServiceException("网段掩码必须是0-32之间的数字");
        }
        long source = ipToLong(parts[0]);
        long mask = prefixLength == 0 ? 0 : (IPV4_MAX << (32 - prefixLength)) & IPV4_MAX;
        long start = source & mask;
        long end = start | (~mask & IPV4_MAX);
        return new CidrRange(longToIp(start), longToIp(end), start, end, prefixLength, longToIp(start) + "/" + prefixLength, end - start + 1);
    }

    public static String normalizeCidr(String cidrBlock)
    {
        return parseCidr(cidrBlock).getCidrBlock();
    }

    public static CidrRange parseGatewayAndMask(String gatewayIp, String subnetMask)
    {
        if (isBlank(gatewayIp))
        {
            throw new ServiceException("网关IP不能为空");
        }
        if (isBlank(subnetMask))
        {
            throw new ServiceException("子网掩码不能为空");
        }
        long gatewayValue = ipToLong(gatewayIp);
        int prefixLength = subnetMaskToPrefixLength(subnetMask);
        if (prefixLength < 1 || prefixLength > 30)
        {
            throw new ServiceException("子网掩码必须保留网络地址、广播地址和至少一个可分配地址");
        }
        long maskValue = prefixLength == 0 ? 0 : (IPV4_MAX << (32 - prefixLength)) & IPV4_MAX;
        long start = gatewayValue & maskValue;
        long end = start | (~maskValue & IPV4_MAX);
        if (gatewayValue == start || gatewayValue == end)
        {
            throw new ServiceException("网关IP不能使用网络地址或广播地址");
        }
        return new CidrRange(longToIp(start), longToIp(end), start, end, prefixLength,
            longToIp(start) + "/" + prefixLength, end - start + 1);
    }

    public static int subnetMaskToPrefixLength(String subnetMask)
    {
        long maskValue = ipToLong(subnetMask);
        long inverse = ~maskValue & IPV4_MAX;
        if ((inverse & (inverse + 1)) != 0)
        {
            throw new ServiceException("子网掩码必须由连续的1和连续的0组成");
        }
        return Long.bitCount(maskValue);
    }

    public static String prefixLengthToSubnetMask(int prefixLength)
    {
        if (prefixLength < 0 || prefixLength > 32)
        {
            throw new ServiceException("子网掩码长度必须在0-32之间");
        }
        long maskValue = prefixLength == 0 ? 0 : (IPV4_MAX << (32 - prefixLength)) & IPV4_MAX;
        return longToIp(maskValue);
    }

    public static long ipToLong(String ip)
    {
        if (isBlank(ip))
        {
            throw new ServiceException("IP地址不能为空");
        }
        String[] octets = ip.trim().split("\\.");
        if (octets.length != OCTET_COUNT)
        {
            throw new ServiceException("IP地址格式不正确：" + ip);
        }
        long result = 0;
        for (String octet : octets)
        {
            if (!octet.matches("\\d{1,3}"))
            {
                throw new ServiceException("IP地址格式不正确：" + ip);
            }
            int value;
            try
            {
                value = Integer.parseInt(octet);
            }
            catch (NumberFormatException e)
            {
                throw new ServiceException("IP地址格式不正确：" + ip);
            }
            if (value < 0 || value > 255)
            {
                throw new ServiceException("IP地址每段必须在0-255之间：" + ip);
            }
            result = (result << 8) + value;
        }
        return result;
    }

    public static String longToIp(long value)
    {
        if (value < 0 || value > IPV4_MAX)
        {
            throw new ServiceException("IP数值超出IPv4范围");
        }
        return ((value >> 24) & 255) + "." + ((value >> 16) & 255) + "." + ((value >> 8) & 255) + "." + (value & 255);
    }

    public static boolean containsIp(String cidrBlock, String ip)
    {
        CidrRange range = parseCidr(cidrBlock);
        long value = ipToLong(ip);
        return value >= range.getStartValue() && value <= range.getEndValue();
    }

    public static boolean containsRange(String parentCidr, String childCidr)
    {
        CidrRange parent = parseCidr(parentCidr);
        CidrRange child = parseCidr(childCidr);
        return child.getStartValue() >= parent.getStartValue() && child.getEndValue() <= parent.getEndValue();
    }

    public static boolean isBoundaryAddress(String ip, String cidrBlock)
    {
        CidrRange range = parseCidr(cidrBlock);
        long value = ipToLong(ip);
        return value == range.getStartValue() || value == range.getEndValue();
    }

    public static List<String> expandAddressGrid(String cidrBlock)
    {
        CidrRange range = parseCidr(cidrBlock);
        if (range.getTotalCount() > 1024)
        {
            throw new ServiceException("地址网格最多支持1024个IP，请调整子网掩码");
        }
        return expandAddressGrid(cidrBlock, 0, (int) range.getTotalCount());
    }

    public static List<String> expandAddressGrid(String cidrBlock, long offset, int limit)
    {
        CidrRange range = parseCidr(cidrBlock);
        if (offset < 0)
        {
            throw new ServiceException("地址窗口偏移量不能小于0");
        }
        if (limit < 1 || limit > 1024)
        {
            throw new ServiceException("地址窗口每页必须在1-1024之间");
        }
        List<String> result = new ArrayList<>();
        if (offset >= range.getTotalCount())
        {
            return result;
        }
        long start = range.getStartValue() + offset;
        long end = Math.min(range.getEndValue(), start + limit - 1L);
        for (long value = start; value <= end; value++)
        {
            result.add(longToIp(value));
        }
        return result;
    }

    public static List<CidrRange> generateChildCidrs(String cidrBlock, int childPrefixLength)
    {
        CidrRange parent = parseCidr(cidrBlock);
        if (parent.getPrefixLength() > childPrefixLength)
        {
            throw new ServiceException("子网段掩码不能小于父网段掩码");
        }
        long childSize = 1L << (32 - childPrefixLength);
        long childCount = parent.getTotalCount() / childSize;
        if (childCount > 4096)
        {
            throw new ServiceException("自动生成子网段数量过多，请使用更小的大网段");
        }
        List<CidrRange> result = new ArrayList<>();
        for (int i = 0; i < childCount; i++)
        {
            long start = parent.getStartValue() + i * childSize;
            result.add(parseCidr(longToIp(start) + "/" + childPrefixLength));
        }
        return result;
    }

    public static int getLastOctet(String ip)
    {
        long value = ipToLong(ip);
        return (int) (value & 255);
    }

    private static boolean isBlank(String value)
    {
        return value == null || value.trim().isEmpty();
    }

    public static class CidrRange
    {
        private final String startIp;
        private final String endIp;
        private final long startValue;
        private final long endValue;
        private final int prefixLength;
        private final String cidrBlock;
        private final long totalCount;

        public CidrRange(String startIp, String endIp, long startValue, long endValue, int prefixLength, String cidrBlock, long totalCount)
        {
            this.startIp = startIp;
            this.endIp = endIp;
            this.startValue = startValue;
            this.endValue = endValue;
            this.prefixLength = prefixLength;
            this.cidrBlock = cidrBlock;
            this.totalCount = totalCount;
        }

        public String getStartIp()
        {
            return startIp;
        }

        public String getEndIp()
        {
            return endIp;
        }

        public long getStartValue()
        {
            return startValue;
        }

        public long getEndValue()
        {
            return endValue;
        }

        public int getPrefixLength()
        {
            return prefixLength;
        }

        public String getCidrBlock()
        {
            return cidrBlock;
        }

        public long getTotalCount()
        {
            return totalCount;
        }

        public boolean contains(String ip)
        {
            long value = IpamAddressUtils.ipToLong(ip);
            return value >= startValue && value <= endValue;
        }

        public boolean isBoundary(String ip)
        {
            long value = IpamAddressUtils.ipToLong(ip);
            return value == startValue || value == endValue;
        }

        public boolean overlaps(CidrRange other)
        {
            return other != null && startValue <= other.endValue && endValue >= other.startValue;
        }
    }
}
