-- ============================================================
-- 0103_synap_claim_stp_manual_review.sql
--
-- 一句话：给 t_claim_case 补 `manual_review_required` 列（STP 闸门标记，主设计 §6.9）。
--
-- 背景（STP 闸门落地的落库缺口）
-- ----------------------------------------------------------------
-- 映射解析内核已交付（resolve + miss 入队 + 重解析），但「映射质量差却码已解析」的案子
-- 仍会静默进入自动理算——缺一个"业务风险开关"。STP 闸门（§6.9）把映射质量翻译成
-- 风险开关：按 (保司×域) 的命中率/直通率/OPEN 未命中/近7天纠错/冲突数五把尺，任一不达标
-- 即强制该域赔案转人工。本列是开关的落库位（收单时写，核赔路由时读）。
--
-- 为什么是 TINYINT(1) 不是布尔
-- ----------------------------------------------------------------
-- 与 consent_withdrawn_flag / hang_up_status / deleted 同口径落 TINYINT（见 ClaimCase 注释）：
-- 避免"枚举带 code 落 varchar 列 ⇒ 建得进读不出"那类历史坑；0=可直通，1=转人工。
-- 非 NULL DEFAULT 0：存量行无需回填，且 SDK 按实体元数据生成 SELECT/INSERT 列，缺默认值
-- 会让存量行按隐式规则填充（NOT NULL TINYINT ⇒ 0），显式声明是对口径的钉死。
--
-- 幂等
-- -------------------------------------------------------------
-- MySQL 的 ADD COLUMN 无 IF NOT EXISTS，information_schema 判定 + 动态 SQL；
-- 末尾断言列存在且带默认值（存量行自动 0 = 可直通，无需回填）。
-- ============================================================

-- ─────────────────────────────────────────────────────────────
-- 一、加列（幂等）
-- ─────────────────────────────────────────────────────────────
SET @ddl_0103_1 := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 't_claim_case'
        AND column_name = 'manual_review_required') = 0,
    'ALTER TABLE `t_claim_case` ADD COLUMN `manual_review_required` TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''STP闸门标记(主设计§6.9):收单时该(保司×域)映射质量不达标强制转人工;0=可直通'' AFTER `mapping_version`',
    'DO 0');
PREPARE s1 FROM @ddl_0103_1; EXECUTE s1; DEALLOCATE PREPARE s1;

-- ─────────────────────────────────────────────────────────────
-- 二、终态断言（列必须在、必须带默认值——存量行无需回填）
-- ─────────────────────────────────────────────────────────────
DELIMITER $$

DROP PROCEDURE IF EXISTS sp_verify_0103$$
CREATE PROCEDURE sp_verify_0103()
BEGIN
    DECLARE v_missing INT DEFAULT 0;

    SELECT COUNT(*) INTO v_missing
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 't_claim_case'
       AND column_name = 'manual_review_required';
    IF v_missing <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0103 校验失败：manual_review_required 列缺失';
    END IF;

    SELECT COUNT(*) INTO v_missing
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 't_claim_case'
       AND column_name = 'manual_review_required'
       AND column_default IS NOT NULL;
    IF v_missing <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0103 校验失败：manual_review_required 列缺少默认值（存量行回填口径不明确）';
    END IF;
END$$
DELIMITER ;

CALL sp_verify_0103();
DROP PROCEDURE IF EXISTS sp_verify_0103;
