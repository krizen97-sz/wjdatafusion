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
import org.junit.jupiter.api.AfterEach;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import com.hm.framework.web.service.SysPermissionService;
import com.hm.system.service.ISysUserService;
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
    HdiCompilerClient compiler; HdiCoreSchedules schedules; HdiCoreRuns runs; HdiCoreCatalog catalog; HdiCoreAssets assets; MockMvc mvc;
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
        when(compiler.schedule(any(), eq("Asia/Shanghai"))).thenAnswer(call -> {
            JsonNode config = call.getArgument(0);
            if (!Set.of(1, 2).contains(config.path("scheduleType").asInt())) throw new HdiTaskFault(422, "Unsupported test schedule");
            ObjectNode result = HdiProtocol.JSON.createObjectNode(); result.set("scheduleConfig", config.deepCopy());
            if (config.path("scheduleType").asInt() == 1) { result.put("mode", "MANUAL"); result.putArray("cronExpressions"); }
            else { result.put("mode", "CRON"); result.putArray("cronExpressions").add("0 0 19 * * ?"); } return result;
        });
        schedules = new HdiCoreSchedules(properties, store, compiler, runs, mock(ISysUserService.class), mock(SysPermissionService.class),
            Clock.fixed(Instant.parse("2099-10-13T10:59:58Z"), ZoneOffset.UTC));
        @SuppressWarnings("unchecked") ObjectProvider<HdiCoreSchedules> scheduleProvider = mock(ObjectProvider.class);
        when(scheduleProvider.getIfAvailable()).thenReturn(schedules);
        HdiCoreApi api = new HdiCoreApi(properties, store, sessions, catalog, assets, compiler, keyProvider, runProvider, scheduleProvider);
        mvc = MockMvcBuilders.standaloneSetup(new DataGovernanceHdiCoreController(api)).addFilters(new CoreSessionFilter(sessions)).build();
    }

    @AfterEach void closeSchedules() { if (schedules != null) schedules.close(); }

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
        assertEquals("flowStatus", json(request(get("/detl/flow/getColumnDisplayName"), otherSession, false), 200).path("data").get(1).asText());
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
    private long published(String name) throws Exception {
        long id = create(name); store.publish(editor.getUserId(), id, 1); return id;
    }
    private ObjectNode runConfig(long id) throws Exception {
        return (ObjectNode)json(request(get("/detl/flow/getStartConfig").param("flowId", Long.toString(id)), editorSession, false), 200).path("data");
    }
    private ObjectNode saveStart(long id, ObjectNode config, int start) {
        ((ObjectNode)config.path("schedule")).put("start", start).put("nodeIds", Long.toString(id));
        ObjectNode body = HdiProtocol.JSON.createObjectNode(); body.putObject("condition").putArray("flowIds").add(id); body.set("startConfig", config); return body;
    }
    private MvcResult submitStart(ObjectNode body, HdiCoreSessions.Session session, boolean csrf) throws Exception {
        return request(post("/detl/flow/saveFlowStartConfig").contentType("application/json").content(body.toString()), session, csrf);
    }
    @Test void originalRunDialogGetsSelectedParametersFromTheStoredRevision() throws Exception {
        when(catalog.listParams(2, null, null)).thenReturn(List.of(Map.of("paramName", "etl.task.log.level", "paramDisplayName", "日志级别",
            "paramDefaultValue", "INFO", "loadFlag", 1, "valueType", "select", "values", List.of(Map.of("configName", "INFO", "configValue", "INFO")))));
        ObjectNode draft = draft("原任务属性"); draft.withArray("paramVoList").addObject().put("paramName", "etl.task.log.level").put("paramValue", "DEBUG");
        long id = json(request(post("/detl/flow/saveFlowVisual/push").contentType("application/json").content(encoded(draft)), editorSession, true), 200).path("data").longValue();
        ObjectNode config = runConfig(id); JsonNode parameter = config.path("taskParams").get(0).path("params").get(0);
        assertEquals("DEBUG", parameter.path("paramValue").asText()); assertEquals("INFO", parameter.path("defaultValue").asText()); assertEquals(1, parameter.path("selected").intValue());
        assertEquals(1, config.path("schedule").path("_platformRevision").intValue());
        assertFalse(config.path("platformSchedule").path("configured").asBoolean());
        ObjectNode query = HdiProtocol.JSON.createObjectNode().put("typeId", 2).put("onlySelected", true);
        query.putArray("params").add(parameter.deepCopy());
        assertEquals("DEBUG", json(request(post("/detl/globalConfig/paramManage/getParamCategory").contentType("application/json").content(query.toString()), editorSession, true), 200).path("data").get(0).path("params").get(0).path("paramValue").asText());
        ((ObjectNode)parameter).put("paramValue", "WARN");
        json(submitStart(saveStart(id, config, 1), editorSession, true), 422);
        assertNull(store.getSchedule(editor.getUserId(), id)); verify(runs, never()).startRun(anyLong(), anyLong(), anyLong());
    }

    @Test void cycleConfigurationUsesRealDurablePlanAndStopDisablesWithoutInventingARun() throws Exception {
        long id = published("每日运行"); ObjectNode config = runConfig(id);
        ((ObjectNode)config.path("schedule")).put("scheduleType", 2).put("unit", 4).put("timeInterval", "1").put("executeTime", "19:00:00");
        json(submitStart(saveStart(id, config, 1), editorSession, true), 200);
        HdiTaskStore.SchedulePlan plan = store.getSchedule(editor.getUserId(), id); assertTrue(plan.enabled()); assertEquals(1, plan.revision());
        assertEquals(Instant.parse("2099-10-13T11:00:00Z"), plan.nextFireAt());
        assertEquals(2, runConfig(id).path("schedule").path("scheduleType").intValue());
        assertEquals(5, read(id).path("flowVo").path("flowStatus").intValue());
        JsonNode scheduled = json(request(post("/detl/flow/findFlowPage").contentType("application/json").content("{\"flowStatus\":\"5\"}"), editorSession, true), 200).path("data");
        assertEquals(1, scheduled.path("total").intValue());
        JsonNode idle = json(request(post("/detl/flow/findFlowPage").contentType("application/json").content("{\"flowStatus\":\"0\"}"), editorSession, true), 200).path("data");
        assertEquals(0, idle.path("total").intValue());
        assertEquals(4, read(id).path("flowVo").path("scheduleVo").path("unit").intValue());
        json(request(get("/detl/flow/stopFlow/" + id), editorSession, true), 200);
        assertFalse(store.getSchedule(editor.getUserId(), id).enabled()); assertNull(store.getSchedule(editor.getUserId(), id).nextFireAt());
        assertEquals(0, store.runs(editor.getUserId(), id).size()); verify(runs, never()).startRun(anyLong(), anyLong(), anyLong()); verify(runs, never()).stop(anyLong(), anyString());
    }

    @Test void cycleSaveOnlyStaysPausedAndPreviewUsesTheOriginalCompilerResponse() throws Exception {
        long id = published("暂停计划"); ObjectNode config = runConfig(id);
        ObjectNode schedule = (ObjectNode)config.path("schedule"); schedule.put("scheduleType", 2).put("unit", 4).put("timeInterval", "1").put("executeTime", "19:00:00");
        json(submitStart(saveStart(id, config, 0), editorSession, true), 200);
        assertFalse(store.getSchedule(editor.getUserId(), id).enabled()); assertNull(store.getSchedule(editor.getUserId(), id).nextFireAt());
        JsonNode times = json(request(post("/detl/flow/getExecuteTimes").contentType("application/json").content(schedule.toString()), editorSession, true), 200).path("data");
        assertEquals("2099-10-13 19:00:00", times.get(0).asText()); assertEquals(5, times.size());
        JsonNode cron = json(request(post("/detl/flow/generateCrontab").contentType("application/json").content(schedule.toString()), editorSession, true), 200).path("data");
        assertEquals("0 0 19 * * ?", cron.path("enablePeriod").asText());
        JsonNode frequencies = json(request(get("/detl/flow/getFrequencyEnums"), editorSession, false), 200).path("data");
        assertEquals(schedules.supportedFrequencies(), HdiProtocol.JSON.convertValue(frequencies, Map.class)); assertEquals(4, frequencies.path("每日").intValue());
    }

    @Test void scheduleWritesRequireCsrfOwnerAndCurrentPublishedRevision() throws Exception {
        long id = published("权限计划"); ObjectNode body = saveStart(id, runConfig(id), 1);
        json(submitStart(body, editorSession, false), 403); json(submitStart(body, otherSession, true), 404);
        ObjectNode visual = (ObjectNode)read(id); json(request(post("/detl/flow/saveFlowVisual/save").contentType("application/json").content(encoded(visual)), editorSession, true), 200);
        json(submitStart(body, editorSession, true), 409); assertNull(store.getSchedule(editor.getUserId(), id));
        editor.setPermissions(Set.of("governance:flow:list", "governance:flow:test"));
        json(submitStart(body, editorSession, true), 403); verify(runs, never()).startRun(anyLong(), anyLong(), anyLong());
    }

    @Test void unsupportedBatchOrChangedScopeNeverSavesOrStarts() throws Exception {
        long id = published("单任务"); ObjectNode body = saveStart(id, runConfig(id), 1);
        body.withObject("condition").withArray("flowIds").add(id + 1);
        json(submitStart(body, editorSession, true), 422);
        body = saveStart(id, runConfig(id), 1); body.withObject("condition").put("groupId", 999);
        json(submitStart(body, editorSession, true), 422); assertNull(store.getSchedule(editor.getUserId(), id));
        body = saveStart(id, runConfig(id), 1); ((ObjectNode)body.path("startConfig").path("schedule")).put("scheduleType", 3);
        json(submitStart(body, editorSession, true), 422); verify(runs, never()).startRun(anyLong(), anyLong(), anyLong());
    }

    @Test void manualSaveIsDurableAndRunFailureDoesNotBecomeSuccess() throws Exception {
        long id = published("一次性运行"); ObjectNode config = runConfig(id);
        json(submitStart(saveStart(id, config, 0), editorSession, true), 200);
        HdiTaskStore.SchedulePlan plan = store.getSchedule(editor.getUserId(), id);
        assertFalse(plan.enabled()); assertNull(plan.nextFireAt()); assertEquals("MANUAL", HdiProtocol.JSON.readTree(plan.planJson()).path("kind").asText());
        assertEquals(1, runConfig(id).path("schedule").path("scheduleType").intValue());
        verify(runs, never()).startRun(anyLong(), anyLong(), anyLong());
        when(runs.startRun(editor.getUserId(), id, 1)).thenThrow(new HdiTaskFault(503, "执行目标离线"));
        json(submitStart(saveStart(id, config, 1), editorSession, true), 503);
        verify(runs).startRun(editor.getUserId(), id, 1);
        assertEquals(0, store.runs(editor.getUserId(), id).size()); assertFalse(store.getSchedule(editor.getUserId(), id).enabled());
    }

    @Test void firstVisitUsesOriginalDfpColumnDefaultsButExplicitEmptyPreferenceIsPreserved() throws Exception {
        JsonNode defaults = json(request(get("/detl/flow/getColumnDisplayName"), editorSession, false), 200).path("data");
        assertEquals(List.of("folderPath", "flowStatus", "instanceStatus", "frequency", "execTime", "flowIncrStatus", "referrer", "userName", "createTime", "updateTime", "updateUser", "tableChangeStatus"), HdiProtocol.JSON.convertValue(defaults, List.class));
        json(request(post("/detl/flow/saveColumnDisplayName").contentType("application/json").content("[]"), editorSession, true), 200);
        assertTrue(json(request(get("/detl/flow/getColumnDisplayName"), editorSession, false), 200).path("data").isEmpty());
    }

    @Test void originalNodeFormsCanReadPluginUiInfoAndAdapterParameterCategories() throws Exception {
        when(catalog.getAdapterUiInfo("fileOutput")).thenReturn(Map.of("adapterTableUse", "false"));
        assertEquals("false", json(request(get("/detl/globalConfig/groupAdapterManage/getAdapterUIInfo").param("adapterName", "fileOutput"), editorSession, false), 200)
            .path("data").path("adapterTableUse").asText());
        for (int typeId : new int[]{6, 9}) for (String[] plugin : new String[][]{{"dataGeneratorInput", "input"}, {"fieldMapping", "preproc"}, {"fileOutput", "output"}}) {
            when(catalog.listParams(eq(typeId), eq(plugin[0]), eq(plugin[1]), anySet())).thenReturn(List.of(Map.of("paramName", "original.param", "paramDisplayName", "原参数",
                "paramDefaultValue", "10", "loadFlag", 1, "valueType", "el-input", "paramCategory", "原分类", "paramSubCategory", "原子类", "values", List.of())));
            ObjectNode query = HdiProtocol.JSON.createObjectNode().put("typeId", typeId).put("adapterName", plugin[0]).put("stepTypeName", plugin[1]).put("onlySelected", true);
            query.putArray("params").addObject().put("paramName", "original.param").put("paramValue", "20");
            JsonNode groups = json(request(post("/detl/globalConfig/paramManage/getParamCategory").contentType("application/json").content(query.toString()), editorSession, true), 200).path("data");
            JsonNode row = groups.get(0).path("subCategories").get(0).path("params").get(0);
            assertEquals("原分类", groups.get(0).path("category").asText()); assertEquals("20", row.path("paramValue").asText());
            assertEquals("10", row.path("defaultValue").asText()); assertEquals("el-input", row.path("valueType").asText()); assertEquals(1, row.path("loadFlag").intValue());
            query.put("keyword", "不会匹配");
            assertTrue(json(request(post("/detl/globalConfig/paramManage/getParamCategory").contentType("application/json").content(query.toString()), editorSession, true), 200).path("data").isEmpty());
            json(request(post("/detl/globalConfig/paramManage/getParamCategory").contentType("application/json").content(query.toString()), editorSession, false), 403);
        }
        when(catalog.listParams(eq(9), eq("jdbcInput"), eq("input"), anySet())).thenThrow(new HdiTaskFault(400, "不支持该插件"));
        json(request(post("/detl/globalConfig/paramManage/getParamCategory").contentType("application/json")
            .content("{\"typeId\":9,\"adapterName\":\"jdbcInput\",\"stepTypeName\":\"input\",\"params\":[]}"), editorSession, true), 400);
    }

    @Test void jdbcTypeDictionaryIsReadOnlyOriginalMetadataAndStillRequiresPostCsrf() throws Exception {
        when(catalog.getJdbcDbTypes()).thenReturn(List.of(1, 2, 16));
        JsonNode types = json(request(post("/detl/globalConfig/dataSourceManage/getJdbcDbTypeList"), editorSession, true), 200).path("data");
        assertEquals(List.of(1, 2, 16), HdiProtocol.JSON.convertValue(types, List.class));
        json(request(post("/detl/globalConfig/dataSourceManage/getJdbcDbTypeList"), editorSession, false), 403);
        json(request(get("/detl/globalConfig/dataSourceManage/getJdbcDbTypeList"), editorSession, false), 405);
        json(request(post("/detl/globalConfig/dataSourceManage/getJdbcDbTypeList").contentType("application/json").content("{\"connection\":\"ignored\"}"), editorSession, true), 400);
        verifyNoInteractions(runs);
    }

    @Test void fieldMappingEditorReceivesActualGroupedFieldsAndNoUnsupportedFelFunctions() throws Exception {
        JsonNode functions = json(request(get("/detl/globalConfig/felManager/getFelClassifyList").param("name", ""), editorSession, false), 200).path("data");
        assertTrue(functions.isArray()); assertTrue(functions.isEmpty());
        String fields = "{\"fields\":[{\"fieldSrcName\":\"message\",\"fieldSrcType\":\"string\"},{\"fieldSrcName\":\"count\",\"fieldSrcType\":\"int\",\"fieldDstName\":\"renamed\",\"fieldDstType\":\"long\"},{\"fieldSrcName\":\"unspecified\"},{\"fieldSrcName\":\"second\",\"fieldSrcType\":\"string\"}]}";
        JsonNode groups = json(request(post("/detl/globalConfig/felManager/getFelFieldClassifyList").contentType("application/json").content(fields), editorSession, true), 200).path("data");
        assertEquals("long", groups.get(0).path("fieldType").asText()); assertEquals("renamed", groups.get(0).path("fieldNames").get(0).asText());
        assertEquals("other", groups.get(1).path("fieldType").asText());
        assertEquals(List.of("message", "second"), HdiProtocol.JSON.convertValue(groups.get(2).path("fieldNames"), List.class)); assertEquals(2, groups.get(2).path("size").intValue());
        json(request(post("/detl/globalConfig/felManager/getFelFieldClassifyList").contentType("application/json").content(fields), editorSession, false), 403);
        verifyNoInteractions(runs); verify(compiler, never()).compile(anyString(), anyString(), anyMap());
    }
    @Test void localFileFormGetsRealColumnTypeDirectoryWithoutExternalConnections() throws Exception {
        when(catalog.getFileColumnTypes()).thenReturn(List.of(Map.of("fieldType", "string", "columnType", "string")));
        for (String dbType : List.of("18", "FTP", "-1", "2", "MySQL", "120", "RabbitMQ")) {
            JsonNode result = json(request(get("/detl/globalConfig/dataSourceManage/getDataTypeList").param("dbType", dbType), editorSession, false), 200).path("data");
            assertEquals("string", result.get(0).path("columnType").asText());
        }
        json(request(get("/detl/globalConfig/dataSourceManage/getDataTypeList").param("dbType", "999"), editorSession, false), 422);
        assertTrue(json(request(post("/detl/globalConfig/dataSourceManage/getDatasourceOptions").contentType("application/json").content("{\"dbType\":18,\"inOutFlags\":[2]}"), editorSession, true), 200).path("data").isEmpty());
        JsonNode capabilities = json(request(get("/detl/globalConfig/dataSourceManage/getDataSourceExtendInfo"), editorSession, false), 200).path("data");
        assertEquals(6, capabilities.size()); capabilities.forEach(value -> assertEquals("", value.asText()));
        verifyNoInteractions(runs);
    }

    @Test void globalParameterCountReflectsTheEmptyPlatformRegistryWithoutEnablingExpressions() throws Exception {
        JsonNode count = json(request(post("/detl/globalConfig/globalParamManage/count").param("keyword", ""), editorSession, true), 200).path("data");
        assertEquals(0, count.intValue());
        json(request(post("/detl/globalConfig/globalParamManage/count"), editorSession, false), 403);
        json(request(post("/detl/globalConfig/globalParamManage/count").contentType("application/json").content("{}"), editorSession, true), 400);
        verifyNoInteractions(runs);
    }

    @Test void datasourceGetReturnsTypeOptionsWhilePostReturnsTheSeparateEmptyConnectionRegistry() throws Exception {
        when(catalog.getDatasourceTypeOptions()).thenReturn(List.of(Map.of("name", "FTP", "value", "18", "code", "FTP")));
        JsonNode options = json(request(get("/detl/globalConfig/dataSourceManage/getDatasourceOptions"), editorSession, false), 200).path("data");
        assertEquals("FTP", options.get(0).path("name").asText()); assertEquals("18", options.get(0).path("value").asText());
        assertTrue(json(request(post("/detl/globalConfig/dataSourceManage/getDatasourceOptions").contentType("application/json").content("{\"dbType\":18}"), editorSession, true), 200).path("data").isEmpty());
        json(request(post("/detl/globalConfig/dataSourceManage/getDatasourceOptions").contentType("application/json").content("{}"), editorSession, false), 403);
        verifyNoInteractions(runs);
    }
    @Test void missingNodeObservationsAreAnEmptySetWithExplicitUnavailableHeadersAndOwnerChecks() throws Exception {
        long id = create("无观测草稿");
        MvcResult result = request(post("/detl/statistics/modulesSpeed/" + id).contentType("application/json").content("[]"), editorSession, true);
        assertTrue(json(result, 200).path("data").isEmpty());
        assertEquals("false", result.getResponse().getHeader("X-HDI-Metrics-Available")); assertEquals("NOT_COLLECTED", result.getResponse().getHeader("X-HDI-Metrics-Reason"));
        json(request(post("/detl/statistics/modulesSpeed/" + id).contentType("application/json").content("[]"), otherSession, true), 404);
        json(request(post("/detl/statistics/modulesSpeed/" + id).contentType("application/json").content("[]"), editorSession, false), 403);
        verifyNoInteractions(runs);
    }

    @Test void fullRunPreflightAdmitsEveryOriginalConnectorButDoesNotEnableIncrementalOrUnknownPlugins() throws Exception {
        long id = create("原连接预检");
        for (String adapter : List.of("dataGeneratorInput", "fieldMapping", "fileOutput", "fileInput", "postgresqlInput", "postgresqlOutput", "kafkaInput", "kafkaV2Output", "rabbitmqInput", "rabbitmqOutput")) {
            ObjectNode visual = (ObjectNode)read(id); visual.withObject("nodes").set("101", HdiProtocol.JSON.createObjectNode().put("id", 101).put("adapterName", adapter));
            json(request(post("/detl/flow/saveFlowVisual/save").contentType("application/json").content(encoded(visual)), editorSession, true), 200);
            JsonNode result = json(request(post("/detl/flow/checkBeforeStart").param("flowId", Long.toString(id)).param("flowIncrStatus", "1"), editorSession, true), 200).path("data");
            assertTrue(result.path("incrConfigComplete").asBoolean());
        }
        json(request(post("/detl/flow/checkBeforeStart").param("flowId", Long.toString(id)).param("flowIncrStatus", "2"), editorSession, true), 422);
        ObjectNode visual = (ObjectNode)read(id); ((ObjectNode)visual.path("nodes").path("101")).put("adapterName", "unknownPlugin");
        json(request(post("/detl/flow/saveFlowVisual/save").contentType("application/json").content(encoded(visual)), editorSession, true), 200);
        json(request(post("/detl/flow/checkBeforeStart").param("flowId", Long.toString(id)).param("flowIncrStatus", "1"), editorSession, true), 422);
        verifyNoInteractions(runs);
    }

}
