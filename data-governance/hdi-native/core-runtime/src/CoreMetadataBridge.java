import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.ParserConfig;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.api.ACLProvider;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Stat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Scoped protocol client, not an ETL engine. Original Core consumes all graph bytes. */
public final class CoreMetadataBridge {
    private final CuratorFramework root;
    private final CuratorFramework client;
    private final JSONObject config;
    private final String instanceId;
    private final String flowId;
    private final String workspaceId;

    private static String id(JSONObject object, String key, long maximum) {
        String value = object.getString(key);
        if (value == null || !value.matches("[1-9][0-9]{0,18}")
                || Long.parseLong(value) > maximum) throw new IllegalArgumentException("INVALID_ID");
        return value;
    }

    private CoreMetadataBridge(JSONObject config) throws Exception {
        this.config = config;
        instanceId = id(config, "instanceId", Long.MAX_VALUE);
        flowId = id(config, "flowId", Long.MAX_VALUE);
        workspaceId = id(config, "workspaceId", Integer.MAX_VALUE);
        String namespace = config.getString("namespace");
        if (namespace == null || !namespace.matches("rynew-core-[a-z0-9-]{8,100}"))
            throw new IllegalArgumentException("INVALID_NAMESPACE");
        String endpoint = config.getString("zookeeper");
        if (endpoint == null || !endpoint.matches("127\\.0\\.0\\.1:[0-9]{2,5}"))
            throw new IllegalArgumentException("LOCAL_COORDINATOR_REQUIRED");
        String authorization = config.getString("zkUser") + ":" + config.getString("zkPassword");
        ACLProvider acl = new ACLProvider() {
            public List<ACL> getDefaultAcl() { return ZooDefs.Ids.CREATOR_ALL_ACL; }
            public List<ACL> getAclForPath(String path) { return getDefaultAcl(); }
        };
        root = CuratorFrameworkFactory.builder().connectString(endpoint)
                .connectionTimeoutMs(5000).sessionTimeoutMs(10000)
                .retryPolicy(new ExponentialBackoffRetry(200, 2))
                .authorization("digest", authorization.getBytes(StandardCharsets.UTF_8))
                .aclProvider(acl).build();
        root.start();
        if (!root.blockUntilConnected(8, TimeUnit.SECONDS)) {
            root.close();
            throw new IllegalStateException("COORDINATOR_UNAVAILABLE");
        }
        client = root.usingNamespace(namespace);
    }

    private void create(String path, byte[] data) throws Exception {
        if (client.checkExists().forPath(path) != null) throw new IllegalStateException("NODE_ALREADY_EXISTS");
        client.create().creatingParentsIfNeeded().forPath(path, data);
    }

    private static byte[] nativeBytes(JSONObject request, String key) throws Exception {
        JSONObject envelope = request.getJSONObject(key);
        if (envelope == null) throw new IllegalArgumentException("MISSING_NATIVE_BYTES");
        byte[] bytes = Base64.getDecoder().decode(envelope.getString("base64"));
        if (bytes.length == 0 || bytes.length > 4 * 1024 * 1024)
            throw new IllegalArgumentException("NATIVE_BYTES_LIMIT");
        StringBuilder sha = new StringBuilder();
        for (byte part : MessageDigest.getInstance("SHA-256").digest(bytes))
            sha.append(String.format("%02x", part & 255));
        if (!sha.toString().equals(envelope.getString("sha256")))
            throw new IllegalArgumentException("NATIVE_HASH_MISMATCH");
        return bytes;
    }

    private String read(String path) throws Exception {
        try {
            return new String(client.getData().forPath(path), StandardCharsets.UTF_8);
        } catch (KeeperException.NoNodeException absent) {
            return null;
        }
    }

