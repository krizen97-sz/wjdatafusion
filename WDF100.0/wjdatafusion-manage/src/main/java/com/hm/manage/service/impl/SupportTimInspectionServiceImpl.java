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
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Vector;
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
import com.hm.manage.domain.SupportTimInspection;
import com.hm.manage.domain.SupportTimInspectionItem;
import com.hm.manage.domain.SupportTimInspectionItemConfig;
import com.hm.manage.domain.SupportTimInspectionTarget;
import com.hm.manage.domain.SupportTimInspectionTargetResult;
import com.hm.manage.domain.vo.SupportTimInspectionDetailVo;
import com.hm.manage.domain.vo.SupportTimInspectionExportVo;
import com.hm.manage.mapper.SupportServerMapper;
import com.hm.manage.mapper.SupportTimInspectionMapper;
import com.hm.manage.service.ISupportTimInspectionService;
import com.hm.manage.service.support.CredentialCryptoService;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

@Service
public class SupportTimInspectionServiceImpl implements ISupportTimInspectionService
{
    private static final String TYPE_TIM = "TIM_GA_VEHICLE";
    private static final String SOURCE_MANUAL = "MANUAL";
    private static final String SOURCE_AUTO = "AUTO";
    private static final String RESULT_NORMAL = "1";
    private static final String RESULT_ABNORMAL = "2";
    private static final String RESULT_SKIP = "3";
    private static final String ENABLED = "Y";
    private static final String DISABLED = "N";
    private static final String STATUS_NORMAL = "0";
    private static final String RULE_MIN = "MIN";
    private static final String RULE_MAX = "MAX";
    private static final int DEFAULT_TIMEOUT_SECONDS = 10;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final List<DefaultItem> DEFAULT_ITEMS = List.of(
            new DefaultItem("VEHICLE_PASS", "过车数量", "HTTP_COUNT", RULE_MIN, new BigDecimal("4000000"), "辆", 1, 480),
            new DefaultItem("FTP_FILE", "FTP文件数量", "FTP", RULE_MAX, new BigDecimal("50"), "个", 2, 0),
            new DefaultItem("DATAI_FILE", "DataI文件数量", "SFTP", RULE_MAX, new BigDecimal("20"), "个", 3, 0),
            new DefaultItem("KAFKA_ORIGIN", "原始Kafka积压", "KAFKA", RULE_MAX, new BigDecimal("2000"), "条", 4, 0),
            new DefaultItem("KAFKA_SECOND", "二次分析Kafka积压", "KAFKA", RULE_MAX, new BigDecimal("2000"), "条", 5, 0),
            new DefaultItem("DISK_USAGE", "大数据服务器磁盘", "SERVER_DISK", RULE_MAX, new BigDecimal("80"), "%", 6, 0),
            new DefaultItem("VEHICLE_ALARM", "违法数量", "HTTP_COUNT", RULE_MIN, new BigDecimal("60000"), "条", 7, 480)
    );

    @Autowired
    private SupportTimInspectionMapper timInspectionMapper;

    @Autowired
    private SupportServerMapper serverMapper;

    @Autowired
    private CredentialCryptoService cryptoService;

    @Override
    public List<SupportTimInspection> selectInspectionList(SupportTimInspection inspection)
    {
        return timInspectionMapper.selectInspectionList(inspection);
    }

