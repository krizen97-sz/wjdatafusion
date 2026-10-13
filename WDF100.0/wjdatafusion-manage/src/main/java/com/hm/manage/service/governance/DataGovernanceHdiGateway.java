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
import java.net.URLDecoder;
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
import org.springframework.http.MediaType;
import static com.hm.manage.service.governance.DataGovernanceHdiSessions.*;

/** Same-origin transport boundary; original task responses are never rewritten as RuoYi responses. */
@Component
public class DataGovernanceHdiGateway {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> HOST_FILES = Set.of("task-host.html", "host.mjs", "native-task-host.mjs");
    /** Verified original task API call sites and matching FlowController query methods, not verb heuristics. */
    private static final Set<String> READ_ONLY = Set.of(
        "GET /", "GET /getLanguage", "GET /getPublicKey", "GET /getEtlUserInfo", "GET /workspace/listWorkspace",
        "GET /system/environment/getLicenseInfo", "GET /system/environment/getLicenseExpire",
        "GET /user/security/privilege/info", "GET /code/codeConfig/getDataTypeList",
        "GET /globalConfig/runningConfig/healthStatus",
        "GET /system/environment/getBase64VisualEnable", "GET /system/environment/getExpertMode", "GET /system/environment/getPortPool",
        "POST /globalConfig/felManager/query", "GET /globalConfig/dataSourceManage/getDataSourceExtendInfo",
        "GET /globalConfig/groupManage/findAllGroup", "GET /globalConfig/serverManage/findAllServers",
        "GET /globalConfig/tag/listTag", "GET /flow/getColumnDisplayName", "GET /flow/pageInterruptedFlow",
        "POST /folder/findFolder", "POST /flow/valid/getAdapterReadWritePath",
        "GET /flow/getFilterOperator", "GET /flow/getFrequencyEnums",
        "POST /flow/findFlowPage",
        "GET /globalConfig/instanceManage/findAllInstance",
        "GET /globalConfig/instanceManage/findAvailableInstances",
        "GET /globalConfig/groupAdapterManage/getAdapterTypeOptions",
        "GET /globalConfig/groupAdapterManage/listAdapters", "POST /globalConfig/paramManage/listParams");
    private static final Pattern READ_ID = Pattern.compile("^/flow/(?:getFlowById|getFlowVisualById)/([1-9][0-9]{0,18})$");
    private static final Set<String> READ_FLOW_QUERY = Set.of("GET /mount/getMountFlowVisual", "GET /flow/getRunningStatus",
        "POST /flow/getFlowVisualState", "POST /flow/findFlowExecRecordPage", "POST /flow/findFlowModuleExecRecordPage");
    private static final Pattern WRITE_ID = Pattern.compile("^/flow/(startFlow|startFlowV2|stopFlow|stopFlowV2|lockFlow|unlockFlow|debugFlow|pushFlowDataToZK)/([1-9][0-9]{0,18})$");
    private static final Pattern READ_WORKSPACE_ID = Pattern.compile("^/globalConfig/(groupAdapterManage/getOrderedGroupAdapters|instanceManage/findByGroupId)/([1-9][0-9]{0,18})$");
    private static final Set<String> REQUEST_HEADERS = Set.of("accept", "content-type", "accept-language", "range", "x-requested-with", "workspace", "projectcode", "origin", "referer");
    private static final Set<String> RESPONSE_HEADERS = Set.of("content-type", "content-disposition", "content-encoding", "content-range", "accept-ranges", "traceid", "bic-auth", "is-refresh", "in-login", "x-csrf-token", "x-xsrf-token");
    private static final Pattern CSRF_HEADER = Pattern.compile("<meta\\s+content=[\"']([^\"']+)[\"']\\s+name=[\"']_csrf_header[\"']\\s*/?>", Pattern.CASE_INSENSITIVE);
    private final DataGovernanceHdiProperties properties;
    private final DataGovernanceHdiClient client;
    private volatile AssetIndex assetIndex;

