import com.alibaba.fastjson.JSON;
import com.hikvision.idatafusion.datasource.core.AbstractDatasource;
import com.hikvision.idatafusion.datasource.core.DatasourceFormProcessor;
import com.hikvision.idatafusion.datasource.plugin.FtpDatasource;
import com.hikvision.idatafusion.datasource.plugin.Kafka2Datasource;
import com.hikvision.idatafusion.datasource.plugin.jdbc.PostgresSqlDatasource;
import com.hikvision.idatafusion.detl.mapper.EtlKerberosDao;
import com.hikvision.idatafusion.detl.utils.SpringBootEnvUtils;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.SocketPermission;
import java.security.Permission;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.context.support.StaticApplicationContext;

/** Original forms, URL generation and guarded read-only metadata in a one-shot JVM. */
public final class OriginalConnectionForms {
    private static final Set<String> FIELDS = Set.of("connectType", "dbUrl", "databaseIp", "databasePort", "databaseSid",
        "databaseUsername", "address", "ext1", "ext2", "ext3", "certification", "authentication", "Host", "Port", "FolderPath", "ConnectMode");
    private static final Set<String> UNCONFIGURED_KERBEROS = Set.of("KerberosId", "ServerName", "DomainName", "JaasConfig");

    public static Map<String, Object> handle(Map<String, Object> request) {
        if ("connectionMetadata".equals(request.get("operation"))) return metadata(request);
        if (!Set.of("connectionForm", "connectionUrl").contains(request.get("operation"))
                || !Set.of("operation", "datasourceType", "datasourceVersion", "parameters").containsAll(request.keySet()))
            throw new IllegalArgumentException("Unsupported connection description");
        String type = String.valueOf(request.get("datasourceType"));
        AbstractDatasource original;
        switch (type) {
            case "Postgresql": case "PostgreSQL": original = new PostgresSqlDatasource(); break;
            case "FTP": original = new FtpDatasource(); break;
            case "Kafka": original = new Kafka2Datasource(); break;
            default: throw new IllegalArgumentException("Unsupported original connection type");
        }
        String version = request.get("datasourceVersion") == null ? "" : String.valueOf(request.get("datasourceVersion"));
        if (!version.matches("[A-Za-z0-9_.-]{0,64}")) throw new IllegalArgumentException("Invalid datasource version");
        Map<String, String> values = new LinkedHashMap<>();
        Object parameters = request.get("parameters");
        if (parameters != null) {
            if (!(parameters instanceof Map)) throw new IllegalArgumentException("Invalid connection parameters");
            for (Map.Entry<?, ?> entry : ((Map<?, ?>)parameters).entrySet()) {
                String key = String.valueOf(entry.getKey());
                // Passwords are irrelevant to descriptors and URL previews, and must never be echoed.
                if (key.toLowerCase(java.util.Locale.ROOT).contains("password")) continue;
                if (UNCONFIGURED_KERBEROS.contains(key)) continue;
                if (!FIELDS.contains(key) || entry.getValue() != null && !(entry.getValue() instanceof String))
                    throw new IllegalArgumentException("Invalid connection parameter");
                String value = entry.getValue() == null ? "" : String.valueOf(entry.getValue());
                if (value.length() > 4096 || value.indexOf('\0') >= 0) throw new IllegalArgumentException("Invalid connection value");
                values.put(key, value);
            }
        }
        if (!Set.of("", "none").contains(values.getOrDefault("certification", "none"))
                || !Set.of("", "none").contains(values.getOrDefault("authentication", "none")))
            throw new IllegalArgumentException("Connection authentication has not been configured");
        values.put("databasePassword", "");
        original.setVersion(version);
        // Use the original annotation injector: FTP's Host/Port/FolderPath are not EtlDatasource field names.
        if (parameters instanceof Map && !((Map<?, ?>)parameters).isEmpty()) DatasourceFormProcessor.inject(original, values);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", true); response.put("protocolVersion", 1); response.put("operation", request.get("operation"));
        response.put("diagnostics", List.of());
        response.put("datasourceType", type); response.put("dbType", original.getDbType());
        response.put("datasourceVersion", original.getVersion()); response.put("category", original.getCategory());
        if ("connectionForm".equals(request.get("operation"))) {
            // The platform has no registered Kerberos profiles. Supply only that metadata repository;
            // do not initialize the vendor application, licensing, identities or datasource services.
            try (StaticApplicationContext context = new StaticApplicationContext()) {
                Object kerberos = Proxy.newProxyInstance(EtlKerberosDao.class.getClassLoader(), new Class<?>[]{EtlKerberosDao.class}, (proxy, method, args) -> {
                    if (method.getName().equals("find")) return List.of();
                    throw new UnsupportedOperationException("Only configured Kerberos profile listing is available");
                });
                context.getBeanFactory().registerSingleton("platformKerberosProfiles", kerberos);
                new SpringBootEnvUtils().setApplicationContext(context);
                response.put("form", JSON.parseObject(JSON.toJSONString(original.getParameters())));
            }
            response.put("supportTest", original.supportTest()); response.put("showVersion", original.showVersion());
        } else {
            String url = original.jointUrl();
            if (url != null && (url.length() > 8192 || url.indexOf('\0') >= 0 || url.indexOf('@') >= 0))
                throw new IllegalArgumentException("Invalid connection URL");
            response.put("url", url);
        }
        return response;
    }

