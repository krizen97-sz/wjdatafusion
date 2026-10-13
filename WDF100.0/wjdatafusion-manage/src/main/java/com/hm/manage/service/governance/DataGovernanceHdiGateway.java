package com.hm.manage.service.governance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hm.common.core.domain.model.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import static com.hm.manage.service.governance.DataGovernanceHdiSessions.*;

/** Same-origin transport boundary; original task responses are never rewritten as RuoYi responses. */
@Component
public class DataGovernanceHdiGateway {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> HOST_FILES = Set.of("task-host.html", "host.mjs", "native-task-host.mjs");
    /** Verified original task API call sites and matching FlowController query methods, not verb heuristics. */
    private static final Set<String> READ_ONLY = Set.of(
        "GET /", "GET /flow/getRunningStatus", "GET /flow/getFlowVisualState",
        "GET /flow/getFilterOperator", "GET /flow/getFrequencyEnums", "GET /flow/getTaskRunningSchedule",
        "POST /flow/findFlowPage", "POST /flow/findFlowExecRecordPage", "POST /flow/findFlowModuleExecRecordPage",
        "GET /globalConfig/instanceManage/findAllInstance",
        "GET /globalConfig/instanceManage/findAvailableInstances",
        "GET /globalConfig/groupAdapterManage/getAdapterTypeOptions");
    private static final Pattern READ_ID = Pattern.compile("^/flow/(?:getFlowById|getFlowVisualById)/[0-9]+$");
    private static final List<String> TASK_SCOPES = List.of("/flow/", "/flowvisual/", "/flowtemplate/", "/flownode/", "/flowlog/");
    private static final Set<String> REQUEST_HEADERS = Set.of("accept", "content-type", "accept-language", "range", "x-requested-with", "workspace", "projectcode", "origin", "referer");
    private static final Set<String> RESPONSE_HEADERS = Set.of("content-type", "content-disposition", "content-encoding", "content-range", "accept-ranges", "traceid", "bic-auth", "is-refresh", "in-login", "x-csrf-token", "x-xsrf-token");
    private static final Pattern CSRF_HEADER = Pattern.compile("<meta\\s+content=[\"']([^\"']+)[\"']\\s+name=[\"']_csrf_header[\"']\\s*/?>", Pattern.CASE_INSENSITIVE);
    private final DataGovernanceHdiProperties properties;
    private volatile AssetIndex assetIndex;

    public DataGovernanceHdiGateway(DataGovernanceHdiProperties properties) { this.properties = properties; }

    public void handle(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Session session = (Session) request.getAttribute(REQUEST_SESSION);
        LoginUser user = (LoginUser) request.getAttribute(REQUEST_USER);
        if (session == null || user == null) throw new Failure(401, "任务会话未认证");
        String path = path(request);
        if (path.equals("/_platform/context")) {
            if (!request.getMethod().equals("GET")) throw new Failure(405, "不支持的请求方法");
            json(response, 200, Map.of("code", "0", "data", session.context()));
            return;
        }
        Asset asset = asset(path);
        if (asset != null) {
            if (!Set.of("GET", "HEAD").contains(request.getMethod())) throw new Failure(405, "静态资源仅支持读取");
            byte[] bytes = readAsset(asset);
            noCache(response);
            response.setContentType(asset.contentType);
            response.setContentLength(bytes.length);
            if (!request.getMethod().equals("HEAD")) response.getOutputStream().write(bytes);
            return;
        }
        authorizeApi(request.getMethod(), path, user);
        bindNativeScope(request, session);
        proxy(request, response, session, path);
    }