    public DataGovernanceHdiGateway(DataGovernanceHdiProperties properties, DataGovernanceHdiClient client) { this.properties = properties; this.client = client; }

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
        bindNativeScope(request, session, path);
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
        String verb = method; // API methods are exact; only static resources accept a generic HEAD request.
        if (READ_ONLY.contains(verb + " " + path) || READ_FLOW_QUERY.contains(verb + " " + path)
                || (verb.equals("GET") && READ_ID.matcher(path).matches())) return;
        Matcher workspaceRead = READ_WORKSPACE_ID.matcher(path);
        if (workspaceRead.matches() && verb.equals(workspaceRead.group(1).startsWith("groupAdapterManage/") ? "POST" : "GET")) return;
        if (path.startsWith("/workspace/")) throw new Failure(403, "原工作区尚未完成受控初始化，不能自动授予权限");
        if (verb.equals("POST") && (path.equals("/flow/saveFlowVisual/save") || path.equals("/flow/saveFlowVisual/push"))) {
            require(user, "governance:flow:edit");
            if (path.endsWith("/push")) require(user, "governance:flow:test");
            return;
        }
        Matcher mutation = WRITE_ID.matcher(path);
        if (!mutation.matches() || !verb.equals(Set.of("debugFlow", "pushFlowDataToZK").contains(mutation.group(1)) ? "POST" : "GET"))
            throw new Failure(403, "该原接口尚未纳入已核验的任务范围");
        // Exact verified operations only. There is deliberately no task-namespace write fallback.
        String operation = mutation.group(1);
        if (Set.of("lockFlow", "unlockFlow", "pushFlowDataToZK").contains(operation)) require(user, "governance:flow:edit");
        if (!Set.of("lockFlow", "unlockFlow").contains(operation)) require(user, "governance:flow:test");
    }

    /** Scope comes only from the signed server exchange. Browser headers never expand that scope. */
    private static void bindNativeScope(HttpServletRequest request, Session session, String path) {
        String workspace = singleHeader(request, "Workspace");
        if (workspace != null && !workspace.equals(scopeValue(session.context().get("workspaceId"))))
            throw new Failure(403, "任务工作区与平台会话映射不一致");
        requireProject(singleHeader(request, "projectCode"), session);
        if (isSave(path)) {
            if (workspace == null) throw new Failure(400, "原任务保存需要已映射的工作区");
            String project = singleHeader(request, "projectCode");
            if (scopeValue(session.context().get("projectCode")) == null) throw new Failure(503, "原任务主项目映射不可用");
            if (project != null && !project.equals(scopeValue(session.context().get("projectCode"))))
                throw new Failure(403, "原任务保存必须使用映射的主项目");
        }
        Matcher workspaceRead = READ_WORKSPACE_ID.matcher(path);
        if (workspaceRead.matches() && !workspaceRead.group(2).equals(scopeValue(session.context().get("workspaceId"))))
            throw new Failure(403, "任务接口工作区与平台会话映射不一致");
        if (Set.of("/globalConfig/instanceManage/findAllInstance", "/globalConfig/instanceManage/findAvailableInstances").contains(path)) {
            String group = queryValue(request, "groupId");
            if (group != null && !group.isBlank() && !group.equals(scopeValue(session.context().get("workspaceId"))))
                throw new Failure(403, "任务实例工作区与平台会话映射不一致");
        }
        if (path.equals("/folder/findFolder")) {
            // Original controller accepts projectCode in the query; a valid header alone cannot constrain it.
            String project = queryValue(request, "projectCode");
            if (project != null && !project.isBlank()) requireProject(project, session);
            String user = queryValue(request, "userId");
            Object nativeUser = session.context().get("user");
            String nativeId = nativeUser instanceof Map<?, ?> map ? scopeValue(map.get("id")) : null;
            if (user != null && !user.isBlank() && !user.equals(nativeId))
                throw new Failure(403, "任务目录用户与平台会话映射不一致");
        }
    }
    private static void requireProject(String project, Session session) {
        if (project == null) return;
        boolean allowed = project.equals(scopeValue(session.context().get("projectCode")));
        Object projects = session.context().get("projectList");
        if (!allowed && projects instanceof List<?> list) {
            for (Object item : list) if (item instanceof Map<?, ?> map
                    && project.equals(scopeValue(map.get("projectCode")))) { allowed = true; break; }
        }
        if (!allowed) throw new Failure(403, "任务项目不在平台会话允许范围内");
    }
    private static String queryValue(HttpServletRequest request, String name) {
        String raw = request.getQueryString();
        if (raw == null) return null;
        if (raw.length() > 16384) throw new Failure(400, "任务查询参数无效");
        String found = null;
        try {
            for (String parameter : raw.split("&", -1)) {
                String[] parts = parameter.split("=", 2);
                if (!URLDecoder.decode(parts[0], StandardCharsets.UTF_8).equals(name)) continue;
                if (found != null) throw new Failure(400, "任务范围参数不能重复");
                found = parts.length < 2 ? "" : URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
                if (found.length() > 256 || !safeHeader(found)) throw new Failure(400, "任务范围参数无效");
            }
        } catch (IllegalArgumentException error) { throw new Failure(400, "任务查询参数编码无效"); }
        return found;
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

    private static boolean isSave(String path) { return path.equals("/flow/saveFlowVisual/save") || path.equals("/flow/saveFlowVisual/push"); }
    private void authorizeSave(HttpServletRequest request, String path, byte[] body) {
        try {
            MediaType type = MediaType.parseMediaType(request.getContentType() == null ? "" : request.getContentType());
            if (!type.getType().equalsIgnoreCase("application") || !type.getSubtype().equalsIgnoreCase("json")
                    || (type.getCharset() != null && !StandardCharsets.UTF_8.equals(type.getCharset()))
                    || request.getHeader("Content-Encoding") != null) throw new IllegalArgumentException();
        } catch (IllegalArgumentException error) { throw new Failure(400, "原任务保存需要 UTF-8 JSON 请求"); }
        if (body.length == 0) throw new Failure(400, "原任务保存请求不能为空");
        LoginUser user = (LoginUser) request.getAttribute(REQUEST_USER);
        try { client.authorizeSave(user.getUserId(), path.endsWith("/push") ? "push" : "save", body); }
        catch (DataGovernanceHdiClient.ScopeDenied denied) { throw new Failure(403, "原任务保存包含未授权对象或身份字段"); }
        catch (DataGovernanceHdiClient.UnsupportedGraph unsupported) { throw new Failure(422, "当前已验证的数据流程范围不包含此节点或数据源引用"); }
        catch (IOException | RuntimeException unavailable) { throw new Failure(502, "暂时无法确认原任务保存范围"); }
    }

    private void authorizeObjects(HttpServletRequest request, String path, byte[] body) {
        Matcher read = READ_ID.matcher(path), write = WRITE_ID.matcher(path);
        String id = null;
        if (read.matches()) id = read.group(1);
        else if (write.matches()) id = write.group(2);
        else if (READ_FLOW_QUERY.contains(request.getMethod() + " " + path)) id = queryValue(request, "flowId");
        else return;
        if (body.length != 0) throw new Failure(400, "该原任务操作仅接受路径或查询参数");
        try {
            if (id == null || !id.matches("[1-9][0-9]{0,18}") || Long.parseLong(id) <= 0) throw new NumberFormatException();
        } catch (NumberFormatException error) { throw new Failure(400, "原任务操作需要有效任务 ID"); }
        LoginUser user = (LoginUser) request.getAttribute(REQUEST_USER);
        try { client.authorize(user.getUserId(), List.of(id)); }
        catch (DataGovernanceHdiClient.ScopeDenied denied) { throw new Failure(403, "任务不在当前原工作区允许范围内"); }
        catch (IOException | RuntimeException unavailable) { throw new Failure(502, "暂时无法确认原任务对象归属"); }
    }

    private void proxy(HttpServletRequest request, HttpServletResponse response, Session session, String path) throws IOException {
        int maxRequest = bounded(properties.getMaxRequestBytes(), 1024, 32 * 1024 * 1024);
        if (isSave(path)) maxRequest = Math.min(maxRequest, 8 * 1024 * 1024);
        int maxResponse = bounded(properties.getMaxResponseBytes(), 1024, 64 * 1024 * 1024);
        if (request.getContentLengthLong() > maxRequest) throw new Failure(413, "任务请求或上传文件超过网关限制");
        byte[] body = request.getInputStream().readNBytes(maxRequest + 1);
        if (body.length > maxRequest) throw new Failure(413, "任务请求或上传文件超过网关限制");
        if (body.length > 0 && path.equals("/folder/findFolder")) throw new Failure(400, "任务目录查询仅接受原查询参数");
        if (body.length > 0 && Set.of("GET", "HEAD").contains(request.getMethod())) throw new Failure(400, "读取请求不能携带请求体");
        if (isSave(path)) authorizeSave(request, path, body);
        else authorizeObjects(request, path, body);
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
            // Native handlers may skip scope checks when Workspace is absent and
            // default a missing projectCode to "0". The platform always supplies
            // the trusted mapping, even when the browser omits these headers.
            String workspace = scopeValue(session.context().get("workspaceId"));
            String project = isSave(path) ? null : singleHeader(request, "projectCode");
            if (project == null) project = scopeValue(session.context().get("projectCode"));
            if (workspace == null || project == null) throw new Failure(503, "原任务工作区或项目映射不可用");
            upstream.setRequestProperty("Workspace", workspace);
            upstream.setRequestProperty("projectCode", project);
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
            if (Set.of("/workspace/listWorkspace", "/globalConfig/groupManage/findAllGroup").contains(path) && status >= 200 && status < 300) {
                if (upstream.getHeaderField("Content-Encoding") != null) throw new Failure(502, "原工作区响应编码不可核验");
                bytes = scopedWorkspaces(bytes, session, path.equals("/workspace/listWorkspace") ? "workspaceId" : "id");
            }
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

    /** Filter genuine native memberships; never synthesize a workspace or turn a failure into success. */
    private static byte[] scopedWorkspaces(byte[] bytes, Session session, String idField) throws IOException {
        JsonNode result;
        try { result = JSON.readTree(bytes); }
        catch (IOException error) { throw new Failure(502, "原工作区响应格式不可核验"); }
        if (result == null || !result.isObject()) throw new Failure(502, "原工作区响应格式不可核验");
        if (!"0".equals(result.path("code").asText()) || result.path("success").isBoolean() && !result.path("success").asBoolean()) return bytes;
        JsonNode rows = result.get("data");
        if (rows == null || !rows.isArray()) throw new Failure(502, "原工作区列表格式不可核验");
        var allowed = JSON.createArrayNode();
        String workspace = scopeValue(session.context().get("workspaceId"));
        for (JsonNode row : rows) {
            JsonNode id = row.get(idField);
            if (!row.isObject() || id == null || !(id.isIntegralNumber() || id.isTextual())) throw new Failure(502, "原工作区标识不可核验");
            if (id.asText().equals(workspace)) allowed.add(row);
        }
        ((com.fasterxml.jackson.databind.node.ObjectNode) result).set("data", allowed);
        return JSON.writeValueAsBytes(result);
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
