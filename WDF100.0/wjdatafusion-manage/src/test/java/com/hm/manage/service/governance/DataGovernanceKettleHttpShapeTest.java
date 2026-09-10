package com.hm.manage.service.governance;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.databind.*;
import com.hm.common.core.domain.entity.SysUser;
import com.hm.common.core.domain.model.LoginUser;
import com.hm.manage.controller.DataGovernanceKettleController;
import java.nio.file.Path;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static com.hm.manage.service.governance.DataGovernanceKettleService.*;

class DataGovernanceKettleHttpShapeTest
{
    @TempDir Path temporary;
    @Test void realFastjsonSerializationKeepsAllNestedWorkerValuesAsJsonArraysObjectsAndScalars() throws Exception
    {
        DataGovernanceKettleProperties properties = new DataGovernanceKettleProperties(); properties.setEnabled(true); properties.setStorageDir(temporary.toString());
        ObjectMapper mapper = new ObjectMapper();
        DataGovernanceKettleClient worker = new DataGovernanceKettleClient(properties) {
            @Override public JsonNode request(String method, String path, Object body)
            {
                if (method.equals("PUT")) return mapper.valueToTree(Map.of("validation", Map.of("valid", true)));
                if (path.equals("/health")) return mapper.valueToTree(Map.of("status", "UP", "protocolVersion", 1, "engine", "original-kettle"));
                if (path.equals("/capabilities")) return mapper.valueToTree(Map.of("steps", List.of(Map.of("id", "Dummy", "className", "example.Dummy", "name", "Dummy", "category", "Flow", "loadable", true))));
                if (path.endsWith("/validate")) return mapper.valueToTree(Map.of("valid", true, "nodes", List.of(Map.of("name", "source", "fields", List.of(Map.of("name", "value", "type", "String"))))));
                if (path.endsWith("/events?after=0")) return mapper.valueToTree(Map.of("events", List.of(Map.of("seq", 1, "type", "row", "row", Map.of("value", "合成数据", "ok", true))), "nextCursor", 1, "state", "SUCCEEDED"));
                return mapper.valueToTree(Map.of("state", "SUCCEEDED", "nodes", List.of(Map.of("name", "source", "rows", List.of(Map.of("value", "合成数据")))),
                    "files", List.of(Map.of("name", "result.csv", "bytes", 14)), "finalized", true, "exitCode", 0, "finishedAt", 123.5, "schemaVersion", 2, "errors", 0));
            }
        };
        var service = new DataGovernanceKettleService(properties, DataGovernanceKettleApiTest.crypto(), worker);
        var scheduler = new DataGovernanceKettleScheduler(service, properties, Clock.systemUTC(), Runnable::run);
        var controller = new DataGovernanceKettleController(service, scheduler);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new LoginUser(7L, 1L, new SysUser(), Set.of("*:*:*")), null, List.of()));
        try
        {
            String definition = (String)service.save(null, new DefinitionInput("shape", null, DataGovernanceKettleXml.encode(DataGovernanceKettleApiTest.xml())), 7).get("id");
            String runId = (String)service.submit(definition, new RunInput(1L, "preview", "source", 37, "shape"), 7).get("id");
            JSONObject run = JSON.parseObject(JSON.toJSONString(controller.run(runId))).getJSONObject("data");
            assertEquals(1, run.getJSONArray("nodes").size()); assertEquals("合成数据", run.getJSONArray("nodes").getJSONObject(0).getJSONArray("rows").getJSONObject(0).getString("value"));
            assertEquals(1, run.getJSONArray("files").size()); assertTrue(run.getBooleanValue("finalized")); assertEquals(0, run.getIntValue("exitCode"));
            assertEquals(123.5, run.getDoubleValue("finishedAt")); assertEquals(2, run.getIntValue("schemaVersion")); assertEquals(0, run.getIntValue("errors"));
            assertEquals("source", run.getString("previewStep")); assertEquals(37, run.getIntValue("rowLimit"));
            JSONObject events = JSON.parseObject(JSON.toJSONString(controller.events(runId, 0))).getJSONObject("data");
            assertEquals(1, events.getJSONArray("events").size()); assertEquals(1, events.getIntValue("nextCursor")); assertTrue(events.getJSONArray("events").getJSONObject(0).getJSONObject("row").getBooleanValue("ok"));
            JSONObject validation = JSON.parseObject(JSON.toJSONString(controller.validate(definition))).getJSONObject("data");
            assertTrue(validation.getBooleanValue("valid")); assertEquals("String", validation.getJSONArray("nodes").getJSONObject(0).getJSONArray("fields").getJSONObject(0).getString("type"));
            JSONObject status = JSON.parseObject(JSON.toJSONString(controller.status())).getJSONObject("data"); assertEquals(1, status.getIntValue("protocolVersion"));
            JSONObject catalog = JSON.parseObject(JSON.toJSONString(controller.catalog())).getJSONObject("data"); assertEquals(1, catalog.getJSONArray("steps").size());
            assertFalse(JSON.toJSONString(controller.events(runId, 0)).contains("bigDecimal"));
        }
        finally { SecurityContextHolder.clearContext(); scheduler.close(); service.close(); }
    }
}
