package com.hm.manage.service.support;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import com.hm.common.exception.ServiceException;
import com.hm.manage.util.IpamAddressUtils;

public final class SupportServerScanTargets
{
    public static final int MAX_HOSTS = 512;
    public static final List<Integer> PORTS = List.of(22, 2343, 55555);
    private SupportServerScanTargets() {}

    public static String canonical(String address)
    {
        try { return IpamAddressUtils.longToIp(IpamAddressUtils.ipToLong(address)); }
        catch (ServiceException invalid) { return address == null ? "" : address.trim(); }
    }

    public static List<String> expand(String input)
    {
        if (input == null || input.isBlank() || input.length() > 8192) throw new ServiceException("请填写IP、CIDR网段或起止IP段");
        Set<String> hosts = new LinkedHashSet<>();
        for (String token : input.trim().split("[;；,，\\s]+"))
        {
            long start, end;
            if (token.contains("/"))
            {
                var range = IpamAddressUtils.parseCidr(token);
                start = range.getStartValue(); end = range.getEndValue();
                if (range.getPrefixLength() < 31) { start++; end--; }
            }
            else if (token.contains("-"))
            {
                String[] pair = token.split("-", -1);
                if (pair.length != 2) throw new ServiceException("IP段格式不正确");
                start = IpamAddressUtils.ipToLong(pair[0]); end = IpamAddressUtils.ipToLong(pair[1]);
            }
            else { start = IpamAddressUtils.ipToLong(token); end = start; }
            if (end < start || end - start + 1 > MAX_HOSTS) throw new ServiceException("单次扫描最多512个地址，请缩小范围");
            for (long value = start; value <= end; value++)
            {
                long first = value >>> 24, second = (value >>> 16) & 255;
                if (first == 0 || first == 127 || first >= 224 || (first == 169 && second == 254))
                    throw new ServiceException("扫描范围不能包含回环、链路本地、组播或保留地址");
                hosts.add(IpamAddressUtils.longToIp(value));
                if (hosts.size() > MAX_HOSTS) throw new ServiceException("单次扫描最多512个地址，请缩小范围");
            }
        }
        return new ArrayList<>(hosts);
    }

    public static List<Integer> ports(List<Integer> input)
    {
        if (input == null || input.isEmpty()) return PORTS;
        if (input.size() > 3 || input.stream().anyMatch(port -> port == null || !PORTS.contains(port)))
            throw new ServiceException("扫描端口仅支持22、2343、55555");
        return input.stream().distinct().toList();
    }
}
