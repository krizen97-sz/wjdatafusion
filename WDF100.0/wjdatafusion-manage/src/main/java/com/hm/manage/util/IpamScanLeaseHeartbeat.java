package com.hm.manage.util;

import java.util.function.LongSupplier;

public final class IpamScanLeaseHeartbeat
{
    private static final long MIN_RENEW_INTERVAL_MS = 1_000L;

    private final LongSupplier clock;
    private final long renewIntervalMs;
    private long nextRenewalAt;

    public IpamScanLeaseHeartbeat(int leaseSeconds)
    {
        this(leaseSeconds, System::currentTimeMillis);
    }

    IpamScanLeaseHeartbeat(int leaseSeconds, LongSupplier clock)
    {
        this.clock = clock;
        this.renewIntervalMs = Math.max(MIN_RENEW_INTERVAL_MS, leaseSeconds * 1_000L / 3L);
        markRenewed();
    }

    public boolean isRenewalDue()
    {
        return clock.getAsLong() >= nextRenewalAt;
    }

    public void markRenewed()
    {
        nextRenewalAt = clock.getAsLong() + renewIntervalMs;
    }
}
