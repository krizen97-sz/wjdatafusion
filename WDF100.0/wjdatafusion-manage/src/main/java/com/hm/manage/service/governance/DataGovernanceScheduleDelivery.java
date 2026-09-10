package com.hm.manage.service.governance;

/** Optional bridge to an owner-scoped FTP delivery implementation; scheduling never handles credentials. */
public interface DataGovernanceScheduleDelivery
{
    /** Fingerprint covers destination identity and wire settings, excluding password, name and revision. */
    record Binding(String id, String name, String fingerprint) { }
    /** QUEUED/RUNNING retain occupancy; DELIVERED alone confirms completion; other states need review. */
    record Delivery(String id, String status, String error, String targetFingerprint)
    {
        public Delivery(String id, String status, String error) { this(id, status, error, null); }
    }

    Binding target(String connectionId, long owner);

    /** Must check the frozen fingerprint and complete manifest, and be idempotent by runId + connectionId. */
    Delivery submit(String runId, Binding binding, long owner);

    /** Read-only lookup; returns null only when no existing delivery is known. Never initiates a retry. */
    Delivery find(String runId, String connectionId, long owner);

    Delivery status(String deliveryId, long owner);
}
