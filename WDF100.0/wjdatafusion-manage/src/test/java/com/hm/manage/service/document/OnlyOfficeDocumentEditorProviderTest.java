package com.hm.manage.service.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.hm.common.exception.ServiceException;
import com.hm.common.core.domain.entity.SysUser;
import com.hm.common.core.domain.model.LoginUser;
import com.hm.manage.config.DocumentManagementProperties;
import com.hm.manage.domain.DocDocument;

class OnlyOfficeDocumentEditorProviderTest
{
    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final String EDITOR_KEY = "doc-42-v3";
    private DocumentJwtService jwtService;
    private OnlyOfficeDocumentEditorProvider provider;
    private DocDocument document;
    private DocumentManagementProperties properties;

    @BeforeEach
    void setUp()
    {
        properties = new DocumentManagementProperties();
        properties.getOnlyOffice().setJwtSecret(SECRET);
        jwtService = new DocumentJwtService();
        ReflectionTestUtils.setField(jwtService, "properties", properties);

        provider = new OnlyOfficeDocumentEditorProvider();
        ReflectionTestUtils.setField(provider, "properties", properties);
        ReflectionTestUtils.setField(provider, "jwtService", jwtService);

        document = new DocDocument();
        document.setDocumentId(42L);
        document.setEditorKey(EDITOR_KEY);
    }

