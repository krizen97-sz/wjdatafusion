package com.hm.manage.service.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.hm.manage.service.governance.DataGovernanceKettleService.*;

/** Real attachments, import only. Never invokes native validation, execution or a network client. */
@EnabledIfSystemProperty(named="kettle.attachments", matches="true")
class DataGovernanceKettleAttachmentTest
{
    @TempDir Path temporary;
    @Test void actualLegacyGbkZipImportsPreserveAllXmlAndRejectBinaryVariantWithoutReplacement() throws Exception
    {
        Path input = Path.of(System.getProperty("kettle.attachmentsDir", "/Volumes/KINGSTON/datai"));
        DataGovernanceKettleProperties properties = new DataGovernanceKettleProperties(); properties.setStorageDir(temporary.toString());
        var worker = new DataGovernanceKettleApiTest.FakeWorker(properties);
        var service = new DataGovernanceKettleService(properties, DataGovernanceKettleApiTest.crypto(), worker);
        List<Map<String,Object>> evidence = new ArrayList<>(); Set<String> plugins = new TreeSet<>();
        try
        {
            for (String relative : List.of("普通过车/851987.zip", "普通过车/851988.zip", "违法数据/917552.zip"))
            {
                Path archive = input.resolve(relative); List<byte[]> originals = new ArrayList<>();
                try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive), Charset.forName("GB18030")))
                { ZipEntry entry; while ((entry = zip.getNextEntry()) != null) if (!entry.isDirectory()) originals.add(zip.readAllBytes()); }
                List<Summary> imported;
                try (InputStream stream = Files.newInputStream(archive)) { imported = service.imports(archive.getFileName().toString(), stream, 97); }
                assertEquals(originals.size(), imported.size());
                for (int i = 0; i < originals.size(); i++)
                {
                    Summary summary = imported.get(i); String original = DataGovernanceKettleXml.decode(Base64.getEncoder().encodeToString(originals.get(i)));
                    String originalMasked = DataGovernanceKettleXml.masked(DataGovernanceKettleXml.prepare(original, null).xml());
                    String returned = DataGovernanceKettleXml.decode((String)service.definition(summary.id(), 97).get("xmlBase64"));
                    assertTrue(canonical(originalMasked).equals(canonical(returned)), "Real imported XML changed structurally; contents intentionally not printed");
                    service.save(summary.id(), new DefinitionInput(summary.name(), 1L, DataGovernanceKettleXml.encode(returned)), 97);
                    String reread = DataGovernanceKettleXml.decode((String)service.definition(summary.id(), 97).get("xmlBase64"));
                    assertTrue(canonical(originalMasked).equals(canonical(reread)), "Real XML save/read roundtrip differs; contents intentionally not printed");
                    Document source = DataGovernanceKettleXml.parse(original); Document view = DataGovernanceKettleXml.parse(reread);
                    Map<String,Integer> types = new TreeMap<>(); NodeList steps = source.getElementsByTagName("step");
                    for (int j = 0; j < steps.getLength(); j++)
                    { String type = DataGovernanceKettleXml.child((Element)steps.item(j), "type"); plugins.add(type); types.merge(type, 1, Integer::sum); }
                    assertEquals(texts(source, "copies"), texts(view, "copies"));
                    for (String tag : List.of("condition", "send_true_to", "send_false_to", "evaluation", "unconditional", "case_value", "case_target_step"))
                        assertTrue(texts(source, tag).equals(texts(view, tag)), "Conditional branch data changed; values intentionally not printed");
                    int maskedSecrets = 0; NodeList all = view.getElementsByTagName("*");
                    for (int j = 0; j < all.getLength(); j++)
                    { Element e = (Element)all.item(j); if (e.hasAttribute("data-rynew-secret-id") && !e.getTextContent().isEmpty())
                      { assertTrue(e.getTextContent().startsWith("__RYNEW_SECRET_"), "Secret field was not masked"); maskedSecrets++; } }
                    evidence.add(Map.of("archive", archive.getFileName().toString(), "memberIndex", i, "kind", summary.kind(),
                        "steps", steps.getLength(), "entries", source.getElementsByTagName("entry").getLength(), "pluginCounts", types,
                        "copiesValues", texts(source, "copies"), "maskedSecretFields", maskedSecrets, "roundTripPreserved", true));
                }
            }
            int before = service.definitions(97).size(); Path binary = input.resolve("违法数据/917552 (1).zip");
            try (InputStream stream = Files.newInputStream(binary))
            { var error = assertThrows(com.hm.common.exception.ServiceException.class, () -> service.imports(binary.getFileName().toString(), stream, 97));
              assertTrue(error.getMessage().contains("二进制")); }
            assertEquals(before, service.definitions(97).size()); assertEquals(4, before); assertEquals(12, plugins.size());
            assertTrue(worker.calls.isEmpty());
            String output = System.getProperty("kettle.attachmentEvidence");
            if (output != null)
            {
                Path path = Path.of(output); Files.createDirectories(path.getParent());
                new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(path.toFile(), Map.of("imports", evidence,
                    "uniqueStepPlugins", plugins, "binaryVariantRejectedWithoutReplacement", true, "workerCalls", 0));
            }
        }
        finally { service.close(); }
    }
    private static List<String> texts(Document document, String tag)
    { List<String> values = new ArrayList<>(); NodeList nodes = document.getElementsByTagName(tag); for (int i = 0; i < nodes.getLength(); i++) values.add(nodes.item(i).getTextContent()); return values; }
    private static String canonical(String xml)
    {
        Document document = DataGovernanceKettleXml.parse(xml); NodeList nodes = document.getElementsByTagName("*");
        for (int i = 0; i < nodes.getLength(); i++)
        {
            Element element = (Element)nodes.item(i); List<String> remove = new ArrayList<>();
            for (int j = 0; j < element.getAttributes().getLength(); j++)
            { Node attr = element.getAttributes().item(j); if (attr.getNodeName().startsWith("data-rynew-")) remove.add(attr.getNodeName()); }
            remove.forEach(element::removeAttribute);
        }
        return DataGovernanceKettleXml.serialize(document).replaceAll("__RYNEW_SECRET_[0-9a-fA-F-]{36}__", "[SECRET]");
    }
}