    @Override
    public SupportTimInspectionDetailVo selectInspectionDetail(Long inspectionId)
    {
        SupportTimInspection inspection = timInspectionMapper.selectInspectionById(inspectionId);
        if (inspection == null)
        {
            throw new ServiceException("巡检记录不存在");
        }
        SupportTimInspectionDetailVo detailVo = new SupportTimInspectionDetailVo();
        detailVo.setInspection(inspection);
        detailVo.setItems(timInspectionMapper.selectItemsByInspectionId(inspectionId));
        detailVo.setTargetResults(timInspectionMapper.selectTargetResultsByInspectionId(inspectionId));
        return detailVo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SupportTimInspectionDetailVo runManualInspection()
    {
        return runInspection(SOURCE_MANUAL, getCurrentOperatorName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SupportTimInspectionDetailVo runScheduledInspection(String executorName)
    {
        return runInspection(SOURCE_AUTO, StringUtils.defaultIfBlank(executorName, "自动巡检"));
    }

    @Override
    public void exportInspection(HttpServletResponse response, SupportTimInspection inspection)
    {
        List<SupportTimInspection> inspections = timInspectionMapper.selectInspectionList(inspection);
        List<SupportTimInspectionExportVo> exportList = new ArrayList<>();
        for (SupportTimInspection item : inspections)
        {
            SupportTimInspectionDetailVo detail = selectInspectionDetail(item.getInspectionId());
            exportList.add(toExportVo(detail));
        }
        ExcelUtil<SupportTimInspectionExportVo> util = new ExcelUtil<>(SupportTimInspectionExportVo.class);
        util.exportExcel(response, exportList, "TIM系统巡检记录");
    }

    @Override
    public List<SupportTimInspectionItemConfig> selectConfigList()
    {
        ensureDefaultConfigs();
        return timInspectionMapper.selectItemConfigList();
    }

    @Override
    public int updateItemConfig(SupportTimInspectionItemConfig config)
    {
        ensureDefaultConfigs();
        SupportTimInspectionItemConfig original = timInspectionMapper.selectItemConfigByCode(config.getItemCode());
        if (original == null)
        {
            throw new ServiceException("巡检项配置不存在");
        }
        normalizeItemConfig(config, original);
        config.setConfigId(original.getConfigId());
        config.setUpdateBy(getCurrentUsername());
        config.setUpdateTime(DateUtils.getNowDate());
        return timInspectionMapper.updateItemConfig(config);
    }

    @Override
    public List<SupportTimInspectionTarget> selectTargetList(SupportTimInspectionTarget target)
    {
        List<SupportTimInspectionTarget> list = timInspectionMapper.selectTargetList(target);
        for (SupportTimInspectionTarget item : list)
        {
            maskTargetSecret(item);
        }
        return list;
    }

    @Override
    public SupportTimInspectionTarget selectTargetById(Long targetId)
    {
        SupportTimInspectionTarget target = timInspectionMapper.selectTargetById(targetId);
        if (target == null)
        {
            throw new ServiceException("巡检目标不存在");
        }
        maskTargetSecret(target);
        return target;
    }

    @Override
    public int insertTarget(SupportTimInspectionTarget target)
    {
        ensureDefaultConfigs();
        validateAndNormalizeTarget(target, false);
        encryptTargetSecret(target);
        target.setCreateBy(getCurrentUsername());
        target.setCreateTime(DateUtils.getNowDate());
        return timInspectionMapper.insertTarget(target);
    }

    @Override
    public int updateTarget(SupportTimInspectionTarget target)
    {
        ensureDefaultConfigs();
        SupportTimInspectionTarget original = timInspectionMapper.selectTargetById(target.getTargetId());
        if (original == null)
        {
            throw new ServiceException("巡检目标不存在");
        }
        validateAndNormalizeTarget(target, true);
        encryptTargetSecret(target);
        target.setUpdateBy(getCurrentUsername());
        target.setUpdateTime(DateUtils.getNowDate());
        return timInspectionMapper.updateTarget(target);
    }

    @Override
    public int deleteTargetById(Long targetId)
    {
        return timInspectionMapper.deleteTargetById(targetId);
    }

    @Override
    public String testTarget(SupportTimInspectionTarget target)
    {
        SupportTimInspectionTarget effectiveTarget = buildEffectiveTargetForTest(target);
        SupportTimInspectionItemConfig config = timInspectionMapper.selectItemConfigByCode(effectiveTarget.getItemCode());
        if (config == null)
        {
            throw new ServiceException("巡检项配置不存在");
        }
        TargetCheckResult result = runSingleTarget(config, effectiveTarget, false);
        if (RESULT_ABNORMAL.equals(result.status))
        {
            throw new ServiceException(StringUtils.defaultIfBlank(result.errorMessage, result.detail));
        }
        return buildTestSuccessMessage(result);
    }

    @Override
    public String getTargetPasswordPlain(Long targetId)
    {
        SupportTimInspectionTarget target = timInspectionMapper.selectTargetById(targetId);
        if (target == null)
        {
            return StringUtils.EMPTY;
        }
        return decryptQuietly(target.getPasswordCipher());
    }

    @Override
    public String getTargetSecretPlain(Long targetId)
    {
        SupportTimInspectionTarget target = timInspectionMapper.selectTargetById(targetId);
        if (target == null)
        {
            return StringUtils.EMPTY;
        }
        return decryptQuietly(target.getSecretCipher());
    }

    private SupportTimInspectionDetailVo runInspection(String sourceType, String executorName)
    {
        ensureDefaultConfigs();
        Date now = DateUtils.getNowDate();
        SupportTimInspection inspection = new SupportTimInspection();
        inspection.setInspectionTime(now);
        inspection.setInspectionType(TYPE_TIM);
        inspection.setSourceType(sourceType);
        inspection.setResultStatus(RESULT_SKIP);
        inspection.setExecutorName(executorName);
        inspection.setCreateBy(getCurrentUsername());
        inspection.setCreateTime(now);
        timInspectionMapper.insertInspection(inspection);

        int enabledCount = 0;
        int skippedCount = 0;
        int abnormalCount = 0;
        List<String> abnormalSummaries = new ArrayList<>();
        List<SupportTimInspectionItemConfig> configs = timInspectionMapper.selectItemConfigList();
        for (SupportTimInspectionItemConfig config : configs)
        {
            SupportTimInspectionItem item = copyConfigToItem(config, inspection.getInspectionId(), now);
            if (!ENABLED.equals(config.getEnabledFlag()))
            {
                skippedCount++;
                item.setResultStatus(RESULT_SKIP);
                item.setResultSummary(config.getItemName() + "已关闭，本次跳过");
                timInspectionMapper.insertInspectionItem(item);
                continue;
            }

            enabledCount++;
            List<SupportTimInspectionTarget> targets = timInspectionMapper.selectEnabledTargetsByItemCode(config.getItemCode());
            if (targets.isEmpty())
            {
                abnormalCount++;
                item.setResultStatus(RESULT_ABNORMAL);
                item.setResultSummary(config.getItemName() + "已启用但未配置目标");
                abnormalSummaries.add(item.getResultSummary());
                timInspectionMapper.insertInspectionItem(item);
                continue;
            }

            List<TargetCheckResult> targetResults = new ArrayList<>();
            boolean hasAbnormal = false;
            for (SupportTimInspectionTarget target : targets)
            {
                TargetCheckResult result = runSingleTarget(config, withPlainSecret(target));
                targetResults.add(result);
                if (RESULT_ABNORMAL.equals(result.status))
                {
                    hasAbnormal = true;
                }
            }

            item.setResultStatus(hasAbnormal ? RESULT_ABNORMAL : RESULT_NORMAL);
            item.setActualValue(resolveItemActualValue(config, targetResults));
            item.setActualUnit(config.getThresholdUnit());
            item.setResultSummary(buildItemSummary(config, targetResults, hasAbnormal));
            if (hasAbnormal)
            {
                abnormalCount++;
                abnormalSummaries.add(item.getItemName() + "：" + item.getResultSummary());
            }
            timInspectionMapper.insertInspectionItem(item);
            for (TargetCheckResult result : targetResults)
            {
                SupportTimInspectionTargetResult targetResult = result.toTargetResult(inspection.getInspectionId(), item.getItemId(), now, getCurrentUsername());
                timInspectionMapper.insertTargetResult(targetResult);
            }
        }

        inspection.setEnabledItemCount(enabledCount);
        inspection.setSkippedItemCount(skippedCount);
        inspection.setResultStatus(resolveInspectionStatus(enabledCount, abnormalCount));
        inspection.setSummary("启用" + enabledCount + "项，跳过" + skippedCount + "项，异常" + abnormalCount + "项");
        inspection.setAbnormalSummary(abnormalSummaries.isEmpty() ? "无异常" : StringUtils.join(abnormalSummaries, "；"));
        inspection.setUpdateBy(getCurrentUsername());
        inspection.setUpdateTime(DateUtils.getNowDate());
        timInspectionMapper.updateInspection(inspection);
        return selectInspectionDetail(inspection.getInspectionId());
    }

    private TargetCheckResult runSingleTarget(SupportTimInspectionItemConfig config, SupportTimInspectionTarget target)
    {
        return runSingleTarget(config, target, true);
    }

    private TargetCheckResult runSingleTarget(SupportTimInspectionItemConfig config, SupportTimInspectionTarget target, boolean thresholdEnabled)
    {
        try
        {
            TargetCheckResult result;
            switch (config.getItemType())
            {
                case "FTP":
                    result = checkFtpFileCount(config, target);
                    break;
                case "SFTP":
                    result = checkSftpFileCount(config, target);
                    break;
                case "KAFKA":
                    result = checkKafkaLag(config, target);
                    break;
                case "SERVER_DISK":
                    result = checkServerDisk(config, target);
                    break;
                case "HTTP_COUNT":
                default:
                    result = checkHttpCount(config, target);
                    break;
            }
            if (thresholdEnabled)
            {
                applyThreshold(config, result);
            }
            return result;
        }
        catch (Exception e)
        {
            TargetCheckResult result = TargetCheckResult.abnormal(target, null, config.getThresholdUnit(), "检测失败：" + e.getMessage());
            result.errorMessage = e.getMessage();
            return result;
        }
    }

    private TargetCheckResult checkHttpCount(SupportTimInspectionItemConfig config, SupportTimInspectionTarget target) throws Exception
    {
        if (StringUtils.isBlank(target.getUrl()))
        {
            throw new ServiceException("HTTP目标URL不能为空");
        }
        int timeout = resolveTimeout(config);
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime begin = end.minusMinutes(config.getTimeWindowMinutes() == null ? 0 : config.getTimeWindowMinutes());
        String body = replaceTimePlaceholders(StringUtils.defaultString(target.getExtraParams()), begin, end);
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(timeout)).build();
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(target.getUrl()))
                .timeout(Duration.ofSeconds(timeout))
                .header("Content-Type", "application/json");
        if (StringUtils.isNotBlank(target.getAppKey()))
        {
            builder.header("X-App-Key", target.getAppKey());
        }
        if (StringUtils.isNotBlank(target.getSecret()))
        {
            builder.header("X-App-Secret", target.getSecret());
        }
        if ("POST".equalsIgnoreCase(target.getHttpMethod()))
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
        BigDecimal value = extractNumber(response.body(), target.getResultPath());
        return TargetCheckResult.normal(target, value, config.getThresholdUnit(), "接口返回计数 " + formatDecimal(value));
    }

    private TargetCheckResult checkFtpFileCount(SupportTimInspectionItemConfig config, SupportTimInspectionTarget target) throws Exception
    {
        FTPClient client = new FTPClient();
        int timeout = resolveTimeout(config) * 1000;
        client.setConnectTimeout(timeout);
        client.setDefaultTimeout(timeout);
        client.setDataTimeout(Duration.ofMillis(timeout));
        try
        {
            client.connect(target.getHost(), resolvePort(target, 21));
            if (!client.login(target.getUsername(), target.getPassword()))
            {
                throw new ServiceException("FTP登录失败");
            }
            client.enterLocalPassiveMode();
            if (StringUtils.isNotBlank(target.getPath()) && !client.changeWorkingDirectory(target.getPath()))
            {
                throw new ServiceException("FTP目录不存在或无权限：" + target.getPath());
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
            return TargetCheckResult.normal(target, new BigDecimal(count), config.getThresholdUnit(), "文件数量 " + count);
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

    private TargetCheckResult checkSftpFileCount(SupportTimInspectionItemConfig config, SupportTimInspectionTarget target) throws Exception
    {
        Session session = createSshSession(target.getHost(), resolvePort(target, 22), target.getUsername(), target.getPassword(), resolveTimeout(config));
        ChannelSftp channel = null;
        try
        {
            Channel rawChannel = session.openChannel("sftp");
            rawChannel.connect(resolveTimeout(config) * 1000);
            channel = (ChannelSftp) rawChannel;
            if (StringUtils.isNotBlank(target.getPath()))
            {
                channel.cd(target.getPath());
            }
            @SuppressWarnings("unchecked")
            Vector<ChannelSftp.LsEntry> entries = channel.ls(".");
            int count = 0;
            for (ChannelSftp.LsEntry entry : entries)
            {
                if (!entry.getAttrs().isDir())
                {
                    count++;
                }
            }
            return TargetCheckResult.normal(target, new BigDecimal(count), config.getThresholdUnit(), "文件数量 " + count);
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

    private TargetCheckResult checkKafkaLag(SupportTimInspectionItemConfig config, SupportTimInspectionTarget target)
    {
        if (StringUtils.isBlank(target.getHost()) || StringUtils.isBlank(target.getTopic()) || StringUtils.isBlank(target.getConsumerGroup()))
        {
            throw new ServiceException("Kafka目标需配置bootstrap、topic和消费组");
        }
        Properties props = new Properties();
        props.put("bootstrap.servers", target.getHost());
        props.put("group.id", target.getConsumerGroup());
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("enable.auto.commit", "false");
        props.put("request.timeout.ms", String.valueOf(resolveTimeout(config) * 1000));
        props.put("default.api.timeout.ms", String.valueOf(resolveTimeout(config) * 1000));
        long maxLag = 0L;
        long sumLag = 0L;
        int partitionCount = 0;
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props))
        {
            List<PartitionInfo> partitions = consumer.partitionsFor(target.getTopic());
            if (partitions == null || partitions.isEmpty())
            {
                throw new ServiceException("Kafka主题不存在或无分区：" + target.getTopic());
            }
            List<TopicPartition> topicPartitions = new ArrayList<>();
            for (PartitionInfo partition : partitions)
            {
                topicPartitions.add(new TopicPartition(target.getTopic(), partition.partition()));
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
        return TargetCheckResult.normal(target, new BigDecimal(maxLag), config.getThresholdUnit(),
                "最大积压 " + maxLag + "，平均积压 " + average);
    }

    private TargetCheckResult checkServerDisk(SupportTimInspectionItemConfig config, SupportTimInspectionTarget target) throws Exception
    {
        if (target.getServerId() == null)
        {
            throw new ServiceException("磁盘巡检目标需选择服务器");
        }
        SupportServer server = serverMapper.selectSupportServerByServerId(target.getServerId());
        if (server == null)
        {
            throw new ServiceException("服务器不存在");
        }
        String password = cryptoService.decrypt(server.getOsPasswordCipher());
        Session session = createSshSession(server.getServerAddress(), server.getSshPort() == null ? 22 : server.getSshPort(),
                server.getOsUsername(), password, resolveTimeout(config));
        ChannelExec channel = null;
        try
        {
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand("df -P");
            InputStream inputStream = channel.getInputStream();
            channel.connect(resolveTimeout(config) * 1000);
            List<DiskLine> lines = readDiskLines(inputStream);
            BigDecimal maxUsage = BigDecimal.ZERO;
            StringBuilder detail = new StringBuilder();
            for (DiskLine line : lines)
            {
                if (StringUtils.isBlank(target.getPath()) && "/".equals(line.mountPoint))
                {
                    continue;
                }
                if (StringUtils.isNotBlank(target.getPath()) && !target.getPath().equals(line.mountPoint))
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
            throw new ServiceException("SSH/SFTP目标需配置主机和账号");
        }
        JSch jsch = new JSch();
        Session session = jsch.getSession(username, host, port);
        session.setPassword(password);
        session.setConfig("StrictHostKeyChecking", "no");
        session.setConfig("PreferredAuthentications", "password");
        session.connect(timeoutSeconds * 1000);
        return session;
    }

    private List<DiskLine> readDiskLines(InputStream inputStream) throws Exception
    {
        List<DiskLine> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)))
        {
            String line;
            boolean skippedHeader = false;
            while ((line = reader.readLine()) != null)
            {
                if (!skippedHeader)
                {
                    skippedHeader = true;
                    continue;
                }
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 6)
                {
                    continue;
                }
                String percent = parts[4].replace("%", "");
                lines.add(new DiskLine(parts[5], new BigDecimal(percent)));
            }
        }
        return lines;
    }

    private void applyThreshold(SupportTimInspectionItemConfig config, TargetCheckResult result)
    {
        if (result.actualValue == null || config.getThresholdValue() == null)
        {
            result.status = RESULT_NORMAL;
            return;
        }
        int compared = result.actualValue.compareTo(config.getThresholdValue());
        boolean abnormal = RULE_MIN.equals(config.getCompareRule()) ? compared < 0 : compared > 0;
        result.status = abnormal ? RESULT_ABNORMAL : RESULT_NORMAL;
        if (abnormal)
        {
            String relation = RULE_MIN.equals(config.getCompareRule()) ? "低于" : "高于";
            result.errorMessage = "实际值" + formatDecimal(result.actualValue) + result.actualUnit
                    + relation + "阈值" + formatDecimal(config.getThresholdValue()) + StringUtils.defaultString(config.getThresholdUnit());
        }
    }

    private BigDecimal resolveItemActualValue(SupportTimInspectionItemConfig config, List<TargetCheckResult> results)
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
        return RULE_MIN.equals(config.getCompareRule()) ? Collections.min(values) : Collections.max(values);
    }

    private String buildItemSummary(SupportTimInspectionItemConfig config, List<TargetCheckResult> results, boolean hasAbnormal)
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
        BigDecimal actual = resolveItemActualValue(config, results);
        return "共" + results.size() + "个目标，检测正常，代表值" + formatDecimal(actual) + StringUtils.defaultString(config.getThresholdUnit());
    }

    private String resolveInspectionStatus(int enabledCount, int abnormalCount)
    {
        if (enabledCount == 0)
        {
            return RESULT_SKIP;
        }
        return abnormalCount > 0 ? RESULT_ABNORMAL : RESULT_NORMAL;
    }

    private SupportTimInspectionItem copyConfigToItem(SupportTimInspectionItemConfig config, Long inspectionId, Date now)
    {
        SupportTimInspectionItem item = new SupportTimInspectionItem();
        item.setInspectionId(inspectionId);
        item.setItemCode(config.getItemCode());
        item.setItemName(config.getItemName());
        item.setItemType(config.getItemType());
        item.setEnabledFlag(config.getEnabledFlag());
        item.setSortOrder(config.getSortOrder());
        item.setThresholdValue(config.getThresholdValue());
        item.setThresholdUnit(config.getThresholdUnit());
        item.setCompareRule(config.getCompareRule());
        item.setTimeWindowMinutes(config.getTimeWindowMinutes());
        item.setTimeoutSeconds(config.getTimeoutSeconds());
        item.setCreateBy(getCurrentUsername());
        item.setCreateTime(now);
        return item;
    }

    private void validateAndNormalizeTarget(SupportTimInspectionTarget target, boolean update)
    {
        if (target == null)
        {
            throw new ServiceException("巡检目标不能为空");
        }
        if (update && target.getTargetId() == null)
        {
            throw new ServiceException("巡检目标ID不能为空");
        }
        SupportTimInspectionItemConfig config = timInspectionMapper.selectItemConfigByCode(target.getItemCode());
        if (config == null)
        {
            throw new ServiceException("巡检项配置不存在");
        }
        target.setTargetType(config.getItemType());
        target.setTargetName(StringUtils.trimToEmpty(target.getTargetName()));
        if (StringUtils.isBlank(target.getTargetName()))
        {
            throw new ServiceException("巡检目标名称不能为空");
        }
        target.setStatus(StringUtils.defaultIfBlank(target.getStatus(), STATUS_NORMAL));
        switch (config.getItemType())
        {
            case "FTP":
            case "SFTP":
                requireText(target.getHost(), "主机地址不能为空");
                requireText(target.getUsername(), "账号不能为空");
                target.setPort(resolvePort(target, "FTP".equals(config.getItemType()) ? 21 : 22));
                break;
            case "KAFKA":
                requireText(target.getHost(), "Kafka bootstrap不能为空");
                requireText(target.getTopic(), "Kafka topic不能为空");
                requireText(target.getConsumerGroup(), "Kafka消费组不能为空");
                break;
            case "SERVER_DISK":
                if (target.getServerId() == null)
                {
                    throw new ServiceException("请选择服务器");
                }
                break;
            case "HTTP_COUNT":
            default:
                requireText(target.getUrl(), "HTTP接口地址不能为空");
                target.setHttpMethod(StringUtils.defaultIfBlank(target.getHttpMethod(), "POST").toUpperCase());
                target.setResultPath(StringUtils.defaultIfBlank(target.getResultPath(), "data.total"));
                break;
        }
    }

    private void normalizeItemConfig(SupportTimInspectionItemConfig config, SupportTimInspectionItemConfig original)
    {
        config.setItemCode(original.getItemCode());
        config.setItemName(StringUtils.defaultIfBlank(config.getItemName(), original.getItemName()));
        config.setItemType(original.getItemType());
        config.setEnabledFlag(ENABLED.equals(config.getEnabledFlag()) ? ENABLED : DISABLED);
        config.setSortOrder(config.getSortOrder() == null ? original.getSortOrder() : config.getSortOrder());
        config.setThresholdValue(config.getThresholdValue() == null ? original.getThresholdValue() : config.getThresholdValue());
        config.setThresholdUnit(StringUtils.defaultIfBlank(config.getThresholdUnit(), original.getThresholdUnit()));
        config.setCompareRule(RULE_MIN.equals(config.getCompareRule()) ? RULE_MIN : RULE_MAX);
        config.setTimeWindowMinutes(config.getTimeWindowMinutes() == null ? original.getTimeWindowMinutes() : config.getTimeWindowMinutes());
        config.setTimeoutSeconds(config.getTimeoutSeconds() == null ? original.getTimeoutSeconds() : config.getTimeoutSeconds());
        config.setStatus(StringUtils.defaultIfBlank(config.getStatus(), original.getStatus()));
    }

    private SupportTimInspectionTarget buildEffectiveTargetForTest(SupportTimInspectionTarget target)
    {
        if (target != null && target.getTargetId() != null)
        {
            SupportTimInspectionTarget persisted = timInspectionMapper.selectTargetById(target.getTargetId());
            if (persisted == null)
            {
                throw new ServiceException("巡检目标不存在");
            }
            mergeTargetForTest(persisted, target);
            validateAndNormalizeTarget(persisted, true);
            return withPlainSecret(persisted);
        }
        validateAndNormalizeTarget(target, false);
        return target;
    }

    private void mergeTargetForTest(SupportTimInspectionTarget persisted, SupportTimInspectionTarget form)
    {
        if (StringUtils.isNotBlank(form.getItemCode()))
        {
            persisted.setItemCode(form.getItemCode());
        }
        if (form.getTargetName() != null)
        {
            persisted.setTargetName(form.getTargetName());
        }
        if (form.getTargetType() != null)
        {
            persisted.setTargetType(form.getTargetType());
        }
        if (form.getServerId() != null)
        {
            persisted.setServerId(form.getServerId());
        }
        if (form.getHost() != null)
        {
            persisted.setHost(form.getHost());
        }
        if (form.getPort() != null)
        {
            persisted.setPort(form.getPort());
        }
        if (form.getPath() != null)
        {
            persisted.setPath(form.getPath());
        }
        if (form.getUrl() != null)
        {
            persisted.setUrl(form.getUrl());
        }
        if (form.getHttpMethod() != null)
        {
            persisted.setHttpMethod(form.getHttpMethod());
        }
        if (form.getTopic() != null)
        {
            persisted.setTopic(form.getTopic());
        }
        if (form.getConsumerGroup() != null)
        {
            persisted.setConsumerGroup(form.getConsumerGroup());
        }
        if (form.getUsername() != null)
        {
            persisted.setUsername(form.getUsername());
        }
        if (StringUtils.isNotBlank(form.getPassword()))
        {
            persisted.setPassword(form.getPassword());
        }
        if (form.getAppKey() != null)
        {
            persisted.setAppKey(form.getAppKey());
        }
        if (StringUtils.isNotBlank(form.getSecret()))
        {
            persisted.setSecret(form.getSecret());
        }
        if (form.getResultPath() != null)
        {
            persisted.setResultPath(form.getResultPath());
        }
        if (form.getExtraParams() != null)
        {
            persisted.setExtraParams(form.getExtraParams());
        }
        if (form.getStatus() != null)
        {
            persisted.setStatus(form.getStatus());
        }
        if (form.getRemark() != null)
        {
            persisted.setRemark(form.getRemark());
        }
    }

    private String buildTestSuccessMessage(TargetCheckResult result)
    {
        String actual = result.actualValue == null ? "已连通" : formatDecimal(result.actualValue) + StringUtils.defaultString(result.actualUnit);
        return "测试通过，当前取值：" + actual + "；" + StringUtils.defaultString(result.detail);
    }

    private SupportTimInspectionTarget withPlainSecret(SupportTimInspectionTarget target)
    {
        if (StringUtils.isBlank(target.getPassword()) && StringUtils.isNotBlank(target.getPasswordCipher()))
        {
            target.setPassword(decryptQuietly(target.getPasswordCipher()));
        }
        if (StringUtils.isBlank(target.getSecret()) && StringUtils.isNotBlank(target.getSecretCipher()))
        {
            target.setSecret(decryptQuietly(target.getSecretCipher()));
        }
        return target;
    }

    private void encryptTargetSecret(SupportTimInspectionTarget target)
    {
        if (StringUtils.isNotBlank(target.getPassword()))
        {
            target.setPasswordCipher(cryptoService.encrypt(target.getPassword()));
        }
        if (StringUtils.isNotBlank(target.getSecret()))
        {
            target.setSecretCipher(cryptoService.encrypt(target.getSecret()));
        }
        target.setPassword(null);
        target.setSecret(null);
    }

    private void maskTargetSecret(SupportTimInspectionTarget target)
    {
        if (target == null)
        {
            return;
        }
        if (StringUtils.isNotBlank(target.getPasswordCipher()))
        {
            target.setPassword("******");
        }
        if (StringUtils.isNotBlank(target.getSecretCipher()))
        {
            target.setSecret("******");
        }
    }

    private String decryptQuietly(String cipherText)
    {
        if (StringUtils.isBlank(cipherText))
        {
            return StringUtils.EMPTY;
        }
        return cryptoService.decrypt(cipherText);
    }

    private void ensureDefaultConfigs()
    {
        for (DefaultItem defaultItem : DEFAULT_ITEMS)
        {
            if (timInspectionMapper.selectItemConfigByCode(defaultItem.itemCode) != null)
            {
                continue;
            }
            SupportTimInspectionItemConfig config = new SupportTimInspectionItemConfig();
            config.setItemCode(defaultItem.itemCode);
            config.setItemName(defaultItem.itemName);
            config.setItemType(defaultItem.itemType);
            config.setEnabledFlag(ENABLED);
            config.setSortOrder(defaultItem.sortOrder);
            config.setThresholdValue(defaultItem.thresholdValue);
            config.setThresholdUnit(defaultItem.thresholdUnit);
            config.setCompareRule(defaultItem.compareRule);
            config.setTimeWindowMinutes(defaultItem.timeWindowMinutes);
            config.setTimeoutSeconds(DEFAULT_TIMEOUT_SECONDS);
            config.setStatus(STATUS_NORMAL);
            config.setCreateBy("system");
            config.setCreateTime(DateUtils.getNowDate());
            config.setRemark("TIM系统巡检内置项");
            timInspectionMapper.insertItemConfig(config);
        }
    }

    private SupportTimInspectionExportVo toExportVo(SupportTimInspectionDetailVo detail)
    {
        SupportTimInspection inspection = detail.getInspection();
        Map<String, String> itemSummaryMap = new HashMap<>();
        for (SupportTimInspectionItem item : detail.getItems())
        {
            itemSummaryMap.put(item.getItemCode(), item.getResultSummary());
        }
        SupportTimInspectionExportVo vo = new SupportTimInspectionExportVo();
        vo.setInspectionId(inspection.getInspectionId());
        vo.setInspectionTime(DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD_HH_MM_SS, inspection.getInspectionTime()));
        vo.setSourceType(labelSource(inspection.getSourceType()));
        vo.setResultStatus(labelResult(inspection.getResultStatus()));
        vo.setExecutorName(inspection.getExecutorName());
        vo.setSummary(inspection.getSummary());
        vo.setAbnormalSummary(inspection.getAbnormalSummary());
        vo.setVehiclePass(itemSummaryMap.get("VEHICLE_PASS"));
        vo.setFtpFile(itemSummaryMap.get("FTP_FILE"));
        vo.setDataiFile(itemSummaryMap.get("DATAI_FILE"));
        vo.setKafkaOrigin(itemSummaryMap.get("KAFKA_ORIGIN"));
        vo.setKafkaSecond(itemSummaryMap.get("KAFKA_SECOND"));
        vo.setDiskUsage(itemSummaryMap.get("DISK_USAGE"));
        vo.setVehicleAlarm(itemSummaryMap.get("VEHICLE_ALARM"));
        return vo;
    }

