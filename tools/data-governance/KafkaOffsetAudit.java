import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.Admin;

/** Read-only check of a dedicated local consumer group; no address is accepted from arguments. */
public class KafkaOffsetAudit {
    public static void main(String[] args) throws Exception {
        if (args.length != 2 || !args[0].matches("[A-Za-z0-9_-]{22}") || !args[1].matches("rynew-governance-[0-9]+-[A-Za-z0-9_-]{1,150}")) throw new IllegalArgumentException("Owned cluster/group required");
        try (Admin admin = Admin.create(Map.of("bootstrap.servers", "127.0.0.1:19092", "default.api.timeout.ms", "5000", "request.timeout.ms", "3000"))) {
            var cluster = admin.describeCluster();
            if (!args[0].equals(cluster.clusterId().get(5, TimeUnit.SECONDS))) throw new IllegalStateException("Cluster mismatch");
            for (var node : cluster.nodes().get(5, TimeUnit.SECONDS)) if (!node.host().equals("127.0.0.1") || node.port() != 19092) throw new IllegalStateException("Endpoint mismatch");
            Map<Integer, Long> offsets = new TreeMap<>();
            admin.listConsumerGroupOffsets(args[1]).partitionsToOffsetAndMetadata().get(5, TimeUnit.SECONDS).forEach((partition, offset) -> offsets.put(partition.partition(), offset.offset()));
            System.out.println("{" + String.join(",", offsets.entrySet().stream().map(e -> "\"" + e.getKey() + "\":\"" + e.getValue() + "\"").toList()) + "}");
        }
    }
}
