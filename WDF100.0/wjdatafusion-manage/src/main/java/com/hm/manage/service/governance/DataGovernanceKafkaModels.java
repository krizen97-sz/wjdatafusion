package com.hm.manage.service.governance;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DataGovernanceKafkaModels
{
    private DataGovernanceKafkaModels() { }
    public record ProfileRequest(String name, List<String> bootstrapServers, String topic, String groupId, Long revision) { }
    public record ProfileView(String id, long revision, String name, List<String> bootstrapServers, String topic, String groupId, String fingerprint, boolean autoCommit, String isolation, String initialOffset, List<String> limitations) { }
    public record Binding(String profileId, String fingerprint, List<String> bootstrapServers, String topic, String groupId, String leaseKey) { }
    public record ReceiptSummary(String id, String profileId, String profileName, String topic, String groupId, String status,
        int recordCount, String inputSha256, Map<String, String> initialOffsets, Map<String, String> nextOffsets,
        String runId, String deliveryConnectionId, String error, String createdAt, String updatedAt, boolean leaseHeld, String isolation, String deliveryStatus) { }
    public record ReceiptDetail(ReceiptSummary receipt, String inputJson) { }
    public static class Profile
    {
        public String id;
        public long ownerId;
        public long revision;
        public String name;
        public List<String> bootstrapServers;
        public String topic;
        public String groupId;
        public String fingerprint;
    }
    public static class Receipt
    {
        public String id;
        public long ownerId;
        public String profileName;
        public Binding binding;
        public String status;
        public String inputJson;
        public String inputSha256;
        public int recordCount;
        /** Partition numbers are keys; null means no prior committed offset. No JSON/JS numeric offsets. */
        public Map<String, String> initialOffsets = new LinkedHashMap<>();
        public Map<String, String> startOffsets = new LinkedHashMap<>();
        public Map<String, String> nextOffsets = new LinkedHashMap<>();
        public String runId;
        public String reservedRunId;
        public String executionIntentAt;
        public DataGovernanceScheduleDelivery.Binding deliveryBinding;
        public String commitIntentAt;
        public String deliveryStatus;
        public String error;
        public String createdAt;
        public String updatedAt;
        public boolean leaseHeld = true;
    }
}
