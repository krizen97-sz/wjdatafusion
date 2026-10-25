import com.alibaba.fastjson.JSON;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hikvision.idatafusion.detl.model.*;
import com.hikvision.idatafusion.detl.dto.task.*;
import com.hikvision.idatafusion.detl.dto.external.api.flow.ModuleDto;
import com.hikvision.idatafusion.detl.dto.preproc.TransformFieldVo;
import com.hikvision.idatafusion.detl.service.task.AdapterService;
import com.hikvision.idatafusion.detl.service.task.input.DataGeneratorInputServiceImpl;
import com.hikvision.idatafusion.detl.service.task.input.InputServiceImpl;
import com.hikvision.idatafusion.detl.service.task.input.FileInputServiceImpl;
import com.hikvision.idatafusion.detl.service.task.input.MqInputServiceImpl;
import com.hikvision.idatafusion.detl.service.task.output.OutputServiceImpl;
import com.hikvision.idatafusion.detl.service.task.output.MqOutputServiceImpl;
import com.hikvision.idatafusion.detl.dto.globalconfig.datasource.DatasourceDto;
import com.hikvision.idatafusion.detl.service.task.output.FileOutputServiceImpl;
import com.hikvision.idatafusion.detl.service.task.impl.ZookeeperServiceImpl;
import com.hikvision.idatafusion.detl.service.task.preproc.AdapterServiceFactory;
import com.hikvision.idatafusion.detl.service.task.preproc.FieldMappingServiceImpl;
import com.hikvision.idatafusion.detl.utils.ZkUtil;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Function;

/** Platform metadata adapter. Vendor DTOs, configuration generators and runtime algorithms are unchanged.
 * One request per JVM; no database, identity server, ZooKeeper or network connection is opened here.
 */
public final class OriginalTaskCompiler {
    static final String P = "com.hikvision.idatafusion.detl.";
    static final ObjectMapper MAPPER = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
        .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    static final int MAX_BYTES = 8 * 1024 * 1024;
    static final String MANAGED_OUTPUT = "/平台输出";
    static final Set<String> INPUTS = Set.of("dataGeneratorInput", "postgresqlInput", "fileInput", "kafkaInput");
    static final Set<String> OUTPUTS = Set.of("fileOutput", "postgresqlOutput", "kafkaV2Output");
    static final Set<String> PLUGINS = Set.of("dataGeneratorInput", "fieldMapping", "fileOutput", "postgresqlInput", "postgresqlOutput", "fileInput", "kafkaInput", "kafkaV2Output");
    static final Map<String, String> TYPES = Map.of("dataGeneratorInput", "input", "fieldMapping", "preproc", "fileOutput", "output", "postgresqlInput", "input", "postgresqlOutput", "output", "fileInput", "input", "kafkaInput", "input", "kafkaV2Output", "output");
    static final Map<String, String> CLASSES = Map.of(
        "dataGeneratorInput", "com.hikvision.idatafusion.adapter.input.data.DataGeneratorInput",
        "fieldMapping", "com.hikvision.idatafusion.etl.adapter.process.fieldmapping.FieldMapping",
        "fileOutput", "com.hikvision.idatafusion.etl.adapter.output.file.FileOutput",
        "fileInput", "com.hikvision.idatafusion.etl.adapter.input.file.FileInput",
        "postgresqlInput", "com.hikvision.idatafusion.etl.adapter.input.postgresql.PostgresqlInput",
        "postgresqlOutput", "com.hikvision.idatafusion.etl.adapter.output.postgresql.PostgreSqlOutput",
        "kafkaInput", "com.hikvision.idatafusion.etl.adapter.input.kafka.KafkaInput",
        "kafkaV2Output", "com.hikvision.idatafusion.etl.adapter.output.kafkaV2.KafkaV2Output");
    static final Set<String> GENERATOR_PARAMS = Set.of("etl.plugin.input.generator.data.size");
    static final Set<String> FILE_PARAMS = Set.of("etl.file.maxRow", "etl.file.flushTime");
    static final Set<String> EMPTY_REFERENCES = Set.of("sqlStr", "runtimeJson", "referenceConfigAdapter",
        "tableParamVoList", "lookupProcessVo", "javascriptVo", "joinVoList", "conditionVoList", "dataFilterVo",
        "httpInputNodeVo", "httpOutputNodeVo", "webServiceNode", "sqlRunnerNode", "alarmConfigVo",
        "cloudStoreVo", "kettleInputVo", "kettleProcessVo", "variableSettingVoList", "strackVoList");

