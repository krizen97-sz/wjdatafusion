package com.hm.manage.service.impl;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.hm.common.exception.ServiceException;
import com.hm.common.utils.DateUtils;
import com.hm.common.utils.SecurityUtils;
import com.hm.common.utils.StringUtils;
import com.hm.common.utils.poi.ExcelUtil;
import com.hm.manage.domain.SupportServer;
import com.hm.manage.domain.vo.SupportAutoInspectionExportVo;
import com.hm.manage.mapper.SupportAutoInspectionMapper;
import com.hm.manage.mapper.SupportServerMapper;
import com.hm.manage.service.ISupportAutoInspectionService;
import com.hm.manage.service.support.CredentialCryptoService;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

@Service
public class SupportAutoInspectionServiceImpl implements ISupportAutoInspectionService
{
    private static final String SOURCE_MANUAL = "MANUAL";
    private static final String SOURCE_AUTO = "AUTO";
    private static final String RESULT_NORMAL = "1";
    private static final String RESULT_ABNORMAL = "2";
    private static final String RESULT_SKIP = "3";
    private static final String ENABLED = "Y";
    private static final String STATUS_NORMAL = "0";
    private static final String STATUS_DISABLED = "1";
    private static final String RULE_MIN = "MIN";
    private static final String RULE_MAX = "MAX";
    private static final String REPORT_STANDARD = "STANDARD";
    private static final int DEFAULT_TIMEOUT_SECONDS = 10;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String TOOL_KAFKA_LAG = "KAFKA_LAG";
    private static final String TOOL_HTTP_COUNT = "HTTP_COUNT";
    private static final String TOOL_FTP_FILE_COUNT = "FTP_FILE_COUNT";
    private static final String TOOL_SERVER_FILE_COUNT = "SERVER_FILE_COUNT";
    private static final String TOOL_SERVER_DISK = "SERVER_DISK";

    @Autowired
    private SupportAutoInspectionMapper autoInspectionMapper;

    @Autowired
    private SupportServerMapper serverMapper;

    @Autowired
    private CredentialCryptoService cryptoService;

    @Override
    public List<Map<String, Object>> selectToolList(Map<String, Object> params)
    {
        ensureBuiltinTools();
        return autoInspectionMapper.selectToolList(params == null ? new HashMap<>() : params);
    }

    @Override
    public List<Map<String, Object>> selectTargetList(Map<String, Object> target)
    {
        List<Map<String, Object>> list = autoInspectionMapper.selectTargetList(target == null ? new HashMap<>() : target);
        for (Map<String, Object> item : list)
        {
            maskTargetSecret(item);
        }
        return list;
    }

    @Override
    public Map<String, Object> selectTargetById(Long targetId)
    {
        Map<String, Object> target = requireTarget(targetId);
        maskTargetSecret(target);
        return target;
    }

    @Override
    public int insertTarget(Map<String, Object> target)
    {
        normalizeTarget(target, false);
        encryptTargetSecret(target);
        target.put("createBy", getCurrentUsername());
        target.put("createTime", DateUtils.getNowDate());
        return autoInspectionMapper.insertTarget(target);
    }

    @Override
    public int updateTarget(Map<String, Object> target)
    {
        normalizeTarget(target, true);
        Map<String, Object> original = requireTarget(toLong(target.get("targetId")));
        encryptTargetSecret(target);
        if (StringUtils.isBlank(str(target, "passwordCipher")))
        {
            target.put("passwordCipher", original.get("passwordCipher"));
        }
        if (StringUtils.isBlank(str(target, "secretCipher")))
        {
            target.put("secretCipher", original.get("secretCipher"));
        }
        target.put("updateBy", getCurrentUsername());
        target.put("updateTime", DateUtils.getNowDate());
        return autoInspectionMapper.updateTarget(target);
    }

    @Override
    public int deleteTargetById(Long targetId)
    {
        return autoInspectionMapper.deleteTargetById(targetId);
    }

    @Override
    public String testTarget(Map<String, Object> target)
    {
        Map<String, Object> effective = buildEffectiveTargetForTest(target);
        normalizeTarget(effective, toLong(effective.get("targetId")) != null);
        withPlainSecret(effective);
        TargetCheckResult result;
        String targetType = str(effective, "targetType");
        switch (targetType)
        {
            case "KAFKA":
                result = testKafkaTarget(effective);
                break;
            case "HTTP":
                result = testHttpTarget(effective);
                break;
            case "FTP":
                result = testFtpTarget(effective);
                break;
            case "SERVER":
                result = testServerTarget(effective);
                break;
            default:
                throw new ServiceException("不支持的目标类型：" + targetType);
        }
        if (RESULT_ABNORMAL.equals(result.status))
        {
            throw new ServiceException(StringUtils.defaultIfBlank(result.errorMessage, result.detail));
        }
        return buildTestSuccessMessage(result);
    }

    @Override
    public String getTargetPasswordPlain(Long targetId)
    {
        Map<String, Object> target = requireTarget(targetId);
        return decryptQuietly(str(target, "passwordCipher"));
    }

    @Override
    public String getTargetSecretPlain(Long targetId)
    {
        Map<String, Object> target = requireTarget(targetId);
        return decryptQuietly(str(target, "secretCipher"));
    }

    @Override
    public List<Map<String, Object>> selectTemplateList(Map<String, Object> template)
    {
        ensureBuiltinTools();
        return autoInspectionMapper.selectTemplateList(template == null ? new HashMap<>() : template);
    }

