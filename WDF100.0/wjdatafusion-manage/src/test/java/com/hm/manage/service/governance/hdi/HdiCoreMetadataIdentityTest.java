package com.hm.manage.service.governance.hdi;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

/** Case-exact identities must not depend on the management database's human-name collation. */
class HdiCoreMetadataIdentityTest {
    record Identity(long owner, long connection, String schema, String name, String kind) { }
    private static final List<Identity> IDENTITIES = List.of(
        new Identity(7, 1, "schema", "Foo", "TABLE"),
        new Identity(7, 1, "schema", "foo", "TABLE"),
        new Identity(7, 1, "Schema", "Foo", "TABLE"),
        new Identity(7, 1, "schema", "Foo", "TOPIC"),
        new Identity(8, 1, "schema", "Foo", "TABLE"),
        new Identity(7, 2, "schema", "Foo", "TABLE"),
        new Identity(7, 3, "", "Orders.V1", "TOPIC"),
        new Identity(7, 3, "", "orders.v1", "TOPIC"));

    @Test void h2ConfirmsCaseSensitiveHashInputsAndRepeatLookupButDoesNotStandInForMysqlCollation() {
        DriverManagerDataSource source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        migrate(source); verifyExactRegistration(source);
    }

    @Test @EnabledIfEnvironmentVariable(named = "HDI_METADATA_MYSQL_FIXTURE", matches = ".+")
    void realMysqlCiColumnsDoNotMergePostgresqlOrKafkaIdentifiers() throws Exception {
        Path path = Path.of(System.getenv("HDI_METADATA_MYSQL_FIXTURE"));
        assertFalse(Files.isSymbolicLink(path));
        assertTrue(Files.getPosixFilePermissions(path).stream().noneMatch(p -> p.name().startsWith("GROUP_") || p.name().startsWith("OTHERS_")));
        JsonNode config = HdiProtocol.JSON.readTree(Files.readAllBytes(path));
        assertEquals("rynew-hdi-metadata-case-test", config.path("owner").asText());
        assertEquals("127.0.0.1", config.path("host").asText()); assertEquals(13306, config.path("port").asInt());
        String database = config.path("database").asText(); assertTrue(database.matches("rynew_hdi_case_[a-f0-9]{12}"));
        // The admin module already supplies this runtime driver; do not add a second dependency to manage.
        Path driverJar = Path.of(config.path("driverJar").asText()); assertFalse(Files.isSymbolicLink(driverJar));
        assertEquals(config.path("driverSha256").asText(), HdiTaskStore.sha256(Files.readAllBytes(driverJar)));
        try (URLClassLoader driverLoader = new URLClassLoader(new java.net.URL[]{driverJar.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            var driver = (java.sql.Driver)Class.forName("com.mysql.cj.jdbc.Driver", true, driverLoader).getDeclaredConstructor().newInstance();
            SimpleDriverDataSource source = new SimpleDriverDataSource(driver, "jdbc:mysql://127.0.0.1:13306/" + database
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false", config.path("username").asText(), config.path("password").asText());
            JdbcTemplate jdbc = new JdbcTemplate(source);
            assertEquals(database, jdbc.queryForObject("SELECT DATABASE()", String.class));
            assertEquals(Path.of(config.path("expectedDatadir").asText()).normalize(), Path.of(jdbc.queryForObject("SELECT @@datadir", String.class)).normalize());
            // Only this separately provisioned, explicitly marked test database receives DDL or rows.
            migrate(source); verifyExactRegistration(source);
            for (String column : List.of("schema_name", "table_name")) {
                String collation = jdbc.queryForObject("SELECT COLLATION_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=? AND TABLE_NAME='dg_hdi_connection_table' AND COLUMN_NAME=?", String.class, database, column);
                assertNotNull(collation); assertTrue(collation.endsWith("_ci"), "This test must use actual case-insensitive MySQL name columns");
            }
            assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM dg_hdi_connection_table WHERE owner_id=7 AND connection_id=1 AND table_kind='TABLE' AND schema_name='schema' AND table_name='foo'", Integer.class));
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM dg_hdi_connection_table WHERE owner_id=7 AND connection_id=3 AND table_kind='TOPIC' AND table_name='orders.v1'", Integer.class));
        }
    }
    private static void migrate(DataSource source) {
        ResourceDatabasePopulator migration = new ResourceDatabasePopulator(new FileSystemResource(Path.of("../sql/data_governance_hdi_connections_upgrade_20261025.sql")));
        migration.execute(source); migration.execute(source);
    }
    private static void verifyExactRegistration(DataSource source) {
        JdbcTemplate jdbc = new JdbcTemplate(source);
        HdiCoreConnections connections = new HdiCoreConnections(jdbc, new DataSourceTransactionManager(source), null, null, new HdiCoreConnectionProperties());
        List<Long> ids = new ArrayList<>();
        for (Identity item : IDENTITIES) {
            Long id = ReflectionTestUtils.invokeMethod(connections, "registerTable", item.owner, item.connection, item.schema, item.name, item.kind);
            assertNotNull(id); ids.add(id);
            assertEquals(id, ReflectionTestUtils.invokeMethod(connections, "registerTable", item.owner, item.connection, item.schema, item.name, item.kind));
            Map<String,Object> stored = jdbc.queryForMap("SELECT schema_name,table_name,table_kind,object_key FROM dg_hdi_connection_table WHERE table_id=? AND owner_id=?", id, item.owner);
            assertEquals(item.schema, stored.get("schema_name")); assertEquals(item.name, stored.get("table_name")); assertEquals(item.kind, stored.get("table_kind"));
            String expected = HdiTaskStore.sha256((item.schema + "\0" + item.name + "\0" + item.kind).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            assertEquals(expected, stored.get("object_key"));
        }
        assertEquals(IDENTITIES.size(), new LinkedHashSet<>(ids).size());
        assertEquals(IDENTITIES.size(), jdbc.queryForObject("SELECT COUNT(*) FROM dg_hdi_connection_table", Integer.class));
    }
}