    /** This guard is installed only inside the short-lived compiler child, never the platform JVM. */
    static final class MetadataNetworkGuard extends SecurityManager {
        private final Set<String> endpoints, addresses;
        final AtomicBoolean denied = new AtomicBoolean();
        MetadataNetworkGuard(Set<String> values) {
            endpoints = Set.copyOf(values); Set<String> hosts = new LinkedHashSet<>();
            for (String value : values) hosts.add(value.substring(0, value.lastIndexOf(':')));
            addresses = Set.copyOf(hosts);
        }
        private void reject() { denied.set(true); throw new SecurityException("Metadata endpoint denied"); }
        @Override public void checkConnect(String host, int port) {
            if (port == -1 ? !addresses.contains(host) : !endpoints.contains(host + ":" + port)) reject();
        }
        @Override public void checkConnect(String host, int port, Object context) { checkConnect(host, port); }
        @Override public void checkListen(int port) { reject(); }
        @Override public void checkAccept(String host, int port) { reject(); }
        @Override public void checkMulticast(InetAddress address) { reject(); }
        @Override public void checkPermission(Permission permission) {
            if (permission instanceof RuntimePermission && Set.of("setSecurityManager", "createSecurityManager").contains(permission.getName()))
                throw new SecurityException("Metadata guard is immutable");
            if (permission instanceof SocketPermission) {
                String action = permission.getActions();
                if (action.contains("listen") || action.contains("accept")) reject();
                String name = permission.getName();
                if (action.contains("connect")) { if (!endpoints.contains(name)) reject(); }
                else if (action.contains("resolve") && !addresses.contains(name)) reject();
            }
        }
        @Override public void checkPermission(Permission permission, Object context) { checkPermission(permission); }
    }

