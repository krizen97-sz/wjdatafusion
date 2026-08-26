create table if not exists wl_filter_data (
  id bigint not null auto_increment comment '主键',
  plate_no varchar(32) default null comment '车牌号',
  alarm_type varchar(64) default null comment '告警类型',
  channel_id int default null comment '通道ID',
  channel_name varchar(255) default null comment '通道名称',
  data_type varchar(64) default null comment '数据类型',
  event_type varchar(64) default null comment '事件类型',
  event_description varchar(128) default null comment '事件描述',
  ip_address varchar(64) default null comment '设备IP',
  port_no int default null comment '端口',
  send_time datetime default null comment '发送时间',
  recv_time datetime default null comment '接收时间',
  pass_time datetime default null comment '过车时间',
  camera_name varchar(255) default null comment '相机名称',
  camera_address varchar(255) default null comment '相机地址',
  device_name varchar(255) default null comment '设备名称',
  direction_index varchar(64) default null comment '方向索引',
  crossing_id bigint default null comment '路口ID',
  lane_no int default null comment '车道号',
  plate_color varchar(32) default null comment '车牌颜色',
  vehicle_type varchar(64) default null comment '车辆类型',
  pass_id varchar(128) default null comment '过车ID',
  event_uuid varchar(128) default null comment '事件UUID',
  task_id varchar(128) default null comment '任务ID',
  target_pic_url varchar(1000) default null comment '抓拍图地址',
  raw_json longtext comment '原始报文',
  create_time datetime default current_timestamp comment '创建时间',
  primary key (id)
) engine=innodb default charset=utf8mb4 comment='白名单过滤数据';

insert into sys_menu values('3000', '白名单管理', '0', '6', 'whitelist', null, '', '', 1, 0, 'M', '0', '0', '', 'shield-check', 'admin', sysdate(), '', null, '白名单管理目录');
insert into sys_menu values('3001', '车牌管控', '3000', '1', 'plate', 'whitelist/plate/index', '', '', 1, 0, 'C', '0', '0', 'whitelist:plate:list', 'car-front', 'admin', sysdate(), '', null, '车牌管控菜单');
insert into sys_menu values('3002', '过滤数据', '3000', '2', 'filterData', 'whitelist/filterData/index', '', '', 1, 0, 'C', '0', '0', 'whitelist:filterData:list', 'list-filter', 'admin', sysdate(), '', null, '过滤数据菜单');

insert into sys_menu values('3003', '车牌管控查询', '3001', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'whitelist:plate:query', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('3004', '车牌管控新增', '3001', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'whitelist:plate:add', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('3005', '车牌管控修改', '3001', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'whitelist:plate:edit', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('3006', '车牌管控删除', '3001', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'whitelist:plate:remove', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('3007', '车牌管控导出', '3001', '5', '#', '', '', '', 1, 0, 'F', '0', '0', 'whitelist:plate:export', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('3012', '车牌管控导入', '3001', '6', '#', '', '', '', 1, 0, 'F', '0', '0', 'whitelist:plate:import', '#', 'admin', sysdate(), '', null, '');

insert into sys_menu values('3008', '过滤数据查询', '3002', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'whitelist:filterData:query', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('3009', '过滤数据删除', '3002', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'whitelist:filterData:remove', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('3010', '过滤数据导出', '3002', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'whitelist:filterData:export', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('3011', '过滤数据拉取', '3002', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'whitelist:filterData:pull', '#', 'admin', sysdate(), '', null, '');

insert into sys_job(job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
values('白名单过滤数据拉取', 'DEFAULT', 'whitelistFilterTask.pullKafkaData', '0 0/1 * * * ?', '3', '1', '0', 'admin', sysdate(), '每分钟拉取一次Kafka白名单过滤数据');
