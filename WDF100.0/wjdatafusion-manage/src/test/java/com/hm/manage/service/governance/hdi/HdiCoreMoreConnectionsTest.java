package com.hm.manage.service.governance.hdi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class HdiCoreMoreConnectionsTest {
    @TempDir Path directory;
    HdiCoreConnectionsTest fixture;
    @BeforeEach void setup() throws Exception {
        fixture=new HdiCoreConnectionsTest(); fixture.privateRoot=directory; fixture.initialize(); directory=fixture.privateRoot;
        fixture.policy.setAllowedEndpoints(List.of("127.0.0.1:13306","127.0.0.1:22022","127.0.0.1:15673","192.168.50.10:3306"));
    }
    ObjectNode form(String type,String name) {
        return HdiProtocol.JSON.createObjectNode().put("datasourceType",type).put("datasourceName",name).put("folderId",-1);
    }
    ObjectNode mysql(boolean explicit) throws Exception {
        ObjectNode form=form("MySQL","原MySQL连接"),p=form.putObject("parameters");
        p.put("connectType",explicit?"url":"host").put("address","127.0.0.1:13306").put("databaseSid","owned_db").put("databaseUsername","user").put("databasePassword",fixture.encrypt("private-mysql-secret"));
        if(explicit)p.put("dbUrl","jdbc:mysql://127.0.0.1:13306/owned_db?useSSL=false&allowPublicKeyRetrieval=true"); return form;
    }
    @Test void mysqlNativeUrlOptionIsExplicitFrozenAndNeverAddedToHostMode() throws Exception {
        ObjectNode source=fixture.connections.saveForm(7,mysql(false)); long id=source.path("id").asLong();
        assertEquals("jdbc:mysql://127.0.0.1:13306/owned_db?useSSL=false",source.path("dbUrl").asText());
        var old=fixture.connections.freezeReferences(7,fixture.visual(id,"mysqlInput"));
        ObjectNode edit=mysql(true).put("id",id).put("_platformRevision","1"); fixture.connections.saveForm(7,edit);
        assertEquals("url",fixture.connections.formParameters(7,id).path("connectType").asText());
        var resolved=fixture.connections.resolveForRun(7,fixture.visual(id,"mysqlInput"),old);
        assertEquals("mysql",resolved.connectionBindings().get(0).path("kind").asText());
        assertFalse(resolved.connections().path(Long.toString(id)).path("datasource").path("dbUrl").asText().contains("allowPublicKeyRetrieval"));
        assertFalse(fixture.connections.get(7,id).toString().contains("private-mysql-secret"));
        ((ObjectNode)edit.path("parameters")).put("connectType","host"); edit.put("_platformRevision","2");
        assertThrows(HdiTaskFault.class,()->fixture.connections.saveForm(7,edit));
        ((ObjectNode)edit.path("parameters")).put("connectType","url").put("dbUrl","jdbc:mysql://127.0.0.1:13306/owned_db?useSSL=false&socketFactory=unknown");
        assertThrows(HdiTaskFault.class,()->fixture.connections.saveForm(7,edit));
    }
    @Test void explicitlyApprovedNonLoopbackMysqlCanUseTheSameNativeUrlOption() throws Exception {
        ObjectNode form=mysql(true); ((ObjectNode)form.path("parameters")).put("address","192.168.50.10:3306").put("dbUrl","jdbc:mysql://192.168.50.10:3306/owned_db?useSSL=false&allowPublicKeyRetrieval=true");
        assertEquals("192.168.50.10",fixture.connections.saveForm(7,form).path("databaseIp").asText());
    }
    @Test void rabbitUsesOriginalSslConventionAndDoesNotInventAQueueList() throws Exception {
        ObjectNode form=form("RabbitMQ","原Rabbit连接"); form.putObject("parameters").put("address","127.0.0.1:15673").put("Ssl","1").put("dbUrl","rabbitmq://").put("databaseUsername","fixture").put("databasePassword",fixture.encrypt("rabbit-secret"));
        var saved=fixture.connections.saveForm(7,form); long id=saved.path("id").asLong();
        assertEquals(120,saved.path("dbType").asInt()); assertEquals("0",saved.path("ext1").asText()); assertEquals("3.x",saved.path("datasourceVersion").asText());
        assertEquals("1",fixture.connections.formParameters(7,id).path("Ssl").asText());
        var bound=fixture.connections.resolveForRun(7,fixture.visual(id,"rabbitmqOutput"),fixture.connections.freezeReferences(7,fixture.visual(id,"rabbitmqOutput")));
        assertEquals("rabbitmq",bound.connectionBindings().get(0).path("kind").asText());
        assertEquals(false,fixture.connections.tables(7,id,HdiProtocol.JSON.createObjectNode()).get("metadataAvailable"));
        ((ObjectNode)form.path("parameters")).put("Ssl","0"); assertThrows(HdiTaskFault.class,()->fixture.connections.saveForm(7,form));
    }
    @Test void sftpRetainsOriginalFtpFormAndUrlButHasOnlyItsSshEndpoint() throws Exception {
        ObjectNode form=form("FTP","原SFTP连接"); form.putObject("parameters").put("Host","127.0.0.1").put("Port","22022").put("FolderPath","/input").put("ConnectMode","0").put("ext2","sftp").put("databaseUsername","fixture").put("databasePassword",fixture.encrypt("sftp-secret"));
        var saved=fixture.connections.saveForm(7,form); long id=saved.path("id").asLong(); assertEquals("ftp://127.0.0.1:22022//input",saved.path("dbUrl").asText()); assertTrue(saved.path("sftpFlag").asBoolean());
        assertEquals("sftp",fixture.connections.formParameters(7,id).path("ext2").asText());
        var bound=fixture.connections.resolveForRun(7,fixture.visual(id,"fileInput"),fixture.connections.freezeReferences(7,fixture.visual(id,"fileInput")));
        assertEquals("sftp",bound.connectionBindings().get(0).path("kind").asText()); assertFalse(bound.connectionBindings().get(0).has("passivePorts"));
        assertEquals(404,assertThrows(HdiTaskFault.class,()->fixture.connections.get(8,id)).status());
    }
    @Test void allNewTypesKeepSecretsOutOfEveryPublicReadAndFrozenReference() throws Exception {
        for (String type : List.of("MySQL", "RabbitMQ", "FTP")) {
            String secret = "private-" + type + "-readback-secret", encrypted = fixture.encrypt(secret);
            ObjectNode input = form(type, type + "秘密回读");
            ObjectNode parameters = input.putObject("parameters").put("databaseUsername", "owned-user").put("databasePassword", encrypted);
            String plugin;
            if (type.equals("MySQL")) {
                parameters.put("connectType", "host").put("address", "127.0.0.1:13306").put("databaseSid", "owned_db"); plugin = "mysqlInput";
            } else if (type.equals("RabbitMQ")) {
                parameters.put("address", "127.0.0.1:15673").put("Ssl", "1"); plugin = "rabbitmqInput";
            } else {
                parameters.put("Host", "127.0.0.1").put("Port", "22022").put("FolderPath", "/input").put("ConnectMode", "0").put("ext2", "sftp"); plugin = "fileInput";
            }
            ObjectNode saved = fixture.connections.saveForm(7, input); long id = saved.path("id").asLong();
            ObjectNode visual = fixture.visual(id, plugin), refs = fixture.connections.freezeReferences(7, visual);
            for (Object publicValue : List.of(saved, fixture.connections.get(7,id), fixture.connections.list(7,null),
                    fixture.connections.page(7,null,1,100), fixture.connections.formParameters(7,id), refs)) {
                String serialized = HdiProtocol.JSON.writeValueAsString(publicValue);
                assertFalse(serialized.contains(secret)); assertFalse(serialized.contains(encrypted));
            }
            assertEquals("******", fixture.connections.formParameters(7,id).path("databasePassword").asText());
            String stored = fixture.jdbc.queryForObject("SELECT datasource_json FROM dg_hdi_connection_revision WHERE connection_id=?", String.class, id);
            String cipher = fixture.jdbc.queryForObject("SELECT encrypted_secret FROM dg_hdi_connection_revision WHERE connection_id=?", String.class, id);
            assertFalse(stored.contains(secret)); assertFalse(stored.contains("databasePassword")); assertFalse(cipher.contains(secret));
            assertNotEquals(encrypted,cipher);
            input.put("id",id).put("_platformRevision","1"); parameters.put("databasePassword",fixture.encrypt("******"));
            fixture.connections.saveForm(7,input);
            var resolved = fixture.connections.resolveForRun(7,visual,refs);
            assertEquals(secret,resolved.connections().path(Long.toString(id)).path("datasource").path("databasePassword").asText());
            assertEquals(404,assertThrows(HdiTaskFault.class,()->fixture.connections.formParameters(8,id)).status());
        }
    }
    @Test @EnabledIfEnvironmentVariable(named="HDI_MORE_CONNECTIONS_FIXTURE",matches=".+")
    void actualOriginalMysqlSftpAndRabbitClientsAreUsedThroughThePlatformGateway() throws Exception {
        String bundle=System.getenv("HDI_ORIGINAL_BUNDLE"),javaHome=System.getenv("HDI_COMPILER_JAVA_HOME"); assertNotNull(bundle);assertNotNull(javaHome);
        Path compilerRuntime=directory.resolve("compile");Files.createDirectory(compilerRuntime,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        var properties=new HdiCoreProperties();properties.setEnabled(true);properties.setStateDir(directory.toString());
        properties.setCompilerCommand(List.of("python3",Path.of("../../tools/data-governance/hdi_original_compiler.py").toAbsolutePath().normalize().toString(),"--bundle",bundle,"--java-home",javaHome,"--runtime",compilerRuntime.toString()));
        fixture.connections.setOriginalMetadata(new HdiOriginalMetadataGateway(new HdiCompilerClient(properties)));
        Path root=Path.of(System.getenv("HDI_MORE_CONNECTIONS_FIXTURE"));
        JsonNode mysql=HdiProtocol.JSON.readTree(Files.readAllBytes(root.resolve("mysql-connection.json")));
        ObjectNode form=mysql(true);var p=(ObjectNode)form.path("parameters");p.put("address",mysql.path("host").asText()+":"+mysql.path("port").asText()).put("databaseSid",mysql.path("database").asText()).put("dbUrl",mysql.path("dbUrl").asText()).put("databaseUsername",mysql.path("username").asText()).put("databasePassword",fixture.encrypt(mysql.path("password").asText()));
        long mysqlId=fixture.connections.saveForm(7,form).path("id").asLong();assertTrue(fixture.connections.testById(7,mysqlId));
        var schemas=fixture.connections.schemas(7,mysqlId);assertEquals(mysql.path("database").asText(),schemas.get(0).get("schemaCode"));
        var tables=fixture.connections.tables(7,mysqlId,HdiProtocol.JSON.createObjectNode());assertTrue(((List<?>)tables.get("list")).size()>=2);
        var columns=fixture.connections.columns(7,mysqlId,HdiProtocol.JSON.createObjectNode().put("tableName",mysql.path("inputTable").asText()));assertEquals(2,((List<?>)columns.get("columns")).size());
        for(String type:List.of("FTP","RabbitMQ")) {
            JsonNode c=HdiProtocol.JSON.readTree(Files.readAllBytes(root.resolve(type.equals("FTP")?"sftp-connection.json":"rabbitmq-connection.json")));
            ObjectNode input=form(type,"原"+type+"实际连接");p=input.putObject("parameters").put("databaseUsername",c.path("username").asText()).put("databasePassword",fixture.encrypt(c.path("password").asText()));
            if(type.equals("FTP"))p.put("Host",c.path("host").asText()).put("Port",c.path("port").asText()).put("FolderPath",c.path("inputDir").asText()).put("ConnectMode","0").put("ext2","sftp");
            else p.put("address",c.path("host").asText()+":"+c.path("port").asText()).put("Ssl","1");
            long id=fixture.connections.saveForm(7,input).path("id").asLong();assertTrue(fixture.connections.testById(7,id));
        }
    }
}
