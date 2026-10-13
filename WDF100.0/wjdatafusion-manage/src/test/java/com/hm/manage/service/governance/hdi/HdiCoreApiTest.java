package com.hm.manage.service.governance.hdi;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hm.common.constant.CacheConstants;
import com.hm.common.core.domain.entity.SysUser;
import com.hm.common.core.domain.model.LoginUser;
import com.hm.common.core.redis.RedisCache;
import com.hm.manage.config.DataGovernanceHdiCoreSecurityConfig.CoreSessionFilter;
import com.hm.manage.controller.DataGovernanceHdiCoreController;
import jakarta.servlet.http.Cookie;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Real isolated database and servlet/CSRF contracts. Compiler/worker transports are controlled doubles, not execution evidence. */
class HdiCoreApiTest {
    @TempDir Path temp;
    JdbcTemplate jdbc; HdiTaskStore store; HdiCoreSessions sessions; HdiCoreProperties properties;
    HdiCompilerClient compiler; HdiCoreRuns runs; HdiCoreCatalog catalog; HdiCoreAssets assets; MockMvc mvc;
    final Map<String, LoginUser> logins = new HashMap<>();
    final AtomicReference<JsonNode> submitted = new AtomicReference<>();
    LoginUser editor, other; HdiCoreSessions.Session editorSession, otherSession;

    @BeforeEach void setup() throws Exception {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:hdi_api_" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        dataSource.setDriverClassName("org.h2.Driver");
        Path sql = Path.of("../sql/data_governance_hdi_core_upgrade_20261013.sql");
        if (!Files.isRegularFile(sql)) sql = Path.of("sql/data_governance_hdi_core_upgrade_20261013.sql");
        assertTrue(Files.isRegularFile(sql), "Use the real additive task schema in the isolated test database");
        new ResourceDatabasePopulator(new FileSystemResource(sql)).execute(dataSource);
        jdbc = new JdbcTemplate(dataSource); store = new HdiTaskStore(jdbc, new DataSourceTransactionManager(dataSource));
        properties = new HdiCoreProperties(); properties.setEnabled(true); properties.setPublicOrigin("https://platform.example");
        temp = temp.toRealPath(); Files.setPosixFilePermissions(temp, PosixFilePermissions.fromString("rwx------")); properties.setStateDir(temp.toString());
        RedisCache redis = mock(RedisCache.class);
        when(redis.getCacheObject(anyString())).thenAnswer(call -> logins.get(call.getArgument(0)));
        sessions = new HdiCoreSessions(properties, redis, store);
        editor = login(7L, "editor"); other = login(8L, "other");
        editorSession = sessions.create(editor, originRequest(), false); otherSession = sessions.create(other, originRequest(), false);
        compiler = mock(HdiCompilerClient.class); runs = mock(HdiCoreRuns.class); catalog = mock(HdiCoreCatalog.class); assets = mock(HdiCoreAssets.class);
        when(compiler.prepare(anyMap(), any())).thenAnswer(call -> {
            Map<String, Object> context = call.getArgument(0); ObjectNode canonical = ((ObjectNode)call.getArgument(1)).deepCopy(); submitted.set(canonical.deepCopy());
            ObjectNode flow = (ObjectNode)canonical.get("flowVo");
            flow.put("id", Long.parseLong(context.get("businessTaskId").toString())).put("userId", Long.parseLong(context.get("ownerId").toString()))
                .put("groupId", Long.parseLong(context.get("workspaceId").toString())).put("projectCode", context.get("projectCode").toString());
            canonical.put("flowId", flow.path("id").longValue());
            if (canonical.has("panelUIInfo")) canonical.set("panelUiInfo", canonical.remove("panelUIInfo"));
            for (String key : List.of("links", "streamVoList", "paramVoList")) if (!canonical.has(key)) canonical.set(key, HdiProtocol.JSON.createArrayNode());
            ObjectNode snapshot = HdiProtocol.JSON.createObjectNode(); snapshot.set("context", HdiProtocol.JSON.valueToTree(context)); snapshot.set("visual", canonical);
            String snapshotJson = snapshot.toString(); ObjectNode result = HdiProtocol.JSON.createObjectNode(); result.set("canonicalVisual", canonical);
            result.put("snapshotJson", snapshotJson).put("snapshotSha256", HdiTaskStore.sha256(snapshotJson.getBytes(StandardCharsets.UTF_8))).set("diagnostics", HdiProtocol.JSON.createArrayNode());
            return result;
        });
        when(runs.resources()).thenReturn(HdiProtocol.JSON.readTree("{\"available\":true,\"bindings\":[{\"id\":\"1\",\"name\":\"registered target\",\"state\":\"CONFIGURED\"}]}"));
        @SuppressWarnings("unchecked") ObjectProvider<HdiCoreRuns> runProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked") ObjectProvider<HdiCoreKeys> keyProvider = mock(ObjectProvider.class);
        when(runProvider.getIfAvailable()).thenReturn(runs); when(keyProvider.getIfAvailable()).thenReturn(new HdiCoreKeys(properties));
        HdiCoreApi api = new HdiCoreApi(properties, store, sessions, catalog, assets, compiler, keyProvider, runProvider);
        mvc = MockMvcBuilders.standaloneSetup(new DataGovernanceHdiCoreController(api)).addFilters(new CoreSessionFilter(sessions)).build();
    }