    @Override
    public Map<String, Object> selectTemplateById(Long templateId)
    {
        Map<String, Object> template = requireTemplate(templateId);
        fillTemplateSteps(template);
        return template;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveTemplate(Map<String, Object> template)
    {
        ensureBuiltinTools();
        normalizeTemplate(template);
        Long templateId = toLong(template.get("templateId"));
        Date now = DateUtils.getNowDate();
        if (templateId == null)
        {
            template.put("createBy", getCurrentUsername());
            template.put("createTime", now);
            template.put("updateBy", getCurrentUsername());
            template.put("updateTime", now);
            autoInspectionMapper.insertTemplate(template);
            templateId = toLong(template.get("templateId"));
        }
        else
        {
            requireTemplate(templateId);
            template.put("updateBy", getCurrentUsername());
            template.put("updateTime", now);
            autoInspectionMapper.updateTemplate(template);
            autoInspectionMapper.deleteStepTargetsByTemplateId(templateId);
            autoInspectionMapper.deleteStepsByTemplateId(templateId);
        }
        saveTemplateSteps(templateId, castList(template.get("steps")));
        return templateId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteTemplateById(Long templateId)
    {
        if (autoInspectionMapper.countPlanByTemplateId(templateId) > 0)
        {
            throw new ServiceException("该模板已被巡检计划引用，不能删除");
        }
        autoInspectionMapper.deleteStepTargetsByTemplateId(templateId);
        autoInspectionMapper.deleteStepsByTemplateId(templateId);
        return autoInspectionMapper.deleteTemplateById(templateId);
    }

    @Override
    public List<Map<String, Object>> selectPlanList(Map<String, Object> plan)
    {
        return autoInspectionMapper.selectPlanList(plan == null ? new HashMap<>() : plan);
    }

    @Override
    public Map<String, Object> selectPlanById(Long planId)
    {
        Map<String, Object> plan = autoInspectionMapper.selectPlanById(planId);
        if (plan == null)
        {
            throw new ServiceException("巡检计划不存在");
        }
        return plan;
    }

    @Override
    public Long savePlan(Map<String, Object> plan)
    {
        normalizePlan(plan);
        Date now = DateUtils.getNowDate();
        if (toLong(plan.get("planId")) == null)
        {
            plan.put("createBy", getCurrentUsername());
            plan.put("createTime", now);
            plan.put("updateBy", getCurrentUsername());
            plan.put("updateTime", now);
            autoInspectionMapper.insertPlan(plan);
        }
        else
        {
            requirePlan(toLong(plan.get("planId")));
            plan.put("updateBy", getCurrentUsername());
            plan.put("updateTime", now);
            autoInspectionMapper.updatePlan(plan);
        }
        return toLong(plan.get("planId"));
    }

    @Override
    public int updatePlanJobId(Long planId, Long jobId)
    {
        return autoInspectionMapper.updatePlanJobId(planId, jobId);
    }

    @Override
    public int updatePlanStatus(Long planId, String status)
    {
        return autoInspectionMapper.updatePlanStatus(planId, STATUS_NORMAL.equals(status) ? STATUS_NORMAL : STATUS_DISABLED, getCurrentUsername());
    }

    @Override
    public int deletePlanById(Long planId)
    {
        return autoInspectionMapper.deletePlanById(planId);
    }

    @Override
    public List<Map<String, Object>> selectRecordList(Map<String, Object> record)
    {
        return autoInspectionMapper.selectRecordList(record == null ? new HashMap<>() : record);
    }

    @Override
    public Map<String, Object> selectRecordDetail(Long recordId)
    {
        Map<String, Object> record = autoInspectionMapper.selectRecordById(recordId);
        if (record == null)
        {
            throw new ServiceException("巡检记录不存在");
        }
        record.put("steps", autoInspectionMapper.selectStepResultsByRecordId(recordId));
        record.put("targetResults", autoInspectionMapper.selectTargetResultsByRecordId(recordId));
        return record;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> runManualTemplate(Long templateId)
    {
        return runInspection(templateId, null, SOURCE_MANUAL, getCurrentOperatorName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> runManualPlan(Long planId)
    {
        Map<String, Object> plan = requirePlan(planId);
        return runInspection(toLong(plan.get("templateId")), plan, SOURCE_MANUAL, getCurrentOperatorName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> runScheduledPlan(Long planId, String executorName)
    {
        Map<String, Object> plan = requirePlan(planId);
        return runInspection(toLong(plan.get("templateId")), plan, SOURCE_AUTO, StringUtils.defaultIfBlank(executorName, "计划巡检"));
    }

    @Override
    public void exportRecord(HttpServletResponse response, Map<String, Object> record)
    {
        List<Map<String, Object>> records = selectRecordList(record);
        List<SupportAutoInspectionExportVo> exportList = new ArrayList<>();
        for (Map<String, Object> item : records)
        {
            exportList.add(toExportVo(selectRecordDetail(toLong(item.get("recordId")))));
        }
        ExcelUtil<SupportAutoInspectionExportVo> util = new ExcelUtil<>(SupportAutoInspectionExportVo.class);
        util.exportExcel(response, exportList, "自动化巡检记录");
    }

    private Map<String, Object> runInspection(Long templateId, Map<String, Object> plan, String sourceType, String executorName)
    {
        Map<String, Object> template = selectTemplateById(templateId);
        if (!STATUS_NORMAL.equals(str(template, "status")))
        {
            throw new ServiceException("巡检模板已停用");
        }
        List<Map<String, Object>> steps = castList(template.get("steps"));
        if (steps.isEmpty())
        {
            throw new ServiceException("巡检模板未配置步骤");
        }

        Date now = DateUtils.getNowDate();
        Map<String, Object> record = new HashMap<>();
        record.put("inspectionTime", now);
        record.put("sourceType", sourceType);
        record.put("resultStatus", RESULT_SKIP);
        record.put("executorName", executorName);
        record.put("templateId", templateId);
        record.put("templateName", template.get("templateName"));
        record.put("planId", plan == null ? null : plan.get("planId"));
        record.put("planName", plan == null ? null : plan.get("planName"));
        record.put("reportStyle", plan == null ? REPORT_STANDARD : StringUtils.defaultIfBlank(str(plan, "reportStyle"), REPORT_STANDARD));
        record.put("enabledStepCount", 0);
        record.put("skippedStepCount", 0);
        record.put("targetCount", 0);
        record.put("abnormalCount", 0);
        record.put("summary", "巡检执行中");
        record.put("abnormalSummary", "");
        record.put("createBy", getCurrentUsername());
        record.put("createTime", now);
        record.put("updateBy", getCurrentUsername());
        record.put("updateTime", now);
        autoInspectionMapper.insertRecord(record);

        int enabledCount = 0;
        int skippedCount = 0;
        int targetCount = 0;
        int abnormalCount = 0;
        List<String> abnormalSummaries = new ArrayList<>();
        for (Map<String, Object> step : steps)
        {
            Map<String, Object> tool = requireTool(str(step, "toolCode"));
            Map<String, Object> stepResult = copyStepToResult(record, step, tool, now);
            if (!ENABLED.equals(str(step, "enabledFlag")))
            {
                skippedCount++;
                stepResult.put("resultStatus", RESULT_SKIP);
                stepResult.put("resultSummary", str(step, "stepName") + "已关闭，本次跳过");
                autoInspectionMapper.insertStepResult(stepResult);
                continue;
            }

            enabledCount++;
            List<Map<String, Object>> targets = autoInspectionMapper.selectEnabledTargetsByStepId(toLong(step.get("stepId")));
            if (targets.isEmpty())
            {
                abnormalCount++;
                stepResult.put("resultStatus", RESULT_ABNORMAL);
                stepResult.put("resultSummary", str(step, "stepName") + "已启用但未配置目标");
                abnormalSummaries.add(str(stepResult, "resultSummary"));
                autoInspectionMapper.insertStepResult(stepResult);
                continue;
            }

            targetCount += targets.size();
            List<TargetCheckResult> results = new ArrayList<>();
            boolean hasAbnormal = false;
            for (Map<String, Object> target : targets)
            {
                TargetCheckResult result = runSingleTarget(step, tool, withPlainSecret(target), true);
                results.add(result);
                if (RESULT_ABNORMAL.equals(result.status))
                {
                    hasAbnormal = true;
                }
            }

            stepResult.put("resultStatus", hasAbnormal ? RESULT_ABNORMAL : RESULT_NORMAL);
            stepResult.put("actualValue", resolveStepActualValue(step, results));
            stepResult.put("actualUnit", StringUtils.defaultIfBlank(str(step, "thresholdUnit"), str(tool, "valueUnit")));
            stepResult.put("resultSummary", buildStepSummary(step, results, hasAbnormal));
            if (hasAbnormal)
            {
                abnormalCount++;
                abnormalSummaries.add(str(step, "stepName") + "：" + str(stepResult, "resultSummary"));
            }
            autoInspectionMapper.insertStepResult(stepResult);
            for (TargetCheckResult result : results)
            {
                autoInspectionMapper.insertTargetResult(result.toTargetResult(toLong(record.get("recordId")),
                        toLong(stepResult.get("stepResultId")), now, getCurrentUsername()));
            }
        }

        record.put("enabledStepCount", enabledCount);
        record.put("skippedStepCount", skippedCount);
        record.put("targetCount", targetCount);
        record.put("abnormalCount", abnormalCount);
        record.put("resultStatus", resolveInspectionStatus(enabledCount, abnormalCount));
        record.put("summary", "启用" + enabledCount + "步，跳过" + skippedCount + "步，检测目标" + targetCount + "个，异常" + abnormalCount + "步");
        record.put("abnormalSummary", abnormalSummaries.isEmpty() ? "无异常" : StringUtils.join(abnormalSummaries, "；"));
        record.put("updateBy", getCurrentUsername());
        record.put("updateTime", DateUtils.getNowDate());
        autoInspectionMapper.updateRecord(record);
        return selectRecordDetail(toLong(record.get("recordId")));
    }

    private TargetCheckResult runSingleTarget(Map<String, Object> step, Map<String, Object> tool, Map<String, Object> target, boolean thresholdEnabled)
    {
        try
        {
            TargetCheckResult result;
            switch (str(tool, "toolType"))
            {
                case TOOL_KAFKA_LAG:
                    result = checkKafkaLag(step, target);
                    break;
                case TOOL_FTP_FILE_COUNT:
                    result = checkFtpFileCount(step, target);
                    break;
                case TOOL_SERVER_FILE_COUNT:
                    result = checkServerFileCount(step, target);
                    break;
                case TOOL_SERVER_DISK:
                    result = checkServerDisk(step, target);
                    break;
                case TOOL_HTTP_COUNT:
                default:
                    result = checkHttpCount(step, target);
                    break;
            }
            if (thresholdEnabled)
            {
                applyThreshold(step, tool, result);
            }
            return result;
        }
        catch (Exception e)
        {
            TargetCheckResult result = TargetCheckResult.abnormal(target, null,
                    StringUtils.defaultIfBlank(str(step, "thresholdUnit"), str(tool, "valueUnit")),
                    "检测失败：" + e.getMessage());
            result.errorMessage = e.getMessage();
            return result;
        }
    }

    private TargetCheckResult checkHttpCount(Map<String, Object> step, Map<String, Object> target) throws Exception
    {
        requireText(str(target, "url"), "HTTP目标URL不能为空");
        int timeout = resolveTimeout(step);
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime begin = end.minusMinutes(toInt(step.get("timeWindowMinutes"), 0));
        String url = replaceTimePlaceholders(str(target, "url"), begin, end);
        String body = replaceTimePlaceholders(StringUtils.defaultString(str(target, "extraParams")), begin, end);
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(timeout)).build();
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(timeout))
                .header("Content-Type", "application/json");
        if (StringUtils.isNotBlank(str(target, "appKey")))
        {
            builder.header("X-App-Key", str(target, "appKey"));
        }
        if (StringUtils.isNotBlank(str(target, "secret")))
        {
            builder.header("X-App-Secret", str(target, "secret"));
        }
        if ("POST".equalsIgnoreCase(str(target, "httpMethod")))
        {
            builder.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        }
        else
        {
            builder.GET();
        }
        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300)
        {
            throw new ServiceException("HTTP状态码异常：" + response.statusCode());
        }
        BigDecimal value = extractNumber(response.body(), str(target, "resultPath"));
        return TargetCheckResult.normal(target, value, str(step, "thresholdUnit"), "接口返回计数 " + formatDecimal(value));
    }

    private TargetCheckResult checkFtpFileCount(Map<String, Object> step, Map<String, Object> target) throws Exception
    {
        FTPClient client = new FTPClient();
        int timeout = resolveTimeout(step) * 1000;
        client.setConnectTimeout(timeout);
        client.setDefaultTimeout(timeout);
        client.setDataTimeout(Duration.ofMillis(timeout));
        try
        {
            client.connect(str(target, "host"), toInt(target.get("port"), 21));
            if (!client.login(str(target, "username"), str(target, "password")))
            {
                throw new ServiceException("FTP登录失败");
            }
            client.enterLocalPassiveMode();
            String path = resolvePath(step, target);
            if (StringUtils.isNotBlank(path) && !client.changeWorkingDirectory(path))
            {
                throw new ServiceException("FTP目录不存在或无权限：" + path);
            }
            int count = 0;
            FTPFile[] files = client.listFiles();
            if (files != null)
            {
                for (FTPFile file : files)
                {
                    if (file.isFile())
                    {
                        count++;
                    }
                }
            }
            return TargetCheckResult.normal(target, new BigDecimal(count), "个", "文件数量 " + count);
        }
        finally
        {
            if (client.isConnected())
            {
                client.logout();
                client.disconnect();
            }
        }
    }

    private TargetCheckResult checkServerFileCount(Map<String, Object> step, Map<String, Object> target) throws Exception
    {
        SupportServer server = requireServer(target);
        String path = resolvePath(step, target);
        requireText(path, "服务器目录不能为空");
        Map<String, Object> params = readParams(step);
        boolean recursive = !"false".equalsIgnoreCase(StringUtils.defaultIfBlank(str(params, "recursive"), "true"));
        String filePattern = str(params, "filePattern");
        String command = "find " + shellQuote(path) + (recursive ? "" : " -maxdepth 1") + " -type f"
                + (StringUtils.isBlank(filePattern) ? "" : " -name " + shellQuote(filePattern)) + " | wc -l";
        String output = executeServerCommand(server, command, resolveTimeout(step)).trim();
        BigDecimal value = new BigDecimal(output.replaceAll("[^0-9]", ""));
        return TargetCheckResult.normal(target, value, "个", "目录 " + path + " 文件数量 " + value.toPlainString());
    }

    private TargetCheckResult checkServerDisk(Map<String, Object> step, Map<String, Object> target) throws Exception
    {
        SupportServer server = requireServer(target);
        String output = executeServerCommand(server, "df -P", resolveTimeout(step));
        String targetPath = resolvePath(step, target);
        List<DiskLine> lines = readDiskLines(output);
        BigDecimal maxUsage = BigDecimal.ZERO;
        StringBuilder detail = new StringBuilder();
        for (DiskLine line : lines)
        {
            if (StringUtils.isBlank(targetPath) && "/".equals(line.mountPoint))
            {
                continue;
            }
            if (StringUtils.isNotBlank(targetPath) && !targetPath.equals(line.mountPoint))
            {
                continue;
            }
            if (line.usePercent.compareTo(maxUsage) > 0)
            {
                maxUsage = line.usePercent;
            }
            if (detail.length() > 0)
            {
                detail.append("；");
            }
            detail.append(line.mountPoint).append(" 使用率").append(formatDecimal(line.usePercent)).append("%");
        }
        if (detail.length() == 0)
        {
            throw new ServiceException("未匹配到可检测磁盘挂载点");
        }
        return TargetCheckResult.normal(target, maxUsage, "%", detail.toString());
    }

    private TargetCheckResult checkKafkaLag(Map<String, Object> step, Map<String, Object> target)
    {
        String topic = StringUtils.defaultIfBlank(str(readParams(step), "topic"), str(target, "topic"));
        String group = StringUtils.defaultIfBlank(str(readParams(step), "consumerGroup"), str(target, "consumerGroup"));
        requireText(str(target, "host"), "Kafka bootstrap不能为空");
        requireText(topic, "Kafka topic不能为空");
        requireText(group, "Kafka消费组不能为空");
        Properties props = new Properties();
        props.put("bootstrap.servers", str(target, "host"));
        props.put("group.id", group);
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("enable.auto.commit", "false");
        props.put("request.timeout.ms", String.valueOf(resolveTimeout(step) * 1000));
        props.put("default.api.timeout.ms", String.valueOf(resolveTimeout(step) * 1000));
        long maxLag = 0L;
        long sumLag = 0L;
        int partitionCount = 0;
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props))
        {
            List<PartitionInfo> partitions = consumer.partitionsFor(topic);
            if (partitions == null || partitions.isEmpty())
            {
                throw new ServiceException("Kafka主题不存在或无分区：" + topic);
            }
            List<TopicPartition> topicPartitions = new ArrayList<>();
            for (PartitionInfo partition : partitions)
            {
                topicPartitions.add(new TopicPartition(topic, partition.partition()));
            }
            Map<TopicPartition, OffsetAndMetadata> committedOffsets = consumer.committed(new HashSet<>(topicPartitions));
            Map<TopicPartition, Long> endOffsets = consumer.endOffsets(topicPartitions);
            for (TopicPartition partition : topicPartitions)
            {
                long endOffset = endOffsets.getOrDefault(partition, 0L);
                OffsetAndMetadata committed = committedOffsets.get(partition);
                long committedOffset = committed == null ? 0L : committed.offset();
                long lag = Math.max(endOffset - committedOffset, 0L);
                maxLag = Math.max(maxLag, lag);
                sumLag += lag;
                partitionCount++;
            }
        }
        BigDecimal average = partitionCount == 0 ? BigDecimal.ZERO : new BigDecimal(sumLag).divide(new BigDecimal(partitionCount), 2, RoundingMode.HALF_UP);
        return TargetCheckResult.normal(target, new BigDecimal(maxLag), "条", "最大积压 " + maxLag + "，平均积压 " + average);
    }

    private TargetCheckResult testKafkaTarget(Map<String, Object> target)
    {
        Properties props = new Properties();
        props.put("bootstrap.servers", str(target, "host"));
        props.put("group.id", StringUtils.defaultIfBlank(str(target, "consumerGroup"), "auto-inspection-test"));
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("enable.auto.commit", "false");
        props.put("request.timeout.ms", String.valueOf(DEFAULT_TIMEOUT_SECONDS * 1000));
        props.put("default.api.timeout.ms", String.valueOf(DEFAULT_TIMEOUT_SECONDS * 1000));
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props))
        {
            if (StringUtils.isNotBlank(str(target, "topic")))
            {
                List<PartitionInfo> partitions = consumer.partitionsFor(str(target, "topic"));
                return TargetCheckResult.normal(target, new BigDecimal(partitions == null ? 0 : partitions.size()), "分区", "Kafka主题可访问");
            }
            int size = consumer.listTopics().size();
            return TargetCheckResult.normal(target, new BigDecimal(size), "主题", "Kafka连接可用");
        }
    }

