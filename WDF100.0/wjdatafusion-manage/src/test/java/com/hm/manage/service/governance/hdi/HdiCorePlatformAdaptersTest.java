package com.hm.manage.service.governance.hdi;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/** File-integrity/identity boundaries, not proof that the native JDBC engine ran. */
class HdiCorePlatformAdaptersTest {
    @TempDir Path temp;
    HdiCoreAssetsTest.Fixtures original;
    Path extension;
    ObjectNode manifest;
    HdiCorePlatformAdapters reader;

    @BeforeEach void setup() throws Exception {
        temp = temp.toRealPath();
        original = new HdiCoreAssetsTest.Fixtures(temp);
        extension = temp.resolve("platform-adapters"); Files.createDirectories(extension);
        original.properties.setPlatformAdaptersDir(extension.toString());
        List<Map<String, Object>> files = new ArrayList<>(), plugins = new ArrayList<>();
        String jar = "lib/rynew-mysql-jdbc-adapter.jar";
        put(files, jar, new byte[]{1, 2, 3}, "platform-build");
        for (String direction : List.of("Input", "Output")) {
            String name = "mysql" + direction, folder = "plugins/" + name, type = direction.toLowerCase();
            String implementation = "com.rynew.hdi.adapter.Mysql" + direction;
            Map<String, Object> descriptor = new LinkedHashMap<>();
            descriptor.put("adapterName", name); descriptor.put("adapterType", type);
            descriptor.put("adapterDisplayName", "MySQL原JDBC适配"); descriptor.put("version", "2.0.0");
            descriptor.put("className", implementation); descriptor.put("adapterDatabaseType", "MySql");
            descriptor.put("adapterLanguge", "java"); descriptor.put("adapterParams", List.of());
            put(files, folder + "/etl_plugin.json", HdiProtocol.JSON.writeValueAsBytes(descriptor), "platform-registration");
            Properties properties = new Properties();
            descriptor.forEach((key, value) -> { if (value instanceof String) properties.setProperty(key, value.toString()); });
            properties.setProperty("adapterLanguage", "java");
            var out = new java.io.ByteArrayOutputStream(); properties.store(out, "platform adaptation fixture");
            put(files, folder + "/adapter.properties", out.toByteArray(), "platform-registration");
            String dependency = "instance/etl-postgresql" + direction + "-2.0.0/lib/etl-jdbc" + direction + "-2.0.0.jar";
            byte[] dependencyBytes = ("original " + direction).getBytes(StandardCharsets.UTF_8);
            original.put(dependency, dependencyBytes);
            Map<String, Object> plugin = new LinkedHashMap<>();
            plugin.put("adapterName", name); plugin.put("adapterVersion", "2.0.0"); plugin.put("adapterType", type);
            plugin.put("dbType", 2); plugin.put("className", implementation); plugin.put("descriptor", folder + "/etl_plugin.json");
            plugin.put("properties", folder + "/adapter.properties"); plugin.put("jar", jar);
            plugin.put("dependencies", List.of(Map.of("bundlePath", dependency, "target", "lib/etl-jdbc" + direction + "-2.0.0.jar",
                "sha256", sha(dependencyBytes), "bytes", dependencyBytes.length)));
            plugins.add(plugin);
        }
        original.manifest();
        Path originalManifest = original.bundle.resolve("bundle-manifest.json");
        ObjectNode originalJson = (ObjectNode)HdiProtocol.JSON.readTree(Files.readString(originalManifest));
        originalJson.put("sourceArchiveSha256", "a".repeat(64)); Files.writeString(originalManifest, originalJson.toString());
        manifest = HdiProtocol.JSON.valueToTree(Map.of("schemaVersion", 1, "kind", "rynew-hdi-jdbc-platform-adapters",
            "sourceArchiveSha256", "a".repeat(64), "files", files, "plugins", plugins));
        saveManifest(); reader = new HdiCorePlatformAdapters(original.properties, new HdiCoreAssets(original.properties));
    }

    @Test void emptyConfigurationDoesNotInventMysqlAndConfiguredFilesStayPrivate() {
        assertEquals(List.of("mysqlInput", "mysqlOutput"), reader.registrations().stream().map(HdiCorePlatformAdapters.Registration::name).toList());
        HdiCoreAssets assets = new HdiCoreAssets(original.properties);
        assertNull(assets.read("/detl/plugins/mysqlInput/etl_plugin.json"));
        assertNull(assets.read("/detl/lib/rynew-mysql-jdbc-adapter.jar"));
        original.properties.setPlatformAdaptersDir(""); original.properties.setPlatformAdaptersManifestSha256("");
        assertTrue(reader.registrations().isEmpty());
    }

