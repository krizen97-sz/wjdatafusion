package com.hm.manage.service.governance.hdi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Reads original public files and compiler metadata; this class never proxies a management server. */
@Component
public final class HdiCoreAssets {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MANIFEST_LIMIT = 8 * 1024 * 1024;
    private static final int PUBLIC_LIMIT = 32 * 1024 * 1024;
    private static final int ORIGINAL_LIMIT = 128 * 1024 * 1024;
    private static final Set<String> HOST_FILES = Set.of("task-host.html", "host.mjs", "native-task-host.mjs");
    private final HdiCoreProperties properties;

    public HdiCoreAssets(HdiCoreProperties properties) { this.properties = properties; }
    public record Asset(byte[] bytes, String contentType) { }
    private record Entry(String path, long bytes, String sha256) { }
    private record Index(Path root, Map<String, Entry> entries) { }

    /** Unknown static paths return null, allowing the platform controller to dispatch its own APIs. */
    public Asset read(String requestPath) {
        if ("/detl".equals(requestPath) || "/detl/".equals(requestPath)) return null;
        if (requestPath == null || !requestPath.startsWith("/detl/")) throw new HdiTaskFault(400, "任务资源路径无效");
        String relative = requestPath.substring(6);
        validateRelative(relative);
        if (HOST_FILES.contains(relative)) {
            return new Asset(readBounded(file(root(properties.getHostDir()), relative), PUBLIC_LIMIT), contentType(relative));
        }
        Index index = index();
        Entry entry = index.entries.get("public/" + relative);
        return entry == null ? null : new Asset(verified(index, entry, PUBLIC_LIMIT), contentType(relative));
    }

    /** Package-only metadata access cannot be routed by a browser into instance/ or task-service/. */
    byte[] readOriginal(String relative) {
        validateRelative(relative);
        Index index = index();
        Entry entry = index.entries.get(relative);
        if (entry == null) throw unavailable();
        return verified(index, entry, ORIGINAL_LIMIT);
    }

    Set<String> originalPaths() { return index().entries.keySet(); }

    String sourceArchiveSha256() {
        try {
            JsonNode manifest = JSON.readTree(readBounded(file(root(properties.getBundleDir()), "bundle-manifest.json"), MANIFEST_LIMIT));
            String hash = manifest.path("sourceArchiveSha256").asText();
            if (!hash.matches("[a-f0-9]{64}")) throw unavailable();
            return hash;
        } catch (IOException invalid) { throw unavailable(); }
    }

    private Index index() {
        Path root = root(properties.getBundleDir());
        try {
            JsonNode doc = JSON.readTree(readBounded(file(root, "bundle-manifest.json"), MANIFEST_LIMIT));
            if (doc == null || !"original-detl".equals(doc.path("engine").asText()) || !doc.path("files").isArray()
                    || doc.path("files").size() > 10000) throw unavailable();
            Map<String, Entry> entries = new LinkedHashMap<>();
            for (JsonNode item : doc.path("files")) {
                String name = item.path("path").asText(), hash = item.path("sha256").asText();
                validateRelative(name);
                if (!hash.matches("[a-f0-9]{64}") || !item.path("bytes").isIntegralNumber() || !item.path("bytes").canConvertToLong()
                        || item.path("bytes").longValue() < 0 || entries.containsKey(name)) throw unavailable();
                entries.put(name, new Entry(name, item.path("bytes").longValue(), hash));
            }
            return new Index(root, Collections.unmodifiableMap(entries));
        } catch (IOException | IllegalArgumentException error) { throw unavailable(); }
    }

    private static byte[] verified(Index index, Entry entry, int limit) {
        if (entry.bytes > limit) throw new HdiTaskFault(413, "原任务资源超过读取限额");
        byte[] data = readBounded(file(index.root, entry.path), limit);
        try {
            if (data.length != entry.bytes || !entry.sha256.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data))))
                throw new HdiTaskFault(503, "原任务资源校验失败");
            return data;
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static Path root(String configured) {
        try {
            if (configured == null || configured.isBlank()) throw unavailable();
            Path root = Path.of(configured).toAbsolutePath().normalize();
            for (Path part = root; part != null; part = part.getParent()) if (Files.isSymbolicLink(part)) throw unavailable();
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) throw unavailable();
            return root;
        } catch (IllegalArgumentException error) { throw unavailable(); }
    }

    private static Path file(Path root, String relative) {
        validateRelative(relative);
        Path current = root;
        for (String part : relative.split("/")) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) throw unavailable();
        }
        if (!current.normalize().startsWith(root) || !Files.isRegularFile(current, LinkOption.NOFOLLOW_LINKS)) throw unavailable();
        return current;
    }

    private static void validateRelative(String relative) {
        if (relative == null || relative.isEmpty() || relative.length() > 2048 || relative.startsWith("/")
                || relative.indexOf('\\') >= 0 || relative.indexOf('%') >= 0 || relative.indexOf(':') >= 0
                || relative.indexOf('?') >= 0 || relative.indexOf('#') >= 0 || relative.indexOf(';') >= 0
                || relative.chars().anyMatch(Character::isISOControl)) throw new HdiTaskFault(400, "任务资源路径无效");
        for (String part : relative.split("/", -1)) if (part.isEmpty() || part.equals(".") || part.equals(".."))
            throw new HdiTaskFault(400, "任务资源路径无效");
    }

    private static byte[] readBounded(Path path, int limit) {
        try {
            if (Files.size(path) > limit) throw new HdiTaskFault(413, "原任务资源超过读取限额");
            try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
                byte[] data = input.readNBytes(limit + 1);
                if (data.length > limit) throw new HdiTaskFault(413, "原任务资源超过读取限额");
                return data;
            }
        } catch (IOException error) { throw unavailable(); }
    }

    private static HdiTaskFault unavailable() { return new HdiTaskFault(503, "原任务资源或校验清单不可用"); }
    private static String contentType(String name) {
        return switch (name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT)) {
            case "js", "mjs" -> "text/javascript; charset=UTF-8";
            case "html" -> "text/html; charset=UTF-8";
            case "css" -> "text/css; charset=UTF-8";
            case "json", "map" -> "application/json; charset=UTF-8";
            case "txt" -> "text/plain; charset=UTF-8";
            case "svg" -> "image/svg+xml";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "ico" -> "image/x-icon";
            case "woff" -> "font/woff";
            case "woff2" -> "font/woff2";
            case "ttf" -> "font/ttf";
            case "wasm" -> "application/wasm";
            default -> "application/octet-stream";
        };
    }
}
