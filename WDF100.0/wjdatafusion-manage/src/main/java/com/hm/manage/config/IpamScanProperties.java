package com.hm.manage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ipam.scan")
public class IpamScanProperties
{
    private static final int MIN_TIMEOUT_MS = 100;
    private static final int MAX_TIMEOUT_MS = 10000;
    private static final int MAX_INTERVAL_MS = 2000;
    private static final int MAX_CONCURRENCY = 32;
    private static final int MIN_BATCH_SIZE = 10;
    private static final int MAX_BATCH_SIZE = 500;
    private static final int MIN_LEASE_SECONDS = 30;
    private static final int MAX_LEASE_SECONDS = 3600;
    private static final long MIN_NETWORK_ADDRESS_COUNT = 2L;
    private static final long MAX_NETWORK_ADDRESS_COUNT = 1048576L;

    private int timeoutMs = 1000;
    private int intervalMs = 30;
    private int concurrency = 12;
    private int batchSize = 100;
    private int leaseSeconds = 120;
    private long maxAddressesPerNetwork = 65536L;

    public int getTimeoutMs()
    {
        return clamp(timeoutMs, MIN_TIMEOUT_MS, MAX_TIMEOUT_MS);
    }

    public void setTimeoutMs(int timeoutMs)
    {
        this.timeoutMs = timeoutMs;
    }

    public int getIntervalMs()
    {
        return clamp(intervalMs, 0, MAX_INTERVAL_MS);
    }

    public void setIntervalMs(int intervalMs)
    {
        this.intervalMs = intervalMs;
    }

    public int getConcurrency()
    {
        return clamp(concurrency, 1, MAX_CONCURRENCY);
    }

    public void setConcurrency(int concurrency)
    {
        this.concurrency = concurrency;
    }

    public int getBatchSize()
    {
        return clamp(batchSize, MIN_BATCH_SIZE, MAX_BATCH_SIZE);
    }

    public void setBatchSize(int batchSize)
    {
        this.batchSize = batchSize;
    }

    public long getMaxAddressesPerNetwork()
    {
        return clamp(maxAddressesPerNetwork, MIN_NETWORK_ADDRESS_COUNT, MAX_NETWORK_ADDRESS_COUNT);
    }

    public int getLeaseSeconds()
    {
        return clamp(leaseSeconds, MIN_LEASE_SECONDS, MAX_LEASE_SECONDS);
    }

    public void setLeaseSeconds(int leaseSeconds)
    {
        this.leaseSeconds = leaseSeconds;
    }

    public void setMaxAddressesPerNetwork(long maxAddressesPerNetwork)
    {
        this.maxAddressesPerNetwork = maxAddressesPerNetwork;
    }

    private int clamp(int value, int minimum, int maximum)
    {
        return Math.max(minimum, Math.min(value, maximum));
    }

    private long clamp(long value, long minimum, long maximum)
    {
        return Math.max(minimum, Math.min(value, maximum));
    }
}
