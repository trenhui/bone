-- ============================================================
-- 0147_claim_sign_tpa_parity.sql
--
-- 一句话：按普康 TPA 团险签收页面补齐批次与赔案明细的可读业务字段。
--
-- 口径
-- ----------------------------------------------------------------
--   · 批次补保险公司/保险分公司/投保公司/紧急程度，全部 VARCHAR 可空；
--   · 明细补证件类型、清点张数、影像数量/上传次数/上传时间/操作人员；
--   · 影像是否上传不冗余存布尔，由 image_ready_count / claim_count 派生；
--   · 源站脏数据（编码值、乱码）由导入方归一，领域模型只存可读名称；
--   · 逐列条件 ADD COLUMN，可重复执行；终态列数断言用存储过程 SIGNAL(1644)。
-- ============================================================

USE bone;

SET @db = DATABASE();

DROP PROCEDURE IF EXISTS sp_add_col_0147;
DELIMITER $$
CREATE PROCEDURE sp_add_col_0147(IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_ddl VARCHAR(512))
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = p_table AND COLUMN_NAME = p_column
  ) THEN
    SET @stmt_0147 = p_ddl;
    PREPARE st_0147 FROM @stmt_0147;
    EXECUTE st_0147;
    DEALLOCATE PREPARE st_0147;
  END IF;
END$$
DELIMITER ;

CALL sp_add_col_0147('t_claim_sign_batch', 'insurance_company',
  'ALTER TABLE t_claim_sign_batch ADD COLUMN insurance_company VARCHAR(128) DEFAULT NULL COMMENT ''保险公司名称'' AFTER sign_channel');
CALL sp_add_col_0147('t_claim_sign_batch', 'insurance_subsidiary',
  'ALTER TABLE t_claim_sign_batch ADD COLUMN insurance_subsidiary VARCHAR(128) DEFAULT NULL COMMENT ''保险分公司名称'' AFTER insurance_company');
CALL sp_add_col_0147('t_claim_sign_batch', 'insuring_agency',
  'ALTER TABLE t_claim_sign_batch ADD COLUMN insuring_agency VARCHAR(128) DEFAULT NULL COMMENT ''投保公司名称'' AFTER insurance_subsidiary');
CALL sp_add_col_0147('t_claim_sign_batch', 'emergency',
  'ALTER TABLE t_claim_sign_batch ADD COLUMN emergency VARCHAR(32) DEFAULT NULL COMMENT ''紧急程度'' AFTER claim_count');

CALL sp_add_col_0147('t_claim_sign_batch_item', 'identity_type',
  'ALTER TABLE t_claim_sign_batch_item ADD COLUMN identity_type VARCHAR(32) DEFAULT NULL COMMENT ''证件类型'' AFTER insured_id_no');
CALL sp_add_col_0147('t_claim_sign_batch_item', 'counting_result',
  'ALTER TABLE t_claim_sign_batch_item ADD COLUMN counting_result INT DEFAULT NULL COMMENT ''清点张数'' AFTER identity_type');
CALL sp_add_col_0147('t_claim_sign_batch_item', 'image_count',
  'ALTER TABLE t_claim_sign_batch_item ADD COLUMN image_count INT DEFAULT NULL COMMENT ''影像数量'' AFTER counting_result');
CALL sp_add_col_0147('t_claim_sign_batch_item', 'image_upload_count',
  'ALTER TABLE t_claim_sign_batch_item ADD COLUMN image_upload_count INT DEFAULT NULL COMMENT ''影像上传次数'' AFTER image_count');
CALL sp_add_col_0147('t_claim_sign_batch_item', 'image_upload_time',
  'ALTER TABLE t_claim_sign_batch_item ADD COLUMN image_upload_time DATETIME(3) DEFAULT NULL COMMENT ''影像上传时间'' AFTER image_upload_count');
CALL sp_add_col_0147('t_claim_sign_batch_item', 'image_uploader',
  'ALTER TABLE t_claim_sign_batch_item ADD COLUMN image_uploader VARCHAR(64) DEFAULT NULL COMMENT ''影像操作人员'' AFTER image_upload_time');

DROP PROCEDURE IF EXISTS sp_add_col_0147;

SELECT COUNT(*) INTO @column_count_0147
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @db
  AND (
    (TABLE_NAME = 't_claim_sign_batch' AND COLUMN_NAME IN ('insurance_company','insurance_subsidiary','insuring_agency','emergency'))
    OR
    (TABLE_NAME = 't_claim_sign_batch_item' AND COLUMN_NAME IN ('identity_type','counting_result','image_count','image_upload_count','image_upload_time','image_uploader'))
  );

DROP PROCEDURE IF EXISTS sp_verify_0147;
DELIMITER $$
CREATE PROCEDURE sp_verify_0147()
BEGIN
  IF @column_count_0147 <> 10 THEN
    SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO = 1644,
      MESSAGE_TEXT = '0147 FAIL: sign parity columns incomplete';
  END IF;
END$$
DELIMITER ;
CALL sp_verify_0147();
DROP PROCEDURE IF EXISTS sp_verify_0147;
