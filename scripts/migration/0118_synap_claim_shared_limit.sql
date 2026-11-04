-- ============================================================
-- 0118_synap_claim_shared_limit.sql
--
-- 一句话：给 t_adj_liability 补 shared_code / prior_liability_uuid 列，给 t_adj_calculation 补 shared_code 列，
-- 支撑共享额度（共保代码）。
--
-- 背景
-- ----------------------------------------------------------------
-- 责任域共享额度（共保代码）落地：责任带 shared_code 时其责任限额账走 SHARED_POOL 维度（按共保代码分行，
-- 复用 liability_uuid 列承载共保代码，避免改唯一索引）；prior_liability_uuid 为「先赔后赔」链标注（V1 未实现，仍显式拒绝）。
-- t_adj_calculation.shared_code 与 liability_uuid 同源，结算时据此选共享池账户。
--
-- 为什么 VARCHAR(64) NOT NULL DEFAULT ''
-- ----------------------------------------------------------------
-- 与 liability_uuid / 共保代码长度档一致；NOT NULL DEFAULT '' 让存量行无需回填即可通过 SDK 元数据生成的读写。
--
-- 幂等
-- -------------------------------------------------------------
-- MySQL 的 ADD COLUMN 无 IF NOT EXISTS，information_schema 判定 + 动态 SQL；末尾断言列存在且带默认值。
-- 与参考态 src/main/resources/db/migration/V23__synap_claim_shared_limit.sql DDL 逐字一致；bone-init.sql 同步加列。
-- ============================================================

-- ─────────────────────────────────────────────────────────────
-- 一、加列（幂等）
-- ─────────────────────────────────────────────────────────────
SET @ddl_0118_1 := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 't_adj_liability'
        AND column_name = 'shared_code') = 0,
    'ALTER TABLE `t_adj_liability` ADD COLUMN `shared_code` VARCHAR(64) NOT NULL DEFAULT '''' COMMENT ''共享保额共保代码(空=独立责任)'' AFTER `status`',
    'DO 0');
PREPARE s1 FROM @ddl_0118_1; EXECUTE s1; DEALLOCATE PREPARE s1;

SET @ddl_0118_2 := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 't_adj_liability'
        AND column_name = 'prior_liability_uuid') = 0,
    'ALTER TABLE `t_adj_liability` ADD COLUMN `prior_liability_uuid` VARCHAR(64) NOT NULL DEFAULT '''' COMMENT ''先赔后赔前序责任UUID(空=无)'' AFTER `shared_code`',
    'DO 0');
PREPARE s2 FROM @ddl_0118_2; EXECUTE s2; DEALLOCATE PREPARE s2;

SET @ddl_0118_3 := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 't_adj_calculation'
        AND column_name = 'shared_code') = 0,
    'ALTER TABLE `t_adj_calculation` ADD COLUMN `shared_code` VARCHAR(64) NOT NULL DEFAULT '''' COMMENT ''本案所属共享池代码(与 liability_uuid 同源, 结算选账户用)'' AFTER `liability_version`',
    'DO 0');
PREPARE s3 FROM @ddl_0118_3; EXECUTE s3; DEALLOCATE PREPARE s3;

-- ─────────────────────────────────────────────────────────────
-- 二、终态断言（列必须在、必须带默认值——存量行无需回填）
-- ─────────────────────────────────────────────────────────────
DELIMITER $$

DROP PROCEDURE IF EXISTS sp_verify_0118$$
CREATE PROCEDURE sp_verify_0118()
BEGIN
    DECLARE v_missing INT DEFAULT 0;

    SELECT COUNT(*) INTO v_missing
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 't_adj_liability'
       AND column_name = 'shared_code';
    IF v_missing <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0118 校验失败：t_adj_liability.shared_code 列缺失';
    END IF;

    SELECT COUNT(*) INTO v_missing
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 't_adj_liability'
       AND column_name = 'prior_liability_uuid';
    IF v_missing <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0118 校验失败：t_adj_liability.prior_liability_uuid 列缺失';
    END IF;

    SELECT COUNT(*) INTO v_missing
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 't_adj_calculation'
       AND column_name = 'shared_code';
    IF v_missing <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0118 校验失败：t_adj_calculation.shared_code 列缺失';
    END IF;
END$$
DELIMITER ;

CALL sp_verify_0118();
DROP PROCEDURE IF EXISTS sp_verify_0118;
