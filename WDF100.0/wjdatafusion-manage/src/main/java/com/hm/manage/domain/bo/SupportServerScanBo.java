package com.hm.manage.domain.bo;

import java.util.List;

public final class SupportServerScanBo
{
    private SupportServerScanBo() {}
    public record Start(Long siteId, Long platformId, String targets, List<Integer> ports,
        List<Long> credentialIds, Integer attemptLimit) {}
    public record Selection(String address, Integer port) {}
    public record Confirm(List<Selection> targets, boolean fingerprintsConfirmed) {}
    public record Credential(Long credentialId, Long siteId, String name, String mode,
        String hikPassword, String rootPassword) {}
}