    private LoginUser login(long id, String name) {
        var account = new SysUser(); account.setUserId(id); account.setUserName(name); account.setNickName(name);
        var user = new LoginUser(id, 1L, account, Set.of("governance:flow:list", "governance:flow:edit", "governance:flow:test"));
        user.setToken("login-" + id); user.setExpireTime(System.currentTimeMillis() + 300000);
        logins.put(CacheConstants.LOGIN_TOKEN_KEY + user.getToken(), user); return user;
    }
    private MockHttpServletRequest originRequest() { var request = new MockHttpServletRequest(); request.addHeader("Origin", properties.getPublicOrigin()); return request; }
    private MvcResult request(MockHttpServletRequestBuilder request, HdiCoreSessions.Session session, boolean csrf) throws Exception {
        request.header("Origin", properties.getPublicOrigin()).header("Sec-Fetch-Site", "same-origin");
        if (session != null) { request.cookie(new Cookie(HdiCoreSessions.COOKIE, session.opaque)); if (csrf) request.header(HdiCoreSessions.CSRF_HEADER, session.csrf); }
        return mvc.perform(request).andReturn();
    }
    private JsonNode json(MvcResult result, int expected) throws Exception { assertEquals(expected, result.getResponse().getStatus(), result.getResponse().getContentAsString()); return HdiProtocol.JSON.readTree(result.getResponse().getContentAsByteArray()); }
    private ObjectNode draft(String name) {
        ObjectNode visual = HdiProtocol.JSON.createObjectNode(); visual.putObject("flowVo").put("flowDisplayName", name).put("folderId", -1).put("flowType", 0).put("projectCode", HdiTaskStore.project(7));
        visual.putObject("nodes"); visual.put("status", false); visual.putArray("paramVoList"); return visual;
    }
    private String encoded(JsonNode visual) throws Exception {
        String uri = URLEncoder.encode(visual.toString(), StandardCharsets.UTF_8).replace("+", "%20");
        return HdiProtocol.JSON.writeValueAsString(Map.of("visualBase64", Base64.getEncoder().encodeToString(uri.getBytes(StandardCharsets.UTF_8))));
    }
    private long create(String name) throws Exception {
        JsonNode result = json(request(post("/detl/flow/saveFlowVisual/save").contentType("application/json").content(encoded(draft(name))), editorSession, true), 200);
        assertEquals("0", result.path("code").asText()); assertTrue(result.path("data").isIntegralNumber()); return result.path("data").longValue();
    }
    private JsonNode read(long id) throws Exception { return json(request(get("/detl/flow/getFlowVisualById/" + id), editorSession, false), 200).path("data"); }

    @Test void bootstrapIsARealSessionCsrfAndWorkspaceNotALicenseResponse() throws Exception {
        MvcResult html = request(get("/detl/"), editorSession, false);
        assertEquals(200, html.getResponse().getStatus()); assertTrue(html.getResponse().getContentAsString().contains("<meta name=\"_csrf\" content=\"" + editorSession.csrf + "\"/>"));
        JsonNode context = json(request(get("/detl/_platform/context"), editorSession, false), 200).path("data");
        assertEquals("7", context.path("user").path("id").asText()); assertFalse(context.has("license")); assertFalse(context.has("csrf"));
        JsonNode workspace = json(request(get("/detl/workspace/getByProduct").param("product", "iDataFusion DFP"), editorSession, false), 200).path("data");
        assertEquals(store.workspace().id(), workspace.path("workspaceId").intValue());
        JsonNode key = json(request(get("/detl/getPublicKey"), editorSession, false), 200).path("data"); assertTrue(key.path("publicKey").asText().startsWith("-----BEGIN PUBLIC KEY-----"));
    }

