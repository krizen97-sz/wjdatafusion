package com.hm.manage.service.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hm.common.exception.ServiceException;
import com.hm.manage.service.governance.DataGovernanceKafkaModels.*;
import com.hm.manage.service.governance.DataGovernanceModels.StoredRun;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Durable bounded receipt -> server-associated execution -> explicit offset acknowledgement. */
@Service
public class DataGovernanceKafkaService
{
    static final int MAX_RECORDS = 100, MAX_INPUT_BYTES = 256 * 1024;
    private final DataGovernanceKafkaProperties properties;
    private final DataGovernanceKafkaStore store;
    private final DataGovernanceRunRepository runs;
    private final Supplier<DataGovernanceKafkaTransport> transport;
    private final Supplier<DataGovernanceScheduleDelivery> deliveries;
    private final Supplier<DataGovernanceArtifactStore> artifacts;
    private final ObjectMapper mapper = new ObjectMapper();
    private boolean initialized;
    @Autowired
    public DataGovernanceKafkaService(DataGovernanceKafkaProperties properties, DataGovernanceKafkaStore store,
        DataGovernanceRunRepository runs, ObjectProvider<DataGovernanceKafkaTransport> transport,
        ObjectProvider<DataGovernanceScheduleDelivery> deliveries, ObjectProvider<DataGovernanceArtifactStore> artifacts)
    { this(properties, store, runs, transport::getIfAvailable, deliveries::getIfAvailable, artifacts::getIfAvailable); }
    DataGovernanceKafkaService(DataGovernanceKafkaProperties properties, DataGovernanceKafkaStore store,
        DataGovernanceRunRepository runs, Supplier<DataGovernanceKafkaTransport> transport, Supplier<DataGovernanceScheduleDelivery> deliveries)
    { this(properties, store, runs, transport, deliveries, () -> null); }
    DataGovernanceKafkaService(DataGovernanceKafkaProperties properties, DataGovernanceKafkaStore store,
        DataGovernanceRunRepository runs, Supplier<DataGovernanceKafkaTransport> transport, Supplier<DataGovernanceScheduleDelivery> deliveries,
        Supplier<DataGovernanceArtifactStore> artifacts)
    { this.properties = properties; this.store = store; this.runs = runs; this.transport = transport; this.deliveries = deliveries; this.artifacts = artifacts; }

