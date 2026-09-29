-- v4.16.0 -> v4.17.0: independent authorized password pool.
-- Stop all application writers and scans; back up first; keep SUPPORT_AES_KEY.
-- Requires the complete v4.16.0 schema. Run in a dedicated connection, without --force.
-- Existing server/account records are untouched. Legacy pool ciphers are copied verbatim.
SET NAMES utf8mb4;
DELIMITER $$
DROP PROCEDURE IF EXISTS rynew_server_password_pool_v4170$$
CREATE PROCEDURE rynew_server_password_pool_v4170()
BEGIN
  DECLARE column_count INT DEFAULT 0;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;
  IF (SELECT COUNT(*) FROM information_schema.tables
      WHERE table_schema=DATABASE() AND table_name IN ('sup_server_scan_credential','sup_server_scan_profile')
        AND engine='InnoDB') <> 2 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Complete v4.16.0 scan schema required';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema=DATABASE()
      AND event_object_table='sup_server_scan_credential') <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Review custom password pool triggers before migration';
  END IF;
  SELECT COUNT(*) INTO column_count FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='sup_server_scan_credential' AND column_name='password_cipher';
  IF column_count=0 THEN
    ALTER TABLE sup_server_scan_credential ADD COLUMN password_cipher VARCHAR(1024) NOT NULL DEFAULT ''
      COMMENT 'Independent authorized candidate password (AES encrypted)';
  ELSEIF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='sup_server_scan_credential' AND column_name='password_cipher'
      AND data_type='varchar' AND character_maximum_length=1024 AND is_nullable='NO'
      AND column_default='' AND COALESCE(generation_expression,'')='') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Incompatible password_cipher definition';
  END IF;
  IF EXISTS (SELECT 1 FROM sup_server_scan_credential WHERE password_cipher=''
      AND (login_mode NOT IN ('ROOT_DIRECT','HIK_SU_ROOT') OR root_password_cipher=''
        OR (login_mode='HIK_SU_ROOT' AND hik_password_cipher=''))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Incomplete legacy pool entry; repair before migration';
  END IF;
  START TRANSACTION;
  -- Insert each legacy hik secret as an independent entry; the old ID keeps its root secret.
  INSERT INTO sup_server_scan_credential
    (site_id,credential_name,login_mode,password_cipher,create_by,create_time,update_by,update_time)
  SELECT site_id,CONCAT(LEFT(credential_name,70),' / 2'),'PASSWORD_POOL',hik_password_cipher,
    create_by,create_time,update_by,update_time
  FROM sup_server_scan_credential
  WHERE password_cipher='' AND login_mode='HIK_SU_ROOT';
  UPDATE sup_server_scan_credential SET password_cipher=root_password_cipher
  WHERE password_cipher='' AND login_mode IN ('ROOT_DIRECT','HIK_SU_ROOT');
  COMMIT;
  -- Nonempty password_cipher marks migrated entries, so reruns do not resurrect deleted split entries.
  SELECT 'SUCCESS' AS migration_result, COUNT(*) AS independent_password_count
  FROM sup_server_scan_credential;
END$$
CALL rynew_server_password_pool_v4170()$$
DROP PROCEDURE rynew_server_password_pool_v4170$$
DELIMITER ;
-- Old pool fields are retained only for recovery. New code exclusively uses password_cipher.
-- Do not mix old/new application instances or restore the whole database to roll back.
