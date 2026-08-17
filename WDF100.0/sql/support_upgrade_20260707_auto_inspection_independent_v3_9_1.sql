-- 自动化巡检独立化重构 v3.9.1
-- 目标：冻结旧 TIM/Team 巡检入口和定时任务，保留历史表与代码用于回看或回滚；
--      新巡检继续使用 sup_auto_inspection_* 表族和 supportAutoInspectionTask。

UPDATE sys_menu
SET visible = '1',
    status = '0',
    update_by = 'admin',
    update_time = NOW(),
    remark = '旧巡检入口已冻结，历史数据保留；请使用自动化巡检模块'
WHERE menu_id = 2206
   OR path = 'timInspection'
   OR perms LIKE 'support:timInspection:%';

UPDATE sys_job
SET status = '1',
    update_by = 'admin',
    update_time = NOW(),
    remark = CONCAT(IFNULL(remark, ''), IF(IFNULL(remark, '') = '', '', '；'), '旧巡检任务已冻结，请使用自动化巡检计划')
WHERE invoke_target LIKE 'supportTimInspectionTask%';

UPDATE sys_menu
SET component = 'support/autoInspection/index',
    status = '0',
    update_by = 'admin',
    update_time = NOW()
WHERE menu_id IN (2301, 2302, 2303, 2304, 2305);

UPDATE sup_auto_inspection_tool
SET status = '0',
    update_by = 'admin',
    update_time = NOW(),
    remark = '自动化巡检独立模块内置工具'
WHERE tool_code IN (
    'KAFKA_LAG',
    'HTTP_COUNT',
    'HTTP_HEALTH',
    'HTTP_API_TEST',
    'FTP_FILE_COUNT',
    'SERVER_FILE_COUNT',
    'SERVER_DISK',
    'BIG_DATA_SERVER_DISK',
    'TCP_PORT_CHECK',
    'SERVER_SERVICE_STATUS'
);
