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
        assertThrows(ServiceException.class, () -> AutoInspectionPeriodReport.resolve(map("reportMode", "DAY")));
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
                    .getBoundSql(map("beginTime", new Date(), "endTime", new Date(), "siteId", 1L)).getSql();
            assertTrue(sql.contains("r.inspection_time >= ?"));
            assertTrue(sql.contains("r.inspection_time < ?"));
            assertTrue(sql.contains("r.site_id = ?"));
            assertTrue(sql.contains("group by"));
            assertFalse(sql.contains("result_detail"));
            assertFalse(sql.contains("password"));
            assertFalse(sql.contains("run_mode ="));
        }
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
