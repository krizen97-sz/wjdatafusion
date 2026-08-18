package com.hm.manage.service.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
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
        String tampered = token.substring(0, token.length() - 1) + (token.endsWith("A") ? "B" : "A");

        assertThrows(ServiceException.class, () -> service.verify(tampered));
        assertThrows(ServiceException.class,
            () -> service.verify(service.sign(Map.of("documentId", 42L), Duration.ofSeconds(-1))));
    }
}