    private void initialize()
    {
        if (!properties.isEnabled()) throw new ServiceException("Kafka 有界批次功能未启用");
        if (initialized) return;
        store.receipts(receipt -> {
            if ("RECEIVING".equals(receipt.status)) {
                receipt.status = "FAILED"; receipt.error = "服务中断了取批；位点未提交。请显式释放本地租约后重试"; save(receipt);
            } else if ("COMMITTING".equals(receipt.status)) {
                receipt.status = "COMMIT_UNKNOWN"; receipt.error = "服务中断了确认请求；必须回读实际位点，不能盲目重写"; save(receipt);
            }
        });
        initialized = true;
    }
    public synchronized List<ProfileView> profiles(long owner)
    {
        if (!properties.isEnabled()) return List.of();
        initialize(); return store.profiles().stream().filter(profile -> profile.ownerId == owner).map(this::view).toList();
    }
    public synchronized ProfileView create(ProfileRequest request, long owner)
    {
        initialize(); if (store.profiles().stream().filter(p -> p.ownerId == owner).count() >= 100) throw new ServiceException("Kafka 配置数量超过上限");
        Profile profile = new Profile(); profile.id = UUID.randomUUID().toString(); profile.ownerId = owner; profile.revision = 1;
        apply(profile, request, owner); store.saveProfile(profile); return view(profile);
    }
    public synchronized ProfileView update(String id, ProfileRequest request, long owner)
    {
        initialize(); Profile old = ownedProfile(id, owner);
        if (request == null || request.revision() == null || request.revision() != old.revision) throw new ServiceException("Kafka 配置已变更，请刷新后重试", 409);
        Profile changed = mapper.convertValue(old, Profile.class); apply(changed, request, owner);
        if (!old.fingerprint.equals(changed.fingerprint) && store.leased(old.fingerprint)) throw new ServiceException("Kafka 配置仍有未完成批次，不能改变源或消费组");
        changed.revision++; store.saveProfile(changed); return view(changed);
    }
    private void apply(Profile profile, ProfileRequest request, long owner)
    {
        if (request == null || request.name() == null || request.name().isBlank() || request.name().length() > 80 || request.name().chars().anyMatch(Character::isISOControl))
            throw new ServiceException("Kafka 配置名称需要 1 至 80 个字符");
        if (request.bootstrapServers() == null || request.bootstrapServers().isEmpty() || request.bootstrapServers().size() > 8) throw new ServiceException("请指定服务端允许的 Kafka 地址");
        List<String> bootstrap = request.bootstrapServers().stream().map(DataGovernanceKafkaService::endpoint).distinct().sorted().toList();
        for (String address : bootstrap) allowed(address);
        if (request.topic() == null || !request.topic().matches("[A-Za-z0-9._-]{1,200}") || Set.of(".", "..").contains(request.topic()) || request.topic().startsWith("__"))
            throw new ServiceException("Kafka topic 无效，不能读取内部 topic");
        String prefix = "rynew-governance-" + owner + "-";
        if (request.groupId() == null || !request.groupId().startsWith(prefix) || !request.groupId().matches("[A-Za-z0-9._-]{1,200}") || request.groupId().length() <= prefix.length())
            throw new ServiceException("消费组必须使用当前用户专属前缀 " + prefix);
        String fingerprint = hash(json(List.of(bootstrap, request.topic(), request.groupId())));
        if (store.profiles().stream().anyMatch(p -> !p.id.equals(profile.id) && fingerprint.equals(p.fingerprint)))
            throw new ServiceException("该 Kafka 源和消费组已由另一配置占用");
        profile.name = request.name().trim(); profile.bootstrapServers = bootstrap; profile.topic = request.topic(); profile.groupId = request.groupId(); profile.fingerprint = fingerprint;
    }
    public synchronized Binding binding(String profileId, long owner)
    {
        initialize(); Profile profile = ownedProfile(profileId, owner);
        profile.bootstrapServers.forEach(this::allowed);
        return new Binding(profile.id, profile.fingerprint, List.copyOf(profile.bootstrapServers), profile.topic, profile.groupId, profile.fingerprint);
    }
    public synchronized ReceiptSummary receive(String profileId, long owner)
    {
        initialize(); Profile profile = ownedProfile(profileId, owner); Binding binding = binding(profileId, owner);
        Receipt receipt = new Receipt(); receipt.id = UUID.randomUUID().toString(); receipt.ownerId = owner; receipt.profileName = profile.name;
        receipt.binding = binding; receipt.status = "RECEIVING"; receipt.createdAt = receipt.updatedAt = Instant.now().toString(); store.claim(receipt);
        try (var session = open(binding))
        {
            session.ensureExclusiveGroup(); List<Integer> partitions = session.partitions();
            Map<Integer, Long> committed = session.committed(), beginning = session.beginningOffsets();
            validatePartitions(partitions, committed); validatePartitions(partitions, beginning);
            Map<Integer, Long> starts = new TreeMap<>();
            for (int partition : partitions) {
                Long initial = committed.get(partition), first = beginning.get(partition);
                if (first == null || first < 0 || initial != null && initial < first) throw new ServiceException("Kafka 位点已越过保留窗口，未自动重置");
                starts.put(partition, initial == null ? first : initial);
            }
            receipt.initialOffsets = strings(committed); receipt.startOffsets = strings(starts); save(receipt);
            List<Map<String, Object>> rows = new ArrayList<>(); Map<Integer, Long> previous = new HashMap<>();
            for (DataGovernanceKafkaTransport.Record record : session.fetch(starts, MAX_RECORDS))
            {
                if (rows.size() >= MAX_RECORDS) break; // Prefetch tails are never included in nextOffsets.
                if (!starts.containsKey(record.partition())) throw new ServiceException("Kafka 返回了未请求的分区");
                if (record.offset() < starts.get(record.partition())) continue;
                if (record.offset() < 0 || previous.containsKey(record.partition()) && record.offset() <= previous.get(record.partition())) throw new ServiceException("Kafka 分区位点未严格递增");
                if (record.value() == null || record.value().length > MAX_INPUT_BYTES) throw new ServiceException("Kafka 记录为空墓碑或超过大小限制，未确认位点");
                String text;
                try { text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(record.value())).toString(); }
                catch (Exception invalidUtf8) { throw new ServiceException("Kafka 记录不是合法 UTF-8，未确认位点"); }
                Map<String, Object> row = new LinkedHashMap<>(); row.put("k_message", text); row.put("kafka_topic", binding.topic()); row.put("kafka_partition", record.partition()); row.put("kafka_offset", Long.toString(record.offset()));
                rows.add(row);
                String input = json(rows); if (input.getBytes(StandardCharsets.UTF_8).length > MAX_INPUT_BYTES) throw new ServiceException("包装后的 Kafka 批次超过 256 KiB，整批未确认");
                previous.put(record.partition(), record.offset()); receipt.nextOffsets.put(Integer.toString(record.partition()), Long.toString(Math.addExact(record.offset(), 1)));
            }
            // Detect another writer/group change during receipt construction before publishing the batch.
            session.ensureExclusiveGroup(); if (!committed.equals(session.committed())) throw new ServiceException("取批期间消费组位点发生变化，未发布可执行批次");
            receipt.inputJson = json(rows); receipt.inputSha256 = hash(receipt.inputJson); receipt.recordCount = rows.size();
            receipt.status = rows.isEmpty() ? "EMPTY" : "RECEIVED"; save(receipt);
        }
        catch (Exception failure)
        {
            receipt.status = "FAILED"; receipt.inputJson = null; receipt.inputSha256 = null; receipt.recordCount = 0;
            receipt.error = safeError(failure, "Kafka 取批失败；未确认位点，请检查独立 broker 与记录格式"); save(receipt);
        }
        return summary(receipt);
    }
    /** Owner-checked detached persistence envelope for internal execution coordinators only. */
    synchronized Receipt snapshot(String id, long owner) { initialize(); return ownedReceipt(id, owner); }
    public synchronized ReceiptDetail read(String id, long owner) { initialize(); Receipt receipt = ownedReceipt(id, owner); return new ReceiptDetail(summary(receipt), receipt.inputJson); }
    public synchronized List<ReceiptSummary> receipts(long owner)
    {
        if (!properties.isEnabled()) return List.of();
        initialize(); List<ReceiptSummary> result = new ArrayList<>(); store.receipts(r -> { if (r.ownerId == owner) result.add(summary(r)); });
        result.sort(Comparator.comparing(ReceiptSummary::createdAt).reversed()); return result.stream().limit(200).toList();
    }
    /** Server-only association. No HTTP route accepts a client-supplied successful run or target. */
    synchronized void reserveExecution(String id, String runId, String inputSha, DataGovernanceScheduleDelivery.Binding delivery, long owner)
    {
        initialize(); DataGovernanceEngine.id(runId); Receipt receipt = ownedReceipt(id, owner);
        if (!receipt.leaseHeld || !Objects.equals(inputSha, receipt.inputSha256) || !Objects.equals(inputSha, hash(receipt.inputJson))) throw new ServiceException("源批次内容或租约已变化");
        if (receipt.executionIntentAt != null) {
            if (Objects.equals(runId, receipt.reservedRunId) && sameTarget(delivery, receipt.deliveryBinding)) return;
            throw new ServiceException("源批次已保留另一执行意图");
        }
        if (receipt.runId != null || !Set.of("RECEIVED", "EMPTY").contains(receipt.status)) throw new ServiceException("该批次不能保留执行意图");
        if (delivery != null && (deliveries.get() == null || !sameTarget(delivery, deliveries.get().target(delivery.id(), owner)))) throw new ServiceException("交付目标已改变");
        receipt.reservedRunId = runId; receipt.executionIntentAt = Instant.now().toString(); receipt.deliveryBinding = delivery;
        receipt.status = "EXECUTION_PLANNED"; save(receipt);
    }
    synchronized ReceiptSummary attachRun(String id, String runId, DataGovernanceScheduleDelivery.Binding delivery, long owner)
    {
        initialize(); Receipt receipt = ownedReceipt(id, owner); StoredRun run = ownedRun(runId, owner); checkInput(receipt, run);
        if (receipt.runId != null) {
            if (receipt.runId.equals(runId) && sameTarget(receipt.deliveryBinding, delivery)) return summary(receipt);
            throw new ServiceException("Kafka 批次已绑定执行，不能替换运行或交付目标");
        }
        if (!receipt.leaseHeld || !Set.of("RECEIVED", "EMPTY", "EXECUTION_PLANNED").contains(receipt.status)) throw new ServiceException("该 Kafka 批次不能绑定执行");
        if (receipt.executionIntentAt != null && (!Objects.equals(runId, receipt.reservedRunId) || !sameTarget(delivery, receipt.deliveryBinding))) throw new ServiceException("真实运行与保留的执行意图不一致");
        if (delivery != null) {
            DataGovernanceScheduleDelivery bridge = deliveries.get(); if (bridge == null) throw new ServiceException("FTP 交付桥未配置");
            if (!sameTarget(delivery, bridge.target(delivery.id(), owner))) throw new ServiceException("FTP 目标已变化");
        }
        receipt.runId = runId; receipt.deliveryBinding = delivery; receipt.status = "EXECUTION_ATTACHED"; save(receipt); return summary(receipt);
    }
    public synchronized ReceiptSummary commit(String id, long owner)
    {
        initialize(); Receipt receipt = ownedReceipt(id, owner);
        if ("COMMITTED".equals(receipt.status)) return summary(receipt);
        if (!receipt.leaseHeld || receipt.runId == null) throw new ServiceException("批次尚未绑定服务端执行，不能确认位点");
        try { gate(receipt, owner); }
        catch (ServiceException blocked) { receipt.error = blocked.getMessage(); save(receipt); throw blocked; }
        try (var session = open(receipt.binding))
        {
            session.ensureExclusiveGroup(); Map<Integer, Long> initial = longs(receipt.initialOffsets), desired = new TreeMap<>(initial), next = longs(receipt.nextOffsets);
            desired.putAll(next); validatePartitions(session.partitions(), initial);
            Map<Integer, Long> actual = session.committed(); Map<Integer, Long> pending = new TreeMap<>();
            for (int partition : initial.keySet()) {
                Long current = actual.get(partition), expected = desired.get(partition), before = initial.get(partition);
                if (Objects.equals(current, expected)) continue;
                if (!Objects.equals(current, before)) throw new ServiceException("OFFSET_CONFLICT: 实际位点偏离原位点与本批次目标，禁止回退或覆盖");
                if (next.containsKey(partition)) pending.put(partition, next.get(partition));
            }
            if (!actual.keySet().equals(initial.keySet())) throw new ServiceException("OFFSET_CONFLICT: 消费组分区集合已变化");
            if (!pending.isEmpty()) {
                receipt.status = "COMMITTING"; receipt.commitIntentAt = Instant.now().toString(); save(receipt);
                session.commit(pending);
            }
            if (!desired.equals(session.committed())) throw new ServiceException("提交后的 Kafka 实际位点未完整确认");
            receipt.status = "COMMITTED"; receipt.leaseHeld = false; receipt.error = null; save(receipt);
        }
        catch (Exception uncertain)
        {
            String message = safeError(uncertain, "Kafka 确认请求未明确完成；请重试确认以先回读实际位点");
            receipt.status = message.startsWith("OFFSET_CONFLICT") ? "OFFSET_CONFLICT" : receipt.commitIntentAt != null ? "COMMIT_UNKNOWN" : "COMMIT_BLOCKED";
            receipt.error = message; save(receipt);
        }
        return summary(receipt);
    }
    private void gate(Receipt receipt, long owner)
    {
        StoredRun stored = ownedRun(receipt.runId, owner); checkInput(receipt, stored); var run = stored.run;
        if (!run.cleanupConfirmed || run.status == null || !Set.of("SUCCEEDED", "EMPTY").contains(run.status)) throw new ServiceException("真实执行尚未成功并确认清理，不能确认 Kafka 位点");
        if ("EMPTY".equals(run.status)) {
            if (run.output == null || !run.output.isEmpty() || run.artifactCount != 0 || run.artifactsManifestAvailable) throw new ServiceException("EMPTY 执行仍包含输出或产物，不能跳过交付");
            receipt.deliveryStatus = "SKIPPED_EMPTY";
            return;
        }
        verifyArtifacts(stored, owner);
        if (receipt.deliveryBinding != null) {
            DataGovernanceScheduleDelivery bridge = deliveries.get(); if (bridge == null) throw new ServiceException("FTP 交付桥未配置");
            var delivery = bridge.find(run.id, receipt.deliveryBinding.id(), owner);
            if (delivery == null || !"DELIVERED".equals(delivery.status()) || !receipt.deliveryBinding.fingerprint().equals(delivery.targetFingerprint()))
                throw new ServiceException("绑定的 FTP 交付未确认完成或目标指纹不一致，不能确认 Kafka 位点");
            receipt.deliveryStatus = "DELIVERED";
        }
    }
    private void verifyArtifacts(StoredRun stored, long owner)
    {
        var run = stored.run; DataGovernanceArtifactStore files = artifacts.get();
        if (files == null || !run.artifactsManifestAvailable || run.artifactCount < 1) throw new ServiceException("成功执行缺少完整本地产物，不能确认 Kafka 位点");
        var manifest = files.manifest(run.id, owner);
        if (!run.id.equals(manifest.runId()) || owner != manifest.submitterId() || run.definitionHash == null
            || !run.definitionHash.equals(manifest.definitionHash()) || manifest.artifacts().size() != run.artifactCount)
            throw new ServiceException("完整产物清单与执行归属、定义或数量不一致");
        if (run.artifactCount > DataGovernanceArtifactStore.MAX_ARTIFACTS) throw new ServiceException("完整产物数量超出限制");
        long total = 0; byte[] buffer = new byte[8192];
        for (var artifact : manifest.artifacts()) {
            if (artifact.byteSize() < 0 || artifact.byteSize() > DataGovernanceArtifactStore.MAX_ARTIFACT_BYTES
                || artifact.byteSize() > DataGovernanceArtifactStore.MAX_TOTAL_BYTES - total) throw new ServiceException("完整产物大小超出限制");
            try (var input = files.read(run.id, artifact.id(), owner)) {
                long size = 0; MessageDigest digest = MessageDigest.getInstance("SHA-256"); int count;
                while ((count = input.read(buffer)) != -1) {
                    if (count <= 0 || count > artifact.byteSize() - size) throw new ServiceException("完整产物长度校验失败");
                    digest.update(buffer, 0, count); size += count;
                }
                if (size != artifact.byteSize() || !HexFormat.of().formatHex(digest.digest()).equals(artifact.sha256())) throw new ServiceException("完整产物摘要校验失败");
                total += size;
            } catch (ServiceException error) { throw error; }
            catch (Exception error) { throw new ServiceException("完整产物读取校验失败，不能确认 Kafka 位点"); }
        }
    }
    public synchronized ReceiptSummary release(String id, long owner)
    {
        initialize(); Receipt receipt = ownedReceipt(id, owner);
        if (receipt.runId != null || receipt.executionIntentAt != null || receipt.commitIntentAt != null || !Set.of("RECEIVED", "EMPTY", "FAILED").contains(receipt.status)) throw new ServiceException("已保留执行、关联运行或尝试确认的批次不能直接释放");
        receipt.leaseHeld = false; receipt.status = "RELEASED"; receipt.error = "已显式释放本地租约；未修改 Kafka 位点"; save(receipt); return summary(receipt);
    }
    private StoredRun ownedRun(String id, long owner)
    {
        StoredRun run = runs.find(id); if (run == null || run.ownerId != owner || run.run == null) throw new ServiceException("执行记录不存在或无权访问"); return run;
    }
    private void checkInput(Receipt receipt, StoredRun run)
    {
        if (receipt.inputJson == null || receipt.inputSha256 == null || run.inputJson == null
            || !receipt.inputSha256.equals(hash(receipt.inputJson)) || !receipt.inputSha256.equals(hash(run.inputJson))) throw new ServiceException("执行输入与 Kafka 原批次不一致，禁止关联或确认");
    }
    private static boolean sameTarget(DataGovernanceScheduleDelivery.Binding left, DataGovernanceScheduleDelivery.Binding right)
    { return left == null ? right == null : right != null && Objects.equals(left.id(), right.id()) && Objects.equals(left.fingerprint(), right.fingerprint()) && left.fingerprint() != null; }
    private DataGovernanceKafkaTransport.Session open(Binding binding)
    {
        binding.bootstrapServers().forEach(this::allowed); DataGovernanceKafkaTransport configured = transport.get();
        if (configured == null) throw new ServiceException("Kafka 有界协议传输尚未配置"); return configured.open(binding);
    }
    private Profile ownedProfile(String id, long owner) { Profile profile = store.profile(id); if (profile == null || profile.ownerId != owner) throw new ServiceException("Kafka 配置不存在或无权访问"); return profile; }
    private Receipt ownedReceipt(String id, long owner) { Receipt receipt = store.receipt(id); if (receipt == null || receipt.ownerId != owner) throw new ServiceException("Kafka 批次不存在或无权访问"); return receipt; }
    private void save(Receipt receipt) { receipt.updatedAt = Instant.now().toString(); store.saveReceipt(receipt); }
    private ProfileView view(Profile p) { return new ProfileView(p.id, p.revision, p.name, p.bootstrapServers, p.topic, p.groupId, p.fingerprint, false, "READ_UNCOMMITTED", "EARLIEST", List.of("PLAINTEXT IPv4 only", "Compression NONE only", "Transactional/control batches rejected", "Dedicated owner group", "No ZooKeeper group migration")); }
    private ReceiptSummary summary(Receipt r) { return new ReceiptSummary(r.id, r.binding.profileId(), r.profileName, r.binding.topic(), r.binding.groupId(), r.status, r.recordCount, r.inputSha256, r.initialOffsets, r.nextOffsets, r.runId, r.deliveryBinding == null ? null : r.deliveryBinding.id(), r.error, r.createdAt, r.updatedAt, r.leaseHeld, "READ_UNCOMMITTED", r.deliveryStatus); }
    private String json(Object value) { try { return mapper.writeValueAsString(value); } catch (Exception e) { throw new ServiceException("Kafka 批次编码失败"); } }
    static String hash(String text) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new ServiceException("Kafka 批次摘要计算失败"); } }
    static String endpoint(String raw)
    {
        if (raw == null) throw new ServiceException("Kafka 地址无效"); String value = raw.trim().toLowerCase(Locale.ROOT);
        if (!value.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}:[0-9]{1,5}")) throw new ServiceException("Kafka 初版地址必须为明确的 IPv4:端口");
        String[] parts = value.split(":"); String[] octets = parts[0].split("\\.");
        List<String> normalized = new ArrayList<>();
        for (String octet : octets) { int number = Integer.parseInt(octet); if (number > 255) throw new ServiceException("Kafka IPv4 地址无效"); normalized.add(Integer.toString(number)); }
        int port = Integer.parseInt(parts[1]); if (port < 1 || port > 65535) throw new ServiceException("Kafka 端口无效");
        return String.join(".", normalized) + ":" + port;
    }
    void allowed(String address)
    {
        if (properties.getAllowedBrokers() == null || properties.getAllowedBrokers().stream().map(DataGovernanceKafkaService::endpoint).noneMatch(endpoint(address)::equals)) throw new ServiceException("Kafka 地址不在服务端允许列表中");
    }
    static Map<String, String> strings(Map<Integer, Long> values) { Map<String, String> result = new LinkedHashMap<>(); new TreeMap<>(values).forEach((key, value) -> result.put(Integer.toString(key), value == null ? null : Long.toString(value))); return result; }
    static Map<Integer, Long> longs(Map<String, String> values) { Map<Integer, Long> result = new TreeMap<>(); values.forEach((key, value) -> result.put(Integer.parseInt(key), value == null ? null : Long.parseLong(value))); return result; }
    private static void validatePartitions(List<Integer> partitions, Map<Integer, Long> offsets)
    { if (partitions.isEmpty() || partitions.size() > 32 || !new HashSet<>(partitions).equals(offsets.keySet())) throw new ServiceException("Kafka 分区与位点集合不一致"); }
    private static String safeError(Exception error, String fallback) { return error instanceof ServiceException ? error.getMessage() : fallback; }
}
