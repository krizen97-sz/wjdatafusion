package com.hm.manage.domain.bo;

import java.util.List;

public record SupportEquipmentServerIntakeBo(
    Long siteId, Long platformId, String addressText, String namePrefix, Row defaults,
    List<Row> rows, Boolean reuseExisting)
{
    public SupportEquipmentServerIntakeBo
    {
        reuseExisting = Boolean.TRUE.equals(reuseExisting);
    }

    public record Row(Integer rowNumber, String serverName, String serverAddress, String sshPort,
        String osType, String hikPassword, String rootPassword, String otherUsername,
        String otherPassword, String status) { }

    public record CheckedRow(Row data, String state, List<String> errors, Long existingServerId,
        String existingScope, boolean alreadyBound) { }

    public record Preview(List<CheckedRow> rows, int createCount, int reuseCount, int skipCount, int errorCount) { }

    public record Result(int createdCount, int boundCount, int skippedCount) { }
}
