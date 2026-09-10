package com.hm.manage.service.governance;

import com.hm.common.exception.ServiceException;
import java.nio.ByteBuffer;
import org.apache.kafka.common.record.CompressionType;
import org.apache.kafka.common.record.MemoryRecords;
import org.apache.kafka.common.record.RecordBatch;
import org.apache.kafka.common.utils.ByteUtils;

/** Validate v2 record lengths and header counts before Kafka's object iterator can allocate arrays. */
final class DataGovernanceKafkaRecords
{
    private static final int HEADER_BYTES = 61, MAX_HEADERS = 1024;
    private DataGovernanceKafkaRecords() { }
    static void validate(MemoryRecords records)
    {
        ByteBuffer bytes = records.buffer().duplicate();
        while (bytes.hasRemaining()) {
            if (bytes.remaining() < HEADER_BYTES) fail("Kafka record batch 被截断");
            int start = bytes.position(), following = bytes.getInt(start + 8);
            if (following < HEADER_BYTES - 12 || following > bytes.remaining() - 12) fail("Kafka record batch 长度无效");
            int length = following + 12;
            ByteBuffer batch = bytes.slice(); batch.limit(length); bytes.position(start + length);
            if (batch.get(16) != RecordBatch.MAGIC_VALUE_V2) fail("初版 Kafka 批次仅支持 magic v2 记录");
            short attributes = batch.getShort(21);
            if ((attributes & 7) != CompressionType.NONE.id) fail("初版 Kafka 批次不支持压缩记录；未确认位点");
            if ((attributes & 0x30) != 0) fail("初版 Kafka 批次不支持事务或控制记录；未确认位点");
            int count = batch.getInt(57); batch.position(HEADER_BYTES);
            if (count < 0 || count > batch.remaining()) fail("Kafka record 数量超过剩余 batch");
            // CRC verification does not construct individual records or decompress payloads.
            var batches = MemoryRecords.readableRecords(batchWithHeader(records, start, length)).batches().iterator();
            if (!batches.hasNext()) fail("Kafka record batch 不完整"); batches.next().ensureValid();
            for (int i = 0; i < count; i++) {
                int recordSize = ByteUtils.readVarint(batch);
                if (recordSize < 0 || recordSize > batch.remaining()) fail("Kafka record 长度超过剩余 batch");
                ByteBuffer record = batch.slice(); record.limit(recordSize); batch.position(batch.position() + recordSize);
                if (!record.hasRemaining()) fail("Kafka record 为空"); record.get();
                ByteUtils.readVarlong(record); ByteUtils.readVarint(record);
                skipBytes(record, ByteUtils.readVarint(record), true, DataGovernanceKafkaService.MAX_INPUT_BYTES);
                skipBytes(record, ByteUtils.readVarint(record), true, DataGovernanceKafkaService.MAX_INPUT_BYTES);
                int headers = ByteUtils.readVarint(record);
                if (headers < 0 || headers > MAX_HEADERS || headers > record.remaining()) fail("Kafka record header 数量超过预算");
                for (int h = 0; h < headers; h++) {
                    skipBytes(record, ByteUtils.readVarint(record), false, DataGovernanceKafkaService.MAX_INPUT_BYTES);
                    skipBytes(record, ByteUtils.readVarint(record), true, DataGovernanceKafkaService.MAX_INPUT_BYTES);
                }
                if (record.hasRemaining()) fail("Kafka record 存在多余字节");
            }
            if (batch.hasRemaining()) fail("Kafka record 数量与 batch 内容不一致");
        }
    }
    private static ByteBuffer batchWithHeader(MemoryRecords records, int start, int length)
    {
        ByteBuffer copy = records.buffer().duplicate(); copy.position(start); copy.limit(start + length); return copy.slice();
    }
    private static void skipBytes(ByteBuffer record, int size, boolean nullable, int limit)
    {
        if (nullable && size == -1) return;
        if (size < 0 || size > limit || size > record.remaining()) fail("Kafka record 字段长度超过预算");
        record.position(record.position() + size);
    }
    private static void fail(String message) { throw new ServiceException(message); }
}
