package com.hm.manage.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.yaml.snakeyaml.Yaml;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hm.manage.domain.bo.AutoInspectionMetricQuery;
import com.hm.manage.mapper.SupportAutoInspectionMetricsMapper;

// The optional probe supplies the admin runtime driver without adding a production dependency here.
@EnabledIfSystemProperty(named = "rynew.metrics.localSmoke", matches = "true")
class AutoInspectionMetricsLocalReadOnlyTest
{
    @Test
    @SuppressWarnings("unchecked")
    void verifiesLocalMapperAndExportsReadOnlyBrowserEvidence() throws Exception
    {
        Map<String, Object> config;
        try (var input = Files.newInputStream(Path.of(System.getProperty("rynew.metrics.config"))))
        {
            config = new Yaml().load(input);
        }
        Map<String, Object> spring = (Map<String, Object>) config.get("spring");
        Map<String, Object> datasource = (Map<String, Object>) spring.get("datasource");
        Map<String, Object> druid = (Map<String, Object>) datasource.get("druid");
        Map<String, Object> master = (Map<String, Object>) druid.get("master");
        String url = String.valueOf(master.get("url"));
        assertTrue(url.startsWith("jdbc:mysql://"));
        assertTrue(List.of("localhost", "127.0.0.1", "::1").contains(URI.create(url.substring(5)).getHost()), "This optional test only permits local MySQL");

        try (var driverLoader = new URLClassLoader(new URL[] {
                Path.of(System.getProperty("rynew.metrics.driver")).toUri().toURL()
        }, getClass().getClassLoader()))
        {
            var source = new UnpooledDataSource(driverLoader, "com.mysql.cj.jdbc.Driver", url,
                    String.valueOf(master.get("username")), String.valueOf(master.get("password")));
            Configuration mapperConfig = new Configuration(new Environment("local-read-only", new JdbcTransactionFactory(), source));
            String resource = "mapper/support/SupportAutoInspectionMetricsMapper.xml";
            try (var input = getClass().getClassLoader().getResourceAsStream(resource))
            {
                new XMLMapperBuilder(input, mapperConfig, resource, mapperConfig.getSqlFragments()).parse();
            }
            Map<String, Object> evidence = new LinkedHashMap<>();
            try (var session = new SqlSessionFactoryBuilder().build(mapperConfig).openSession(false))
            {
                session.getConnection().setReadOnly(true);
                var service = new SupportAutoInspectionMetricsServiceImpl(session.getMapper(SupportAutoInspectionMetricsMapper.class));
                var first = service.selectMetrics(new AutoInspectionMetricQuery());
                evidence.put("default:7", Map.of("code", 200, "data", first));
                assertFalse(first.getPlans().isEmpty(), "Local smoke validation needs at least one existing plan");
                for (var plan : first.getPlans().stream().limit(10).toList())
                {
                    for (int days : new int[] {1, 7, 30})
                    {
                        AutoInspectionMetricQuery query = new AutoInspectionMetricQuery();
                        query.setPlanId(plan.getPlanId());
                        query.setDays(days);
                        var result = service.selectMetrics(query);
                        assertEquals(plan.getPlanId(), result.getPlanId());
                        assertTrue(result.getSampleCount() <= result.getSampleLimit());
                        evidence.put(plan.getPlanId() + ":" + days, Map.of("code", 200, "data", result));
                    }
                }
                session.rollback();
            }
            Path output = Path.of(System.getProperty("rynew.metrics.output"));
            Files.createDirectories(output.getParent());
            ObjectMapper json = new ObjectMapper();
            json.setTimeZone(TimeZone.getDefault());
            Files.writeString(output, json.writeValueAsString(evidence));
            System.out.println("Local metric query snapshots verified: " + evidence.size());
        }
    }
}
