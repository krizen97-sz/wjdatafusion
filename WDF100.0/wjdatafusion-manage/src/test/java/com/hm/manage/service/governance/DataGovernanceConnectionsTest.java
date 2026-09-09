package com.hm.manage.service.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hm.common.exception.ServiceException;
import com.hm.manage.config.SupportCredentialProperties;
import com.hm.manage.service.support.CredentialCryptoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static com.hm.manage.service.governance.DataGovernanceConnections.*;

class DataGovernanceConnectionsTest
{
    @TempDir Path temporary;
    static CredentialCryptoService crypto() throws Exception
    {
        SupportCredentialProperties properties = new SupportCredentialProperties(); properties.setKey("synthetic-governance-test-key-not-a-real-secret");
        CredentialCryptoService crypto = new CredentialCryptoService();
        var field = CredentialCryptoService.class.getDeclaredField("properties"); field.setAccessible(true); field.set(crypto, properties);
        return crypto;
    }
    DataGovernanceProperties properties()
    {
        DataGovernanceProperties p = new DataGovernanceProperties(); p.setStorageDir(temporary.toString()); p.getNifi().setBaseUrl("https://localhost:9443/nifi-api"); return p;
    }
    static ProfileInput input(Long revision, String password)
    { return new ProfileInput(revision, "测试字典", "127.0.0.1", 15432, "postgres", "synthetic_user", password, "disable"); }
    @Test void encryptedProfilesKeepPasswordsOutOfResponsesAndPreserveOnBlankUpdate() throws Exception
    {
        try (var service = closeable(properties()))
        {
            Profile profile = service.value.save(null, input(null, "synthetic-password"), 7);
            String stored = Files.readString(temporary.resolve("connections").resolve(profile.id() + ".json"));
            assertFalse(stored.contains("synthetic-password")); assertTrue(stored.contains("encryptedPassword"));
            String response = new ObjectMapper().writeValueAsString(profile);
            assertFalse(response.contains("encryptedPassword")); assertFalse(response.contains("synthetic-password"));
            StoredProfile envelope = new ObjectMapper().readValue(stored, StoredProfile.class);
            assertEquals("synthetic-password", crypto().decrypt(envelope.encryptedPassword));
            Profile update = service.value.save(profile.id(), input(profile.revision(), ""), 7);
            StoredProfile reread = new ObjectMapper().readValue(Files.readString(temporary.resolve("connections").resolve(profile.id() + ".json")), StoredProfile.class);
            assertEquals(envelope.encryptedPassword, reread.encryptedPassword); assertEquals(2, update.revision());
        }
    }
    @Test void ownerAndRevisionChecksRejectBeforeAnyConnection() throws Exception
    {
        try (var service = closeable(properties()))
        {
            Profile profile = service.value.save(null, input(null, "synthetic-password"), 7);
            assertTrue(service.value.list(8).isEmpty());
            assertThrows(ServiceException.class, () -> service.value.test(profile.id(), 8));
            assertThrows(ServiceException.class, () -> service.value.save(profile.id(), input(0L, "replacement"), 7));
            assertThrows(ServiceException.class, () -> service.value.save(profile.id(), input(1L, "replacement"), 8));
            assertEquals(1, service.value.list(7).get(0).revision());
        }
    }
    @Test void explicitEndpointsRejectDnsAndJdbcOptionInjection() throws Exception
    {
        try (var service = closeable(properties()))
        {
            assertEquals("127.0.0.1", service.value.validateEndpoint("localhost", 15432));
            for (String host : List.of("127.0.0.1?sslmode=disable", "127.1", "example.com", "2130706433", "127.00.0.1", "169.254.169.254"))
                assertThrows(ServiceException.class, () -> service.value.validateEndpoint(host, 15432));
            assertThrows(ServiceException.class, () -> service.value.validateEndpoint("127.0.0.1", 5432));
        }
    }
    @Test void remoteConnectionsCannotDisableCertificateVerification() throws Exception
    {
        DataGovernanceProperties properties = properties(); properties.setConnectionAllowedEndpoints(List.of("10.0.0.10:5432"));
        try (var service = closeable(properties))
        {
            assertThrows(ServiceException.class, () -> service.value.save(null, new ProfileInput(null, "remote", "10.0.0.10", 5432, "db", "user", "synthetic", "disable"), 7));
            assertEquals("verify-full", service.value.save(null, new ProfileInput(null, "remote", "10.0.0.10", 5432, "db", "user", "synthetic", "verify-full"), 7).sslMode());
        }
    }
    @Test void quotedIdentifiersCannotTruncateOrAlterTheStructuredQuery()
    {
        assertEquals("汉".repeat(21), identifier("汉".repeat(21)));
        assertThrows(ServiceException.class, () -> identifier("汉".repeat(21) + "A"));
        assertThrows(ServiceException.class, () -> identifier("汉".repeat(21) + "B"));
        for (String bad : List.of("id; DROP TABLE t", "t--", "table\"", "a.b", "*")) assertThrows(ServiceException.class, () -> identifier(bad));
        assertThrows(ServiceException.class, () -> snapshotSql(new SnapshotRequest("pg_catalog", "pg_authid", List.of("rolname"), List.of())));
        assertThrows(ServiceException.class, () -> snapshotSql(new SnapshotRequest("public", "t", List.of("id", "id"), List.of())));
        String sql = snapshotSql(new SnapshotRequest("public", "字典", List.of("code", "name"), List.of("code")));
        assertTrue(sql.contains("CASE WHEN")); assertTrue(sql.contains("octet_length(snapshot_source.\"code\"::text)::bigint"));
        assertTrue(sql.contains("AS __governance_oversize")); assertTrue(sql.endsWith("ORDER BY snapshot_source.\"code\" LIMIT 1001"));
    }
    @Test void instanceLocksAreReleasedAndClosedStoresDoNotReopen() throws Exception
    {
        DataGovernanceConnections first = new DataGovernanceConnections(properties(), crypto());
        DataGovernanceConnections second = new DataGovernanceConnections(properties(), crypto());
        first.list(1); assertThrows(ServiceException.class, () -> second.list(1));
        first.close(); assertTrue(second.list(1).isEmpty()); second.close();
        assertThrows(ServiceException.class, () -> second.list(1));
    }
    @Test void unsafeColumnTypesAndNonFiniteNumbersAreRejected()
    {
        assertThrows(ServiceException.class, () -> scalar(new byte[2]));
        assertThrows(ServiceException.class, () -> scalar(Double.NaN));
        assertThrows(ServiceException.class, () -> scalar(Float.POSITIVE_INFINITY));
        assertNull(scalar(null)); assertEquals("0", scalar("0"));
    }
    private CloseableService closeable(DataGovernanceProperties p) throws Exception { return new CloseableService(new DataGovernanceConnections(p, crypto())); }
    private record CloseableService(DataGovernanceConnections value) implements AutoCloseable { public void close() { value.close(); } }
}
