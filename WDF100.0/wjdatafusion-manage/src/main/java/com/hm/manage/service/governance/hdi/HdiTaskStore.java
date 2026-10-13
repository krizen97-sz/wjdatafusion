package com.hm.manage.service.governance.hdi;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Stores the original visual model and immutable revisions in the existing platform database. */
@Repository
public class HdiTaskStore {
    public static final long MAX_BROWSER_ID = 9007199254740991L;
    private static final long RUNTIME_ID_BLOCK = 100000L;
    private static final String TASK_COLUMNS = "task_id,workspace_id,owner_id,project_code,task_name,folder_id,revision,published_revision,state,last_run_id,schedule_json,schedule_enabled,created_at,updated_at";
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    public record Workspace(int id, String name) { }
    public record Task(long id, int workspaceId, long ownerId, String projectCode, String name, long folderId,
                       long revision, Long publishedRevision, String state, String lastRunId, String scheduleJson,
                       boolean scheduleEnabled, Instant createdAt, Instant updatedAt) { }
    public record Revision(long taskId, long revision, long ownerId, String visualJson, String sha256, String snapshotJson, String snapshotSha256, String diagnosticsJson) { }
    public record Run(String id, long taskId, long revision, long ownerId, int workspaceId, long nativeFlowId,
                      long nativeInstanceId, long metadataIdBase, String definitionSha256, String mode, String state,
                      String workerSnapshotJson, String errorMessage, Instant createdAt, Instant updatedAt) {
        public String execId() { return Long.toString(nativeFlowId + 2); }
    }
    private static final RowMapper<Task> TASK = (r, n) -> new Task(r.getLong("task_id"), r.getInt("workspace_id"), r.getLong("owner_id"),
        r.getString("project_code"), r.getString("task_name"), r.getLong("folder_id"), r.getLong("revision"),
        r.getObject("published_revision") == null ? null : r.getLong("published_revision"), r.getString("state"),
        r.getString("last_run_id"), r.getString("schedule_json"), r.getBoolean("schedule_enabled"),
        r.getTimestamp("created_at").toInstant(), r.getTimestamp("updated_at").toInstant());
    private static final RowMapper<Run> RUN = (r, n) -> new Run(r.getString("run_id"), r.getLong("task_id"), r.getLong("revision"),
        r.getLong("owner_id"), r.getInt("workspace_id"), r.getLong("native_flow_id"), r.getLong("native_instance_id"),
        r.getLong("metadata_id_base"), r.getString("definition_sha256"), r.getString("mode"), r.getString("state"),
        r.getString("worker_snapshot_json"), r.getString("error_message"), r.getTimestamp("created_at").toInstant(), r.getTimestamp("updated_at").toInstant());
    public HdiTaskStore(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc; transactions = new TransactionTemplate(manager);
    }
    public Workspace workspace() {
        List<Workspace> rows = jdbc.query("SELECT workspace_id,workspace_name FROM dg_hdi_workspace WHERE workspace_key='platform'", (r, n) -> new Workspace(r.getInt(1), r.getString(2)));
        if (rows.size() != 1 || rows.get(0).id() <= 0) throw new HdiTaskFault(503, "数据流程任务域尚未初始化，请执行模块升级脚本");
        return rows.get(0);
    }
    public Task get(long owner, long id) {
        List<Task> rows = jdbc.query("SELECT " + TASK_COLUMNS + " FROM dg_hdi_task WHERE task_id=? AND owner_id=? AND deleted=0", TASK, id, owner);
        if (rows.size() != 1) throw new HdiTaskFault(404, "任务不存在或无权访问");
        return rows.get(0);
    }
    public List<Task> list(long owner, String keyword, Long folder, int page, int size) {
        String filter = folder == null || folder < 0 ? "" : " AND folder_id=?";
        if (filter.isEmpty()) return jdbc.query("SELECT " + TASK_COLUMNS + " FROM dg_hdi_task WHERE owner_id=? AND deleted=0 AND task_name LIKE ? ORDER BY updated_at DESC,task_id DESC LIMIT ? OFFSET ?", TASK, owner, like(keyword), size, (page - 1) * size);
        return jdbc.query("SELECT " + TASK_COLUMNS + " FROM dg_hdi_task WHERE owner_id=? AND deleted=0 AND task_name LIKE ?" + filter + " ORDER BY updated_at DESC,task_id DESC LIMIT ? OFFSET ?", TASK, owner, like(keyword), folder, size, (page - 1) * size);
    }
    public long count(long owner, String keyword, Long folder) {
        Long count = folder == null || folder < 0
            ? jdbc.queryForObject("SELECT COUNT(*) FROM dg_hdi_task WHERE owner_id=? AND deleted=0 AND task_name LIKE ?", Long.class, owner, like(keyword))
            : jdbc.queryForObject("SELECT COUNT(*) FROM dg_hdi_task WHERE owner_id=? AND deleted=0 AND task_name LIKE ? AND folder_id=?", Long.class, owner, like(keyword), folder);
        return count == null ? 0 : count;
    }
    private static String like(String keyword) { return "%" + (keyword == null ? "" : keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")) + "%"; }
    public Revision revision(long owner, long task, long revision) {
        get(owner, task);
        List<Revision> rows = jdbc.query("SELECT * FROM dg_hdi_revision WHERE task_id=? AND revision=? AND owner_id=?", (r, n) -> new Revision(r.getLong("task_id"), r.getLong("revision"), r.getLong("owner_id"), r.getString("visual_json"), r.getString("definition_sha256"), r.getString("snapshot_json"), r.getString("snapshot_sha256"), r.getString("diagnostics_json")), task, revision, owner);
        if (rows.size() != 1) throw new HdiTaskFault(404, "任务修订不存在");
        return rows.get(0);
    }
    public record PreparedVisual(String visualJson, String snapshotJson, String snapshotSha256, String diagnosticsJson) { }
    @FunctionalInterface public interface VisualWriter { PreparedVisual write(long id, long revision); }
    public Task save(long owner, Long id, Long expectedRevision, String name, long folder, VisualWriter visualWriter) {
        if (owner <= 0 || name == null || name.isBlank() || name.length() > 128) throw new HdiTaskFault(400, "请填写有效的任务名称");
        return transactions.execute(status -> {
            Workspace workspace = workspace(); Instant now = Instant.now(); long taskId; long next;
            if (id == null) {
                GeneratedKeyHolder key = new GeneratedKeyHolder();
                jdbc.update(connection -> {
                    PreparedStatement s = connection.prepareStatement("INSERT INTO dg_hdi_task(workspace_id,owner_id,project_code,task_name,folder_id,created_at,updated_at) VALUES(?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
                    s.setInt(1, workspace.id()); s.setLong(2, owner); s.setString(3, project(owner)); s.setString(4, name.trim()); s.setLong(5, folder); s.setTimestamp(6, Timestamp.from(now)); s.setTimestamp(7, Timestamp.from(now)); return s;
                }, key);
                if (key.getKey() == null) throw new HdiTaskFault(503, "任务标识分配失败");
                taskId = key.getKey().longValue(); next = 1;
                if (taskId <= 0 || taskId > MAX_BROWSER_ID) throw new HdiTaskFault(503, "任务标识超过原画布支持范围");
            } else {
                List<Task> rows = jdbc.query("SELECT " + TASK_COLUMNS + " FROM dg_hdi_task WHERE task_id=? AND owner_id=? AND deleted=0 FOR UPDATE", TASK, id, owner);
                if (rows.size() != 1) throw new HdiTaskFault(404, "任务不存在或无权访问");
                Task previous = rows.get(0); taskId = previous.id();
                if (expectedRevision == null || expectedRevision != previous.revision()) throw new HdiTaskFault(409, "任务已被其他窗口修改，请重新打开后再保存");
                next = previous.revision() + 1;
            }
            PreparedVisual prepared = visualWriter.write(taskId, next);
            String visual = prepared.visualJson(); String sha = sha256(visual.getBytes(StandardCharsets.UTF_8));
            if (!sha256(prepared.snapshotJson().getBytes(StandardCharsets.UTF_8)).equals(prepared.snapshotSha256())) throw new HdiTaskFault(502, "原任务编译快照校验不一致");
            jdbc.update("INSERT INTO dg_hdi_revision(task_id,revision,owner_id,visual_json,definition_sha256,snapshot_json,snapshot_sha256,diagnostics_json,created_at) VALUES(?,?,?,?,?,?,?,?,?)", taskId, next, owner, visual, sha, prepared.snapshotJson(), prepared.snapshotSha256(), prepared.diagnosticsJson(), Timestamp.from(now));
            jdbc.update("UPDATE dg_hdi_task SET task_name=?,folder_id=?,revision=?,updated_at=? WHERE task_id=? AND owner_id=?", name.trim(), folder, next, Timestamp.from(now), taskId, owner);
            return get(owner, taskId);
        });
    }
    public Run reserveRun(long owner, long taskId, long revision, String mode, String triggerKey) {
        if (!List.of("DEBUG", "RUN", "SCHEDULE").contains(mode)) throw new HdiTaskFault(400, "运行方式无效");
        return transactions.execute(status -> {
            List<Task> rows = jdbc.query("SELECT " + TASK_COLUMNS + " FROM dg_hdi_task WHERE task_id=? AND owner_id=? AND deleted=0 FOR UPDATE", TASK, taskId, owner);
            if (rows.size() != 1) throw new HdiTaskFault(404, "任务不存在或无权访问");
            Revision frozen = revision(owner, taskId, revision);
            Long active = jdbc.queryForObject("SELECT COUNT(*) FROM dg_hdi_run WHERE task_id=? AND state IN ('QUEUED','PREPARING','COMPILING','STARTING','RUNNING','STOPPING','UNKNOWN')", Long.class, taskId);
            if (active != null && active > 0) throw new HdiTaskFault(409, "任务已有未结束或待核查的运行");
            Long base = jdbc.queryForObject("SELECT next_value FROM dg_hdi_counter WHERE counter_name='native_runtime' FOR UPDATE", Long.class);
            if (base == null || base < 1 || base > MAX_BROWSER_ID - RUNTIME_ID_BLOCK) throw new HdiTaskFault(503, "原引擎标识分配不可用");
            jdbc.update("UPDATE dg_hdi_counter SET next_value=? WHERE counter_name='native_runtime'", base + RUNTIME_ID_BLOCK);
            String id = UUID.randomUUID().toString(); Timestamp now = Timestamp.from(Instant.now());
            jdbc.update("INSERT INTO dg_hdi_run(run_id,task_id,revision,owner_id,workspace_id,native_flow_id,native_instance_id,metadata_id_base,definition_sha256,mode,state,trigger_key,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)", id, taskId, revision, owner, rows.get(0).workspaceId(), base, base + 1, base + 100, frozen.sha256(), mode, "QUEUED", triggerKey, now, now);
            jdbc.update("UPDATE dg_hdi_task SET last_run_id=?,state='QUEUED' WHERE task_id=? AND owner_id=?", id, taskId, owner);
            return run(owner, id);
        });
    }
    public Run run(long owner, String id) {
        List<Run> rows = jdbc.query("SELECT * FROM dg_hdi_run WHERE run_id=? AND owner_id=?", RUN, id, owner);
        if (rows.size() != 1) throw new HdiTaskFault(404, "运行不存在或无权访问");
        return rows.get(0);
    }
    public List<Run> runs(long owner, long task) { get(owner, task); return jdbc.query("SELECT * FROM dg_hdi_run WHERE owner_id=? AND task_id=? ORDER BY created_at DESC LIMIT 100", RUN, owner, task); }
    public void transition(Run run, String state, String snapshot, String error) {
        if (!List.of("QUEUED", "PREPARING", "COMPILING", "STARTING", "RUNNING", "STOPPING", "SUCCEEDED", "FAILED", "CANCELLED", "UNKNOWN").contains(state)) throw new IllegalArgumentException("Unknown run state");
        transactions.executeWithoutResult(status -> {
            Timestamp now = Timestamp.from(Instant.now()); boolean terminal = List.of("SUCCEEDED", "FAILED", "CANCELLED").contains(state);
            int changed = jdbc.update("UPDATE dg_hdi_run SET state=?,worker_snapshot_json=?,error_message=?,updated_at=?,finished_at=? WHERE run_id=? AND owner_id=? AND state NOT IN ('SUCCEEDED','FAILED','CANCELLED')", state, snapshot, error, now, terminal ? now : null, run.id(), run.ownerId());
            if (changed == 1) jdbc.update("UPDATE dg_hdi_task SET state=? WHERE task_id=? AND owner_id=? AND last_run_id=?", state, run.taskId(), run.ownerId(), run.id());
        });
    }
    public List<Run> unfinished() { return jdbc.query("SELECT * FROM dg_hdi_run WHERE state NOT IN ('SUCCEEDED','FAILED','CANCELLED') ORDER BY created_at", RUN); }
    public String getPreference(long owner, String key, String fallback) {
        List<String> rows = jdbc.query("SELECT preference_json FROM dg_hdi_preference WHERE owner_id=? AND preference_key=?", (r, n) -> r.getString(1), owner, key);
        return rows.isEmpty() ? fallback : rows.get(0);
    }
    public void setPreference(long owner, String key, String json) {
        jdbc.update("INSERT INTO dg_hdi_preference(owner_id,preference_key,preference_json,updated_at) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE preference_json=VALUES(preference_json),updated_at=VALUES(updated_at)", owner, key, json, Timestamp.from(Instant.now()));
    }
    public static String project(long owner) { return "rynew-user-" + owner; }
    public static String sha256(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
