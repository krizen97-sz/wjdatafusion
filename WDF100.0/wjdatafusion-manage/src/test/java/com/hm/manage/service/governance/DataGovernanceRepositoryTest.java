package com.hm.manage.service.governance;

import com.hm.common.exception.ServiceException;
import com.hm.manage.service.governance.DataGovernanceModels.*;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DataGovernanceRepositoryTest
{
    @TempDir Path directory;
    @Test void atomicRecordsSurviveReopenAndSecondWriterIsRejected()
    {
        DataGovernanceProperties properties = new DataGovernanceProperties(); properties.setStorageDir(directory.toString());
        var first = new DataGovernanceFileRunRepository(properties); var second = new DataGovernanceFileRunRepository(properties);
        StoredRun record = new StoredRun(); record.ownerId = 12; record.inputJson = "{}"; record.run = new TestRun();
        record.run.id = UUID.randomUUID().toString(); record.run.status = "RUNNING"; record.run.createdAt = Instant.now().toString();
        try
        {
            first.save(record);
            assertThrows(ServiceException.class, () -> second.save(record));
            assertThrows(ServiceException.class, () -> first.find("../../outside"));
            first.close(); second.close();
            var reopened = new DataGovernanceFileRunRepository(properties);
            try { assertEquals(12, reopened.find(record.run.id).ownerId); assertEquals("RUNNING", reopened.list().get(0).run.status); }
            finally { reopened.close(); }
        }
        finally { first.close(); second.close(); }
    }
}
