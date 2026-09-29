package com.hm.manage.service.support;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class SupportServerScanTargetsTest
{
    @Test void expandsDeduplicatesAndKeepsOnlyHostAddresses()
    {
        assertEquals(List.of("192.0.2.1", "192.0.2.2", "192.0.2.8", "192.0.2.9"),
            SupportServerScanTargets.expand("192.0.2.0/30;192.0.2.2；192.0.2.8-192.0.2.9"));
        assertEquals(2, SupportServerScanTargets.expand("192.0.2.0/31").size());
        assertEquals(List.of("192.0.2.1"), SupportServerScanTargets.expand("192.0.2.1/32"));
    }
    @Test void refusesUnboundedSpecialOrMalformedTargets()
    {
        for (String input : List.of("0.0.0.0/0", "10.0.0.0/16", "127.0.0.1", "169.254.169.254", "224.0.0.1", "::1", "example.com", "192.0.2.3-192.0.2.1", "192.0.2.1;uname -a"))
            assertThrows(Exception.class, () -> SupportServerScanTargets.expand(input), input);
    }
    @Test void onlyTheThreeRequestedPortsAreAllowed()
    {
        assertEquals(List.of(22, 2343, 55555), SupportServerScanTargets.ports(null));
        assertEquals(List.of(55555, 22), SupportServerScanTargets.ports(List.of(55555, 22, 22)));
        assertThrows(Exception.class, () -> SupportServerScanTargets.ports(List.of(23)));
        assertThrows(Exception.class, () -> SupportServerScanTargets.ports(Arrays.asList(22, null)));
    }
    @Test void parsesOsReleaseWithoutExecutingShellAssignments()
    {
        var facts = SupportServerScanProbe.parse("__RY_HOST__\nserver-a\n__RY_KERNEL__\n5.14.0\n__RY_OS__\nNAME=\"CentOS Linux\"\nVERSION_ID=\"7\"\nIGNORED=secret\n__RY_LEGACY__\n__RY_END__\n");
        assertEquals("server-a", facts.hostName()); assertEquals("CentOS Linux", facts.osName());
        assertEquals("7", facts.osVersion()); assertEquals("5.14.0", facts.kernelVersion());
        assertEquals("CentOS Linux 7", facts.osType()); assertTrue(facts.warning().isEmpty());
        assertFalse(facts.values().toString().contains("secret"));
        assertFalse(SupportServerScanProbe.FACTS_COMMAND.contains(". /etc/os-release"));
    }
    @Test void legacyCentosVersionsAndIncompleteOutputsAreHonest()
    {
        var legacy = SupportServerScanProbe.parse("__RY_HOST__\na\n__RY_KERNEL__\n2.6.32\n__RY_OS__\n__RY_LEGACY__\nCentOS release 6.10 (Final)\n__RY_END__\n");
        assertEquals("6.10", legacy.osVersion()); assertEquals("CentOS", legacy.osName());
        var partial = SupportServerScanProbe.parse("__RY_OS__\nNAME=Linux\n__RY_END__\n");
        assertFalse(partial.warning().isEmpty()); assertTrue(partial.osType().isEmpty());
    }
    @Test void centosMajorVersionIsRefinedUsingItsCompatibleReleaseFile()
    {
        var facts = SupportServerScanProbe.parse("__RY_HOST__\na\n__RY_KERNEL__\n3.10\n__RY_OS__\nNAME=\"CentOS Linux\"\nVERSION_ID=\"7\"\n__RY_LEGACY__\nCentOS Linux release 7.9.2009 (Core)\n__RY_END__\n");
        assertEquals("7.9.2009", facts.osVersion());
    }
    @Test void sharedGuardThrottlesFailedAttemptsAndDoesNotThrottleFingerprints()
    {
        var guard = new SupportServerLoginGuard();
        try (var lease = guard.acquire("192.0.2.1")) { assertThrows(Exception.class, () -> guard.acquire("192.0.2.1")); }
        try (var lease = guard.acquire("192.0.2.1")) { lease.attempted(); }
        assertThrows(Exception.class, () -> guard.acquire("192.0.2.1"));
        try (var lease = guard.acquire("192.0.2.2")) { assertNotNull(lease); }
    }
}
