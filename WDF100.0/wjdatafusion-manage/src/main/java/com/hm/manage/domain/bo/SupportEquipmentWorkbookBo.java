package com.hm.manage.domain.bo;

import java.util.List;
import java.util.Map;

public class SupportEquipmentWorkbookBo
{
    private Long siteId;
    private List<Row> rows;
    public Long getSiteId() { return siteId; }
    public void setSiteId(Long siteId) { this.siteId = siteId; }
    public List<Row> getRows() { return rows; }
    public void setRows(List<Row> rows) { this.rows = rows; }

    public static class Row extends SupportEquipmentDeviceRefBo
    {
        private String revision;
        private Map<String, Object> changes;
        public String getRevision() { return revision; }
        public void setRevision(String revision) { this.revision = revision; }
        public Map<String, Object> getChanges() { return changes; }
        public void setChanges(Map<String, Object> changes) { this.changes = changes; }
    }
}
