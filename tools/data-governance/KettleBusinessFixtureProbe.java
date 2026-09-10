import com.fasterxml.jackson.databind.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.*;
import org.apache.zookeeper.*;

/** Dedicated localhost fixture client. Runs with official Kafka client jars, never Kettle jars. */
class KettleBusinessFixtureProbe {
    static final ObjectMapper JSON = new ObjectMapper();
    static final String BROKER = "127.0.0.1:29092";
    static void require(boolean yes, String message) { if (!yes) throw new IllegalStateException(message); }
    static void named(String value) { require(value.matches("rynew-fixture-[a-z][a-z0-9_]{2,31}-(ordinary|illegal|egress)"), "Dedicated fixture name required"); }
    static Properties common() {
        Properties p = new Properties(); p.put("bootstrap.servers", BROKER); p.put("request.timeout.ms", "10000");
        p.put("default.api.timeout.ms", "15000"); return p;
    }
    static Map<String,Object> health() throws Exception {
        try (Admin admin = Admin.create(common())) {
            var cluster = admin.describeCluster(); var nodes = cluster.nodes().get(15, TimeUnit.SECONDS);
            require(nodes.size() == 1 && nodes.iterator().next().host().equals("127.0.0.1") && nodes.iterator().next().port() == 29092, "Unexpected broker endpoints");
            return Map.of("clusterId", cluster.clusterId().get(15, TimeUnit.SECONDS), "broker", BROKER);
        }
    }
    static ZooKeeper zoo() throws Exception {
        CountDownLatch connected = new CountDownLatch(1);
        ZooKeeper zk = new ZooKeeper("127.0.0.1:22181", 5000, e -> { if (e.getState() == Watcher.Event.KeeperState.SyncConnected) connected.countDown(); });
        require(connected.await(8, TimeUnit.SECONDS), "ZooKeeper connection timeout"); return zk;
    }
    static void createPath(ZooKeeper zk, String path, String data) throws Exception {
        String built = "";
        for (String segment : path.substring(1).split("/")) {
            built += "/" + segment;
            if (zk.exists(built, false) == null) zk.create(built, (built.equals(path) ? data : "").getBytes(StandardCharsets.UTF_8), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        }
    }
    static Map<String,Object> audit(JsonNode manifest) throws Exception {
        Map<String,Object> result = new LinkedHashMap<>();
        try (ZooKeeper zk = zoo()) {
            for (String kind : List.of("ordinary", "illegal")) {
                String group = manifest.path("groups").path(kind).asText(), topic = manifest.path("topics").path(kind).asText(); named(group); named(topic);
                String base = "/consumers/" + group, path = base + "/offsets/" + topic + "/0";
                String offset = zk.exists(path, false) == null ? null : new String(zk.getData(path, false, null), StandardCharsets.UTF_8);
                Map<String,Object> value = new LinkedHashMap<>(); value.put("group", group); value.put("topic", topic); value.put("zookeeperOffset", offset);
                value.put("owners", zk.exists(base + "/owners/" + topic, false) == null ? List.of() : zk.getChildren(base + "/owners/" + topic, false));
                result.put(kind, value);
            }
        }
        Properties p = common(); p.put("key.deserializer", StringDeserializer.class.getName()); p.put("value.deserializer", StringDeserializer.class.getName()); p.put("enable.auto.commit", "false");
        try (KafkaConsumer<String,String> consumer = new KafkaConsumer<>(p)) {
            Map<String,Long> ends = new TreeMap<>();
            for (String kind : List.of("ordinary", "illegal", "egress")) {
                String topic = manifest.path("topics").path(kind).asText(); named(topic); TopicPartition partition = new TopicPartition(topic, 0);
                ends.put(kind, consumer.endOffsets(List.of(partition)).get(partition));
            }
            result.put("endOffsets", ends);
        }
        return result;
    }
    static Map<String,Object> prepare(Path directory, JsonNode manifest) throws Exception {
        List<String> topics = new ArrayList<>();
        for (String kind : List.of("ordinary", "illegal", "egress")) { String topic = manifest.path("topics").path(kind).asText(); named(topic); topics.add(topic); }
        try (Admin admin = Admin.create(common()); ZooKeeper zk = zoo()) {
            Set<String> existing = admin.listTopics().names().get(15, TimeUnit.SECONDS);
            require(Collections.disjoint(existing, topics), "Fixture topics already exist; never reseed automatically");
            for (String kind : List.of("ordinary", "illegal")) {
                String group = manifest.path("groups").path(kind).asText(); named(group);
                require(zk.exists("/consumers/" + group, false) == null, "Fixture group already exists; never reset existing offsets");
            }
            admin.createTopics(topics.stream().map(t -> new NewTopic(t, 1, (short)1)).toList()).all().get(15, TimeUnit.SECONDS);
            for (String kind : List.of("ordinary", "illegal")) {
                String group = manifest.path("groups").path(kind).asText(), topic = manifest.path("topics").path(kind).asText();
                createPath(zk, "/consumers/" + group + "/offsets/" + topic + "/0", "0");
            }
        }
        Properties p = common(); p.put("key.serializer", StringSerializer.class.getName()); p.put("value.serializer", StringSerializer.class.getName());
        p.put("acks", "all"); p.put("compression.type", "none"); p.put("retries", "0");
        Map<String,Integer> counts = new LinkedHashMap<>();
        try (KafkaProducer<String,String> producer = new KafkaProducer<>(p)) {
            for (String kind : List.of("ordinary", "illegal")) {
                List<String> lines = Files.readAllLines(directory.resolve(kind + "-messages.ndjson"), StandardCharsets.UTF_8);
                int expected = kind.equals("ordinary") ? 10000 : 200;
                require(lines.size() == expected, "Original bounded consumer limit changed");
                List<Future<RecordMetadata>> futures = new ArrayList<>(); Set<String> keys = new HashSet<>();
                for (String line : lines) {
                    JsonNode record = JSON.readTree(line); String key = record.path("key").asText(), message = record.path("message").asText();
                    require(key.startsWith(kind + ":") && keys.add(key) && message.length() < 8192, "Invalid synthetic record");
                    require(JSON.readTree(message).path("_fixture").path("id").asText().equals(key), "Synthetic record identity mismatch");
                    futures.add(producer.send(new ProducerRecord<>(manifest.path("topics").path(kind).asText(), 0, key, message)));
                }
                producer.flush();
                for (int i = 0; i < futures.size(); i++) require(futures.get(i).get(15, TimeUnit.SECONDS).offset() == i, "Seed offsets differ from a new empty single-partition topic");
                counts.put(kind, futures.size());
            }
        }
        return Map.of("seeded", counts, "offsetInitialState", "dedicated-group-offset-zero", "audit", audit(manifest));
    }
    static Map<String,Object> capture(Path directory, JsonNode manifest, Path output) throws Exception {
        String topic = manifest.path("topics").path("egress").asText(); named(topic);
        Properties p = common(); p.put("key.deserializer", StringDeserializer.class.getName()); p.put("value.deserializer", StringDeserializer.class.getName()); p.put("enable.auto.commit", "false");
        List<Map<String,Object>> records = new ArrayList<>();
        long endOffset;
        try (KafkaConsumer<String,String> consumer = new KafkaConsumer<>(p)) {
            TopicPartition partition = new TopicPartition(topic, 0); consumer.assign(List.of(partition)); consumer.seekToBeginning(List.of(partition));
            endOffset = consumer.endOffsets(List.of(partition)).get(partition);
            require(endOffset <= 200, "Unexpected egress size in dedicated fixture topic");
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            while (records.size() < endOffset && System.nanoTime() < deadline)
                for (var record : consumer.poll(Duration.ofMillis(200))) {
                    Map<String,Object> row = new LinkedHashMap<>(); row.put("offset", record.offset()); row.put("key", record.key()); row.put("message", record.value()); records.add(row);
                }
        }
        require(records.size() == endOffset, "Egress capture incomplete");
        List<String> lines = new ArrayList<>(); for (var record : records) lines.add(JSON.writeValueAsString(record));
        Files.write(output, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        return Map.of("capturedRecords", records.size(), "endOffset", endOffset);
    }
    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args[1]); JsonNode manifest = JSON.readTree(directory.resolve("manifest.json").toFile());
        require(manifest.path("format").asText().equals("RYNEW_ORIGINAL_KETTLE_BUSINESS_FIXTURE_V1"), "Unexpected prepared fixture");
        Map<String,Object> health = health(); Object result;
        switch (args[0]) {
            case "prepare": result = prepare(directory, manifest); break;
            case "audit": result = audit(manifest); break;
            case "capture": result = capture(directory, manifest, Path.of(args[2])); break;
            default: throw new IllegalArgumentException("Unknown fixture action");
        }
        System.out.println(JSON.writeValueAsString(Map.of("health", health, "result", result)));
    }
}
