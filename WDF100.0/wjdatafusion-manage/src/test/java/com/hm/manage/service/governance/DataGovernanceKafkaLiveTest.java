package com.hm.manage.service.governance;

import com.hm.manage.service.governance.DataGovernanceKafkaModels.*;
import com.hm.manage.service.governance.DataGovernanceModels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "governance.kafka.smoke", matches = "true")
class DataGovernanceKafkaLiveTest
{
    @TempDir Path root;
    @Test void realKafkaReceiptDoesNotCommitUntilServerGateAndNeverCommitsPrefetchedOrRejectedRecords() throws Exception
    {
        String marker = UUID.randomUUID().toString().replace("-", ""), topic = "rynew-kafka-batch-test-" + marker, group = "rynew-governance-7-test-" + marker;
        Map<String, Object> config = Map.of("bootstrap.servers", "127.0.0.1:19092", "request.timeout.ms", "5000", "default.api.timeout.ms", "10000");
        var props = new DataGovernanceKafkaProperties(); props.setEnabled(true); props.setStorageDir(root.resolve("kafka").toString());
        var store = new DataGovernanceKafkaStore(props); var runProps = new DataGovernanceProperties(); runProps.setStorageDir(root.resolve("runs").toString());
        var runs = new DataGovernanceFileRunRepository(runProps); var wire = new DataGovernanceKafkaProtocol(props);
        var artifacts = new DataGovernanceArtifactStore(runProps);
        var service = new DataGovernanceKafkaService(props, store, runs, () -> wire, () -> null, () -> artifacts);
        try (Admin admin = Admin.create(config))
        {
            for (var node : admin.describeCluster().nodes().get(10, TimeUnit.SECONDS)) assertEquals("127.0.0.1:19092", node.host() + ":" + node.port());
            admin.createTopics(List.of(new NewTopic(topic, 2, (short) 1))).all().get(10, TimeUnit.SECONDS);
            Properties producerConfig = new Properties(); producerConfig.put("bootstrap.servers", "127.0.0.1:19092"); producerConfig.put("acks", "all"); producerConfig.put("compression.type", "none");
            try (var producer = new KafkaProducer<byte[], byte[]>(producerConfig, new ByteArraySerializer(), new ByteArraySerializer()))
            {
                for (int i = 0; i < 120; i++) producer.send(new ProducerRecord<>(topic, 0, null, ("{\"synthetic\":" + i + "}").getBytes(StandardCharsets.UTF_8))).get(10, TimeUnit.SECONDS);
                for (int i = 0; i < 5; i++) producer.send(new ProducerRecord<>(topic, 1, null, "{\"partition\":1}".getBytes(StandardCharsets.UTF_8))).get(10, TimeUnit.SECONDS);
                ProfileView profile = service.create(new ProfileRequest("独立协议验收", List.of("127.0.0.1:19092"), topic, group, null), 7);
                ReceiptSummary receipt = service.receive(profile.id(), 7);
                assertEquals("RECEIVED", receipt.status(), receipt.error()); assertEquals(100, receipt.recordCount()); assertEquals(Map.of("0", "100"), receipt.nextOffsets());
                assertTrue(admin.listConsumerGroupOffsets(group).partitionsToOffsetAndMetadata().get(10, TimeUnit.SECONDS).isEmpty(), "Receive must not auto-commit");
                assertThrows(Exception.class, () -> service.commit(receipt.id(), 7));
                StoredRun localFixture = new StoredRun(); localFixture.ownerId = 7; localFixture.inputJson = service.read(receipt.id(), 7).inputJson(); localFixture.run = new TestRun();
                localFixture.run.id = UUID.randomUUID().toString(); localFixture.run.status = "SUCCEEDED"; localFixture.run.createdAt = localFixture.run.updatedAt = java.time.Instant.now().toString();
                runs.save(localFixture); service.attachRun(receipt.id(), localFixture.run.id, null, 7);
                assertThrows(Exception.class, () -> service.commit(receipt.id(), 7));
                localFixture.run.cleanupConfirmed = true; localFixture.run.definitionHash = "a".repeat(64); runs.save(localFixture);
                assertThrows(Exception.class, () -> service.commit(receipt.id(), 7), "Missing complete artifact must block acknowledgement");
                var stage = artifacts.begin(localFixture); artifacts.capture(stage, "synthetic.txt", "text/plain", 3, out -> out.write(new byte[]{1, 2, 3})); artifacts.publish(stage, localFixture); runs.save(localFixture);
                ReceiptSummary committed = service.commit(receipt.id(), 7); assertEquals("COMMITTED", committed.status(), committed.error()); assertFalse(committed.leaseHeld());
                var offsets = admin.listConsumerGroupOffsets(group).partitionsToOffsetAndMetadata().get(10, TimeUnit.SECONDS);
                assertEquals(100, offsets.get(new TopicPartition(topic, 0)).offset()); assertFalse(offsets.containsKey(new TopicPartition(topic, 1)));
                ReceiptSummary next = service.receive(profile.id(), 7); assertEquals("RECEIVED", next.status(), next.error()); assertEquals(25, next.recordCount());
                assertEquals(Map.of("0", "120", "1", "5"), next.nextOffsets()); service.release(next.id(), 7);
                producer.send(new ProducerRecord<>(topic, 0, null, new byte[]{(byte) 0xff})).get(10, TimeUnit.SECONDS);
                ReceiptSummary rejected = service.receive(profile.id(), 7); assertEquals("FAILED", rejected.status());
                var after = admin.listConsumerGroupOffsets(group).partitionsToOffsetAndMetadata().get(10, TimeUnit.SECONDS);
                assertEquals(100, after.get(new TopicPartition(topic, 0)).offset()); assertFalse(after.containsKey(new TopicPartition(topic, 1))); service.release(rejected.id(), 7);
                System.out.println("Kafka protocol live: owned two-partition topic; 100-record receipt; no auto-commit; cleanup gate; committed only 0:100; unread 25 replayed; invalid UTF-8 batch did not advance offsets. Run evidence is an explicit local fixture.");
            }
            finally { admin.deleteTopics(List.of(topic)).all().get(10, TimeUnit.SECONDS); admin.deleteConsumerGroups(List.of(group)).all().get(10, TimeUnit.SECONDS); }
        }
        finally { store.close(); runs.close(); }
    }
}
