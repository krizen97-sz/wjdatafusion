package com.hm.manage.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;

import com.alibaba.fastjson2.JSON;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;

import static com.hm.manage.service.impl.AutoInspectionPeriodReport.createTable;
import static com.hm.manage.service.impl.AutoInspectionPeriodReport.heading;
import static com.hm.manage.service.impl.AutoInspectionPeriodReport.paragraph;
import static com.hm.manage.service.impl.AutoInspectionPeriodReport.planLabel;
import static com.hm.manage.service.impl.AutoInspectionPeriodReport.scopeLabel;
import static com.hm.manage.service.impl.AutoInspectionPeriodReport.writeCells;

final class AutoInspectionWordDetails
{
    private static final String NOT_RECORDED = "未记录";
    private static final String SENSITIVE = "(?:[\\w-]*(?:password|secret|token|api[_-]?key|authorization|cookie|session[_-]?id|signature)[\\w-]*)";
    private static final Pattern SENSITIVE_KEY = Pattern.compile(SENSITIVE, Pattern.CASE_INSENSITIVE);
    private static final Pattern URL = Pattern.compile("(?i)((?:https?|ftps?|sftp|mqtts?|tcp|ssl)://[^\\s；，<>\\\"']+)");
    private static final Pattern SECRET_FIELDS = Pattern.compile("(?i)((?:[\\\"']?)\\b" + SENSITIVE
            + "\\b[\\\"']?\\s*[:=]\\s*)(?:\\\"[^\\\"]*\\\"|'[^']*'|(?:Bearer |Basic )?[^\\s,;&}；，]+)");
    private static final Pattern XML_CONTROLS = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\uFFFE\\uFFFF]");
    private static final int[] ITEM_WIDTHS = {19, 16, 19, 17, 29};
    private static final int[] STANDARD_WIDTHS = {16, 28, 26, 30};
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private AutoInspectionWordDetails() {}

    record Evidence(List<Map<String, Object>> records, Map<Long, List<Map<String, Object>>> steps,
                    Map<Long, List<Map<String, Object>>> targets, Map<Long, Map<String, Object>> plans,
                    Set<Long> unexecutedPlans)
    {
        static final Evidence EMPTY = new Evidence(List.of(), Map.of(), Map.of(), Map.of(), Set.of());
    }

    private record TemplateStep(String name, String settings, List<List<String>> rows) {}

    static void writePlanOverview(XWPFDocument doc, Evidence evidence)
    {
        heading(doc, "一、巡检计划与模板");
        Map<List<String>, Map<String, Object>> plans = new LinkedHashMap<>();
        Map<List<String>, Set<List<String>>> standards = new LinkedHashMap<>();
        for (Map<String, Object> record : evidence.records)
        {
            List<String> key = List.of(value(record, "planId", planLabel(record)), value(record, "templateId", ""),
                    planLabel(record), value(record, "templateName", "未命名模板"), scopeLabel(record), source(record),
                    AutoInspectionPeriodReport.scopeKey(record));
            plans.putIfAbsent(key, record);
            Set<List<String>> rows = standards.computeIfAbsent(key, ignored -> new LinkedHashSet<>());
            for (List<String> item : executionRows(record, evidence, false))
            {
                rows.add(item);
            }
        }
        if (plans.isEmpty() && evidence.unexecutedPlans.isEmpty())
        {
            paragraph(doc, "本周期没有执行记录，暂无可汇报的计划和模板快照。", 10, false, ParagraphAlignment.LEFT);
        }
        int sequence = 0;
        for (var plan : plans.entrySet())
        {
            List<String> key = plan.getKey();
            heading(doc, ++sequence + ". " + safe(key.get(2)) + " / " + safe(key.get(3)));
            paragraph(doc, "归属：" + safe(key.get(4)) + "    执行方式：" + key.get(5)
                    + (plan.getValue().get("planId") == null ? "" : "    计划编号：" + safe(key.get(0)))
                    + (key.get(1).isEmpty() ? "" : "    模板编号：" + safe(key.get(1))), 10, false, ParagraphAlignment.LEFT);
            Long planId = id(plan.getValue().get("planId"));
            if (planId != null) writeSchedule(doc, evidence.plans.get(planId));
            Set<List<String>> rows = standards.get(key);
            if (rows.isEmpty())
            {
                paragraph(doc, "历史执行未保存步骤或目标快照。", 9, false, ParagraphAlignment.LEFT);
                continue;
            }
            int[] widths = {30, 30, 40};
            XWPFTable table = createTable(doc, List.of("巡检内容 / 工具", "检查子项", "巡检要求（执行快照）"), widths);
            rows.forEach(row -> detailRow(table, row, widths));
        }
        for (Long planId : evidence.unexecutedPlans)
        {
            Map<String, Object> plan = evidence.plans.get(planId);
            heading(doc, ++sequence + ". " + safe(planLabel(plan)) + " / " + safe(value(plan, "templateName", "未命名模板")));
            paragraph(doc, safe("归属：" + scopeLabel(plan) + "    计划编号：" + planId
                    + "    本周期无执行记录，不存在历史检查快照。"), 10, false, ParagraphAlignment.LEFT);
            writeSchedule(doc, plan);
        }
    }

    private static void writeSchedule(XWPFDocument doc, Map<String, Object> plan)
    {
        if (plan == null || value(plan, "cronExpression", "").isBlank()) return;
        Map<String, Object> cfg = object(plan.get("cronConfig"));
        String time = value(cfg, "time", "-");
        String schedule = switch (value(cfg, "type", ""))
        {
            case "daily" -> "每日 " + time;
            case "weekly" -> "每周 " + value(cfg, "weekDays", "-") + " " + time;
            case "monthly" -> "每月 " + value(cfg, "monthDays", "-") + "日 " + time;
            case "interval" -> "每 " + value(cfg, "interval", "-") + ("hour".equals(cfg.get("intervalUnit")) ? " 小时" : " 分钟");
            default -> value(plan, "cronExpression", "-");
        };
        paragraph(doc, safe("当前调度：" + schedule + "    " + enabled(plan)), 9, false, ParagraphAlignment.LEFT);
    }

    static void writeExecutions(XWPFDocument doc, Evidence evidence)
    {
        if (evidence.records.isEmpty()) return;
        heading(doc, "附录、逐次执行与检查子项");
        LocalDate currentDay = null;
        for (Map<String, Object> record : evidence.records)
        {
            LocalDateTime time = AutoInspectionPeriodReport.timestamp(record.get("inspectionTime"));
            if (!time.toLocalDate().equals(currentDay))
            {
                currentDay = time.toLocalDate();
                heading(doc, LocalDateTime.MIN.equals(time) ? "日期未记录" : currentDay.toString());
            }
            heading(doc, (LocalDateTime.MIN.equals(time) ? "-" : TIME.format(time)) + "  #" + value(record, "recordId", "-")
                    + "  " + safe(planLabel(record)) + " / " + safe(value(record, "templateName", "未命名模板")));
            List<List<String>> rows = executionRows(record, evidence, true);
            if (rows.isEmpty())
            {
                paragraph(doc, "该次执行未记录步骤或目标明细。", 9, false, ParagraphAlignment.LEFT);
                continue;
            }
            XWPFTable table = createTable(doc, List.of("步骤 / 工具", "检查子项", "巡检要求", "实测数据", "结果与执行详情"), ITEM_WIDTHS);
            rows.forEach(row -> detailRow(table, row, ITEM_WIDTHS));
        }
    }

    private static List<List<String>> executionRows(Map<String, Object> record, Evidence evidence, boolean includeResults)
    {
        Long recordId = id(record.get("recordId"));
        if (recordId == null) return List.of();
        Map<String, List<Map<String, Object>>> byStep = new LinkedHashMap<>();
        for (Map<String, Object> target : evidence.targets.getOrDefault(recordId, List.of()))
        {
            byStep.computeIfAbsent(value(target, "stepResultId", ""), ignored -> new ArrayList<>()).add(target);
        }
        List<List<String>> rows = new ArrayList<>();
        for (Map<String, Object> step : evidence.steps.getOrDefault(recordId, List.of()))
        {
            List<Map<String, Object>> items = byStep.remove(value(step, "stepResultId", ""));
            if (items == null)
            {
                rows.add(executionRow(step, Map.of(), includeResults));
            }
            else
            {
                items.forEach(item -> rows.add(executionRow(step, item, includeResults)));
            }
        }
        byStep.values().forEach(items -> items.forEach(item -> rows.add(executionRow(Map.of(), item, includeResults))));
        return rows;
    }

    private static List<String> executionRow(Map<String, Object> step, Map<String, Object> target, boolean includeResults)
    {
        Map<String, Object> data = target.isEmpty() ? step : target;
        Map<String, Object> stepInfo = step.isEmpty() ? target : step;
        String check = value(stepInfo, "stepName", "历史步骤（名称未记录）") + "\n" + toolName(stepInfo);
        String item = value(target, "targetName", target.isEmpty() ? "无子项记录" : "名称未记录");
        String requirement = value(target, "evaluationRule", "");
        String tool = value(stepInfo, "toolCode", "");
        if ("HTTP_HEALTH".equals(tool) || "HTTP_API_TEST".equals(tool))
        {
            String detail = value(target, "resultDetail", "");
            boolean httpHealth = "HTTP_HEALTH".equals(tool);
            String prefix = httpHealth ? "；期望状态：" : "；判定条件：";
            int start = detail.indexOf(prefix);
            if (start >= 0)
            {
                String[] parts = detail.substring(start + prefix.length()).split(httpHealth ? "；" : "；响应预览：", 2);
                requirement = join(requirement, (httpHealth ? "HTTP 状态码：" : "") + parts[0]);
            }
        }
        if (requirement.isBlank())
        {
            requirement = value(step, "exportRequirement", "");
        }
        if (requirement.isBlank() || "-".equals(requirement))
        {
            requirement = "未保存具体判定条件";
        }
        if (!includeResults)
        {
            String window = value(step, "timeWindowMinutes", "0");
            String timeout = value(step, "timeoutSeconds", "");
            requirement = join(requirement, !"0".equals(window) ? "统计窗口：" + window + " 分钟" : "",
                    timeout.isEmpty() ? "" : "超时：" + timeout + " 秒");
            return List.of(check, item, requirement);
        }
        String actual = "本次：" + measured(data, "actualValue");
        if (target.get("previousValue") != null || target.get("changeValue") != null
                || "PREVIOUS".equals(target.get("evaluationMode")))
        {
            actual = join(actual, "上次：" + measured(target, "previousValue"), "变化：" + measured(target, "changeValue"));
        }
        String details = join(status(data), "Y".equals(target.get("baselineFlag")) ? "仅建立基线" : "",
                value(data, target.isEmpty() ? "resultSummary" : "resultDetail", ""),
                data.get("errorMessage") == null ? "" : "异常原因：" + value(data, "errorMessage", ""));
        return List.of(check, item, requirement, actual, details);
    }

    static byte[] writeTemplate(Map<String, Object> template, List<Map<String, Object>> steps,
                                BiFunction<Map<String, Object>, Map<String, Object>, String> requirement,
                                ToIntFunction<Map<String, Object>> timeout) throws IOException
    {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream())
        {
            AutoInspectionPeriodReport.setPageLayout(doc);
            paragraph(doc, safe(value(template, "templateName", "巡检模板")), 20, true, ParagraphAlignment.CENTER);
            paragraph(doc, "巡检模板清单    编号：" + safe(value(template, "templateId", "-"))
                    + "    状态：" + enabled(template) + "    步骤：" + steps.size(), 10, false, ParagraphAlignment.CENTER);
            String description = value(template, "description", "");
            if (!description.isEmpty())
                paragraph(doc, safe(description), 10, false, ParagraphAlignment.LEFT);
            writeTemplateSteps(doc, templateSteps(steps, requirement, timeout));
            doc.write(output);
            return output.toByteArray();
        }
    }

    private static List<TemplateStep> templateSteps(List<Map<String, Object>> steps,
                                             BiFunction<Map<String, Object>, Map<String, Object>, String> requirement,
                                             ToIntFunction<Map<String, Object>> timeout)
    {
        List<TemplateStep> result = new ArrayList<>();
        for (Map<String, Object> step : steps)
        {
            Map<String, Object> params = object(step.get("stepParams"));
            List<Map<String, Object>> targets = maps(step.get("targets"));
            if (targets.isEmpty()) targets = List.of(object(step.get("target")));
            String settings = toolName(step) + "    " + enabled(step) + "    超时 " + timeout.applyAsInt(step) + " 秒";
            if (step.get("timeWindowMinutes") instanceof Number window && window.intValue() > 0)
                settings += "    统计窗口 " + window + " 分钟";
            List<List<String>> rows = new ArrayList<>();
            for (Map<String, Object> target : targets)
            {
                List<String> content = describeTool(step, target, params);
                String rule = requirement.apply(step, target);
                rows.add(List.of(value(target, "targetName", "未配置检查目标")
                                + ("1".equals(value(target, "status", "")) ? "\n停用" : ""),
                        content.get(0), "-".equals(rule) ? "未设置数值阈值；按工具取数结果判定" : rule, content.get(1)));
            }
            result.add(new TemplateStep(value(step, "stepName", "未命名步骤"), settings, rows));
        }
        return result;
    }

    private static void writeTemplateSteps(XWPFDocument doc, List<TemplateStep> steps)
    {
        if (steps.isEmpty())
            paragraph(doc, "暂无可用的模板步骤配置。", 10, false, ParagraphAlignment.LEFT);
        int index = 0;
        for (TemplateStep step : steps)
        {
            heading(doc, ++index + ". " + safe(step.name));
            paragraph(doc, safe(step.settings), 10, false, ParagraphAlignment.LEFT);
            XWPFTable table = createTable(doc, List.of("检查子项", "巡检内容", "巡检标准", "巡检方法"), STANDARD_WIDTHS);
            step.rows.forEach(row -> detailRow(table, row, STANDARD_WIDTHS));
        }
    }

    private static List<String> describeTool(Map<String, Object> step, Map<String, Object> target, Map<String, Object> p)
    {
        Map<String, Object> extra = object(target.get("extraParams"));
        String host = value(target, "host", "未配置");
        String path = value(p, "path", value(target, "path", ""));
        String topic = value(p, "topic", value(target, "topic", "未配置"));
        String group = value(p, "consumerGroup", value(target, "consumerGroup", "未配置"));
        String code = value(step, "toolCode", "");
        return switch (code)
        {
            case "KAFKA_LAG", "KAFKA_CONSUMER_PROGRESS", "KAFKA_TOPIC_ACTIVITY" -> List.of(
                    join("Kafka：" + host, "Topic：" + topic, "KAFKA_TOPIC_ACTIVITY".equals(code) ? "" : "消费组：" + group),
                    switch (code)
                    {
                        case "KAFKA_TOPIC_ACTIVITY" -> "读取 Topic 各分区末端 Offset 并求和，观察写入变化。";
                        case "KAFKA_CONSUMER_PROGRESS" -> "读取消费组已提交 Offset 和分区末端 Offset，观察消费进度与积压。";
                        default -> "读取各分区末端与消费组已提交 Offset；采样指标：" + switch (value(p, "kafkaMetric", "MAX_LAG"))
                        {
                            case "TOTAL_LAG" -> "消费组总积压";
                            case "PRODUCED_OFFSET" -> "生产总 Offset";
                            case "CONSUMED_OFFSET" -> "消费总 Offset";
                            default -> "最大分区积压";
                        };
                    });
            case "MQTT_TOPIC_ACTIVITY" -> List.of(join("MQTT：" + host + (host.contains("://") ? "" : ":" + value(target, "port", "1883")),
                    "Topic：" + value(target, "topic", value(extra, "topicFilter", "未配置")), "QoS：" + value(extra, "qos", "1")),
                    "持续订阅指定主题并读取接收计数。"
                            + ("false".equalsIgnoreCase(value(extra, "ignoreRetained", "")) ? "计入保留消息。" : "忽略保留消息。"));
            case "HTTP_COUNT", "HTTP_HEALTH", "HTTP_API_TEST" -> List.of(
                    value(target, "httpMethod", "HTTP_COUNT".equals(code) && target.get("appKey") != null ? "POST" : "GET")
                            + " " + safeUrl(value(target, "url", "未配置")),
                    switch (code)
                    {
                        case "HTTP_COUNT" -> "请求海康统计接口，从响应字段 " + value(target, "resultPath", "data.total") + " 提取数量。";
                        case "HTTP_HEALTH" -> "调用指定接口，检查 HTTP 状态码并记录响应耗时。";
                        default -> join("按配置发送请求，记录状态码和耗时，并逐项判定响应条件。",
                                "鉴权：" + authName(object(extra.get("auth"))),
                                "POST".equalsIgnoreCase(value(target, "httpMethod", "")) ? "请求体类型：" + value(extra, "bodyType", "NONE") : "");
                    });
            case "FTP_FILE_COUNT" -> List.of(host + ":" + value(target, "port", "21") + "\n目录：" + value(target, "path", "/"),
                    "列出 FTP 目标目录中的普通文件并计数，不递归。");
            case "SERVER_FILE_COUNT" -> List.of(host + ":" + value(target, "port", "55555") + "\n目录：" + (path.isEmpty() ? "未配置" : path),
                    join("通过 SSH 统计普通文件。",
                         "递归：" + ("false".equalsIgnoreCase(value(p, "recursive", "")) ? "否" : "是"),
                         "文件匹配：" + value(p, "filePattern", "不限")));
            case "SERVER_DISK", "BIG_DATA_SERVER_DISK" -> List.of(
                    host + ":" + value(target, "port", "BIG_DATA_SERVER_DISK".equals(code) ? "2343" : "55555")
                            + ("SERVER_DISK".equals(code) ? "\n挂载点：" + (path.isEmpty() ? "除根分区外的各挂载点" : path) : "\n所有分区"),
                    "SERVER_DISK".equals(code) ? "用 SSH 读取挂载点使用率，取最高使用率。"
                            : "通过 SSH 采集各分区容量、已用量、剩余量和使用率，以最高使用率判定。"
                                + ("true".equalsIgnoreCase(value(p, "includePseudo", "")) ? "包含伪文件系统。" : "不含伪文件系统。"));
            case "TCP_PORT_CHECK" -> List.of(host + ":" + value(target, "port", "未配置"),
                    "建立 TCP 连接，记录连通结果与连接耗时。");
            case "SERVER_SERVICE_STATUS" -> List.of(join(host + ":" + value(target, "port", "55555"),
                    "服务：" + value(extra, "serviceName", value(target, "path", value(p, "serviceName", "未配置")))),
                    "通过 SSH 读取 systemd 服务状态。"
                            + ("true".equalsIgnoreCase(value(extra, "autoRestart", value(p, "autoRestart", "false")))
                            ? "异常时按配置尝试重启，等待 " + value(extra, "restartWaitSeconds", value(p, "restartWaitSeconds", "5"))
                                    + " 秒后复查。" : "仅检查，不重启。"));
            case "DATABASE_QUERY" -> List.of(join(value(extra, "databaseType", "MYSQL") + " " + host + ":"
                    + value(target, "port", "POSTGRESQL".equals(value(extra, "databaseType", "")) ? "5432" : "3306"),
                    "数据库：" + value(target, "path", "未配置")),
                    join("执行只读查询；" + ("ROW_COUNT".equals(value(extra, "resultMode", "")) ? "统计返回行数。"
                            : "取首行的 " + value(target, "resultPath", "首列") + " 作为数值。"),
                            value(extra, "query", "未保存查询语句")));
            default -> List.of(value(target, "targetName", "未配置"), "此工具未保存可导出的专用采集方式。");
        };
    }

    private static String authName(Map<String, Object> auth)
    {
        return switch (value(auth, "type", "NONE"))
        {
            case "BEARER" -> "Bearer";
            case "BASIC" -> "Basic";
            case "API_KEY" -> "API Key / " + value(auth, "location", "HEADER");
            case "COOKIE" -> "Cookie";
            case "CUSTOM_HEADER" -> "自定义请求头";
            default -> "无独立鉴权配置";
        };
    }

    static String apiRequirements(Map<String, Object> options)
    {
        List<Map<String, Object>> conditions = maps(options.get("assertions"));
        if (conditions.isEmpty())
        {
            return "HTTP 状态码 200–399";
        }
        List<String> rules = new ArrayList<>();
        rules.add("以下条件全部满足：");
        for (Map<String, Object> condition : conditions)
        {
            String type = value(condition, "type", "STATUS").toUpperCase(Locale.ROOT);
            String path = value(condition, "path", value(condition, "field", ""));
            String expected = value(condition, "expected", "");
            if (!path.isEmpty() && SENSITIVE_KEY.matcher(path).find() && !expected.isEmpty())
                expected = "[已隐藏]";
            String subject = switch (type)
            {
                case "STATUS" -> "HTTP 状态码";
                case "LATENCY" -> "响应耗时（ms）";
                case "ARRAY_LENGTH" -> "数组长度 " + path;
                case "BODY_TEXT", "BODY_REGEX", "TEXT" -> "响应正文";
                case "HEADER" -> "响应头 " + path;
                default -> "响应字段 " + path;
            };
            String operator = "BODY_REGEX".equals(type) ? "REGEX" : value(condition, "operator", "EQ").toUpperCase(Locale.ROOT);
            rules.add(rules.size() + ". " + subject + " " + switch (operator)
            {
                case "EQ", "=", "==" -> "等于";
                case "NE", "!=", "<>" -> "不等于";
                case "LT", "<" -> "小于";
                case "LTE", "<=" -> "小于等于";
                case "GT", ">" -> "大于";
                case "GTE", ">=" -> "大于等于";
                case "RANGE", "BETWEEN" -> "在范围";
                case "IN" -> "属于";
                case "CONTAINS" -> "包含";
                case "NOT_CONTAINS" -> "不包含";
                case "EXISTS" -> "存在";
                case "NOT_EXISTS" -> "不存在";
                case "EMPTY" -> "为空";
                case "NOT_EMPTY" -> "非空";
                case "REGEX", "MATCHES" -> "匹配正则";
                default -> operator;
            } + (expected.isEmpty() ? "" : " " + expected));
        }
        return safe(String.join("\n", rules));
    }

    private static String source(Map<String, Object> data)
    {
        return "MANUAL".equals(data.get("sourceType")) ? "手动执行"
                : "FREQUENT".equals(data.get("runMode")) ? "高频巡检" : "例行巡检";
    }

    private static String status(Map<String, Object> data)
    {
        return switch (value(data, "resultStatus", ""))
        {
            case "1" -> "正常";
            case "2" -> "异常";
            case "4" -> "关注";
            default -> "未执行";
        };
    }

    private static String enabled(Map<String, Object> row)
    {
        return "1".equals(value(row, "status", "")) || "N".equals(row.get("enabledFlag")) ? "停用" : "启用";
    }

    private static String toolName(Map<String, Object> row)
    {
        return value(row, "toolName", value(row, "toolCode", NOT_RECORDED));
    }

    private static String measured(Map<String, Object> row, String key)
    {
        Object number = row.get(key);
        return number == null ? NOT_RECORDED
                : (number instanceof BigDecimal d ? d.stripTrailingZeros().toPlainString() : number.toString()) + value(row, "actualUnit", "");
    }

    private static void detailRow(XWPFTable table, List<String> row, int[] widths)
    {
        var cells = table.createRow();
        writeCells(cells, row.stream().map(AutoInspectionWordDetails::safe).toList(), widths, false);
        cells.setCantSplitRow(false);
    }

    private static String safeUrl(String value)
    {
        // Query and fragment can contain private parameters under arbitrary names.
        return value.split("[?#]", 2)[0].replaceFirst("(?i)([a-z]+://)[^/]*@", "$1");
    }

    static String safe(String value)
    {
        value = XML_CONTROLS.matcher(value).replaceAll("");
        value = URL.matcher(value).replaceAll(m -> java.util.regex.Matcher.quoteReplacement(safeUrl(m.group())));
        return SECRET_FIELDS.matcher(value).replaceAll("$1[已隐藏]");
    }

    private static String join(String... parts)
    {
        return String.join("\n", java.util.Arrays.stream(parts).filter(part -> !part.isBlank()).toList());
    }

    private static String value(Map<String, Object> map, String key, String fallback)
    {
        Object value = map.get(key);
        return value == null || value.toString().isBlank() ? fallback : value.toString();
    }

    private static Long id(Object value)
    {
        return value instanceof Number number ? number.longValue() : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value)
    {
        if (value instanceof Map<?, ?>) return (Map<String, Object>) value;
        if (value instanceof String json && !json.isBlank())
        {
            try
            {
                Map<String, Object> parsed = JSON.parseObject(json);
                return parsed == null ? Map.of() : parsed;
            }
            catch (com.alibaba.fastjson2.JSONException ignored) { }
        }
        return Map.of();
    }

    private static List<Map<String, Object>> maps(Object value)
    {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(Map.class::isInstance).map(AutoInspectionWordDetails::object).toList();
    }
}
