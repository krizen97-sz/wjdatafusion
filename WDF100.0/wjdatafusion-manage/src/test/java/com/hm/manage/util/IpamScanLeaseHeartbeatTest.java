package com.hm.manage.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class IpamScanLeaseHeartbeatTest
{
    @Test
    void shouldRequestRenewalBeforeLeaseCanExpire()
    {
        AtomicLong clock = new AtomicLong(1_000L);
        IpamScanLeaseHeartbeat heartbeat = new IpamScanLeaseHeartbeat(30, clock::get);

        assertFalse(heartbeat.isRenewalDue());

        clock.addAndGet(9_999L);
        assertFalse(heartbeat.isRenewalDue());

        clock.incrementAndGet();
        assertTrue(heartbeat.isRenewalDue());

        heartbeat.markRenewed();
        assertFalse(heartbeat.isRenewalDue());
    }
}
