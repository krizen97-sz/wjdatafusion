package com.hm.manage.service.governance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hm.common.constant.CacheConstants;
import com.hm.common.core.domain.entity.SysUser;
import com.hm.common.core.domain.model.LoginUser;
import com.hm.common.core.redis.RedisCache;
import com.hm.manage.controller.DataGovernanceHdiController;
import com.hm.manage.config.DataGovernanceHdiSecurityConfig.OpaqueSessionFilter;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class DataGovernanceHdiGatewayTest {
    @TempDir Path folder;
    final ObjectMapper json = new ObjectMapper();
    final Clock clock = Clock.fixed(Instant.parse("2026-10-13T04:00:00Z"), ZoneOffset.UTC);
    DataGovernanceHdiProperties properties;
    DataGovernanceHdiClient client;
    RedisCache cache;
    DataGovernanceHdiSessions sessions;
    DataGovernanceHdiGateway gateway;
    HttpServer server;
    LoginUser user;
    final AtomicInteger calls = new AtomicInteger();
    final List<Map<String, List<String>>> incoming = new ArrayList<>();
    final List<byte[]> bodies = new ArrayList<>();
    String nativeBody = "{\"code\":\"0\",\"data\":{\"rows\":[]}}";
    String nativeCookie;
    int nativeStatus = 200;

    @BeforeEach void setUp() throws Exception {
        folder = folder.toRealPath();
        properties = new DataGovernanceHdiProperties(); properties.setEnabled(true); properties.setPublicOrigin("https://platform.example:10443");
        Path bundle = folder.resolve("bundle"), host = folder.resolve("host");
        Files.createDirectories(bundle.resolve("public/static/js")); Files.createDirectories(host);
        Files.writeString(host.resolve("task-host.html"), "<html>original host</html>");
        Files.writeString(host.resolve("host.mjs"), "export const host = true");
        Files.writeString(host.resolve("native-task-host.mjs"), "export const native = true");
        byte[] asset = "export const original = true".getBytes(StandardCharsets.UTF_8);
        Files.write(bundle.resolve("public/static/js/original.js"), asset);
        Files.writeString(bundle.resolve("bundle-manifest.json"), json.writeValueAsString(Map.of("engine", "original-detl", "files",
            List.of(Map.of("path", "public/static/js/original.js", "sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(asset)))))));
        Files.createDirectories(bundle.resolve("task-service/config")); Files.writeString(bundle.resolve("task-service/config/secret.properties"), "never exposed");
        properties.setBundleDir(bundle.toString()); properties.setHostDir(host.toString());
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/detl", exchange -> {
            calls.incrementAndGet(); incoming.add(new HashMap<>(exchange.getRequestHeaders())); bodies.add(exchange.getRequestBody().readAllBytes());
            exchange.getResponseHeaders().set("Content-Type", nativeBody.startsWith("<") ? "text/html; charset=UTF-8" : "application/json; charset=UTF-8");
            if (nativeCookie != null) exchange.getResponseHeaders().add("Set-Cookie", nativeCookie);
            if (nativeStatus == 302) exchange.getResponseHeaders().set("Location", "http://192.0.2.1/never-follow");
            byte[] body = nativeBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(nativeStatus, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start(); properties.setTaskServiceUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/detl");
        client = mock(DataGovernanceHdiClient.class); cache = mock(RedisCache.class);
        user = login(7L, "login-seven", Set.of("governance:flow:list", "governance:flow:edit", "governance:flow:test"));
        when(cache.getCacheObject(CacheConstants.LOGIN_TOKEN_KEY + user.getToken())).thenAnswer(call -> user);
        when(client.exchange(anyLong())).thenAnswer(call -> new DataGovernanceHdiClient.NativeSession("JSESSIONID=native-" + call.getArgument(0),
            Map.of("user", Map.of("id", call.getArgument(0)), "projectCode", "p7", "projectList", List.of(Map.of("projectCode", "p7"), Map.of("projectCode", "p8")), "workspaceId", 42)));
        sessions = new DataGovernanceHdiSessions(properties, client, cache, clock); gateway = new DataGovernanceHdiGateway(properties);
    }
    @AfterEach void tearDown() { server.stop(0); SecurityContextHolder.clearContext(); }
    LoginUser login(long id, String token, Set<String> permissions) {
        SysUser account = new SysUser(); account.setUserId(id); account.setUserName("platform-user"); account.setStatus("0"); account.setDelFlag("0");
        LoginUser value = new LoginUser(id, 1L, account, permissions); value.setToken(token); value.setExpireTime(clock.millis() + 600000); return value;
    }
    MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.addHeader("Origin", properties.getPublicOrigin()); request.addHeader("Sec-Fetch-Site", "same-origin");
        return request;
    }
    DataGovernanceHdiSessions.Session session() { return sessions.create(user, request("POST", "/governance/hdi/session")); }
    MockHttpServletRequest authenticated(String method, String path, DataGovernanceHdiSessions.Session session) {
        MockHttpServletRequest request = request(method, path); request.setCookies(new Cookie(DataGovernanceHdiSessions.COOKIE, session.opaque()));
        sessions.authenticate(request); return request;
    }
    MockHttpServletResponse forward(MockHttpServletRequest request) throws IOException { var response = new MockHttpServletResponse(); gateway.handle(request, response); return response; }
    String header(Map<String, List<String>> headers, String name) {
        return headers.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(name)).flatMap(e -> e.getValue().stream()).findFirst().orElse(null);
    }

    @Test void sessionResponseContainsOnlyOpaqueHttpOnlyCookieAndContext() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
        var servletResponse = new MockHttpServletResponse();
        var response = new DataGovernanceHdiController(sessions).session(request("POST", "/governance/hdi/session"), servletResponse);
        assertEquals(200, response.getStatusCode().value());
        String cookie = response.getHeaders().getFirst("Set-Cookie");
        assertTrue(cookie.startsWith("RYNEW_HDI_SESSION=")); assertTrue(cookie.contains("HttpOnly")); assertTrue(cookie.contains("SameSite=Strict"));
        assertTrue(cookie.contains("Path=/detl")); assertTrue(cookie.contains("Secure")); assertFalse(cookie.contains("native-7"));
        String body = json.writeValueAsString(response.getBody()); assertTrue(body.contains("/detl/task-host.html")); assertFalse(body.contains("native-7")); assertFalse(body.contains("login-seven"));
        assertEquals("no-store", servletResponse.getHeader("Cache-Control")); verify(client).exchange(7L);
    }

    @Test void logoutAndPermissionRevocationInvalidateEveryOpaqueRequest() {
        var session = session();
        sessions.authenticate(authenticated("GET", "/detl/task-host.html", session));
        when(cache.getCacheObject(CacheConstants.LOGIN_TOKEN_KEY + user.getToken())).thenReturn(null);
        var request = request("GET", "/detl/static/js/original.js"); request.setCookies(new Cookie(DataGovernanceHdiSessions.COOKIE, session.opaque()));
        assertEquals(401, assertThrows(DataGovernanceHdiSessions.Failure.class, () -> sessions.authenticate(request)).status());
        when(cache.getCacheObject(CacheConstants.LOGIN_TOKEN_KEY + user.getToken())).thenReturn(user);
        var next = session(); user.setPermissions(Set.of());
        var denied = request("GET", "/detl/task-host.html"); denied.setCookies(new Cookie(DataGovernanceHdiSessions.COOKIE, next.opaque()));
        assertEquals(403, assertThrows(DataGovernanceHdiSessions.Failure.class, () -> sessions.authenticate(denied)).status());
    }

    @Test void logoutDuringNativeExchangeDoesNotMintBrowserSession() throws Exception {
        when(client.exchange(7L)).thenAnswer(call -> {
            when(cache.getCacheObject(CacheConstants.LOGIN_TOKEN_KEY + user.getToken())).thenReturn(null);
            return new DataGovernanceHdiClient.NativeSession("JSESSIONID=native-secret", Map.of());
        });
        assertEquals(401, assertThrows(DataGovernanceHdiSessions.Failure.class, this::session).status());
    }

    @Test void originRulesHandleMissingBrowserOriginWithoutTrustingSameSite() {
        for (String value : List.of("https://other.example", "null", "https://platform.example:10443@evil.test", "https://platform.example:10443/anything")) {
            var request = request("GET", "/detl/flow/startFlow/1"); request.removeHeader("Origin"); request.addHeader("Origin", value);
            assertEquals(403, assertThrows(DataGovernanceHdiSessions.Failure.class, () -> sessions.checkOrigin(request)).status());
        }
        var sameOrigin = request("GET", "/detl/task-host.html"); sameOrigin.removeHeader("Origin"); sessions.checkOrigin(sameOrigin);
        var referer = new MockHttpServletRequest("GET", "/detl/task-host.html"); referer.addHeader("Referer", properties.getPublicOrigin() + "/governance/workspace"); sessions.checkOrigin(referer);
        for (String site : List.of("same-site", "cross-site", "none")) {
            var request = request("GET", "/detl/flow/startFlow/1"); request.removeHeader("Origin"); request.removeHeader("Sec-Fetch-Site"); request.addHeader("Sec-Fetch-Site", site);
            assertThrows(DataGovernanceHdiSessions.Failure.class, () -> sessions.checkOrigin(request));
        }
        assertThrows(DataGovernanceHdiSessions.Failure.class, () -> sessions.checkOrigin(new MockHttpServletRequest("GET", "/detl/task-host.html")));
    }

    @Test void filtersAuthenticateStaticResourcesAndExposeRealLoginUserOnly() throws Exception {
        var session = session(); var filter = new OpaqueSessionFilter(sessions);
        var request = request("GET", "/detl/static/js/original.js"); request.setCookies(new Cookie(DataGovernanceHdiSessions.COOKIE, session.opaque()));
        filter.doFilter(request, new MockHttpServletResponse(), (r, s) -> assertSame(user, SecurityContextHolder.getContext().getAuthentication().getPrincipal()));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        var response = new MockHttpServletResponse(); AtomicInteger reached = new AtomicInteger();
        filter.doFilter(request("GET", "/detl/static/js/original.js"), response, (r, s) -> reached.incrementAndGet());
        assertEquals(401, response.getStatus()); assertEquals(0, reached.get()); assertFalse(response.getContentAsString().contains("native-"));
    }

    @Test void originalPostQueriesWorkButMutatingGetNeedsAllPermissions() throws Exception {
        var session = session(); user.setPermissions(Set.of("governance:flow:list"));
        var read = authenticated("POST", "/detl/flow/findFlowPage", session); read.setContent("{}".getBytes(StandardCharsets.UTF_8));
        assertEquals(nativeBody, forward(read).getContentAsString()); assertEquals(1, calls.get());
        var mutation = authenticated("GET", "/detl/flow/startFlow/9", session);
        assertEquals(403, assertThrows(DataGovernanceHdiSessions.Failure.class, () -> forward(mutation)).status());
        assertEquals(1, calls.get());
    }

    @Test void nativePayloadCookiesAndCsrfArePreservedWithoutForwardingBrowserIdentity() throws Exception {
        var session = session(); nativeCookie = "JSESSIONID=rotated-native; Path=/detl; HttpOnly";
        nativeBody = "<html><meta content=\"X-ETL-CSRF-TOKEN\" name=\"_csrf_header\"/><meta content=\"genuine-token\" name=\"_csrf\"/></html>";
        var bootstrap = forward(authenticated("GET", "/detl/", session));
        assertEquals(nativeBody, bootstrap.getContentAsString()); assertNull(bootstrap.getHeader("Set-Cookie"));
        nativeCookie = null; nativeBody = "{\"code\":\"0\",\"data\":{\"original\":true}}";
        var request = authenticated("POST", "/detl/flow/findFlowPage", session);
        request.addHeader("Authorization", "Bearer platform-secret"); request.addHeader("Cookie", "JSESSIONID=browser-forgery; secret=browser-value");
        request.addHeader("Forwarded", "host=evil"); request.addHeader("X-Forwarded-For", "192.0.2.1"); request.addHeader("X-RYNEW-Signature", "forged");
        request.addHeader("X-ETL-CSRF-TOKEN", "genuine-token"); request.addHeader("Workspace", "42"); request.addHeader("projectCode", "p7");
        request.setContent("{\"name\":\"原任务\"}".getBytes(StandardCharsets.UTF_8)); request.setContentType("application/json");
        var response = forward(request); var sent = incoming.get(incoming.size() - 1);
        assertEquals(nativeBody, response.getContentAsString()); assertFalse(response.getContentAsString().contains("\"code\":200"));
        assertEquals("JSESSIONID=rotated-native", header(sent, "Cookie")); assertNull(header(sent, "Authorization"));
        assertNull(header(sent, "Forwarded")); assertNull(header(sent, "X-Forwarded-For")); assertNull(header(sent, "X-RYNEW-Signature"));
        assertEquals("genuine-token", header(sent, "X-ETL-CSRF-TOKEN")); assertEquals("42", header(sent, "Workspace")); assertEquals("p7", header(sent, "projectCode"));
        assertArrayEquals(request.getContentAsByteArray(), bodies.get(bodies.size() - 1));
    }

    @Test void csrfAndOtherNativeSetCookiesRemainServerSide() throws Exception {
        when(client.exchange(7L)).thenReturn(new DataGovernanceHdiClient.NativeSession("JSESSIONID=private", Map.of("user", Map.of("id", 7)),
            List.of("JSESSIONID=private; Path=/detl; HttpOnly", "XSRF-TOKEN=original-xsrf; Path=/detl")));
        var session = session(); forward(authenticated("GET", "/detl/flow/getFilterOperator", session));
        assertTrue(header(incoming.get(0), "Cookie").contains("XSRF-TOKEN=original-xsrf"));
    }

    @Test void usersCannotShareOriginalCookies() throws Exception {
        var first = session();
        LoginUser other = login(8L, "login-eight", user.getPermissions()); when(cache.getCacheObject(CacheConstants.LOGIN_TOKEN_KEY + other.getToken())).thenReturn(other);
        var second = sessions.create(other, request("POST", "/governance/hdi/session"));
        forward(authenticated("GET", "/detl/flow/getFilterOperator", first)); forward(authenticated("GET", "/detl/flow/getFilterOperator", second));
        assertEquals("JSESSIONID=native-7", header(incoming.get(0), "Cookie")); assertEquals("JSESSIONID=native-8", header(incoming.get(1), "Cookie"));
    }

    @Test void redirectAndResponseLimitsFailWithoutLeakingNativeBody() throws Exception {
        var session = session(); nativeStatus = 302; nativeBody = "sensitive-original-body";
        var failure = assertThrows(DataGovernanceHdiSessions.Failure.class, () -> forward(authenticated("GET", "/detl/flow/getFilterOperator", session)));
        assertEquals(502, failure.status()); assertFalse(failure.getMessage().contains(nativeBody)); assertEquals(1, calls.get());
        nativeStatus = 200; nativeBody = "x".repeat(2048); properties.setMaxResponseBytes(1024);
        assertEquals(502, assertThrows(DataGovernanceHdiSessions.Failure.class, () -> forward(authenticated("GET", "/detl/flow/getFilterOperator", session))).status());
    }

    @Test void readBodyCannotBeRewrittenByHttpUrlConnectionIntoPost() {
        var session = session(); user.setPermissions(Set.of("governance:flow:list"));
        var read = authenticated("GET", "/detl/flow/getFilterOperator", session); read.setContent(new byte[]{1});
        assertEquals(400, assertThrows(DataGovernanceHdiSessions.Failure.class, () -> forward(read)).status());
        assertEquals(0, calls.get());
    }

    @Test void bodyLimitAndNonLoopbackTargetsFailBeforeHttpRequest() {
        var session = session(); properties.setMaxRequestBytes(1024);
        var request = authenticated("POST", "/detl/flow/saveFlowVisual/save", session); request.setContent(new byte[1025]);
        assertEquals(413, assertThrows(DataGovernanceHdiSessions.Failure.class, () -> forward(request)).status()); assertEquals(0, calls.get());
        properties.setTaskServiceUrl("http://192.0.2.1:19312/detl");
        assertEquals(503, assertThrows(DataGovernanceHdiSessions.Failure.class, () -> forward(authenticated("GET", "/detl/flow/getFilterOperator", session))).status()); assertEquals(0, calls.get());
    }

    @Test void workspacePermissionInitializationAndCreationAreDeniedEvenForTaskEditors() {
        var session = session();
        for (String path : List.of("/detl/workspace/getByProduct", "/detl/workspace/addWorkspace")) {
            for (String method : List.of("GET", "POST")) {
                var request = authenticated(method, path, session);
                request.setQueryString("product=iDataFusion%20DFP");
                var failure = assertThrows(DataGovernanceHdiSessions.Failure.class, () -> forward(request));
                assertEquals(403, failure.status()); assertTrue(failure.getMessage().contains("受控初始化"));
            }
        }
        assertEquals(0, calls.get());
    }

    @Test void nativeScopeHeadersCanOnlySelectServerMappedWorkspaceAndProjects() throws Exception {
        var session = session();
        var allowed = authenticated("GET", "/detl/flow/getFilterOperator", session);
        allowed.addHeader("Workspace", "42"); allowed.addHeader("projectCode", "p8");
        forward(allowed); assertEquals("42", header(incoming.get(0), "Workspace")); assertEquals("p8", header(incoming.get(0), "projectCode"));
        for (String name : List.of("Workspace", "projectCode")) {
            var denied = authenticated("GET", "/detl/flow/getFilterOperator", session); denied.addHeader(name, "foreign");
            assertEquals(403, assertThrows(DataGovernanceHdiSessions.Failure.class, () -> forward(denied)).status());
            var duplicated = authenticated("GET", "/detl/flow/getFilterOperator", session); duplicated.addHeader(name, "42"); duplicated.addHeader(name, "foreign");
            assertEquals(400, assertThrows(DataGovernanceHdiSessions.Failure.class, () -> forward(duplicated)).status());
        }
        assertEquals(1, calls.get());
        when(client.exchange(7L)).thenReturn(new DataGovernanceHdiClient.NativeSession("JSESSIONID=private", Map.of("projectCode", "p7")));
        var missing = session(); var unbound = authenticated("GET", "/detl/flow/getFilterOperator", missing); unbound.addHeader("Workspace", "42");
        assertEquals(403, assertThrows(DataGovernanceHdiSessions.Failure.class, () -> forward(unbound)).status());
        assertEquals(1, calls.get());
    }

    @Test void manifestAndExactHostAllowlistCannotExposeSecretsOrTraversal() throws Exception {
        var session = session();
        assertTrue(forward(authenticated("GET", "/detl/static/js/original.js", session)).getContentAsString().contains("original"));
        assertTrue(forward(authenticated("GET", "/detl/native-task-host.mjs", session)).getContentAsString().contains("native"));
        for (String path : List.of("/detl/task-service/config/secret.properties", "/detl/system/user/list", "/detl/_platform/session", "/detl/flow/license/delete", "/detl/globalConfig/instanceManage/addInstance")) {
            assertEquals(403, assertThrows(DataGovernanceHdiSessions.Failure.class, () -> forward(authenticated("GET", path, session))).status());
        }
        for (String path : List.of("/detl/../secret", "/detl/%2e%2e/secret", "/detl/static%2fjs/original.js", "/detl/static//js/original.js", "/detl/static/js/original.js;anything")) {
            assertEquals(400, assertThrows(DataGovernanceHdiSessions.Failure.class, () -> forward(authenticated("GET", path, session))).status());
        }
        assertEquals(0, calls.get());
        Files.writeString(Path.of(properties.getBundleDir()).resolve("public/static/js/original.js"), "changed");
        assertEquals(503, assertThrows(DataGovernanceHdiSessions.Failure.class, () -> forward(authenticated("GET", "/detl/static/js/original.js", session))).status());
    }

    @Test void symbolicHostAndManifestResourcesAreRejected() throws Exception {
        var session = session(); Path host = Path.of(properties.getHostDir()).resolve("host.mjs");
        Files.delete(host); Files.createSymbolicLink(host, folder.resolve("bundle/task-service/config/secret.properties"));
        assertEquals(404, assertThrows(DataGovernanceHdiSessions.Failure.class, () -> forward(authenticated("GET", "/detl/host.mjs", session))).status());
        Path original = Path.of(properties.getBundleDir()).resolve("public/static/js/original.js");
        Files.delete(original); Files.createSymbolicLink(original, folder.resolve("bundle/task-service/config/secret.properties"));
        assertEquals(503, assertThrows(DataGovernanceHdiSessions.Failure.class, () -> forward(authenticated("GET", "/detl/static/js/original.js", session))).status());
    }

    @Test void disabledConfigurationAndRedisFailureNeverTrustCookieAlone() {
        properties.setEnabled(false); assertEquals(503, assertThrows(DataGovernanceHdiSessions.Failure.class, this::session).status()); verifyNoInteractions(client);
        properties.setEnabled(true); var session = session();
        when(cache.getCacheObject(CacheConstants.LOGIN_TOKEN_KEY + user.getToken())).thenThrow(new IllegalStateException("redis-sensitive-detail"));
        var request = request("GET", "/detl/static/js/original.js"); request.setCookies(new Cookie(DataGovernanceHdiSessions.COOKIE, session.opaque()));
        var failure = assertThrows(DataGovernanceHdiSessions.Failure.class, () -> sessions.authenticate(request));
        assertEquals(503, failure.status()); assertFalse(failure.getMessage().contains("redis-sensitive"));
    }
}
