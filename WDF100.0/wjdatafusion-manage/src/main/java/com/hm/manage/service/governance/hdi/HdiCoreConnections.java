package com.hm.manage.service.governance.hdi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hm.manage.service.support.CredentialCryptoService;
import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Owner-scoped management adapter for original HDI datasource forms, not an ETL implementation. */
@Service
public class HdiCoreConnections {
    public record Resolved(ObjectNode connections, ArrayNode connectionBindings, List<String> redactionValues) { }
    private record Current(long id, long owner, long revision, String name, int type, boolean enabled, int testStatus, long testedRevision, String testMessage, Instant testedAt) {
        Current(long id, long owner, long revision, String name, int type, boolean enabled) { this(id, owner, revision, name, type, enabled, -1, 0, "", null); }
    }
    private record Version(ObjectNode datasource, String encryptedSecret) { }
    private record Normalized(ObjectNode datasource, String secret) { }
    private record Endpoint(String host, int port) { String key() { return host + ":" + port; } }
    private static final Set<String> CONNECTION_PLUGINS = Set.of("postgresqlInput", "postgresqlOutput", "fileInput", "fileOutput", "kafkaInput", "kafkaV2Output");
    private static final Set<String> FORM_FIELDS = Set.of("connectType", "databaseIp", "databasePort", "databaseSid", "databaseUsername", "databasePassword", "dbUrl", "address", "certification", "authentication", "Host", "Port", "FolderPath", "ConnectMode", "KerberosId", "ServerName", "DomainName", "JaasConfig", "ext1", "ext2", "ext3");
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final CredentialCryptoService crypto;
    private final HdiCoreKeys keys;
    private final HdiCoreConnectionProperties policy;
    private final Semaphore metadataPermits = new Semaphore(4);

    @Autowired public HdiCoreConnections(@Qualifier("masterDataSource") DataSource source, CredentialCryptoService crypto,
            HdiCoreKeys keys, HdiCoreConnectionProperties policy) {
        this(new JdbcTemplate(source), new DataSourceTransactionManager(source), crypto, keys, policy);
    }
    HdiCoreConnections(JdbcTemplate jdbc, PlatformTransactionManager manager, CredentialCryptoService crypto,
            HdiCoreKeys keys, HdiCoreConnectionProperties policy) {
        this.jdbc = jdbc; this.tx = new TransactionTemplate(manager); this.crypto = crypto; this.keys = keys; this.policy = policy;
    }

    /** Original module 79553 multipart fields, with parameters decoded by the HTTP adapter as JSON. */
    public synchronized ObjectNode saveForm(long owner, JsonNode form) {
        requireOwner(owner); object(form);
        Long id = optionalId(form.get("id"));
        return tx.execute(status -> {
            Current previous = id == null ? null : current(owner, id, true);
            if (previous != null && revision(form) != previous.revision) throw fault(409, "连接已被修改，请重新打开后保存");
            if (previous == null && count(owner) >= 100) throw fault(422, "当前最多保存 100 个连接");
            String name = text(form.path("datasourceName").asText(form.path("name").asText()), 128, "连接名称");
            if (nameExists(owner, name, id)) throw fault(409, "连接名称已存在");
            Version old = previous == null ? null : version(owner, previous.id, previous.revision);
            Normalized normalized = normalize(form, old);
            if (previous != null && normalized.datasource.path("dbType").intValue() != previous.type) throw fault(422, "不能更改已有连接类型");
            long next = previous == null ? 1 : previous.revision + 1;
            Instant now = Instant.now(); long connectionId;
            if (previous == null) {
                GeneratedKeyHolder generated = new GeneratedKeyHolder();
                jdbc.update(connection -> {
                    var statement = connection.prepareStatement("INSERT INTO dg_hdi_connection(owner_id,revision,name,db_type,enabled,deleted,created_at,updated_at) VALUES(?,?,?,?,1,0,?,?)", java.sql.Statement.RETURN_GENERATED_KEYS);
                    statement.setLong(1, owner); statement.setLong(2, next); statement.setString(3, name); statement.setInt(4, normalized.datasource.path("dbType").intValue());
                    statement.setTimestamp(5, Timestamp.from(now)); statement.setTimestamp(6, Timestamp.from(now)); return statement;
                }, generated);
                if (generated.getKey() == null) throw fault(503, "连接保存失败"); connectionId = generated.getKey().longValue();
            } else {
                connectionId = previous.id;
                if (jdbc.update("UPDATE dg_hdi_connection SET revision=?,name=?,updated_at=? WHERE connection_id=? AND owner_id=? AND revision=? AND deleted=0",
                        next, name, Timestamp.from(now), connectionId, owner, previous.revision) != 1) throw fault(409, "连接已被修改，请重新打开后保存");
            }
            ObjectNode data = normalized.datasource.put("id", connectionId).put("name", name);
            jdbc.update("INSERT INTO dg_hdi_connection_revision(connection_id,revision,owner_id,datasource_json,encrypted_secret,created_at) VALUES(?,?,?,?,?,?)",
                connectionId, next, owner, data.toString(), crypto.encrypt(normalized.secret), Timestamp.from(now));
            return publicView(new Current(connectionId, owner, next, name, data.path("dbType").intValue(), true), data, !normalized.secret.isEmpty());
        });
    }

