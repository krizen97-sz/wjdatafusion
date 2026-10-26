package com.hm.manage.service.impl;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.imageio.ImageIO;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblLayoutType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblLayoutType;
import com.hm.common.exception.ServiceException;

/** Period-level reporting only: no execution log or connection payload is embedded. */
final class AutoInspectionPeriodReport
{
    private static final DateTimeFormatter COMPACT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final String[] COUNTS = {"normalCount", "abnormalCount", "warningCount", "skippedCount"};
    private static final Color[] COLORS = {new Color(52, 153, 104), new Color(207, 75, 83),
            new Color(216, 157, 48), new Color(148, 160, 177)};

    static final class Period
    {
        final LocalDate begin;
        final LocalDate end;
        final boolean monthly;

        private Period(LocalDate begin, LocalDate end, boolean monthly)
        {
            this.begin = begin;
            this.end = end;
            this.monthly = monthly;
        }

        String title() { return monthly ? "自动化巡检月报" : "自动化巡检周报"; }
        String label() { return begin + " 至 " + end.minusDays(1); }
        String fileName()
        {
            return title() + "_" + (monthly ? YearMonth.from(begin).toString().replace("-", "")
                    : COMPACT.format(begin) + "-" + COMPACT.format(end.minusDays(1))) + ".docx";
        }
    }

    static Period resolve(Map<String, Object> params)
    {
        String mode = text(params, "reportMode", "WEEK");
        try
        {
            if ("MONTH".equalsIgnoreCase(mode))
            {
                YearMonth month = YearMonth.parse(text(params, "month", YearMonth.now().toString()));
                return new Period(month.atDay(1), month.plusMonths(1).atDay(1), true);
            }
            if (!"WEEK".equalsIgnoreCase(mode))
            {
                throw new IllegalArgumentException("unknown report mode");
            }
            LocalDate day = LocalDate.parse(text(params, "weekDate", LocalDate.now().toString()));
            LocalDate begin = day.with(DayOfWeek.MONDAY);
            return new Period(begin, begin.plusDays(7), false);
        }
        catch (RuntimeException e)
        {
            throw new ServiceException("请选择有效的周或月份");
        }
    }

