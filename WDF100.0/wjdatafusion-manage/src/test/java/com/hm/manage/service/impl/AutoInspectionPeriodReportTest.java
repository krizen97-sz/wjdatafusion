package com.hm.manage.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import com.hm.common.exception.ServiceException;
import com.hm.manage.domain.bo.AutoInspectionReportExportBo;
import com.hm.manage.mapper.SupportAutoInspectionMapper;

class AutoInspectionPeriodReportTest
{
    @Test
    void periodUsesExactCalendarMonthAndMondayWeek()
    {
        var month = AutoInspectionPeriodReport.resolve(map("reportMode", "MONTH", "month", "2024-02"));
        assertEquals(LocalDate.of(2024, 2, 1), month.begin);
        assertEquals(LocalDate.of(2024, 3, 1), month.end);
        assertEquals("自动化巡检月报_202402.docx", month.fileName());
        var week = AutoInspectionPeriodReport.resolve(map("weekDate", "2026-10-31"));
        assertEquals(LocalDate.of(2026, 10, 26), week.begin);
        assertEquals(LocalDate.of(2026, 11, 2), week.end);
        assertThrows(ServiceException.class, () -> AutoInspectionPeriodReport.resolve(map("reportMode", "MONTH", "month", "2026-13")));
        var day = AutoInspectionPeriodReport.resolve(map("reportMode", "DAY", "reportDate", "2026-10-31", "reportContent", "FULL"));
        assertEquals(LocalDate.of(2026, 10, 31), day.begin);
        assertEquals(LocalDate.of(2026, 11, 1), day.end);
        assertEquals("自动化巡检日报_20261031_完整版.docx", day.fileName());
        assertThrows(ServiceException.class, () -> AutoInspectionPeriodReport.resolve(map("reportMode", "DAY", "reportDate", "2026-02-30")));
        assertThrows(ServiceException.class, () -> AutoInspectionPeriodReport.resolve(map("reportContent", "UNKNOWN")));
    }

