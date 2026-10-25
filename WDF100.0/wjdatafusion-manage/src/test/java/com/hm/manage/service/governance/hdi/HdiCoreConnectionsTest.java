package com.hm.manage.service.governance.hdi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hm.manage.config.SupportCredentialProperties;
import com.hm.manage.service.support.CredentialCryptoService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyFactory;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Cipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

class HdiCoreConnectionsTest {
    @TempDir Path privateRoot;
    HdiCoreConnections connections; JdbcTemplate jdbc; HdiCoreKeys keys; HdiCoreConnectionProperties policy;
    @BeforeEach void initialize() throws Exception {
        var db = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        var migration = new ResourceDatabasePopulator(new FileSystemResource(Path.of("../sql/data_governance_hdi_connections_upgrade_20261025.sql")));
        migration.execute(db); migration.execute(db); jdbc = new JdbcTemplate(db);
        var props = new SupportCredentialProperties(); props.setKey("isolated-hdi-connection-test-key");
        var crypto = new CredentialCryptoService(); ReflectionTestUtils.setField(crypto, "properties", props);
        privateRoot = privateRoot.toRealPath(); Files.setPosixFilePermissions(privateRoot, PosixFilePermissions.fromString("rwx------"));
        var core = new HdiCoreProperties(); core.setStateDir(privateRoot.toString()); keys = new HdiCoreKeys(core);
        policy = new HdiCoreConnectionProperties(); policy.setAllowedEndpoints(List.of("127.0.0.1:15432", "127.0.0.1:2121", "127.0.0.1:21", "127.0.0.1:19092", "127.0.0.2:19093"));
        policy.setFtpPassiveRanges(Map.of("127.0.0.1:2121", "22100-22109", "127.0.0.1:21", "22100-22109"));
        policy.setKafkaBrokers(Map.of("127.0.0.1:19092", List.of("127.0.0.2:19093")));
        connections = new HdiCoreConnections(jdbc, new DataSourceTransactionManager(db), crypto, keys, policy);
    }
    String encrypt(String password) throws Exception {
        String pem = keys.publicKey(); var key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(pem.replaceAll("-----[^-]+-----", "").replaceAll("\\s", ""))));
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding"); cipher.init(Cipher.ENCRYPT_MODE, key); return Base64.getEncoder().encodeToString(cipher.doFinal(password.getBytes(StandardCharsets.UTF_8)));
    }
    ObjectNode form(String type, String name, String password) throws Exception {
        ObjectNode form = HdiProtocol.JSON.createObjectNode().put("datasourceType", type).put("datasourceName", name).put("folderId", -1);
        ObjectNode p = form.putObject("parameters");
        if (type.equals("Kafka")) p.put("address", "127.0.0.1:19092").put("connectType", "host");
        else p.put("databaseIp", "127.0.0.1").put("databasePort", type.equals("FTP") ? "2121" : "15432").put("databaseSid", type.equals("FTP") ? "/incoming" : "fixture").put("databaseUsername", "fixture").put("databasePassword", encrypt(password));
        return form;
    }
    ObjectNode visual(long id, String plugin) {
        ObjectNode visual = HdiProtocol.JSON.createObjectNode(); visual.putObject("nodes").putObject("101").put("id", 101).put("adapterName", plugin).put("dbId", id); return visual;
    }
    @Test void originalFormPersistsOnlyEncryptedCredentialsAndNeverReturnsTheSecret() throws Exception {
        var saved = connections.saveForm(7, form("PostgreSQL", "原PG连接", "private-pg-password")); long id = saved.path("id").asLong();
        assertEquals("******", saved.path("databasePassword").asText()); assertEquals(1, saved.path("revision").asLong());
        assertFalse(saved.toString().contains("private-pg-password")); assertTrue(saved.path("passwordConfigured").asBoolean());
        String stored = jdbc.queryForObject("SELECT datasource_json FROM dg_hdi_connection_revision", String.class);
        String encrypted = jdbc.queryForObject("SELECT encrypted_secret FROM dg_hdi_connection_revision", String.class);
        assertFalse(stored.contains("password")); assertFalse(encrypted.contains("private-pg-password"));
        assertEquals(1, connections.list(7, HdiProtocol.JSON.createObjectNode()).size()); assertEquals(0, connections.list(8, null).size());
        assertEquals(404, assertThrows(HdiTaskFault.class, () -> connections.get(8, id)).status());
        assertEquals(404, assertThrows(HdiTaskFault.class, () -> connections.freezeReferences(8, visual(id, "postgresqlInput"))).status());
    }
    @Test void publishedReferencesKeepOriginalRevisionAfterConnectionEditing() throws Exception {
        var saved = connections.saveForm(7, form("PostgreSQL", "连接", "first-secret")); long id = saved.path("id").asLong(); var visual = visual(id, "postgresqlInput");
        var frozen = connections.freezeReferences(7, visual);
        var edit = form("PostgreSQL", "修改后的连接", "second-secret").put("id", id).put("_platformRevision", "1"); connections.saveForm(7, edit);
        var original = connections.resolveForRun(7, visual, frozen); assertEquals("first-secret", original.connections().path(Long.toString(id)).path("datasource").path("databasePassword").asText());
        assertEquals("1", original.connectionBindings().get(0).path("connectionRevision").asText()); assertEquals(List.of("first-secret"), original.redactionValues());
        assertEquals("second-secret", connections.resolveForRun(7, visual, connections.freezeReferences(7, visual)).connections().path(Long.toString(id)).path("datasource").path("databasePassword").asText());
        assertEquals(409, assertThrows(HdiTaskFault.class, () -> connections.saveForm(7, edit)).status());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM dg_hdi_connection_revision", Integer.class));
        connections.delete(7, id, 2); assertEquals(404, assertThrows(HdiTaskFault.class, () -> connections.resolveForRun(7, visual, frozen)).status());
    }
    @Test void maskedOriginalEditPreservesPasswordAndDoesNotAcceptPlaintext() throws Exception {
        var saved = connections.saveForm(7, form("FTP", "原FTP连接", "ftp-secret")); long id = saved.path("id").asLong();
        var edit = form("FTP", "原FTP连接", "******").put("id", id).put("_platformRevision", "1"); connections.saveForm(7, edit);
        var visual = visual(id, "fileInput"); var result = connections.resolveForRun(7, visual, connections.freezeReferences(7, visual));
        assertEquals("ftp-secret", result.connections().path(Long.toString(id)).path("datasource").path("databasePassword").asText());
        assertEquals(22100, result.connectionBindings().get(0).path("passivePorts").path("start").intValue());
        var plain = form("FTP", "坏连接", "ignored"); ((ObjectNode)plain.path("parameters")).put("databasePassword", "plaintext-refused");
        assertEquals(400, assertThrows(HdiTaskFault.class, () -> connections.saveForm(7, plain)).status());
    }
    @Test void endpointPolicyIsRecheckedForFrozenVersionsAndCannotComeFromTheBrowser() throws Exception {
        var saved = connections.saveForm(7, form("PostgreSQL", "PG", "secret")); long id = saved.path("id").asLong(); var visual = visual(id, "postgresqlInput"); var refs = connections.freezeReferences(7, visual);
        policy.setAllowedEndpoints(List.of()); assertEquals(422, assertThrows(HdiTaskFault.class, () -> connections.resolveForRun(7, visual, refs)).status());
        policy.setAllowedEndpoints(List.of("127.0.0.1:15432")); var form = form("PostgreSQL", "任意地址", "secret"); ((ObjectNode)form.path("parameters")).put("databaseIp", "10.9.9.9");
        assertThrows(HdiTaskFault.class, () -> connections.saveForm(7, form));
        ((ObjectNode)form.path("parameters")).put("databaseIp", "127.0.0.1").put("sslMode", "disable"); assertThrows(HdiTaskFault.class, () -> connections.saveForm(7, form));
        assertThrows(HdiTaskFault.class, () -> connections.resolveForRun(7, visual, HdiProtocol.JSON.createObjectNode().put(Long.toString(id), "999")));
        assertThrows(HdiTaskFault.class, () -> connections.resolveForRun(7, visual, HdiProtocol.JSON.createObjectNode().put(Long.toString(id), "1").put("9", "1")));
    }
    @Test void kafkaUsesOriginalDatasourceTypeAndExplicitAdvertisedBrokerBindings() throws Exception {
        var saved = connections.saveForm(7, form("Kafka", "原Kafka", "")); long id = saved.path("id").asLong(); var visual = visual(id, "kafkaInput");
        var result = connections.resolveForRun(7, visual, connections.freezeReferences(7, visual));
        assertEquals("kafka2://127.0.0.1:19092", result.connections().path(Long.toString(id)).path("datasource").path("dbUrl").asText());
        assertEquals(2, result.connectionBindings().get(0).path("endpoints").size()); assertEquals(0, result.redactionValues().size());
        assertEquals(0, connections.list(7, HdiProtocol.JSON.createObjectNode().put("datasourceType", "FTP")).size());
    }
    @Test void standardFtpPortWorksButCoreReservedPortsDoNot() throws Exception {
        var input = form("FTP", "标准FTP端口", "secret"); ((ObjectNode)input.path("parameters")).put("databasePort", "21");
        assertEquals("21", connections.saveForm(7, input).path("databasePort").asText());
        ((ObjectNode)input.path("parameters")).put("databasePort", "2181"); assertThrows(HdiTaskFault.class, () -> connections.saveForm(7, input));
    }
    @Test void platformRejectsWorkerForbiddenAddressesPassivePortsAndSecretBytesBeforeExecution() throws Exception {
        var input = form("FTP", "不可执行地址", "secret"); var parameters = (ObjectNode)input.path("parameters");
        parameters.put("databaseIp", "169.254.8.9"); policy.setAllowedEndpoints(List.of("169.254.8.9:2121")); policy.setFtpPassiveRanges(Map.of("169.254.8.9:2121", "22100-22109"));
        assertThrows(HdiTaskFault.class, () -> connections.saveForm(7, input));
        parameters.put("databaseIp", "127.0.0.1"); policy.setAllowedEndpoints(List.of("127.0.0.1:2121")); policy.setFtpPassiveRanges(Map.of("127.0.0.1:2121", "2170-2190"));
        assertThrows(HdiTaskFault.class, () -> connections.saveForm(7, input));
        policy.setFtpPassiveRanges(Map.of("127.0.0.1:2121", "22100-22109")); parameters.put("databasePassword", encrypt("bad\0secret"));
        assertThrows(HdiTaskFault.class, () -> connections.saveForm(7, input));
    }
    @Test void originalReferencesRejectCrossDatasourceTableBindingsAndSupportLocalOnlyFlows() throws Exception {
        var one = connections.saveForm(7, form("PostgreSQL", "输入", "secret")); var two = connections.saveForm(7, form("PostgreSQL", "输出", "secret"));
        var visual = visual(one.path("id").longValue(), "postgresqlInput"); ((ObjectNode)visual.path("nodes").path("101")).putArray("srcTableUseVoList").addObject().put("srcDbId", two.path("id").longValue());
        assertThrows(HdiTaskFault.class, () -> connections.freezeReferences(7, visual));
        assertTrue(connections.freezeReferences(7, visual(-1, "fileOutput")).isEmpty());
    }
    @Test void actualOriginalGenericFormNamesRoundTripWithoutReplacingTheForm() throws Exception {
        var pg = form("Postgresql", "原地址表单", "secret"); var p = (ObjectNode)pg.path("parameters");
        p.remove(List.of("databaseIp", "databasePort")); p.put("address", "127.0.0.1:15432").put("connectType", "host");
        var savedPg = connections.saveForm(7, pg); long pgId = savedPg.path("id").asLong();
        assertEquals("jdbc:postgresql://127.0.0.1:15432/fixture", savedPg.path("dbUrl").asText());
        assertEquals("127.0.0.1:15432", connections.formParameters(7, pgId).path("address").asText());
        var ftp = form("FTP", "原FTP表单", "secret"); p = (ObjectNode)ftp.path("parameters");
        p.remove(List.of("databaseIp", "databasePort", "databaseSid")); p.put("Host", "127.0.0.1").put("Port", "2121").put("FolderPath", "/incoming").put("ConnectMode", "1").put("ext2", "ftp").put("dbUrl", "ftp://127.0.0.1:2121//incoming");
        var savedFtp = connections.saveForm(7, ftp); long ftpId = savedFtp.path("id").asLong();
        assertEquals("ftp://127.0.0.1:2121//incoming", savedFtp.path("dbUrl").asText()); assertEquals("1", savedFtp.path("ext1").asText()); assertEquals("ftp", savedFtp.path("ext2").asText());
        assertEquals("/incoming", connections.formParameters(7, ftpId).path("FolderPath").asText());
        assertEquals("******", connections.formParameters(7, ftpId).path("databasePassword").asText());
        var kafka = form("Kafka", "原Kafka表单", ""); p = (ObjectNode)kafka.path("parameters"); p.put("certification", "none").put("authentication", "none").put("JaasConfig", "unused original hidden template");
        assertEquals(8, connections.saveForm(7, kafka).path("dbType").asInt());
        p.put("authentication", "ranger"); assertThrows(HdiTaskFault.class, () -> connections.saveForm(7, kafka));
    }
    @Test @EnabledIfEnvironmentVariable(named = "HDI_CONNECTION_FIXTURE_DIR", matches = ".+")
    void realLocalPostgresFtpKafkaConnectionsAndTableMetadata() throws Exception {
        Path directory = Path.of(System.getenv("HDI_CONNECTION_FIXTURE_DIR"));
        JsonNode pgFixture = HdiProtocol.JSON.readTree(Files.readAllBytes(directory.resolve("postgres-connection.json")));
        JsonNode ftpFixture = HdiProtocol.JSON.readTree(Files.readAllBytes(directory.resolve("ftp-connection.json")));
        var pg = form("PostgreSQL", "本机原PG连接验收", pgFixture.path("password").asText()); var p = (ObjectNode)pg.path("parameters");
        p.put("databaseIp", pgFixture.path("host").asText()).put("databasePort", pgFixture.path("port").asText()).put("databaseSid", pgFixture.path("database").asText()).put("databaseUsername", pgFixture.path("username").asText());
        long pgId = connections.saveForm(7, pg).path("id").asLong(); assertEquals(-1, connections.get(7, pgId).path("status").asInt()); assertTrue(connections.testById(7, pgId)); assertEquals(1, connections.get(7, pgId).path("status").asInt());
        String schema = pgFixture.path("schema").asText(); assertTrue(connections.schemas(7, pgId).stream().anyMatch(s -> schema.equals(s.get("schemaCode"))));
        var tables = connections.tables(7, pgId, HdiProtocol.JSON.createObjectNode().put("schemaCode", schema)); assertTrue(((List<?>)tables.get("list")).size() >= 2);
        Map<?,?> discovered = (Map<?,?>)((List<?>)tables.get("list")).get(0); long tableId = ((Number)discovered.get("id")).longValue();
        var registered = connections.getTable(7, tableId); assertFalse(((List<?>)registered.get("fieldUpdateRequestList")).isEmpty());
        assertEquals(404, assertThrows(HdiTaskFault.class, () -> connections.getTable(8, tableId)).status());
        var secondRead = connections.tables(7, pgId, HdiProtocol.JSON.createObjectNode().put("schemaCode", schema)); assertEquals(discovered.get("id"), ((Map<?,?>)((List<?>)secondRead.get("list")).get(0)).get("id"));
        var fields = connections.columns(7, pgId, HdiProtocol.JSON.createObjectNode().put("tableSchem", schema).put("tableName", "source_rows")); assertFalse(((List<?>)fields.get("columns")).isEmpty());
        var ftp = form("FTP", "本机原FTP连接验收", ftpFixture.path("password").asText()); p = (ObjectNode)ftp.path("parameters");
        p.put("databaseIp", ftpFixture.path("host").asText()).put("databasePort", ftpFixture.path("port").asText()).put("databaseUsername", ftpFixture.path("username").asText());
        long ftpId = connections.saveForm(7, ftp).path("id").asLong(); assertTrue(connections.testById(7, ftpId));
        policy.setKafkaBrokers(Map.of()); long kafkaId = connections.saveForm(7, form("Kafka", "本机原Kafka连接验收", "")).path("id").asLong(); assertTrue(connections.testById(7, kafkaId)); assertNotNull(connections.tables(7, kafkaId, HdiProtocol.JSON.createObjectNode()).get("list"));
        assertEquals(404, assertThrows(HdiTaskFault.class, () -> connections.testById(8, pgId)).status());
    }
}
