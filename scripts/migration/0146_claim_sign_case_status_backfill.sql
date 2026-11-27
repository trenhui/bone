-- ============================================================
-- 0146_claim_sign_case_status_backfill.sql
--
-- 一句话：给 0145 新增的 case_status 建立明确存量语义，避免 NULL 被前端计入「待人工」。
--
-- 口径
-- ----------------------------------------------------------------
--   · 0145 已发且记账，改一字即 drift，因此新建迁移修正，不改 0145；
--   · claim_id 非空的存量明细：既有案件引用，语义 SKIPPED（不重复建案）；
--   · claim_id 为空的存量明细：批次已签收且自动建案事件不会重放，语义 AWAIT_MANUAL；
--   · 新行默认值 PENDING，与 SignBatchItem.withCaseElements 的初始态一致；
--   · UPDATE 幂等，列默认值仅在 DEFAULT NULL 时条件 MODIFY；
--   · 终态断言用存储过程 SIGNAL(1644)，PREPARE 不能执行 SIGNAL。
-- ============================================================

USE bone;

SET @db = DATABASE();

UPDATE t_claim_sign_batch_item
SET case_status = 'SKIPPED'
WHERE case_status IS NULL AND claim_id IS NOT NULL;

UPDATE t_claim_sign_batch_item
SET case_status = 'AWAIT_MANUAL'
WHERE case_status IS NULL AND claim_id IS NULL;

SET @null_default = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db
    AND TABLE_NAME = 't_claim_sign_batch_item'
    AND COLUMN_NAME = 'case_status'
    AND COLUMN_DEFAULT IS NULL
);
SET @modify_sql = IF(@null_default = 1,
  'ALTER TABLE t_claim_sign_batch_item MODIFY COLUMN case_status VARCHAR(32) DEFAULT ''PENDING'' COMMENT ''自动建案结果：NEW/SKIPPED/AWAIT_MANUAL/FAILED''',
  'DO 0');
PREPARE stmt_0146 FROM @modify_sql;
EXECUTE stmt_0146;
DEALLOCATE PREPARE stmt_0146;

SELECT COUNT(*) INTO @null_status_0146
FROM t_claim_sign_batch_item
WHERE case_status IS NULL;

DROP PROCEDURE IF EXISTS sp_verify_0146;
DELIMITER $$
CREATE PROCEDURE sp_verify_0146()
BEGIN
  IF @null_status_0146 <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO = 1644,
      MESSAGE_TEXT = '0146 FAIL: sign item case_status still null';
  END IF;
END$$
DELIMITER ;
CALL sp_verify_0146();
DROP PROCEDURE IF EXISTS sp_verify_0146;
