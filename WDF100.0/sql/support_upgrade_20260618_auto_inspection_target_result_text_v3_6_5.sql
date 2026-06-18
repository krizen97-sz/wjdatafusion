-- 自动化巡检目标结果详情扩容 v3.6.5
-- 说明：
-- 1. 服务器服务状态检测会保存 systemctl status / restart / 复查输出，原 VARCHAR(1000) 容量不足。
-- 2. 将目标结果详情和异常原因调整为 MEDIUMTEXT，保留已有数据，不影响历史巡检记录。

ALTER TABLE sup_auto_inspection_target_result
  MODIFY COLUMN result_detail MEDIUMTEXT DEFAULT NULL COMMENT '结果详情',
  MODIFY COLUMN error_message MEDIUMTEXT DEFAULT NULL COMMENT '异常原因';