    public ObjectNode get(long owner, long id) { Current row = current(owner, id, false); Version v = version(owner, id, row.revision); return publicView(row, v.datasource, !v.encryptedSecret.isEmpty()); }
    public ObjectNode formParameters(long owner, long id) {
        ObjectNode data = get(owner, id), result = HdiProtocol.JSON.createObjectNode(); int type = data.path("dbType").asInt();
        for (String key : List.of("databaseUsername", "databasePassword", "dbUrl")) result.set(key, data.path(key));
        if (type == 18) result.set("Host", data.path("databaseIp"));
        if (type == 18) { result.set("Port", data.path("databasePort")); result.set("FolderPath", data.path("databaseSid")); result.set("ConnectMode", data.path("ext1")); result.put("ext2", "ftp"); }
        else { result.put("connectType", "host"); result.set("address", data.path("address"));
            if (type == 16) result.set("databaseSid", data.path("databaseSid")); else result.put("certification", "none").put("authentication", "none"); }
        return result;
    }
    public List<ObjectNode> list(long owner, JsonNode filter) {
        requireOwner(owner); if (filter != null && !filter.isNull()) object(filter);
        List<ObjectNode> rows = new ArrayList<>(); String keyword = filter == null ? "" : filter.path("name").asText(filter.path("datasourceName").asText(filter.path("keyword").asText("")));
        if (keyword.length() > 128) throw fault(400, "连接查询过长");
        for (Current row : jdbc.query("SELECT connection_id,owner_id,revision,name,db_type,enabled,test_status,tested_revision,test_message,tested_at FROM dg_hdi_connection WHERE owner_id=? AND deleted=0 ORDER BY updated_at DESC,connection_id DESC", (r,n) -> row(r), owner)) {
            if (!row.name.toLowerCase(java.util.Locale.ROOT).contains(keyword.toLowerCase(java.util.Locale.ROOT))) continue;
            if (filter != null && filter.hasNonNull("datasourceType") && !filter.path("datasourceType").asText().isBlank() && !filter.path("datasourceType").asText().equalsIgnoreCase(typeName(row.type))) continue;
            if (filter != null && filter.hasNonNull("dbType") && !filter.path("dbType").asText().isBlank() && !filter.path("dbType").asText().equals("-1") && row.type != filter.path("dbType").asInt()) continue;
            Version v = version(owner, row.id, row.revision); rows.add(publicView(row, v.datasource, !v.encryptedSecret.isEmpty()));
        }
        return rows;
    }
    public Map<String, Object> page(long owner, JsonNode filter, int page, int size) {
        if (page < 1 || size < 1 || size > 200) throw fault(400, "分页参数无效");
        List<ObjectNode> rows = list(owner, filter); int from = (int)Math.min(rows.size(), (long)(page - 1) * size);
        return Map.of("list", rows.subList(from, Math.min(rows.size(), from + size)), "total", rows.size());
    }
    public boolean nameExists(long owner, String name, Long except) {
        requireOwner(owner); if (name == null || name.length() > 128) throw fault(400, "连接名称无效");
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM dg_hdi_connection WHERE owner_id=? AND deleted=0 AND name=? AND connection_id<>?", Integer.class, owner, name, except == null ? -1L : except);
        return count != null && count > 0;
    }
    public int count(long owner) { requireOwner(owner); return jdbc.queryForObject("SELECT COUNT(*) FROM dg_hdi_connection WHERE owner_id=? AND deleted=0", Integer.class, owner); }
    public void delete(long owner, long id, long expectedRevision) {
        tx.executeWithoutResult(status -> { Current row = current(owner, id, true);
            if (row.revision != expectedRevision) throw fault(409, "连接已被修改，请刷新后删除");
            jdbc.update("UPDATE dg_hdi_connection SET deleted=1,enabled=0,updated_at=? WHERE connection_id=? AND owner_id=? AND revision=?", Timestamp.from(Instant.now()), id, owner, expectedRevision);
        });
    }

    public ObjectNode freezeReferences(long owner, JsonNode visual) {
        ObjectNode refs = HdiProtocol.JSON.createObjectNode();
        for (long id : referenceIds(visual)) { Current row = current(owner, id, false); requireEnabled(row); refs.put(Long.toString(id), Long.toString(row.revision)); }
        return refs;
    }
    public Resolved resolveForRun(long owner, JsonNode visual, JsonNode refs) {
        object(refs); Set<Long> ids = referenceIds(visual);
        if (refs.size() != ids.size()) throw fault(422, "任务冻结的连接引用不完整");
        ObjectNode connections = HdiProtocol.JSON.createObjectNode(); ArrayNode bindings = HdiProtocol.JSON.createArrayNode(); Set<String> secrets = new LinkedHashSet<>();
        for (long id : ids) {
            Current row = current(owner, id, false); requireEnabled(row); long frozen = positive(refs.get(Long.toString(id)), "连接版本");
            Version v = version(owner, id, frozen); String secret = crypto.decrypt(v.encryptedSecret);
            ObjectNode data = v.datasource.deepCopy(); data.put("databasePassword", secret);
            ObjectNode value = connections.putObject(Long.toString(id)); value.set("datasource", data); value.putArray("datasourceParamVoList"); value.put("connectionRevision", Long.toString(frozen));
            bindings.add(binding(owner, id, frozen, data)); if (!secret.isEmpty()) secrets.add(secret);
        }
        Set<String> targets = new LinkedHashSet<>();
        for (JsonNode binding : bindings) {
            for (JsonNode endpoint : binding.path("endpoints")) targets.add(endpoint.path("host").asText() + ":" + endpoint.path("port").asInt());
            if (binding.has("passivePorts")) for (int port = binding.path("passivePorts").path("start").asInt(); port <= binding.path("passivePorts").path("end").asInt(); port++) targets.add(binding.path("endpoints").get(0).path("host").asText() + ":" + port);
        }
        if (targets.size() > 160) throw fault(422, "任务连接端点数量超过执行器范围");
        return new Resolved(connections, bindings, secrets.stream().sorted(Comparator.comparingInt(String::length).reversed()).toList());
    }

