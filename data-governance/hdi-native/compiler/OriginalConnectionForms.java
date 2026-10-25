import com.alibaba.fastjson.JSON;
import com.hikvision.idatafusion.datasource.core.AbstractDatasource;
import com.hikvision.idatafusion.datasource.core.DatasourceFormProcessor;
import com.hikvision.idatafusion.datasource.plugin.FtpDatasource;
import com.hikvision.idatafusion.datasource.plugin.Kafka2Datasource;
import com.hikvision.idatafusion.datasource.plugin.RabbitmqDataSource;
import com.hikvision.idatafusion.datasource.plugin.jdbc.MysqlDatasource;
import com.hikvision.idatafusion.datasource.plugin.jdbc.PostgresSqlDatasource;
import com.hikvision.idatafusion.datasource.core.utils.DatabaseUtils;
import com.hikvision.idatafusion.detl.model.EtlDatasource;
import com.hikvision.idatafusion.detl.dto.globalconfig.DatabaseTableColumnInfoRequest;
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
        "databaseUsername", "address", "ext1", "ext2", "ext3", "certification", "authentication", "Host", "Port", "FolderPath", "ConnectMode", "Ssl");
    private static final Set<String> UNCONFIGURED_KERBEROS = Set.of("KerberosId", "ServerName", "DomainName", "JaasConfig");

    public static Map<String, Object> handle(Map<String, Object> request) {
        if ("connectionMetadata".equals(request.get("operation")))
            return "Kafka".equals(request.get("datasourceType")) ? metadata(request) : additionalMetadata(request);
        if (!Set.of("connectionForm", "connectionUrl").contains(request.get("operation"))
                || !Set.of("operation", "datasourceType", "datasourceVersion", "parameters").containsAll(request.keySet()))
            throw new IllegalArgumentException("Unsupported connection description");
        String type = String.valueOf(request.get("datasourceType"));
        AbstractDatasource original;
        switch (type) {
            case "Postgresql": case "PostgreSQL": original = new PostgresSqlDatasource(); break;
            case "MySQL": original = new MysqlDatasource(); break;
            case "FTP": original = new FtpDatasource(); break;
            case "Kafka": original = new Kafka2Datasource(); break;
            case "RabbitMQ": original = new RabbitmqDataSource(); break;
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

    private static String string(Map<String,Object> values, String key) {
        Object value = values.get(key);
        if (value == null) return "";
        if (!(value instanceof String) || ((String)value).length() > 4096 || ((String)value).indexOf('\0') >= 0)
            throw new IllegalArgumentException("Invalid datasource value");
        return (String)value;
    }

    /** Original MySQL driver/schema utilities, RabbitMQ test and SFTP test; no ETL task or broker writes. */
    @SuppressWarnings("unchecked")
    private static Map<String,Object> additionalMetadata(Map<String,Object> request) {
        String type = String.valueOf(request.get("datasourceType"));
        if (!Set.of("operation", "datasourceType", "parameters").equals(request.keySet())
                || !Set.of("MySQL", "RabbitMQ", "FTP").contains(type) || !(request.get("parameters") instanceof Map))
            throw new IllegalArgumentException("Invalid datasource metadata request");
        Map<String,Object> parameters = (Map<String,Object>)request.get("parameters");
        if (!Set.of("action", "datasource", "query", "allowedEndpoints").equals(parameters.keySet())
                || !(parameters.get("datasource") instanceof Map) || !(parameters.get("query") instanceof Map)
                || !(parameters.get("allowedEndpoints") instanceof List)) throw new IllegalArgumentException("Invalid metadata scope");
        String action = String.valueOf(parameters.get("action"));
        if (!Set.of("test", "schemas", "tables", "columns").contains(action) || !type.equals("MySQL") && !action.equals("test"))
            throw new IllegalArgumentException("Unsupported original metadata action");
        Map<String,Object> data = (Map<String,Object>)parameters.get("datasource"), query = (Map<String,Object>)parameters.get("query");
        int dbType = type.equals("MySQL") ? 2 : type.equals("RabbitMQ") ? 120 : 18;
        if (!(data.get("dbType") instanceof Number) || ((Number)data.get("dbType")).intValue() != dbType)
            throw new IllegalArgumentException("Datasource type mismatch");
        Set<String> allowed = new LinkedHashSet<>(); List<?> configured = (List<?>)parameters.get("allowedEndpoints");
        if (configured.isEmpty() || configured.size() > 16) throw new IllegalArgumentException("Metadata endpoint limit");
        for (Object value : configured) if (!allowed.add(endpoint(value))) throw new IllegalArgumentException("Duplicate metadata endpoint");
        String primary = endpoint(string(data,"databaseIp") + ":" + string(data,"databasePort"));
        if (!allowed.contains(primary)) throw new IllegalArgumentException("Datasource outside endpoint policy");
        String database = string(data,"databaseSid"), url = string(data,"dbUrl");
        if (type.equals("MySQL")) {
            if (!database.matches("[A-Za-z_][A-Za-z0-9_$]{0,127}")) throw new IllegalArgumentException("Explicit database required");
            String expected = "jdbc:mysql://" + primary + "/" + database + "?useSSL=false";
            if (!url.equals(expected) && !(string(data,"connectType").equals("url") && url.equals(expected + "&allowPublicKeyRetrieval=true")))
                throw new IllegalArgumentException("MySQL URL outside supported original options");
        } else if (type.equals("RabbitMQ")) {
            if (!string(data,"ext1").equals("0")) throw new IllegalArgumentException("RabbitMQ TLS is not configured");
            String[] addresses = string(data,"address").split(",",-1); Set<String> seen = new LinkedHashSet<>();
            for (String address : addresses) if (!allowed.contains(endpoint(address)) || !seen.add(address)) throw new IllegalArgumentException("RabbitMQ address outside policy");
            if (addresses.length == 0 || !addresses[0].equals(primary) || !url.equals("rabbitmq://" + String.join(",",addresses))) throw new IllegalArgumentException("RabbitMQ URL mismatch");
        } else if (!string(data,"ext2").equals("sftp") || !url.equals("ftp://" + primary + (database.isEmpty() ? "" : "/" + database))) {
            throw new IllegalArgumentException("SFTP protocol or original URL mismatch");
        }
        Map<String,Object> response = new LinkedHashMap<>(), observed = new LinkedHashMap<>();
        response.put("ok",true); response.put("protocolVersion",1); response.put("operation","connectionMetadata");
        response.put("datasourceType",type); response.put("action",action); response.put("diagnostics",List.of()); response.put("metadata",observed);
        observed.put("available",false);
        if (!Set.of(11,17).contains(Runtime.version().feature()) || System.getSecurityManager() != null) { observed.put("reason","METADATA_GUARD_UNAVAILABLE"); return response; }
        MetadataNetworkGuard guard = new MetadataNetworkGuard(allowed);
        try { System.setSecurityManager(guard); } catch (RuntimeException failure) { observed.put("reason","METADATA_GUARD_UNAVAILABLE"); return response; }
        if (System.getSecurityManager() != guard) throw new SecurityException("Metadata guard not installed");
        try {
            Map<String,String> values = new LinkedHashMap<>();
            values.put("databaseUsername",string(data,"databaseUsername")); values.put("databasePassword",""); values.put("dbUrl",url);
            AbstractDatasource original;
            if (type.equals("MySQL")) {
                original = new MysqlDatasource(); values.put("connectType","url"); values.put("address",primary); values.put("databaseSid",database);
            } else if (type.equals("RabbitMQ")) {
                original = new RabbitmqDataSource(); values.put("address",string(data,"address")); values.put("Ssl","1");
            } else {
                original = new FtpDatasource(); values.put("Host",string(data,"databaseIp")); values.put("Port",string(data,"databasePort"));
                values.put("FolderPath",database); values.put("ConnectMode",string(data,"ext1")); values.put("ext2","sftp");
            }
            DatasourceFormProcessor.inject(original,values);
            // The platform has already decrypted its own RSA/AES credential boundary. Do not ask
            // the retired management encryption service to decrypt that plaintext a second time.
            original.setDatabasePassword(string(data,"databasePassword"));
            if (type.equals("RabbitMQ")) {
                // The original management setter splits address into the client's host/port fields;
                // annotation injection alone only fills the address field.
                var setter = RabbitmqDataSource.class.getDeclaredMethod("setAddress",String.class);
                setter.setAccessible(true); setter.invoke(original,string(data,"address"));
            }
            if (action.equals("test")) original.test();
            else {
                MysqlDatasource mysql = (MysqlDatasource)original; java.sql.Driver driver = mysql.getDriver();
                if (driver == null) throw new IllegalStateException("Original MySQL driver unavailable");
                Properties credentials = new Properties(); credentials.put("user",string(data,"databaseUsername")); credentials.put("password",string(data,"databasePassword"));
                credentials.put("connectTimeout","3000"); credentials.put("socketTimeout","8000");
                try (java.sql.Connection connection = driver.connect(url,credentials)) {
                    connection.setReadOnly(true); connection.setAutoCommit(false);
                    String catalog = connection.getCatalog(); if (!database.equals(catalog)) throw new IllegalStateException("MySQL catalog mismatch");
                    observed.put("catalog",catalog);
                    if (action.equals("schemas")) observed.put("schemas",List.of(catalog));
                    else if (action.equals("tables")) {
                        EtlDatasource source = JSON.parseObject(JSON.toJSONString(data),EtlDatasource.class);
                        source.setSchemaName(null); var tables = DatabaseUtils.getDatabaseTablesFromConnection(source,connection,"%");
                        if (tables.size() > 1000) throw new IllegalStateException("Table metadata limit");
                        observed.put("tables",JSON.parseArray(JSON.toJSONString(tables)));
                    } else {
                        String name = string(query,"tableName"); if (!name.matches("[A-Za-z_][A-Za-z0-9_$]{0,127}")) throw new IllegalArgumentException("Explicit table name required");
                        DatabaseTableColumnInfoRequest columns = new DatabaseTableColumnInfoRequest(); columns.setTableCat(catalog); columns.setTableSchem(null); columns.setTableName(name);
                        var info = DatabaseUtils.getDatabaseColumnsFromConnection(connection,columns);
                        if (info.getColumns() == null || info.getColumns().size() > 512) throw new IllegalStateException("Column metadata limit");
                        // Preserve the original TableManage service's display projection after its JDBC helper.
                        for (var column : info.getColumns()) if (column.getTypeName() != null) column.setTypeName(column.getTypeName().toLowerCase(java.util.Locale.ROOT));
                        observed.put("columns",JSON.parseArray(JSON.toJSONString(info.getColumns())));
                        observed.put("primaryKeys",info.getPrimaryKeys() == null ? List.of() : JSON.parseArray(JSON.toJSONString(info.getPrimaryKeys())));
                        observed.put("targetTableName",name);
                    }
                    connection.rollback();
                }
            }
            if (guard.denied.get()) throw new SecurityException("Metadata attempted an unapproved endpoint");
            observed.put("available",true);
        } catch (Throwable failure) {
            // Class/locations only: original client exception messages can contain connection values.
            Throwable cause=failure;
            for(int depth=0;cause!=null && depth<3;depth++,cause=cause.getCause()) {
                System.err.println("ORIGINAL_METADATA_FAILURE " + cause.getClass().getName());
                for(StackTraceElement frame : java.util.Arrays.asList(cause.getStackTrace()).subList(0,Math.min(6,cause.getStackTrace().length))) System.err.println("  at " + frame);
            }
            observed.put("reason",guard.denied.get() ? "METADATA_ENDPOINT_DENIED" : "METADATA_UNAVAILABLE");
        }
        if (guard.denied.get()) { observed.clear(); observed.put("available",false); observed.put("reason","METADATA_ENDPOINT_DENIED"); }
        observed.put("guardActive",System.getSecurityManager() == guard); return response;
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
