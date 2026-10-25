package com.hm.manage.service.governance.hdi;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** Reads the separately built, explicitly platform-owned MySQL JDBC adaptation.
 * Never loads its classes, changes the original bundle, or exposes these files over HTTP.
 */
final class HdiCorePlatformAdapters {
    private static final ObjectMapper JSON = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final List<String> NAMES = List.of("mysqlInput", "mysqlOutput");
    private static final String JAR = "lib/rynew-mysql-jdbc-adapter.jar";
    private static final Set<String> ORIGINS = Set.of("platform-build", "platform-registration", "platform-source", "platform-audit");
    private static final int MAX_FILE = 8 * 1024 * 1024;
    private final HdiCoreProperties properties;
    private final HdiCoreAssets originals;
    record Registration(String name, String folder, JsonNode definition, Properties properties) { }

    HdiCorePlatformAdapters(HdiCoreProperties properties, HdiCoreAssets originals) {
        this.properties = properties; this.originals = originals;
    }

    List<Registration> registrations() {
        String configured = properties.getPlatformAdaptersDir();
        String expected = properties.getPlatformAdaptersManifestSha256();
        if (configured == null || configured.isBlank()) {
            if (expected != null && !expected.isBlank()) throw invalid();
            return List.of();
        }
        if (expected == null || !expected.matches("[a-f0-9]{64}")) throw invalid();
        try {
            Path root = Path.of(configured).toAbsolutePath().normalize();
            for (Path parent = root; parent != null; parent = parent.getParent()) if (Files.isSymbolicLink(parent)) throw invalid();
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) throw invalid();
            byte[] manifestBytes = read(root, "platform-adapter-manifest.json", 256 * 1024);
            if (!sha(manifestBytes).equals(expected)) throw invalid();
            JsonNode manifest = JSON.readTree(manifestBytes);
            if (!manifest.path("schemaVersion").isIntegralNumber() || manifest.path("schemaVersion").asInt() != 1 || !"rynew-hdi-jdbc-platform-adapters".equals(manifest.path("kind").asText())
                    || !originals.sourceArchiveSha256().equals(manifest.path("sourceArchiveSha256").asText())
                    || !manifest.path("files").isArray() || manifest.path("files").isEmpty() || manifest.path("files").size() > 128
                    || !manifest.path("plugins").isArray() || manifest.path("plugins").size() != 2) throw invalid();
            Map<String, byte[]> files = new LinkedHashMap<>();
            Map<String, String> origins = new LinkedHashMap<>();
            long total = 0;
            for (JsonNode entry : manifest.path("files")) {
                String path = entry.path("path").asText(), origin = entry.path("origin").asText();
                if (!ORIGINS.contains(origin) || files.containsKey(path)) throw invalid();
                byte[] bytes = read(root, path, MAX_FILE);
                verify(entry, bytes);
                total += bytes.length; if (total > 32L * 1024 * 1024) throw invalid();
                files.put(path, bytes); origins.put(path, origin);
            }
            if (!"platform-build".equals(origins.get(JAR))) throw invalid();
            Map<String, Registration> registered = new LinkedHashMap<>();
            for (JsonNode plugin : manifest.path("plugins")) {
                String name = plugin.path("adapterName").asText();
                if (!NAMES.contains(name) || registered.containsKey(name)) throw invalid();
                String direction = name.equals("mysqlInput") ? "input" : "output";
                String type = "com.rynew.hdi.adapter." + (name.equals("mysqlInput") ? "MysqlInput" : "MysqlOutput");
                String folder = "plugins/" + name, descriptor = folder + "/etl_plugin.json", propertyFile = folder + "/adapter.properties";
                if (!"2.0.0".equals(plugin.path("adapterVersion").asText()) || !direction.equals(plugin.path("adapterType").asText())
                        || !plugin.path("dbType").isIntegralNumber() || plugin.path("dbType").asInt() != 2
                        || !type.equals(plugin.path("className").asText()) || !JAR.equals(plugin.path("jar").asText())
                        || !descriptor.equals(plugin.path("descriptor").asText()) || !propertyFile.equals(plugin.path("properties").asText())
                        || !"platform-registration".equals(origins.get(descriptor)) || !"platform-registration".equals(origins.get(propertyFile))) throw invalid();
                JsonNode definition = JSON.readTree(files.get(descriptor));
                Properties props = new Properties(); props.load(new ByteArrayInputStream(files.get(propertyFile)));
                for (String field : List.of("adapterName", "adapterType", "adapterDisplayName", "version", "className", "adapterDatabaseType"))
                    if (!definition.path(field).isTextual() || !definition.path(field).asText().equals(props.getProperty(field))) throw invalid();
                if (!name.equals(definition.path("adapterName").asText()) || !direction.equals(definition.path("adapterType").asText())
                        || !type.equals(definition.path("className").asText()) || !"2.0.0".equals(definition.path("version").asText())
                        || !"MySql".equals(definition.path("adapterDatabaseType").asText()) || !"java".equals(definition.path("adapterLanguge").asText())
                        || !"java".equals(props.getProperty("adapterLanguage")) || !definition.path("adapterParams").isArray()) throw invalid();
                JsonNode dependencies = plugin.path("dependencies");
                if (!dependencies.isArray() || dependencies.isEmpty() || dependencies.size() > 128) throw invalid();
                Set<String> targets = new java.util.HashSet<>(); boolean basePresent = false;
                String base = "etl-jdbc" + (name.equals("mysqlInput") ? "Input" : "Output") + "-2.0.0.jar";
                for (JsonNode dependency : dependencies) {
                    String path = dependency.path("bundlePath").asText(), target = dependency.path("target").asText();
                    if ((!path.startsWith("instance/") && !path.startsWith("task-service/lib/")) || !path.endsWith(".jar") || !target.equals("lib/" + path.substring(path.lastIndexOf('/') + 1))
                            || !targets.add(target)) throw invalid();
                    byte[] original = originals.readOriginal(path); verify(dependency, original);
                    if (target.equals("lib/" + base)) basePresent = true;
                }
                if (!basePresent) throw invalid();
                registered.put(name, new Registration(name, folder, definition, props));
            }
            List<Registration> result = new ArrayList<>();
            for (String name : NAMES) { if (!registered.containsKey(name)) throw invalid(); result.add(registered.get(name)); }
            return List.copyOf(result);
        } catch (IOException | IllegalArgumentException invalid) { throw invalid(); }
    }

    private static byte[] read(Path root, String relative, int max) throws IOException {
        if (relative == null || relative.isEmpty() || relative.length() > 512 || relative.startsWith("/") || relative.contains("\\")
                || relative.contains(":") || relative.contains("%") || relative.chars().anyMatch(Character::isISOControl)) throw invalid();
        Path file = root;
        for (String part : relative.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) throw invalid();
            file = file.resolve(part); if (Files.isSymbolicLink(file)) throw invalid();
        }
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > max) throw invalid();
        try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(max + 1); if (bytes.length > max) throw invalid(); return bytes;
        }
    }

    private static void verify(JsonNode entry, byte[] bytes) {
        if (!entry.path("bytes").isIntegralNumber() || !entry.path("bytes").canConvertToLong() || entry.path("bytes").asLong() != bytes.length
                || !entry.path("sha256").asText().equals(sha(bytes))) throw invalid();
    }
    private static String sha(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static HdiTaskFault invalid() { return new HdiTaskFault(503, "MySQL平台适配组件或原依赖校验失败"); }
}
