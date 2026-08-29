package com.hm.manage.service.document;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.hm.common.exception.ServiceException;
import com.hm.manage.config.DocumentManagementProperties;

class DocumentJwtServiceTest
{
    private DocumentJwtService service;

    @BeforeEach
    void setUp()
    {
        DocumentManagementProperties properties = new DocumentManagementProperties();
        properties.getOnlyOffice().setJwtSecret("0123456789abcdef0123456789abcdef");
        service = new DocumentJwtService();
        ReflectionTestUtils.setField(service, "properties", properties);
    }

    @Test
    void shouldRoundTripSignedDocumentClaims()
    {
        String token = service.sign(Map.of("purpose", "DOCUMENT_FILE", "documentId", 42L), Duration.ofMinutes(5));
        Map<String, Object> claims = service.verify(token);

        assertEquals("DOCUMENT_FILE", claims.get("purpose"));
        assertEquals(42L, service.numberValue(claims.get("documentId")));
    }

    @Test
    void shouldRejectTamperedAndExpiredTokens()
    {
        String token = service.sign(Map.of("documentId", 42L), Duration.ofMinutes(5));
        String[] parts = token.split("\\.");
        String payload = parts[1];
        String tamperedPayload = (payload.startsWith("A") ? "B" : "A") + payload.substring(1);
        String tampered = parts[0] + "." + tamperedPayload + "." + parts[2];

        assertThrows(ServiceException.class, () -> service.verify(tampered));
        assertThrows(ServiceException.class,
            () -> service.verify(service.sign(Map.of("documentId", 42L), Duration.ofSeconds(-1))));
    }

    @Test
    void shouldRejectNonCanonicalSignatureEncoding()
    {
        String token = service.sign(Map.of("documentId", 42L), Duration.ofMinutes(5));
        String[] parts = token.split("\\.");
        byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
        String canonical = Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        int index = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
            .indexOf(canonical.charAt(canonical.length() - 1));
        char alias = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_".charAt(index + 1);
        String nonCanonical = parts[0] + "." + parts[1] + "."
            + canonical.substring(0, canonical.length() - 1) + alias;

        assertArrayEquals(signature, Base64.getUrlDecoder().decode(nonCanonical.split("\\.")[2]));
        assertThrows(ServiceException.class, () -> service.verify(nonCanonical));
    }
}