    @Test void originalCreateListAndReopenUseActualOwnedDatabaseRows() throws Exception {
        long id = create("中文流程"); assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dg_hdi_task WHERE owner_id=7", Integer.class));
        JsonNode list = json(request(post("/detl/flow/findFlowPage").param("pageNo", "1").param("pageSize", "20").contentType("application/json")
            .content("{\"keyword\":\"中文\",\"folderId\":-1,\"projectCode\":\"rynew-user-7\"}"), editorSession, true), 200).path("data");
        assertEquals(1, list.path("total").intValue()); assertEquals(id, list.path("list").get(0).path("id").longValue());
        JsonNode visual = read(id); assertEquals(1, visual.path("flowVo").path("_platformRevision").intValue()); assertEquals(-1, visual.path("flowVo").path("flowStatus").intValue());
        assertTrue(visual.path("nodes").isObject()); assertTrue(visual.path("links").isArray());
        assertEquals(404, request(get("/detl/flow/getFlowVisualById/" + id), otherSession, false).getResponse().getStatus());
        JsonNode foreignList = json(request(post("/detl/flow/findFlowPage").contentType("application/json").content("{}"), otherSession, true), 200).path("data"); assertEquals(0, foreignList.path("total").intValue());
    }

    @Test void updatePersistsOriginalFieldsAndRejectsStaleWindowRevision() throws Exception {
        long id = create("版本流程"); ObjectNode visual = (ObjectNode)read(id);
        visual.putObject("panelUIInfo").put("x", 48).put("scale", 0.75); visual.remove("panelUiInfo");
        ObjectNode node = ((ObjectNode)visual.path("nodes")).putObject("101"); node.put("id", 101).put("adapterName", "dataGeneratorInput");
        node.putArray("adapterData").addObject().put("fieldName", "message").put("fieldExpression", "中文 <a> + &");
        JsonNode saved = json(request(post("/detl/flow/saveFlowVisual/save").contentType("application/json").content(encoded(visual)), editorSession, true), 200);
        assertEquals(id, saved.path("data").longValue()); assertFalse(submitted.get().path("flowVo").has("_platformRevision"));
        JsonNode reopened = read(id); assertEquals(2, reopened.path("flowVo").path("_platformRevision").intValue());
        assertEquals("中文 <a> + &", reopened.path("nodes").path("101").path("adapterData").get(0).path("fieldExpression").asText());
        assertEquals(0.75, reopened.path("panelUiInfo").path("scale").doubleValue());
        json(request(post("/detl/flow/saveFlowVisual/save").contentType("application/json").content(encoded(visual)), editorSession, true), 409);
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM dg_hdi_revision WHERE task_id=?", Integer.class, id));
    }

    @Test void csrfPermissionsOwnershipAndUnsupportedDirectoriesRejectBeforePreparation() throws Exception {
        String payload = encoded(draft("受保护任务"));
        json(request(post("/detl/flow/saveFlowVisual/save").contentType("application/json").content(payload), editorSession, false), 403);
        editor.setPermissions(Set.of("governance:flow:list"));
        json(request(post("/detl/flow/saveFlowVisual/save").contentType("application/json").content(payload), editorSession, true), 403);
        editor.setPermissions(Set.of("governance:flow:list", "governance:flow:edit"));
        ObjectNode foreign = draft("越界"); ((ObjectNode)foreign.get("flowVo")).put("userId", 8);
        json(request(post("/detl/flow/saveFlowVisual/save").contentType("application/json").content(encoded(foreign)), editorSession, true), 403);
        ((ObjectNode)foreign.get("flowVo")).remove("userId"); ((ObjectNode)foreign.get("flowVo")).put("folderId", 9);
        json(request(post("/detl/flow/saveFlowVisual/save").contentType("application/json").content(encoded(foreign)), editorSession, true), 422);
        verify(compiler, never()).prepare(anyMap(), any()); assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dg_hdi_task", Integer.class));
    }

