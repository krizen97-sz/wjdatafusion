package com.hm.manage.service.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hm.common.exception.ServiceException;
import com.hm.manage.service.governance.DataGovernanceModels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static com.hm.manage.service.governance.DataGovernanceEngine.*;

/** Creates its own temporary NiFi subtree; never edits an existing user flow or restarts an engine. */
@EnabledIfSystemProperty(named = "governance.nifi.smoke", matches = "true")
class DataGovernanceArtifactLiveEngineTest
{
    @TempDir Path reports;
    @Test void retainsCompleteFrozenOutputAndEveryArtifactBeyondPreviewCount() throws Exception
    {
        DataGovernanceProperties properties = new DataGovernanceProperties(); properties.setStorageDir(reports.toString()); properties.setTestTimeoutSeconds(90);
        properties.getNifi().setBaseUrl(System.getProperty("governance.nifi.baseUrl"));
        properties.getNifi().setRootGroupId(System.getProperty("governance.nifi.rootGroupId"));
        properties.getNifi().setCredentialsFile(System.getProperty("governance.nifi.credentialsFile"));
        var client = new DataGovernanceNifiClient(properties); var engine = new DataGovernanceEngine(client, properties);
        String marker = "RYNEW_ARTIFACT_SMOKE:" + UUID.randomUUID();
        String temporaryRoot = engine.createGroup(engine.root(), "完整产物独立验收", marker).path("component").path("id").asText();
        properties.getNifi().setRootGroupId(temporaryRoot);
        var repository = new DataGovernanceFileRunRepository(properties); var artifacts = new DataGovernanceArtifactStore(properties);
        var service = new DataGovernanceService(engine, new DataGovernanceTestRunner(engine, artifacts), repository);
        try
        {
            Project project = engine.createProject(new CreateProject("完整字节验收", "仅合成样本，独立临时目录"));
            Flow direct = engine.createFlow(new CreateFlow(project.id(), "大于8KiB完整输出", "blank"));
            String source = engine.createProcessor(direct.id(), "样本", STANDARD + "GenerateFlowFile", "", map("Custom Text", "{}"), List.of(), 0).path("component").path("id").asText();
            String capture = engine.createProcessor(direct.id(), "观察", UPDATE, CAPTURE, map(), List.of("success"), 200).path("component").path("id").asText();
            engine.connect(direct.id(), source, capture, List.of("success"));
            String input = new ObjectMapper().createObjectNode().put("payload", "中🙂".repeat(6000)).toString();
            byte[] expected = input.getBytes(StandardCharsets.UTF_8); assertTrue(expected.length > 8192);
            StoredRun release = service.prepareSnapshot(direct.id(), new TestInput(input, Map.of()), 7);
            TestRun run = await(service, service.submitFrozen(release, 7));
            assertEquals("SUCCEEDED", run.status, run.error); assertTrue(run.cleanupConfirmed);
            assertTrue(run.artifactsManifestAvailable); assertEquals(1, run.artifactCount);
            assertTrue(run.output.get(0).contains("[样本预览已截断]"));
            assertTrue(run.output.get(0).getBytes(StandardCharsets.UTF_8).length < expected.length);
            var reopened = new DataGovernanceArtifactStore(properties); var manifest = reopened.manifest(run.id, 7);
            var artifact = manifest.artifacts().get(0);
            assertEquals(expected.length, artifact.byteSize()); assertEquals(run.definitionHash, manifest.definitionHash());
            assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(expected)), artifact.sha256());
            try (var bytes = reopened.read(run.id, artifact.id(), 7)) { assertArrayEquals(expected, bytes.readAllBytes()); }

            assertTrue(engine.supports(WRITER), "Real Writer must be installed for all-output capture acceptance");
            Flow split = engine.createFlow(new CreateFlow(project.id(), "十二片全部捕获", DELIMITED));
            for (var entity : engine.groupContents(split.id()).path("processors"))
            {
                var component = entity.path("component");
                if (WRITER.equals(component.path("type").asText()))
                    client.json("PUT", "/processors/" + component.path("id").asText(), map("revision", entity.path("revision"), "component",
                        map("id", component.path("id").asText(), "config", map("properties", map("Field Order", "message", "Include Header", "false",
                            "Split Limit", "1", "Count Basis", "DATA_RECORDS", "Filename Prefix", "all-artifacts")))));
            }
            var records = new ObjectMapper().createArrayNode();
            for (int i = 0; i < 12; i++) records.addObject().put("message", "row-" + i);
            TestRun many = await(service, service.submit(split.id(), new TestInput(records.toString(), Map.of()), 7));
            assertEquals("SUCCEEDED", many.status, many.error); assertTrue(many.cleanupConfirmed); assertEquals(12, many.artifactCount);
            assertEquals(10, many.output.size()); // UI preview limit must not limit durable artifacts.
            var complete = reopened.manifest(many.id, 7); assertEquals(12, complete.artifacts().size());
            var rows = new HashSet<String>(); int order = 0;
            for (var file : complete.artifacts())
            {
                assertEquals(++order, file.order()); assertTrue(file.filename().startsWith("all-artifacts"));
                try (var bytes = reopened.read(many.id, file.id(), 7)) { rows.add(new String(bytes.readAllBytes(), StandardCharsets.UTF_8).trim()); }
            }
            for (int i = 0; i < 12; i++) assertTrue(rows.contains("row-" + i));
            System.out.println("Artifact NiFi acceptance: completeBytes=" + artifact.byteSize() + ", fullHashVerified=true, previews=10, completeArtifacts=12, frozenRun=true, cleanup=true");
        }
        finally
        {
            service.close(); repository.close();
            var own = client.json("GET", "/process-groups/" + temporaryRoot, null);
            assertEquals(marker, own.path("component").path("comments").asText());
            client.json("DELETE", "/process-groups/" + temporaryRoot + "?version=" + own.path("revision").path("version").asLong() + "&disconnectedNodeAcknowledged=false", null);
            assertTrue(assertThrows(ServiceException.class, () -> client.json("GET", "/process-groups/" + temporaryRoot, null)).getMessage().contains("404"));
            System.out.println("Artifact acceptance temporary NiFi root removed");
        }
    }
    private TestRun await(DataGovernanceService service, TestRun run) throws Exception
    {
        long deadline = System.currentTimeMillis() + 120000;
        while (List.of("QUEUED", "RUNNING").contains(run.status) && System.currentTimeMillis() < deadline)
        { Thread.sleep(100); run = service.run(run.id, 7); }
        return run;
    }
}
