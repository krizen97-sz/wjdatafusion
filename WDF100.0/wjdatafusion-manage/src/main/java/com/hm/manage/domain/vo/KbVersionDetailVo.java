package com.hm.manage.domain.vo;

import java.util.List;
import com.hm.manage.domain.KbPageVersion;

public class KbVersionDetailVo
{
    private KbPageVersion version;
    private List<String> tags;
    private List<Long> documentIds;

    public KbPageVersion getVersion() { return version; }
    public void setVersion(KbPageVersion version) { this.version = version; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    public List<Long> getDocumentIds() { return documentIds; }
    public void setDocumentIds(List<Long> documentIds) { this.documentIds = documentIds; }
}
