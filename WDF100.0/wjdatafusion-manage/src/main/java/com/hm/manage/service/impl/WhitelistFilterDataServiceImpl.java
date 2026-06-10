package com.hm.manage.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.hm.common.exception.ServiceException;
import com.hm.common.utils.DateUtils;
import com.hm.common.utils.SecurityUtils;
import com.hm.common.utils.StringUtils;
import com.hm.manage.config.WhitelistKafkaProperties;
import com.hm.manage.domain.WhitelistFilterData;
import com.hm.manage.domain.vo.WhitelistKafkaPullResultVo;
import com.hm.manage.mapper.WhitelistFilterDataMapper;
import com.hm.manage.service.IWhitelistFilterDataService;
import com.hm.manage.service.IWhitelistPlateService;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@Service
public class WhitelistFilterDataServiceImpl implements IWhitelistFilterDataService
{
    private final WhitelistFilterDataMapper whitelistFilterDataMapper;
    private final WhitelistKafkaProperties whitelistKafkaProperties;
    private final IWhitelistPlateService whitelistPlateService;

    public WhitelistFilterDataServiceImpl(WhitelistFilterDataMapper whitelistFilterDataMapper,
            WhitelistKafkaProperties whitelistKafkaProperties,
            IWhitelistPlateService whitelistPlateService)
    {
        this.whitelistFilterDataMapper = whitelistFilterDataMapper;
        this.whitelistKafkaProperties = whitelistKafkaProperties;
        this.whitelistPlateService = whitelistPlateService;
    }

    @Override
    public WhitelistFilterData selectWhitelistFilterDataById(Long id)
    {
        WhitelistFilterData data = whitelistFilterDataMapper.selectWhitelistFilterDataById(id);
        if (data == null)
        {
            return null;
        }
        validateFilterDataAccess(data);
        return data;
    }

    @Override
    public List<WhitelistFilterData> selectWhitelistFilterDataList(WhitelistFilterData whitelistFilterData)
    {
        applyPlateDataScope(whitelistFilterData);
        return whitelistFilterDataMapper.selectWhitelistFilterDataList(whitelistFilterData);
    }

    @Override
    public int insertWhitelistFilterData(WhitelistFilterData whitelistFilterData)
    {
        whitelistFilterData.setCreateTime(DateUtils.getNowDate());
        return whitelistFilterDataMapper.insertWhitelistFilterData(whitelistFilterData);
    }

    @Override
    public int deleteWhitelistFilterDataByIds(Long[] ids)
    {
        if (ids == null || ids.length == 0)
        {
            return 0;
        }
        if (SecurityUtils.isAdmin())
        {
            return whitelistFilterDataMapper.deleteWhitelistFilterDataByIds(ids);
        }
        List<WhitelistFilterData> currentList = whitelistFilterDataMapper.selectWhitelistFilterDataByIds(ids);
        List<String> plateScopeList = whitelistPlateService.selectAuthorizedVehiclePlates();
        Long[] authorizedIds = currentList.stream()
                .filter(item -> plateScopeList.contains(item.getPlateNo()))
                .map(WhitelistFilterData::getId)
                .toArray(Long[]::new);
        if (authorizedIds.length == 0)
        {
            return 0;
        }
        return whitelistFilterDataMapper.deleteWhitelistFilterDataByIds(authorizedIds);
    }

    @Override
    public Map<String, Object> getDashboardSummary()
    {
        WhitelistFilterData query = new WhitelistFilterData();
        applyPlateDataScope(query);
        List<String> plateScopeList = whitelistPlateService.selectAuthorizedVehiclePlates();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("plateCount", plateScopeList == null ? 0 : plateScopeList.size());
        summary.put("filterDataCount", whitelistFilterDataMapper.countWhitelistFilterData(query));
        summary.put("filteredPlateCount", whitelistFilterDataMapper.countDistinctPlateNo(query));
        return summary;
    }

