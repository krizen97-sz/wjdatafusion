-- 2026-06-12: 补齐sys_job_log执行时间字段，避免任务日志写入SQL报错
-- 场景：老环境中sys_job_log缺少start_time/end_time，导致新增任务日志时BadSqlGrammarException
-- 说明：与现有任务日志数据兼容，仅补列，不影响历史记录；无则新增，有则忽略。

ALTER TABLE sys_job_log
  ADD COLUMN IF NOT EXISTS start_time DATETIME COMMENT '执行开始时间' AFTER exception_info,
  ADD COLUMN IF NOT EXISTS end_time   DATETIME COMMENT '执行结束时间' AFTER start_time;
