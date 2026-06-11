-- v3.0.1 自动化巡检菜单 Query 修复
-- 说明：若依前端菜单会对 sys_menu.query 执行 JSON.parse，v3.0.0 中写入的 tab=xxx 会导致已登录首页白屏。

UPDATE sys_menu
SET `query` = CASE menu_id
  WHEN 2301 THEN '{"tab":"template"}'
  WHEN 2302 THEN '{"tab":"target"}'
  WHEN 2303 THEN '{"tab":"plan"}'
  WHEN 2304 THEN '{"tab":"record"}'
  ELSE `query`
END,
update_time = NOW(),
remark = CASE menu_id
  WHEN 2301 THEN '自动化巡检模板入口'
  WHEN 2302 THEN '自动化巡检目标入口'
  WHEN 2303 THEN '自动化巡检计划入口'
  WHEN 2304 THEN '自动化巡检记录入口'
  ELSE remark
END
WHERE menu_id IN (2301, 2302, 2303, 2304);
