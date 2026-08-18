package com.hm.manage.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import com.hm.common.exception.ServiceException;
import com.hm.manage.util.IpamAddressUtils.CidrRange;

class IpamAddressUtilsTest
{
    @Test
    void shouldDeriveNetworkRangeFromGatewayAndMask()
    {
        CidrRange range = IpamAddressUtils.parseGatewayAndMask("2.57.1.1", "255.255.255.0");

        assertTrue(range.contains("2.57.1.1"));
        assertTrue(range.isBoundary("2.57.1.0"));
        assertTrue(range.isBoundary("2.57.1.255"));
        assertFalse(range.isBoundary("2.57.1.254"));
    }

    @Test
    void shouldRejectDiscontinuousMask()
    {
        assertThrows(ServiceException.class,
            () -> IpamAddressUtils.parseGatewayAndMask("2.57.1.1", "255.0.255.0"));
    }

    @Test
    void shouldDetectOverlappingButNotAdjacentRanges()
    {
        CidrRange first = IpamAddressUtils.parseCidr("2.57.1.0/24");
        CidrRange overlap = IpamAddressUtils.parseCidr("2.57.1.128/25");
        CidrRange adjacent = IpamAddressUtils.parseCidr("2.57.2.0/24");

        assertTrue(first.overlaps(overlap));
        assertFalse(first.overlaps(adjacent));
    }
}
