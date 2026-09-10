package com.hm.manage.service.governance;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.hm.manage.service.governance.DataGovernanceKettleService.*;

/** Explicit local broker test; only synthetic rows and private worker outputs. */
@EnabledIfSystemProperty(named="kettle.live", matches="true")
class DataGovernanceKettleLiveTest
{
    @TempDir Path temporary;
    @Test void originalCsvScriptFileRunsThroughOwnerGatewayAndDownloadsWholeArtifact() throws Exception
    {
        DataGovernanceKettleProperties properties = new DataGovernanceKettleProperties(); properties.setEnabled(true);
        properties.setStorageDir(temporary.toString()); properties.setWorkerUrl("http://127.0.0.1:19162");
        properties.setWorkerTokenFile(System.getProperty("kettle.tokenFile"));
        DataGovernanceKettleClient client = new DataGovernanceKettleClient(properties);
        DataGovernanceKettleService service = new DataGovernanceKettleService(properties, DataGovernanceKettleApiTest.crypto(), client);
        try
        {
            JsonNode capabilities = client.request("GET", "/capabilities", null); String xml = fixture(capabilities, "中文输入.csv");
            Map<String,Object> definition = service.save(null, new DefinitionInput("原生 API 合成验收", null, DataGovernanceKettleXml.encode(xml)), 73);
            String id = (String)definition.get("id");
            service.upload(id, "中文输入.csv", new ByteArrayInputStream("name\nalice\nbob\n".getBytes(StandardCharsets.UTF_8)), 73);
            assertTrue(service.validate(id, 73).path("valid").asBoolean());
            Map<String,Object> run = service.submit(id, new RunInput(1L, "run", null, 20, "native-api-smoke"), 73);
            String runId = (String)run.get("id"); long deadline = System.nanoTime() + 60_000_000_000L;
            while (!Set.of("SUCCEEDED", "FAILED", "SUBMISSION_UNKNOWN", "PREPARATION_FAILED", "VALIDATION_FAILED").contains(run.get("state")) && System.nanoTime() < deadline)
            { Thread.sleep(150); run = service.run(runId, 73); }
            assertEquals("SUCCEEDED", run.get("state"));
            assertEquals(runId, service.submit(id, new RunInput(1L, "run", null, 20, "native-api-smoke"), 73).get("id"));
            JsonNode files = (JsonNode)run.get("files"); String outputName = "";
            for (JsonNode file : files) if (file.path("role").asText().equals("output")) outputName = file.path("name").asText();
            assertFalse(outputName.isEmpty());
            try (var download = service.download(runId, outputName, 73))
            { String output = new String(download.stream().readAllBytes(), StandardCharsets.UTF_8); assertTrue(output.contains("ALICE!")); assertTrue(output.contains("BOB!")); }
            assertTrue(((JsonNode)service.events(runId, 0, 73).get("events")).size() > 0);
            assertThrows(com.hm.common.exception.ServiceException.class, () -> service.run(runId, 74));
        }
        finally { service.close(); }
    }
    @Test
    @EnabledIfSystemProperty(named="kettle.jobLive", matches="true")
    void jobBindingSnapshotsSameOwnerTransformationAndChineseFileThroughOriginalEngine() throws Exception
    {
        DataGovernanceKettleProperties properties = new DataGovernanceKettleProperties(); properties.setEnabled(true);
        properties.setStorageDir(temporary.toString()); properties.setWorkerUrl("http://127.0.0.1:19162");
        properties.setWorkerTokenFile(System.getProperty("kettle.tokenFile"));
        DataGovernanceKettleClient client = new DataGovernanceKettleClient(properties);
        DataGovernanceKettleService service = new DataGovernanceKettleService(properties, DataGovernanceKettleApiTest.crypto(), client);
        try
        {
            JsonNode capabilities = client.request("GET", "/capabilities", null);
            Map<String,Object> child = service.save(null, new DefinitionInput("中文输入子转换", null,
                DataGovernanceKettleXml.encode(fixture(capabilities, "中文作业输入.csv"))), 73);
            String childId = (String)child.get("id"), childFilename = (String)child.get("runtimeFilename");
            service.upload(childId, "中文作业输入.csv", new ByteArrayInputStream("name\nalice\nbob\n".getBytes(StandardCharsets.UTF_8)), 73);
            Map<String,String> defaults = new HashMap<>(); for (JsonNode entry : capabilities.path("jobs")) defaults.put(entry.path("id").asText(), entry.path("defaultXml").asText());
            assertTrue(defaults.containsKey("SPECIAL")); assertTrue(defaults.containsKey("TRANS"));
            Document document = DataGovernanceKettleXml.parse("<job><name>synthetic-api-job</name><entries/><hops/></job>");
            Element entries = (Element)document.getElementsByTagName("entries").item(0);
            String[] types = {"SPECIAL", "TRANS"}, names = {"开始", "运行子转换"};
            for (int i = 0; i < types.length; i++)
            {
                Element entry = document.createElement("entry"); entries.appendChild(entry);
                Document settings = DataGovernanceKettleXml.parse("<settings>" + defaults.get(types[i]) + "</settings>");
                for (Node n = settings.getDocumentElement().getFirstChild(); n != null; n = n.getNextSibling()) entry.appendChild(document.importNode(n, true));
                put(entry, "name", names[i]); put(entry, "type", types[i]); put(entry, "copy_nr", "0");
                if (i == 0) { put(entry, "start", "Y"); put(entry, "dummy", "N"); put(entry, "repeat", "N"); }
                else { put(entry, "filename", "${WORK_DIR}/" + childFilename); entry.setAttribute("data-rynew-definition-id", childId); }
            }
            Element hop = document.createElement("hop"); document.getElementsByTagName("hops").item(0).appendChild(hop);
            for (var value : Map.of("from", names[0], "to", names[1], "from_nr", "0", "to_nr", "0", "enabled", "Y", "evaluation", "Y", "unconditional", "Y").entrySet()) put(hop, value.getKey(), value.getValue());
            Map<String,Object> job = service.save(null, new DefinitionInput("绑定原生子转换作业", null, DataGovernanceKettleXml.encode(DataGovernanceKettleXml.serialize(document))), 73);
            String jobId = (String)job.get("id");
            assertTrue(service.validate(jobId, 73).path("valid").asBoolean());
            Map<String,Object> run = service.submit(jobId, new RunInput(1L, "run", null, 20, "native-job-api-smoke"), 73);
            String runId = (String)run.get("id"); long deadline = System.nanoTime() + 60_000_000_000L;
            while (!Set.of("SUCCEEDED", "FAILED", "SUBMISSION_UNKNOWN", "PREPARATION_FAILED", "VALIDATION_FAILED").contains(run.get("state")) && System.nanoTime() < deadline)
            { Thread.sleep(150); run = service.run(runId, 73); }
            assertEquals("SUCCEEDED", run.get("state")); assertEquals("job", run.get("kind"));
            assertEquals(runId, service.runs(jobId, 73).get(0).get("id"));
            boolean downloaded = false;
            for (JsonNode file : (JsonNode)run.get("files")) if (file.path("role").asText().equals("output"))
            { try (var download = service.download(runId, file.path("name").asText(), 73))
              { String output = new String(download.stream().readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(output.contains("ALICE!")); assertTrue(output.contains("BOB!")); downloaded = true; } }
            assertTrue(downloaded);
        }
        finally { service.close(); }
    }
    static String fixture(JsonNode capabilities, String inputFilename)
    {
        Map<String,String> defaults = new HashMap<>(); for (JsonNode step : capabilities.path("steps")) defaults.put(step.path("id").asText(), step.path("defaultXml").asText());
        Document document = DataGovernanceKettleXml.parse("<transformation><info><name>synthetic-api-native</name><trans_type>Normal</trans_type><size_rowset>100</size_rowset><capture_step_performance>N</capture_step_performance><feedback_shown>N</feedback_shown></info><order/></transformation>");
        Element root = document.getDocumentElement(); String[] types = {"CsvInput", "ScriptValueMod", "TextFileOutput"}; String[] names = {"file-input", "native-script", "file-output"};
        for (int i = 0; i < types.length; i++)
        {
            assertTrue(defaults.containsKey(types[i]), "Required original plugin absent: " + types[i]);
            Element step = document.createElement("step"); root.appendChild(step);
            put(step, "name", names[i]); put(step, "type", types[i]); put(step, "copies", "1"); put(step, "distribute", "Y"); put(step, "partitioning/method", "none");
            Document settings = DataGovernanceKettleXml.parse("<settings>" + defaults.get(types[i]) + "</settings>");
            for (Node n = settings.getDocumentElement().getFirstChild(); n != null; n = n.getNextSibling()) step.appendChild(document.importNode(n, true));
            if (i == 0) { put(step, "filename", "${WORK_DIR}/" + inputFilename); put(step, "encoding", "UTF-8"); put(step, "lazy_conversion", "N"); put(step, "fields/field/name", "name"); }
            if (i == 1)
            {
                put(step, "compatible", "N"); put(step, "optimizationLevel", "9");
                put(step, "jsScripts", ""); put(step, "fields", "");
                put(step, "jsScripts/jsScript/jsScript_type", "0"); put(step, "jsScripts/jsScript/jsScript_name", "Synthetic");
                put(step, "jsScripts/jsScript/jsScript_script", "var greeting = name.toUpperCase() + \"!\";");
                for (var pair : Map.of("name", "greeting", "rename", "", "type", "String", "length", "-1", "precision", "-1", "replace", "N").entrySet()) put(step, "fields/field/" + pair.getKey(), pair.getValue());
            }
            if (i == 2)
                for (var pair : Map.of("file/name", "${WORK_DIR}/result", "file/extention", "csv", "file/do_not_open_new_file_init", "Y", "file/rename_file_name", "N", "separator", ",", "encoding", "UTF-8", "format", "UNIX", "enclosure", "").entrySet()) put(step, pair.getKey(), pair.getValue());
            if (i > 0)
            { Element hop = document.createElement("hop"); document.getElementsByTagName("order").item(0).appendChild(hop); put(hop, "from", names[i - 1]); put(hop, "to", names[i]); put(hop, "enabled", "Y"); }
        }
        return DataGovernanceKettleXml.serialize(document);
    }
    private static void put(Element parent, String path, String value)
    {
        Element current = parent;
        for (String name : path.split("/"))
        {
            Element found = null;
            for (Node n = current.getFirstChild(); n != null; n = n.getNextSibling()) if (n instanceof Element e && e.getTagName().equals(name)) { found = e; break; }
            if (found == null) { found = parent.getOwnerDocument().createElement(name); current.appendChild(found); }
            current = found;
        }
        current.setTextContent(value);
    }
}