    @Test
    void fullDailyExportKeepsEveryExecutionAcrossPlansSharingATemplateAndTime() throws Exception
    {
        var records = List.of(
                map("recordId", 21L, "planId", 1L, "planName", "上午例行计划", "templateName", "共用模板",
                        "inspectionTime", "2026-10-31 09:00:00", "sourceType", "AUTO", "runMode", "ROUTINE",
                        "resultStatus", "1", "summary", "第一条执行"),
                map("recordId", 22L, "planId", 2L, "planName", "高频监测计划", "templateName", "共用模板",
                        "inspectionTime", "2026-10-31 09:00:00", "sourceType", "AUTO", "runMode", "FREQUENT",
                        "resultStatus", "2", "summary", "第二条执行"),
                map("recordId", 23L, "planId", 2L, "planName", "高频监测计划", "templateName", "共用模板",
                        "inspectionTime", "2026-10-31 09:05:00", "sourceType", "AUTO", "runMode", "FREQUENT",
                        "resultStatus", "1", "summary", "第三条执行"));
        var groups = List.of(
                map("planId", 1L, "planName", "上午例行计划", "templateId", 5L, "templateName", "共用模板", "totalCount", 1L, "normalCount", 1L),
                map("planId", 2L, "planName", "高频监测计划", "templateId", 5L, "templateName", "共用模板", "totalCount", 2L, "normalCount", 1L, "abnormalCount", 1L));
        var days = List.of(map("reportDate", "2026-10-31 09:00", "totalCount", 2L, "normalCount", 1L, "abnormalCount", 1L),
                map("reportDate", "2026-10-31 10:00", "totalCount", 1L, "normalCount", 1L));
        var issues = List.of(map("planId", 2L, "templateId", 5L, "stepName", "业务接口检查", "targetName", "统计接口",
                "abnormalCount", 1L, "firstIssueTime", "2026-10-31 09:00:00", "lastAbnormalTime", "2026-10-31 09:00:00",
                "lastNormalTime", "2026-10-31 09:05:00"));
        List<String> calls = new ArrayList<>();
        var steps = List.of(
                map("recordId", 21L, "stepResultId", 201L, "stepName", "存储容量检查", "toolCode", "SERVER_DISK",
                        "toolName", "服务器磁盘使用率检测", "compareRule", "MAX", "thresholdValue", 90, "thresholdUnit", "%"),
                map("recordId", 21L, "stepResultId", 203L, "stepName", "暂停检查", "resultStatus", "3"),
                map("recordId", 22L, "stepResultId", 202L, "stepName", "Kafka 消费检查", "toolCode", "KAFKA_LAG",
                        "toolName", "Kafka 消费积压检测", "compareRule", "MAX", "thresholdValue", 5));
        var targets = List.of(
                map("recordId", 21L, "stepResultId", 201L, "targetName", "文件服务磁盘", "resultStatus", "1",
                        "actualValue", new java.math.BigDecimal("12.340"), "actualUnit", "%",
                        "resultDetail", "挂载点 /data；已用 123G"),
                map("recordId", 21L, "stepResultId", 201L, "targetName", "归档磁盘", "resultStatus", "1", "actualValue", 0, "actualUnit", "%"),
                map("recordId", 22L, "stepResultId", 202L, "targetName", "消费组 traffic", "resultStatus", "2",
                        "actualValue", 6058, "previousValue", 6052, "changeValue", 6, "actualUnit", "条",
                        "evaluationRule", "本次值减上次值不得高于 5 条", "errorMessage", "消费积压超限"),
                map("recordId", 23L, "stepResultId", 999L, "targetName", "旧历史目标", "resultStatus", "1", "actualValue", 1));
        var mapper = (SupportAutoInspectionMapper) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {SupportAutoInspectionMapper.class}, (proxy, method, args) -> {
                    calls.add(method.getName());
                    if ("selectStepResultsByRecordIds".equals(method.getName()) || "selectTargetResultsByRecordIds".equals(method.getName()))
                    {
                        assertEquals(List.of(21L, 22L, 23L), args[0]);
                        return "selectStepResultsByRecordIds".equals(method.getName()) ? steps : targets;
                    }
                    var filters = (Map<?, ?>) args[0];
                    assertFalse(filters.containsKey("planId"));
                    assertFalse(filters.containsKey("templateId"));
                    assertFalse(filters.containsKey("runMode"));
                    assertEquals(java.sql.Timestamp.valueOf("2026-10-31 00:00:00").getTime(), ((Date) filters.get("beginTime")).getTime());
                    assertEquals(java.sql.Timestamp.valueOf("2026-11-01 00:00:00").getTime(), ((Date) filters.get("endTime")).getTime());
                    return switch (method.getName())
                    {
                        case "selectPeriodReportGroups" -> groups;
                        case "selectPeriodReportDays" -> days;
                        case "selectPeriodReportIssues" -> issues;
                        case "selectPeriodReportRecords" -> records;
                        case "selectPlanList" -> List.of(
                                map("planId", 1L, "status", "0", "cronExpression", "0 0 9 * * ?", "cronConfig", "{\"type\":\"daily\",\"time\":\"09:00:00\"}"),
                                map("planId", 3L, "status", "0", "planName", "备用链路计划", "templateName", "备用链路模板", "cronExpression", "0 0 10 * * ?"));
                        default -> throw new AssertionError(method.getName());
                    };
                });
        var service = new SupportAutoInspectionServiceImpl();
        ReflectionTestUtils.setField(service, "autoInspectionMapper", mapper);
        var request = new AutoInspectionReportExportBo();
        request.setReportType("PERIOD_REPORT");
        request.setReportMode("DAY");
        request.setReportDate("2026-10-31");
        request.setReportContent("FULL");
        var response = new MockHttpServletResponse();
        service.exportRecord(response, request);
        assertEquals(7, calls.size());
        assertEquals("selectPeriodReportRecords", calls.get(3));
        byte[] bytes = response.getContentAsByteArray();
        try (var doc = new XWPFDocument(new ByteArrayInputStream(bytes)))
        {
            String report = allText(doc);
            assertTrue(report.contains("执行记录（3 条）"));
            for (String expected : List.of("当前调度：每日 09:00:00", "备用链路计划", "本周期无执行记录",
                    "服务器磁盘使用率检测", "文件服务磁盘", "归档磁盘", "本次：12.34%", "本次：0%", "不得高于 90%",
                    "本次：6058条", "上次：6052条", "变化：6条", "消费积压超限", "暂停检查", "旧历史目标"))
                assertTrue(report.contains(expected), expected);
            var paragraphs = doc.getParagraphs().stream().map(paragraph -> paragraph.getText()).toList();
            assertTrue(paragraphs.indexOf("一、巡检计划与模板") < paragraphs.indexOf("二、按日巡检结果"));
            assertTrue(paragraphs.indexOf("二、按日巡检结果") < paragraphs.indexOf("三、周期结果与状态变化"));
            var daily = doc.getTables().stream().filter(t -> t.getText().contains("当日结论")).findFirst().orElseThrow();
            assertEquals("3", daily.getRow(1).getCell(1).getText());
            assertEquals("2", daily.getRow(1).getCell(2).getText());
            assertEquals("1", daily.getRow(1).getCell(3).getText());
            assertEquals(1, doc.getAllPictures().size());
            var table = doc.getTables().stream().filter(t -> t.getText().contains("执行时间 / 编号")).findFirst().orElseThrow();
            assertEquals(4, table.getNumberOfRows());
            for (int i = 0; i < records.size(); i++)
            {
                assertTrue(table.getRow(i + 1).getCell(0).getText().contains("#" + records.get(i).get("recordId")));
                assertTrue(table.getRow(i + 1).getCell(1).getText().contains((String) records.get(i).get("planName")));
            }
        }
        String output = System.getProperty("rynew.report.output");
        if (output != null)
        {
            Path dir = Path.of(output);
            Files.createDirectories(dir);
            Files.write(dir.resolve("双计划完整版日报.docx"), bytes);
        }
    }

    @Test
    void templateExportDocumentsConcreteMethodsAndStandardsWithoutCredentials() throws Exception
    {
        var api = map("targetId", 71L, "targetName", "过车统计接口", "url", "https://svc:URL_PASSWORD@example.test/events?sig=PRIVATEQUERY",
                "httpMethod", "POST", "passwordCipher", "PASSWORD_CIPHER", "secretCipher", "SECRET_CIPHER",
                "extraParams", """
                        {"headers":[{"key":"X-Api-Key","value":"PRIVATE_HEADER"}],
                         "assertions":[{"type":"JSON_NUMBER","path":"data.total","operator":"GTE","expected":"100"},
                         {"type":"HEADER","path":"Set-Cookie","operator":"CONTAINS","expected":"PRIVATE_COOKIE"}]}
                        """);
        var database = map("targetId", 72L, "targetName", "统计数据库", "host", "db.example.test", "path", "events",
                "resultPath", "amount", "extraParams", """
                        {"databaseType":"POSTGRESQL","query":"select count(*) as amount from events","resultMode":"FIRST_VALUE"}
                        """);
        var mapper = (SupportAutoInspectionMapper) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {SupportAutoInspectionMapper.class}, (proxy, method, args) -> switch (method.getName())
                {
                    case "selectTemplateById" -> map("templateId", 8L, "templateName", "业务服务巡检", "description", "检查过车数据");
                    case "selectStepsByTemplateId" -> List.of(
                            map("stepId", 51L, "stepName", "检查响应", "toolCode", "HTTP_API_TEST", "toolName", "接口调用测试"),
                            map("stepId", 52L, "stepName", "检查入库", "toolCode", "DATABASE_QUERY", "toolName", "数据库查询检查",
                                    "thresholdValue", 100, "compareRule", "MIN", "thresholdUnit", "条"));
                    case "selectStepTargetsByTemplateId" -> List.of(map("stepId", 51L, "targetId", 71L), map("stepId", 52L, "targetId", 72L));
                    case "selectTargetById" -> Long.valueOf(71L).equals(args[0]) ? api : database;
                    default -> throw new AssertionError(method.getName());
                });
        var service = new SupportAutoInspectionServiceImpl();
        ReflectionTestUtils.setField(service, "autoInspectionMapper", mapper);
        var response = new MockHttpServletResponse();
        service.exportTemplate(response, 8L);
        byte[] bytes = response.getContentAsByteArray();
        try (var doc = new XWPFDocument(new ByteArrayInputStream(bytes)))
        {
            String report = allText(doc);
            for (String expected : List.of("业务服务巡检", "巡检内容", "巡检标准", "巡检方法", "POST https://example.test/events",
                    "响应字段 data.total 大于等于 100", "不得低于 100条", "POSTGRESQL db.example.test:5432",
                    "select count(*) as amount from events"))
                assertTrue(report.contains(expected), expected);
            for (String secret : List.of("URL_PASSWORD", "PRIVATEQUERY", "PASSWORD_CIPHER", "SECRET_CIPHER", "PRIVATE_HEADER", "PRIVATE_COOKIE"))
                assertFalse(report.contains(secret), secret);
        }
        String output = System.getProperty("rynew.report.output");
        if (output != null)
        {
            Path path = Path.of(output);
            Files.createDirectories(path);
            Files.write(path.resolve("巡检模板样例.docx"), bytes);
        }
    }

    @Test
    void fullMonthIncludesEmptyDatesWithoutCountingThemAsHealthy() throws Exception
    {
        var period = AutoInspectionPeriodReport.resolve(map("reportMode", "MONTH", "month", "2026-09", "reportContent", "FULL"));
        try (var doc = new XWPFDocument(new ByteArrayInputStream(AutoInspectionPeriodReport.write(period, List.of(), List.of(), List.of()))))
        {
            var daily = doc.getTables().stream().filter(t -> t.getText().contains("当日结论")).findFirst().orElseThrow();
            assertEquals(31, daily.getNumberOfRows());
            assertEquals("2026-09-01", daily.getRow(1).getCell(0).getText());
            assertEquals("2026-09-30", daily.getRow(30).getCell(0).getText());
            assertEquals("无记录", daily.getRow(30).getCell(7).getText());
            assertTrue(doc.getAllPictures().isEmpty());
        }
    }

    @Test
    void aggregatedReportStaysSmallAndEmbedsOfflineChart() throws Exception
    {
        var period = AutoInspectionPeriodReport.resolve(map("reportMode", "MONTH", "month", "2026-09"));
        var groups = List.of(
                group(1L, 11L, "TIM平台", 100000, 99980, 12, 5, 3),
                group(1L, 12L, "ISC平台", 240, 235, 4, 1, 0),
                group(2L, null, "", 30, 30, 0, 0, 0));
        var issues = List.of(map("siteId", 1L, "siteName", "常州市武进分局",
                "mainPlatformId", 11L, "mainPlatformName", "TIM平台", "targetName", "大数据服务器 /data",
                "stepName", "服务器磁盘占用检查", "abnormalCount", 12L, "warningCount", 0L,
                "firstIssueTime", "2026-09-03 08:00:00", "lastAbnormalTime", "2026-09-25 09:00:00",
                "lastNormalTime", "2026-09-25 10:00:00", "resultDetail", "PASSWORD_MUST_NOT_BE_EXPORTED"));
        var days = new ArrayList<Map<String, Object>>();
        for (int d = 1; d <= 30; d++)
        {
            long normal = 3341L + (d <= 15 ? 1 : 0);
            long abnormal = d <= 16 ? 1L : 0L;
            long warning = d <= 6 ? 1L : 0L;
            long skipped = d <= 3 ? 1L : 0L;
            days.add(map("reportDate", String.format("2026-09-%02d", d), "totalCount", normal + abnormal + warning + skipped,
                    "normalCount", normal, "abnormalCount", abnormal, "warningCount", warning, "skippedCount", skipped));
        }
        assertEquals(AutoInspectionPeriodReport.totals(groups), AutoInspectionPeriodReport.totals(days));
        byte[] bytes = AutoInspectionPeriodReport.write(period, groups, days, issues);
        assertTrue(bytes.length < 200000);
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes)))
        {
            String text = allText(doc);
            assertEquals(1, doc.getAllPictures().size());
            assertTrue(text.contains("自动化巡检月报"));
            assertTrue(text.contains("100270"));
            assertTrue(text.contains("TIM平台"));
            assertTrue(text.contains("ISC平台"));
            assertTrue(text.contains("已恢复（周期内）"));
            assertTrue(text.contains("巡检人员签字"));
            assertFalse(text.contains("PASSWORD_MUST_NOT_BE_EXPORTED"));
            assertEquals(5, doc.getTables().size());
            assertEquals(4, doc.getTables().get(3).getRows().size());
        }
        String output = System.getProperty("rynew.report.output");
        if (output != null)
        {
            Path dir = Path.of(output);
            Files.createDirectories(dir);
            Files.write(dir.resolve(period.fileName()), bytes);
            var week = AutoInspectionPeriodReport.resolve(map("weekDate", "2026-09-23"));
            var weekDays = new ArrayList<Map<String, Object>>();
            for (int d = 21; d <= 27; d++)
                weekDays.add(map("reportDate", "2026-09-" + d, "totalCount", 1L, "normalCount", d == 24 ? 0L : 1L,
                        "abnormalCount", d == 24 ? 1L : 0L));
            var weekIssue = map("siteId", 1L, "siteName", "常州市武进分局", "mainPlatformId", 11L,
                    "mainPlatformName", "TIM平台", "stepName", "服务器磁盘占用检查", "targetName", "大数据服务器 /data",
                    "abnormalCount", 1L, "firstIssueTime", "2026-09-24 09:00:00",
                    "lastAbnormalTime", "2026-09-24 09:00:00", "lastNormalTime", "2026-09-25 09:00:00");
            Files.write(dir.resolve(week.fileName()), AutoInspectionPeriodReport.write(week,
                    List.of(group(1L, 11L, "TIM平台", 7, 6, 1, 0, 0)), weekDays, List.of(weekIssue)));
            Files.write(dir.resolve("空数据周报.docx"), AutoInspectionPeriodReport.write(week, List.of(), List.of(), List.of()));
        }
    }

    @Test
    void missingOrSkippedResultsAreNotReportedAsHealthy() throws Exception
    {
        assertEquals("未执行", AutoInspectionPeriodReport.latestStatus(map("lastSkippedTime", "2026-10-01 09:00:00")));
        var period = AutoInspectionPeriodReport.resolve(map("weekDate", "2026-10-26"));
        try (var doc = new XWPFDocument(new ByteArrayInputStream(AutoInspectionPeriodReport.write(period, List.of(), List.of(), List.of()))))
        {
            assertTrue(allText(doc).contains("无法据此判断"));
            assertTrue(doc.getAllPictures().isEmpty());
            assertFalse(allText(doc).contains("全部正常"));
        }
    }

    @Test
    void laterWarningOrSkippedResultPreventsFalseRecovery()
    {
        var state = map("lastNormalTime", "2026-10-01 11:00:00", "lastAbnormalTime", "2026-10-01 10:00:00");
        assertEquals("已恢复（周期内）", AutoInspectionPeriodReport.issueStatus(state));
        state.put("lastWarningTime", "2026-10-01 12:00:00");
        assertEquals("关注", AutoInspectionPeriodReport.issueStatus(state));
        state.put("lastSkippedTime", "2026-10-01 13:00:00");
        assertEquals("未执行", AutoInspectionPeriodReport.issueStatus(state));
        state.put("lastAbnormalTime", "2026-10-01 13:00:00");
        assertEquals("异常", AutoInspectionPeriodReport.issueStatus(state));
    }

    @Test
    void exportUsesThreeAggregatesAndReturnsOneDocxForLegacyMonthlyRequest() throws Exception
    {
        List<String> calls = new ArrayList<>();
        var mapper = (SupportAutoInspectionMapper) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {SupportAutoInspectionMapper.class}, (proxy, method, args) -> {
                    calls.add(method.getName());
                    @SuppressWarnings("unchecked") var query = (Map<String, Object>) args[0];
                    assertFalse(query.containsKey("runMode"));
                    assertEquals(java.sql.Timestamp.valueOf("2026-09-01 00:00:00").getTime(), ((Date) query.get("beginTime")).getTime());
                    assertEquals(java.sql.Timestamp.valueOf("2026-10-01 00:00:00").getTime(), ((Date) query.get("endTime")).getTime());
                    return List.of();
                });
        var service = new SupportAutoInspectionServiceImpl();
        ReflectionTestUtils.setField(service, "autoInspectionMapper", mapper);
        var query = new AutoInspectionReportExportBo();
        query.setReportType("WEEKLY_REPORT");
        query.setReportMode("MONTH");
        query.setMonth("2026-09");
        var response = new MockHttpServletResponse();
        service.exportRecord(response, query);
        assertEquals(List.of("selectPeriodReportGroups", "selectPeriodReportDays", "selectPeriodReportIssues"), calls);
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", response.getContentType());
        assertTrue(response.getHeader("Content-Disposition").contains(".docx"));
        try (var document = new XWPFDocument(new ByteArrayInputStream(response.getContentAsByteArray())))
        {
            assertTrue(allText(document).contains("自动化巡检月报"));
        }
    }

    @Test
    void mapperParsesBoundedAggregatesWithoutLoadingExecutionPayloads() throws Exception
    {
        Configuration config = new Configuration();
        String resource = "mapper/support/SupportAutoInspectionMapper.xml";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource))
        {
            new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
        }
        for (String method : List.of("selectPeriodReportGroups", "selectPeriodReportDays", "selectPeriodReportIssues"))
        {
            String sql = config.getMappedStatement(SupportAutoInspectionMapper.class.getName() + "." + method)
                    .getBoundSql(map("beginTime", new Date(), "endTime", new Date(), "siteId", 1L)).getSql().replaceAll("\\s+", " ");
            assertTrue(sql.contains("r.inspection_time >= ?"));
            assertTrue(sql.contains("r.inspection_time < ?"));
            assertTrue(sql.contains("coalesce(r.site_id, report_plan.site_id) = ?"));
            assertTrue(sql.contains("group by"));
            assertFalse(sql.contains("result_detail"));
            assertFalse(sql.contains("password"));
            assertFalse(sql.contains("run_mode ="));
        }
        String detailSql = config.getMappedStatement(SupportAutoInspectionMapper.class.getName() + ".selectPeriodReportRecords")
                .getBoundSql(map("beginTime", new Date(), "endTime", new Date(), "siteId", 1L)).getSql().replaceAll("\\s+", " ");
        assertTrue(detailSql.contains("coalesce(r.site_id, report_plan.site_id) = ?"));
        assertTrue(detailSql.contains("order by r.inspection_time, r.record_id"));
        assertFalse(detailSql.contains("group by"));
        assertFalse(detailSql.contains("limit"));
        assertFalse(detailSql.contains("and r.plan_id ="));
    }

    static Map<String, Object> group(Long siteId, Long platformId, String name, long total, long normal, long abnormal, long warning, long skipped)
    {
        return map("siteId", siteId, "siteName", siteId == 1 ? "常州市武进分局" : "常州市市局",
                "mainPlatformId", platformId, "mainPlatformName", name, "templateId", platformId,
                "templateName", name + "基础运行巡检", "planName", name + "日常与高频检测",
                "totalCount", total, "normalCount", normal, "abnormalCount", abnormal,
                "warningCount", warning, "skippedCount", skipped, "lastNormalTime", "2026-09-30 23:55:00");
    }

    static Map<String, Object> map(Object... values)
    {
        Map<String, Object> result = new HashMap<>();
        for (int i = 0; i < values.length; i += 2) result.put(String.valueOf(values[i]), values[i + 1]);
        return result;
    }

    private static String allText(XWPFDocument document)
    {
        StringBuilder text = new StringBuilder();
        document.getParagraphs().forEach(p -> text.append(p.getText()));
        document.getTables().forEach(t -> text.append(t.getText()));
        return text.toString();
    }
}
