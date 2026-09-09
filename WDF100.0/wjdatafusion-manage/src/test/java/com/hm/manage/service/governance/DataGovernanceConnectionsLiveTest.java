package com.hm.manage.service.governance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hm.common.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static com.hm.manage.service.governance.DataGovernanceConnections.*;

@EnabledIfEnvironmentVariable(named="GOVERNANCE_PG_CREDENTIALS", matches=".+")
class DataGovernanceConnectionsLiveTest
{
    @TempDir Path temporary;
    @Test void readsAnActualCompleteDictionaryAndRejectsOversizedRowsAndBatches() throws Exception
    {
        Path path = Path.of(System.getenv("GOVERNANCE_PG_CREDENTIALS"));
        ObjectMapper mapper = new ObjectMapper(); JsonNode secret = mapper.readTree(Files.readAllBytes(path));
        assertEquals("127.0.0.1", secret.path("host").asText()); assertEquals(15432, secret.path("port").asInt());
        String schema = "gov_test_" + UUID.randomUUID().toString().replace("-", "");
        Properties jdbc = new Properties(); jdbc.setProperty("user", secret.path("username").asText()); jdbc.setProperty("password", secret.path("password").asText());
        try (var setup = DriverManager.getConnection("jdbc:postgresql://127.0.0.1:15432/" + secret.path("database").asText(), jdbc); var statement = setup.createStatement())
        {
            try (var rows = statement.executeQuery("SHOW data_directory")) { assertTrue(rows.next()); assertEquals(path.getParent().getParent().resolve("postgres/data").toRealPath(), Path.of(rows.getString(1)).toRealPath()); }
            statement.execute("CREATE SCHEMA " + schema);
            try
            {
                statement.execute("CREATE TABLE " + schema + ".dict(code text, value text, nullable text)");
                statement.execute("INSERT INTO " + schema + ".dict VALUES ('CAM-001','中文编码',NULL),('CAM-002','0','Y')");
                statement.execute("CREATE TABLE " + schema + ".large(value text)");
                statement.execute("INSERT INTO " + schema + ".large VALUES (repeat('汉', 100000))");
                statement.execute("CREATE TABLE " + schema + ".many AS SELECT generate_series(1,1001) AS id");
                statement.execute("CREATE TABLE " + schema + ".reserved_column(__governance_oversize integer)");
                statement.execute("INSERT INTO " + schema + ".reserved_column VALUES (2),(1),(3)");
                DataGovernanceProperties properties = new DataGovernanceProperties(); properties.setStorageDir(temporary.toString()); properties.getNifi().setBaseUrl("https://localhost:9443/nifi-api");
                DataGovernanceConnections service = new DataGovernanceConnections(properties, DataGovernanceConnectionsTest.crypto());
                try
                {
                    Profile profile = service.save(null, new ProfileInput(null, "synthetic-pg", "127.0.0.1", 15432, secret.path("database").asText(), secret.path("username").asText(), secret.path("password").asText(), "disable"), 99);
                    assertEquals(true, service.test(profile.id(), 99).get("readOnly"));
                    Snapshot snapshot = service.snapshot(profile.id(), new SnapshotRequest(schema, "dict", List.of("code", "value", "nullable"), List.of("code")), 99);
                    assertEquals(2, snapshot.rowCount()); JsonNode rows = mapper.readTree(snapshot.rowsJson());
                    assertEquals("中文编码", rows.get(0).path("value").asText()); assertTrue(rows.get(0).get("nullable").isNull()); assertEquals("0", rows.get(1).path("value").asText());
                    assertTrue(assertThrows(ServiceException.class, () -> service.snapshot(profile.id(), new SnapshotRequest(schema, "large", List.of("value"), List.of()), 99)).getMessage().contains("超大行"));
                    assertTrue(assertThrows(ServiceException.class, () -> service.snapshot(profile.id(), new SnapshotRequest(schema, "many", List.of("id"), List.of()), 99)).getMessage().contains("1000"));
                    Snapshot ordered = service.snapshot(profile.id(), new SnapshotRequest(schema, "reserved_column", List.of("__governance_oversize"), List.of("__governance_oversize")), 99);
                    JsonNode orderedRows = mapper.readTree(ordered.rowsJson());
                    assertEquals(3, ordered.rowCount());
                    for (int i = 0; i < 3; i++) assertEquals(i + 1, orderedRows.get(i).path("__governance_oversize").asInt());
                }
                finally { service.close(); }
            }
            finally { statement.execute("DROP SCHEMA " + schema + " CASCADE"); }
        }
    }
}