    public boolean testForm(long owner, JsonNode form) {
        requireOwner(owner); Long id = optionalId(form.get("id")); Version old = null;
        if (id != null) { Current current = current(owner, id, false); if (form.hasNonNull("_platformRevision") || form.hasNonNull("revision")) { if (revision(form) != current.revision) throw fault(409, "连接已被修改，请重新打开后测试"); } old = version(owner, id, current.revision); }
        Normalized value = normalize(form, old); return test(value.datasource, value.secret);
    }
    public boolean testById(long owner, long id) {
        Current row = current(owner, id, false); requireEnabled(row); Version v = version(owner, id, row.revision);
        try { test(v.datasource, crypto.decrypt(v.encryptedSecret)); recordTest(row, 1, null); return true; }
        catch (HdiTaskFault failure) { if (failure.status() != 429) recordTest(row, 0, failure.getMessage()); throw failure; }
    }
    private void recordTest(Current row, int status, String message) {
        jdbc.update("UPDATE dg_hdi_connection SET test_status=?,tested_revision=?,test_message=?,tested_at=? WHERE connection_id=? AND owner_id=? AND revision=? AND deleted=0",
            status, row.revision, message, Timestamp.from(Instant.now()), row.id, row.owner, row.revision);
    }
    private boolean test(ObjectNode data, String secret) {
        acquire();
        try {
            int type = data.path("dbType").intValue(); binding(1, 1, 1, data);
            if (type == 16) {
                try (Connection connection = pg(data, secret); var statement = connection.prepareStatement("SELECT 1")) {
                    statement.setQueryTimeout(5); try (ResultSet result = statement.executeQuery()) { if (!result.next() || result.getInt(1) != 1) throw fault(422, "数据库连接测试失败"); }
                    connection.rollback();
                }
            } else if (type == 18) {
                FTPClient ftp = new FTPClient(); ftp.setConnectTimeout(3000); ftp.setDefaultTimeout(5000);
                try { ftp.connect(data.path("databaseIp").asText(), data.path("databasePort").asInt()); ftp.setSoTimeout(5000);
                    if (!ftp.login(data.path("databaseUsername").asText(), secret) || !ftp.sendNoOp()) throw fault(422, "FTP连接或账号验证失败");
                    ftp.logout();
                } finally { if (ftp.isConnected()) ftp.disconnect(); }
            } else {
                Properties config = new Properties(); config.put("bootstrap.servers", data.path("address").asText()); config.put("request.timeout.ms", "4000"); config.put("default.api.timeout.ms", "6000"); config.put("retries", "0");
                AdminClient client = AdminClient.create(config);
                try {
                    Set<String> allowed = new LinkedHashSet<>(); for (JsonNode endpoint : binding(1, 1, 1, data).path("endpoints")) allowed.add(endpoint.path("host").asText() + ":" + endpoint.path("port").asInt());
                    var nodes = client.describeCluster().nodes().get(6, TimeUnit.SECONDS); if (nodes.isEmpty()) throw fault(422, "Kafka未返回可用节点");
                    for (var node : nodes) if (!allowed.contains(node.host() + ":" + node.port())) throw fault(422, "Kafka公布的节点不在已配置范围内");
                } finally { client.close(java.time.Duration.ofSeconds(1)); }
            }
            return true;
        } catch (HdiTaskFault error) { throw error; }
        catch (Exception error) { throw fault(422, "连接测试失败，请核对地址、账号、网络范围及服务状态"); }
        finally { metadataPermits.release(); }
    }

