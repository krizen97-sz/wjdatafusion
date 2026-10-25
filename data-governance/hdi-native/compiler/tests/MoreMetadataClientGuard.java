import com.hikvision.idatafusion.datasource.core.AbstractDatasource;
import com.hikvision.idatafusion.datasource.core.DatasourceFormProcessor;
import com.hikvision.idatafusion.datasource.plugin.FtpDatasource;
import com.hikvision.idatafusion.datasource.plugin.RabbitmqDataSource;
import com.hikvision.idatafusion.datasource.plugin.jdbc.MysqlDatasource;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Exercises the production guard against actual original clients; never installs it in the test runner. */
public final class MoreMetadataClientGuard {
    public static void main(String[] args) throws Exception {
        String type = args[0], target = "127.0.0.1:" + args[2];
        var guard = new OriginalConnectionForms.MetadataNetworkGuard(Set.of("127.0.0.1:" + args[1]));
        System.setSecurityManager(guard);
        Map<String,String> values = new LinkedHashMap<>();
        values.put("databaseUsername", "no-real-account"); values.put("databasePassword", "");
        AbstractDatasource original;
        if (type.equals("MySQL")) {
            original = new MysqlDatasource(); values.put("connectType", "url");
            values.put("address", target); values.put("databaseSid", "guard_probe");
            values.put("dbUrl", "jdbc:mysql://" + target + "/guard_probe?useSSL=false");
        } else if (type.equals("RabbitMQ")) {
            original = new RabbitmqDataSource(); values.put("address", target); values.put("Ssl", "1");
            values.put("dbUrl", "rabbitmq://" + target);
        } else if (type.equals("SFTP")) {
            original = new FtpDatasource(); values.put("Host", "127.0.0.1"); values.put("Port", args[2]);
            values.put("FolderPath", "/guard_probe"); values.put("ConnectMode", "0"); values.put("ext2", "sftp");
            values.put("dbUrl", "ftp://" + target + "//guard_probe");
        } else throw new IllegalArgumentException("Unknown test client");
        DatasourceFormProcessor.inject(original, values);
        original.setDatabasePassword("probe-secret-not-a-real-credential");
        if (type.equals("RabbitMQ")) {
            var setter = RabbitmqDataSource.class.getDeclaredMethod("setAddress", String.class);
            setter.setAccessible(true); setter.invoke(original, target);
        }
        try { original.test(); } catch (Throwable expected) { /* Guard outcome is checked independently. */ }
        if (!guard.denied.get() || System.getSecurityManager() != guard)
            throw new AssertionError("Original client did not exercise the immutable endpoint guard");
        System.out.println("ORIGINAL_CLIENT_ENDPOINT_DENIED " + type);
    }
}
