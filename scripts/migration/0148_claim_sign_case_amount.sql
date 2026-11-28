-- ============================================================
-- 0148_claim_sign_case_amount.sql
--
-- 一句话：签收明细补 claim_amount，让线上自动签收携带的赔案金额能透传到案件/任务/理算。
--
-- 口径
-- ----------------------------------------------------------------
--   · 金额落明细而非批次：一次回执可带多条 caseElements，各赔案金额可能不同；
--   · DECIMAL(12,2) 默认 NULL：NULL = 未回传金额，建案时落 0 费用占位并转人工判断；
--   · 条件 ADD COLUMN + 存储过程 SIGNAL(1644) 终态断言，可重复执行；
--   · 不回改已发迁移，已应用环境的 sha256 保持不变。
-- ============================================================

USE bone;

SET @db = DATABASE();

DROP PROCEDURE IF EXISTS sp_add_col_0148;
DELIMITER $$
CREATE PROCEDURE sp_add_col_0148()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_claim_sign_batch_item'
      AND COLUMN_NAME = 'claim_amount'
  ) THEN
    ALTER TABLE t_claim_sign_batch_item
      ADD COLUMN claim_amount DECIMAL(12,2) DEFAULT NULL COMMENT '回执赔案金额（未回传为NULL）'
      AFTER source_channel;
  END IF;
END$$
DELIMITER ;
CALL sp_add_col_0148();
DROP PROCEDURE IF EXISTS sp_add_col_0148;

SELECT COUNT(*) INTO @amount_col_count_0148
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @db
  AND TABLE_NAME = 't_claim_sign_batch_item'
  AND COLUMN_NAME = 'claim_amount'
  AND DATA_TYPE = 'decimal'
  AND NUMERIC_PRECISION = 12
  AND NUMERIC_SCALE = 2;

DROP PROCEDURE IF EXISTS sp_verify_0148;
DELIMITER $$
CREATE PROCEDURE sp_verify_0148()
BEGIN
  IF @amount_col_count_0148 <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO = 1644,
      MESSAGE_TEXT = '0148 FAIL: sign item claim_amount column missing';
  END IF;
END$$
DELIMITER ;
CALL sp_verify_0148();
DROP PROCEDURE IF EXISTS sp_verify_0148;
