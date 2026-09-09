package com.hm.manage.service.governance;

import java.util.List;
import com.hm.manage.service.governance.DataGovernanceModels.StoredRun;

/** Replaceable persistence boundary; the initial implementation is explicitly single-instance. */
public interface DataGovernanceRunRepository
{
    void save(StoredRun run);
    StoredRun find(String id);
    List<StoredRun> list();
}
