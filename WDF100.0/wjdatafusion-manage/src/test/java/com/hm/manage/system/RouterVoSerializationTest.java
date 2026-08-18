package com.hm.manage.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hm.system.domain.vo.RouterVo;
import org.junit.jupiter.api.Test;

class RouterVoSerializationTest
{
    @Test
    void shouldKeepEmptyPathForDefaultChildRoute() throws Exception
    {
        RouterVo router = new RouterVo();
        router.setName("DocumentWorkspace");
        router.setPath("");
        router.setComponent("document/workspace/index");

        JsonNode json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(router));

        assertTrue(json.has("path"), "若依默认子路由必须显式返回空 path");
        assertEquals("", json.get("path").asText());
    }
}
