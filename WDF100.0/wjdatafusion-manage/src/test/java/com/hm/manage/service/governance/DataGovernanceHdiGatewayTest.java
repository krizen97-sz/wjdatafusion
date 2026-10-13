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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
        sessions = new DataGovernanceHdiSessions(properties, client, cache, clock); gateway = new DataGovernanceHdiGateway(properties, client);
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
        String body = json.writeValueAsString(response.getBody()); assertTrue(body.contains("/detl/#/task/taskList?appCode=42&flag=1&menuflag=1")); assertFalse(body.contains("native-7")); assertFalse(body.contains("login-seven"));
        assertEquals("no-store", servletResponse.getHeader("Cache-Control")); verify(client).exchange(7L);
    }

    @Test void sessionHttpEndpointParsesOptionalRenewJsonAndRejectsIdentityFields() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new DataGovernanceHdiController(sessions)).build();
        var first = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/governance/hdi/session")
            .header("Origin", properties.getPublicOrigin())).andReturn().getResponse();
        assertEquals(200, first.getStatus()); String firstCookie = first.getHeader("Set-Cookie");
        var reused = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/governance/hdi/session")
            .header("Origin", properties.getPublicOrigin()).contentType("application/json").content("{\"renew\":false}"))
            .andReturn().getResponse();
        assertEquals(200, reused.getStatus()); assertEquals(firstCookie, reused.getHeader("Set-Cookie"));
        var renewed = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/governance/hdi/session")
            .header("Origin", properties.getPublicOrigin()).contentType("application/json").content("{\"renew\":true}"))
            .andReturn().getResponse();
        assertEquals(200, renewed.getStatus()); assertNotEquals(firstCookie, renewed.getHeader("Set-Cookie"));
        var forged = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/governance/hdi/session")
            .header("Origin", properties.getPublicOrigin()).contentType("application/json").content("{\"userId\":99}"))
            .andReturn().getResponse();
        assertEquals(400, forged.getStatus()); verify(client, times(2)).exchange(7L);
    }

    @Test void standaloneEntryUsesOnlyPositiveServerMappedWorkspaceNotBrowserAppCode() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
        var request = request("POST", "/governance/hdi/session"); request.addParameter("appCode", "999999");
        request.setContent("{\"appCode\":\"999999\",\"workspaceId\":999999}".getBytes(StandardCharsets.UTF_8));
        var controller = new DataGovernanceHdiController(sessions);
        var result = controller.session(request, new MockHttpServletResponse());
        assertTrue(json.writeValueAsString(result.getBody()).contains("appCode=42&flag=1&menuflag=1"));
        for (Object invalid : List.of("0", "-1", "1.0", "01", "9223372036854775808", "42&appName=iDataFusion DFP", "42%26flag=0", List.of(42))) {
            when(client.exchange(7L)).thenReturn(new DataGovernanceHdiClient.NativeSession("JSESSIONID=private", Map.of("workspaceId", invalid)));
            var failure = controller.session(request, new MockHttpServletResponse(), Map.of("renew", true));
            assertEquals(502, failure.getStatusCode().value()); assertNull(failure.getHeaders().getFirst("Set-Cookie"));
        }
        when(client.exchange(7L)).thenReturn(new DataGovernanceHdiClient.NativeSession("JSESSIONID=private", Map.of()));
        assertEquals(502, controller.session(request, new MockHttpServletResponse(), Map.of("renew", true)).getStatusCode().value());
    }

    @Test void standaloneBootstrapAllowsOnlyExactVerifiedGetsAndPreservesNative404() throws Exception {
        var session = session(); user.setPermissions(Set.of("governance:flow:list"));
        List<String> paths = List.of("/getLanguage", "/getEtlUserInfo", "/workspace/listWorkspace",
            "/system/environment/getLicenseInfo", "/system/environment/getLicenseExpire",
            "/user/security/privilege/info", "/code/codeConfig/getDataTypeList", "/globalConfig/runningConfig/healthStatus");
        for (String path : paths) {
            assertEquals(nativeBody, forward(authenticated("GET", "/detl" + path, session)).getContentAsString());
            for (String verb : List.of("POST", "PUT", "DELETE", "HEAD"))
                assertEquals(403, assertThrows(DataGovernanceHdiSessions.Failure.class, () -> forward(authenticated(verb, "/detl" + path, session))).status());
        }
        assertEquals(paths.size(), calls.get());
        nativeStatus = 404; nativeBody = "{\"status\":404,\"error\":\"Not Found\"}";
        var missing = forward(authenticated("GET", "/detl/user/security/privilege/info", session));
        assertEquals(404, missing.getStatus()); assertEquals(nativeBody, missing.getContentAsString());
        assertFalse(missing.getContentAsString().contains("\"code\":\"0\""));
        for (String path : List.of("/user/list", "/user/security/privilege/update", "/system/environment/updateLicense", "/system/users"))
            assertEquals(403, assertThrows(DataGovernanceHdiSessions.Failure.class, () -> forward(authenticated("GET", "/detl" + path, session))).status());
        assertEquals(paths.size() + 1, calls.get());
    }

    @Test void originalTaskListAndDesignerQueriesNeedOnlyViewerGrantAndKeepExactMethods() throws Exception {
        var session = session(); user.setPermissions(Set.of("governance:flow:list"));
        Map<String, String> routes = Map.ofEntries(
            Map.entry("/getPublicKey", "GET"), Map.entry("/system/environment/getExpertMode", "GET"), Map.entry("/system/environment/getPortPool", "GET"),
            Map.entry("/globalConfig/felManager/query", "POST"), Map.entry("/globalConfig/dataSourceManage/getDataSourceExtendInfo", "GET"),
            Map.entry("/globalConfig/groupManage/findAllGroup", "GET"), Map.entry("/globalConfig/serverManage/findAllServers", "GET"),
            Map.entry("/globalConfig/tag/listTag", "GET"), Map.entry("/flow/getColumnDisplayName", "GET"),
            Map.entry("/flow/pageInterruptedFlow", "GET"), Map.entry("/flow/valid/getAdapterReadWritePath", "POST"),
            Map.entry("/globalConfig/groupAdapterManage/listAdapters", "GET"), Map.entry("/globalConfig/paramManage/listParams", "POST"),
            Map.entry("/globalConfig/groupAdapterManage/getOrderedGroupAdapters/42", "POST"), Map.entry("/globalConfig/instanceManage/findByGroupId/42", "GET"));
        for (var route : routes.entrySet()) {
            var request = authenticated(route.getValue(), "/detl" + route.getKey(), session); request.addHeader("Workspace", "42");
            if (route.getValue().equals("POST")) { request.setContent("{}".getBytes(StandardCharsets.UTF_8)); request.setContentType("application/json"); }
            assertEquals(nativeBody, forward(request).getContentAsString());
            String wrongMethod = route.getValue().equals("POST") ? "GET" : "POST";
            assertEquals(403, assertThrows(DataGovernanceHdiSessions.Failure.class, () -> forward(authenticated(wrongMethod, "/detl" + route.getKey(), session))).status());
        }
        assertEquals(routes.size(), calls.get());
        for (String path : List.of("/globalConfig/groupAdapterManage/getOrderedGroupAdapters/99", "/globalConfig/instanceManage/findByGroupId/99")) {
            String method = path.contains("getOrderedGroupAdapters") ? "POST" : "GET";
            assertEquals(403, assertThrows(DataGovernanceHdiSessions.Failure.class, () -> forward(authenticated(method, "/detl" + path, session))).status());
        }
        assertEquals(routes.size(), calls.get());
        var instances = authenticated("GET", "/detl/globalConfig/instanceManage/findAllInstance", session);
        instances.setQueryString("groupId=99");
        assertEquals(403, assertThrows(DataGovernanceHdiSessions.Failure.class, () -> forward(instances)).status());
        instances.setQueryString("groupId=42&groupId=99");
        assertEquals(400, assertThrows(DataGovernanceHdiSessions.Failure.class, () -> forward(instances)).status());
        assertEquals(routes.size(), calls.get());
    }

    @Test void originalFolderQueryCannotOverrideServerMappedProjectOrUser() throws Exception {
        var session = session(); user.setPermissions(Set.of("governance:flow:list"));
        for (String query : List.of("folderType=1&projectCode=p7&userId=7&pid=-1", "folderType=1&projectCode=&userId=", "folderType=1&projectCode=p8")) {
            var request = authenticated("POST", "/detl/folder/findFolder", session); request.setQueryString(query);
            assertEquals(nativeBody, forward(request).getContentAsString());
        }
        assertEquals(3, calls.get());
        for (String query : List.of("projectCode=foreign", "%70rojectCode=foreign", "userId=8", "projectCode=p7&projectCode=foreign", "projectCode=&projectCode=p7", "userId=7&userId=8")) {
            var request = authenticated("POST", "/detl/folder/findFolder", session); request.setQueryString(query);
            var failure = assertThrows(DataGovernanceHdiSessions.Failure.class, () -> forward(request));
            assertTrue(failure.status() == 400 || failure.status() == 403);
        }
        var form = authenticated("POST", "/detl/folder/findFolder", session); form.setContentType("application/x-www-form-urlencoded");
        form.setContent("projectCode=foreign".getBytes(StandardCharsets.UTF_8));
        assertEquals(400, assertThrows(DataGovernanceHdiSessions.Failure.class, () -> forward(form)).status());
        assertEquals(3, calls.get());
    }

    @Test void originalWindowsShareNativeSessionCookiesAndCsrfUntilExplicitRenewal() throws Exception {
        var first = session();
        nativeBody = "<html><meta content=\"X-ETL-CSRF-TOKEN\" name=\"_csrf_header\"/><meta content=\"original-csrf\" name=\"_csrf\"/></html>";
        forward(authenticated("GET", "/detl/", first));
        var second = session(); assertSame(first, second); assertEquals(first.opaque(), second.opaque());
        nativeBody = "{\"code\":\"0\"}";
        for (var tab : List.of(first, second)) {
            var query = authenticated("POST", "/detl/flow/findFlowPage", tab); query.addHeader("X-ETL-CSRF-TOKEN", "original-csrf");
            forward(query);
        }
        verify(client, times(1)).exchange(7L);
        assertEquals(header(incoming.get(1), "Cookie"), header(incoming.get(2), "Cookie"));
        assertEquals("original-csrf", header(incoming.get(1), "X-ETL-CSRF-TOKEN"));
        assertEquals("original-csrf", header(incoming.get(2), "X-ETL-CSRF-TOKEN"));
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
        var controller = new DataGovernanceHdiController(sessions);
        var normal = controller.session(request("POST", "/governance/hdi/session"), new MockHttpServletResponse(), Map.of("renew", false));
        assertTrue(normal.getHeaders().getFirst("Set-Cookie").contains(first.opaque()));
        var renewed = controller.session(request("POST", "/governance/hdi/session"), new MockHttpServletResponse(), Map.of("renew", true));
        assertEquals(200, renewed.getStatusCode().value()); assertFalse(renewed.getHeaders().getFirst("Set-Cookie").contains(first.opaque()));
        verify(client, times(2)).exchange(7L);
        assertEquals(401, assertThrows(DataGovernanceHdiSessions.Failure.class, () -> authenticated("GET", "/detl/", first)).status());
    }

    @Test void simultaneousNewTabsPerformOneNativeExchange() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        when(client.exchange(7L)).thenAnswer(call -> {
            entered.countDown(); if (!release.await(3, TimeUnit.SECONDS)) throw new IOException("test timeout");
            return new DataGovernanceHdiClient.NativeSession("JSESSIONID=one-native", Map.of("workspaceId", "42"));
        });
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(this::session); assertTrue(entered.await(3, TimeUnit.SECONDS));
            var second = pool.submit(this::session); release.countDown();
            assertSame(first.get(3, TimeUnit.SECONDS), second.get(3, TimeUnit.SECONDS));
            verify(client, times(1)).exchange(7L);
        } finally { release.countDown(); pool.shutdownNow(); }
    }

    @Test void reuseAndRenewCannotReviveLoggedOutPlatformLoginOrAcceptCallerIdentity() throws Exception {
        var original = session();
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
        var controller = new DataGovernanceHdiController(sessions);
        for (Map<String, Object> input : List.<Map<String, Object>>of(Map.of("subject", "rynew:user:99"), Map.of("userId", 99), Map.of("renew", "true"), Map.of("renew", true, "workspaceId", 99))) {
            var result = controller.session(request("POST", "/governance/hdi/session"), new MockHttpServletResponse(), input);
            assertEquals(400, result.getStatusCode().value()); assertNull(result.getHeaders().getFirst("Set-Cookie"));
        }
        when(cache.getCacheObject(CacheConstants.LOGIN_TOKEN_KEY + user.getToken())).thenReturn(null);
        for (boolean renew : List.of(false, true)) {
            var result = controller.session(request("POST", "/governance/hdi/session"), new MockHttpServletResponse(), Map.of("renew", renew));
            assertEquals(401, result.getStatusCode().value()); assertNull(result.getHeaders().getFirst("Set-Cookie"));
        }
        when(cache.getCacheObject(CacheConstants.LOGIN_TOKEN_KEY + user.getToken())).thenReturn(user);
        assertEquals(401, assertThrows(DataGovernanceHdiSessions.Failure.class, () -> authenticated("GET", "/detl/", original)).status());
        verify(client, times(1)).exchange(7L);
        var newLogin = login(7L, "new-platform-login", user.getPermissions());
        when(cache.getCacheObject(CacheConstants.LOGIN_TOKEN_KEY + newLogin.getToken())).thenReturn(newLogin);
        var replacement = sessions.create(newLogin, request("POST", "/governance/hdi/session"));
        assertNotEquals(original.opaque(), replacement.opaque()); verify(client, times(2)).exchange(7L);
    }

    @Test void taskObjectReadsAndExactControlOperationsRequireNativeOwnershipBeforeForwarding() throws Exception {
        var session = session();
        for (String path : List.of("/flow/getFlowById/17", "/flow/getFlowVisualById/17", "/mount/getMountFlowVisual", "/flow/startFlowV2/17")) {
            var request = authenticated("GET", "/detl" + path, session); request.setQueryString("flowId=17");
            assertEquals(nativeBody, forward(request).getContentAsString());
        }
        verify(client, times(4)).authorize(7L, List.of("17")); assertEquals(4, calls.get());
        doThrow(new DataGovernanceHdiClient.ScopeDenied()).when(client).authorize(7L, List.of("99"));
        for (String path : List.of("/flow/getFlowVisualById/99", "/flow/startFlowV2/99", "/mount/getMountFlowVisual")) {
            var request = authenticated("GET", "/detl" + path, session); request.setQueryString("flowId=99");
            assertEquals(403, assertThrows(DataGovernanceHdiSessions.Failure.class, () -> forward(request)).status());
        }
        doThrow(new IOException("private-native-error")).when(client).authorize(7L, List.of("88"));
        var unavailable = authenticated("POST", "/detl/flow/debugFlow/88", session);
        var failure = assertThrows(DataGovernanceHdiSessions.Failure.class, () -> forward(unavailable));
        assertEquals(502, failure.status()); assertFalse(failure.getMessage().contains("private-native-error"));
        assertEquals(4, calls.get());
    }

    @Test void executionAndEditingGrantsRetainTheirSeparatePlatformMeanings() throws Exception {
        var session = session(); user.setPermissions(Set.of("governance:flow:list", "governance:flow:test"));
        forward(authenticated("GET", "/detl/flow/startFlowV2/17", session));
        forward(authenticated("GET", "/detl/flow/stopFlowV2/17", session));
        forward(authenticated("POST", "/detl/flow/debugFlow/17", session));
        assertEquals(403, assertThrows(DataGovernanceHdiSessions.Failure.class, () -> forward(authenticated("GET", "/detl/flow/lockFlow/17", session))).status());
        assertEquals(403, assertThrows(DataGovernanceHdiSessions.Failure.class, () -> forward(authenticated("POST", "/detl/flow/pushFlowDataToZK/17", session))).status());
        user.setPermissions(Set.of("governance:flow:list", "governance:flow:edit"));
        forward(authenticated("GET", "/detl/flow/lockFlow/17", session));
        forward(authenticated("GET", "/detl/flow/unlockFlow/17", session));
        assertEquals(403, assertThrows(DataGovernanceHdiSessions.Failure.class, () -> forward(authenticated("POST", "/detl/flow/debugFlow/17", session))).status());
        assertEquals(403, assertThrows(DataGovernanceHdiSessions.Failure.class, () -> forward(authenticated("POST", "/detl/flow/pushFlowDataToZK/17", session))).status());
        user.setPermissions(Set.of("governance:flow:list", "governance:flow:edit", "governance:flow:test"));
        forward(authenticated("POST", "/detl/flow/pushFlowDataToZK/17", session));
        assertEquals(6, calls.get()); verify(client, times(6)).authorize(7L, List.of("17"));
    }

    @Test void flowIdQueriesCannotUseRepeatedOrBodyOverrideIdsAndUnknownWritesAreDenied() throws Exception {
        var session = session();
        for (String path : List.of("/flow/getFlowVisualState", "/flow/findFlowExecRecordPage", "/flow/findFlowModuleExecRecordPage")) {
            var valid = authenticated("POST", "/detl" + path, session); valid.setQueryString("flowId=17&nodeId=23&pageNo=1&pageSize=10");
            assertEquals(nativeBody, forward(valid).getContentAsString());
            var duplicate = authenticated("POST", "/detl" + path, session); duplicate.setQueryString("flowId=17&flowId=99");
            assertEquals(400, assertThrows(DataGovernanceHdiSessions.Failure.class, () -> forward(duplicate)).status());
            var form = authenticated("POST", "/detl" + path, session); form.setQueryString("flowId=17");
            form.setContentType("application/x-www-form-urlencoded"); form.setContent("flowId=99".getBytes(StandardCharsets.UTF_8));
            assertEquals(400, assertThrows(DataGovernanceHdiSessions.Failure.class, () -> forward(form)).status());
        }
        for (String path : List.of("/flow/deleteFlow", "/flow/anythingNew", "/flowtemplate/importFlow/17", "/flowlog/packageTaskLog"))
            assertEquals(403, assertThrows(DataGovernanceHdiSessions.Failure.class, () -> forward(authenticated("POST", "/detl" + path, session))).status());
        assertEquals(3, calls.get()); verify(client, times(3)).authorize(7L, List.of("17"));
    }

    @Test void originalSaveBodyIsAuthorizedWithoutRewritingAndEditingDoesNotRequireExecution() throws Exception {
        var session = session(); user.setPermissions(Set.of("governance:flow:list", "governance:flow:edit"));
        byte[] original = "{ \"visualBase64\": \"JTdCJTIyZmxvd1ZvJTIyJTNBJTdCJTIyaWQlMjIlM0FudWxsJTdEJTdE\" }".getBytes(StandardCharsets.UTF_8);
        var save = authenticated("POST", "/detl/flow/saveFlowVisual/save", session); save.addHeader("Workspace", "42");
        save.setContentType("application/json; charset=UTF-8"); save.setContent(original);
        assertEquals(nativeBody, forward(save).getContentAsString());
        verify(client).authorizeSave(eq(7L), eq("save"), argThat(bytes -> java.util.Arrays.equals(original, bytes)));
        assertArrayEquals(original, bodies.get(0)); assertEquals("p7", header(incoming.get(0), "projectCode"));
        var publish = authenticated("POST", "/detl/flow/saveFlowVisual/push", session); publish.addHeader("Workspace", "42");
        publish.setContentType("application/json"); publish.setContent(original);
        assertEquals(403, assertThrows(DataGovernanceHdiSessions.Failure.class, () -> forward(publish)).status());
        user.setPermissions(Set.of("governance:flow:list", "governance:flow:edit", "governance:flow:test"));
        assertEquals(nativeBody, forward(publish).getContentAsString());
        verify(client).authorizeSave(eq(7L), eq("push"), argThat(bytes -> java.util.Arrays.equals(original, bytes)));
        assertArrayEquals(original, bodies.get(1)); assertEquals(2, calls.get());
    }

    @Test void saveScopeFailureOrUnavailableAdapterNeverCallsOriginalMutation() throws Exception {
        var session = session();
        var save = authenticated("POST", "/detl/flow/saveFlowVisual/save", session); save.addHeader("Workspace", "42");
        save.setContentType("application/json"); save.setContent("{\"flowVo\":{\"id\":99}}".getBytes(StandardCharsets.UTF_8));
        doThrow(new DataGovernanceHdiClient.ScopeDenied()).when(client).authorizeSave(eq(7L), eq("save"), any(byte[].class));
        assertEquals(403, assertThrows(DataGovernanceHdiSessions.Failure.class, () -> forward(save)).status());
        doThrow(new IOException("private-secret")).when(client).authorizeSave(eq(7L), eq("save"), any(byte[].class));
        save.setContent("{\"flowVo\":{\"id\":17}}".getBytes(StandardCharsets.UTF_8));
        var failure = assertThrows(DataGovernanceHdiSessions.Failure.class, () -> forward(save));
        assertEquals(502, failure.status()); assertFalse(failure.getMessage().contains("private-secret"));
        doThrow(new DataGovernanceHdiClient.UnsupportedGraph()).when(client).authorizeSave(eq(7L), eq("save"), any(byte[].class));
        save.setContent("{}".getBytes(StandardCharsets.UTF_8));
        assertEquals(422, assertThrows(DataGovernanceHdiSessions.Failure.class, () -> forward(save)).status()); assertEquals(0, calls.get());
    }

    @Test void savingCannotSwitchProjectOmitWorkspaceOrUseUnverifiedEncoding() throws Exception {
        var session = session();
        for (String type : List.of("application/x-www-form-urlencoded", "text/plain", "application/json; charset=UTF-16")) {
            var save = authenticated("POST", "/detl/flow/saveFlowVisual/save", session); save.addHeader("Workspace", "42");
            save.setContentType(type); save.setContent("{}".getBytes(StandardCharsets.UTF_8));
            assertEquals(400, assertThrows(DataGovernanceHdiSessions.Failure.class, () -> forward(save)).status());
        }
        var missing = authenticated("POST", "/detl/flow/saveFlowVisual/save", session); missing.setContentType("application/json"); missing.setContent("{}".getBytes(StandardCharsets.UTF_8));
        assertEquals(400, assertThrows(DataGovernanceHdiSessions.Failure.class, () -> forward(missing)).status());
        missing.addHeader("Workspace", "42"); missing.addHeader("projectCode", "p8");
        assertEquals(403, assertThrows(DataGovernanceHdiSessions.Failure.class, () -> forward(missing)).status());
        verify(client, never()).authorizeSave(anyLong(), anyString(), any(byte[].class)); assertEquals(0, calls.get());
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

    @Test void originalPostQueriesWorkButMutatingGetNeedsExecutionPermission() throws Exception {
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
        when(client.exchange(7L)).thenReturn(new DataGovernanceHdiClient.NativeSession("JSESSIONID=private", Map.of("user", Map.of("id", 7), "workspaceId", 42),
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
        var request = authenticated("POST", "/detl/flow/debugFlow/17", session); request.setContent(new byte[1025]);
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
        assertEquals(502, assertThrows(DataGovernanceHdiSessions.Failure.class, () -> sessions.create(user, request("POST", "/governance/hdi/session"), true)).status());
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