    static final class Rejected extends RuntimeException {
        final String code, reason;
        Rejected(String code, String message) { this(code, message, null); }
        Rejected(String code, String message, String reason) { super(message); this.code = code; this.reason = reason; }
    }
    static void require(boolean condition, String message) { if (!condition) throw new Rejected("INVALID_INPUT", message); }
    static void supported(boolean condition, String message) { if (!condition) throw new Rejected("UNSUPPORTED_GRAPH", message); }
    static Map<String,Object> map(Object... args) {
        Map<String,Object> result = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i += 2) result.put((String)args[i], args[i+1]);
        return result;
    }
    @SuppressWarnings("unchecked") static Map<String,Object> object(Object value, String name) {
        require(value instanceof Map, name + " must be an object"); return (Map<String,Object>)value;
    }
    @SuppressWarnings("unchecked") static List<Object> list(Object value, String name) {
        if (value == null) return List.of();
        require(value instanceof List, name + " must be an array"); return (List<Object>)value;
    }
    static boolean empty(Object value) { return value == null || "".equals(value) || value instanceof Collection && ((Collection<?>)value).isEmpty() || value instanceof Map && ((Map<?,?>)value).isEmpty(); }
    static String text(Object value) { return value == null ? "" : String.valueOf(value); }
    static long id(Object value, String name) {
        String s = text(value); require(s.matches("[1-9][0-9]{0,15}"), name + " must be a positive decimal identifier");
        long n = Long.parseLong(s); require(n <= 9007199254740991L, name + " exceeds the original browser ID range"); return n;
    }
    static long wireId(Map<String,Object> value, String key) {
        require(value.get(key) instanceof String, key + " must be a decimal string"); return id(value.get(key), key);
    }
    static String json(Object value) { try { return MAPPER.writeValueAsString(value); } catch (IOException e) { throw new IllegalArgumentException(e); } }
    static Map<String,Object> parse(String value) {
        require(value.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES, "JSON exceeds 8 MiB");
        try { Map<String,Object> result = object(MAPPER.readValue(value, Object.class), "JSON"); safeJson(result, 0); return result; }
        catch (Rejected e) { throw e; } catch (IOException e) { throw new Rejected("INVALID_INPUT", "Invalid or duplicate JSON fields"); }
    }
    static void safeJson(Object value, int depth) {
        require(depth <= 64, "JSON nesting exceeds 64");
        if (value instanceof Map) for (Map.Entry<?,?> e : ((Map<?,?>)value).entrySet()) {
            require(!text(e.getKey()).equals("@type") && !text(e.getKey()).equals("$ref"), "JSON type/reference directives are forbidden"); safeJson(e.getValue(), depth+1);
        }
        if (value instanceof List) for (Object item : (List<?>)value) safeJson(item, depth+1);
    }
    static String sha(byte[] value) {
        try { StringBuilder out = new StringBuilder(); for (byte b : MessageDigest.getInstance("SHA-256").digest(value)) out.append(String.format("%02x", b)); return out.toString(); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    static <T> T bean(Class<T> type, Object value) { return JSON.parseObject(json(value), type); }
    static <T> T bean(Class<T> type, Object... fields) { return bean(type, map(fields)); }
    static Map<String,Object> context(Map<String,Object> value) {
        for (String key : List.of("businessTaskId", "revision", "ownerId", "workspaceId")) wireId(value, key);
        require(wireId(value, "workspaceId") <= Integer.MAX_VALUE, "workspaceId must fit the original int group ID");
        require(value.get("projectCode") instanceof String && text(value.get("projectCode")).matches("[A-Za-z0-9_.:-]{1,128}"), "Invalid projectCode");
        require(value.keySet().equals(Set.of("businessTaskId", "revision", "ownerId", "workspaceId", "projectCode")), "Unexpected context field");
        return new LinkedHashMap<>(value);
    }

    static Map<String,Object> normalize(Map<String,Object> input, Map<String,Object> context) {
        Map<String,Object> visual = parse(json(input));
        removeVisualCredentials(visual);
        if (visual.containsKey("panelUIInfo")) {
            require(!visual.containsKey("panelUiInfo") || Objects.equals(visual.get("panelUiInfo"), visual.get("panelUIInfo")), "Conflicting panelUiInfo aliases");
            visual.put("panelUiInfo", visual.remove("panelUIInfo"));
        }
        require(empty(visual.get("visualBase64")), "Supply the decoded original FlowVisual DTO");
        visual.putIfAbsent("nodes", map()); visual.putIfAbsent("links", new ArrayList<>());
        visual.putIfAbsent("streamVoList", new ArrayList<>()); visual.putIfAbsent("paramVoList", new ArrayList<>());
        visual.putIfAbsent("flowVo", map());
        Map<String,Object> flow = object(visual.get("flowVo"), "flowVo");
        // Trusted platform ownership replaces transport/UI identity fields before freezing a revision.
        visual.put("flowId", id(context.get("businessTaskId"), "businessTaskId"));
        flow.put("id", id(context.get("businessTaskId"), "businessTaskId"));
        flow.put("groupId", id(context.get("workspaceId"), "workspaceId"));
        flow.put("userId", id(context.get("ownerId"), "ownerId"));
        flow.put("projectCode", context.get("projectCode"));
        bean(FlowVisualVo.class, visual); // Original typed DTO must accept the original UI structure.
        topology(visual, false);
        return visual;
    }

    /** Topology checks are scope checks; all field transformations still execute in original plugins. */
    static List<Map<String,Object>> topology(Map<String,Object> visual, boolean complete) {
        Map<String,Object> nodes = object(visual.get("nodes"), "nodes");
        require(nodes.size() <= 32, "At most 32 nodes are supported");
        List<Object> links = list(visual.get("links"), "links"); require(links.size() <= 96, "At most 96 links are supported");
        Map<String,Map<String,Object>> byId = new LinkedHashMap<>();
        Map<String,String> next = new HashMap<>(); Set<String> incoming = new HashSet<>();
        for (Map.Entry<String,Object> e : nodes.entrySet()) {
            Map<String,Object> node = object(e.getValue(), "node"); String key = String.valueOf(id(node.get("id"), "node.id"));
            require(e.getKey().equals(key), "Node map key must equal its original numeric id");
            require(byId.put(key, node) == null, "Duplicate node ID");
            supported(PLUGINS.contains(text(node.get("adapterName"))), "Unsupported original plugin: " + text(node.get("adapterName")));
        }
        for (Object item : links) {
            Map<String,Object> link = object(item, "link");
            String source = text(link.get("source")), target = text(link.get("target"));
            require(byId.containsKey(source) && byId.containsKey(target), "Dangling link");
            supported(next.put(source, target) == null && incoming.add(target), "Branching or merging is not supported by this profile");
        }
        List<Map<String,Object>> ordered = new ArrayList<>();
        for (String start : byId.keySet()) if (!incoming.contains(start)) {
            String current = start;
            while (current != null) { require(ordered.size() < nodes.size(), "Cyclic graph"); ordered.add(byId.get(current)); current = next.get(current); }
        }
        supported(ordered.size() == nodes.size(), "Cyclic graph is not supported");
        if (complete) {
            require(nodes.size() >= 2, "Execution requires an input and an output");
            supported(links.size() == nodes.size()-1, "Execution requires one connected linear chain");
            supported(INPUTS.contains(ordered.get(0).get("adapterName")) && OUTPUTS.contains(ordered.get(ordered.size()-1).get("adapterName")), "The supported chain starts with an original input and ends with an original output");
            for (int i = 1; i < ordered.size()-1; i++) supported("fieldMapping".equals(ordered.get(i).get("adapterName")), "Only fieldMapping is supported between input and output");
        }
        return ordered;
    }

    static void removeVisualCredentials(Object value) {
        if (value instanceof Map) {
            Map<?,?> fields=(Map<?,?>)value;
            fields.keySet().removeIf(key -> Set.of("password","databasePassword","passwd","secret","secretKey").contains(text(key)));
            for (Object child : fields.values()) removeVisualCredentials(child);
        } else if (value instanceof List) for (Object child : (List<?>)value) removeVisualCredentials(child);
    }
    static Set<String> connectionIds(Map<String,Object> visual) {
        Set<String> ids = new LinkedHashSet<>();
        for (Object raw : object(visual.get("nodes"),"nodes").values()) {
            Map<String,Object> node = object(raw,"node");
            if (!empty(node.get("dbId"))) ids.add(Long.toString(id(node.get("dbId"),"node.dbId")));
        }
        return ids;
    }
    static void identifier(Object value, String name) {
        require(text(value).matches("[A-Za-z_][A-Za-z0-9_$]{0,127}"), name + " must be an explicit identifier");
    }
    static void remoteDirectory(Object value) {
        String path = text(value); require(path.startsWith("/") && path.length() <= 1024 && !path.contains("\\") && !path.contains("${") && !path.contains("\0"), "Remote directory must be an explicit absolute path");
        for (String component : path.split("/")) require(!component.equals("..") && !component.equals("."), "Remote path traversal is forbidden");
    }
    static Map<String,Object> prepare(Map<String,Object> request) {
        Set<String> prepareKeys = new HashSet<>(request.keySet()); prepareKeys.remove("trustedConnectionRefs");
        require(prepareKeys.equals(Set.of("operation", "context", "visual")), "Unexpected prepare field");
        Map<String,Object> ctx = context(object(request.get("context"), "context"));
        Map<String,Object> visual = normalize(object(request.get("visual"), "visual"), ctx);
        Map<String,Object> snapshot = map("protocolVersion", 1, "context", ctx, "visual", visual);
        Map<String,Object> refs = request.get("trustedConnectionRefs") == null ? map() : object(request.get("trustedConnectionRefs"), "trustedConnectionRefs");
        Set<String> referenced = connectionIds(visual);
        require(refs.keySet().equals(referenced), "The platform must freeze every selected connection revision");
        for (Map.Entry<String,Object> ref : refs.entrySet()) { id(ref.getKey(), "connection ID"); require(ref.getValue() instanceof String, "Connection revision must be a decimal string"); id(ref.getValue(), "connection revision"); }
        if (!refs.isEmpty()) snapshot.put("connectionRefs", new LinkedHashMap<>(refs));
        String serialized = json(snapshot);
        return map("ok", true, "protocolVersion", 1, "operation", "prepare", "canonicalVisual", visual,
            "snapshotJson", serialized, "snapshotSha256", sha(serialized.getBytes(StandardCharsets.UTF_8)), "diagnostics", List.of());
    }

    static Map<String,Object> schedule(Map<String,Object> request) {
        require(request.keySet().equals(Set.of("operation", "scheduleConfig", "zoneId")), "Unexpected schedule request field");
        String zone = text(request.get("zoneId"));
        try { java.time.ZoneId.of(zone); } catch (RuntimeException invalid) { throw new Rejected("INVALID_INPUT", "Invalid platform scheduling time zone"); }
        Map<String,Object> raw = object(request.get("scheduleConfig"), "scheduleConfig");
        Set<String> fields = Set.of("id", "nodeIds", "scheduleType", "intervalTime", "flowIncrStatus", "dependencyFlowId", "dependencyFlowStatus", "unit", "timeInterval", "executeTime", "exactDate", "executeTimeExactDate", "excludeTimeType", "enablePeriod", "excludeDate", "excludeStartTime", "excludeEndTime", "disablePeriod", "startFullImmediately", "start", "alarmEnable");
        supported(fields.containsAll(raw.keySet()), "Unknown original schedule field");
        var config = bean(com.hikvision.idatafusion.detl.dto.task.schedule.ScheduleConfigVo.class, raw);
        supported(config.getFlowIncrStatus() == null || config.getFlowIncrStatus() == 1, "This profile schedules independent full batches only");
        supported(config.getStartFullImmediately() == null || config.getStartFullImmediately() == 0, "Immediate initial execution is not supported by a periodic plan");
        supported(config.getAlarmEnable() == null || config.getAlarmEnable() == 0, "Original external alarm delivery is not supported");
        // executeTimeExactDate is an inactive original form default when exactDate is empty.
        // The original Cron generator consumes exactDate, not this standalone time field.
        for (String key : List.of("exactDate", "enablePeriod", "excludeDate", "excludeStartTime", "excludeEndTime", "disablePeriod"))
            supported(empty(raw.get(key)), "Exact dates, custom cron and exclusion windows are not supported in this schedule profile");
        if (Integer.valueOf(1).equals(config.getScheduleType())) {
            return map("ok", true, "protocolVersion", 1, "operation", "schedule", "mode", "MANUAL", "scheduleConfig", raw, "zoneId", zone,
                "cronExpressions", List.of(), "nativeSchedule", config.toScheduleVo(), "diagnostics", List.of());
        }
        supported(Integer.valueOf(2).equals(config.getScheduleType()) && config.getUnit() != null && Set.of(2, 3, 4, 5, 6).contains(config.getUnit()), "Only original monthly, weekly, daily, hourly and minute cycle schedules are supported");
        if (Set.of(5, 6).contains(config.getUnit())) {
            require(config.getTimeInterval() != null && config.getTimeInterval().matches("[1-9][0-9]{0,9}"), "Calendar interval must be a positive integer");
            long interval = Long.parseLong(config.getTimeInterval()); require(interval <= Integer.MAX_VALUE, "Calendar interval exceeds original integer range");
            ScheduleVo nativeSchedule = config.toScheduleVo();
            EtlSchedule persisted = new EtlSchedule(); org.springframework.beans.BeanUtils.copyProperties(nativeSchedule, persisted);
            String expression = com.hikvision.idatafusion.detl.utils.CronUtil.getExecExpr(persisted);
            require(expression.equals(interval + "#" + config.getUnit()), "Unexpected original calendar expression");
            // This original method only constructs the Quartz builder; it does not submit/start a scheduler or read ZK.
            org.quartz.ScheduleBuilder<?> builder = com.hikvision.idatafusion.etl.core.schedule.JobManager.getInstance().getScheduleBuilder(
                Map.of("scheduleType", Long.valueOf(com.hikvision.idatafusion.etl.core.schedule.ScheduleInfo.SCHEDULE_TYPE_INTERVAL), "interval", interval, "unit", config.getUnit().longValue()), expression);
            org.quartz.CalendarIntervalTrigger trigger = (org.quartz.CalendarIntervalTrigger)org.quartz.TriggerBuilder.newTrigger().withSchedule(builder).startAt(new Date(0)).build();
            return map("ok", true, "protocolVersion", 1, "operation", "schedule", "mode", "CALENDAR", "scheduleConfig", raw, "zoneId", zone,
                "cronExpressions", List.of(), "nativeExpression", expression, "calendar", map("interval", trigger.getRepeatInterval(), "unit", trigger.getRepeatIntervalUnit().name()),
                "nativeSchedule", nativeSchedule, "diagnostics", List.of());
        }
        String interval = config.getTimeInterval(), time = config.getExecuteTime();
        require(time != null && time.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]"), "Execution time must be HH:mm:ss");
        require(interval != null && interval.matches("[0-9]+(?:,[0-9]+){0,31}"), "Invalid original cycle selection");
        int maximum = config.getUnit() == 2 ? 32 : config.getUnit() == 3 ? 7 : 1; Set<Integer> selected = new HashSet<>();
        for (String value : interval.split(",")) {
            require(value.matches("[1-9][0-9]?"), "Invalid original cycle selection"); int n = Integer.parseInt(value);
            require(n <= maximum && selected.add(n), "Invalid or duplicate original cycle selection");
        }
        // Preserve the original DTO's validation and Cron construction; do not implement a second rule compiler.
        ScheduleVo nativeSchedule = config.toScheduleVo();
        String cron = com.hikvision.idatafusion.detl.utils.CronUtil.generateEnableCronExpr(nativeSchedule.getUnit(), nativeSchedule.getTimeInterval(), nativeSchedule.getExecuteTime(), nativeSchedule.getExactDate());
        List<String> expressions = new ArrayList<>();
        for (String expression : cron.split(";")) {
            require(!expression.isBlank() && org.quartz.CronExpression.isValidExpression(expression), "Original schedule did not generate a valid Quartz expression");
            expressions.add(expression);
        }
        require(!expressions.isEmpty(), "Original schedule has no trigger");
        return map("ok", true, "protocolVersion", 1, "operation", "schedule", "mode", "CRON", "scheduleConfig", raw, "zoneId", zone,
            "cronExpressions", expressions, "nativeSchedule", nativeSchedule, "diagnostics", List.of());
    }

    static Path outputDirectory(Map<String,Object> run) {
        require(Set.of("test", "run").contains(text(run.get("mode"))), "Invalid execution mode");
        Path root = Path.of(text(run.get("authorizedOutputRoot"))), out = Path.of(text(run.get("outputsDir")));
        require(root.isAbsolute() && out.isAbsolute() && root.equals(root.normalize()) && out.equals(out.normalize()), "Output paths must be absolute normalized paths");
        require(out.startsWith(root) && !out.equals(root), "outputsDir must be a private child of the authorized output root");
        require(!out.toString().contains("${"), "Dynamic placeholders are forbidden in the platform output path");
        // A remote worker's /home may have unrelated symlink semantics on the compiler's OS.
        // Only authenticated platform code may set this provenance after a bound worker.reserve response.
        String source = text(run.get("outputRootSource"));
        require(source.isEmpty() || source.equals("worker-reserve"), "Invalid output root provenance");
        if (source.isEmpty()) for (Path p = out; p != null; p = p.getParent()) require(!Files.isSymbolicLink(p), "Symbolic output paths are forbidden");
        return out;
    }
    static List<EtlParam> params(Object raw, String plugin, long nodeId, int nodeType, Path bundle, Path output) throws IOException {
        List<EtlParam> result = new ArrayList<>(); Set<String> names = new HashSet<>();
        Set<String> allowed = plugin.equals("dataGeneratorInput") ? GENERATOR_PARAMS : plugin.equals("fileOutput") ? FILE_PARAMS : Set.of();
        Map<String,String> defaults = new LinkedHashMap<>();
        Map<String,Object> descriptor = parse(Files.readString(bundle.resolve("instance/etl-" + plugin + "-2.0.0/etl_plugin.json")));
        for (Object rawParam : list(descriptor.get("adapterParams"), "original parameter catalog")) {
            Map<String,Object> p = object(rawParam, "original parameter"); defaults.put(text(p.get("name")), text(p.get("defaultValue")));
        }
        for (Object item : list(raw, "parameters")) {
            Map<String,Object> p = object(item, "parameter"); String name = text(p.get("paramName"));
            supported(empty(p.get("globalParamKey")) && !Set.of("el-select-source", "el-input-fel").contains(text(p.get("valueType"))), "External/global/FEL parameters are not supported");
            require(names.add(name), "Duplicate parameter " + name);
            String value = text(p.get("paramValue"));
            if (plugin.equals("fileOutput") && name.equals("etl.plugin.fileOutput.asynchronous.generation.text")) {
                supported("false".equals(value), "Asynchronous output is not supported in this initial execution profile");
            }
            if (allowed.contains(name)) {
                require(value.matches("[1-9][0-9]{0,8}"), "Parameter must be a bounded positive integer: " + name);
                if (plugin.equals("dataGeneratorInput")) require(Long.parseLong(value) <= 1000000, "Generator row limit is 1000000");
            } else if (plugin.equals("fileOutput") && name.equals("etl.file.localDir")) {
                value = output.toString();
            } else if (plugin.equals("fileOutput") && name.equals("etl.file.fileType")) {
                supported(Set.of("csv", "line").contains(value), "Only original csv/line file output is supported");
            } else if (plugin.equals("fileOutput") && name.equals("etl.file.serverType") && value.equals("local")) {
                // Explicitly consistent local metadata is allowed. Legacy catalog Ftp
                // remains untouched and is rejected after the original merge below.
            } else if (!Set.of("dataGeneratorInput","fieldMapping","fileOutput").contains(plugin)) {
                supported(defaults.containsKey(name), "Parameter is not present in the original plugin catalog");
                supported(!name.matches("(?i).*(password|username|bootstrap.servers|jdbc.url|server_ip|server_port|local_dir|server_dir).*"), "Connection and local paths must come from the platform binding and original form");
                supported(!value.contains("${") && !value.contains("#{") || value.equals(defaults.get(name)), "Dynamic custom parameters are not supported");
                require(value.length() <= 4096, "Plugin parameter exceeds the value limit");
                if (name.equals("etl.plugin.partial.failed.stop.enable")) supported(Set.of("true","false").contains(value),"Original failure-stop switch must be true or false");
                else if (name.equals("etl.kafka.auto.offset.reset")) supported(Set.of("earliest","latest","none").contains(value),"Invalid original Kafka offset choice");
                else if (name.equals("format")) supported(Set.of("json","text").contains(value),"Only JSON/text message format is supported");
                else if (name.equals("etl.plugin.sql.query.limit")) require(value.isEmpty() || value.matches("[1-9][0-9]{0,6}"),"Bounded query row limit required");
                else if (name.equals("etl.kafka.group.id")) require(value.equals(defaults.get(name)) || value.matches("[A-Za-z0-9_.-]{1,128}"),"Invalid Kafka group ID");
                else supported(Objects.equals(defaults.get(name),value),"Non-default advanced connector parameter requires explicit support: "+name);
            } else {
                // Inactive placeholders from the actual original plugin form are legitimate.
                // Changed external/FEL settings require a separately audited execution profile.
                supported(defaults.containsKey(name) && (defaults.get(name).equals(value) || name.equals("etl.plugin.fileOutput.asynchronous.generation.text") && value.equals("false")), "Unsupported non-default parameter: " + name);
            }
            result.add(bean(EtlParam.class, "paramName", name, "paramValue", value, "nodeId", nodeId, "nodeType", nodeType, "enableFlag", 1));
        }
        if (plugin.equals("dataGeneratorInput") && !names.contains("etl.plugin.input.generator.data.size")) {
            String originalDefault = defaults.get("etl.plugin.input.generator.data.size");
            require(originalDefault != null && originalDefault.matches("[1-9][0-9]{0,6}") && Long.parseLong(originalDefault) <= 1000000, "Original generator default is not bounded");
            result.add(new EtlParam("etl.plugin.input.generator.data.size", originalDefault));
        }
        return result;
    }
    /** Reuse the vendor's save projection; only its persistence destination is replaced. */
    public static final class SavedAdapterParameters extends com.hikvision.idatafusion.detl.service.globalconfig.impl.ParamManageServiceImpl {
        final List<EtlParam> captured = new ArrayList<>();
        @Override public void saveNodeParam(List<ParamVo> values, com.hikvision.idatafusion.detl.common.PropertyTypeEnum type, Long nodeId, Long flowId) {
            require(type == com.hikvision.idatafusion.detl.common.PropertyTypeEnum.MODULE, "Unexpected original parameter persistence category");
            for (ParamVo value : values) captured.add(bean(EtlParam.class, "paramName",value.getParamName(),"paramValue",value.getParamValue(),"nodeType",type.getId(),"nodeId",nodeId,"enableFlag",1));
        }
    }
    static List<EtlParam> savedAdapterParameters(Map<String,Object> node, List<EtlParam> checked, long itemId, long flowId) {
        Map<String,Object> projected = parse(json(node));
        projected.put("adapterType", TYPES.get(text(node.get("adapterName"))));
        projected.put("adapterParamVoList",checked);
        SavedAdapterParameters saver = new SavedAdapterParameters();
        saver.saveAdapterParam(bean(NodeVo.class,projected),itemId,flowId);
        return saver.captured;
    }
    static List<EtlParam> flowParams(Object raw, long flowId, Path bundle) throws IOException {
        Map<String,Object> catalog = parse(Files.readString(bundle.resolve("instance/conf/task_config.json")));
        Map<String,Map<String,Object>> definitions = new LinkedHashMap<>();
        for (Object item : list(catalog.get("configParam"), "original task parameter catalog")) {
            Map<String,Object> definition = object(item, "task parameter definition");
            require(definitions.put(text(definition.get("name")), definition) == null, "Duplicate original task parameter definition");
        }
        List<EtlParam> result = new ArrayList<>(); Set<String> names = new HashSet<>();
        Set<String> defaultOnly = Set.of("etl.task.schedule.expand.enable", "etl.task.input.kafka.offset.save.enable", "etl.task.failed.data.retry.cron", "etl.task.data.heartbeat.send.interval", "etl.task.schedule.periodic.enforcement");
        for (Object item : list(raw, "flow parameters")) {
            Map<String,Object> parameter = object(item, "flow parameter"); String name = text(parameter.get("paramName"));
            Map<String,Object> definition = definitions.get(name);
            supported(definition != null, "Task parameter is not present in the original catalog");
            require(names.add(name), "Duplicate task parameter");
            supported(empty(parameter.get("globalParamKey")) && !Set.of("el-select-source", "el-input-fel").contains(text(parameter.get("valueType"))), "External/global/FEL task parameters are not supported");
            supported(empty(parameter.get("enableFlag")) || "1".equals(text(parameter.get("enableFlag"))), "Disabled task parameter metadata is not supported");
            require(parameter.get("paramValue") != null && !(parameter.get("paramValue") instanceof Map) && !(parameter.get("paramValue") instanceof List), "Task parameter value is required");
            String value = text(parameter.get("paramValue")), type = text(definition.get("type"));
            if (defaultOnly.contains(name)) supported(value.equals(text(definition.get("defaultValue"))), "Task parameter requires a scheduling or external-source capability outside this profile");
            else if (type.equals("el-switch")) require(Set.of("true", "false").contains(value), "Task switch must be true or false");
            else if (type.equals("el-select")) {
                Set<String> choices = new HashSet<>(); for (Object option : list(definition.get("allowableValues"), "task parameter options")) choices.add(text(object(option, "option").get("value")));
                require(choices.contains(value), "Task parameter is not an original enumerated option");
            } else {
                require(value.matches("[1-9][0-9]{0,9}"), "Task numeric parameter must be a bounded positive integer");
                long number = Long.parseLong(value), min = 1, max = Integer.MAX_VALUE;
                if (definition.get("minValue") instanceof Number) min = ((Number)definition.get("minValue")).longValue();
                if (definition.get("maxValue") instanceof Number) max = ((Number)definition.get("maxValue")).longValue();
                if (name.equals("etl.task.data.clone.thread.num")) max = 16;
                require(number >= min && number <= max, "Task numeric parameter is outside the supported original range");
            }
            result.add(bean(EtlParam.class, "paramName", name, "paramValue", value, "nodeId", flowId, "nodeType", 2, "enableFlag", 1));
        }
        return result;
    }
    static void validateFields(List<Map<String,Object>> nodes) {
        Set<String> fields = new LinkedHashSet<>();
        for (Object raw : list(nodes.get(0).get("streamFieldVoList"), "stream fields")) {
            Map<String,Object> field = object(raw, "stream field"); String name = text(field.get("fieldName"));
            require(!name.isEmpty() && name.length() <= 256 && fields.add(name), "Invalid or duplicate stream field");
            noPrimaryFilename(field);
        }
        Set<String> configured = new LinkedHashSet<>();
        if ("dataGeneratorInput".equals(nodes.get(0).get("adapterName"))) for (Object raw : list(nodes.get(0).get("adapterData"), "generator fields")) configured.add(text(object(raw, "generator field").get("fieldName")));
        if ("dataGeneratorInput".equals(nodes.get(0).get("adapterName"))) require(fields.equals(configured), "Generator configuration and stream field names must match");
        for (int i = 1; i < nodes.size()-1; i++) {
            Set<String> result = new LinkedHashSet<>();
            for (Object raw : list(nodes.get(i).get("transformFieldVoList"), "mapping fields")) {
                Map<String,Object> field = object(raw, "mapping field"); String source = text(field.get("fieldSrcName")), target = text(field.get("fieldDstName"));
                require(fields.contains(source) && !target.isEmpty() && target.length() <= 256, "Mapping references an unavailable field");
                require(result.add(target), "Duplicate mapping target");
                if ("keep".equalsIgnoreCase(text(field.get("processType")))) result.add(source);
            }
            fields = result;
        }
        for (Object raw : list(nodes.get(nodes.size()-1).get("dstTableFieldVoList"), "output fields")) {
            Map<String,Object> field = object(raw, "output field");
            noPrimaryFilename(field);
            // The original file plugin selects row values by fieldName. Its original
            // DstTableFieldService accepts an absent expression and does not synthesize one.
            // Keep that DTO shape; an explicit expression still cannot enable arbitrary FEL.
            supported((empty(field.get("fieldExpression")) ? fields.contains(text(field.get("fieldName"))) : fields.contains(text(field.get("fieldExpression")))), "Output fields must directly reference an available field");
        }
    }
    static void noPrimaryFilename(Map<String,Object> field) {
        // Original FileOutput.genFileName appends the value of the schema's primary field.
        // Blocking this unneeded capability prevents data values becoming host path components.
        supported(empty(field.get("fieldKeyFlag")) || "0".equals(text(field.get("fieldKeyFlag"))), "Primary-key-derived output filenames are outside this execution profile");
    }
    static void validateNode(Map<String,Object> node) {
        for (String key : EMPTY_REFERENCES) supported(empty(node.get(key)), "External reference is not supported: " + key);
        String plugin = text(node.get("adapterName"));
        supported(list(node.get("datasourceParamVoList"), "datasource parameters").isEmpty(), "Datasource overrides must use a platform connection");
        boolean external = Set.of("postgresqlInput","postgresqlOutput","fileInput","kafkaInput","kafkaV2Output").contains(plugin)
            || plugin.equals("fileOutput") && !"local".equals(object(node.get("adapterData"),"file output").get("sourceType"));
        if (external) id(node.get("dbId"),"Selected platform connection");
        else supported(empty(node.get("dbId")),"This local plugin must not carry an unrelated connection");
        if (!Set.of("postgresqlInput","fileInput","kafkaInput").contains(plugin)) supported(empty(node.get("srcTableUseVoList")),"This node cannot carry source-table references");
        if (OUTPUTS.contains(plugin) && node.get("dstTableVo") instanceof Map) {
            Map<String,Object> table=object(node.get("dstTableVo"),"dstTableVo");
            supported(empty(table.get("dataCleansing")) || "0".equals(text(table.get("dataCleansing"))),"Use the original disabled cleansing value 0; automatic target clearing is not supported");
        }
        if (plugin.equals("dataGeneratorInput")) {
            List<Object> fields = list(node.get("adapterData"), "generator.adapterData");
            require(!fields.isEmpty() && fields.size() <= 512, "Generator must contain 1..512 fields");
            Set<String> names = new HashSet<>();
            for (Object raw : fields) {
                Map<String,Object> field = object(raw, "generator field");
                require(!text(field.get("fieldName")).isEmpty() && names.add(text(field.get("fieldName"))), "Invalid or duplicate generator field");
                supported("FIXED_VALUE".equals(field.get("ruleType")), "This initial profile supports the original FIXED_VALUE generator rule");
            }
            require(!list(node.get("streamFieldVoList"), "streamFieldVoList").isEmpty(), "Generator stream fields are required");
        } else if (plugin.equals("fieldMapping")) {
            List<Object> fields = list(node.get("transformFieldVoList"), "transformFieldVoList"); require(!fields.isEmpty() && fields.size() <= 512, "Mapping must contain 1..512 fields");
            for (Object raw : fields) {
                Map<String,Object> field = object(raw, "mapping field");
                // Original FieldMappingService skips identical name/type mappings, and
                // only tests equalsIgnoreCase("keep") for a rename. Null is its form default.
                supported((empty(field.get("processType")) || Set.of("move", "keep").contains(text(field.get("processType")).toLowerCase(Locale.ROOT))) && empty(field.get("condExpression")) && empty(field.get("processClassName")) && empty(field.get("redisKey")) && empty(field.get("bussinessRedisKey")), "Only original move/keep mappings without external functions are supported");
            }
        } else if (plugin.equals("postgresqlInput") || plugin.equals("kafkaInput")) {
            List<Object> tables = list(node.get("srcTableUseVoList"),"source tables"); require(tables.size()==1,"Select exactly one original table or topic");
            Map<String,Object> table=object(tables.get(0),"source table");
            if (plugin.equals("postgresqlInput")) {
                identifier(table.get("tableName"),"Source table");identifier(table.get("tableSchem") == null ? table.get("tableOwner") : table.get("tableSchem"),"Source schema");
                supported(empty(table.get("tableSql")) && !"lifecycle".equals(table.get("tableConfigType")),"This database profile reads a selected table");
                for (Object raw : list(node.get("streamFieldVoList"),"database fields")) {
                    Map<String,Object> field=object(raw,"database field");
                    identifier(field.get("fieldName"),"Database field");
                    supported(Objects.equals(field.get("fieldName"),field.get("fieldExpression")),"Selected-table fields must retain the original column-name expression");
                }
            } else require(text(table.get("tableName")).matches("[A-Za-z0-9_.-]{1,249}"),"Select one explicit Kafka topic");
            supported(empty(table.get("incrType")) || "0".equals(text(table.get("incrType"))),"This connector profile runs full inputs");
            require(!list(node.get("streamFieldVoList"),"stream fields").isEmpty(),"Input fields are required");
            supported(empty(node.get("adapterData")),"Non-default raw input adapterData is not supported");
        } else if (plugin.equals("postgresqlOutput") || plugin.equals("kafkaV2Output")) {
            Map<String,Object> table=object(node.get("dstTableVo"),"dstTableVo");
            if (plugin.equals("postgresqlOutput")) {
                identifier(table.get("tableName"),"Target table"); identifier(table.get("tableOwner"),"Target schema");
                supported(empty(table.get("addCreateSql")),"Automatic creation SQL requires a separate reviewed operation");
                supported(empty(table.get("writeOperation")) || "INSERT".equals(table.get("writeOperation")),"Choose the original INSERT write operation");
            } else {
                require(text(table.get("tableName")).matches("[A-Za-z0-9_.-]{1,249}"),"Select one explicit Kafka topic");
                supported(Set.of("json","text").contains(text(table.get("messageFormat"))),"Select the original JSON/text message format");
                supported(empty(table.get("avroSchemaUrl")),"Remote Avro schema is not supported");
            }
            require(!list(node.get("dstTableFieldVoList"),"output fields").isEmpty(),"Output fields are required");
            supported(empty(node.get("adapterData")),"Non-default raw output adapterData is not supported");
        } else if (plugin.equals("fileInput")) {
            Map<String,Object> config=object(node.get("adapterData"),"fileInput.adapterData");
            supported("ftp".equals(config.get("sourceType")),"This file input profile uses the original FTP input");
            remoteDirectory(config.get("absoluteDir"));
            supported(empty(config.get("relativeDir")) && "2".equals(text(config.get("sourcePathType"))),"Choose an explicit absolute FTP directory");
            supported(empty(config.get("localDir")) || MANAGED_OUTPUT.equals(config.get("localDir")),"Local download staging is platform managed");
            supported(Set.of("csv","line").contains(text(config.get("fileType"))),"Only original CSV/line FTP input is supported");
            supported(Set.of("time","delete","backup").contains(text(config.get("incrMode"))),"Choose an original FTP increment mode");
            if ("backup".equals(config.get("incrMode"))) remoteDirectory(config.get("backDir"));
            require(!list(node.get("streamFieldVoList"),"input fields").isEmpty(),"FTP input fields are required");
        } else {
            Map<String,Object> config = object(node.get("adapterData"), "fileOutput.adapterData");
            supported(Set.of("local","ftp").contains(text(config.get("sourceType"))), "Choose original local or FTP output");
            boolean local="local".equals(config.get("sourceType"));
            if (!local) { remoteDirectory(config.get("absoluteDir")); supported("2".equals(text(config.get("sourcePathType"))),"Choose an explicit absolute FTP directory"); }
            boolean managedSelected = false;
            for (String path : List.of("localDir", "absoluteDir")) {
                Object value = config.get(path);
                if (local || path.equals("localDir")) supported(empty(value) || MANAGED_OUTPUT.equals(value), "Select the explicit /平台输出 managed output; arbitrary host paths are not supported");
                managedSelected |= MANAGED_OUTPUT.equals(value);
            }
            supported(empty(config.get("relativeDir")), "Managed output subdirectories are not supported");
            for (Object raw : list(node.get("adapterParamVoList"), "file parameters")) {
                Map<String,Object> parameter = object(raw, "file parameter");
                if ("etl.file.localDir".equals(parameter.get("paramName"))) {
                    supported(empty(parameter.get("paramValue")) || MANAGED_OUTPUT.equals(parameter.get("paramValue")), "The output path parameter must explicitly select /平台输出");
                    managedSelected |= MANAGED_OUTPUT.equals(parameter.get("paramValue"));
                }
                if ("etl.file.fileType".equals(parameter.get("paramName")))
                    if (!Objects.equals(config.get("fileType"), parameter.get("paramValue")))
                        throw new Rejected("UNSUPPORTED_GRAPH", "File format parameter conflicts with the original file output form", "FILE_FORMAT_CONFLICT");
            }
            supported(!local || managedSelected, "Select /平台输出 before testing or running this flow");
            supported(Set.of("csv", "line").contains(text(config.get("fileType"))), "Only original csv/line file output is supported");
            for (String key : List.of("imgField", "temXlsDir", "jsonTemplate", "arrayPath")) supported(empty(config.get(key)), "File template/reference is not supported: " + key);
            for (String key : List.of("backFill", "isAsyncGenerateText")) supported(empty(config.get(key)) || Set.of("false", "0").contains(text(config.get(key))), "Asynchronous/backfill file output is not supported");
            supported(empty(config.get("upload")) || Objects.equals(text(config.get("upload")),local?"false":"true"),"File upload selection conflicts with source type");
            supported(empty(config.get("customExtension")) || text(config.get("customExtension")).matches("[A-Za-z0-9_-]{1,16}"), "File extension cannot contain a path");
            require(!list(node.get("dstTableFieldVoList"), "dstTableFieldVoList").isEmpty(), "File output fields are required");
        }
    }

    static void inject(Object target, String name, Object value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) try {
            Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(target, value); return;
        } catch (NoSuchFieldException ignored) { }
        throw new NoSuchFieldException(name);
    }
    static Object provider(String name, Map<String,Function<Object[],Object>> calls) throws Exception {
        Class<?> type = Class.forName(P + name);
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (method.getName().equals("toString")) return "Frozen platform metadata: " + name;
            if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
            if (method.getName().equals("equals")) return proxy == args[0];
            Function<Object[],Object> call = calls.get(method.getName());
            if (call == null) throw new UnsupportedOperationException("Unmapped original metadata method: " + name + "." + method.getName());
            return call.apply(args);
        });
    }
    static <T> T lookup(Map<Long,T> map, Object key) {
        T result = map.get(Long.valueOf(String.valueOf(key)));
        if (result == null) throw new Rejected("METADATA_MISMATCH", "Original generator requested metadata outside the frozen revision");
        return result;
    }
    static void key(Object actual, long wanted) { require(Long.valueOf(text(actual)).equals(wanted), "Original generator requested an unrelated metadata ID"); }
    static List<DstTableFieldVo> savedOutputFields(Object raw, long streamId, long nodeId) throws Exception {
        List<DstTableFieldVo> incoming = new ArrayList<>(), persisted = new ArrayList<>();
        for (Object value : list(raw, "output fields")) incoming.add(bean(DstTableFieldVo.class, value));
        // Reuse the original save projection (enableFlag, list order, stream/node binding
        // and passThrough semantics), with its sole DAO write captured in this snapshot.
        var service = new com.hikvision.idatafusion.detl.service.task.impl.DstTableFieldServiceImpl();
        inject(service, "etlDstTableFieldDao", provider("mapper.EtlDstTableFieldDao", Map.of("batchInsert", args -> {
            for (Object value : (List<?>)args[0]) {
                EtlDstTableField row = (EtlDstTableField)value;
                key(row.getStreamId(), streamId); key(row.getNodeId(), nodeId);
                DstTableFieldVo dto = new DstTableFieldVo(); org.springframework.beans.BeanUtils.copyProperties(row, dto); persisted.add(dto);
            }
            return null;
        })));
        service.batchInsert(incoming, streamId, null, nodeId);
        require(!persisted.isEmpty(), "File output requires persisted output fields");
        return persisted;
    }
    public static final class CompilationSink extends ZkUtil {
        final String flowPath, oldPath; byte[] bytes; boolean publishIntent;
        CompilationSink(long groupId, long flowId) { flowPath = "/metadata/group/" + groupId + "/flowjson/" + flowId; oldPath = "/metadata/group/" + groupId + "/flow/" + flowId; }
        public Boolean upsertNode(String path, byte[] value) {
            require(flowPath.equals(path) && bytes == null, "Unexpected original compiler write"); bytes = value.clone(); return true;
        }
        public String deleteNode(String path) { require(oldPath.equals(path), "Unexpected original legacy metadata deletion"); return path; }
    }
    static Properties catalog(Path bundle, String plugin) throws IOException {
        Properties value = new Properties();
        try (InputStream in = Files.newInputStream(bundle.resolve("instance/etl-" + plugin + "-2.0.0/adapter.properties"))) { value.load(in); }
        require(plugin.equals(value.getProperty("adapterName")) && "2.0.0".equals(value.getProperty("version")) && TYPES.get(plugin).equals(value.getProperty("adapterType")) && CLASSES.get(plugin).equals(value.getProperty("className")) && "java".equals(value.getProperty("adapterLanguage")), "Original plugin catalog does not match this verified profile");
        return value;
    }

    static void validateOriginalOutputs(byte[] flowBytes, List<Map<String,Object>> nodes) {
        String expectedSource = text(object(nodes.get(nodes.size()-1).getOrDefault("adapterData",map()),"output configuration").get("sourceType"));
        Map<String,Object> generated = parse(new String(flowBytes, StandardCharsets.UTF_8));
        for (Object rawStream : list(generated.get("streams"), "original streams")) {
            Map<String,Object> stream = object(rawStream, "original stream");
            for (Object rawModule : list(stream.get("modules"), "original modules")) {
                Map<String,Object> module = object(rawModule, "original module");
                if (!"fileOutput".equals(object(module.get("adapter"), "original adapter").get("adapter_name"))) continue;
                Map<String,String> values = new LinkedHashMap<>();
                for (Object rawParam : list(object(module.get("config"), "original output config").get("adapter_param"), "original output parameters")) {
                    Map<String,Object> parameter = object(rawParam, "original output parameter");
                    require(values.put(text(parameter.get("param_name")), text(parameter.get("param_value"))) == null, "Duplicate generated output parameter");
                }
                // Same strict local-file profile as the worker. Inspect the ORIGINAL
                // merged parameters; never silently replace an advanced parameter.
                if (!expectedSource.equals(values.get("etl.file.serverType")) || !(expectedSource.equals("local")?"false":"true").equals(values.get("etl.file.isUpload")))
                    throw new Rejected("UNSUPPORTED_GRAPH", "Original fileOutput parameters must match the selected connection and upload mode; remove conflicting advanced parameters", "LOCAL_OUTPUT_REQUIRED");
            }
        }
    }

    static Map<String,Object> compile(Map<String,Object> request, Path bundle) throws Exception {
        require(request.keySet().equals(Set.of("operation", "snapshotJson", "snapshotSha256", "runContext")), "Unexpected compile field");
        require(request.get("snapshotJson") instanceof String, "snapshotJson must be original UTF-8 text");
        String snapshotText = (String)request.get("snapshotJson"); String snapshotHash = sha(snapshotText.getBytes(StandardCharsets.UTF_8));
        require(snapshotHash.equals(request.get("snapshotSha256")), "Frozen snapshot digest mismatch");
        Map<String,Object> snapshot = parse(snapshotText);
        Set<String> snapshotKeys = new HashSet<>(snapshot.keySet()); snapshotKeys.remove("connectionRefs");
        require(snapshotKeys.equals(Set.of("protocolVersion", "context", "visual")) && Integer.valueOf(1).equals(snapshot.get("protocolVersion")), "Unknown snapshot schema");
        Map<String,Object> ctx = context(object(snapshot.get("context"), "context"));
        Map<String,Object> visual = object(snapshot.get("visual"), "visual");
        require(json(visual).equals(json(normalize(visual, ctx))), "Snapshot visual is not canonical for its platform context");
        Map<String,Object> run = object(request.get("runContext"), "runContext");
        Set<String> requiredRunKeys = Set.of("businessTaskId", "revision", "nativeFlowId", "nativeInstanceId", "metadataIdBase", "execId", "outputsDir", "authorizedOutputRoot", "mode");
        Set<String> actualRunKeys = new HashSet<>(run.keySet()); actualRunKeys.remove("outputRootSource"); actualRunKeys.remove("connections"); actualRunKeys.remove("connectionBindings");
        require(actualRunKeys.equals(requiredRunKeys), "Unexpected runContext field");
        for (String key : List.of("businessTaskId", "revision")) require(Objects.equals(ctx.get(key), run.get(key)), "Run does not belong to the frozen task revision");
        long flowId = wireId(run, "nativeFlowId"), instanceId = wireId(run, "nativeInstanceId"), base = wireId(run, "metadataIdBase"), groupId = wireId(ctx, "workspaceId");
        wireId(run, "execId"); require(base <= 9007199254740991L - 8192, "Metadata allocation block overflow");
        require(flowId != instanceId && (flowId < base || flowId >= base+8192) && (instanceId < base || instanceId >= base+8192), "Runtime identifiers overlap the reserved metadata block");
        Path output = outputDirectory(run);
        List<Map<String,Object>> nodes = topology(visual, true);
        Map<String,Object> connections = run.get("connections") == null ? map() : object(run.get("connections"), "connections");
        Map<String,Object> connectionRefs = snapshot.get("connectionRefs") == null ? map() : object(snapshot.get("connectionRefs"), "connectionRefs");
        require(connectionRefs.keySet().equals(connectionIds(visual)) && connections.keySet().equals(connectionRefs.keySet()), "Frozen connection bindings do not match the graph");
        Map<Long,DatasourceDto> datasourceById = new LinkedHashMap<>();
        Map<Long,List<EtlParam>> datasourceParameters = new LinkedHashMap<>();
        for (Map.Entry<String,Object> entry : connections.entrySet()) {
            long connectionId = id(entry.getKey(), "connectionId"); Map<String,Object> binding = object(entry.getValue(), "connection binding");
            require(binding.keySet().equals(Set.of("datasource", "datasourceParamVoList", "connectionRevision")), "Unexpected connection binding field");
            require(Objects.equals(binding.get("connectionRevision"), connectionRefs.get(entry.getKey())), "Connection revision differs from frozen task revision");
            Map<String,Object> rawDatasource = object(binding.get("datasource"), "datasource");
            require(id(rawDatasource.get("id"), "datasource.id") == connectionId, "Connection identity mismatch");
            DatasourceDto source = bean(DatasourceDto.class, rawDatasource);
            require(Set.of(16,18,8).contains(source.getDbType()), "Unsupported original datasource type");
            datasourceById.put(connectionId, source);
            supported(list(binding.get("datasourceParamVoList"), "connection parameters").isEmpty(), "Connection-level parameter overrides are not yet supported");
            datasourceParameters.put(connectionId, List.of());
        }
        for (Map<String,Object> node : nodes) validateNode(node);
        validateFields(nodes);
        Map<String,Object> logicalFlow = object(visual.get("flowVo"), "flowVo");
        supported(empty(logicalFlow.get("streamCombineType")) || "0".equals(text(logicalFlow.get("streamCombineType"))), "Combined streams are not supported in this profile");
        List<EtlParam> flowParameters = flowParams(visual.get("paramVoList"), flowId, bundle);
        long streamId = base, scheduleId = base + 1;
        Map<String,Object> nativeFlow = new LinkedHashMap<>(object(visual.get("flowVo"), "flowVo"));
        nativeFlow.putAll(map("id", flowId, "groupId", groupId, "userId", wireId(ctx, "ownerId"), "projectCode", ctx.get("projectCode"), "streamCombineType", 0, "flowStatus", 1, "flowIncrStatus", 0, "enableFlag", 1));
        nativeFlow.putIfAbsent("flowName", "task_" + ctx.get("businessTaskId")); nativeFlow.putIfAbsent("flowDisplayName", nativeFlow.get("flowName"));
        EtlFlow flow = bean(EtlFlow.class, nativeFlow);
        Map<Long,EtlAdapterData> dataByItem = new LinkedHashMap<>();
        Map<Long,List<EtlParam>> paramByItem = new LinkedHashMap<>();
        Map<Long,EtlModule> moduleById = new LinkedHashMap<>();
        Map<Long,EtlAdapter> adapterById = new LinkedHashMap<>();
        Map<Long,List<TransformFieldVo>> mappingByItem = new LinkedHashMap<>();
        Map<Long,List<DstTableFieldVo>> outputByNode = new LinkedHashMap<>();
        Map<Long,DstTableVo> outputTableByItem = new LinkedHashMap<>();
        Map<Long,List<DstTableFieldVo>> outputByTable = new LinkedHashMap<>();
        Map<Long,EtlSrcTable> sourceTableById = new LinkedHashMap<>();
        Map<Long,List<EtlSrcTableField>> sourceFieldsByTable = new LinkedHashMap<>();
        Map<Long,List<EtlParam>> datasourceParamsByItem = new LinkedHashMap<>();
        List<SrcTableUseVo> sourceUses = new ArrayList<>();
        List<StreamFieldVo> inputFields = new ArrayList<>(); List<ModuleDto> modules = new ArrayList<>();
        List<Object> mappings = new ArrayList<>(), nativeLinks = new ArrayList<>(), nativeStreamNodes = new ArrayList<>();
        Map<String,Object> nativeNodes = new LinkedHashMap<>();
        for (int i = 0; i < nodes.size(); i++) {
            Map<String,Object> node = parse(json(nodes.get(i))); String plugin = text(node.get("adapterName")); Properties properties = catalog(bundle, plugin);
            long offset = base + 16 + i * 16L, moduleId = offset, stepId = offset+1, itemId = offset+2, nodeId = offset+3, adapterId = offset+4;
            String visualId = text(node.get("id")); node.put("id", nodeId); node.put("moduleAdapterId", adapterId);
            Long connectionId = empty(node.get("dbId")) ? null : id(node.get("dbId"), "node.dbId");
            if (connectionId != null) {
                DatasourceDto source = lookup(datasourceById, connectionId);
                int expected = plugin.startsWith("postgresql") ? 16 : plugin.startsWith("kafka") ? 8 : 18;
                require(source.getDbType() == expected, "Selected connection type does not match the original plugin");
                datasourceParamsByItem.put(itemId, new ArrayList<>(lookup(datasourceParameters, connectionId)));
            } else datasourceParamsByItem.put(itemId, new ArrayList<>());
            node.put("moduleInstanceList", List.of(map("id", instanceId))); node.put("adapterVersion", properties.getProperty("version"));
            if (plugin.equals("fileOutput")) {
                Map<String,Object> config = object(node.get("adapterData"), "file output");
                config.put("localDir", output.toString());
                if ("local".equals(config.get("sourceType"))) config.putAll(map("absoluteDir", output.toString(), "relativeDir", "", "sourcePathType", 1));
            }
            if (OUTPUTS.contains(plugin)) {
                List<DstTableFieldVo> outputFields = savedOutputFields(node.get("dstTableFieldVoList"), streamId, nodeId);
                outputByNode.put(nodeId, outputFields);
                if (connectionId != null) {
                    Map<String,Object> table = new LinkedHashMap<>(object(node.get("dstTableVo"), "dstTableVo"));
                    require(empty(table.get("dbId")) || id(table.get("dbId"), "dstTableVo.dbId") == connectionId, "Output table connection mismatch");
                    table.putAll(map("id", offset+5, "dbId", connectionId, "streamId", streamId));
                    DstTableVo dto = bean(DstTableVo.class, table); outputTableByItem.put(itemId, dto); outputByTable.put(offset+5, outputFields);
                    node.put("dstTableVo", table);
                }
            }
            if (INPUTS.contains(plugin)) {
                if (plugin.equals("fileInput")) object(node.get("adapterData"), "file input").put("localDir", output.getParent().resolve("tmp/downloads").toString());
                for (Object raw : list(node.get("streamFieldVoList"), "input fields")) {
                    Map<String,Object> f = new LinkedHashMap<>(object(raw,"stream field"));
                    if (connectionId != null) {
                        f.remove("tableId"); // StreamFieldVo uses srcTableId, as emitted by the original browser.
                        f.put("srcTableId", offset+5);
                    }
                    f.put("streamId", streamId); inputFields.add(bean(StreamFieldVo.class,f));
                }
                if (connectionId != null && !list(node.get("srcTableUseVoList"), "source tables").isEmpty()) {
                    Map<String,Object> use = new LinkedHashMap<>(object(list(node.get("srcTableUseVoList"), "source tables").get(0), "source table"));
                    require(empty(use.get("srcDbId")) || id(use.get("srcDbId"), "srcDbId") == connectionId, "Input table connection mismatch");
                    use.putAll(map("id", offset+6, "tableId", offset+5, "streamId", streamId, "srcDbId", connectionId));
                    use.putIfAbsent("tableSchem", use.get("tableOwner")); use.putIfAbsent("tableAlias", ""); use.putIfAbsent("tableOrder",1); use.putIfAbsent("tableUseFlag",1); use.putIfAbsent("incrType",0); use.putIfAbsent("partFlag",0);
                    SrcTableUseVo dto = bean(SrcTableUseVo.class,use); sourceUses.add(dto);
                    Map<String,Object> table = new LinkedHashMap<>(use); table.put("id", offset+5); table.put("dbId",connectionId); table.putIfAbsent("tableViewFlag",0); table.putIfAbsent("tablePartedType",0);
                    sourceTableById.put(offset+5,bean(EtlSrcTable.class,table));
                    sourceFieldsByTable.put(offset+5,new DataGeneratorInputServiceImpl().getFieldList(inputFields,offset+5));
                    node.put("srcTableUseVoList",List.of(use));
                }
            }
            if (plugin.equals("fieldMapping")) {
                List<TransformFieldVo> transforms = new ArrayList<>();
                for (Object raw : list(node.get("transformFieldVoList"), "mapping fields")) transforms.add(bean(TransformFieldVo.class, raw));
                mappingByItem.put(itemId, transforms);
            }
            if (node.get("adapterData") != null) dataByItem.put(itemId, bean(EtlAdapterData.class, "itemId", itemId, "adapterDataJson", json(node.get("adapterData"))));
            paramByItem.put(itemId, savedAdapterParameters(node,params(node.get("adapterParamVoList"), plugin, itemId, 8, bundle, output),itemId,flowId));
            String name = text(node.get("stageName")); if (name.isEmpty()) name = properties.getProperty("adapterDisplayName") + "_" + visualId;
            ModuleDto module = bean(ModuleDto.class, "moduleId", moduleId, "flowId", flowId, "instanceId", instanceId, "moduleName", name, "adapterType", TYPES.get(plugin), "streamId", streamId, "stepId", stepId, "itemId", itemId, "nodeId", nodeId, "adapterId", adapterId, "adapterName", plugin, "adapterVersion", properties.getProperty("version"), "instanceHost", "127.0.0.1", "runStatus", 0);
            modules.add(module); nativeNodes.put(String.valueOf(nodeId), node);
            moduleById.put(moduleId, bean(EtlModule.class, "id", moduleId, "itemId", itemId, "visualNodeId", nodeId, "flowId", flowId, "moduleInstanceId", instanceId, "moduleAdapterId", adapterId, "datasourceId", connectionId, "adapterType", TYPES.get(plugin), "moduleName", name, "enableFlag", 1));
            adapterById.put(adapterId, bean(EtlAdapter.class, "id", adapterId, "groupId", groupId, "adapterName", plugin, "adapterDisplayName", properties.getProperty("adapterDisplayName"), "adapterType", TYPES.get(plugin), "languageType", com.hikvision.idatafusion.detl.common.LanguageType.JAVA.getType(), "dbTypeId", connectionId == null ? "" : text(lookup(datasourceById, connectionId).getDbType()), "adapterClassName", properties.getProperty("className"), "enableFlag", 1));
            mappings.add(map("visualNodeId", visualId, "nodeId", String.valueOf(nodeId), "moduleId", String.valueOf(moduleId), "stepId", String.valueOf(stepId), "itemId", String.valueOf(itemId), "streamId", String.valueOf(streamId), "adapterName", plugin));
            nativeStreamNodes.add(map("id", nodeId, "stepId", stepId, "itemId", itemId, "streamNodeModuleVoList", List.of(map("moduleId", moduleId, "moduleInstanceId", instanceId, "moduleAdapterId", adapterId))));
            if (i > 0) nativeLinks.add(map("source", base + 16 + (i-1)*16L + 3, "target", nodeId, "connectStatus", 0));
        }
        Map<String,Object> runtimeVisual = map("flowId", flowId, "flowVo", nativeFlow, "nodes", nativeNodes, "links", nativeLinks, "streamVoList", List.of(map("id", streamId, "upNodeId", flowId, "upNodeType", 2, "streamId", streamId, "streamNodeVoList", nativeStreamNodes)));
        EtlFlowHistory history = bean(EtlFlowHistory.class, "flowJson", json(runtimeVisual));
        EtlSchedule schedule = bean(EtlSchedule.class, "id", scheduleId, "nodeId", flowId, "flowIncrStatus", 1, "execType", 1, "enableFlag", 1);
        Object adapterData = provider("service.task.AdapterDataService", Map.of("findAdapterDataByItemId", a -> lookup(dataByItem, a[0])));
        Object params = provider("service.globalconfig.ParamManageService", Map.of("getParamConfigByNode", a -> {
            int type = Integer.parseInt(text(a[0])); if (type == 2) { key(a[1], flowId); return new ArrayList<>(flowParameters); }
            if (type == 9) return new ArrayList<>(lookup(datasourceParamsByItem,a[1]));
            require(type == 8, "Unsupported original parameter category"); return new ArrayList<>(lookup(paramByItem, a[1]));
        }));
        Object moduleDao = provider("mapper.EtlModuleDao", Map.of("selectByPrimaryKey", a -> lookup(moduleById, a[0]), "listFlowModules", a -> { key(a[0], flowId); return modules; }));
        Object flowDao = provider("mapper.EtlFlowMapper", Map.of("selectByPrimaryKey", a -> { key(a[0], flowId); return flow; }));
        DataGeneratorInputServiceImpl input = new DataGeneratorInputServiceImpl(); inject(input, "adapterDataService", adapterData); inject(input, "paramService", params);
        inject(input, "streamFieldService", provider("service.task.StreamFieldService", Map.of("findStreamField", a -> { key(a[0], streamId); return inputFields; })));
        FileOutputServiceImpl file = new FileOutputServiceImpl(); inject(file, "adapterDataService", adapterData); inject(file, "paramService", params); inject(file, "moduleDao", moduleDao);
        inject(file, "dstTableService", provider("service.task.DstTableService", Map.of("findDstTableByItemId", a -> { lookup(paramByItem, a[0]); return outputTableByItem.get(Long.valueOf(text(a[0]))); })));
        inject(file, "tableFieldDao", provider("mapper.EtlDstTableFieldDao", Map.of("getByNodeId", a -> { key(a[0], streamId); return lookup(outputByNode, a[1]); }, "getByTableId", a -> lookup(outputByTable,a[0]))));
        FieldMappingServiceImpl mapping = new FieldMappingServiceImpl(); inject(mapping, "paramService", params);
        inject(mapping, "streamDao", provider("mapper.EtlStreamDao", Map.of("selectByPrimaryKey", a -> { key(a[0], streamId); return bean(EtlStream.class, "id", streamId, "upNodeId", flowId); })));
        inject(mapping, "flowDao", flowDao);
        inject(mapping, "felManagerService", provider("service.globalconfig.FelManagerService", Map.of("selectThirdPartFel", a -> { require(Objects.equals(a[0], ctx.get("projectCode")), "Unexpected project metadata"); return List.of(); })));
        inject(mapping, "confDao", provider("mapper.EtlRunningConfDao", Map.of("findByConfigKey", a -> null)));
        inject(mapping, "transformFieldService", provider("service.task.TransformFieldService", Map.of("findTransformFieldByItemId", a -> lookup(mappingByItem, a[0]))));
        Object dataSourceService = provider("service.globalconfig.DataSourceManageService", Map.of(
            "getById", a -> lookup(datasourceById,a[0]), "getKerberosDatasourceByDataSourceId", a -> { lookup(datasourceById,a[0]); return null; }));
        Object srcUseService = provider("service.task.SrcTableUseService", Map.of(
            "findUseVoByStreamId", a -> { key(a[0],streamId); return sourceUses; },
            "findByFlowIdAndNodeId", a -> { key(a[0],flowId); return sourceUses; },
            "getSrcTableUseList", a -> { key(a[0],streamId); return sourceUses; }));
        Object tableDao = provider("mapper.EtlSrcTableDao", Map.of("selectByPrimaryKey", a -> lookup(sourceTableById,a[0])));
        Object streamFields = provider("service.task.StreamFieldService", Map.of("findStreamField", a -> { key(a[0],streamId); return inputFields; }));
        Object destinationService = provider("service.task.DstTableService", Map.of(
            "findDstTableByItemId", a -> outputTableByItem.get(Long.valueOf(text(a[0]))),
            "findDstTable", a -> { key(a[0],streamId); require(outputTableByItem.size()==1,"Missing output table");return outputTableByItem.values().iterator().next(); }));
        Object destinationFields = provider("service.task.DstTableFieldService", Map.of("findDstTableField", a -> { key(a[0],streamId);return lookup(outputByTable,a[1]); }));
        InputServiceImpl jdbcInput = new InputServiceImpl();
        for (AdapterService service : List.of(jdbcInput)) {
            inject(service,"adapterDataService",adapterData); inject(service,"paramService",params);inject(service,"moduleDao",moduleDao); inject(service,"dataSourceManageService",dataSourceService);
            inject(service,"srcTableUseService",srcUseService);inject(service,"etlSrcTableDao",tableDao); inject(service,"streamFieldService",streamFields);
            inject(service,"tableFieldManageService",provider("service.globalconfig.TableFieldManageService",Map.of("selectEtlSrcTableFieldListByTableId",a->lookup(sourceFieldsByTable,a[0]))));
            inject(service,"joinConditionService",provider("service.task.JoinConditionService",Map.of("findEtlCondition",a->List.of(),"findEtlJoin",a->List.of())));
        }
        OutputServiceImpl jdbcOutput = new OutputServiceImpl(); MqOutputServiceImpl mqOutput = new MqOutputServiceImpl();
        for (AdapterService service : List.of(jdbcOutput,mqOutput)) {
            inject(service,"adapterDataService",adapterData);inject(service,"paramService",params); inject(service,"dataSourceManageService",dataSourceService);
            inject(service,"dstTableService",destinationService);inject(service,"dstTableFieldService",destinationFields);
        }
        inject(mqOutput,"moduleDao",moduleDao);inject(mqOutput,"confDao",provider("mapper.EtlRunningConfDao",Map.of("findByConfigKey",a->null)));
        FileInputServiceImpl fileInput = new FileInputServiceImpl(); MqInputServiceImpl mqInput = new MqInputServiceImpl();
        for (AdapterService service : List.of(fileInput,mqInput)) {
            inject(service,"paramService",params);inject(service,"moduleDao",moduleDao);inject(service,"dataSourceManageService",dataSourceService);
            inject(service,"srcTableUseService",srcUseService);inject(service,"tableDao",tableDao);inject(service,"streamFieldService",streamFields);
        }
        inject(fileInput,"adapterDataService",adapterData);inject(fileInput,"scheduleDao",provider("mapper.EtlScheduleDao",Map.of("getByFlowId",a->{key(a[0],flowId);return schedule;})));
        inject(file,"dataSourceManageService",dataSourceService);
        List<AdapterService> generators = List.of(input, mapping, file, fileInput, mqInput, mqOutput, jdbcOutput);
        AdapterServiceFactory factory = new AdapterServiceFactory(); inject(factory, "adapterServiceList", generators);
        inject(factory,"input",jdbcInput);inject(factory,"output",jdbcOutput);
        CompilationSink sink = new CompilationSink(groupId, flowId);
        ZookeeperServiceImpl compiler = new ZookeeperServiceImpl(); inject(compiler, "etlFlowMapper", flowDao);
        inject(compiler, "etlScheduleDao", provider("mapper.EtlScheduleDao", Map.of("getByFlowId", a -> { key(a[0], flowId); return schedule; })));
        inject(compiler, "moduleDao", moduleDao);
        inject(compiler, "etlFlowHistoryDao", provider("mapper.EtlFlowHistoryDao", Map.of("selectByFlowId", a -> { key(a[0], flowId); return history; })));
        inject(compiler, "adapterMirrorService", provider("service.globalconfig.AdapterMirrorService", Map.of("findBasicMirrorAdapter", a -> lookup(adapterById, a[0]))));
        inject(compiler, "codeDao", provider("mapper.SysCodeDao", Map.of("getByParentAndCode", a -> { require("ADAPTER_REFERENCE_CONFIG".equals(a[0]) && PLUGINS.contains(text(a[1])), "Unexpected adapter reference lookup"); return null; })));
        inject(compiler, "groupAdapterManageService", provider("service.globalconfig.GroupAdapterManageService", Map.of("getAdapterSource", a -> { require(PLUGINS.contains(text(a[0])), "Unexpected plugin source lookup"); return 0; })));
        inject(compiler, "etlAdapterVersionDao", provider("mapper.EtlAdapterVersionDao", Map.of("getAllByAdapterIds", a -> { for (Object adapter : list(a[0], "adapter IDs")) lookup(adapterById, adapter); return List.of(); })));
        inject(compiler, "workspaceService", provider("service.workspace.WorkspaceService", Map.of("isDfp", a -> { key(a[0], groupId); return false; })));
        inject(compiler, "adapterServiceFactory", factory); inject(compiler, "paramManageService", params); inject(compiler, "zKutil", sink);
        inject(compiler, "flowService", provider("service.task.FlowService", Map.of("updatePushStatus", a -> { key(a[0], flowId); sink.publishIntent = true; return null; })));
        compiler.forcePushFlowDataToZk(flowId);
        require(sink.bytes != null && sink.publishIntent, "Original compiler did not emit the expected metadata");
        validateOriginalOutputs(sink.bytes, nodes);
        flow.setFlowIncrStatus(1);
        Object start = com.hikvision.idatafusion.detl.dto.task.control.FlowControlMessageDto.builder().flowId(flowId).type(1).scheduleOnce(schedule).flow(flow).offset(List.of()).execId(text(run.get("execId"))).build();
        byte[] scheduleBytes = com.hikvision.idatafusion.detl.utils.ObjectUtils.toUnderlineJsonString(start).getBytes(StandardCharsets.UTF_8);
        Object stop = com.hikvision.idatafusion.detl.dto.task.control.FlowControlMessageDto.builder().flowId(flowId).type(0).build();
        byte[] stopBytes = com.hikvision.idatafusion.detl.utils.ObjectUtils.toUnderlineJsonString(stop).getBytes(StandardCharsets.UTF_8);
        return map("ok", true, "protocolVersion", 1, "operation", "compile", "businessTaskId", ctx.get("businessTaskId"), "revision", ctx.get("revision"), "ownerId", ctx.get("ownerId"), "flowId", String.valueOf(flowId), "workspaceId", String.valueOf(groupId), "instanceId", String.valueOf(instanceId), "execId", run.get("execId"), "snapshotSha256", snapshotHash,
            "flowjson", map("base64", Base64.getEncoder().encodeToString(sink.bytes), "sha256", sha(sink.bytes)),
            "schedule", map("base64", Base64.getEncoder().encodeToString(scheduleBytes), "sha256", sha(scheduleBytes)),
            "stop", map("base64", Base64.getEncoder().encodeToString(stopBytes), "sha256", sha(stopBytes)),
            "metadataSnapshot", map("runtimeVisual", runtimeVisual, "modules", modules, "metadataIdBase", String.valueOf(base), "allocatedThrough", String.valueOf(base + 16 + nodes.size()*16L - 1), "outputsDir", output.toString(), "connectionBindings", run.getOrDefault("connectionBindings",List.of())), "nodeMappings", mappings, "diagnostics", List.of());
    }

    public static void main(String[] args) throws Exception {
        PrintStream protocol = System.out; System.setOut(System.err);
        try {
            require(args.length == 1, "Expected bundle root argument");
            byte[] input = System.in.readNBytes(MAX_BYTES + 1); require(input.length <= MAX_BYTES, "Request exceeds 8 MiB");
            Map<String,Object> request = parse(new String(input, StandardCharsets.UTF_8));
            String operation = text(request.get("operation"));
            require(Set.of("prepare", "compile", "schedule", "connectionForm", "connectionUrl").contains(operation), "Unknown operation");
            @SuppressWarnings("unchecked") Map<String,Object> result = operation.startsWith("connection")
                ? (Map<String,Object>)Class.forName("OriginalConnectionForms").getMethod("handle",Map.class).invoke(null,request)
                : operation.equals("prepare") ? prepare(request) : operation.equals("schedule") ? schedule(request) : compile(request, Path.of(args[0]));
            protocol.println(json(result));
        } catch (Throwable failure) {
            // Do not expose vendor exception messages or stack traces: these may contain task data.
            Rejected rejected = failure instanceof Rejected ? (Rejected)failure : null;
            Map<String,Object> response = map("ok", false, "protocolVersion", 1, "code", rejected == null ? "ORIGINAL_COMPILER_FAILED" : rejected.code,
                "message", rejected == null ? "Original configuration generator failed" : rejected.getMessage());
            if (rejected != null && rejected.reason != null) response.put("reason", rejected.reason);
            protocol.println(json(response));
            System.err.println("compiler failure type=" + failure.getClass().getName());
            System.exit(2);
        }
    }
}
