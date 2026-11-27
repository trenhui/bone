-- ============================================================
-- 0144_fix_claim_sign_tenant_id.sql
--
-- 一句话：把 0142 建的签收三表 tenant_id 从 VARCHAR(64) 修正为 BIGINT，
--        与 Bone 的 TenantAggregateRoot<Long> / 全仓其余表（t_claim_case 等）对齐。
--
-- 为什么另起脚本而不改 0142：0142 已按 sha256 记入 schema_migration_journal，
-- 回改会让所有已应用环境报 drift（迁移身份是文件名，内容是校验和）。
--
-- 故障现象（本机 8097 实证）：线下批次 importParticipants → requireBatch 比较
-- `batch.getTenantId()` 时抛 ClassCastException: String cannot be cast to Long，
-- 线上自动签收不查批次故不受影响——只有线下四步全链路会 500。
--
-- 幂等：MODIFY COLUMN 到同类型本身无害；用 information_schema 条件拼装，
-- 仅在列仍是 varchar 时 ALTER，终态断言确认三列均 bigint。
-- ============================================================

USE bone;

DROP PROCEDURE IF EXISTS sp_fix_claim_sign_tenant_0144;
DELIMITER $$
CREATE PROCEDURE sp_fix_claim_sign_tenant_0144()
BEGIN
  DECLARE v_batch  VARCHAR(32);
  DECLARE v_item   VARCHAR(32);
  DECLARE v_track  VARCHAR(32);

  SELECT COLUMN_TYPE INTO v_batch
    FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_claim_sign_batch' AND COLUMN_NAME = 'tenant_id';
  SELECT COLUMN_TYPE INTO v_item
    FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_claim_sign_batch_item' AND COLUMN_NAME = 'tenant_id';
  SELECT COLUMN_TYPE INTO v_track
    FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_claim_sign_track' AND COLUMN_NAME = 'tenant_id';

  IF v_batch IS NULL OR v_item IS NULL OR v_track IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO = 1644,
      MESSAGE_TEXT = '0144 FAIL: sign tables missing (apply 0142 first)';
  END IF;

  IF v_batch LIKE 'varchar%' THEN
    ALTER TABLE t_claim_sign_batch MODIFY COLUMN tenant_id BIGINT NOT NULL;
  END IF;
  IF v_item LIKE 'varchar%' THEN
    ALTER TABLE t_claim_sign_batch_item MODIFY COLUMN tenant_id BIGINT NOT NULL;
  END IF;
  IF v_track LIKE 'varchar%' THEN
    ALTER TABLE t_claim_sign_track MODIFY COLUMN tenant_id BIGINT NOT NULL;
  END IF;
END$$
DELIMITER ;
CALL sp_fix_claim_sign_tenant_0144();
DROP PROCEDURE IF EXISTS sp_fix_claim_sign_tenant_0144;

-- 终态断言：三列均为 bigint。
SELECT COUNT(*) INTO @sign_tenant_bad_0144
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('t_claim_sign_batch','t_claim_sign_batch_item','t_claim_sign_track')
  AND COLUMN_NAME = 'tenant_id'
  AND COLUMN_TYPE <> 'bigint';

DROP PROCEDURE IF EXISTS sp_verify_0144;
DELIMITER $$
CREATE PROCEDURE sp_verify_0144()
BEGIN
  IF @sign_tenant_bad_0144 <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO = 1644,
      MESSAGE_TEXT = '0144 FAIL: sign tenant_id still not bigint';
  END IF;
END$$
DELIMITER ;
CALL sp_verify_0144();
DROP PROCEDURE IF EXISTS sp_verify_0144;
