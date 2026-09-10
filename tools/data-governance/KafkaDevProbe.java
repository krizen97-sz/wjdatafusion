import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

/** A fixed-loopback, synthetic-only probe; no connection address is accepted from arguments. */
public final class KafkaDevProbe {
    private static final String BOOTSTRAP = "127.0.0.1:19092";
    private static final Duration WAIT = Duration.ofSeconds(10);

    public static void main(String[] args) throws Exception {
        require(args.length >= 2 && args[1].matches("[A-Za-z0-9_-]{22}"), "Expected owned cluster identity required");
        Map<String, Object> health = health(args[1]);
        Map<String, Object> result;
        if (args[0].equals("health")) result = health;
        else {
            require(args.length == 3 && args[2].matches("[0-9a-f]{32}"), "Synthetic run identity required");
            result = switch (args[0]) {
                case "seed" -> seed(args[2]);
                case "verify" -> verify(args[2]);
                default -> throw new IllegalArgumentException("Unknown probe action");
            };
            result.put("clusterId", args[1]);
        }
        System.out.println(json(result));
    }

    private static Properties config() {
        Properties p = new Properties();
        p.put("bootstrap.servers", BOOTSTRAP); p.put("security.protocol", "PLAINTEXT");
        p.put("default.api.timeout.ms", "5000"); p.put("request.timeout.ms", "3000");
        return p;
    }
    private static Map<String, Object> health(String expectedCluster) throws Exception {
        Admin admin = Admin.create(config());
        try {
            var description = admin.describeCluster();
            String cluster = description.clusterId().get(5, TimeUnit.SECONDS);
            require(expectedCluster.equals(cluster), "Cluster identity mismatch; no topic operation attempted");
            Collection<Node> nodes = description.nodes().get(5, TimeUnit.SECONDS);
            require(nodes.size() == 1, "Expected one broker");
            Node node = nodes.iterator().next();
            require(node.id() == 1 && node.host().equals("127.0.0.1") && node.port() == 19092, "Broker advertised an unapproved endpoint");
            var quorum = admin.describeMetadataQuorum().quorumInfo().get(5, TimeUnit.SECONDS);
            require(quorum.leaderId() == 1 && quorum.voters().size() == 1 && quorum.voters().get(0).replicaId() == 1, "Standalone KRaft quorum mismatch");
            return map("healthy", true, "clusterId", cluster, "brokerCount", 1, "advertisedBroker", BOOTSTRAP,
                "controllerLeaderId", quorum.leaderId(), "voters", quorum.voters().size(), "metadataHighWatermark", quorum.highWatermark());
        } finally { admin.close(Duration.ofSeconds(1)); }
    }
    private static KafkaConsumer<String, String> consumer(String group) {
        Properties p = config(); p.put("group.id", group); p.put("group.protocol", "classic");
        p.put("enable.auto.commit", "false"); p.put("auto.offset.reset", "earliest"); p.put("max.poll.records", "20");
        p.put("key.deserializer", StringDeserializer.class.getName()); p.put("value.deserializer", StringDeserializer.class.getName());
        return new KafkaConsumer<>(p);
    }
    private static String topic(String run) { return "rynew-governance-smoke-" + run; }
    private static String group(String run) { return "rynew-governance-manual-" + run; }
    private static List<TopicPartition> partitions(String run) { return List.of(new TopicPartition(topic(run), 0), new TopicPartition(topic(run), 1)); }
    private static String body(String run, int partition, long sequence) {
        return "{\"synthetic\":true,\"run\":\"" + run + "\",\"partition\":" + partition + ",\"sequence\":" + sequence + ",\"text\":\"合成事件\"}";
    }
    private static Map<String, Object> seed(String run) throws Exception {
        Admin admin = Admin.create(config());
        try {
            require(!admin.listTopics().names().get(5, TimeUnit.SECONDS).contains(topic(run)), "Synthetic topic already exists; refusing to overwrite");
            admin.createTopics(List.of(new NewTopic(topic(run), 2, (short) 1).configs(Map.of("retention.ms", "604800000")))).all().get(10, TimeUnit.SECONDS);
            var description = admin.describeTopics(List.of(topic(run))).allTopicNames().get(5, TimeUnit.SECONDS).get(topic(run));
            require(description.partitions().size() == 2, "Topic partition count mismatch");
        } finally { admin.close(Duration.ofSeconds(1)); }
        Properties producerConfig = config(); producerConfig.put("key.serializer", StringSerializer.class.getName()); producerConfig.put("value.serializer", StringSerializer.class.getName());
        producerConfig.put("acks", "all"); producerConfig.put("enable.idempotence", "true"); producerConfig.put("delivery.timeout.ms", "10000"); producerConfig.put("max.block.ms", "5000"); producerConfig.put("linger.ms", "0");
        KafkaProducer<String, String> producer = new KafkaProducer<>(producerConfig);
        try {
            for (int partition = 0; partition < 2; partition++) for (int sequence = 0; sequence < 3; sequence++) {
                var metadata = producer.send(new ProducerRecord<>(topic(run), partition, "synthetic-key-" + partition, body(run, partition, sequence))).get(10, TimeUnit.SECONDS);
                require(metadata.partition() == partition && metadata.offset() == sequence, "Unexpected fresh-topic offset");
            }
        } finally { producer.close(Duration.ofSeconds(1)); }
        KafkaConsumer<String, String> consumer = consumer(group(run));
        try {
            consumer.assign(partitions(run)); consumer.seekToBeginning(partitions(run));
            List<ConsumerRecord<String, String>> records = read(consumer, 6);
            validate(records, run, 0, 3);
            Map<TopicPartition, OffsetAndMetadata> before = consumer.committed(Set.copyOf(partitions(run)), WAIT);
            require(before.values().stream().allMatch(value -> value == null), "Offsets were committed before explicit manual commit");
            Map<TopicPartition, OffsetAndMetadata> commit = new LinkedHashMap<>();
            for (TopicPartition partition : partitions(run)) commit.put(partition, new OffsetAndMetadata(2));
            consumer.commitSync(commit, WAIT);
            var committed = consumer.committed(Set.copyOf(partitions(run)), WAIT);
            require(committed.size() == 2 && committed.values().stream().allMatch(value -> value != null && value.offset() == 2), "Manual prefix commit was not persisted");
            return map("topic", topic(run), "group", group(run), "partitions", 2, "producedRecords", 6, "consumedRecords", 6,
                "autoCommitDisabled", true, "nothingCommittedBeforeManualCommit", true, "committedNextOffsets", map("0", 2, "1", 2));
        } finally { consumer.close(Duration.ofSeconds(1)); }
    }
    private static Map<String, Object> verify(String run) throws Exception {
        KafkaConsumer<String, String> consumer = consumer(group(run));
        try {
            consumer.assign(partitions(run));
            var committed = consumer.committed(Set.copyOf(partitions(run)), WAIT);
            require(committed.size() == 2 && committed.values().stream().allMatch(value -> value != null && value.offset() == 2), "Manual offsets did not survive broker restart");
            require(consumer.endOffsets(partitions(run), WAIT).values().stream().allMatch(value -> value == 3), "Topic data length did not survive restart");
            List<ConsumerRecord<String, String>> resumed = read(consumer, 2);
            validate(resumed, run, 2, 1);
            consumer.commitSync(WAIT);
            require(consumer.committed(Set.copyOf(partitions(run)), WAIT).values().stream().allMatch(value -> value != null && value.offset() == 3), "Final manual commit failed");
            consumer.seekToBeginning(partitions(run));
            List<ConsumerRecord<String, String>> replay = read(consumer, 6);
            validate(replay, run, 0, 3);
            return map("topic", topic(run), "partitions", 2, "committedOffsetsSurvivedRestart", true, "resumedFromCommittedOffsets", true,
                "resumedRecords", resumed.size(), "fullReplayRecords", replay.size(), "payloadsAndKeysMatched", true, "committedNextOffsets", map("0", 3, "1", 3));
        } finally { consumer.close(Duration.ofSeconds(1)); }
    }
    private static List<ConsumerRecord<String, String>> read(KafkaConsumer<String, String> consumer, int count) {
        List<ConsumerRecord<String, String>> result = new ArrayList<>();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (result.size() < count && System.nanoTime() < deadline) consumer.poll(Duration.ofMillis(250)).forEach(result::add);
        require(result.size() == count, "Bounded synthetic consumer returned an unexpected record count");
        return result;
    }
    private static void validate(List<ConsumerRecord<String, String>> records, String run, int first, int count) {
        for (int partition = 0; partition < 2; partition++) {
            int expectedPartition = partition;
            var values = records.stream().filter(record -> record.partition() == expectedPartition).sorted(Comparator.comparingLong(ConsumerRecord::offset)).toList();
            require(values.size() == count, "Partition record count mismatch");
            for (int index = 0; index < count; index++) {
                var record = values.get(index); long offset = first + index;
                require(record.offset() == offset && record.key().equals("synthetic-key-" + partition) && record.value().equals(body(run, partition, offset)), "Synthetic record value, key or offset mismatch");
            }
        }
    }
    private static Map<String, Object> map(Object... items) {
        Map<String, Object> result = new LinkedHashMap<>(); for (int index = 0; index < items.length; index += 2) result.put((String) items[index], items[index + 1]); return result;
    }
    private static String json(Object value) {
        if (value == null) return "null";
        if (value instanceof String text) return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\"";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) return "{" + String.join(",", map.entrySet().stream().map(entry -> json(entry.getKey()) + ":" + json(entry.getValue())).toList()) + "}";
        throw new IllegalArgumentException("Unsupported probe result value");
    }
    private static void require(boolean condition, String reason) { if (!condition) throw new IllegalStateException(reason); }
}
