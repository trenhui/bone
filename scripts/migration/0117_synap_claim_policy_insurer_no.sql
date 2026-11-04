-- ============================================================
-- 0117_synap_claim_policy_insurer_no.sql
--
-- 一句话：给 t_claim_policy 补 `insurer_policy_no` 列（§16-2 双保单号：policy_no 平台号 + insurer_policy_no 保司号）。
--
-- 背景
-- ----------------------------------------------------------------
-- 此前 t_claim_policy 只存 policy_no（平台号），保司侧保单号无处落库，
-- 导致按保司号检索/匹配时只能回退到 policy_no。双保单号方案 B（冗余列）：
-- 保留 policy_no 为平台号不动，新增 insurer_policy_no 承载保司侧保单号，
-- 迁移面最小（不加外键、不改既有索引），写入期双号各自落库、互不影响。
--
-- 为什么 VARCHAR(64) NOT NULL DEFAULT ''
-- ----------------------------------------------------------------
-- 与 policy_no 同长度档（保司号可能含字母+数字）；NOT NULL DEFAULT '' 让存量行无需回填
-- 即可通过 SDK 元数据生成的 SELECT/INSERT（缺默认值会让 SDK 按隐式规则填充）；
-- 空串表示「保司号尚未登记」，查询时按保司号检索自然不匹配存量行，待保司重新登记时填写。
--
-- 幂等
-- -------------------------------------------------------------
-- MySQL 的 ADD COLUMN 无 IF NOT EXISTS，information_schema 判定 + 动态 SQL；
-- 末尾断言列存在且带默认值（存量行自动 '' = 未登记，符合条件）。
-- ============================================================

-- ─────────────────────────────────────────────────────────────
-- 一、加列（幂等）
-- ─────────────────────────────────────────────────────────────
SET @ddl_0117_1 := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 't_claim_policy'
        AND column_name = 'insurer_policy_no') = 0,
    'ALTER TABLE `t_claim_policy` ADD COLUMN `insurer_policy_no` VARCHAR(64) NOT NULL DEFAULT '''' COMMENT ''双保单号保司侧保单号'' AFTER `policy_no`',
    'DO 0');
PREPARE s1 FROM @ddl_0117_1; EXECUTE s1; DEALLOCATE PREPARE s1;

-- ─────────────────────────────────────────────────────────────
-- 二、终态断言（列必须在、必须带默认值——存量行无需回填）
-- ─────────────────────────────────────────────────────────────
DELIMITER $$

DROP PROCEDURE IF EXISTS sp_verify_0117$$
CREATE PROCEDURE sp_verify_0117()
BEGIN
    DECLARE v_missing INT DEFAULT 0;

    SELECT COUNT(*) INTO v_missing
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 't_claim_policy'
       AND column_name = 'insurer_policy_no';
    IF v_missing <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0117 校验失败：insurer_policy_no 列缺失';
    END IF;

    SELECT COUNT(*) INTO v_missing
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 't_claim_policy'
       AND column_name = 'insurer_policy_no'
       AND column_default IS NOT NULL;
    IF v_missing <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0117 校验失败：insurer_policy_no 列缺少默认值（存量行回填口径不明确）';
    END IF;
END$$
DELIMITER ;

CALL sp_verify_0117();
DROP PROCEDURE IF EXISTS sp_verify_0117;
