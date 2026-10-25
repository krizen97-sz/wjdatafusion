import com.alibaba.fastjson.JSON;
import com.hikvision.idatafusion.datasource.core.AbstractDatasource;
import com.hikvision.idatafusion.datasource.core.DatasourceFormProcessor;
import com.hikvision.idatafusion.datasource.plugin.FtpDatasource;
import com.hikvision.idatafusion.datasource.plugin.Kafka2Datasource;
import com.hikvision.idatafusion.datasource.plugin.jdbc.PostgresSqlDatasource;
import com.hikvision.idatafusion.detl.mapper.EtlKerberosDao;
import com.hikvision.idatafusion.detl.utils.SpringBootEnvUtils;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.context.support.StaticApplicationContext;

/** Original connection form annotations and URL builder; never opens a connection. */
public final class OriginalConnectionForms {
    private static final Set<String> FIELDS = Set.of("connectType", "dbUrl", "databaseIp", "databasePort", "databaseSid",
        "databaseUsername", "address", "ext1", "ext2", "ext3", "certification", "authentication", "Host", "Port", "FolderPath", "ConnectMode");
    private static final Set<String> UNCONFIGURED_KERBEROS = Set.of("KerberosId", "ServerName", "DomainName", "JaasConfig");

    public static Map<String, Object> handle(Map<String, Object> request) {
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
}