    static String path(HttpServletRequest request) {
        String raw = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && raw.startsWith(context)) raw = raw.substring(context.length());
        if (raw.equals("/detl")) return "/";
        if (!raw.startsWith("/detl/") || raw.length() > 2048 || raw.indexOf('%') >= 0 || raw.indexOf('\\') >= 0
                || raw.indexOf(';') >= 0 || raw.indexOf('?') >= 0 || raw.indexOf('#') >= 0 || raw.contains("//")
                || raw.chars().anyMatch(Character::isISOControl)) throw new Failure(400, "任务路径无效");
        String relative = raw.substring("/detl".length());
        for (String part : relative.split("/")) if (part.equals(".") || part.equals("..")) throw new Failure(400, "任务路径无效");
        return relative;
    }

    static void authorizeApi(String method, String path, LoginUser user) {
        require(user, "governance:flow:list");
        String verb = method.equals("HEAD") ? "GET" : method;
        if (READ_ONLY.contains(verb + " " + path) || (verb.equals("GET") && READ_ID.matcher(path).matches())) return;
        // getByProduct is a permission-initialization GET that grants native edit access.
        // No workspace endpoint is exposed until original workspace/permission provisioning is verified.
        if (path.startsWith("/workspace/")) throw new Failure(403, "原工作区尚未完成受控初始化，不能自动授予权限");
        String lower = path.toLowerCase(Locale.ROOT);
        if (!Set.of("GET", "POST", "PUT", "DELETE").contains(verb)
                || TASK_SCOPES.stream().noneMatch(path::startsWith)
                || lower.matches(".*(?:/auth(?:/|$)|/user(?:/|$)|/system(?:/|$)|license|licence|/session(?:/|$)).*"))
            throw new Failure(403, "该原接口尚未纳入已核验的任务范围");
        // Several original mutations are GET (start/stop/lock/saveFlowFile). All need all three grants.
        require(user, "governance:flow:edit");
        require(user, "governance:flow:test");
    }

    /** Scope comes only from the signed server exchange. Browser headers never expand that scope. */
    private static void bindNativeScope(HttpServletRequest request, Session session) {
        String workspace = singleHeader(request, "Workspace");
        if (workspace != null && !workspace.equals(scopeValue(session.context().get("workspaceId"))))
            throw new Failure(403, "任务工作区与平台会话映射不一致");
        String project = singleHeader(request, "projectCode");
        if (project == null) return;
        boolean allowed = project.equals(scopeValue(session.context().get("projectCode")));
        Object projects = session.context().get("projectList");
        if (!allowed && projects instanceof List<?> list) {
            for (Object item : list) if (item instanceof Map<?, ?> map
                    && project.equals(scopeValue(map.get("projectCode")))) { allowed = true; break; }
        }
        if (!allowed) throw new Failure(403, "任务项目不在平台会话允许范围内");
    }
    private static String scopeValue(Object value) {
        if (!(value instanceof String || value instanceof Number)) return null;
        String text = value.toString();
        return text.isBlank() || text.length() > 256 ? null : text;
    }
    private static String singleHeader(HttpServletRequest request, String name) {
        var values = request.getHeaders(name);
        if (values == null || !values.hasMoreElements()) return null;
        String value = values.nextElement();
        if (values.hasMoreElements() || value.isBlank() || value.length() > 256 || !safeHeader(value))
            throw new Failure(400, "任务范围请求头无效");
        return value;
    }

    private void proxy(HttpServletRequest request, HttpServletResponse response, Session session, String path) throws IOException {
        int maxRequest = bounded(properties.getMaxRequestBytes(), 1024, 32 * 1024 * 1024);
        int maxResponse = bounded(properties.getMaxResponseBytes(), 1024, 64 * 1024 * 1024);
        if (request.getContentLengthLong() > maxRequest) throw new Failure(413, "任务请求或上传文件超过网关限制");
        byte[] body = request.getInputStream().readNBytes(maxRequest + 1);
        if (body.length > maxRequest) throw new Failure(413, "任务请求或上传文件超过网关限制");
        if (body.length > 0 && Set.of("GET", "HEAD").contains(request.getMethod())) throw new Failure(400, "读取请求不能携带请求体");
        String query = request.getQueryString();
        if (query != null && (query.length() > 16384 || query.chars().anyMatch(Character::isISOControl) || query.contains("#")))
            throw new Failure(400, "任务查询参数无效");
        URI target;
        try {
            URI base = DataGovernanceHdiClient.localServiceUri(properties.getTaskServiceUrl());
            target = URI.create(base.getScheme() + "://" + base.getRawAuthority() + "/detl" + path + (query == null ? "" : "?" + query));
        } catch (IllegalArgumentException | IOException error) { throw new Failure(503, "原任务服务地址配置无效"); }
        HttpURLConnection upstream = (HttpURLConnection) target.toURL().openConnection(Proxy.NO_PROXY);
        try {
            upstream.setConnectTimeout(bounded(properties.getConnectTimeoutMs(), 100, 15000));
            upstream.setReadTimeout(bounded(properties.getReadTimeoutMs(), 100, 120000));
            upstream.setInstanceFollowRedirects(false);
            upstream.setRequestMethod(request.getMethod());
            upstream.setUseCaches(false);
            for (String name : REQUEST_HEADERS) copyRequestHeader(request, upstream, name);
            copyRequestHeader(request, upstream, session.csrfHeader());
            if (!session.csrfHeader().equalsIgnoreCase("X-XSRF-TOKEN")) copyRequestHeader(request, upstream, "X-XSRF-TOKEN");
            String nativeCookies = session.cookieHeader(target);
            if (nativeCookies.isEmpty()) throw new Failure(401, "原任务会话已失效，请从平台重新进入");
            upstream.setRequestProperty("Cookie", nativeCookies);
            if (body.length > 0) {
                upstream.setDoOutput(true);
                upstream.setFixedLengthStreamingMode(body.length);
                try (var out = upstream.getOutputStream()) { out.write(body); }
            }
            int status = upstream.getResponseCode();
            session.updateCookies(target, upstream.getHeaderFields());
            if (status >= 300 && status < 400) throw new Failure(502, "原任务服务返回了不允许的重定向");
            long announced = upstream.getContentLengthLong();
            if (announced > maxResponse) throw new Failure(502, "原任务服务响应超过网关限制");
            byte[] bytes;
            try (InputStream input = status >= 400 ? upstream.getErrorStream() : upstream.getInputStream()) {
                bytes = input == null ? new byte[0] : input.readNBytes(maxResponse + 1);
            }
            if (bytes.length > maxResponse) throw new Failure(502, "原任务服务响应超过网关限制");
            if (path.equals("/") && status == 200 && upstream.getHeaderField("Content-Encoding") == null) {
                Matcher match = CSRF_HEADER.matcher(new String(bytes, StandardCharsets.UTF_8));
                if (match.find()) session.csrfHeader(match.group(1)); // Forward only what the original bootstrap declares.
            }
            response.setStatus(status);
            noCache(response);
            for (var header : upstream.getHeaderFields().entrySet()) if (header.getKey() != null
                    && RESPONSE_HEADERS.contains(header.getKey().toLowerCase(Locale.ROOT))) {
                for (String value : header.getValue()) if (safeHeader(value)) response.addHeader(header.getKey(), value);
            }
            if (request.getMethod().equals("HEAD") && announced >= 0) response.setContentLengthLong(announced);
            else response.setContentLength(bytes.length);
            if (!request.getMethod().equals("HEAD")) response.getOutputStream().write(bytes);
        } catch (Failure failure) { throw failure; }
        catch (IOException | IllegalArgumentException error) { throw new Failure(502, "原任务服务请求失败"); }
        finally { upstream.disconnect(); }
    }
    private static void copyRequestHeader(HttpServletRequest request, HttpURLConnection target, String name) {
        String value = request.getHeader(name);
        if (value != null) {
            if (!safeHeader(value)) throw new Failure(400, "任务请求头无效");
            target.setRequestProperty(name, value);
        }
    }
    private static boolean safeHeader(String value) { return value != null && value.length() <= 8192 && value.chars().noneMatch(ch -> ch < 32 && ch != 9 || ch == 127); }

    private record Asset(Path root, String relative, String sha256, String contentType) {}
    private record AssetIndex(Path root, Map<String, Asset> files) {}
    private Asset asset(String requestPath) {
        if (requestPath.equals("/")) return null; // Native bootstrap HTML includes the genuine CSRF meta fields.
        String relative = requestPath.substring(1);
        if (HOST_FILES.contains(relative)) {
            Path host = checkedRoot(properties.getHostDir());
            return new Asset(host, relative, null, contentType(relative));
        }
        Path root = checkedRoot(properties.getBundleDir());
        AssetIndex index = assetIndex;
        if (index == null || !index.root.equals(root)) {
            synchronized (this) {
                index = assetIndex;
                if (index == null || !index.root.equals(root)) assetIndex = index = loadIndex(root);
            }
        }
        return index.files.get(relative);
    }
    private AssetIndex loadIndex(Path root) {
        try {
            Path manifest = file(root, "bundle-manifest.json");
            if (Files.size(manifest) > 8 * 1024 * 1024) throw new IOException("Manifest limit");
            JsonNode document = JSON.readTree(Files.readAllBytes(manifest));
            if (!"original-detl".equals(document.path("engine").asText()) || !document.path("files").isArray()
                    || document.path("files").size() > 10000) throw new IOException("Manifest format");
            Map<String, Asset> assets = new LinkedHashMap<>();
            for (JsonNode entry : document.path("files")) {
                String name = entry.path("path").asText(), sha = entry.path("sha256").asText();
                if (!name.startsWith("public/")) continue;
                String relative = name.substring("public/".length());
                if (relative.isEmpty() || !sha.matches("[a-f0-9]{64}") || assets.containsKey(relative)) throw new IOException("Invalid asset inventory");
                file(root, name);
                assets.put(relative, new Asset(root, name, sha, contentType(relative)));
            }
            return new AssetIndex(root, java.util.Collections.unmodifiableMap(new LinkedHashMap<>(assets)));
        } catch (IOException | RuntimeException error) { throw new Failure(503, "原任务资源清单无效或不可用"); }
    }
    private byte[] readAsset(Asset asset) throws IOException {
        try {
            Path file = file(asset.root, asset.relative);
            int limit = bounded(properties.getMaxResponseBytes(), 1024, 64 * 1024 * 1024);
            if (Files.size(file) > limit) throw new Failure(413, "任务静态资源超过网关限制");
            byte[] bytes;
            try (InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) { bytes = input.readNBytes(limit + 1); }
            if (bytes.length > limit) throw new Failure(413, "任务静态资源超过网关限制");
            if (asset.sha256 != null && !asset.sha256.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))))
                throw new Failure(503, "原任务资源校验失败");
            return bytes;
        } catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
        catch (IOException error) { throw new Failure(404, "任务资源不存在"); }
    }
    private static Path checkedRoot(String configured) {
        try {
            if (configured == null || configured.isBlank()) throw new IOException("Root not configured");
            Path root = Path.of(configured).toAbsolutePath().normalize();
            for (Path part = root; part != null; part = part.getParent()) if (Files.isSymbolicLink(part)) throw new IOException("Symbolic root");
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Missing root");
            return root;
        } catch (IOException | RuntimeException error) { throw new Failure(503, "原任务资源目录配置无效"); }
    }
    private static Path file(Path root, String relative) throws IOException {
        if (relative == null || relative.isEmpty() || relative.startsWith("/") || relative.contains("\\")
                || relative.indexOf('%') >= 0 || relative.indexOf(':') >= 0 || relative.chars().anyMatch(Character::isISOControl))
            throw new IOException("Invalid path");
        Path target = root;
        for (String part : relative.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) throw new IOException("Invalid path segment");
            target = target.resolve(part);
            if (Files.isSymbolicLink(target)) throw new IOException("Symbolic artifact");
        }
        if (!target.normalize().startsWith(root) || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Missing artifact");
        return target;
    }
    private static String contentType(String path) {
        String suffix = path.substring(path.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return switch (suffix) {
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
    private static int bounded(int value, int min, int max) { if (value < min || value > max) throw new Failure(503, "任务网关限额配置无效"); return value; }
    public static void noCache(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store"); response.setHeader("Pragma", "no-cache");
        response.setHeader("X-Content-Type-Options", "nosniff");
    }
    public static void json(HttpServletResponse response, int status, Object body) throws IOException {
        byte[] bytes = JSON.writeValueAsBytes(body);
        response.setStatus(status); noCache(response); response.setContentType("application/json; charset=UTF-8");
        response.setContentLength(bytes.length); response.getOutputStream().write(bytes);
    }
    public static void failure(HttpServletResponse response, Failure error) throws IOException {
        json(response, error.status(), Map.of("code", Integer.toString(error.status()), "message", error.getMessage()));
    }
}
