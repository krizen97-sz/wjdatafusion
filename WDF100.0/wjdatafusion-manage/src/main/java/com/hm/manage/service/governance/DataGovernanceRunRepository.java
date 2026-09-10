package com.hm.manage.service.governance;

import java.util.List;
import com.hm.manage.service.governance.DataGovernanceModels.StoredRun;
import com.hm.manage.service.governance.DataGovernanceModels.TestRun;

/** Replaceable persistence boundary; the initial implementation is explicitly single-instance. */
public interface DataGovernanceRunRepository
{
    void save(StoredRun run);
    StoredRun find(String id);
    List<StoredRun> list();
    /** Metadata scans may be overridden to avoid loading every frozen input and observed sample at once. */
    default List<StoredRun> summaries() { return list().stream().map(DataGovernanceRunRepository::summary).toList(); }

    static StoredRun summary(StoredRun stored)
    {
        StoredRun result = new StoredRun(); result.ownerId = stored.ownerId;
        TestRun source = stored.run, run = new TestRun(); result.run = run;
        run.id = source.id; run.status = source.status; run.flowId = source.flowId; run.projectId = source.projectId;
        run.createdAt = source.createdAt; run.updatedAt = source.updatedAt; run.error = source.error;
        run.cleanupConfirmed = source.cleanupConfirmed; run.engineTestGroupId = source.engineTestGroupId;
        run.artifactsManifestAvailable = source.artifactsManifestAvailable; run.artifactCount = source.artifactCount;
        run.definitionHash = source.definitionHash; run.definitionCapturedAt = source.definitionCapturedAt;
        return result;
    }
}
