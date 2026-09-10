package com.hm.manage.service.governance;

import com.hm.common.exception.ServiceException;
import com.hm.manage.service.governance.DataGovernanceKafkaModels.Binding;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.common.IsolationLevel;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.Uuid;
import org.apache.kafka.common.message.*;
import org.apache.kafka.common.protocol.ApiKeys;
import org.apache.kafka.common.protocol.Errors;
import org.apache.kafka.common.protocol.ByteBufferAccessor;
import org.apache.kafka.common.record.CompressionType;
import org.apache.kafka.common.requests.*;
import org.springframework.stereotype.Component;

/** Kafka 3.6 request/response schemas over bounded, explicitly allowed IPv4 sockets. No background client. */
@Component
public class DataGovernanceKafkaProtocol implements DataGovernanceKafkaTransport
{
    static final int MAX_FRAME = 2 * 1024 * 1024, MAX_SESSION_BYTES = 8 * 1024 * 1024;
    private final DataGovernanceKafkaProperties properties;
    public DataGovernanceKafkaProtocol(DataGovernanceKafkaProperties properties) { this.properties = properties; }
    @Override public Session open(Binding binding)
    {
        if (!properties.isEnabled()) throw new ServiceException("Kafka 功能未启用");
        return new WireSession(binding);
    }
    private final class WireSession implements Session
    {
        private final Binding binding;
        private final Set<String> allowed;
        private final String bootstrap;
        private final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        private final Map<Integer, String> leaders = new TreeMap<>();
        private final Map<Integer, Integer> epochs = new HashMap<>();
        private ApiVersionsResponse versions;
        private String coordinator;
        private int correlation, bytes;
        private boolean closed;
        WireSession(Binding binding)
        {
            this.binding = binding;
            allowed = new HashSet<>();
            if (properties.getAllowedBrokers() != null) properties.getAllowedBrokers().forEach(value -> allowed.add(DataGovernanceKafkaService.endpoint(value)));
            if (binding.bootstrapServers().isEmpty()) throw new ServiceException("Kafka bootstrap 地址不能为空");
            binding.bootstrapServers().forEach(this::checkAddress); bootstrap = binding.bootstrapServers().get(0);
            versions = (ApiVersionsResponse) exchange(bootstrap, new ApiVersionsRequest.Builder().build((short) 3));
            error(versions.data().errorCode());
            MetadataResponse metadata = request(bootstrap, new MetadataRequest.Builder(List.of(binding.topic()), false));
            allErrors(metadata);
            Map<Integer, String> nodes = new HashMap<>();
            for (var broker : metadata.data().brokers()) {
                String address = checkAddress(broker.host() + ":" + broker.port());
                if (nodes.put(broker.nodeId(), address) != null) throw new ServiceException("Kafka 返回重复 broker 标识");
            }
            if (nodes.isEmpty() || nodes.size() > 32) throw new ServiceException("Kafka broker 数量无效");
            boolean found = false;
            for (var topic : metadata.data().topics()) {
                if (!binding.topic().equals(topic.name()) || found) throw new ServiceException("Kafka 返回了未请求的 topic");
                found = true;
                for (var partition : topic.partitions()) {
                    String leader = nodes.get(partition.leaderId());
                    if (leader == null || partition.partitionIndex() < 0 || leaders.put(partition.partitionIndex(), leader) != null) throw new ServiceException("Kafka 分区 leader 无效");
                    for (int replica : partition.replicaNodes()) if (!nodes.containsKey(replica)) throw new ServiceException("Kafka 副本地址未确认");
                    for (int replica : partition.isrNodes()) if (!nodes.containsKey(replica)) throw new ServiceException("Kafka ISR 地址未确认");
                    epochs.put(partition.partitionIndex(), partition.leaderEpoch());
                }
            }
            if (!found || leaders.isEmpty() || leaders.size() > 32) throw new ServiceException("Kafka 分区数量无效或超过 32");
        }
        @Override public List<Integer> partitions() { return List.copyOf(leaders.keySet()); }
        private String coordinator()
        {
            if (coordinator != null) return coordinator;
            FindCoordinatorResponse response = request(bootstrap, new FindCoordinatorRequest.Builder(new FindCoordinatorRequestData().setKey(binding.groupId()).setKeyType((byte) 0)), (short) 3);
            allErrors(response); Node node = response.node(); coordinator = checkAddress(node.host() + ":" + node.port()); return coordinator;
        }
        @Override public void ensureExclusiveGroup()
        {
            DescribeGroupsResponse response = request(coordinator(), new DescribeGroupsRequest.Builder(new DescribeGroupsRequestData().setGroups(List.of(binding.groupId()))));
            if (response.data().groups().size() != 1) throw new ServiceException("Kafka 未返回唯一消费组状态");
            var group = response.data().groups().get(0);
            if (!binding.groupId().equals(group.groupId())) throw new ServiceException("Kafka 返回了其他消费组");
            if (Errors.forCode(group.errorCode()) != Errors.GROUP_ID_NOT_FOUND) error(group.errorCode());
            if (!group.members().isEmpty() || !(group.groupState().equals("Empty") || group.groupState().equals("Dead"))) throw new ServiceException("Kafka 专属消费组存在其他消费者或状态未确认");
        }
        private List<TopicPartition> requestedPartitions() { return leaders.keySet().stream().map(p -> new TopicPartition(binding.topic(), p)).toList(); }
        @Override public Map<Integer, Long> committed()
        {
            OffsetFetchResponse response = request(coordinator(), new OffsetFetchRequest.Builder(binding.groupId(), true, requestedPartitions(), true));
            allErrors(response); Map<Integer, Long> result = new TreeMap<>();
            for (var entry : response.partitionDataMap(binding.groupId()).entrySet()) {
                checkPartition(entry.getKey()); error(entry.getValue().error.code()); long offset = entry.getValue().offset;
                if (offset < -1) throw new ServiceException("Kafka committed 位点无效"); result.put(entry.getKey().partition(), offset == -1 ? null : offset);
            }
            requireComplete(result.keySet()); return result;
        }
        @Override public Map<Integer, Long> beginningOffsets()
        {
            Map<Integer, Long> result = new TreeMap<>();
            for (var leader : byLeader().entrySet()) {
                var partitions = leader.getValue().stream().map(p -> new ListOffsetsRequestData.ListOffsetsPartition().setPartitionIndex(p).setTimestamp(-2).setCurrentLeaderEpoch(epochs.get(p))).toList();
                var topic = new ListOffsetsRequestData.ListOffsetsTopic().setName(binding.topic()).setPartitions(partitions);
                ListOffsetsResponse response = request(leader.getKey(), ListOffsetsRequest.Builder.forConsumer(true, IsolationLevel.READ_UNCOMMITTED, false).setTargetTimes(List.of(topic)));
                allErrors(response);
                for (var returnedTopic : response.topics()) {
                    if (!binding.topic().equals(returnedTopic.name())) throw new ServiceException("Kafka 返回了其他 topic 位点");
                    for (var partition : returnedTopic.partitions()) {
                        if (!leader.getValue().contains(partition.partitionIndex()) || partition.offset() < 0 || result.put(partition.partitionIndex(), partition.offset()) != null) throw new ServiceException("Kafka 起始位点响应无效");
                    }
                }
            }
            requireComplete(result.keySet()); return result;
        }
        @Override public List<Record> fetch(Map<Integer, Long> starts, int maximum)
        {
            if (maximum < 1 || maximum > 100) throw new ServiceException("Kafka 批次数量无效"); requireComplete(starts.keySet());
            Map<TopicPartition, FetchResponseData.PartitionData> partitions = new TreeMap<>(Comparator.comparingInt(TopicPartition::partition));
            // Check every response/error before exposing any record from this requested fetch set.
            for (var leader : byLeader().entrySet()) {
                Map<TopicPartition, FetchRequest.PartitionData> request = new LinkedHashMap<>();
                for (int p : leader.getValue()) request.put(new TopicPartition(binding.topic(), p), new FetchRequest.PartitionData(Uuid.ZERO_UUID, starts.get(p), -1, 256 * 1024, Optional.of(epochs.get(p))));
                FetchResponse response = request(leader.getKey(), FetchRequest.Builder.forConsumer((short) 12, 250, 1, request).setMaxBytes(512 * 1024).isolationLevel(IsolationLevel.READ_UNCOMMITTED).metadata(FetchMetadata.LEGACY), (short) 12);
                allErrors(response);
                var returned = response.responseData(Map.of(), (short) 12);
                if (!returned.keySet().equals(request.keySet())) throw new ServiceException("Kafka Fetch 分区响应不完整");
                partitions.putAll(returned);
            }
            List<Record> result = new ArrayList<>();
            for (var entry : partitions.entrySet()) {
                checkPartition(entry.getKey()); int partition = entry.getKey().partition(); long previous = -1;
                var records = FetchResponse.recordsOrFail(entry.getValue());
                if (!(records instanceof org.apache.kafka.common.record.MemoryRecords memory)) throw new ServiceException("Kafka records 类型无效");
                DataGovernanceKafkaRecords.validate(memory);
                for (var batch : records.batches()) {
                    if (result.size() >= maximum) return result;
                    batch.ensureValid();
                    if (batch.compressionType() != CompressionType.NONE) throw new ServiceException("初版 Kafka 批次不支持压缩记录；未确认位点");
                    if (batch.isTransactional() || batch.isControlBatch()) throw new ServiceException("初版 Kafka 批次不支持事务或控制记录；未确认位点");
                    for (var record : batch) {
                        checkDeadline(); record.ensureValid(); if (record.offset() < starts.get(partition)) continue;
                        if (result.size() >= maximum) return result;
                        if (record.offset() <= previous) throw new ServiceException("Kafka Fetch 位点未严格递增"); previous = record.offset();
                        if (record.valueSize() < 0 || record.valueSize() > DataGovernanceKafkaService.MAX_INPUT_BYTES) throw new ServiceException("Kafka 记录为空墓碑或超过 256 KiB");
                        byte[] value = new byte[record.valueSize()]; record.value().duplicate().get(value);
                        result.add(new Record(partition, record.offset(), value));
                    }
                }
            }
            return result;
        }
        @Override public void commit(Map<Integer, Long> nextOffsets)
        {
            if (nextOffsets.isEmpty()) throw new ServiceException("Kafka 确认集合不能为空");
            List<OffsetCommitRequestData.OffsetCommitRequestPartition> partitions = new ArrayList<>();
            for (var entry : nextOffsets.entrySet()) {
                if (!leaders.containsKey(entry.getKey()) || entry.getValue() == null || entry.getValue() < 0) throw new ServiceException("Kafka 确认位点无效");
                partitions.add(new OffsetCommitRequestData.OffsetCommitRequestPartition().setPartitionIndex(entry.getKey()).setCommittedOffset(entry.getValue()).setCommittedLeaderEpoch(epochs.get(entry.getKey())).setCommittedMetadata("rynew-governance-batch"));
            }
            var topic = new OffsetCommitRequestData.OffsetCommitRequestTopic().setName(binding.topic()).setPartitions(partitions);
            var data = new OffsetCommitRequestData().setGroupId(binding.groupId()).setGenerationIdOrMemberEpoch(-1).setMemberId("").setTopics(List.of(topic));
            OffsetCommitResponse response = request(coordinator(), new OffsetCommitRequest.Builder(data)); allErrors(response);
            Set<Integer> confirmed = new HashSet<>();
            for (var returnedTopic : response.data().topics()) {
                if (!binding.topic().equals(returnedTopic.name())) throw new ServiceException("Kafka 确认返回了其他 topic");
                for (var partition : returnedTopic.partitions()) if (!confirmed.add(partition.partitionIndex())) throw new ServiceException("Kafka 确认返回重复分区");
            }
            if (!confirmed.equals(nextOffsets.keySet())) throw new ServiceException("Kafka 确认响应分区不完整");
        }
        private Map<String, List<Integer>> byLeader()
        {
            Map<String, List<Integer>> result = new LinkedHashMap<>(); leaders.forEach((partition, address) -> result.computeIfAbsent(address, ignored -> new ArrayList<>()).add(partition)); return result;
        }
        private void checkPartition(TopicPartition partition) { if (!binding.topic().equals(partition.topic()) || !leaders.containsKey(partition.partition())) throw new ServiceException("Kafka 返回了未请求的分区"); }
        private void requireComplete(Set<Integer> partitions) { if (!leaders.keySet().equals(partitions)) throw new ServiceException("Kafka 分区集合不完整"); }
        private String checkAddress(String raw)
        {
            String address = DataGovernanceKafkaService.endpoint(raw);
            if (!allowed.contains(address)) throw new ServiceException("Kafka metadata 地址不在服务端允许列表中"); return address;
        }
        private <T extends AbstractResponse> T request(String address, AbstractRequest.Builder<?> builder) { return request(address, builder, builder.latestAllowedVersion()); }
        @SuppressWarnings("unchecked") private <T extends AbstractResponse> T request(String address, AbstractRequest.Builder<?> builder, short maximum)
        {
            var supported = versions.apiVersion(builder.apiKey().id);
            if (supported == null) throw new ServiceException("Kafka broker 缺少所需协议能力");
            short version = (short) Math.min(maximum, Math.min(builder.latestAllowedVersion(), supported.maxVersion()));
            short minimum = switch (builder.apiKey()) {
                case METADATA -> 9; case FIND_COORDINATOR -> 3; case DESCRIBE_GROUPS -> 5; case OFFSET_FETCH -> 7;
                case LIST_OFFSETS -> 6; case FETCH -> 12; case OFFSET_COMMIT -> 8;
                default -> throw new ServiceException("Kafka 协议能力不在批次白名单中");
            };
            if (version < supported.minVersion() || version < builder.oldestAllowedVersion() || version < minimum) throw new ServiceException("Kafka broker 缺少初版要求的现代 flexible 协议能力");
            return (T) exchange(address, builder.build(version));
        }
        private AbstractResponse exchange(String raw, AbstractRequest request)
        {
            checkDeadline(); String address = checkAddress(raw); long requestDeadline = Math.min(deadline, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(properties.getTimeoutMillis()));
            RequestHeader header = new RequestHeader(request.apiKey(), request.version(), "rynew-governance-bounded", ++correlation);
            ByteBuffer payload = request.serializeWithHeader(header);
            if (payload.remaining() > 65536) throw new ServiceException("Kafka 请求帧超过限制");
            ByteBuffer frame = ByteBuffer.allocate(4 + payload.remaining()).putInt(payload.remaining()).put(payload); frame.flip(); budget(frame.remaining());
            String[] hostPort = address.split(":"); String[] octets = hostPort[0].split("\\."); byte[] ip = new byte[4]; for (int i = 0; i < 4; i++) ip[i] = (byte) Integer.parseInt(octets[i]);
            try (SocketChannel socket = SocketChannel.open(); Selector selector = Selector.open())
            {
                socket.configureBlocking(false); socket.connect(new InetSocketAddress(InetAddress.getByAddress(ip), Integer.parseInt(hostPort[1])));
                while (!socket.finishConnect()) await(socket, selector, SelectionKey.OP_CONNECT, requestDeadline);
                while (frame.hasRemaining()) { checkDeadline(requestDeadline); if (socket.write(frame) == 0) await(socket, selector, SelectionKey.OP_WRITE, requestDeadline); }
                ByteBuffer length = ByteBuffer.allocate(4); read(socket, selector, length, requestDeadline); length.flip(); int size = length.getInt();
                if (size < 4 || size > MAX_FRAME) throw new ServiceException("Kafka 响应帧长度超过限制"); budget(size + 4);
                ByteBuffer response = ByteBuffer.allocate(size); read(socket, selector, response, requestDeadline); response.flip();
                BoundedReadable reader = new BoundedReadable(response);
                ResponseHeaderData returned = new ResponseHeaderData(reader, request.apiKey().responseHeaderVersion(request.version()));
                if (returned.correlationId() != header.correlationId()) throw new ServiceException("Kafka 响应 correlation 不匹配");
                AbstractResponse parsed = parseResponse(request.apiKey(), reader, request.version());
                if (response.hasRemaining()) throw new ServiceException("Kafka 响应存在未消费字节"); return parsed;
            }
            catch (ServiceException e) { throw e; }
            catch (Exception e) { throw new ServiceException("Kafka 协议请求未能明确完成"); }
        }
        private void budget(int amount) { if ((long) bytes + amount > MAX_SESSION_BYTES) throw new ServiceException("Kafka 会话总字节超过限制"); bytes += amount; }
        private void read(SocketChannel socket, Selector selector, ByteBuffer value, long end) throws IOException
        {
            while (value.hasRemaining()) { checkDeadline(end); int count = socket.read(value); if (count < 0) throw new IOException("Truncated Kafka response"); if (count == 0) await(socket, selector, SelectionKey.OP_READ, end); }
        }
        private void await(SocketChannel socket, Selector selector, int interest, long end) throws IOException
        {
            checkDeadline(end); socket.register(selector, interest); long remaining = Math.max(1, TimeUnit.NANOSECONDS.toMillis(end - System.nanoTime())); selector.select(remaining); selector.selectedKeys().clear(); checkDeadline(end);
        }
        private void checkDeadline() { checkDeadline(deadline); }
        private void checkDeadline(long end) { if (closed || System.nanoTime() >= end || Thread.currentThread().isInterrupted()) throw new ServiceException("Kafka 请求超过绝对时限或已取消"); }
        @Override public void close() { closed = true; }
    }
    /** All selected schemas use compact lengths, so hostile array/string counts are checked before allocation. */
    static final class BoundedReadable extends ByteBufferAccessor
    {
        private long allocationUnits;
        BoundedReadable(ByteBuffer buffer) { super(buffer); }
        @Override public int readUnsignedVarint() {
            int value = super.readUnsignedVarint();
            if (value < 0 || value > (long) remaining() + 1) throw new ServiceException("Kafka compact 长度或计数超过剩余帧");
            allocationUnits += value;
            if (allocationUnits > 4L * MAX_FRAME) throw new ServiceException("Kafka 解析分配预算超过限制");
            return value;
        }
        @Override public byte[] readArray(int length) {
            if (length < 0 || length > remaining()) throw new ServiceException("Kafka 字节数组长度超过剩余帧");
            return super.readArray(length);
        }
        @Override public ByteBuffer readByteBuffer(int length) {
            if (length < 0 || length > remaining()) throw new ServiceException("Kafka 记录长度超过剩余帧");
            return super.readByteBuffer(length);
        }
        @Override public String readString(int length) {
            if (length < 0 || length > remaining()) throw new ServiceException("Kafka 字符串长度超过剩余帧");
            return super.readString(length);
        }
    }
    static AbstractResponse parseResponse(ApiKeys api, BoundedReadable reader, short version)
    {
        return switch (api) {
            case API_VERSIONS -> new ApiVersionsResponse(new ApiVersionsResponseData(reader, version));
            case METADATA -> new MetadataResponse(new MetadataResponseData(reader, version), version);
            case FIND_COORDINATOR -> new FindCoordinatorResponse(new FindCoordinatorResponseData(reader, version));
            case DESCRIBE_GROUPS -> new DescribeGroupsResponse(new DescribeGroupsResponseData(reader, version));
            case OFFSET_FETCH -> new OffsetFetchResponse(new OffsetFetchResponseData(reader, version), version);
            case LIST_OFFSETS -> new ListOffsetsResponse(new ListOffsetsResponseData(reader, version));
            case FETCH -> new FetchResponse(new FetchResponseData(reader, version));
            case OFFSET_COMMIT -> new OffsetCommitResponse(new OffsetCommitResponseData(reader, version));
            default -> throw new ServiceException("Kafka 响应类型不在白名单中");
        };
    }
    private static void error(short code) { if (code != Errors.NONE.code()) throw new ServiceException("Kafka broker 错误：" + Errors.forCode(code).name()); }
    private static void allErrors(AbstractResponse response) { for (var entry : response.errorCounts().entrySet()) if (entry.getKey() != Errors.NONE && entry.getValue() > 0) error(entry.getKey().code()); }
}
