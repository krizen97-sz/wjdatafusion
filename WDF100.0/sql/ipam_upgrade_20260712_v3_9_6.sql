-- v3.9.6 IPAM 地址连通性扫描
-- 说明：
-- 1. 本脚本只新增 ipam_ 扫描表、IPAM 扫描权限和若依定时任务，不修改 sup_* 现场融合业务表。
-- 2. 扫描结果与 IP 分配状态完全分离，Ping 结果不会自动改变 ipam_address.status。
-- 3. 全域扫描每天 01:00 执行，Quartz 任务禁止并发。

CREATE TABLE IF NOT EXISTS ipam_scan_job (
  scan_id           BIGINT(20)    NOT NULL AUTO_INCREMENT COMMENT '扫描任务ID',
  scan_scope        VARCHAR(20)   NOT NULL COMMENT '扫描范围（NETWORK单网段 ALL全域）',
  trigger_type      VARCHAR(20)   NOT NULL COMMENT '触发方式（MANUAL手工 SCHEDULED定时）',
  network_id        BIGINT(20)    DEFAULT NULL COMMENT '单网段扫描时的网段ID',
  network_name      VARCHAR(120)  DEFAULT NULL COMMENT '单网段扫描时的网段名称',
  scan_status       VARCHAR(20)   NOT NULL COMMENT '状态（QUEUED RUNNING COMPLETED PARTIAL FAILED）',
  total_count       BIGINT(20)    NOT NULL DEFAULT 0 COMMENT '待检测地址数',
  completed_count   BIGINT(20)    NOT NULL DEFAULT 0 COMMENT '已完成地址数',
  online_count      BIGINT(20)    NOT NULL DEFAULT 0 COMMENT '在线地址数',
  offline_count     BIGINT(20)    NOT NULL DEFAULT 0 COMMENT '离线地址数',
  error_count       BIGINT(20)    NOT NULL DEFAULT 0 COMMENT '检测异常地址数',
  timeout_ms        INT           NOT NULL COMMENT '单地址超时时间（毫秒）',
  interval_ms       INT           NOT NULL COMMENT '探针提交间隔（毫秒）',
  concurrency_count INT           NOT NULL COMMENT '最大并发探针数',
  started_time      DATETIME      DEFAULT NULL COMMENT '开始时间',
  finished_time     DATETIME      DEFAULT NULL COMMENT '结束时间',
  error_message     VARCHAR(1000) DEFAULT NULL COMMENT '任务异常说明',
  create_by         VARCHAR(64)   DEFAULT '' COMMENT '创建者',
  create_time       DATETIME      DEFAULT NULL COMMENT '创建时间',
  update_by         VARCHAR(64)   DEFAULT '' COMMENT '更新者',
  update_time       DATETIME      DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (scan_id),
  KEY idx_ipam_scan_job_network (network_id, create_time),
  KEY idx_ipam_scan_job_status (scan_status, create_time),
  KEY idx_ipam_scan_job_scope (scan_scope, trigger_type, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IPAM连通性扫描任务';

CREATE TABLE IF NOT EXISTS ipam_scan_result (
  result_id            BIGINT(20)   NOT NULL AUTO_INCREMENT COMMENT '扫描结果ID',
  scan_id              BIGINT(20)   NOT NULL COMMENT '最近扫描任务ID',
  network_id           BIGINT(20)   NOT NULL COMMENT '网段ID',
  segment_id           BIGINT(20)   NOT NULL COMMENT '地址池ID',
  ip_address           VARCHAR(64)  NOT NULL COMMENT 'IP地址',
  ip_value             BIGINT(20)   NOT NULL COMMENT 'IP数值',
  connectivity_status  VARCHAR(20)  NOT NULL COMMENT '连通状态（ONLINE OFFLINE UNKNOWN）',
  response_time_ms     BIGINT(20)   DEFAULT NULL COMMENT '本次响应时间（毫秒）',
  last_scan_time       DATETIME     NOT NULL COMMENT '最近检测时间',
  last_online_time     DATETIME     DEFAULT NULL COMMENT '最近在线时间',
  last_offline_time    DATETIME     DEFAULT NULL COMMENT '最近离线时间',
  error_message        VARCHAR(500) DEFAULT NULL COMMENT '检测异常说明',
  update_time          DATETIME     DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (result_id),
  UNIQUE KEY uk_ipam_scan_result_ip (ip_address),
  KEY idx_ipam_scan_result_segment (segment_id, ip_value),
  KEY idx_ipam_scan_result_network_status (network_id, connectivity_status),
  KEY idx_ipam_scan_result_time (last_scan_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IPAM地址最新连通状态';

INSERT INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, `query`, route_name,
   is_frame, is_cache, menu_type, visible, status, perms, icon,
   create_by, create_time, update_by, update_time, remark)
VALUES
  (2411, '网段扫描', 2400, 10, '#', '', '', '', 1, 0, 'F', '0', '0',
   'ipam:network:scan', '#', 'admin', NOW(), 'admin', NOW(), '扫描网段IP连通性')
ON DUPLICATE KEY UPDATE
  menu_name = VALUES(menu_name),
  parent_id = VALUES(parent_id),
  order_num = VALUES(order_num),
  menu_type = VALUES(menu_type),
  visible = VALUES(visible),
  status = VALUES(status),
  perms = VALUES(perms),
  update_by = VALUES(update_by),
  update_time = VALUES(update_time),
  remark = VALUES(remark);

INSERT INTO sys_job
  (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent,
   status, create_by, create_time, update_by, update_time, remark)
SELECT
  'IPAM每日全域连通性扫描', 'SYSTEM', 'ipamScanTask.scanAllNetworks',
  '0 0 1 * * ?', '3', '1', '0', 'admin', NOW(), 'admin', NOW(),
  '每天凌晨1点扫描全部启用网段；IPAM内部使用有界并发、探针间隔和单IP超时'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM sys_job WHERE invoke_target = 'ipamScanTask.scanAllNetworks'
);

UPDATE sys_job
SET job_name = 'IPAM每日全域连通性扫描',
    job_group = 'SYSTEM',
    cron_expression = '0 0 1 * * ?',
    misfire_policy = '3',
    concurrent = '1',
    status = '0',
    update_by = 'admin',
    update_time = NOW(),
    remark = '每天凌晨1点扫描全部启用网段；IPAM内部使用有界并发、探针间隔和单IP超时'
WHERE invoke_target = 'ipamScanTask.scanAllNetworks';
