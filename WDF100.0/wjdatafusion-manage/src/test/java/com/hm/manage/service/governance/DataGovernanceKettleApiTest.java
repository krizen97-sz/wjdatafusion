package com.hm.manage.service.governance;

import com.fasterxml.jackson.databind.*;
import com.hm.common.exception.ServiceException;
import com.hm.manage.config.SupportCredentialProperties;
import com.hm.manage.service.support.CredentialCryptoService;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.hm.manage.service.governance.DataGovernanceKettleService.*;

class DataGovernanceKettleApiTest
{
    @TempDir Path temporary;
    final ObjectMapper mapper = new ObjectMapper();
    DataGovernanceKettleProperties properties;
    FakeWorker worker;
    DataGovernanceKettleService service;
    @BeforeEach void open() throws Exception
    {
        properties = new DataGovernanceKettleProperties(); properties.setEnabled(true); properties.setStorageDir(temporary.toString());
        worker = new FakeWorker(properties); service = new DataGovernanceKettleService(properties, crypto(), worker);
    }
    @AfterEach void close() { service.close(); }
    static CredentialCryptoService crypto() throws Exception
    {
        SupportCredentialProperties p = new SupportCredentialProperties(); p.setKey("synthetic-kettle-api-key-not-a-production-secret");
        CredentialCryptoService crypto = new CredentialCryptoService(); var field = CredentialCryptoService.class.getDeclaredField("properties");
        field.setAccessible(true); field.set(crypto, p); return crypto;
    }
    static String xml()
    { return "<transformation><info><name>native</name></info><step><name>source</name><type>Dummy</type><password>synthetic-secret-abc</password><extension vendor='future'><![CDATA[var value = a < 3 && b > 2;]]><!--keep--><unknown flag='yes'>future value</unknown></extension></step></transformation>"; }
    DefinitionInput input(Long revision, String xml) { return new DefinitionInput("测试原生流程", revision, DataGovernanceKettleXml.encode(xml)); }
    String save(String xml) { return (String)service.save(null, input(null, xml), 7).get("id"); }
    String returned(String id) { return DataGovernanceKettleXml.decode((String)service.definition(id, 7).get("xmlBase64")); }
    @Test void unknownXmlCdataCommentsAndExtensionAttributesSurviveAndSecretsNeverReturn() throws Exception
    {
        String id = save(xml()); String response = returned(id);
        assertFalse(response.contains("synthetic-secret-abc")); assertTrue(response.contains("__RYNEW_SECRET_"));
        assertTrue(response.contains("<![CDATA[var value = a < 3 && b > 2;]]>")); assertTrue(response.contains("<!--keep-->"));
        assertTrue(response.contains("vendor=\"future\"")); assertTrue(response.contains("future value"));
        String disk = Files.readString(temporary.resolve("definitions").resolve(id + ".json"));
        assertFalse(disk.contains("synthetic-secret-abc")); assertFalse(disk.contains("<transformation"));
        service.save(id, input(1L, response.replace("<name>source</name>", "<name>renamed</name>")), 7);
        var stored = mapper.readValue(Files.readString(temporary.resolve("definitions").resolve(id + ".json")), Definition.class);
        String real = crypto().decrypt(stored.encryptedXml);
        assertTrue(real.contains("<name>renamed</name>")); assertTrue(real.contains("synthetic-secret-abc"));
        assertEquals(2L, service.definition(id, 7).get("revision"));
    }
    @Test void emptySecretExplicitlyClearsAndCopiedMarkersCannotMoveAcrossNodes()
    {
        String id = save(xml()); String current = returned(id);
        Document doc = DataGovernanceKettleXml.parse(current); Element secret = (Element)doc.getElementsByTagName("password").item(0);
        String marker = secret.getTextContent(); secret.setTextContent("");
        service.save(id, input(1L, DataGovernanceKettleXml.serialize(doc)), 7);
        assertFalse(returned(id).contains(marker));
        String newId = save(xml() + " "); String source = returned(newId);
        Document moved = DataGovernanceKettleXml.parse(source); Element node = (Element)moved.getElementsByTagName("step").item(0);
        node.setAttribute(DataGovernanceKettleXml.ID, UUID.randomUUID().toString());
        assertThrows(ServiceException.class, () -> service.save(newId, input(1L, DataGovernanceKettleXml.serialize(moved)), 7));
        assertThrows(ServiceException.class, () -> service.save(null, input(null, source), 7));
    }
    @Test void keyValueAndAttributeSecretsAreMasked()
    {
        String id = save("<transformation><connection password='synthetic-attribute'><attributes><attribute><code>password</code><value>synthetic-kv-secret</value></attribute></attributes></connection><step><name>x</name><type>Dummy</type></step></transformation>");
        String returned = returned(id); assertFalse(returned.contains("synthetic-attribute")); assertFalse(returned.contains("synthetic-kv-secret"));
        assertTrue(returned.contains("__RYNEW_SECRET_")); service.save(id, input(1L, returned), 7);
    }
    @Test void malformedXmlDtdAndExternalEntitiesAreRejectedWithoutWorkerCalls()
    {
        for (String bad : List.of("<transformation>", "<!DOCTYPE x [<!ENTITY v SYSTEM 'file:///etc/passwd'>]><transformation>&v;</transformation>", "<unrelated/>"))
            assertThrows(ServiceException.class, () -> save(bad));
        assertThrows(ServiceException.class, () -> service.save(null, new DefinitionInput("name", null, "not base64!"), 7));
        assertTrue(worker.calls.isEmpty());
    }
    @Test void disabledWorkerStillAllowsDraftsAndMissingNativeConfiguration()
    {
        properties.setEnabled(false); String id = save("<transformation><step><name>draft</name><type>TableInput</type><future>value</future></step></transformation>");
        assertEquals(1, service.definitions(7).size()); assertNotNull(service.definition(id, 7));
        assertThrows(ServiceException.class, () -> service.validate(id, 7));
        assertThrows(ServiceException.class, () -> service.submit(id, new RunInput(1L, "run", null, null, null), 7));
        assertTrue(worker.calls.isEmpty());
    }
    @Test void revisionAndOwnerChecksPrecedeProxyCalls()
    {
        String id = save(xml());
        assertTrue(service.definitions(8).isEmpty());
        assertThrows(ServiceException.class, () -> service.definition(id, 8));
        assertThrows(ServiceException.class, () -> service.save(id, input(0L, xml()), 7));
        assertThrows(ServiceException.class, () -> service.validate(id, 8));
        assertThrows(ServiceException.class, () -> service.files(id, 8));
        assertThrows(ServiceException.class, () -> service.submit(id, new RunInput(1L, "run", null, null, null), 8));
        assertTrue(worker.calls.isEmpty());
    }
    @Test void immutableRunIsRecordedBeforeSubmissionAndUnknownSubmissionIsNeverRepeated() throws Exception
    {
        String id = save(xml()); worker.failSubmit = true;
        worker.onPut = () -> {
            assertEquals(1, filesIn("runs").size());
            service.save(id, input(1L, returned(id).replace("future value", "later revision")), 7);
        };
        Map<String,Object> run = service.submit(id, new RunInput(1L, "run", null, 20, "request-one"), 7);
        assertEquals("SUBMISSION_UNKNOWN", run.get("state")); String runId = (String)run.get("id");
        StoredRun stored = mapper.readValue(Files.readString(temporary.resolve("runs").resolve(runId + ".json")), StoredRun.class);
        assertEquals(1, stored.revision); assertTrue(crypto().decrypt(stored.encryptedXml).contains("future value"));
        assertFalse(crypto().decrypt(stored.encryptedXml).contains("later revision"));
        assertEquals(1, worker.submissions);
        assertEquals(runId, service.submit(id, new RunInput(1L, "run", null, 20, "request-one"), 7).get("id"));
        assertThrows(ServiceException.class, () -> service.submit(id, new RunInput(2L, "run", null, 20, "request-one"), 7));
        service.run(runId, 7); assertEquals(1, worker.submissions);
        assertThrows(ServiceException.class, () -> service.run(runId, 8));
        int calls = worker.calls.size();
        assertThrows(ServiceException.class, () -> service.events(runId, 0, 8));
        assertThrows(ServiceException.class, () -> service.stop(runId, 8));
        assertThrows(ServiceException.class, () -> service.download(runId, "result.csv", 8));
        assertEquals(calls, worker.calls.size());
    }
    @Test void sameRequestIdReturnsOriginalRunWithoutResubmissionAndPayloadIsScrubbed()
    {
        String id = save(xml()); RunInput input = new RunInput(1L, "run", null, 20, "same-request");
        Map<String,Object> first = service.submit(id, input, 7); Map<String,Object> second = service.submit(id, input, 7);
        assertEquals(first.get("id"), second.get("id")); assertEquals(1, worker.submissions);
        assertFalse(first.toString().contains("synthetic-secret-abc"));
        assertEquals("RUNNING", first.get("state"));
    }
    @Test void acceptedNativePreparingStateIsReconciledRatherThanMistakenForLocalPreparation()
    {
        worker.initialState = "PREPARING"; worker.readState = "SUCCEEDED";
        String id = save(xml()); Map<String,Object> run = service.submit(id, new RunInput(1L, "run", null, 20, "preparing"), 7);
        assertEquals("PREPARING", run.get("state")); assertEquals("ACCEPTED", run.get("submissionState"));
        assertEquals("SUCCEEDED", service.run((String)run.get("id"), 7).get("state"));
    }
    @Test void fileUploadsAreBoundedPrivateAndFrozenInRun() throws Exception
    {
        String id = save(xml());
        FileInfo info = service.upload(id, "input.csv", new ByteArrayInputStream("name\nalice\n".getBytes(StandardCharsets.UTF_8)), 7);
        assertEquals(11, info.bytes());
        assertThrows(ServiceException.class, () -> service.upload(id, "../escape.csv", new ByteArrayInputStream(new byte[1]), 7));
        assertThrows(ServiceException.class, () -> service.upload(id, "bad\nfile\n.csv", new ByteArrayInputStream(new byte[1]), 7));
        assertThrows(ServiceException.class, () -> service.upload(id, "large.csv", new ByteArrayInputStream(new byte[8 * 1024 * 1024 + 1]), 7));
        assertThrows(ServiceException.class, () -> service.upload(id, "input.csv", new ByteArrayInputStream(new byte[1]), 7));
        var run = service.submit(id, new RunInput(1L, "run", null, null, "files"), 7);
        service.deleteFile(id, info.id(), 7); assertTrue(service.files(id, 7).isEmpty());
        StoredRun stored = mapper.readValue(Files.readString(temporary.resolve("runs").resolve(run.get("id") + ".json")), StoredRun.class);
        assertEquals(1, stored.inputs.size()); assertEquals("name\nalice\n", new String(Base64.getDecoder().decode(crypto().decrypt(stored.inputs.get(0).encryptedContent)), StandardCharsets.UTF_8));
        assertThrows(ServiceException.class, () -> service.download((String)run.get("id"), "../outside", 7));
        FileInfo chinese = service.upload(id, "中文输入.csv", new ByteArrayInputStream(new byte[1]), 7);
        assertEquals("中文输入.csv", chinese.name()); assertEquals(id + ".ktr", service.definition(id, 7).get("runtimeFilename"));
    }
    @Test void importedZipIsAllValidatedBeforeSavingAndRejectsTraversalOrNonXml() throws Exception
    {
        assertThrows(ServiceException.class, () -> service.imports("917552 (1).zip", new ByteArrayInputStream(zip(Map.of("a.ktr", new byte[]{47,78,91,110,(byte)174,88,(byte)163,(byte)132}))), 7));
        assertThrows(ServiceException.class, () -> service.imports("bad.zip", new ByteArrayInputStream(zip(Map.of("good.ktr", xml().getBytes(StandardCharsets.UTF_8), "bad.kjb", "binary".getBytes(StandardCharsets.UTF_8)))), 7));
        assertThrows(ServiceException.class, () -> service.imports("escape.zip", new ByteArrayInputStream(zip(Map.of("../escape.ktr", xml().getBytes(StandardCharsets.UTF_8)))), 7));
        assertTrue(service.definitions(7).isEmpty());
        assertEquals(2, service.imports("valid.zip", new ByteArrayInputStream(zip(Map.of("one.ktr", xml().getBytes(StandardCharsets.UTF_8), "two.ktr", xml().getBytes(StandardCharsets.UTF_8)))), 7).size());
        assertTrue(worker.calls.isEmpty());
    }
    @Test void boundJobUsesSameOwnerChildXmlSnapshotAndRejectsOtherOwner()
    {
        String child = save(xml()); String jobXml = "<job><name>job</name><entries><entry data-rynew-definition-id='" + child + "'><name>transform</name><type>TRANS</type><filename>${WORK_DIR}/child.ktr</filename></entry></entries></job>";
        String job = save(jobXml); var run = service.submit(job, new RunInput(1L, "run", null, null, "job"), 7);
        assertEquals("job", run.get("kind")); assertTrue(worker.calls.stream().anyMatch(c -> c.startsWith("PUT /jobs/")));
        assertEquals("child.ktr", worker.lastSubmit.path("inputFiles").get(0).path("name").asText());
        String foreign = (String)service.save(null, input(null, xml()), 8).get("id");
        String invalid = save(jobXml.replace(child, foreign)); int calls = worker.calls.size();
        assertThrows(ServiceException.class, () -> service.submit(invalid, new RunInput(1L, "run", null, null, "foreign"), 7));
        assertEquals(calls, worker.calls.size());
    }
    @Test void jobMergesChildAssetsByNormalizedNameAndHashAndNeverSilentlyOverrides()
    {
        String child = save(xml()); String job = save(jobXml(child)); byte[] content = "same-content".getBytes(StandardCharsets.UTF_8);
        service.upload(child, "CAFE\u0301.csv", new ByteArrayInputStream(content), 7);
        service.upload(job, "café.CSV", new ByteArrayInputStream(content), 7);
        service.submit(job, new RunInput(1L, "run", null, 20, "merge-same"), 7);
        assertEquals(2, worker.lastSubmit.path("inputFiles").size()); // one asset plus child XML
        for (var file : service.files(job, 7)) service.deleteFile(job, file.id(), 7);
        service.upload(job, "café.CSV", new ByteArrayInputStream("different-content".getBytes(StandardCharsets.UTF_8)), 7);
        int calls = worker.calls.size();
        ServiceException conflict = assertThrows(ServiceException.class, () -> service.submit(job, new RunInput(1L, "run", null, 20, "merge-conflict"), 7));
        assertTrue(conflict.getMessage().contains("禁止自动覆盖")); assertEquals(calls, worker.calls.size());
    }
    @Test void changingChildXmlOrAssetsDoesNotChangeExistingRunAndNewRunHasNewFrozenFingerprint() throws Exception
    {
        String child = save(xml()); String job = save(jobXml(child));
        FileInfo asset = service.upload(child, "中文输入.csv", new ByteArrayInputStream("version-one".getBytes(StandardCharsets.UTF_8)), 7);
        RunInput firstRequest = new RunInput(1L, "run", null, 20, "frozen-assets-one");
        var first = service.submit(job, firstRequest, 7);
        assertEquals(2, worker.lastSubmit.path("inputFiles").size());
        service.save(child, input(1L, returned(child).replace("future value", "changed child")), 7);
        service.deleteFile(child, asset.id(), 7);
        service.upload(child, "中文输入.csv", new ByteArrayInputStream("version-two".getBytes(StandardCharsets.UTF_8)), 7);
        var repeated = service.submit(job, firstRequest, 7);
        assertEquals(first.get("id"), repeated.get("id")); assertEquals(first.get("inputsHash"), repeated.get("inputsHash"));
        var second = service.submit(job, new RunInput(1L, "run", null, 20, "frozen-assets-two"), 7);
        assertEquals(first.get("xmlSha256"), second.get("xmlSha256"));
        assertNotEquals(first.get("inputsHash"), second.get("inputsHash")); assertNotEquals(first.get("snapshotFingerprint"), second.get("snapshotFingerprint"));
        StoredRun saved = mapper.readValue(Files.readString(temporary.resolve("runs").resolve(first.get("id") + ".json")), StoredRun.class);
        StoredFile oldAsset = saved.inputs.stream().filter(f -> f.info.name().equals("中文输入.csv")).findFirst().orElseThrow();
        assertEquals("version-one", new String(Base64.getDecoder().decode(crypto().decrypt(oldAsset.encryptedContent)), StandardCharsets.UTF_8));
        assertEquals(child, oldAsset.sourceDefinitionId); assertEquals(1, oldAsset.sourceRevision);
        assertEquals(first.get("inputsHash"), service.runs(job, 7).get(1).get("inputsHash"));
    }
    private String jobXml(String child)
    { return "<job><name>job</name><entries><entry data-rynew-definition-id='" + child + "'><name>transform</name><type>TRANS</type><filename>${WORK_DIR}/" + child + ".ktr</filename></entry></entries></job>"; }
    @Test void catalogKeepsDiscoveryLoadingAndExecutionEvidenceSeparate() throws Exception
    {
        Path catalog = temporary.resolve("catalog.json"); Files.writeString(catalog, "{\"plugins\":[{\"kind\":\"step\",\"id\":\"Unavailable\",\"registeredClass\":\"example.Meta\",\"name\":{\"zhCN\":{\"text\":\"未加载工具\",\"evidence\":\"editorial_translation\"}},\"category\":{\"zhCN\":{\"text\":\"输入\"}}}]}");
        properties.setCatalogFile(catalog.toString()); Map<String,Object> response = service.catalog();
        String json = mapper.writeValueAsString(response); assertTrue(json.contains("未加载工具")); assertTrue(json.contains("DISCOVERED"));
        assertFalse(json.contains("defaultXml\"")); assertTrue(json.contains("defaultXmlBase64")); assertFalse(json.contains("synthetic-default-password"));
        JsonNode tree = mapper.valueToTree(response);
        for (JsonNode entry : tree.path("steps")) if (entry.has("defaultXmlBase64"))
        { String template = DataGovernanceKettleXml.decode(entry.path("defaultXmlBase64").asText());
          assertFalse(template.contains("synthetic-default-password")); assertFalse(template.contains("data-rynew-")); }
    }
    @Test void nativeExecutionUnsupportedJobRemainsNonExecutableDespiteLoadableMeta()
    {
        JsonNode result = mapper.valueToTree(service.catalog());
        JsonNode shell = null, special = null;
        for (JsonNode job : result.path("jobs"))
        { if (job.path("id").asText().equals("SHELL")) shell = job; if (job.path("id").asText().equals("SPECIAL")) special = job; }
        assertNotNull(shell); assertNotNull(special); assertTrue(shell.path("loadable").asBoolean());
        assertFalse(shell.path("executionSupported").asBoolean()); assertFalse(shell.path("executable").asBoolean());
        assertTrue(special.path("executable").asBoolean()); assertFalse(special.path("executionValidated").asBoolean());
    }
    @Test void ownerScopedHistoryReturnsNewestFirstAndRetainsFailureAndUnknownStatesWithoutPolling() throws Exception
    {
        String id = save(xml());
        var first = service.submit(id, new RunInput(1L, "run", null, 20, "history-one"), 7);
        worker.failSubmit = true;
        var unknown = service.submit(id, new RunInput(1L, "preview", "source", 12, "history-two"), 7);
        worker.invalidSave = true;
        var validation = service.submit(id, new RunInput(1L, "run", null, 20, "history-three"), 7);
        worker.preparationFailure = true;
        var preparation = service.submit(id, new RunInput(1L, "run", null, 20, "history-four"), 7);
        int calls = worker.calls.size(); properties.setEnabled(false);
        var history = service.runs(id, 7);
        assertEquals(List.of(preparation.get("id"), validation.get("id"), unknown.get("id"), first.get("id")), history.stream().map(r -> r.get("id")).toList());
        assertEquals(List.of("PREPARATION_FAILED", "VALIDATION_FAILED", "SUBMISSION_UNKNOWN", "RUNNING"), history.stream().map(r -> r.get("state")).toList());
        assertEquals("preview", history.get(2).get("mode")); assertEquals(12, history.get(2).get("rowLimit"));
        assertEquals(calls, worker.calls.size()); assertThrows(ServiceException.class, () -> service.runs(id, 8));
        for (var item : history) for (String absent : List.of("owner", "encryptedXml", "inputs", "workerSnapshot", "nodes", "files")) assertFalse(item.containsKey(absent));
        assertFalse(mapper.writeValueAsString(history).contains("synthetic-secret-abc"));
    }
    List<Path> filesIn(String folder)
    { try (var files = Files.list(temporary.resolve(folder))) { return files.filter(p -> p.toString().endsWith(".json")).toList(); } catch (IOException e) { throw new RuntimeException(e); } }
    static byte[] zip(Map<String,byte[]> entries) throws IOException
    { ByteArrayOutputStream bytes = new ByteArrayOutputStream(); try (ZipOutputStream zip = new ZipOutputStream(bytes)) { for (var entry : entries.entrySet()) { zip.putNextEntry(new ZipEntry(entry.getKey())); zip.write(entry.getValue()); zip.closeEntry(); } } return bytes.toByteArray(); }
    static class FakeWorker extends DataGovernanceKettleClient
    {
        final ObjectMapper mapper = new ObjectMapper(); final List<String> calls = new ArrayList<>();
        int submissions; boolean failSubmit, invalidSave, preparationFailure; Runnable onPut; JsonNode lastSubmit;
        String initialState = "RUNNING", readState;
        FakeWorker(DataGovernanceKettleProperties properties) { super(properties); }
        @Override public JsonNode request(String method, String path, Object body)
        {
            calls.add(method + " " + path);
            if (path.equals("/capabilities")) return mapper.valueToTree(Map.of("steps", List.of(Map.of("id", "Dummy", "className", "example.DummyMeta", "name", "Dummy", "category", "Flow", "loadable", true, "defaultXml", "<password>synthetic-default-password</password>")),
                "jobs", List.of(Map.of("id", "SHELL", "className", "example.JobShell", "name", "Shell", "category", "Script", "loadable", true, "executionSupported", false),
                    Map.of("id", "SPECIAL", "className", "example.JobSpecial", "name", "Start", "category", "General", "loadable", true, "executionSupported", true))));
            if (method.equals("PUT")) { if (preparationFailure) throw new Failure(503); if (onPut != null) onPut.run(); return mapper.valueToTree(Map.of("validation", Map.of("valid", !invalidSave))); }
            if (method.equals("POST") && path.equals("/runs"))
            {
                submissions++; lastSubmit = mapper.valueToTree(body); if (failSubmit) throw new Failure(0);
                return mapper.valueToTree(Map.of("id", lastSubmit.path("runId").asText(), "state", initialState, "message", "synthetic-secret-abc"));
            }
            if (method.equals("GET") && path.startsWith("/runs/") && readState != null) return mapper.valueToTree(Map.of("state", readState));
            if (path.endsWith("/validate")) return mapper.valueToTree(Map.of("valid", true));
            throw new Failure(404);
        }
    }
}
