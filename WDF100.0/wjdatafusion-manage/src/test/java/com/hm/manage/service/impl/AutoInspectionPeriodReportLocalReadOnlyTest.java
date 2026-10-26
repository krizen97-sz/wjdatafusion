package com.hm.manage.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.yaml.snakeyaml.Yaml;
import com.hm.manage.mapper.SupportAutoInspectionMapper;

@EnabledIfSystemProperty(named = "rynew.report.localSmoke", matches = "true")
class AutoInspectionPeriodReportLocalReadOnlyTest
{
    @Test
    @SuppressWarnings("unchecked")
    void aggregatesExistingLocalHistoryWithoutChangingDatabase() throws Exception
    {
        Map<String, Object> config;
        try (var input = Files.newInputStream(Path.of(System.getProperty("rynew.report.config"))))
        {
            config = new Yaml().load(input);
        }
        var spring = (Map<String, Object>) config.get("spring");
        var datasource = (Map<String, Object>) spring.get("datasource");
        var druid = (Map<String, Object>) datasource.get("druid");
        var master = (Map<String, Object>) druid.get("master");
        String url = String.valueOf(master.get("url"));
        assertTrue(List.of("localhost", "127.0.0.1", "::1").contains(URI.create(url.substring(5)).getHost()));
        try (var loader = new URLClassLoader(new URL[] {Path.of(System.getProperty("rynew.report.driver")).toUri().toURL()}, getClass().getClassLoader()))
        {
            var source = new UnpooledDataSource(loader, "com.mysql.cj.jdbc.Driver", url,
                    String.valueOf(master.get("username")), String.valueOf(master.get("password")));
            var configuration = new Configuration(new Environment("report-read-only", new JdbcTransactionFactory(), source));
            String resource = "mapper/support/SupportAutoInspectionMapper.xml";
            try (var input = getClass().getClassLoader().getResourceAsStream(resource))
            {
                new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            }
            try (var session = new SqlSessionFactoryBuilder().build(configuration).openSession(false))
            {
                session.getConnection().setReadOnly(true);
                var mapper = session.getMapper(SupportAutoInspectionMapper.class);
                var query = AutoInspectionPeriodReportTest.map("beginTime", Timestamp.valueOf("2026-09-01 00:00:00"),
                        "endTime", Timestamp.valueOf("2026-10-01 00:00:00"));
                var groups = mapper.selectPeriodReportGroups(query);
                var days = mapper.selectPeriodReportDays(query);
                var issues = mapper.selectPeriodReportIssues(query);
                assertEquals(AutoInspectionPeriodReport.totals(groups), AutoInspectionPeriodReport.totals(days));
                try (var statement = session.getConnection().prepareStatement(
                        "select count(*) from sup_auto_inspection_record where inspection_time >= ? and inspection_time < ?"))
                {
                    statement.setObject(1, query.get("beginTime"));
                    statement.setObject(2, query.get("endTime"));
                    try (var result = statement.executeQuery())
                    {
                        assertTrue(result.next());
                        assertEquals(result.getLong(1), AutoInspectionPeriodReport.totals(groups).get("totalCount"));
                    }
                }
                query.put("siteId", 1L);
                for (var group : mapper.selectPeriodReportGroups(query))
                    assertEquals(1L, ((Number) group.get("siteId")).longValue());
                for (var issue : mapper.selectPeriodReportIssues(query))
                    assertEquals(1L, ((Number) issue.get("siteId")).longValue());
                String output = System.getProperty("rynew.report.output");
                if (output != null)
                {
                    Files.createDirectories(Path.of(output));
                    Files.write(Path.of(output, "本地实际巡检月报_202609.docx"), AutoInspectionPeriodReport.write(
                            AutoInspectionPeriodReport.resolve(Map.of("reportMode", "MONTH", "month", "2026-09")), groups, days, issues));
                }
                System.out.println("Period report read-only verification: " + AutoInspectionPeriodReport.totals(groups).get("totalCount")
                        + " executions, " + groups.size() + " plan/template groups, " + issues.size() + " merged issues");
                session.rollback();
            }
        }
    }
}
