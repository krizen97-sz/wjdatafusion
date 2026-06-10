-- support 组织类型升级：
-- VENDOR -> THIRD_VENDOR
-- USER_UNIT -> USER
-- 可重复执行，未命中的语句不会报错。

START TRANSACTION;

UPDATE sup_org
SET org_type = 'THIRD_VENDOR'
WHERE org_type = 'VENDOR';

UPDATE sup_org
SET org_type = 'USER'
WHERE org_type = 'USER_UNIT';

COMMIT;