    static byte[] write(Period period, List<Map<String, Object>> groups,
                        List<Map<String, Object>> days, List<Map<String, Object>> issues) throws IOException
    {
        Map<String, Long> totals = totals(groups);
        Map<String, Map<String, Object>> scopes = new LinkedHashMap<>();
        for (Map<String, Object> group : groups)
        {
            Map<String, Object> scope = scopes.computeIfAbsent(scopeKey(group), key -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("label", scopeLabel(group));
                row.put("groupCount", 0L);
                return row;
            });
            scope.put("groupCount", number(scope, "groupCount") + 1);
            for (String key : List.of("totalCount", "normalCount", "abnormalCount", "warningCount", "skippedCount"))
            {
                scope.put(key, number(scope, key) + number(group, key));
            }
        }
        List<Map<String, Object>> sortedIssues = new ArrayList<>(issues);
        sortedIssues.sort(Comparator.<Map<String, Object>>comparingLong(row -> number(row, "abnormalCount")).reversed()
                .thenComparing(Comparator.<Map<String, Object>>comparingLong(row -> number(row, "warningCount")).reversed()));

        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream())
        {
            var section = document.getDocument().getBody().addNewSectPr();
            // Set optional section fields through DOM; the bundled POI schema subset omits their Java types.
            var sectionNode = section.getDomNode();
            String namespace = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
            var size = sectionNode.getOwnerDocument().createElementNS(namespace, "w:pgSz");
            size.setAttributeNS(namespace, "w:w", "11906");
            size.setAttributeNS(namespace, "w:h", "16838");
            sectionNode.appendChild(size);
            var margins = sectionNode.getOwnerDocument().createElementNS(namespace, "w:pgMar");
            for (String side : List.of("top", "bottom", "left", "right"))
                margins.setAttributeNS(namespace, "w:" + side, side.equals("top") || side.equals("bottom") ? "960" : "1020");
            sectionNode.appendChild(margins);

            paragraph(document, period.title(), 20, true, ParagraphAlignment.CENTER);
            paragraph(document, "统计周期：" + period.label() + "    生成："
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), 9, false, ParagraphAlignment.CENTER);
            table(document, List.of("巡检人员签字", "用户签字", "确认日期"),
                    List.of(List.of("________________", "________________", "________________")));
            heading(document, "一、周期巡检概况");
            table(document, List.of("覆盖归属", "计划 / 模板组合", "执行次数", "正常 / 异常 / 关注 / 未执行"),
                    List.of(List.of(String.valueOf(scopes.size()), String.valueOf(groups.size()),
                            String.valueOf(totals.get("totalCount")), counts(totals))));
            if (totals.get("totalCount") == 0)
            {
                paragraph(document, "本周期暂无巡检记录，无法据此判断现场或平台是否健康。", 10, false, ParagraphAlignment.LEFT);
            }
            else
            {
                paragraph(document, "执行结果分布与每日执行趋势", 10, true, ParagraphAlignment.LEFT);
                XWPFRun image = document.createParagraph().createRun();
                try
                {
                    image.addPicture(new ByteArrayInputStream(chart(period, totals, days)), Document.PICTURE_TYPE_PNG,
                            "period-summary.png", Units.toEMU(480), Units.toEMU(148));
                }
                catch (org.apache.poi.openxml4j.exceptions.InvalidFormatException e)
                {
                    throw new IOException("无法生成报告图表", e);
                }
                legend(document, totals);
                paragraph(document, "异常与关注按检查对象合并为 " + issues.size() + " 项，详见汇总清单。", 10, false, ParagraphAlignment.LEFT);
            }
            paragraph(document, "正常率按全部已记录执行统计；无记录不代表正常。例行、高频及手动执行均计入。", 9, false, ParagraphAlignment.LEFT);

            heading(document, "二、现场与主平台汇总");
            List<List<String>> scopeRows = new ArrayList<>();
            for (Map<String, Object> scope : scopes.values())
            {
                scopeRows.add(List.of(text(scope, "label", "-"), String.valueOf(number(scope, "groupCount")),
                        String.valueOf(number(scope, "totalCount")), rate(scope), counts(scope), periodStatus(scope)));
            }
            table(document, List.of("归属", "计划 / 模板", "执行", "正常率", "正常 / 异常 / 关注 / 未执行", "周期结果"), scopeRows);

            heading(document, "三、检查内容汇总");
            List<List<String>> groupRows = new ArrayList<>();
            for (Map<String, Object> group : groups)
            {
                groupRows.add(List.of(scopeLabel(group), text(group, "templateName", "未命名模板")
                                + "\n" + text(group, "planName", "手动执行"),
                        String.valueOf(number(group, "totalCount")), String.valueOf(number(group, "abnormalCount")),
                        String.valueOf(number(group, "warningCount")), latestStatus(group)));
            }
            table(document, List.of("归属", "模板 / 计划", "执行次数", "异常", "关注", "周期末最近结果"), groupRows);

            heading(document, "四、异常与关注汇总");
            if (issues.isEmpty())
            {
                paragraph(document, totals.get("totalCount") == 0 ? "本周期无可分析的执行记录。"
                        : "本周期已记录的巡检结果中未发现异常或关注项。", 10, false, ParagraphAlignment.LEFT);
            }
            else
            {
                List<List<String>> issueRows = new ArrayList<>();
                for (Map<String, Object> issue : sortedIssues)
                {
                    Map<String, Object> evidence = issue;
                    if ("EXECUTION".equals(issue.get("issueLevel")))
                    {
                        evidence = new LinkedHashMap<>(issue);
                        for (Map<String, Object> group : groups)
                        {
                            if (sameGroup(group, issue))
                            {
                                evidence.put("lastNormalTime", group.get("lastNormalTime"));
                                evidence.put("lastSkippedTime", group.get("lastSkippedTime"));
                                break;
                            }
                        }
                    }
                    issueRows.add(List.of(scopeLabel(issue), text(issue, "stepName", "执行过程") + "\n"
                                    + text(issue, "targetName", "执行异常"),
                            number(issue, "abnormalCount") + " / " + number(issue, "warningCount"),
                            timeLabel(issue.get("firstIssueTime")) + "\n至 " + timeLabel(lastIssue(issue)),
                            issueStatus(evidence)));
                }
                table(document, List.of("归属", "步骤 / 检查对象", "异常 / 关注次数", "首次至最近出现", "周期内最近情况"), issueRows);
            }
            document.write(output);
            return output.toByteArray();
        }
    }

    static String scopeKey(Map<String, Object> row)
    {
        return Objects.toString(row.get("siteId"), "-") + "/" + Objects.toString(row.get("mainPlatformId"), "-");
    }

    private static boolean sameGroup(Map<String, Object> a, Map<String, Object> b)
    {
        return scopeKey(a).equals(scopeKey(b)) && Objects.equals(a.get("templateId"), b.get("templateId"))
                && Objects.equals(a.get("planId"), b.get("planId"));
    }

    static Map<String, Long> totals(List<Map<String, Object>> rows)
    {
        Map<String, Long> result = new LinkedHashMap<>();
        for (String key : List.of("totalCount", "normalCount", "abnormalCount", "warningCount", "skippedCount"))
        {
            result.put(key, rows.stream().mapToLong(row -> number(row, key)).sum());
        }
        return result;
    }

    private static String scopeLabel(Map<String, Object> row)
    {
        String site = text(row, "siteName", "未归属现场");
        return row.get("mainPlatformId") == null ? site + " / 现场公共"
                : site + " / " + text(row, "mainPlatformName", "未命名主平台");
    }

    static String latestStatus(Map<String, Object> row)
    {
        LocalDateTime normal = timestamp(row.get("lastNormalTime"));
        LocalDateTime abnormal = timestamp(row.get("lastAbnormalTime"));
        LocalDateTime warning = timestamp(row.get("lastWarningTime"));
        LocalDateTime skipped = timestamp(row.get("lastSkippedTime"));
        LocalDateTime latest = List.of(normal, abnormal, warning, skipped).stream().max(Comparator.naturalOrder()).orElse(LocalDateTime.MIN);
        if (latest.equals(LocalDateTime.MIN)) return "无结果";
        // Ties cannot prove recovery: prefer the more severe observed state.
        if (latest.equals(abnormal)) return "异常";
        if (latest.equals(warning)) return "关注";
        if (latest.equals(skipped)) return "未执行";
        return "正常";
    }

    static String issueStatus(Map<String, Object> row)
    {
        String latest = latestStatus(row);
        return "正常".equals(latest) ? "已恢复（周期内）" : latest;
    }

    private static Object lastIssue(Map<String, Object> row)
    {
        return timestamp(row.get("lastAbnormalTime")).compareTo(timestamp(row.get("lastWarningTime"))) >= 0
                ? row.get("lastAbnormalTime") : row.get("lastWarningTime");
    }

    private static String periodStatus(Map<String, Object> row)
    {
        if (number(row, "abnormalCount") > 0) return "曾出现异常";
        if (number(row, "warningCount") > 0) return "存在关注";
        if (number(row, "skippedCount") > 0) return "包含未执行";
        return number(row, "totalCount") > 0 ? "全部正常" : "无记录";
    }

    private static String counts(Map<String, ? extends Object> row)
    {
        return number(row, "normalCount") + " / " + number(row, "abnormalCount") + " / "
                + number(row, "warningCount") + " / " + number(row, "skippedCount");
    }

    private static long number(Map<String, ? extends Object> row, String key)
    {
        Object value = row.get(key);
        return value instanceof Number ? ((Number) value).longValue() : 0;
    }

    private static String rate(Map<String, Object> row)
    {
        long total = number(row, "totalCount");
        return total == 0 ? "-" : BigDecimal.valueOf(number(row, "normalCount")).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "%";
    }

    private static String text(Map<String, Object> row, String key, String fallback)
    {
        String value = Objects.toString(row.get(key), "").strip();
        return value.isEmpty() ? fallback : value;
    }

    private static LocalDateTime timestamp(Object value)
    {
        if (value instanceof java.sql.Timestamp t) return t.toLocalDateTime();
        if (value instanceof Date d) return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        if (value instanceof LocalDateTime t) return t;
        if (value == null) return LocalDateTime.MIN;
        try { return LocalDateTime.parse(value.toString().replace(' ', 'T')); }
        catch (RuntimeException ignored) { return LocalDateTime.MIN; }
    }

    private static String timeLabel(Object value)
    {
        LocalDateTime time = timestamp(value);
        return time.equals(LocalDateTime.MIN) ? "-" : TIME.format(time);
    }

    private static void heading(XWPFDocument document, String text)
    {
        XWPFParagraph paragraph = paragraph(document, text, 12, true, ParagraphAlignment.LEFT);
        paragraph.setKeepNext(true);
        paragraph.setSpacingBefore(180);
    }

    private static XWPFParagraph paragraph(XWPFDocument document, String text, int size, boolean bold, ParagraphAlignment align)
    {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(align);
        paragraph.setSpacingAfter(90);
        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setFontFamily("Microsoft YaHei");
        run.setFontFamily("宋体", XWPFRun.FontCharRange.eastAsia);
        run.setFontSize(size);
        run.setBold(bold);
        run.setColor(size <= 9 ? "617184" : "233347");
        return paragraph;
    }

    private static void table(XWPFDocument document, List<String> headers, List<List<String>> rows)
    {
        XWPFTable table = document.createTable(1, headers.size());
        table.setWidth(9866);
        CTTblLayoutType layout = table.getCTTbl().getTblPr().addNewTblLayout();
        layout.setType(STTblLayoutType.FIXED);
        int[] weights = columnWeights(headers);
        var grid = table.getCTTbl().addNewTblGrid();
        for (int i = 0; i < weights.length; i++)
            grid.addNewGridCol().setW(BigInteger.valueOf(9866L * weights[i] / 100));
        table.getRow(0).setRepeatHeader(true);
        writeCells(table.getRow(0), headers, weights, true);
        if (rows.isEmpty())
        {
            List<String> empty = new ArrayList<>();
            for (int i = 0; i < headers.size(); i++) empty.add(i == 0 ? "暂无记录" : "-");
            writeCells(table.createRow(), empty, weights, false);
        }
        for (List<String> row : rows) writeCells(table.createRow(), row, weights, false);
    }

    private static int[] columnWeights(List<String> headers)
    {
        if (headers.contains("模板 / 计划")) return new int[] {24, 38, 10, 8, 8, 12};
        if (headers.contains("步骤 / 检查对象")) return new int[] {24, 34, 10, 18, 14};
        if (headers.contains("周期结果")) return new int[] {30, 10, 9, 11, 24, 16};
        return headers.size() == 3 ? new int[] {34, 33, 33} : new int[] {18, 22, 16, 44};
    }

    private static void writeCells(XWPFTableRow row, List<String> values, int[] weights, boolean header)
    {
        row.setCantSplitRow(true);
        for (int i = 0; i < values.size(); i++)
        {
            XWPFTableCell cell = row.getCell(i);
            cell.setWidth(String.valueOf(9866L * weights[i] / 100));
            if (header) cell.setColor("EDF2F7");
            XWPFParagraph p = cell.getParagraphs().get(0);
            p.setSpacingBefore(60);
            p.setSpacingAfter(60);
            XWPFRun run = p.createRun();
            run.setFontFamily("Microsoft YaHei");
            run.setFontFamily("宋体", XWPFRun.FontCharRange.eastAsia);
            run.setFontSize(9);
            run.setBold(header);
            run.setColor("233347");
            String[] lines = values.get(i).split("\n", -1);
            for (int line = 0; line < lines.length; line++)
            {
                if (line > 0) run.addBreak();
                run.setText(lines[line]);
            }
        }
    }

    private static void legend(XWPFDocument document, Map<String, Long> totals)
    {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingAfter(100);
        String[] labels = {"正常", "异常", "关注", "未执行"};
        for (int i = 0; i < labels.length; i++)
        {
            XWPFRun run = paragraph.createRun();
            run.setFontFamily("Microsoft YaHei");
            run.setFontFamily("宋体", XWPFRun.FontCharRange.eastAsia);
            run.setFontSize(9);
            run.setColor(String.format("%02X%02X%02X", COLORS[i].getRed(), COLORS[i].getGreen(), COLORS[i].getBlue()));
            run.setText(labels[i] + " " + totals.get(COUNTS[i]) + "    ");
        }
        XWPFRun note = paragraph.createRun();
        note.setFontSize(9);
        note.setText("横轴：日期；纵轴：每日执行次数");
    }

    private static byte[] chart(Period period, Map<String, Long> totals, List<Map<String, Object>> days) throws IOException
    {
        BufferedImage image = new BufferedImage(1440, 444, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try
        {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
            long total = totals.get("totalCount");
            int angle = 90;
            long consumed = 0;
            for (int i = 0; i < COUNTS.length; i++)
            {
                int endAngle = 90 - (int) Math.round((consumed + totals.get(COUNTS[i])) * 360.0 / Math.max(total, 1));
                int arc = angle - endAngle;
                g.setColor(COLORS[i]);
                g.fillArc(38, 38, 320, 320, angle, -arc);
                consumed += totals.get(COUNTS[i]);
                angle = endAngle;
            }
            g.setColor(Color.WHITE);
            g.fillOval(97, 97, 202, 202);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 42));
            g.setColor(new Color(35, 51, 71));
            String label = String.valueOf(total);
            g.drawString(label, 198 - g.getFontMetrics().stringWidth(label) / 2, 213);

            Map<String, Map<String, Object>> byDate = new LinkedHashMap<>();
            for (Map<String, Object> day : days) byDate.put(text(day, "reportDate", ""), day);
            int dayCount = (int) java.time.temporal.ChronoUnit.DAYS.between(period.begin, period.end);
            long max = Math.max(1, days.stream().mapToLong(row -> number(row, "totalCount")).max().orElse(0));
            int tickCount = (int) Math.min(4, max);
            long ceiling = (long) Math.ceil(max / (double) tickCount) * tickCount;
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 21));
            for (int tick = 0; tick <= tickCount; tick++)
            {
                int y = 355 - (int) Math.round(tick * 304.0 / tickCount);
                g.setColor(new Color(225, 231, 238));
                g.setStroke(new BasicStroke(1));
                g.drawLine(470, y, 1415, y);
                g.setColor(new Color(97, 113, 132));
                g.drawString(String.valueOf(ceiling * tick / tickCount), 405, y + 7);
            }
            double slot = 940.0 / dayCount;
            for (int d = 0; d < dayCount; d++)
            {
                LocalDate date = period.begin.plusDays(d);
                Map<String, Object> day = byDate.getOrDefault(date.toString(), Map.of());
                int x = (int) (477 + d * slot);
                int bottom = 355;
                for (int i = 0; i < COUNTS.length; i++)
                {
                    int height = (int) Math.round(number(day, COUNTS[i]) * 304.0 / ceiling);
                    g.setColor(COLORS[i]);
                    g.fillRect(x, bottom - height, Math.max(4, (int) slot - 7), height);
                    bottom -= height;
                }
                g.setColor(new Color(97, 113, 132));
                String dayLabel = String.valueOf(date.getDayOfMonth());
                g.drawString(dayLabel, x, 393);
            }
        }
        finally { g.dispose(); }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream())
        {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        }
    }
}