    private TargetCheckResult testHttpTarget(Map<String, Object> target)
    {
        Map<String, Object> step = defaultStep(TOOL_HTTP_COUNT, "个");
        return runSingleTarget(step, requireTool(TOOL_HTTP_COUNT), target, false);
    }

    private TargetCheckResult testFtpTarget(Map<String, Object> target)
    {
        Map<String, Object> step = defaultStep(TOOL_FTP_FILE_COUNT, "个");
        return runSingleTarget(step, requireTool(TOOL_FTP_FILE_COUNT), target, false);
    }

    private TargetCheckResult testServerTarget(Map<String, Object> target)
    {
        try
        {
            SupportServer server = requireServer(target);
            String output = executeServerCommand(server, "echo ok", DEFAULT_TIMEOUT_SECONDS).trim();
            return TargetCheckResult.normal(target, null, "", "服务器连接可用：" + output);
        }
        catch (Exception e)
        {
            throw new ServiceException(e.getMessage());
        }
    }

    private void applyThreshold(Map<String, Object> step, Map<String, Object> tool, TargetCheckResult result)
    {
        BigDecimal threshold = toBigDecimal(step.get("thresholdValue"));
        if (result.actualValue == null || threshold == null)
        {
            result.status = RESULT_NORMAL;
            return;
        }
        String rule = StringUtils.defaultIfBlank(str(step, "compareRule"), str(tool, "defaultCompareRule"));
        int compared = result.actualValue.compareTo(threshold);
        boolean abnormal = RULE_MIN.equals(rule) ? compared < 0 : compared > 0;
        result.status = abnormal ? RESULT_ABNORMAL : RESULT_NORMAL;
        if (abnormal)
        {
            String relation = RULE_MIN.equals(rule) ? "低于" : "高于";
            result.errorMessage = "实际值" + formatDecimal(result.actualValue) + result.actualUnit
                    + relation + "阈值" + formatDecimal(threshold) + StringUtils.defaultString(str(step, "thresholdUnit"));
        }
    }