    @Test void realPreparationFailureRollsBackTaskAndRevision() throws Exception {
        doThrow(new HdiTaskFault(503, "原编译器尚未就绪")).when(compiler).prepare(anyMap(), any());
        json(request(post("/detl/flow/saveFlowVisual/save").contentType("application/json").content(encoded(draft("失败草稿"))), editorSession, true), 503);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dg_hdi_task", Integer.class)); assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dg_hdi_revision", Integer.class));
    }

    @Test void publicationAndStatusFiltersUseRealRecordsWithoutInventingAnExecution() throws Exception {
        long id = create("准备运行"); JsonNode visual = read(id);
        json(request(post("/detl/flow/saveFlowVisual/push").contentType("application/json").content(encoded(visual)), editorSession, true), 200);
        HdiTaskStore.Task stored = store.get(7, id); assertEquals(2L, stored.publishedRevision()); assertEquals("READY", stored.state());
        JsonNode notRun = json(request(post("/detl/flow/findFlowPage").contentType("application/json").content("{\"flowStatus\":\"0\"}"), editorSession, true), 200).path("data");
        assertEquals(1, notRun.path("total").intValue());
        JsonNode draft = json(request(post("/detl/flow/findFlowPage").contentType("application/json").content("{\"flowStatus\":\"-1\"}"), editorSession, true), 200).path("data");
        assertEquals(0, draft.path("total").intValue());
        JsonNode noReadyNative = json(request(post("/detl/flow/findFlowPage").contentType("application/json").content("{\"flowStatus\":\"5\"}"), editorSession, true), 200).path("data");
        assertEquals(0, noReadyNative.path("total").intValue());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dg_hdi_run", Integer.class)); verify(runs, never()).startRun(anyLong(), anyLong(), anyLong());
        JsonNode binding = json(request(get("/detl/globalConfig/instanceManage/findByGroupId/" + store.workspace().id()), editorSession, false), 200).path("data").get(0);
        assertEquals(1, binding.path("totalFlowCount").intValue()); assertEquals(0, binding.path("activeFlowCount").intValue());
        assertEquals("PLATFORM_OWNER_REGISTERED", binding.path("countScope").asText());
    }

    @Test void preferenceChangesArePersistedPerUserAndUnimplementedWritesNeverSucceed() throws Exception {
        json(request(post("/detl/flow/saveColumnDisplayName").contentType("application/json").content("[\"nodeSize\",\"createTime\"]"), editorSession, true), 200);
        JsonNode own = json(request(get("/detl/flow/getColumnDisplayName"), editorSession, false), 200).path("data"); assertEquals("nodeSize", own.get(0).asText());
        assertTrue(json(request(get("/detl/flow/getColumnDisplayName"), otherSession, false), 200).path("data").isEmpty());
        json(request(post("/detl/flow/deleteSelected").contentType("application/json").content("[1]"), editorSession, true), 422);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dg_hdi_preference", Integer.class));
    }

    @Test void staticAndResourceRequestsStillRequireCurrentPlatformSession() throws Exception {
        when(assets.read("/detl/host.mjs")).thenReturn(new HdiCoreAssets.Asset("export {}".getBytes(StandardCharsets.UTF_8), "text/javascript"));
        assertEquals(401, request(get("/detl/host.mjs"), null, false).getResponse().getStatus());
        assertEquals(200, request(get("/detl/host.mjs"), editorSession, false).getResponse().getStatus());
        json(request(get("/detl/globalConfig/instanceManage/findAvailableInstances").param("groupId", "999"), editorSession, false), 403);
        JsonNode resource = json(request(get("/detl/globalConfig/instanceManage/findAvailableInstances"), editorSession, false), 200).path("data").get(0);
        assertEquals("CONFIGURED", resource.path("bindingState").asText()); assertFalse(resource.has("pid")); assertFalse(resource.has("cpuUsed"));
        logins.remove(CacheConstants.LOGIN_TOKEN_KEY + editor.getToken());
        assertEquals(401, request(get("/detl/host.mjs"), editorSession, false).getResponse().getStatus());
    }
}
