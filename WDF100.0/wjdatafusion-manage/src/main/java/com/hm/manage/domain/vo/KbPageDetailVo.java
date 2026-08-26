package com.hm.manage.domain.vo;

import java.util.List;
import com.hm.manage.domain.KbPage;

public class KbPageDetailVo
{
    private KbPage page;
    private List<String> tags;
    private List<KbDocumentLinkVo> documents;

    public KbPage getPage() { return page; }
    public void setPage(KbPage page) { this.page = page; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    public List<KbDocumentLinkVo> getDocuments() { return documents; }
    public void setDocuments(List<KbDocumentLinkVo> documents) { this.documents = documents; }
}
