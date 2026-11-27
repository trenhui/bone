-- ============================================================
-- 0145_claim_sign_auto_case.sql
--
-- 一句话：扩展签收明细表 t_claim_sign_batch_item，携带建案要素与幂等键，
--        支撑签收确认后按明细自动建理赔案件。
--
-- 口径
-- ----------------------------------------------------------------
--   · 只扩列不删列，存量明细（claim_id 非空）行为不变；
--   · business_key 为明细级幂等键，唯一索引保证重复签收/事件重放不重复建案；
--   · case_status 记录自动化结果（NEW/SKIPPED/AWAIT_MANUAL/FAILED），供人工兜底筛选；
--   · 实名明文落库是用户显式裁决（偏离 PIPL 假名化），仅限本地开发库；
--   · 幂等：information_schema 条件 ADD COLUMN + ADD INDEX；
--   · 终态断言用存储过程 SIGNAL(1644)（PREPARE 不支持 SIGNAL）。
-- ============================================================

USE bone;

SET @db = DATABASE();

-- 逐列条件添加：列不存在才 ALTER，重复执行安全。
DROP PROCEDURE IF EXISTS sp_add_sign_item_cols_0145;
DELIMITER $$
CREATE PROCEDURE sp_add_sign_item_cols_0145()
BEGIN
  -- claim_id 改为可空：NULL 表示尚未关联案件（携带要素建案路径）。
  -- 唯一键 (batch_id, claim_id) 在 MySQL 允许多 NULL，故不影响存量。
  IF EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_claim_sign_batch_item' AND COLUMN_NAME='claim_id' AND IS_NULLABLE='NO') THEN
    ALTER TABLE t_claim_sign_batch_item MODIFY COLUMN claim_id BIGINT DEFAULT NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_claim_sign_batch_item' AND COLUMN_NAME='policy_no') THEN
    ALTER TABLE t_claim_sign_batch_item ADD COLUMN policy_no VARCHAR(64) DEFAULT NULL COMMENT '保单号（建案要素）';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_claim_sign_batch_item' AND COLUMN_NAME='plan_id') THEN
    ALTER TABLE t_claim_sign_batch_item ADD COLUMN plan_id BIGINT DEFAULT NULL COMMENT '计划ID（本地开发库沿用0）';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_claim_sign_batch_item' AND COLUMN_NAME='insured_name') THEN
    ALTER TABLE t_claim_sign_batch_item ADD COLUMN insured_name VARCHAR(128) DEFAULT NULL COMMENT '被保人姓名（实名，用户裁决）';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_claim_sign_batch_item' AND COLUMN_NAME='insured_id_no') THEN
    ALTER TABLE t_claim_sign_batch_item ADD COLUMN insured_id_no VARCHAR(64) DEFAULT NULL COMMENT '被保人证件号（实名，用户裁决）';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_claim_sign_batch_item' AND COLUMN_NAME='out_insure_time') THEN
    ALTER TABLE t_claim_sign_batch_item ADD COLUMN out_insure_time DATETIME(3) DEFAULT NULL COMMENT '出险时间（建案要素）';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_claim_sign_batch_item' AND COLUMN_NAME='claim_type') THEN
    ALTER TABLE t_claim_sign_batch_item ADD COLUMN claim_type VARCHAR(32) DEFAULT NULL COMMENT '险种';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_claim_sign_batch_item' AND COLUMN_NAME='source_channel') THEN
    ALTER TABLE t_claim_sign_batch_item ADD COLUMN source_channel VARCHAR(64) DEFAULT NULL COMMENT '来源渠道';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_claim_sign_batch_item' AND COLUMN_NAME='business_key') THEN
    ALTER TABLE t_claim_sign_batch_item ADD COLUMN business_key VARCHAR(128) DEFAULT NULL COMMENT '明细级建案幂等键';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_claim_sign_batch_item' AND COLUMN_NAME='case_status') THEN
    ALTER TABLE t_claim_sign_batch_item ADD COLUMN case_status VARCHAR(32) DEFAULT NULL COMMENT '自动建案结果：NEW/SKIPPED/AWAIT_MANUAL/FAILED';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_claim_sign_batch_item' AND COLUMN_NAME='created_case_id') THEN
    ALTER TABLE t_claim_sign_batch_item ADD COLUMN created_case_id BIGINT DEFAULT NULL COMMENT '自动建案生成的赔案ID';
  END IF;
END$$
DELIMITER ;
CALL sp_add_sign_item_cols_0145();
DROP PROCEDURE IF EXISTS sp_add_sign_item_cols_0145;

-- 幂等唯一索引：business_key 非空且租户内唯一（MySQL 唯一索引允许多 NULL）。
SET @has_uk = (SELECT COUNT(*) FROM information_schema.statistics
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_claim_sign_batch_item'
    AND INDEX_NAME='uk_sign_item_business_key');
SET @add_uk = IF(@has_uk = 0,
  'ALTER TABLE t_claim_sign_batch_item ADD UNIQUE KEY uk_sign_item_business_key (tenant_id, business_key)',
  'DO 0');
PREPARE stmt_0145 FROM @add_uk;
EXECUTE stmt_0145;
DEALLOCATE PREPARE stmt_0145;

-- 终态断言：10 列齐全 + 唯一索引存在，否则 SIGNAL 1644。
SELECT COUNT(*) INTO @sign_item_cols_0145
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_claim_sign_batch_item'
  AND COLUMN_NAME IN ('policy_no','plan_id','insured_name','insured_id_no','out_insure_time',
                      'claim_type','source_channel','business_key','case_status','created_case_id');
SELECT COUNT(*) INTO @sign_item_uk_0145
FROM information_schema.statistics
WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_claim_sign_batch_item'
  AND INDEX_NAME='uk_sign_item_business_key'
  AND SEQ_IN_INDEX = 2 AND COLUMN_NAME='business_key';

DROP PROCEDURE IF EXISTS sp_verify_0145;
DELIMITER $$
CREATE PROCEDURE sp_verify_0145()
BEGIN
  IF @sign_item_cols_0145 <> 10 THEN
    SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO = 1644,
      MESSAGE_TEXT = '0145 FAIL: sign item columns incomplete';
  END IF;
  IF @sign_item_uk_0145 <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO = 1644,
      MESSAGE_TEXT = '0145 FAIL: sign item business_key unique index missing';
  END IF;
END$$
DELIMITER ;
CALL sp_verify_0145();
DROP PROCEDURE IF EXISTS sp_verify_0145;
