package com.hm.manage.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.stereotype.Component;
import com.alibaba.fastjson2.JSON;
import com.hm.common.exception.ServiceException;
import com.hm.common.utils.StringUtils;

@Component
class AutoInspectionMqttMonitor
{
    private final Map<String, ListenerContext> listeners = new ConcurrentHashMap<>();
    private final String instanceSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6);

    Snapshot observe(Map<String, Object> target, int timeoutSeconds)
    {
        MqttSettings settings = MqttSettings.from(target, timeoutSeconds);
        String targetKey = settings.targetId == null ? "temp:" + settings.fingerprint : "target:" + settings.targetId;
        if (settings.targetId == null)
        {
            ListenerContext temporary = new ListenerContext(settings, instanceSuffix);
            try
            {
                temporary.connect();
                return temporary.snapshot();
            }
            finally
            {
                temporary.close();
            }
        }

        ListenerContext context = listeners.get(targetKey);
        if (context == null || !context.matches(settings))
        {
            synchronized (listeners)
            {
                context = listeners.get(targetKey);
                if (context == null || !context.matches(settings))
                {
                    if (context != null)
                    {
                        context.close();
                    }
                    context = new ListenerContext(settings, instanceSuffix);
                    context.connect();
                    listeners.put(targetKey, context);
                }
            }
        }
        else
        {
            context.ensureConnected();
        }
        return context.snapshot();
    }

    void evict(Long targetId)
    {
        if (targetId == null)
        {
            return;
        }
        ListenerContext removed = listeners.remove("target:" + targetId);
        if (removed != null)
        {
            removed.close();
        }
    }

    @PreDestroy
    void closeAll()
    {
        listeners.values().forEach(ListenerContext::close);
        listeners.clear();
    }

    static final class Snapshot
    {
        final boolean connected;
        final long messageCount;
        final LocalDateTime startedAt;
        final LocalDateTime lastMessageAt;
        final String brokerUri;
        final String topicFilter;
        final int qos;
        final String lastError;

        Snapshot(boolean connected, long messageCount, LocalDateTime startedAt,
                 LocalDateTime lastMessageAt, String brokerUri, String topicFilter,
                 int qos, String lastError)
        {
            this.connected = connected;
            this.messageCount = messageCount;
            this.startedAt = startedAt;
            this.lastMessageAt = lastMessageAt;
            this.brokerUri = brokerUri;
            this.topicFilter = topicFilter;
            this.qos = qos;
            this.lastError = lastError;
        }
    }

    private static final class ListenerContext implements MqttCallbackExtended
    {
        private final MqttSettings settings;
        private final MqttClient client;
        private final AtomicLong messageCount = new AtomicLong();
        private final LocalDateTime startedAt = LocalDateTime.now();
        private volatile LocalDateTime lastMessageAt;
        private volatile String lastError = "";

        private ListenerContext(MqttSettings settings, String instanceSuffix)
        {
            this.settings = settings;
            try
            {
                String clientId = settings.clientId;
                if (StringUtils.isBlank(clientId))
                {
                    clientId = "ryi-" + instanceSuffix + "-" + settings.fingerprint.substring(0, 8);
                }
                client = new MqttClient(settings.brokerUri, clientId, new MemoryPersistence());
                client.setCallback(this);
            }
            catch (MqttException e)
            {
                throw new ServiceException("MQTT客户端初始化失败：" + e.getMessage());
            }
        }

        private boolean matches(MqttSettings value)
        {
            return settings.fingerprint.equals(value.fingerprint);
        }

        private synchronized void connect()
        {
            if (client.isConnected())
            {
                return;
            }
            try
            {
                MqttConnectOptions options = new MqttConnectOptions();
                options.setAutomaticReconnect(true);
                options.setCleanSession(true);
                options.setConnectionTimeout(settings.timeoutSeconds);
                options.setKeepAliveInterval(Math.max(15, Math.min(120, settings.keepAliveSeconds)));
                if (StringUtils.isNotBlank(settings.username))
                {
                    options.setUserName(settings.username);
                }
                if (StringUtils.isNotBlank(settings.password))
                {
                    options.setPassword(settings.password.toCharArray());
                }
                client.connect(options);
                subscribe();
                lastError = "";
            }
            catch (MqttException e)
            {
                lastError = e.getMessage();
                throw new ServiceException("MQTT连接或订阅失败：" + e.getMessage());
            }
        }

        private void ensureConnected()
        {
            if (!client.isConnected())
            {
                connect();
            }
        }

        private void subscribe() throws MqttException
        {
            client.subscribe(settings.topicFilter, settings.qos);
        }

        private Snapshot snapshot()
        {
            return new Snapshot(client.isConnected(), messageCount.get(), startedAt, lastMessageAt,
                    settings.brokerUri, settings.topicFilter, settings.qos, lastError);
        }

        @Override
        public void connectComplete(boolean reconnect, String serverURI)
        {
            if (reconnect)
            {
                try
                {
                    subscribe();
                    lastError = "";
                }
                catch (MqttException e)
                {
                    lastError = e.getMessage();
                }
            }
        }

        @Override
        public void connectionLost(Throwable cause)
        {
            lastError = cause == null ? "MQTT连接中断" : StringUtils.defaultIfBlank(cause.getMessage(), "MQTT连接中断");
        }

        @Override
        public void messageArrived(String topic, MqttMessage message)
        {
            if (settings.ignoreRetained && message != null && message.isRetained())
            {
                return;
            }
            messageCount.incrementAndGet();
            lastMessageAt = LocalDateTime.now();
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token)
        {
            // This client only subscribes and never publishes.
        }

        private void close()
        {
            try
            {
                if (client.isConnected())
                {
                    client.disconnect(1000);
                }
            }
            catch (Exception ignored)
            {
                // Best-effort shutdown.
            }
            try
            {
                client.close();
            }
            catch (Exception ignored)
            {
                // Best-effort shutdown.
            }
        }
    }

    private static final class MqttSettings
    {
        private final Long targetId;
        private final String brokerUri;
        private final String topicFilter;
        private final String username;
        private final String password;
        private final String clientId;
        private final int qos;
        private final int keepAliveSeconds;
        private final int timeoutSeconds;
        private final boolean ignoreRetained;
        private final String fingerprint;

        private MqttSettings(Long targetId, String brokerUri, String topicFilter, String username,
                             String password, String clientId, int qos, int keepAliveSeconds,
                             int timeoutSeconds, boolean ignoreRetained)
        {
            this.targetId = targetId;
            this.brokerUri = brokerUri;
            this.topicFilter = topicFilter;
            this.username = username;
            this.password = password;
            this.clientId = clientId;
            this.qos = qos;
            this.keepAliveSeconds = keepAliveSeconds;
            this.timeoutSeconds = timeoutSeconds;
            this.ignoreRetained = ignoreRetained;
            this.fingerprint = digest(String.join("|", brokerUri, topicFilter, username, password,
                    clientId, String.valueOf(qos), String.valueOf(ignoreRetained)));
        }

        @SuppressWarnings("unchecked")
        private static MqttSettings from(Map<String, Object> target, int timeoutSeconds)
        {
            String host = text(target.get("host"));
            if (StringUtils.isBlank(host))
            {
                throw new ServiceException("MQTT Broker地址不能为空");
            }
            int port = integer(target.get("port"), 1883, 1, 65535);
            Map<String, Object> options;
            try
            {
                Object raw = target.get("extraParams");
                options = raw instanceof Map<?, ?> ? (Map<String, Object>) raw
                        : JSON.parseObject(StringUtils.defaultIfBlank(text(raw), "{}"), Map.class);
            }
            catch (Exception ignored)
            {
                options = Map.of();
            }
            String protocol = StringUtils.defaultIfBlank(text(options.get("protocol")), "tcp").toLowerCase();
            String brokerUri = buildBrokerUri(host, port, protocol);
            String topic = StringUtils.defaultIfBlank(text(target.get("topic")), text(options.get("topicFilter")));
            if (StringUtils.isBlank(topic))
            {
                throw new ServiceException("MQTT Topic Filter不能为空");
            }
            return new MqttSettings(longValue(target.get("targetId")), brokerUri, topic,
                    text(target.get("username")), text(target.get("password")), text(options.get("clientId")),
                    integer(options.get("qos"), 1, 0, 2), integer(options.get("keepAliveSeconds"), 30, 15, 300),
                    Math.max(3, Math.min(120, timeoutSeconds)),
                    !"false".equalsIgnoreCase(text(options.get("ignoreRetained"))));
        }

        private static String buildBrokerUri(String host, int port, String protocol)
        {
            String value = host.trim();
            if (value.startsWith("mqtt://")) value = "tcp://" + value.substring(7);
            if (value.startsWith("mqtts://")) value = "ssl://" + value.substring(8);
            if (value.contains("://")) return value.matches(".*://[^/]+:[0-9]+.*") ? value : value + ":" + port;
            String scheme = "ssl".equals(protocol) ? "ssl" : "tcp";
            return scheme + "://" + value + ":" + port;
        }

        private static String digest(String value)
        {
            try
            {
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(value.getBytes(StandardCharsets.UTF_8)));
            }
            catch (Exception e)
            {
                return Integer.toHexString(Objects.hash(value));
            }
        }

        private static int integer(Object value, int fallback, int min, int max)
        {
            try
            {
                return Math.max(min, Math.min(max, Integer.parseInt(text(value))));
            }
            catch (Exception ignored)
            {
                return fallback;
            }
        }

        private static Long longValue(Object value)
        {
            try
            {
                return value == null ? null : Long.valueOf(value.toString());
            }
            catch (Exception ignored)
            {
                return null;
            }
        }

        private static String text(Object value)
        {
            return value == null ? "" : value.toString().trim();
        }
    }
}
