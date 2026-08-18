-- 2026-08-11 IPAM 凭据密钥机制清理
--
-- 作用：
-- 1. 保留 ipam_address.login_password 中的全部原始密码。
-- 2. 仅在 login_password_cipher 没有任何有效密文时删除该废弃字段。
-- 3. 删除“迁移历史密码”菜单权限；保留“查看设备密码”权限和审计日志。
--
-- 执行前必须完成数据库备份。MySQL DDL 会自动提交。
-- 若发现任何密文，本脚本会在删除字段前报错停止，绝不静默丢失密码。

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS ipam_remove_credential_key_20260811;
DELIMITER $$
CREATE PROCEDURE ipam_remove_credential_key_20260811()
SQL SECURITY INVOKER
BEGIN
  DECLARE v_total_before BIGINT DEFAULT 0;
  DECLARE v_password_before BIGINT DEFAULT 0;
  DECLARE v_total_after BIGINT DEFAULT 0;
  DECLARE v_password_after BIGINT DEFAULT 0;
  DECLARE v_cipher_rows BIGINT DEFAULT 0;

  IF DATABASE() IS NULL OR DATABASE() = '' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '未选择目标数据库';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'ipam_address'
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '缺少 IPAM 表 ipam_address';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'sys_menu'
  ) OR NOT EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'sys_role_menu'
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '缺少若依菜单基础表，无法清理废弃权限';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'ipam_address'
      AND column_name = 'login_password'
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '缺少 login_password，无法保证密码完整性';
  END IF;

  SELECT COUNT(1),
         COALESCE(SUM(CASE WHEN NULLIF(login_password, '') IS NOT NULL THEN 1 ELSE 0 END), 0)
    INTO v_total_before, v_password_before
  FROM ipam_address;

  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'ipam_address'
      AND column_name = 'login_password_cipher'
  ) THEN
    SELECT COUNT(1) INTO v_cipher_rows
    FROM ipam_address
    WHERE NULLIF(TRIM(login_password_cipher), '') IS NOT NULL;

    IF v_cipher_rows > 0 THEN
      SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = '发现尚未恢复的 IPAM 密文密码，已停止清理；请先用原密钥恢复到 login_password';
    END IF;

    ALTER TABLE ipam_address DROP COLUMN login_password_cipher;
  END IF;

  DELETE role_menu
  FROM sys_role_menu role_menu
  JOIN sys_menu menu ON menu.menu_id = role_menu.menu_id
  WHERE menu.perms = 'ipam:credential:migrate';

  DELETE FROM sys_menu WHERE perms = 'ipam:credential:migrate';

  SELECT COUNT(1),
         COALESCE(SUM(CASE WHEN NULLIF(login_password, '') IS NOT NULL THEN 1 ELSE 0 END), 0)
    INTO v_total_after, v_password_after
  FROM ipam_address;

  IF v_total_before <> v_total_after OR v_password_before <> v_password_after THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'IPAM 数据完整性校验失败，请立即使用备份回滚';
  END IF;

  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'ipam_address'
      AND column_name = 'login_password_cipher'
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'IPAM 密文字段清理失败';
  END IF;

  IF EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'ipam:credential:migrate') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'IPAM 密码迁移权限清理失败';
  END IF;

  SELECT 'IPAM_CREDENTIAL_KEY_REMOVED' AS result,
         v_total_after AS address_count,
         v_password_after AS password_count,
         v_cipher_rows AS discarded_cipher_count;
END$$
DELIMITER ;

CALL ipam_remove_credential_key_20260811();
DROP PROCEDURE IF EXISTS ipam_remove_credential_key_20260811;
