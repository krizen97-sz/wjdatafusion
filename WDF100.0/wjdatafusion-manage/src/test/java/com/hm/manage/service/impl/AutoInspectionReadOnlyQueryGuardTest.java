package com.hm.manage.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import com.hm.common.exception.ServiceException;

class AutoInspectionReadOnlyQueryGuardTest
{
    @Test
    void acceptsSingleSelectAndWithQueries()
    {
        assertEquals("SELECT COUNT(*) AS total FROM pass_record",
                AutoInspectionReadOnlyQueryGuard.normalize("SELECT COUNT(*) AS total FROM pass_record;"));
        assertEquals("WITH recent AS (SELECT id FROM task) SELECT COUNT(*) FROM recent",
                AutoInspectionReadOnlyQueryGuard.normalize("WITH recent AS (SELECT id FROM task) SELECT COUNT(*) FROM recent"));
        assertEquals("SELECT 'update; delete' AS display_text",
                AutoInspectionReadOnlyQueryGuard.normalize("SELECT 'update; delete' AS display_text"));
    }

    @Test
    void rejectsMultipleStatementsAndWriteOperations()
    {
        assertThrows(ServiceException.class,
                () -> AutoInspectionReadOnlyQueryGuard.normalize("SELECT 1; DELETE FROM task"));
        assertThrows(ServiceException.class,
                () -> AutoInspectionReadOnlyQueryGuard.normalize("WITH changed AS (UPDATE task SET status=1 RETURNING id) SELECT * FROM changed"));
        assertThrows(ServiceException.class,
                () -> AutoInspectionReadOnlyQueryGuard.normalize("SELECT * FROM task FOR UPDATE"));
    }

    @Test
    void rejectsFileReadFunctions()
    {
        assertThrows(ServiceException.class,
                () -> AutoInspectionReadOnlyQueryGuard.normalize("SELECT load_file('/etc/passwd')"));
        assertThrows(ServiceException.class,
                () -> AutoInspectionReadOnlyQueryGuard.normalize("SELECT pg_read_file('/etc/passwd')"));
    }
}
