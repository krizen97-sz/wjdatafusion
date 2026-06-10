-- 将历史“主平台-组织”关系迁移为“主平台-联系人”关系
-- 迁移规则：
-- 1. 仅处理主平台的旧组织关系
-- 2. 将组织下的联系人全部映射到主平台
-- 3. 迁移完成后删除已迁移的旧组织关系

START TRANSACTION;

INSERT IGNORE INTO sup_platform_contact_rel (platform_id, contact_id, create_by, create_time)
SELECT por.platform_id, c.contact_id, 'migration', NOW()
FROM sup_platform_org_rel por
INNER JOIN sup_platform p ON p.platform_id = por.platform_id
INNER JOIN sup_contact c ON c.org_id = por.org_id
WHERE p.platform_level = 'MAIN';

DELETE por
FROM sup_platform_org_rel por
INNER JOIN sup_platform p ON p.platform_id = por.platform_id
INNER JOIN sup_contact c ON c.org_id = por.org_id
WHERE p.platform_level = 'MAIN';

COMMIT;
