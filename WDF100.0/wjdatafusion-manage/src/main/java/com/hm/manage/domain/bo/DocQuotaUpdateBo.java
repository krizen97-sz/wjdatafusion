package com.hm.manage.domain.bo;

public class DocQuotaUpdateBo
{
    private Long quotaMb;
    /** 100 MB or 0 for no individual-file quota; total storage remains enforced. */
    private Long maxUploadMb;

    public Long getQuotaMb() { return quotaMb; }
    public void setQuotaMb(Long quotaMb) { this.quotaMb = quotaMb; }
    public Long getMaxUploadMb() { return maxUploadMb; }
    public void setMaxUploadMb(Long maxUploadMb) { this.maxUploadMb = maxUploadMb; }
}