    @Override
    public WhitelistKafkaPullResultVo pullKafkaData()
    {
        validateKafkaConfig();
        WhitelistKafkaPullResultVo result = new WhitelistKafkaPullResultVo();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(buildConsumerProperties()))
        {
            consumer.subscribe(Collections.singletonList(whitelistKafkaProperties.getTopic()));
            int rounds = whitelistKafkaProperties.getPullRounds() == null ? 3 : Math.max(1, whitelistKafkaProperties.getPullRounds());
            boolean hasRecords = false;
            for (int i = 0; i < rounds; i++)
            {
                ConsumerRecords<String, String> records = consumer.poll(whitelistKafkaProperties.getPollTimeoutMs());
                if (records.isEmpty())
                {
                    continue;
                }
                hasRecords = true;
                result.setPolledMessages(result.getPolledMessages() + records.count());
                for (ConsumerRecord<String, String> record : records)
                {
                    HandleResult handleResult = handleRecord(record.value());
                    result.setParsedMessages(result.getParsedMessages() + handleResult.parsedMessages);
                    result.setInsertedRows(result.getInsertedRows() + handleResult.insertedRows);
                    result.setSkippedMessages(result.getSkippedMessages() + handleResult.skippedMessages);
                }
                break;
            }
            if (hasRecords)
            {
                consumer.commitSync();
            }
        }
        catch (Exception e)
        {
            throw new ServiceException("拉取Kafka过滤数据失败: " + e.getMessage()).setDetailMessage(e.getMessage());
        }
        return result;
    }

    @Override
    public void publishKafkaData(String message)
    {
        validateKafkaConfig();
        if (StringUtils.isEmpty(message))
        {
            throw new ServiceException("消息内容不能为空");
        }
        try
        {
            JSON.parseObject(message);
        }
        catch (Exception e)
        {
            throw new ServiceException("消息内容不是合法的 JSON");
        }

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(buildProducerProperties()))
        {
            producer.send(new ProducerRecord<>(whitelistKafkaProperties.getTopic(), null, message)).get();
        }
        catch (Exception e)
        {
            throw new ServiceException("写入Kafka Topic失败: " + e.getMessage()).setDetailMessage(e.getMessage());
        }
    }

    private HandleResult handleRecord(String rawJson)
    {
        JSONObject root = JSON.parseObject(rawJson);
        if (root == null)
        {
            return HandleResult.skipped();
        }
        JSONArray results = root.getJSONArray("vehicleAlarmResult");
        if (results == null || results.isEmpty())
        {
            return HandleResult.skipped();
        }
        int inserted = 0;
        for (int i = 0; i < results.size(); i++)
        {
            JSONObject item = results.getJSONObject(i);
            WhitelistFilterData entity = buildEntity(root, item, rawJson);
            if (entity == null)
            {
                continue;
            }
            inserted += insertWhitelistFilterData(entity);
        }
        return new HandleResult(results.size(), inserted, Math.max(0, results.size() - inserted));
    }

    private static class HandleResult
    {
        private final int parsedMessages;
        private final int insertedRows;
        private final int skippedMessages;

        private HandleResult(int parsedMessages, int insertedRows, int skippedMessages)
        {
            this.parsedMessages = parsedMessages;
            this.insertedRows = insertedRows;
            this.skippedMessages = skippedMessages;
        }

        private static HandleResult skipped()
        {
            return new HandleResult(0, 0, 1);
        }
    }

    private WhitelistFilterData buildEntity(JSONObject root, JSONObject item, String rawJson)
    {
        JSONObject targetAttrs = item.getJSONObject("targetAttrs");
        JSONObject vehicle = getVehicleObject(item);
        WhitelistFilterData entity = new WhitelistFilterData();
        entity.setSendTime(parseDate(root.getString("sendTime")));
        entity.setRecvTime(parseDate(root.getString("recvTime")));
        entity.setChannelId(root.getInteger("channelID"));
        entity.setChannelName(root.getString("channelName"));
        entity.setDataType(root.getString("dataType"));
        entity.setEventType(root.getString("eventType"));
        entity.setEventDescription(root.getString("eventDescription"));
        entity.setIpAddress(root.getString("ipAddress"));
        entity.setPortNo(root.getInteger("portNo"));
        entity.setPlateNo(getNestedValue(vehicle, "plateNo"));
        entity.setPlateColor(getNestedValue(vehicle, "plateColor"));
        entity.setVehicleType(getNestedValue(vehicle, "vehicleType"));
        entity.setAlarmType(targetAttrs == null ? null : targetAttrs.getString("alarmType"));
        entity.setCameraName(targetAttrs == null ? null : targetAttrs.getString("cameraName"));
        entity.setCameraAddress(targetAttrs == null ? null : targetAttrs.getString("cameraAddress"));
        entity.setDeviceName(targetAttrs == null ? null : targetAttrs.getString("deviceName"));
        entity.setDirectionIndex(targetAttrs == null ? null : targetAttrs.getString("directionIndex"));
        entity.setCrossingId(targetAttrs == null ? null : targetAttrs.getLong("crossingId"));
        entity.setLaneNo(targetAttrs == null ? null : targetAttrs.getInteger("laneNo"));
        entity.setPassId(targetAttrs == null ? null : targetAttrs.getString("passID"));
        entity.setPassTime(parseDate(targetAttrs == null ? null : targetAttrs.getString("passTime")));
        entity.setEventUuid(targetAttrs == null ? null : targetAttrs.getString("uuid"));
        entity.setTaskId(item.getString("taskID"));
        entity.setTargetPicUrl(item.getString("targetPicUrl"));
        entity.setRawJson(rawJson);
        return entity;
    }

    private JSONObject getVehicleObject(JSONObject item)
    {
        JSONArray targets = item.getJSONArray("target");
        if (targets == null || targets.isEmpty())
        {
            return null;
        }
        JSONObject firstTarget = targets.getJSONObject(0);
        return firstTarget == null ? null : firstTarget.getJSONObject("vehicle");
    }

    private String getNestedValue(JSONObject parent, String key)
    {
        if (parent == null)
        {
            return null;
        }
        JSONObject nested = parent.getJSONObject(key);
        if (nested == null)
        {
            return null;
        }
        Object value = nested.get("value");
        return value == null ? null : String.valueOf(value);
    }

    private Date parseDate(String value)
    {
        if (StringUtils.isEmpty(value))
        {
            return null;
        }
        return Date.from(OffsetDateTime.parse(value).toInstant());
    }

    private Properties buildConsumerProperties()
    {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, whitelistKafkaProperties.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, whitelistKafkaProperties.getGroupId());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, whitelistKafkaProperties.getAutoOffsetReset());
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, whitelistKafkaProperties.getMaxPollRecords());
        // Old Kafka brokers may not support ApiVersions/ListOffsets v1+, so force legacy fallback mode.
        props.put("api.version.request", "false");
        props.put("broker.version.fallback", "0.9.0.0");
        if (StringUtils.isNotEmpty(whitelistKafkaProperties.getSecurityProtocol()))
        {
            props.put("security.protocol", whitelistKafkaProperties.getSecurityProtocol());
        }
        if (StringUtils.isNotEmpty(whitelistKafkaProperties.getSaslMechanism()))
        {
            props.put("sasl.mechanism", whitelistKafkaProperties.getSaslMechanism());
        }
        if (StringUtils.isNotEmpty(whitelistKafkaProperties.getSaslJaasConfig()))
        {
            props.put("sasl.jaas.config", whitelistKafkaProperties.getSaslJaasConfig());
        }
        return props;
    }

    private Properties buildProducerProperties()
    {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, whitelistKafkaProperties.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        props.put(ProducerConfig.RETRIES_CONFIG, 0);
        props.put("api.version.request", "false");
        props.put("broker.version.fallback", "0.9.0.0");
        if (StringUtils.isNotEmpty(whitelistKafkaProperties.getSecurityProtocol()))
        {
            props.put("security.protocol", whitelistKafkaProperties.getSecurityProtocol());
        }
        if (StringUtils.isNotEmpty(whitelistKafkaProperties.getSaslMechanism()))
        {
            props.put("sasl.mechanism", whitelistKafkaProperties.getSaslMechanism());
        }
        if (StringUtils.isNotEmpty(whitelistKafkaProperties.getSaslJaasConfig()))
        {
            props.put("sasl.jaas.config", whitelistKafkaProperties.getSaslJaasConfig());
        }
        return props;
    }

    private void validateKafkaConfig()
    {
        if (StringUtils.isEmpty(whitelistKafkaProperties.getBootstrapServers())
                || StringUtils.isEmpty(whitelistKafkaProperties.getTopic())
                || StringUtils.isEmpty(whitelistKafkaProperties.getGroupId()))
        {
            throw new ServiceException("Kafka配置不完整，请检查 whitelist.kafka 配置");
        }
    }

    private void applyPlateDataScope(WhitelistFilterData whitelistFilterData)
    {
        if (SecurityUtils.isAdmin())
        {
            return;
        }
        List<String> plateScopeList = whitelistPlateService.selectAuthorizedVehiclePlates();
        if (plateScopeList == null || plateScopeList.isEmpty())
        {
            whitelistFilterData.getParams().put("plateScopeEmpty", true);
            whitelistFilterData.getParams().remove("plateScopeList");
            return;
        }
        whitelistFilterData.getParams().put("plateScopeList", plateScopeList);
        whitelistFilterData.getParams().remove("plateScopeEmpty");
    }

    private void validateFilterDataAccess(WhitelistFilterData whitelistFilterData)
    {
        if (SecurityUtils.isAdmin())
        {
            return;
        }
        List<String> plateScopeList = whitelistPlateService.selectAuthorizedVehiclePlates();
        if (plateScopeList == null || !plateScopeList.contains(whitelistFilterData.getPlateNo()))
        {
            throw new ServiceException("无权查看该过滤数据");
        }
    }
}