    private String labelSource(String sourceType)
    {
        return SOURCE_MANUAL.equals(sourceType) ? "手动" : "自动";
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
        return text.replace("${beginTime}", beginTime)
                .replace("${endTime}", endTime)
                .replace("${beginTimeIso}", beginIso)
                .replace("${endTimeIso}", endIso);
    }

    private int resolvePort(SupportTimInspectionTarget target, int defaultPort)
    {
        return target.getPort() == null ? defaultPort : target.getPort();
    }

    private int resolveTimeout(SupportTimInspectionItemConfig config)
    {
        return config.getTimeoutSeconds() == null || config.getTimeoutSeconds() <= 0 ? DEFAULT_TIMEOUT_SECONDS : config.getTimeoutSeconds();
    }

    private void requireText(String value, String message)
    {
        if (StringUtils.isBlank(value))
        {
            throw new ServiceException(message);
        }
    }

    private String formatDecimal(BigDecimal value)
    {
        if (value == null)
        {
            return "-";
        }
        return value.stripTrailingZeros().toPlainString();
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

    private static class DefaultItem
    {
        private final String itemCode;
        private final String itemName;
        private final String itemType;
        private final String compareRule;
        private final BigDecimal thresholdValue;
        private final String thresholdUnit;
        private final int sortOrder;
        private final int timeWindowMinutes;

        private DefaultItem(String itemCode, String itemName, String itemType, String compareRule,
                            BigDecimal thresholdValue, String thresholdUnit, int sortOrder, int timeWindowMinutes)
        {
            this.itemCode = itemCode;
            this.itemName = itemName;
            this.itemType = itemType;
            this.compareRule = compareRule;
            this.thresholdValue = thresholdValue;
            this.thresholdUnit = thresholdUnit;
            this.sortOrder = sortOrder;
            this.timeWindowMinutes = timeWindowMinutes;
        }
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

        private TargetCheckResult(SupportTimInspectionTarget target, String status, BigDecimal actualValue,
                                  String actualUnit, String detail)
        {
            this.targetId = target.getTargetId();
            this.targetName = target.getTargetName();
            this.targetType = target.getTargetType();
            this.status = status;
            this.actualValue = actualValue;
            this.actualUnit = actualUnit;
            this.detail = detail;
        }

        private static TargetCheckResult normal(SupportTimInspectionTarget target, BigDecimal actualValue,
                                                String actualUnit, String detail)
        {
            return new TargetCheckResult(target, RESULT_NORMAL, actualValue, actualUnit, detail);
        }

        private static TargetCheckResult abnormal(SupportTimInspectionTarget target, BigDecimal actualValue,
                                                  String actualUnit, String detail)
        {
            return new TargetCheckResult(target, RESULT_ABNORMAL, actualValue, actualUnit, detail);
        }

        private SupportTimInspectionTargetResult toTargetResult(Long inspectionId, Long itemId, Date now, String operator)
        {
            SupportTimInspectionTargetResult targetResult = new SupportTimInspectionTargetResult();
            targetResult.setInspectionId(inspectionId);
            targetResult.setItemId(itemId);
            targetResult.setTargetId(targetId);
            targetResult.setTargetName(targetName);
            targetResult.setTargetType(targetType);
            targetResult.setResultStatus(status);
            targetResult.setActualValue(actualValue);
            targetResult.setActualUnit(actualUnit);
            targetResult.setResultDetail(detail);
            targetResult.setErrorMessage(errorMessage);
            targetResult.setCreateBy(operator);
            targetResult.setCreateTime(now);
            return targetResult;
        }
    }
}
