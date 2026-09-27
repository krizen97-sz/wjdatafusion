package com.hm.manage.util;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.hm.manage.domain.SupportServer;
import com.hm.manage.domain.SupportServerCredential;
import com.hm.manage.domain.SupportHardwareAsset;

class SupportServerAccountNamesTest
{
    @Test void onlyTheFixedHikAccountIsCanonicalized()
    {
        for (String value : new String[] {"hik", "HIK", "Hik", " hIk "})
            assertEquals("hik", SupportServerAccountNames.normalize(value));
        for (String value : new String[] {"root", "ROOT", "Admin", "Hikvision", "HIK_backup"})
            assertEquals(value, SupportServerAccountNames.normalize(value));
        assertNull(SupportServerAccountNames.normalize(null));
    }

    @Test void historicalRowsReadAsHikWithoutChangingPasswords()
    {
        SupportServer server = new SupportServer();
        ReflectionTestUtils.setField(server, "osUsername", "HIK");
        server.setOsPasswordCipher("old-encrypted-value");
        assertEquals("hik", server.getOsUsername());
        assertEquals("old-encrypted-value", server.getOsPasswordCipher());

        SupportServerCredential account = new SupportServerCredential();
        ReflectionTestUtils.setField(account, "username", "Hik");
        account.setPassword("Keep-HIK-PaSs");
        assertEquals("hik", account.getUsername());
        assertEquals("Keep-HIK-PaSs", account.getPassword());
        account.setUsername("HIK");
        assertEquals("hik", account.getUsername());

        SupportHardwareAsset hardware = new SupportHardwareAsset();
        hardware.setLoginUsername("HIK");
        assertEquals("hik", hardware.getLoginUsername());
    }
}
