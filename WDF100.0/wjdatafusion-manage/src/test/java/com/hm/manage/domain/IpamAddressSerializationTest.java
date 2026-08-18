package com.hm.manage.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;

class IpamAddressSerializationTest
{
    @Test
    void shouldNeverSerializePlaintextCredential() throws Exception
    {
        IpamAddress address = new IpamAddress();
        address.setAddressId(1L);
        address.setIpAddress("2.57.1.10");
        address.setLoginPassword("plain-secret");
        address.setCredentialConfigured(true);

        String json = new ObjectMapper().writeValueAsString(address);

        assertFalse(json.contains("plain-secret"));
        assertFalse(json.contains("loginPassword"));
        assertTrue(json.contains("\"credentialConfigured\":true"));
    }
}