    @Test void tamperedArtifactOrOriginalDependencyRejectsTheWholeRegistration() throws Exception {
        Files.write(extension.resolve("lib/rynew-mysql-jdbc-adapter.jar"), new byte[]{3, 2, 1});
        assertUnavailable();
        Files.write(extension.resolve("lib/rynew-mysql-jdbc-adapter.jar"), new byte[]{1, 2, 3});
        String dependency = manifest.path("plugins").get(0).path("dependencies").get(0).path("bundlePath").asText();
        Files.writeString(original.bundle.resolve(dependency), "changed"); assertUnavailable();
    }

    @Test void arbitraryClassesAndOriginalOriginLabelsCannotBeAdvertisedAsTheMysqlAdaptation() throws Exception {
        ((ObjectNode)manifest.path("plugins").get(0)).put("className", "unapproved.Runtime"); saveManifest(); assertUnavailable();
        ((ObjectNode)manifest.path("plugins").get(0)).put("className", "com.rynew.hdi.adapter.MysqlInput");
        ((ObjectNode)manifest.path("files").get(0)).put("origin", "original-detl"); saveManifest(); assertUnavailable();
    }

    @Test void duplicateEntriesWrongOriginalArchiveAndUnknownPluginNamesFailClosed() throws Exception {
        var files = (com.fasterxml.jackson.databind.node.ArrayNode)manifest.path("files"); files.add(files.get(0).deepCopy());
        saveManifest(); assertUnavailable(); files.remove(files.size() - 1);
        manifest.put("sourceArchiveSha256", "b".repeat(64)); saveManifest(); assertUnavailable();
        manifest.put("sourceArchiveSha256", "a".repeat(64));
        ((ObjectNode)manifest.path("plugins").get(1)).put("adapterName", "anotherPlugin"); saveManifest(); assertUnavailable();
    }

    @Test void symbolicArtifactsAndEscapingFileNamesCannotReadOutsideTheExtension() throws Exception {
        Path jar = extension.resolve("lib/rynew-mysql-jdbc-adapter.jar"), outside = temp.resolve("outside.jar");
        Files.write(outside, new byte[]{1, 2, 3}); Files.delete(jar); Files.createSymbolicLink(jar, outside);
        assertUnavailable(); Files.delete(jar); Files.write(jar, new byte[]{1, 2, 3});
        ((ObjectNode)manifest.path("files").get(0)).put("path", "../outside.jar"); saveManifest(); assertUnavailable();
    }

    @Test void replacingAReceiptCannotChangeTheConfiguredDefinitionOrOmitItsPin() throws Exception {
        manifest.put("buildNote", "replacement");
        Files.writeString(extension.resolve("platform-adapter-manifest.json"), manifest.toString());
        assertUnavailable();
        original.properties.setPlatformAdaptersManifestSha256(""); assertUnavailable();
    }

    @Test @EnabledIfSystemProperty(named = "hdi.platform.adapters", matches = ".+")
    void actualBuiltAdaptationUsesOriginalDependenciesAndOnlyTwoFixedMysqlClasses() {
        HdiCoreProperties properties = new HdiCoreProperties(); properties.setBundleDir(System.getProperty("hdi.core.bundle"));
        properties.setPlatformAdaptersDir(System.getProperty("hdi.platform.adapters"));
        properties.setPlatformAdaptersManifestSha256(System.getProperty("hdi.platform.adapters.sha256"));
        var registrations = new HdiCorePlatformAdapters(properties, new HdiCoreAssets(properties)).registrations();
        assertEquals(List.of("mysqlInput", "mysqlOutput"), registrations.stream().map(HdiCorePlatformAdapters.Registration::name).toList());
        assertTrue(registrations.stream().allMatch(item -> item.definition().path("adapterParams").size() > 0));
    }

    private void assertUnavailable() { assertEquals(503, assertThrows(HdiTaskFault.class, reader::registrations).status()); }
    private void saveManifest() throws Exception {
        byte[] bytes = manifest.toString().getBytes(StandardCharsets.UTF_8);
        Files.write(extension.resolve("platform-adapter-manifest.json"), bytes);
        original.properties.setPlatformAdaptersManifestSha256(sha(bytes));
    }
    private void put(List<Map<String, Object>> files, String name, byte[] bytes, String origin) throws Exception {
        Path file = extension.resolve(name); Files.createDirectories(file.getParent()); Files.write(file, bytes);
        files.add(Map.of("path", name, "bytes", bytes.length, "sha256", sha(bytes), "origin", origin));
    }
    private static String sha(byte[] data) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)); }
}