    private BigDecimal resolveStepActualValue(Map<String, Object> step, List<TargetCheckResult> results)
    {
        List<BigDecimal> values = new ArrayList<>();
        for (TargetCheckResult result : results)
        {
            if (result.actualValue != null)
            {
                values.add(result.actualValue);
            }
        }
        if (values.isEmpty())
        {
            return null;
        }
        return RULE_MIN.equals(str(step, "compareRule")) ? Collections.min(values) : Collections.max(values);
    }

    private String buildStepSummary(Map<String, Object> step, List<TargetCheckResult> results, boolean hasAbnormal)
    {
        int abnormal = 0;
        List<String> details = new ArrayList<>();
        for (TargetCheckResult result : results)
        {
            if (RESULT_ABNORMAL.equals(result.status))
            {
                abnormal++;
                details.add(result.targetName + " " + StringUtils.defaultIfBlank(result.errorMessage, result.detail));
            }
        }
        if (hasAbnormal)
        {
            return "共" + results.size() + "个目标，异常" + abnormal + "个：" + StringUtils.join(details, "；");
        }
        BigDecimal actual = resolveStepActualValue(step, results);
        return "共" + results.size() + "个目标，检测正常，代表值" + formatDecimal(actual) + StringUtils.defaultString(str(step, "thresholdUnit"));
    }

    private String resolveInspectionStatus(int enabledCount, int abnormalCount)
    {
        if (enabledCount == 0)
        {
            return RESULT_SKIP;
        }
        return abnormalCount > 0 ? RESULT_ABNORMAL : RESULT_NORMAL;
    }

