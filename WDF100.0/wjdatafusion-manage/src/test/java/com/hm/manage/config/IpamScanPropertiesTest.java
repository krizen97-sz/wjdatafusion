package com.hm.manage.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class IpamScanPropertiesTest
{
    @Test
    void shouldClampLeaseToSafeBounds()
    {
        IpamScanProperties properties = new IpamScanProperties();

        properties.setLeaseSeconds(1);
        assertEquals(30, properties.getLeaseSeconds());

        properties.setLeaseSeconds(9999);
        assertEquals(3600, properties.getLeaseSeconds());
    }
}
