package com.hm.manage.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class IpamPingProbeTest
{
    @Test
    void shouldUseSecondBasedTimeoutOnLinux()
    {
        String original = System.getProperty("os.name");
        try
        {
            System.setProperty("os.name", "Linux");
            assertEquals(List.of("ping", "-n", "-c", "1", "-W", "2", "2.57.1.10"),
                new IpamPingProbe().buildCommand("2.57.1.10", 1500));
        }
        finally
        {
            if (original == null)
            {
                System.clearProperty("os.name");
            }
            else
            {
                System.setProperty("os.name", original);
            }
        }
    }
}
