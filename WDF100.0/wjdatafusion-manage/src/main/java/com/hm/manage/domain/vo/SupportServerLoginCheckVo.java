package com.hm.manage.domain.vo;

import java.util.List;

public record SupportServerLoginCheckVo(String status, String message, String mode, String loginUsername,
    String host, int port, String hostFingerprint, boolean rootPasswordVerified, String checkedAt, List<Step> steps)
{
    public record Step(String name, String status, String message) {}
}
