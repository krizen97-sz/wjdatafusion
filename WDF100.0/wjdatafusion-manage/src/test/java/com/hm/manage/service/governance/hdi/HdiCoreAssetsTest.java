package com.hm.manage.service.governance.hdi;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HdiCoreAssetsTest {
    @TempDir Path temp;
    Fixtures fixture;
    @BeforeEach void setup() throws Exception { fixture = new Fixtures(temp); }

    @Test void onlyManifestPublicFilesAndThreeOwnedHostFilesAreRoutable() throws Exception {
        fixture.put("public/static/js/original.js", "export const 原件 = true".getBytes(StandardCharsets.UTF_8));
        fixture.put("task-service/config/private.properties", "private".getBytes(StandardCharsets.UTF_8));
        fixture.manifest();
        Files.writeString(fixture.host.resolve("task-host.html"), "<html>host</html>");
        Files.writeString(fixture.host.resolve("unlisted.html"), "must not expose");
        HdiCoreAssets assets = new HdiCoreAssets(fixture.properties);
        assertEquals("text/javascript; charset=UTF-8", assets.read("/detl/static/js/original.js").contentType());
        assertTrue(new String(assets.read("/detl/static/js/original.js").bytes(), StandardCharsets.UTF_8).contains("原件"));
        assertEquals("text/html; charset=UTF-8", assets.read("/detl/task-host.html").contentType());
        assertNull(assets.read("/detl/task-service/config/private.properties"));
        assertNull(assets.read("/detl/unlisted.html"));
        assertNull(assets.read("/detl/flow/findFlowPage"));
        assertNull(assets.read("/detl/"));
    }

    @Test void mutatedBytesOrDeclaredSizeFailClosedOnEveryRead() throws Exception {
        fixture.put("public/static/a.js", "first".getBytes(StandardCharsets.UTF_8)); fixture.manifest();
        HdiCoreAssets assets = new HdiCoreAssets(fixture.properties);
        assets.read("/detl/static/a.js");
        Files.writeString(fixture.bundle.resolve("public/static/a.js"), "other");
        assertEquals(503, assertThrows(HdiTaskFault.class, () -> assets.read("/detl/static/a.js")).status());
        fixture.entries.get(0).put("bytes", 1); fixture.manifest();
        assertEquals(503, assertThrows(HdiTaskFault.class, () -> assets.read("/detl/static/a.js")).status());
    }

    @Test void traversalEncodingAndSymbolicFilesCannotCrossTheBundleBoundary() throws Exception {
        fixture.put("public/static/a.js", new byte[]{1}); fixture.manifest();
        HdiCoreAssets assets = new HdiCoreAssets(fixture.properties);
        for (String path : List.of("/detl/../private", "/detl/%2e%2e/private", "/detl/static//a.js", "/detl/static/a.js;v=1", "/detl/static/a.js?x=1", "/detl/static\\a.js"))
            assertEquals(400, assertThrows(HdiTaskFault.class, () -> assets.read(path)).status());
        Files.delete(fixture.bundle.resolve("public/static/a.js"));
        Path external = fixture.root.resolve("outside.js"); Files.write(external, new byte[]{1});
        Files.createSymbolicLink(fixture.bundle.resolve("public/static/a.js"), external);
        assertEquals(503, assertThrows(HdiTaskFault.class, () -> assets.read("/detl/static/a.js")).status());
        Path alias = fixture.root.resolve("bundle-alias"); Files.createSymbolicLink(alias, fixture.bundle);
        fixture.properties.setBundleDir(alias.toString());
        assertThrows(HdiTaskFault.class, () -> assets.read("/detl/static/a.js"));
    }

    @Test void duplicateAndMalformedManifestsAreRejectedWithoutReadingUnlistedMetadata() throws Exception {
        fixture.put("instance/config.json", new byte[]{1}); fixture.entries.add(new LinkedHashMap<>(fixture.entries.get(0))); fixture.manifest();
        HdiCoreAssets assets = new HdiCoreAssets(fixture.properties);
        assertThrows(HdiTaskFault.class, () -> assets.readOriginal("instance/config.json"));
        fixture.entries.remove(1); fixture.manifest();
        Files.writeString(fixture.bundle.resolve("unlisted.json"), "{}");
        assertThrows(HdiTaskFault.class, () -> assets.readOriginal("unlisted.json"));
        fixture.entries.get(0).put("bytes", 1.0); fixture.manifest();
        assertThrows(HdiTaskFault.class, () -> assets.readOriginal("instance/config.json"));
    }

    static final class Fixtures {
        static final ObjectMapper JSON = new ObjectMapper();
        final Path root, bundle, host;
        final HdiCoreProperties properties = new HdiCoreProperties();
        final List<Map<String, Object>> entries = new ArrayList<>();
        Fixtures(Path temp) throws Exception {
            root = temp.toRealPath(); bundle = root.resolve("bundle"); host = root.resolve("host");
            Files.createDirectories(bundle); Files.createDirectories(host);
            properties.setEnabled(true); properties.setBundleDir(bundle.toString()); properties.setHostDir(host.toString());
        }
        void put(String relative, byte[] data) throws Exception {
            Path path = bundle.resolve(relative); Files.createDirectories(path.getParent()); Files.write(path, data);
            Map<String, Object> entry = new LinkedHashMap<>(); entry.put("path", relative); entry.put("bytes", data.length);
            entry.put("sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data))); entries.add(entry);
        }
        void manifest() throws Exception { Files.writeString(bundle.resolve("bundle-manifest.json"), JSON.writeValueAsString(Map.of("engine", "original-detl", "files", entries))); }
    }
}