    private Object action(JSONObject request) throws Exception {
        switch (request.getString("op")) {
            case "seed": {
                JSONObject instance = request.getJSONObject("instance");
                if (instance == null || !instanceId.equals(instance.getString("id"))
                        || !workspaceId.equals(instance.getString("group_id")))
                    throw new IllegalArgumentException("INSTANCE_ID_MISMATCH");
                byte[] flowBytes = nativeBytes(request, "flowjson");
                JSONObject flow = JSON.parseObject(new String(flowBytes, StandardCharsets.UTF_8)).getJSONObject("flow");
                if (flow == null || !flowId.equals(flow.getString("id"))
                        || !workspaceId.equals(flow.getString("group_id")))
                    throw new IllegalArgumentException("FLOW_ID_MISMATCH");
                String namespace = config.getString("namespace");
                if (root.checkExists().forPath("/" + namespace) != null)
                    throw new IllegalStateException("NAMESPACE_ALREADY_EXISTS");
                // Exclusive create is the ownership barrier; never adopt another run's namespace.
                root.create().forPath("/" + namespace,
                        JSON.toJSONBytes(Map.of("ownerId", config.getString("ownerId"),
                                "execId", config.getString("execId"))));
                create("/metadata/instance/" + instanceId, JSON.toJSONBytes(instance));
                create("/metadata/group/" + workspaceId,
                        JSON.toJSONBytes(Map.of("id", Integer.parseInt(workspaceId))));
                create("/metadata/group/" + workspaceId + "/flowjson/" + flowId,
                        flowBytes);
                return Map.of("seeded", true);
            }
            case "submit": {
                byte[] bytes = nativeBytes(request, "schedule");
                JSONObject nativeSchedule = JSON.parseObject(new String(bytes, StandardCharsets.UTF_8));
                JSONObject strategy = nativeSchedule.getJSONObject("strategy_flow");
                if (!flowId.equals(nativeSchedule.getString("id"))
                        || !config.getString("execId").equals(nativeSchedule.getString("exec_id"))
                        || nativeSchedule.getIntValue("oper_type") != 1
                        || nativeSchedule.getIntValue("oper_value") != 1
                        || strategy == null || !flowId.equals(strategy.getString("id"))
                        || !workspaceId.equals(strategy.getString("group_id")))
                    throw new IllegalArgumentException("SCHEDULE_ID_MISMATCH");
                create("/metadata/schedule/" + flowId, bytes);
                return Map.of("published", true);
            }
            case "stop": {
                String path = "/metadata/schedule/" + flowId;
                if (client.checkExists().forPath(path) == null)
                    return Map.of("published", false, "reason", "NOT_SUBMITTED");
                byte[] bytes = nativeBytes(request, "stop");
                JSONObject originalStop = JSON.parseObject(new String(bytes, StandardCharsets.UTF_8));
                if (!flowId.equals(originalStop.getString("id"))
                        || originalStop.getIntValue("oper_type") != 1
                        || originalStop.getIntValue("oper_value") != 0)
                    throw new IllegalArgumentException("STOP_ID_MISMATCH");
                client.setData().forPath(path, bytes);
                return Map.of("published", true);
            }
            case "status": {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("instance", read("/status/instance/" + instanceId));
                Stat stat = new Stat();
                try {
                    result.put("pid", new String(client.getData().storingStatIn(stat)
                            .forPath("/status/instance/" + instanceId + "/pid"), StandardCharsets.UTF_8));
                    result.put("pidEphemeral", stat.getEphemeralOwner() != 0);
                } catch (KeeperException.NoNodeException absent) {
                    result.put("pid", null);
                    result.put("pidEphemeral", false);
                }
                result.put("execution", read("/metadata/schedule/" + flowId + "/exec"));
                return result;
            }
            default: throw new IllegalArgumentException("UNKNOWN_OPERATION");
        }
    }

    public static void main(String[] args) throws Exception {
        PrintStream protocol = System.out;
        System.setOut(System.err); // Library diagnostics never enter the JSON-lines transport.
        ParserConfig.getGlobalInstance().setSafeMode(true);
        JSONObject configuration = JSON.parseObject(Files.readString(Path.of(args[0])));
        CoreMetadataBridge bridge = new CoreMetadataBridge(configuration);
        try (BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            protocol.println("{\"event\":\"METADATA_CONNECTED\"}");
            String line;
            while ((line = input.readLine()) != null) {
                if (line.length() > 12 * 1024 * 1024) throw new IllegalArgumentException("REQUEST_LIMIT");
                JSONObject request = JSON.parseObject(line);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("requestId", request.getString("requestId"));
                try {
                    result.put("data", bridge.action(request));
                    result.put("ok", true);
                } catch (Exception error) {
                    result.put("ok", false);
                    result.put("errorType", error.getClass().getSimpleName());
                }
                protocol.println(JSON.toJSONString(result));
                protocol.flush();
            }
        } finally {
            bridge.root.close();
        }
    }
}