    private static String endpoint(Object raw) {
        if (!(raw instanceof String)) throw new IllegalArgumentException("Literal endpoint required");
        String value = (String)raw;
        if (!value.matches("(?:0|[1-9][0-9]{0,2})(?:\\.(?:0|[1-9][0-9]{0,2})){3}:[1-9][0-9]{0,4}"))
            throw new IllegalArgumentException("Literal endpoint required");
        String[] pair = value.split(":"); String[] octets = pair[0].split("\\.");
        for (String part : octets) if (Integer.parseInt(part) > 255) throw new IllegalArgumentException("Invalid address");
        int first = Integer.parseInt(octets[0]), port = Integer.parseInt(pair[1]);
        if (first == 0 || first >= 224 || pair[0].startsWith("169.254.") || port > 65535 || port >= 2180 && port <= 2183)
            throw new IllegalArgumentException("Endpoint outside policy");
        return value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String,Object> metadata(Map<String,Object> request) {
        if (!Set.of("operation", "datasourceType", "parameters").equals(request.keySet()) || !"Kafka".equals(request.get("datasourceType"))
                || !(request.get("parameters") instanceof Map)) throw new IllegalArgumentException("Invalid metadata request");
        Map<String,Object> parameters = (Map<String,Object>)request.get("parameters");
        if (!Set.of("action", "bootstrapServers", "allowedEndpoints").equals(parameters.keySet())
                || !Set.of("test", "topics").contains(parameters.get("action")) || !(parameters.get("bootstrapServers") instanceof String)
                || !(parameters.get("allowedEndpoints") instanceof List)) throw new IllegalArgumentException("Invalid metadata scope");
        Set<String> allowed = new LinkedHashSet<>(); List<?> configured = (List<?>)parameters.get("allowedEndpoints");
        if (configured.isEmpty() || configured.size() > 16) throw new IllegalArgumentException("Metadata endpoint limit");
        for (Object value : configured) if (!allowed.add(endpoint(value))) throw new IllegalArgumentException("Duplicate endpoint");
        String[] seeds = ((String)parameters.get("bootstrapServers")).split(",", -1);
        if (seeds.length == 0 || seeds.length > 16) throw new IllegalArgumentException("Bootstrap limit");
        Set<String> selected = new LinkedHashSet<>();
        for (String seed : seeds) if (!allowed.contains(endpoint(seed)) || !selected.add(seed)) throw new IllegalArgumentException("Bootstrap outside policy");
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("ok", true); result.put("protocolVersion", 1); result.put("operation", "connectionMetadata");
        result.put("datasourceType", "Kafka"); result.put("action", parameters.get("action"));
        result.put("diagnostics", List.of());
        Map<String,Object> observed = new LinkedHashMap<>(); result.put("metadata", observed);
        observed.put("available", false); observed.put("nodes", List.of()); observed.put("topics", List.of());
        if (!Set.of(11,17).contains(Runtime.version().feature()) || System.getSecurityManager() != null) {
            observed.put("reason", "METADATA_GUARD_UNAVAILABLE"); return result;
        }
        MetadataNetworkGuard guard = new MetadataNetworkGuard(allowed);
        try { System.setSecurityManager(guard); }
        catch (RuntimeException unavailable) { observed.put("reason", "METADATA_GUARD_UNAVAILABLE"); return result; }
        if (System.getSecurityManager() != guard) throw new SecurityException("Guard installation not confirmed");
        // Do not restore the guard: this one-shot process exits immediately after returning its envelope.
        Properties config = new Properties(); config.put("bootstrap.servers", String.join(",", selected));
        config.put("request.timeout.ms", "4000"); config.put("default.api.timeout.ms", "6000"); config.put("retries", "0");
        config.put("client.id", "rynew-guarded-metadata");
        AdminClient client = null;
        try {
            client = AdminClient.create(config);
            List<Map<String,Object>> nodes = new ArrayList<>();
            for (org.apache.kafka.common.Node node : client.describeCluster().nodes().get(6, TimeUnit.SECONDS)) {
                if (!allowed.contains(node.host() + ":" + node.port())) { guard.denied.set(true); throw new SecurityException("Unexpected broker"); }
                nodes.add(Map.of("host", node.host(), "port", node.port(), "id", node.id()));
            }
            if (nodes.isEmpty()) throw new IllegalStateException("No broker metadata");
            List<String> topics = new ArrayList<>();
            if (parameters.get("action").equals("topics")) {
                Set<String> names = client.listTopics().names().get(6, TimeUnit.SECONDS);
                if (names.size() > 1000) throw new IllegalStateException("Topic limit");
                for (String name : names) { if (name.length() > 249) throw new IllegalStateException("Topic name limit"); topics.add(name); }
                java.util.Collections.sort(topics);
            }
            if (guard.denied.get()) throw new SecurityException("Unexpected network attempt");
            observed.put("available", true); observed.put("nodes", nodes); observed.put("topics", topics);
            observed.put("guardActive", System.getSecurityManager() == guard);
        } catch (Exception failure) {
            observed.put("reason", guard.denied.get() ? "METADATA_ENDPOINT_DENIED" : "METADATA_UNAVAILABLE");
        } finally {
            if (client != null) try { client.close(java.time.Duration.ofSeconds(1)); }
            catch (RuntimeException closeFailure) { observed.put("available", false); observed.put("reason", "METADATA_UNAVAILABLE"); }
        }
        if (guard.denied.get()) {
            observed.put("available", false); observed.put("nodes", List.of()); observed.put("topics", List.of());
            observed.put("reason", "METADATA_ENDPOINT_DENIED");
        }
        observed.put("guardActive", System.getSecurityManager() == guard);
        return result;
    }
}
