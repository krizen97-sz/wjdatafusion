package com.hm.manage.service.governance;

import com.hm.common.exception.ServiceException;
import com.hm.manage.service.governance.DataGovernanceKafkaModels.Binding;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import org.apache.kafka.common.Uuid;
import org.apache.kafka.common.message.*;
import org.apache.kafka.common.protocol.*;
import org.apache.kafka.common.record.*;
import org.apache.kafka.common.requests.*;
import org.apache.kafka.common.utils.ByteUtils;
import org.apache.kafka.common.utils.Crc32C;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DataGovernanceKafkaProtocolTest
{
    private final String topic = "synthetic-topic";
    private DataGovernanceKafkaProperties properties(String endpoint) {
        var p = new DataGovernanceKafkaProperties(); p.setEnabled(true); p.setAllowedBrokers(List.of(endpoint)); p.setTimeoutMillis(500); return p;
    }
    private Binding binding(String endpoint) { return new Binding(UUID.randomUUID().toString(), "synthetic", List.of(endpoint), topic, "rynew-governance-7-wire-test", "synthetic"); }
    private ApiVersionsResponse apiVersions() {
        var data = new ApiVersionsResponseData();
        for (ApiKeys api : List.of(ApiKeys.METADATA, ApiKeys.FETCH)) data.apiKeys().add(new ApiVersionsResponseData.ApiVersion().setApiKey(api.id).setMinVersion((short) 0).setMaxVersion(api == ApiKeys.FETCH ? (short) 12 : (short) 12));
        return new ApiVersionsResponse(data);
    }
    private MetadataResponse metadata(String endpoint, boolean forbiddenExtraNode, int partitions) {
        var data = new MetadataResponseData(); String[] parts = endpoint.split(":");
        data.brokers().add(new MetadataResponseData.MetadataResponseBroker().setNodeId(1).setHost(parts[0]).setPort(Integer.parseInt(parts[1])));
        if (forbiddenExtraNode) data.brokers().add(new MetadataResponseData.MetadataResponseBroker().setNodeId(2).setHost("192.0.2.123").setPort(9092));
        var t = new MetadataResponseData.MetadataResponseTopic().setName(topic).setTopicId(Uuid.randomUuid());
        for (int i = 0; i < partitions; i++) t.partitions().add(new MetadataResponseData.MetadataResponsePartition().setPartitionIndex(i).setLeaderId(1).setLeaderEpoch(0).setReplicaNodes(List.of(1)).setIsrNodes(List.of(1)));
        data.topics().add(t); return new MetadataResponse(data, (short) 12);
    }
    private byte[] response(RequestHeader request, AbstractResponse response, boolean wrongCorrelation) {
        short version = request.apiVersion(), headerVersion = request.apiKey().responseHeaderVersion(version);
        ByteBuffer header = MessageUtil.toByteBuffer(new ResponseHeaderData().setCorrelationId(request.correlationId() + (wrongCorrelation ? 1 : 0)), headerVersion);
        ByteBuffer body = MessageUtil.toByteBuffer(response.data(), version);
        return ByteBuffer.allocate(4 + header.remaining() + body.remaining()).putInt(header.remaining() + body.remaining()).put(header).put(body).array();
    }
    @Test void correlationOversizedTruncatedAndExpiredFramesAreRejected() throws Exception {
        try (FakeBroker broker = new FakeBroker()) {
            broker.answer = r -> response(r, apiVersions(), true);
            assertTrue(assertThrows(ServiceException.class, () -> new DataGovernanceKafkaProtocol(properties(broker.endpoint())).open(binding(broker.endpoint()))).getMessage().contains("correlation"));
        }
        for (byte[] invalid : List.of(ByteBuffer.allocate(4).putInt(DataGovernanceKafkaProtocol.MAX_FRAME + 1).array(), ByteBuffer.allocate(8).putInt(100).putInt(0).array())) {
            try (FakeBroker broker = new FakeBroker()) {
                broker.answer = r -> invalid;
                assertThrows(ServiceException.class, () -> new DataGovernanceKafkaProtocol(properties(broker.endpoint())).open(binding(broker.endpoint())));
            }
        }
        try (FakeBroker broker = new FakeBroker()) {
            broker.answer = r -> { try { Thread.sleep(1200); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); } return response(r, apiVersions(), false); };
            assertTimeout(Duration.ofSeconds(2), () -> assertThrows(ServiceException.class, () -> new DataGovernanceKafkaProtocol(properties(broker.endpoint())).open(binding(broker.endpoint()))));
        }
    }
    @Test void everyAdvertisedBrokerMustBeAllowedEvenWhenItIsNotThePartitionLeader() throws Exception {
        try (FakeBroker broker = new FakeBroker()) {
            broker.answer = r -> response(r, r.apiKey() == ApiKeys.API_VERSIONS ? apiVersions() : metadata(broker.endpoint(), true, 1), false);
            assertTrue(assertThrows(ServiceException.class, () -> new DataGovernanceKafkaProtocol(properties(broker.endpoint())).open(binding(broker.endpoint()))).getMessage().contains("允许列表"));
            assertEquals(2, broker.requests);
        }
    }
    @Test void onePartitionErrorRejectsTheWholeFetchInsteadOfReturningOtherPartitions() throws Exception {
        try (FakeBroker broker = new FakeBroker()) {
            broker.answer = r -> {
                AbstractResponse value;
                if (r.apiKey() == ApiKeys.API_VERSIONS) value = apiVersions();
                else if (r.apiKey() == ApiKeys.METADATA) value = metadata(broker.endpoint(), false, 2);
                else {
                    var data = new FetchResponseData(); var t = new FetchResponseData.FetchableTopicResponse().setTopic(topic);
                    t.partitions().add(new FetchResponseData.PartitionData().setPartitionIndex(0).setRecords(MemoryRecords.withRecords(CompressionType.NONE, new SimpleRecord("synthetic".getBytes()))));
                    t.partitions().add(new FetchResponseData.PartitionData().setPartitionIndex(1).setErrorCode(Errors.NOT_LEADER_OR_FOLLOWER.code())); data.responses().add(t); value = new FetchResponse(data);
                }
                return response(r, value, false);
            };
            try (var session = new DataGovernanceKafkaProtocol(properties(broker.endpoint())).open(binding(broker.endpoint()))) {
                assertThrows(ServiceException.class, () -> session.fetch(Map.of(0, 0L, 1, 0L), 100));
            }
        }
    }
    @Test void hostileCompactCountsAndLengthsAreRejectedBeforeGeneratedParserAllocations() {
        ByteBuffer huge = ByteBuffer.allocate(32); huge.putShort((short) 0); ByteUtils.writeUnsignedVarint(Integer.MAX_VALUE, huge); huge.flip();
        assertThrows(ServiceException.class, () -> DataGovernanceKafkaProtocol.parseResponse(ApiKeys.API_VERSIONS, new DataGovernanceKafkaProtocol.BoundedReadable(huge), (short) 3));
        var reader = new DataGovernanceKafkaProtocol.BoundedReadable(ByteBuffer.wrap(new byte[8]));
        assertThrows(ServiceException.class, () -> reader.readArray(Integer.MAX_VALUE)); assertThrows(ServiceException.class, () -> reader.readString(100)); assertThrows(ServiceException.class, () -> reader.readByteBuffer(100));
        ByteBuffer repeated = ByteBuffer.allocate(65536); for (int i = 0; i < 9000; i++) ByteUtils.writeUnsignedVarint(1024, repeated); repeated.position(0);
        var cumulative = new DataGovernanceKafkaProtocol.BoundedReadable(repeated);
        assertThrows(ServiceException.class, () -> { for (int i = 0; i < 9000; i++) cumulative.readUnsignedVarint(); });
    }
    @Test void crcMagicCompressionAndHostileRecordHeaderCountsCannotReachObjectDecoding() {
        MemoryRecords good = MemoryRecords.withRecords(CompressionType.NONE, new SimpleRecord("synthetic".getBytes()));
        assertDoesNotThrow(() -> DataGovernanceKafkaRecords.validate(good));
        byte[] corrupted = MessageUtil.byteBufferToArray(good.buffer().duplicate()); corrupted[corrupted.length - 2] ^= 1;
        assertThrows(Exception.class, () -> DataGovernanceKafkaRecords.validate(MemoryRecords.readableRecords(ByteBuffer.wrap(corrupted))));
        byte[] magic = MessageUtil.byteBufferToArray(good.buffer().duplicate()); magic[16] = 9;
        assertThrows(ServiceException.class, () -> DataGovernanceKafkaRecords.validate(MemoryRecords.readableRecords(ByteBuffer.wrap(magic))));
        assertThrows(ServiceException.class, () -> DataGovernanceKafkaRecords.validate(MemoryRecords.withRecords(CompressionType.GZIP, new SimpleRecord("synthetic".getBytes()))));
        assertThrows(ServiceException.class, () -> DataGovernanceKafkaRecords.validate(MemoryRecords.readableRecords(ByteBuffer.wrap(Arrays.copyOf(corrupted, corrupted.length - 1)))));
        // A tiny CRC-valid batch declares a huge header array. Validate count/remaining before Kafka allocates it.
        ByteBuffer recordBody = ByteBuffer.allocate(32); recordBody.put((byte) 0); ByteUtils.writeVarlong(0, recordBody); ByteUtils.writeVarint(0, recordBody); ByteUtils.writeVarint(-1, recordBody); ByteUtils.writeVarint(0, recordBody); ByteUtils.writeVarint(Integer.MAX_VALUE, recordBody); recordBody.flip();
        ByteBuffer hostile = ByteBuffer.allocate(128); hostile.put(MessageUtil.byteBufferToArray(good.buffer().duplicate()), 0, 61);
        ByteUtils.writeVarint(recordBody.remaining(), hostile); hostile.put(recordBody); int length = hostile.position(); hostile.putInt(8, length - 12); hostile.putInt(57, 1); hostile.limit(length); hostile.position(0); hostile.putInt(17, (int) Crc32C.compute(hostile, 21, length - 21));
        assertThrows(ServiceException.class, () -> DataGovernanceKafkaRecords.validate(MemoryRecords.readableRecords(hostile)));
    }
    private static final class FakeBroker implements AutoCloseable {
        final ServerSocket socket; final ExecutorService executor = Executors.newSingleThreadExecutor(); volatile Function<RequestHeader, byte[]> answer; volatile int requests;
        FakeBroker() throws Exception {
            socket = new ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"));
            executor.submit(() -> { while (!socket.isClosed()) try (Socket client = socket.accept()) {
                DataInputStream input = new DataInputStream(client.getInputStream()); int size = input.readInt(); if (size < 1 || size > 65536) throw new IOException();
                byte[] frame = input.readNBytes(size); requests++; RequestHeader request = RequestHeader.parse(ByteBuffer.wrap(frame));
                byte[] output = answer.apply(request); client.getOutputStream().write(output); client.getOutputStream().flush();
            } catch (Exception ignored) { if (socket.isClosed()) return; } });
        }
        String endpoint() { return "127.0.0.1:" + socket.getLocalPort(); }
        @Override public void close() throws Exception { socket.close(); executor.shutdownNow(); executor.awaitTermination(2, TimeUnit.SECONDS); }
    }
}