    public List<Map<String, Object>> schemas(long owner, long id) {
        return metadata(owner, id, connection -> {
            List<Map<String, Object>> result = new ArrayList<>();
            try (ResultSet rows = connection.getMetaData().getSchemas()) {
                while (rows.next()) { String name = rows.getString("TABLE_SCHEM"); if (systemSchema(name)) continue;
                    if (result.size() >= 200) throw fault(422, "数据库模式超过当前浏览范围"); result.add(Map.of("schemaId", name, "schemaCode", name, "schemaName", name)); }
            } return result;
        });
    }
    public Map<String, Object> tables(long owner, long id, JsonNode query) {
        object(query); Current source = current(owner, id, false); requireEnabled(source); if (source.type == 8) return topics(owner, id, query);
        String requestedSchema = query.path("schemaCode").asText(query.path("tableSchem").asText("public")); String schema = requestedSchema.isBlank() ? "public" : requestedSchema; identifier(schema);
        String keyword = query.path("tableName").asText(""); if (keyword.length() > 128) throw fault(400, "表名称过长");
        int page = query.path("pageNo").asInt(1), size = query.path("pageSize").asInt(100); if (page < 1 || size < 1 || size > 200) throw fault(400, "分页参数无效");
        return metadata(owner, id, connection -> {
            List<Map<String, Object>> all = new ArrayList<>();
            try (ResultSet rows = connection.getMetaData().getTables(null, pattern(schema), "%", new String[]{"TABLE", "VIEW"})) {
                while (rows.next()) { String name = rows.getString("TABLE_NAME"); if (!name.contains(keyword)) continue; if (all.size() >= 1000) throw fault(422, "表数量过多，请缩小模式范围");
                    Map<String,Object> item = new LinkedHashMap<>(); item.put("dbId", id); item.put("id", registerTable(owner, id, schema, name, "TABLE")); item.put("dbTableName", name); item.put("tableName", name); item.put("tableDisplayName", name); item.put("tableSchema", schema); item.put("schemaCode", schema); item.put("tableSchem", schema); item.put("tableCat", rows.getString("TABLE_CAT")); item.put("tableCode", name); item.put("tableOwner", schema); item.put("tableRemarks", rows.getString("REMARKS")); all.add(item); }
            }
            int from = (int)Math.min(all.size(), (long)(page - 1) * size); return Map.of("list", all.subList(from, Math.min(all.size(), from + size)), "total", all.size());
        });
    }
    public Map<String, Object> columns(long owner, long id, JsonNode query) {
        object(query); Current source = current(owner, id, false); requireEnabled(source);
        if (source.type == 8) return Map.of("columns", List.of(), "primaryKeys", List.of(), "targetTableName", query.path("tableName").asText());
        String requestedSchema = query.path("tableSchem").asText(query.path("tableSchema").asText("public")); String schema = requestedSchema.isBlank() ? "public" : requestedSchema, table = query.path("tableName").asText(); identifier(schema); identifier(table);
        return metadata(owner, id, connection -> {
            List<Map<String,Object>> columns = new ArrayList<>(), primary = new ArrayList<>();
            try (ResultSet rows = connection.getMetaData().getColumns(null, pattern(schema), pattern(table), "%")) {
                while (rows.next()) { if (columns.size() >= 512) throw fault(422, "表字段超过512个"); Map<String,Object> c = new LinkedHashMap<>();
                    c.put("columnName", rows.getString("COLUMN_NAME")); c.put("typeName", rows.getString("TYPE_NAME")); c.put("dataType", rows.getInt("DATA_TYPE")); c.put("columnSize", rows.getInt("COLUMN_SIZE")); c.put("decimalDigits", rows.getInt("DECIMAL_DIGITS")); c.put("isNullable", rows.getString("IS_NULLABLE")); c.put("isAutoincrement", rows.getString("IS_AUTOINCREMENT")); c.put("remarks", rows.getString("REMARKS")); c.put("ordinalPosition", rows.getInt("ORDINAL_POSITION")); columns.add(c); }
            }
            try (ResultSet rows = connection.getMetaData().getPrimaryKeys(null, schema, table)) { while (rows.next()) primary.add(Map.of("columnName", rows.getString("COLUMN_NAME"), "keySeq", rows.getInt("KEY_SEQ"))); }
            return Map.of("columns", columns, "primaryKeys", primary, "targetTableName", table);
        });
    }
    private synchronized long registerTable(long owner, long connection, String schema, String name, String kind) {
        String key = HdiTaskStore.sha256((schema + "\0" + name + "\0" + kind).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        List<Long> found = jdbc.query("SELECT table_id FROM dg_hdi_connection_table WHERE connection_id=? AND owner_id=? AND object_key=?", (r,n) -> r.getLong(1), connection, owner, key);
        if (!found.isEmpty()) return found.get(0);
        GeneratedKeyHolder holder = new GeneratedKeyHolder();
        jdbc.update(c -> { var statement = c.prepareStatement("INSERT INTO dg_hdi_connection_table(connection_id,owner_id,object_key,schema_name,table_name,table_kind,created_at) VALUES(?,?,?,?,?,?,?)", java.sql.Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, connection); statement.setLong(2, owner); statement.setString(3, key); statement.setString(4, schema); statement.setString(5, name); statement.setString(6, kind); statement.setTimestamp(7, Timestamp.from(Instant.now())); return statement; }, holder);
        if (holder.getKey() == null) throw fault(503, "表标识登记失败"); return holder.getKey().longValue();
    }
    public Map<String,Object> getTable(long owner, long tableId) {
        requireOwner(owner); List<Map<String,Object>> records = jdbc.query("SELECT connection_id,schema_name,table_name,table_kind FROM dg_hdi_connection_table WHERE table_id=? AND owner_id=?", (r,n) -> Map.<String,Object>of("dbId", r.getLong(1), "tableSchema", r.getString(2), "tableName", r.getString(3), "kind", r.getString(4)), tableId, owner);
        if (records.size() != 1) throw fault(404, "数据表不存在或无权访问"); Map<String,Object> result = new LinkedHashMap<>(records.get(0));
        long id = (Long)result.get("dbId"); String schema = (String)result.get("tableSchema"), name = (String)result.get("tableName");
        current(owner, id, false); result.put("id", tableId); result.put("dbTableName", name); result.put("tableDisplayName", name); result.put("tableId", tableId); result.put("schemaCode", schema); result.put("tableSchem", schema); result.put("tableCode", name); result.put("tableOwner", schema);
        if (result.remove("kind").equals("TOPIC")) { result.put("fieldUpdateRequestList", List.of()); return result; }
        Map<String,Object> metadata = columns(owner, id, HdiProtocol.JSON.createObjectNode().put("tableSchem", schema).put("tableName", name));
        List<?> columns = (List<?>)metadata.get("columns"), primary = (List<?>)metadata.get("primaryKeys"); List<Map<String,Object>> fields = new ArrayList<>();
        if (columns.isEmpty()) throw fault(404, "数据表不存在或没有可见字段");
        for (Object value : columns) {
            Map<?,?> column = (Map<?,?>)value; Map<String,Object> field = new LinkedHashMap<>();
            field.put("tableId", tableId); field.put("columnName", column.get("columnName")); field.put("columnDisplayName", column.get("columnName"));
            field.put("columnDataType", String.valueOf(column.get("dataType"))); field.put("columnTypeName", column.get("typeName")); field.put("columnSize", column.get("columnSize")); field.put("decimalDigits", column.get("decimalDigits"));
            field.put("columnNullable", column.get("isNullable")); field.put("columnAutoincrement", column.get("isAutoincrement")); field.put("columnRemarks", column.get("remarks"));
            field.put("columnIsPrimary", primary.stream().anyMatch(k -> java.util.Objects.equals(((Map<?,?>)k).get("columnName"), column.get("columnName"))) ? "YES" : "NO"); fields.add(field);
        }
        result.put("fieldUpdateRequestList", fields); return result;
    }
    private Map<String,Object> topics(long owner, long id, JsonNode query) {
        Current row = current(owner, id, false); requireEnabled(row); Version stored = version(owner, id, row.revision); ObjectNode bound = binding(owner, id, row.revision, stored.datasource);
        String keyword = query.path("tableName").asText(""); if (keyword.length() > 249) throw fault(400, "主题名称过长");
        int page = query.path("pageNo").asInt(1), size = query.path("pageSize").asInt(100); if (page < 1 || size < 1 || size > 200) throw fault(400, "分页参数无效");
        acquire(); AdminClient client = null;
        try {
            Properties config = new Properties(); config.put("bootstrap.servers", stored.datasource.path("address").asText()); config.put("request.timeout.ms", "4000"); config.put("default.api.timeout.ms", "6000"); config.put("retries", "0"); client = AdminClient.create(config);
            Set<String> allowed = new LinkedHashSet<>(); for (JsonNode endpoint : bound.path("endpoints")) allowed.add(endpoint.path("host").asText() + ":" + endpoint.path("port").asInt());
            for (var node : client.describeCluster().nodes().get(6, TimeUnit.SECONDS)) if (!allowed.contains(node.host() + ":" + node.port())) throw fault(422, "Kafka公布的节点不在已配置范围内");
            var names = client.listTopics().names().get(6, TimeUnit.SECONDS); if (names.size() > 1000) throw fault(422, "Kafka主题超过当前浏览范围");
            List<Map<String,Object>> rows = new ArrayList<>();
            for (String name : names.stream().sorted().toList()) if (name.contains(keyword)) { Map<String,Object> table = new LinkedHashMap<>(); table.put("id", registerTable(owner, id, "", name, "TOPIC")); table.put("dbId", id); table.put("tableName", name); table.put("dbTableName", name); table.put("tableDisplayName", name); table.put("tableCode", name); table.put("tableSchema", ""); table.put("tableSchem", ""); table.put("schemaCode", ""); rows.add(table); }
            int from = (int)Math.min(rows.size(), (long)(page - 1) * size); return Map.of("list", rows.subList(from, Math.min(rows.size(), from + size)), "total", rows.size());
        } catch (HdiTaskFault error) { throw error; }
        catch (Exception error) { throw fault(422, "Kafka主题读取失败，请检查连接和节点配置"); }
        finally { if (client != null) client.close(java.time.Duration.ofSeconds(1)); metadataPermits.release(); }
    }
    private interface Metadata<T> { T read(Connection connection) throws Exception; }
    private <T> T metadata(long owner, long id, Metadata<T> action) {
        Current row = current(owner, id, false); requireEnabled(row); if (row.type != 16) throw fault(422, "此连接不提供关系表元数据");
        Version v = version(owner, id, row.revision); binding(owner, id, row.revision, v.datasource); acquire();
        try (Connection connection = pg(v.datasource, crypto.decrypt(v.encryptedSecret))) { T result = action.read(connection); connection.rollback(); return result; }
        catch (HdiTaskFault error) { throw error; }
        catch (Exception error) { throw fault(422, "表结构读取失败，请核对连接和访问权限"); }
        finally { metadataPermits.release(); }
    }
    private static Connection pg(ObjectNode d, String secret) throws Exception {
        Properties config = new Properties(); config.setProperty("user", d.path("databaseUsername").asText()); config.setProperty("password", secret);
        config.setProperty("connectTimeout", "3"); config.setProperty("socketTimeout", "8"); config.setProperty("cancelSignalTimeout", "3"); config.setProperty("readOnly", "true"); config.setProperty("readOnlyMode", "always");
        Connection connection = DriverManager.getConnection(d.path("dbUrl").asText(), config);
        try { connection.setReadOnly(true); connection.setAutoCommit(false); return connection; } catch (Exception error) { connection.close(); throw error; }
    }

    private Normalized normalize(JsonNode input, Version previous) {
        object(input); JsonNode parameters = input.has("parameters") ? input.get("parameters") : input; object(parameters);
        String nativeType = input.path("datasourceType").asText(""); int type = input.path("dbType").asInt(0);
        if (type == 0) type = switch (nativeType) { case "PostgreSQL", "Postgresql" -> 16; case "FTP" -> 18; case "Kafka" -> 8; default -> throw fault(422, "当前仅支持PostgreSQL、FTP和Kafka连接"); };
        if (!Set.of(16,18,8).contains(type)) throw fault(422, "当前仅支持PostgreSQL、FTP和Kafka连接");
        if (!nativeType.isBlank() && !nativeType.equalsIgnoreCase(typeName(type))) throw fault(422, "连接类型不一致");
        if (input.has("parameters")) parameters.fieldNames().forEachRemaining(key -> { if (!FORM_FIELDS.contains(key)) throw fault(422, "当前连接参数尚未开放：" + key); });
        ObjectNode data = HdiProtocol.JSON.createObjectNode(); data.put("dbType", type).put("inOutFlag", 2).put("enableFlag", 1).put("folderId", -1).put("sourceFlag", 0).put("datasourceType", typeName(type)).put("datasourceVersion", input.path("datasourceVersion").asText(type == 16 ? "9.x" : type == 18 ? "1.0" : "2.x"));
        String expectedVersion = type == 16 ? "9.x" : type == 18 ? "1.0" : "2.x"; if (data.path("datasourceVersion").asText().isBlank()) data.put("datasourceVersion", expectedVersion); if (!data.path("datasourceVersion").asText().equals(expectedVersion)) throw fault(422, "原连接插件版本无效");
        if (optionalId(input.get("folderId")) != null && input.path("folderId").asLong() != -1) throw fault(422, "当前连接保存到平台根目录");
        String raw = parameters.path("databasePassword").asText(""); String secret = raw;
        if (!raw.isEmpty() && !raw.equals("******")) secret = decryptFormPassword(raw);
        if (secret.isEmpty() || secret.equals("******")) secret = previous == null ? "" : crypto.decrypt(previous.encryptedSecret);
        if (secret.length() > 4096 || secret.indexOf('\0') >= 0 || type != 8 && secret.isEmpty()) throw fault(422, "请填写连接密码");
        String host = parameters.path("Host").asText(parameters.path("databaseIp").asText("")), port = parameters.path("Port").asText(parameters.path("databasePort").asText(""));
        String db = parameters.path("FolderPath").asText(parameters.path("databaseSid").asText("")), suppliedUrl = parameters.path("dbUrl").asText("");
        if (type == 8) {
            String certification = parameters.path("certification").asText("");
            if (!Set.of("", "none").contains(parameters.path("authentication").asText(""))) throw fault(422, "当前Kafka连接不支持Ranger身份服务");
            if (!secret.isEmpty() || !Set.of("", "none", "NONE", "PLAINTEXT", "0").contains(certification)) throw fault(422, "当前Kafka连接开放无认证模式，认证模式需单独验收");
            String address = parameters.path("address").asText(""); if (address.isBlank() && suppliedUrl.startsWith("kafka2://")) address = suppliedUrl.substring("kafka2://".length());
            List<Endpoint> endpoints = parseAddresses(address); String canonical = String.join(",", endpoints.stream().map(Endpoint::key).toList());
            if (!suppliedUrl.isBlank() && !suppliedUrl.equals("kafka2://" + canonical)) throw fault(422, "Kafka地址与URL不一致");
            data.put("address", canonical).put("dbUrl", "kafka2://" + canonical).put("databaseIp", endpoints.get(0).host).put("databasePort", Integer.toString(endpoints.get(0).port)).put("databaseSid", "").put("databaseUsername", "").put("ext1", "").put("ext2", "").put("ext3", "");
        } else {
            if (type == 16 && parameters.hasNonNull("address") && !parameters.path("address").asText().isBlank()) { List<Endpoint> hosts = parseAddresses(parameters.path("address").asText()); if (hosts.size() != 1) throw fault(422, "当前PostgreSQL连接使用单一地址"); host = hosts.get(0).host; port = Integer.toString(hosts.get(0).port); }
            if ((host.isBlank() || port.isBlank() || type == 16 && db.isBlank()) && !suppliedUrl.isBlank()) {
                try { URI uri = URI.create(type == 16 ? suppliedUrl.substring(5) : suppliedUrl); if (uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) throw new IllegalArgumentException();
                    host = uri.getHost(); port = Integer.toString(uri.getPort()); if (type == 16) db = uri.getPath().substring(1);
                } catch (Exception error) { throw fault(422, "连接URL格式无效"); }
            }
            Endpoint endpoint = endpoint(host, port); data.put("databaseIp", endpoint.host).put("databasePort", Integer.toString(endpoint.port));
            data.put("databaseUsername", text(parameters.path("databaseUsername").asText(), 128, "连接账号"));
            if (type == 16) { identifier(db); data.put("databaseSid", db).put("address", endpoint.key()); data.put("dbUrl", "jdbc:postgresql://" + endpoint.key() + "/" + db); data.put("ext1", "").put("ext2", "").put("ext3", ""); }
            else { String protocol = parameters.path("ext2").asText("ftp"); if (!protocol.equalsIgnoreCase("ftp")) throw fault(422, "当前FTP连接开放原FTP模式");
                String mode = parameters.path("ConnectMode").asText(parameters.path("ext1").asText("1")); if (!mode.equals("1")) throw fault(422, "当前FTP连接使用被动模式");
                if (!db.isEmpty() && (!db.startsWith("/") || db.contains("..") || db.length() > 512)) throw fault(422, "FTP根目录无效");
                data.put("databaseSid", db).put("dbUrl", "ftp://" + endpoint.key() + (db.isEmpty() ? "" : "/" + db)).put("ext1", "1").put("ext2", "ftp").put("ext3", ""); }
            if (!suppliedUrl.isBlank() && !(type == 18 && suppliedUrl.equals("ftp://")) && !suppliedUrl.equals(data.path("dbUrl").asText())) throw fault(422, "连接地址与URL不一致");
        }
        binding(1, 1, 1, data); return new Normalized(data, secret);
    }
    private String decryptFormPassword(String raw) {
        try { byte[] bytes = Base64.getDecoder().decode(raw); if (bytes.length == 0 || bytes.length > 8192 || bytes.length % 256 != 0) throw new IllegalArgumentException();
            StringBuilder value = new StringBuilder(); for (int start = 0; start < bytes.length; start += 256) value.append(keys.decrypt(Base64.getEncoder().encodeToString(Arrays.copyOfRange(bytes, start, start + 256)))); return value.toString();
        } catch (Exception error) { throw fault(400, "连接密码密文无效，请重新输入"); }
    }
    private ObjectNode binding(long owner, long id, long revision, ObjectNode data) {
        int type = data.path("dbType").intValue(); List<Endpoint> endpoints = type == 8 ? parseAddresses(data.path("address").asText()) : List.of(endpoint(data.path("databaseIp").asText(), data.path("databasePort").asText()));
        ObjectNode result = HdiProtocol.JSON.createObjectNode().put("connectionId", Long.toString(id)).put("connectionRevision", Long.toString(revision)).put("ownerId", Long.toString(owner)).put("kind", type == 16 ? "postgresql" : type == 18 ? "ftp" : "kafka");
        if (type == 8) {
            Set<Endpoint> expanded = new LinkedHashSet<>(endpoints);
            for (Endpoint endpoint : endpoints) for (String broker : policy.getKafkaBrokers().getOrDefault(endpoint.key(), List.of())) expanded.addAll(parseAddresses(broker));
            if (expanded.size() > 16) throw fault(422, "Kafka节点数量超过范围"); endpoints = new ArrayList<>(expanded);
        }
        ArrayNode list = result.putArray("endpoints"); for (Endpoint endpoint : endpoints) { if (!policy.getAllowedEndpoints().contains(endpoint.key())) throw fault(422, "连接地址尚未加入平台允许范围"); list.addObject().put("host", endpoint.host).put("address", endpoint.host).put("port", endpoint.port); }
        if (type == 18) { String range = policy.getFtpPassiveRanges().get(endpoints.get(0).key());
            if (range == null || !range.matches("[1-9][0-9]{0,4}-[1-9][0-9]{0,4}")) throw fault(422, "请先配置FTP被动端口范围");
            String[] values = range.split("-"); int start = Integer.parseInt(values[0]), end = Integer.parseInt(values[1]);
            if (start < 1024 || end > 65535 || end < start || end - start > 31 || start <= 2183 && end >= 2180) throw fault(422, "FTP被动端口范围无效"); result.putObject("passivePorts").put("start", start).put("end", end);
        }
        return result;
    }
    private static List<Endpoint> parseAddresses(String address) {
        String[] values = address.split(",", -1); if (values.length < 1 || values.length > 16) throw fault(422, "连接地址数量无效"); List<Endpoint> endpoints = new ArrayList<>();
        for (String value : values) { String[] parts = value.split(":", -1); if (parts.length != 2) throw fault(422, "请填写IPv4地址和端口"); Endpoint endpoint = endpoint(parts[0], parts[1]); if (endpoints.contains(endpoint)) throw fault(422, "连接地址重复"); endpoints.add(endpoint); } return endpoints;
    }
    private static Endpoint endpoint(String host, String port) {
        if (host == null || !host.matches("(?:0|[1-9][0-9]{0,2})(?:\\.(?:0|[1-9][0-9]{0,2})){3}")) throw fault(422, "当前连接须填写明确IPv4地址");
        String[] parts = host.split("\\."); for (String part : parts) if (Integer.parseInt(part) > 255) throw fault(422, "IPv4地址无效");
        int first = Integer.parseInt(parts[0]); if (first == 0 || first >= 224 || host.startsWith("169.254.")) throw fault(422, "连接地址无效");
        if (port == null || !port.matches("[1-9][0-9]{0,4}")) throw fault(422, "连接端口无效"); int number = Integer.parseInt(port); if (number < 1 || number > 65535 || number >= 2180 && number <= 2183) throw fault(422, "连接端口无效或与执行器保留端口冲突"); return new Endpoint(host, number);
    }
    private static ObjectNode publicView(Current row, ObjectNode source, boolean configured) {
        ObjectNode value = source.deepCopy().put("id", row.id).put("_platformRevision", Long.toString(row.revision)).put("revision", row.revision).put("name", row.name).put("datasourceName", row.name).put("enableFlag", row.enabled ? 1 : 0).put("passwordConfigured", configured).put("databasePassword", configured ? "******" : "");
        value.put("datasourceCategory", row.type == 16 ? "关系型数据库" : row.type == 18 ? "其它" : "消息队列");
        boolean currentTest = row.testedRevision == row.revision;
        value.put("status", currentTest ? row.testStatus : -1).put("errorMsg", currentTest && row.testMessage != null ? row.testMessage : "").put("supportTest", true).put("showVersion", false);
        if (currentTest && row.testedAt != null) value.put("testedAt", row.testedAt.toString()); else value.putNull("testedAt");
        return value;
    }
    private Current current(long owner, long id, boolean lock) {
        requireOwner(owner); List<Current> rows = jdbc.query("SELECT connection_id,owner_id,revision,name,db_type,enabled,test_status,tested_revision,test_message,tested_at FROM dg_hdi_connection WHERE connection_id=? AND owner_id=? AND deleted=0" + (lock ? " FOR UPDATE" : ""), (r,n) -> row(r), id, owner);
        if (rows.size() != 1) throw fault(404, "连接不存在或无权访问"); return rows.get(0);
    }
    private Version version(long owner, long id, long revision) {
        List<Version> rows = jdbc.query("SELECT datasource_json,encrypted_secret FROM dg_hdi_connection_revision WHERE connection_id=? AND revision=? AND owner_id=?", (r,n) -> {
            try { return new Version((ObjectNode)HdiProtocol.JSON.readTree(r.getString(1)), r.getString(2)); } catch (Exception error) { throw fault(503, "连接配置无法读取"); }
        }, id, revision, owner); if (rows.size() != 1) throw fault(422, "冻结的连接版本不可用"); return rows.get(0);
    }
    private static Current row(ResultSet r) throws java.sql.SQLException { return new Current(r.getLong(1), r.getLong(2), r.getLong(3), r.getString(4), r.getInt(5), r.getInt(6) == 1, r.getInt(7), r.getLong(8), r.getString(9), r.getTimestamp(10) == null ? null : r.getTimestamp(10).toInstant()); }
    private static Set<Long> referenceIds(JsonNode visual) {
        object(visual); JsonNode nodes = visual.path("nodes"); if (!nodes.isArray() && !nodes.isObject()) nodes = visual.path("nodesMap"); if (!nodes.isArray() && !nodes.isObject()) throw fault(400, "任务节点无效");
        Set<Long> result = new LinkedHashSet<>(); for (JsonNode node : nodes) { if (!CONNECTION_PLUGINS.contains(node.path("adapterName").asText())) continue;
            Long id = optionalId(node.get("dbId"));
            for (JsonNode value : List.of(node.path("ftpConfig").path("ftpId"), node.path("dstTableVo").path("dbId"))) {
                Long nested = optionalId(value); if (nested != null) { if (id != null && !id.equals(nested)) throw fault(422, "节点连接引用不一致"); id = nested; }
            }
            if (id != null) result.add(id);
            for (JsonNode table : node.path("srcTableUseVoList")) { Long nested = optionalId(table.get("srcDbId")); if (nested != null) { if (id != null && !id.equals(nested)) throw fault(422, "节点连接引用不一致"); result.add(nested); } }
        } if (result.size() > 16) throw fault(422, "任务连接数量超过范围"); return result;
    }
    private static void requireOwner(long owner) { if (owner <= 0) throw fault(401, "请先登录平台"); }
    private static void requireEnabled(Current value) { if (!value.enabled) throw fault(422, "连接已停用"); }
    private static String typeName(int type) { return type == 16 ? "PostgreSQL" : type == 18 ? "FTP" : "Kafka"; }
    private static long revision(JsonNode node) { return positive(node.has("_platformRevision") ? node.get("_platformRevision") : node.get("revision"), "连接版本"); }
    private static long positive(JsonNode value, String name) { Long id = optionalId(value); if (id == null) throw fault(400, name + "无效"); return id; }
    private static Long optionalId(JsonNode value) { if (value == null || value.isNull() || value.asText().isBlank() || value.asText().equals("-1") || value.asText().equals("0")) return null; try { if (!value.asText().matches("[1-9][0-9]{0,18}")) throw new IllegalArgumentException(); return Long.parseLong(value.asText()); } catch (Exception error) { throw fault(400, "连接标识无效"); } }
    private static void object(JsonNode node) { if (node == null || !node.isObject()) throw fault(400, "连接参数格式无效"); }
    private static String text(String value, int max, String name) { if (value == null || value.isBlank() || value.length() > max || value.indexOf('\0') >= 0) throw fault(400, name + "无效"); return value.trim(); }
    private static void identifier(String value) { if (value == null || !value.matches("[A-Za-z_][A-Za-z0-9_$]{0,127}") || systemSchema(value)) throw fault(422, "数据库、模式或表标识无效"); }
    private static boolean systemSchema(String value) { return value == null || value.startsWith("pg_") || value.equals("information_schema"); }
    private static String pattern(String value) { return value.replace("\\", "\\\\").replace("_", "\\_").replace("%", "\\%"); }
    private void acquire() { if (!metadataPermits.tryAcquire()) throw fault(429, "连接检查繁忙，请稍后重试"); }
    private static HdiTaskFault fault(int status, String message) { return new HdiTaskFault(status, message); }
}
