package com.hm.manage.mapper;

import static org.junit.jupiter.api.Assertions.*;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import com.hm.manage.controller.SupportAutoInspectionController;
import com.hm.manage.domain.bo.AutoInspectionMetricQuery;

class AutoInspectionMetricsMapperContractTest
{
    @Test
    void mapperParsesAndAllQueriesAreBoundedReadOnlyStatements() throws Exception
    {
        String resource = "mapper/support/SupportAutoInspectionMetricsMapper.xml";
        Configuration configuration = new Configuration();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource))
        {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        Map<String, Object> query = Map.of("planId", 7L, "beginTime", new Date(), "endTime", new Date(), "limit", 5001);
        String namespace = "com.hm.manage.mapper.SupportAutoInspectionMetricsMapper.";
        for (String id : new String[] { "selectPlans", "selectSamples", "selectExecutions" })
        {
            var statement = configuration.getMappedStatement(namespace + id);
            assertEquals(SqlCommandType.SELECT, statement.getSqlCommandType());
            String sql = statement.getBoundSql(query).getSql();
            assertTrue(sql.contains("r.source_type = 'AUTO'"));
            assertTrue(sql.contains("r.inspection_time >= ?"));
            assertTrue(sql.contains("r.inspection_time < ?"));
            if (!"selectPlans".equals(id))
            {
                assertTrue(sql.contains("r.plan_id = ?"));
                assertTrue(sql.contains("limit ?"));
            }
        }
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource))
        {
            String xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertFalse(xml.contains("${"));
            assertFalse(xml.contains("result_detail"));
            assertFalse(xml.contains("step_params"));
            assertFalse(xml.contains("password"));
        }
    }

    @Test
    void endpointRetainsTheExistingInspectionQueryPermission() throws Exception
    {
        var method = SupportAutoInspectionController.class.getMethod("metrics", AutoInspectionMetricQuery.class);
        assertEquals("@ss.hasPermi('support:autoInspection:query')", method.getAnnotation(PreAuthorize.class).value());
        assertArrayEquals(new String[] {"/dashboard/metrics"}, method.getAnnotation(GetMapping.class).value());
    }
}
