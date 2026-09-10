package com.hm.manage.service.governance;

import java.util.List;
import java.util.Map;
import com.hm.manage.service.governance.DataGovernanceKafkaModels.Binding;

/** Testable protocol boundary. It has no auto-commit and never exposes a prefetch consumer position. */
public interface DataGovernanceKafkaTransport
{
    record Record(int partition, long offset, byte[] value) { }
    interface Session extends AutoCloseable
    {
        List<Integer> partitions();
        void ensureExclusiveGroup();
        Map<Integer, Long> committed();
        Map<Integer, Long> beginningOffsets();
        List<Record> fetch(Map<Integer, Long> starts, int maxRecords);
        void commit(Map<Integer, Long> nextOffsets);
        @Override void close();
    }
    Session open(Binding binding);
}