    private Map<String, Object> copyStepToResult(Map<String, Object> record, Map<String, Object> step, Map<String, Object> tool, Date now)
    {
        Map<String, Object> result = new HashMap<>();
        result.put("recordId", record.get("recordId"));
        result.put("stepId", step.get("stepId"));
        result.put("toolCode", step.get("toolCode"));
        result.put("toolName", tool.get("toolName"));
        result.put("toolType", tool.get("toolType"));
        result.put("stepName", step.get("stepName"));
        result.put("enabledFlag", step.get("enabledFlag"));
        result.put("sortOrder", step.get("sortOrder"));
        result.put("thresholdValue", step.get("thresholdValue"));
        result.put("thresholdUnit", step.get("thresholdUnit"));
        result.put("compareRule", step.get("compareRule"));
        result.put("timeWindowMinutes", step.get("timeWindowMinutes"));
        result.put("timeoutSeconds", step.get("timeoutSeconds"));
        result.put("stepParams", step.get("stepParams"));
        result.put("resultStatus", RESULT_SKIP);
        result.put("actualValue", null);
        result.put("actualUnit", StringUtils.defaultIfBlank(str(step, "thresholdUnit"), str(tool, "valueUnit")));
        result.put("resultSummary", "");
        result.put("createBy", getCurrentUsername());
        result.put("createTime", now);
        result.put("updateBy", getCurrentUsername());
        result.put("updateTime", now);
        return result;
    }

    private void normalizeTarget(Map<String, Object> target, boolean update)
    {
        if (target == null)
        {
            throw new ServiceException("巡检目标不能为空");
        }
        if (update && toLong(target.get("targetId")) == null)
        {
            throw new ServiceException("巡检目标ID不能为空");
        }
        target.put("targetName", StringUtils.trimToEmpty(str(target, "targetName")));
        requireText(str(target, "targetName"), "巡检目标名称不能为空");
        String targetType = StringUtils.defaultIfBlank(str(target, "targetType"), "").toUpperCase();
        target.put("targetType", targetType);
        target.put("status", STATUS_DISABLED.equals(str(target, "status")) ? STATUS_DISABLED : STATUS_NORMAL);
        switch (targetType)
        {
            case "KAFKA":
                requireText(str(target, "host"), "Kafka bootstrap不能为空");
                requireText(str(target, "topic"), "Kafka topic不能为空");
                requireText(str(target, "consumerGroup"), "Kafka消费组不能为空");
                break;
            case "HTTP":
                requireText(str(target, "url"), "HTTP接口地址不能为空");
                target.put("httpMethod", StringUtils.defaultIfBlank(str(target, "httpMethod"), "POST").toUpperCase());
                target.put("resultPath", StringUtils.defaultIfBlank(str(target, "resultPath"), "data.total"));
                break;
            case "FTP":
                requireText(str(target, "host"), "FTP主机不能为空");
                requireText(str(target, "username"), "FTP账号不能为空");
                requireText(str(target, "path"), "FTP目录不能为空");
                target.put("port", toInt(target.get("port"), 21));
                break;
            case "SERVER":
                if (toLong(target.get("serverId")) == null)
                {
                    throw new ServiceException("请选择服务器资产");
                }
                requireText(str(target, "path"), "检测路径不能为空");
                break;
            default:
                throw new ServiceException("不支持的目标类型：" + targetType);
        }
    }

    private void normalizeTemplate(Map<String, Object> template)
    {
        if (template == null)
        {
            throw new ServiceException("巡检模板不能为空");
        }
        template.put("templateName", StringUtils.trimToEmpty(str(template, "templateName")));
        requireText(str(template, "templateName"), "巡检模板名称不能为空");
        template.put("status", STATUS_DISABLED.equals(str(template, "status")) ? STATUS_DISABLED : STATUS_NORMAL);
        if (castList(template.get("steps")).isEmpty())
        {
            throw new ServiceException("巡检模板至少需要配置一个步骤");
        }
    }

    private void saveTemplateSteps(Long templateId, List<Map<String, Object>> steps)
    {
        int order = 1;
        for (Map<String, Object> step : steps)
        {
            Map<String, Object> tool = requireTool(str(step, "toolCode"));
            step.put("templateId", templateId);
            step.put("stepName", StringUtils.defaultIfBlank(str(step, "stepName"), str(tool, "toolName")));
            step.put("enabledFlag", ENABLED.equals(str(step, "enabledFlag")) ? ENABLED : "N");
            step.put("sortOrder", toInt(step.get("sortOrder"), order++));
            step.put("thresholdValue", step.get("thresholdValue") == null ? tool.get("defaultThresholdValue") : step.get("thresholdValue"));
            step.put("thresholdUnit", StringUtils.defaultIfBlank(str(step, "thresholdUnit"), str(tool, "valueUnit")));
            step.put("compareRule", StringUtils.defaultIfBlank(str(step, "compareRule"), str(tool, "defaultCompareRule")));
            step.put("timeWindowMinutes", toInt(step.get("timeWindowMinutes"), toInt(tool.get("defaultTimeWindowMinutes"), 0)));
            step.put("timeoutSeconds", toInt(step.get("timeoutSeconds"), toInt(tool.get("defaultTimeoutSeconds"), DEFAULT_TIMEOUT_SECONDS)));
            Object stepParams = step.get("stepParams");
            if (stepParams != null && !(stepParams instanceof String))
            {
                step.put("stepParams", JSON.toJSONString(stepParams));
            }
            step.put("createBy", getCurrentUsername());
            step.put("createTime", DateUtils.getNowDate());
            step.put("updateBy", getCurrentUsername());
            step.put("updateTime", DateUtils.getNowDate());
            List<Long> targetIds = resolveStepTargetIds(step, tool);
            autoInspectionMapper.insertStep(step);
            Long stepId = toLong(step.get("stepId"));
            for (Long targetId : targetIds)
            {
                autoInspectionMapper.insertStepTarget(stepId, targetId, getCurrentUsername());
            }
        }
    }

    private List<Long> resolveStepTargetIds(Map<String, Object> step, Map<String, Object> tool)
    {
        Map<String, Object> inlineTarget = castMap(step.get("target"));
        if (!inlineTarget.isEmpty())
        {
            return Collections.singletonList(saveInlineStepTarget(step, tool, inlineTarget));
        }
        return toLongList(step.get("targetIds"));
    }

    private Long saveInlineStepTarget(Map<String, Object> step, Map<String, Object> tool, Map<String, Object> target)
    {
        target.put("targetType", StringUtils.defaultIfBlank(str(target, "targetType"), resolveTargetTypeByTool(str(step, "toolCode"))));
        target.put("targetName", StringUtils.defaultIfBlank(str(target, "targetName"), str(step, "stepName") + "目标"));
        target.put("status", STATUS_DISABLED.equals(str(target, "status")) ? STATUS_DISABLED : STATUS_NORMAL);
        mergeStepParamsToTarget(step, target);
        Long targetId = toLong(target.get("targetId"));
        if (targetId == null)
        {
            normalizeTarget(target, false);
            encryptTargetSecret(target);
            target.put("createBy", getCurrentUsername());
            target.put("createTime", DateUtils.getNowDate());
            target.put("updateBy", getCurrentUsername());
            target.put("updateTime", DateUtils.getNowDate());
            autoInspectionMapper.insertTarget(target);
            return toLong(target.get("targetId"));
        }
        updateTarget(target);
        return targetId;
    }

    private void mergeStepParamsToTarget(Map<String, Object> step, Map<String, Object> target)
    {
        Map<String, Object> params = readParams(step);
        if (TOOL_KAFKA_LAG.equals(str(step, "toolCode")))
        {
            if (StringUtils.isBlank(str(target, "topic")) && StringUtils.isNotBlank(str(params, "topic")))
            {
                target.put("topic", params.get("topic"));
            }
            if (StringUtils.isBlank(str(target, "consumerGroup")) && StringUtils.isNotBlank(str(params, "consumerGroup")))
            {
                target.put("consumerGroup", params.get("consumerGroup"));
            }
        }
        if ((TOOL_FTP_FILE_COUNT.equals(str(step, "toolCode")) || TOOL_SERVER_FILE_COUNT.equals(str(step, "toolCode")) || TOOL_SERVER_DISK.equals(str(step, "toolCode")))
                && StringUtils.isBlank(str(target, "path")) && StringUtils.isNotBlank(str(params, "path")))
        {
            target.put("path", params.get("path"));
        }
    }