    @Test
    @SuppressWarnings("unchecked")
    void editorBootstrapShouldDefaultToSimplifiedChineseAndChinaRegion()
    {
        properties.getOnlyOffice().setEnabled(true);
        properties.getOnlyOffice().setServerUrl("http://onlyoffice-documentserver");
        properties.getOnlyOffice().setPlatformBaseUrl("http://platform:8080");
        document.setFileType("docx");
        document.setDocumentType("word");
        document.setTitle("测试文档.docx");
        SysUser user = new SysUser();
        user.setUserId(8L);
        user.setUserName("editor");
        user.setNickName("编辑用户");
        LoginUser loginUser = new LoginUser(8L, 100L, user, Set.of());

        Map<String, Object> bootstrap = provider.buildEditorBootstrap(document, loginUser, true);

        Map<String, Object> config = (Map<String, Object>) bootstrap.get("config");
        Map<String, Object> editorConfig = (Map<String, Object>) config.get("editorConfig");
        Map<String, Object> customization = (Map<String, Object>) editorConfig.get("customization");
        assertEquals("zh-CN", editorConfig.get("lang"));
        assertEquals("zh-CN", editorConfig.get("region"));
        assertEquals(false, customization.get("about"));
        assertEquals(false, customization.get("help"));
        assertEquals(false, customization.get("feedback"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void viewPermissionShouldAllowContentCopyWithoutEditingSavingOrDownloading()
    {
        properties.getOnlyOffice().setEnabled(true);
        properties.getOnlyOffice().setServerUrl("http://onlyoffice-documentserver");
        properties.getOnlyOffice().setPlatformBaseUrl("http://platform:8080");
        document.setFileType("docx");
        document.setDocumentType("word");
        document.setTitle("仅查看文档.docx");
        SysUser user = new SysUser();
        user.setUserId(9L);
        user.setUserName("viewer");
        user.setNickName("查看用户");
        LoginUser loginUser = new LoginUser(9L, 100L, user, Set.of());

        Map<String, Object> bootstrap = provider.buildEditorBootstrap(document, loginUser, false);

        Map<String, Object> config = (Map<String, Object>) bootstrap.get("config");
        Map<String, Object> documentConfig = (Map<String, Object>) config.get("document");
        Map<String, Object> permissions = (Map<String, Object>) documentConfig.get("permissions");
        Map<String, Object> editorConfig = (Map<String, Object>) config.get("editorConfig");
        assertEquals("VIEW", bootstrap.get("permission"));
        assertEquals("view", editorConfig.get("mode"));
        assertEquals(false, permissions.get("edit"));
        assertEquals(true, permissions.get("copy"));
        assertEquals(false, permissions.get("download"));
        assertEquals(false, permissions.get("print"));
    }

    @Test
    void permissionDowngradeShouldSendSignedDropCommand() throws Exception
    {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> requestPath = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/command", exchange -> {
            requestPath.set(exchange.getRequestURI().toString());
            requestBody.set(new String(exchange.getRequestBody().readAllBytes()));
            byte[] response = "{\"error\":0,\"key\":\"doc-42-v3\"}".getBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try
        {
            properties.getOnlyOffice().setEnabled(true);
            properties.getOnlyOffice().setServerUrl("http://127.0.0.1:" + server.getAddress().getPort());
            properties.getOnlyOffice().setPlatformBaseUrl("http://platform:8080");

            provider.revokeEditingRights(document, List.of(8L, 9L));

            assertTrue(requestPath.get().startsWith("/command?shardkey="));
            Map<String, Object> body = JSON.parseObject(requestBody.get(),
                new TypeReference<Map<String, Object>>() { });
            assertEquals("drop", body.get("c"));
            assertEquals(EDITOR_KEY, body.get("key"));
            assertEquals(List.of("8", "9"), body.get("users"));
            assertNotNull(body.get("token"));
            Map<String, Object> claims = jwtService.verify(String.valueOf(body.get("token")));
            assertEquals("drop", claims.get("c"));
            assertEquals(EDITOR_KEY, claims.get("key"));
        }
        finally
        {
            server.stop(0);
        }
    }

    @Test
    void forceSaveShouldSendSignedCommandAndWaitForCallback() throws Exception
    {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/command", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes()));
            byte[] response = "{\"error\":0,\"key\":\"doc-42-v3\"}".getBytes();
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try
        {
            properties.getOnlyOffice().setEnabled(true);
            properties.getOnlyOffice().setServerUrl("http://127.0.0.1:" + server.getAddress().getPort());
            properties.getOnlyOffice().setPlatformBaseUrl("http://platform:8080");

            assertTrue(provider.forceSave(document));

            Map<String, Object> body = JSON.parseObject(requestBody.get(),
                new TypeReference<Map<String, Object>>() { });
            assertEquals("forcesave", body.get("c"));
            assertEquals(EDITOR_KEY, body.get("key"));
            assertNotNull(body.get("token"));
            Map<String, Object> claims = jwtService.verify(String.valueOf(body.get("token")));
            assertEquals("forcesave", claims.get("c"));
            assertEquals(EDITOR_KEY, claims.get("key"));
        }
        finally
        {
            server.stop(0);
        }
    }

    @Test
    void forceSaveShouldTreatNoNewChangesAsAlreadySettled() throws Exception
    {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/command", exchange -> {
            byte[] response = "{\"error\":4,\"key\":\"doc-42-v3\"}".getBytes();
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try
        {
            properties.getOnlyOffice().setEnabled(true);
            properties.getOnlyOffice().setServerUrl("http://127.0.0.1:" + server.getAddress().getPort());
            properties.getOnlyOffice().setPlatformBaseUrl("http://platform:8080");

            assertFalse(provider.forceSave(document));
        }
        finally
        {
            server.stop(0);
        }
    }

    @Test
    void versionRestoreShouldSendDropCommandWithoutUsersToDisconnectAllEditors() throws Exception
    {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/command", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes()));
            byte[] response = "{\"error\":0,\"key\":\"doc-42-v3\"}".getBytes();
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try
        {
            properties.getOnlyOffice().setEnabled(true);
            properties.getOnlyOffice().setServerUrl("http://127.0.0.1:" + server.getAddress().getPort());
            properties.getOnlyOffice().setPlatformBaseUrl("http://platform:8080");

            provider.revokeAllEditingRights(document);

            Map<String, Object> body = JSON.parseObject(requestBody.get(),
                new TypeReference<Map<String, Object>>() { });
            assertEquals("drop", body.get("c"));
            assertEquals(EDITOR_KEY, body.get("key"));
            assertEquals(false, body.containsKey("users"));
            assertNotNull(body.get("token"));
        }
        finally
        {
            server.stop(0);
        }
    }

    @Test
    void shouldUseSignedBodyClaimsWhenOnlyOfficeSendsTokenInBody()
    {
        String outboxToken = jwtService.sign(Map.of(
            "key", EDITOR_KEY,
            "status", 6,
            "url", "http://onlyoffice-documentserver/cache/force-save.docx"), Duration.ofMinutes(5));

        Map<String, Object> verified = provider.verifyCallback(accessToken(), null, document,
            Map.of("token", outboxToken));

        assertEquals(6L, jwtService.numberValue(verified.get("status")));
        assertEquals(EDITOR_KEY, verified.get("key"));
    }

    @Test
    void shouldSupportHeaderTokenAndRejectUnsignedBodyOverrides()
    {
        String sourceUrl = "http://onlyoffice-documentserver/cache/final-save.docx";
        String outboxToken = jwtService.sign(Map.of("payload", Map.of(
            "key", EDITOR_KEY,
            "status", 2,
            "url", sourceUrl)), Duration.ofMinutes(5));
        Map<String, Object> opened = new LinkedHashMap<>();
        opened.put("key", EDITOR_KEY);
        opened.put("status", 2);
        opened.put("url", sourceUrl);

        Map<String, Object> verified = provider.verifyCallback(accessToken(), "Bearer " + outboxToken,
            document, opened);
        assertEquals(sourceUrl, verified.get("url"));

        opened.put("url", "http://onlyoffice-documentserver/cache/forged.docx");
        assertThrows(ServiceException.class,
            () -> provider.verifyCallback(accessToken(), "Bearer " + outboxToken, document, opened));
    }

    private String accessToken()
    {
        return jwtService.sign(Map.of(
            "purpose", "DOCUMENT_CALLBACK",
            "documentId", 42L,
            "editorKey", EDITOR_KEY), Duration.ofHours(1));
    }
}