    private String resolveTargetTypeByTool(String toolCode)
    {
        if (TOOL_KAFKA_LAG.equals(toolCode))
        {
            return "KAFKA";
        }
        if (TOOL_HTTP_COUNT.equals(toolCode))
        {
            return "HTTP";
        }
        if (TOOL_FTP_FILE_COUNT.equals(toolCode))
        {
            return "FTP";
        }
        return "SERVER";
    }

    private void normalizePlan(Map<String, Object> plan)
    {
        if (plan == null)
        {
            throw new ServiceException("巡检计划不能为空");
        }
        plan.put("planName", StringUtils.trimToEmpty(str(plan, "planName")));
        requireText(str(plan, "planName"), "巡检计划名称不能为空");
        Long templateId = toLong(plan.get("templateId"));
        if (templateId == null)
        {
            throw new ServiceException("请选择巡检模板");
        }
        requireTemplate(templateId);
        requireText(str(plan, "cronExpression"), "执行周期不能为空");
        plan.put("status", STATUS_DISABLED.equals(str(plan, "status")) ? STATUS_DISABLED : STATUS_NORMAL);
        plan.put("reportStyle", StringUtils.defaultIfBlank(str(plan, "reportStyle"), REPORT_STANDARD));
        Object cronConfig = plan.get("cronConfig");
        if (cronConfig != null && !(cronConfig instanceof String))
        {
            plan.put("cronConfig", JSON.toJSONString(cronConfig));
        }
    }

    private void fillTemplateSteps(Map<String, Object> template)
    {
        Long templateId = toLong(template.get("templateId"));
        List<Map<String, Object>> steps = autoInspectionMapper.selectStepsByTemplateId(templateId);
        Map<Long, List<Long>> targetMap = new HashMap<>();
        for (Map<String, Object> relation : autoInspectionMapper.selectStepTargetsByTemplateId(templateId))
        {
            Long stepId = toLong(relation.get("stepId"));
            targetMap.computeIfAbsent(stepId, key -> new ArrayList<>()).add(toLong(relation.get("targetId")));
        }
        for (Map<String, Object> step : steps)
        {
            List<Long> targetIds = targetMap.getOrDefault(toLong(step.get("stepId")), new ArrayList<>());
            step.put("targetIds", targetIds);
            if (!targetIds.isEmpty())
            {
                Map<String, Object> target = autoInspectionMapper.selectTargetById(targetIds.get(0));
                if (target != null)
                {
                    maskTargetSecret(target);
                    step.put("target", target);
                }
            }
        }
        template.put("steps", steps);
    }

    private void ensureBuiltinTools()
    {
        insertBuiltinTool(TOOL_KAFKA_LAG, "Kafka消费积压检测", TOOL_KAFKA_LAG, "条", RULE_MAX, new BigDecimal("2000"), 10, 0,
                "{\"fields\":[\"topic\",\"consumerGroup\"]}");
        insertBuiltinTool(TOOL_HTTP_COUNT, "海康接口数量检测", TOOL_HTTP_COUNT, "条", RULE_MIN, new BigDecimal("0"), 10, 480,
                "{\"fields\":[\"resultPath\",\"extraParams\",\"timeWindowMinutes\"]}");
        insertBuiltinTool(TOOL_FTP_FILE_COUNT, "FTP目录文件数量检测", TOOL_FTP_FILE_COUNT, "个", RULE_MAX, new BigDecimal("50"), 10, 0,
                "{\"fields\":[\"path\"]}");
        insertBuiltinTool(TOOL_SERVER_FILE_COUNT, "服务器目录文件数量检测", TOOL_SERVER_FILE_COUNT, "个", RULE_MAX, new BigDecimal("20"), 10, 0,
                "{\"fields\":[\"path\",\"recursive\",\"filePattern\"]}");
        insertBuiltinTool(TOOL_SERVER_DISK, "服务器磁盘使用率检测", TOOL_SERVER_DISK, "%", RULE_MAX, new BigDecimal("80"), 10, 0,
                "{\"fields\":[\"path\"]}");
    }

    private void insertBuiltinTool(String code, String name, String type, String unit, String rule,
                                   BigDecimal threshold, int timeout, int window, String schema)
    {
        if (autoInspectionMapper.selectToolByCode(code) != null)
        {
            return;
        }
        Map<String, Object> tool = new HashMap<>();
        tool.put("toolCode", code);
        tool.put("toolName", name);
        tool.put("toolType", type);
        tool.put("valueUnit", unit);
        tool.put("defaultCompareRule", rule);
        tool.put("defaultThresholdValue", threshold);
        tool.put("defaultTimeoutSeconds", timeout);
        tool.put("defaultTimeWindowMinutes", window);
        tool.put("paramSchema", schema);
        tool.put("builtInFlag", "Y");
        tool.put("status", STATUS_NORMAL);
        tool.put("createBy", "system");
        tool.put("createTime", DateUtils.getNowDate());
        tool.put("remark", "自动化巡检内置工具");
        autoInspectionMapper.insertTool(tool);
    }

    private Map<String, Object> requireTarget(Long targetId)
    {
        if (targetId == null)
        {
            throw new ServiceException("巡检目标ID不能为空");
        }
        Map<String, Object> target = autoInspectionMapper.selectTargetById(targetId);
        if (target == null)
        {
            throw new ServiceException("巡检目标不存在");
        }
        return target;
    }

    private Map<String, Object> requireTool(String toolCode)
    {
        ensureBuiltinTools();
        Map<String, Object> tool = autoInspectionMapper.selectToolByCode(toolCode);
        if (tool == null)
        {
            throw new ServiceException("巡检工具不存在：" + toolCode);
        }
        return tool;
    }

    private Map<String, Object> requireTemplate(Long templateId)
    {
        if (templateId == null)
        {
            throw new ServiceException("巡检模板ID不能为空");
        }
        Map<String, Object> template = autoInspectionMapper.selectTemplateById(templateId);
        if (template == null)
        {
            throw new ServiceException("巡检模板不存在");
        }
        return template;
    }

    private Map<String, Object> requirePlan(Long planId)
    {
        if (planId == null)
        {
            throw new ServiceException("巡检计划ID不能为空");
        }
        Map<String, Object> plan = autoInspectionMapper.selectPlanById(planId);
        if (plan == null)
        {
            throw new ServiceException("巡检计划不存在");
        }
        return plan;
    }

    private SupportServer requireServer(Map<String, Object> target)
    {
        Long serverId = toLong(target.get("serverId"));
        if (serverId == null)
        {
            throw new ServiceException("请选择服务器资产");
        }
        SupportServer server = serverMapper.selectSupportServerByServerId(serverId);
        if (server == null)
        {
            throw new ServiceException("服务器不存在");
        }
        return server;
    }

    private String executeServerCommand(SupportServer server, String command, int timeoutSeconds) throws Exception
    {
        String password = cryptoService.decrypt(server.getOsPasswordCipher());
        Session session = createSshSession(server.getServerAddress(), server.getSshPort() == null ? 22 : server.getSshPort(),
                server.getOsUsername(), password, timeoutSeconds);
        ChannelExec channel = null;
        try
        {
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            InputStream inputStream = channel.getInputStream();
            channel.connect(timeoutSeconds * 1000);
            return readStream(inputStream);
        }
        finally
        {
            if (channel != null)
            {
                channel.disconnect();
            }
            session.disconnect();
        }
    }

    private Session createSshSession(String host, int port, String username, String password, int timeoutSeconds) throws Exception
    {
        if (StringUtils.isBlank(host) || StringUtils.isBlank(username))
        {
            throw new ServiceException("SSH目标需配置主机和账号");
        }
        JSch jsch = new JSch();
        Session session = jsch.getSession(username, host, port);
        session.setPassword(password);
        session.setConfig("StrictHostKeyChecking", "no");
        session.setConfig("PreferredAuthentications", "password");
        session.connect(timeoutSeconds * 1000);
        return session;
    }

    private String readStream(InputStream inputStream) throws Exception
    {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                if (builder.length() > 0)
                {
                    builder.append('\n');
                }
                builder.append(line);
            }
        }
        return builder.toString();
    }

    private List<DiskLine> readDiskLines(String text)
    {
        List<DiskLine> lines = new ArrayList<>();
        if (StringUtils.isBlank(text))
        {
            return lines;
        }
        String[] rows = text.split("\\R");
        for (int i = 1; i < rows.length; i++)
        {
            String[] parts = rows[i].trim().split("\\s+");
            if (parts.length < 6)
            {
                continue;
            }
            String percent = parts[4].replace("%", "");
            lines.add(new DiskLine(parts[5], new BigDecimal(percent)));
        }
        return lines;
    }

    private Map<String, Object> buildEffectiveTargetForTest(Map<String, Object> form)
    {
        Long targetId = toLong(form == null ? null : form.get("targetId"));
        if (targetId == null)
        {
            return form == null ? new HashMap<>() : new HashMap<>(form);
        }
        Map<String, Object> persisted = requireTarget(targetId);
        Map<String, Object> merged = new HashMap<>(persisted);
        if (form != null)
        {
            merged.putAll(form);
        }
        if (StringUtils.isBlank(str(merged, "password")) && StringUtils.isNotBlank(str(persisted, "passwordCipher")))
        {
            merged.put("password", decryptQuietly(str(persisted, "passwordCipher")));
        }
        if (StringUtils.isBlank(str(merged, "secret")) && StringUtils.isNotBlank(str(persisted, "secretCipher")))
        {
            merged.put("secret", decryptQuietly(str(persisted, "secretCipher")));
        }
        return merged;
    }

    private Map<String, Object> withPlainSecret(Map<String, Object> target)
    {
        if (StringUtils.isBlank(str(target, "password")) && StringUtils.isNotBlank(str(target, "passwordCipher")))
        {
            target.put("password", decryptQuietly(str(target, "passwordCipher")));
        }
        if (StringUtils.isBlank(str(target, "secret")) && StringUtils.isNotBlank(str(target, "secretCipher")))
        {
            target.put("secret", decryptQuietly(str(target, "secretCipher")));
        }
        return target;
    }

    private void encryptTargetSecret(Map<String, Object> target)
    {
        if (StringUtils.isNotBlank(str(target, "password")) && !"******".equals(str(target, "password")))
        {
            target.put("passwordCipher", cryptoService.encrypt(str(target, "password")));
        }
        if (StringUtils.isNotBlank(str(target, "secret")) && !"******".equals(str(target, "secret")))
        {
            target.put("secretCipher", cryptoService.encrypt(str(target, "secret")));
        }
    }

    private void maskTargetSecret(Map<String, Object> target)
    {
        if (StringUtils.isNotBlank(str(target, "passwordCipher")))
        {
            target.put("password", "******");
        }
        if (StringUtils.isNotBlank(str(target, "secretCipher")))
        {
            target.put("secret", "******");
        }
    }

    private String decryptQuietly(String cipherText)
    {
        return StringUtils.isBlank(cipherText) ? StringUtils.EMPTY : cryptoService.decrypt(cipherText);
    }

    private BigDecimal extractNumber(String responseBody, String resultPath)
    {
        if (StringUtils.isBlank(responseBody))
        {
            throw new ServiceException("接口响应为空");
        }
        try
        {
            JSONObject json = JSON.parseObject(responseBody);
            Object value = findJsonValue(json, StringUtils.defaultIfBlank(resultPath, "data.total"));
            if (value == null)
            {
                value = findJsonValue(json, "total");
            }
            if (value == null)
            {
                value = findJsonValue(json, "data");
            }
            if (value == null)
            {
                throw new ServiceException("无法从响应中解析计数字段：" + resultPath);
            }
            return new BigDecimal(value.toString());
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            return new BigDecimal(responseBody.trim());
        }
    }

    private Object findJsonValue(JSONObject json, String path)
    {
        if (json == null || StringUtils.isBlank(path))
        {
            return null;
        }
        Object current = json;
        for (String segment : path.split("\\."))
        {
            if (!(current instanceof JSONObject))
            {
                return null;
            }
            current = ((JSONObject) current).get(segment);
            if (current == null)
            {
                return null;
            }
        }
        return current;
    }

    private String replaceTimePlaceholders(String text, LocalDateTime begin, LocalDateTime end)
    {
        String beginTime = begin.format(DATE_TIME_FORMATTER);
        String endTime = end.format(DATE_TIME_FORMATTER);
        String beginIso = begin.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String endIso = end.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String today = end.toLocalDate().toString();
        String todayStart = end.toLocalDate().atStartOfDay().format(DATE_TIME_FORMATTER);
        String todayEnd = end.toLocalDate().atTime(23, 59, 59).format(DATE_TIME_FORMATTER);
        String yyyyMMdd = end.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return text.replace("${beginTime}", beginTime)
                .replace("${endTime}", endTime)
                .replace("${beginTimeIso}", beginIso)
                .replace("${endTimeIso}", endIso)
                .replace("${today}", today)
                .replace("${todayStart}", todayStart)
                .replace("${todayEnd}", todayEnd)
                .replace("${yyyyMMdd}", yyyyMMdd);
    }

    private String resolvePath(Map<String, Object> step, Map<String, Object> target)
    {
        return StringUtils.defaultIfBlank(str(readParams(step), "path"), str(target, "path"));
    }

    private Map<String, Object> readParams(Map<String, Object> step)
    {
        Object value = step == null ? null : step.get("stepParams");
        if (value instanceof Map)
        {
            return castMap(value);
        }
        if (StringUtils.isBlank(value == null ? null : value.toString()))
        {
            return new HashMap<>();
        }
        try
        {
            return JSON.parseObject(value.toString(), Map.class);
        }
        catch (Exception e)
        {
            return new HashMap<>();
        }
    }

    private Map<String, Object> defaultStep(String toolType, String unit)
    {
        Map<String, Object> step = new HashMap<>();
        step.put("toolType", toolType);
        step.put("thresholdUnit", unit);
        step.put("timeoutSeconds", DEFAULT_TIMEOUT_SECONDS);
        step.put("timeWindowMinutes", 0);
        step.put("compareRule", RULE_MAX);
        step.put("stepParams", "{}");
        return step;
    }

    private SupportAutoInspectionExportVo toExportVo(Map<String, Object> detail)
    {
        SupportAutoInspectionExportVo vo = new SupportAutoInspectionExportVo();
        vo.setRecordId(toLong(detail.get("recordId")));
        vo.setInspectionTime(formatDate(detail.get("inspectionTime")));
        vo.setTemplateName(str(detail, "templateName"));
        vo.setPlanName(str(detail, "planName"));
        vo.setSourceType(SOURCE_MANUAL.equals(str(detail, "sourceType")) ? "手动" : "自动");
        vo.setResultStatus(labelResult(str(detail, "resultStatus")));
        vo.setExecutorName(str(detail, "executorName"));
        vo.setSummary(str(detail, "summary"));
        vo.setAbnormalSummary(str(detail, "abnormalSummary"));
        List<String> stepSummaries = new ArrayList<>();
        for (Map<String, Object> step : castList(detail.get("steps")))
        {
            stepSummaries.add(str(step, "stepName") + "：" + str(step, "resultSummary"));
        }
        List<String> targetSummaries = new ArrayList<>();
        for (Map<String, Object> target : castList(detail.get("targetResults")))
        {
            targetSummaries.add(str(target, "targetName") + "：" + str(target, "resultDetail") + StringUtils.defaultIfBlank(str(target, "errorMessage"), ""));
        }
        vo.setStepSummary(StringUtils.join(stepSummaries, "；"));
        vo.setTargetSummary(StringUtils.join(targetSummaries, "；"));
        return vo;
    }

    private String labelResult(String resultStatus)
    {
        if (RESULT_NORMAL.equals(resultStatus))
        {
            return "正常";
        }
        if (RESULT_ABNORMAL.equals(resultStatus))
        {
            return "异常";
        }
        return "未检测";
    }

    private String buildTestSuccessMessage(TargetCheckResult result)
    {
        String actual = result.actualValue == null ? "已连通" : formatDecimal(result.actualValue) + StringUtils.defaultString(result.actualUnit);
        return "测试通过，当前取值：" + actual + "；" + StringUtils.defaultString(result.detail);
    }

    private int resolveTimeout(Map<String, Object> step)
    {
        return Math.max(toInt(step.get("timeoutSeconds"), DEFAULT_TIMEOUT_SECONDS), 3);
    }

    private void requireText(String value, String message)
    {
        if (StringUtils.isBlank(value))
        {
            throw new ServiceException(message);
        }
    }

    private String shellQuote(String value)
    {
        return "'" + StringUtils.defaultString(value).replace("'", "'\"'\"'") + "'";
    }

    private String formatDecimal(BigDecimal value)
    {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }

    private String formatDate(Object value)
    {
        if (value instanceof Date)
        {
            return DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD_HH_MM_SS, (Date) value);
        }
        return value == null ? "" : value.toString();
    }

    private String getCurrentUsername()
    {
        try
        {
            return SecurityUtils.getUsername();
        }
        catch (Exception e)
        {
            return "system";
        }
    }

    private String getCurrentOperatorName()
    {
        try
        {
            if (SecurityUtils.getLoginUser().getUser() != null && StringUtils.isNotBlank(SecurityUtils.getLoginUser().getUser().getNickName()))
            {
                return SecurityUtils.getLoginUser().getUser().getNickName();
            }
            return SecurityUtils.getUsername();
        }
        catch (Exception e)
        {
            return "system";
        }
    }

    private String str(Map<String, Object> map, String key)
    {
        if (map == null)
        {
            return StringUtils.EMPTY;
        }
        Object value = map.get(key);
        return value == null ? StringUtils.EMPTY : value.toString();
    }

    private Long toLong(Object value)
    {
        if (value == null || StringUtils.isBlank(value.toString()))
        {
            return null;
        }
        return value instanceof Number ? ((Number) value).longValue() : Long.valueOf(value.toString());
    }

    private int toInt(Object value, int defaultValue)
    {
        if (value == null || StringUtils.isBlank(value.toString()))
        {
            return defaultValue;
        }
        return value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(value.toString());
    }

    private BigDecimal toBigDecimal(Object value)
    {
        if (value == null || StringUtils.isBlank(value.toString()))
        {
            return null;
        }
        return value instanceof BigDecimal ? (BigDecimal) value : new BigDecimal(value.toString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value)
    {
        return value instanceof Map ? (Map<String, Object>) value : new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castList(Object value)
    {
        return value instanceof List ? (List<Map<String, Object>>) value : new ArrayList<>();
    }

    private List<Long> toLongList(Object value)
    {
        List<Long> result = new ArrayList<>();
        if (!(value instanceof List))
        {
            return result;
        }
        for (Object item : (List<?>) value)
        {
            Long id = toLong(item);
            if (id != null)
            {
                result.add(id);
            }
        }
        return result;
    }

    private static class DiskLine
    {
        private final String mountPoint;
        private final BigDecimal usePercent;

        private DiskLine(String mountPoint, BigDecimal usePercent)
        {
            this.mountPoint = mountPoint;
            this.usePercent = usePercent;
        }
    }

    private static class TargetCheckResult
    {
        private final Long targetId;
        private final String targetName;
        private final String targetType;
        private String status;
        private final BigDecimal actualValue;
        private final String actualUnit;
        private final String detail;
        private String errorMessage;

        private TargetCheckResult(Map<String, Object> target, String status, BigDecimal actualValue, String actualUnit, String detail)
        {
            this.targetId = target == null || target.get("targetId") == null ? null
                    : (target.get("targetId") instanceof Number ? ((Number) target.get("targetId")).longValue() : Long.valueOf(target.get("targetId").toString()));
            this.targetName = target == null ? "" : StringUtils.defaultIfBlank(String.valueOf(target.get("targetName")), "未命名目标");
            this.targetType = target == null ? "" : String.valueOf(target.get("targetType"));
            this.status = status;
            this.actualValue = actualValue;
            this.actualUnit = actualUnit;
            this.detail = detail;
        }

        private static TargetCheckResult normal(Map<String, Object> target, BigDecimal actualValue, String actualUnit, String detail)
        {
            return new TargetCheckResult(target, RESULT_NORMAL, actualValue, actualUnit, detail);
        }

        private static TargetCheckResult abnormal(Map<String, Object> target, BigDecimal actualValue, String actualUnit, String detail)
        {
            return new TargetCheckResult(target, RESULT_ABNORMAL, actualValue, actualUnit, detail);
        }

        private Map<String, Object> toTargetResult(Long recordId, Long stepResultId, Date now, String operator)
        {
            Map<String, Object> result = new HashMap<>();
            result.put("recordId", recordId);
            result.put("stepResultId", stepResultId);
            result.put("targetId", targetId);
            result.put("targetName", targetName);
            result.put("targetType", targetType);
            result.put("resultStatus", status);
            result.put("actualValue", actualValue);
            result.put("actualUnit", actualUnit);
            result.put("resultDetail", detail);
            result.put("errorMessage", errorMessage);
            result.put("createBy", operator);
            result.put("createTime", now);
            result.put("updateBy", operator);
            result.put("updateTime", now);
            return result;
        }
    }
}
