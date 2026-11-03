-- ============================================================
-- 0102_synap_claim_item_dual_code.sql
--
-- 一句话：给 t_claim_item / t_claim_case 补「双码回填列」（std_* / mapping_version），
--       让「收单落双码」（主设计 §6.5 / §14 P0 判据①③④）从「解析接口存在」
--       变成「解析结果有处可落」。
--
-- 背景（P0 验收判据的落库缺口）
-- ----------------------------------------------------------------
-- 映射解析内核（CodeMappingResolver / CodeMappingApplicationService.resolve）
-- 已交付且带 MISS 入队，但解析结果没有落库位：t_claim_item 只有 raw 侧的
-- fee_code（保司报的费用类别码），t_claim_case 只有 hospital_code / diagnosis_code
-- 两个 raw 列。收单侧就算调了解析，标准码也无处可写——「收单落双码」
-- 实际是「解析了但没落」，规则引擎与报表仍然拿不到标准码。
--
-- 为什么 raw 侧不加列
-- ----------------------------------------------------------------
-- 三处 raw 已有承载：明细 fee_code、案件 hospital_code / diagnosis_code。
-- 双码设计的本意是「raw 保留原值 + std 存翻译结果 + 版本可追溯」；
-- 重复加 raw_* 列只会制造同一码值的两个真源。
--
-- 空串语义（为什么 NOT NULL DEFAULT '' 而不是 NULL）
-- ----------------------------------------------------------------
-- 与 fee_code / hospital_code 的既有口径一致：空串 = 未解析（初始态），
-- 不是「解析失败但被容忍」。「解析失败不得回落 raw 或空串冒充标准码」由
-- 消费方闸门保证：理算/规则读 std 时，空串必须按「维度缺失」处理
-- （fail-closed），不得当成合法码值参与匹配。判据④（未命中不得回落空串/0）
-- 的「空串」指把空串当解析结果写进 std 的行为；未解析行 std 恒空串、
-- 解析成功行 std 必非空——两者由测试钉死。
--
-- mapping_version 用命中的映射行 version_no（发布即 supersede 产生新版本），
-- 回答「这行明细当时按哪条映射翻译」——已结案案件可复现，不随映射演进漂移。
--
-- 幂等
-- -------------------------------------------------------------
-- MySQL 的 ADD COLUMN 无 IF NOT EXISTS，information_schema 判定 + 动态 SQL；
-- 末尾断言 5 列齐全且带默认值（存量行自动 '' = 未解析，无需回填）。
-- ============================================================

-- ─────────────────────────────────────────────────────────────
-- 一、加列（幂等，5 列逐个判定）
-- ─────────────────────────────────────────────────────────────
SET @ddl_0102_1 := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 't_claim_item'
        AND column_name = 'std_fee_code') = 0,
    'ALTER TABLE `t_claim_item` ADD COLUMN `std_fee_code` VARCHAR(64) NOT NULL DEFAULT '''' COMMENT ''标准费用类别码(映射解析回填;空串=未解析,消费方须按维度缺失fail-closed)'' AFTER `fee_code`',
    'DO 0');
PREPARE s1 FROM @ddl_0102_1; EXECUTE s1; DEALLOCATE PREPARE s1;

SET @ddl_0102_2 := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 't_claim_item'
        AND column_name = 'mapping_version') = 0,
    'ALTER TABLE `t_claim_item` ADD COLUMN `mapping_version` VARCHAR(64) NOT NULL DEFAULT '''' COMMENT ''命中的映射行版本号(回放/复算依据;空串=未解析)'' AFTER `std_fee_code`',
    'DO 0');
PREPARE s2 FROM @ddl_0102_2; EXECUTE s2; DEALLOCATE PREPARE s2;

SET @ddl_0102_3 := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 't_claim_case'
        AND column_name = 'std_hospital_code') = 0,
    'ALTER TABLE `t_claim_case` ADD COLUMN `std_hospital_code` VARCHAR(64) NOT NULL DEFAULT '''' COMMENT ''标准医院码(映射解析回填;空串=未解析)'' AFTER `diagnosis_code`',
    'DO 0');
PREPARE s3 FROM @ddl_0102_3; EXECUTE s3; DEALLOCATE PREPARE s3;

SET @ddl_0102_4 := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 't_claim_case'
        AND column_name = 'std_diagnosis_code') = 0,
    'ALTER TABLE `t_claim_case` ADD COLUMN `std_diagnosis_code` VARCHAR(64) NOT NULL DEFAULT '''' COMMENT ''标准诊断码(ICD-10 医保版;映射解析回填;空串=未解析)'' AFTER `std_hospital_code`',
    'DO 0');
PREPARE s4 FROM @ddl_0102_4; EXECUTE s4; DEALLOCATE PREPARE s4;

SET @ddl_0102_5 := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 't_claim_case'
        AND column_name = 'mapping_version') = 0,
    'ALTER TABLE `t_claim_case` ADD COLUMN `mapping_version` VARCHAR(64) NOT NULL DEFAULT '''' COMMENT ''命中的映射行版本号(回放/复算依据;空串=未解析)'' AFTER `std_diagnosis_code`',
    'DO 0');
PREPARE s5 FROM @ddl_0102_5; EXECUTE s5; DEALLOCATE PREPARE s5;

-- ─────────────────────────────────────────────────────────────
-- 二、终态断言（5 列必须在、必须带默认值——存量行无需回填）
-- ─────────────────────────────────────────────────────────────
DELIMITER $$

DROP PROCEDURE IF EXISTS sp_verify_0102$$
CREATE PROCEDURE sp_verify_0102()
BEGIN
    DECLARE v_missing INT DEFAULT 0;

    SELECT COUNT(*) INTO v_missing
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND ((table_name = 't_claim_item'
             AND column_name IN ('std_fee_code', 'mapping_version'))
         OR (table_name = 't_claim_case'
             AND column_name IN ('std_hospital_code', 'std_diagnosis_code', 'mapping_version')));
    IF v_missing <> 5 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0102 校验失败：双码列未齐（期望 item 2 列 + case 3 列）';
    END IF;

    -- 必须有默认值：ALTER 加列时存量行按默认值填充；若列无默认值，
    -- MySQL 会用隐式规则填充（NOT NULL VARCHAR ⇒ ''），显式声明是对口径的钉死。
    SELECT COUNT(*) INTO v_missing
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND ((table_name = 't_claim_item'
             AND column_name IN ('std_fee_code', 'mapping_version'))
         OR (table_name = 't_claim_case'
             AND column_name IN ('std_hospital_code', 'std_diagnosis_code', 'mapping_version')))
       AND column_default IS NULL;
    IF v_missing > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0102 校验失败：双码列缺少默认值（存量行回填口径不明确）';
    END IF;
END$$
DELIMITER ;

CALL sp_verify_0102();
DROP PROCEDURE IF EXISTS sp_verify_0102;

-- ─────────────────────────────────────────────────────────────
-- 三、验证探针（手工执行，非脚本自动断言）
-- ─────────────────────────────────────────────────────────────
-- 读路径（列缺失时这里 ERROR 1054）：
--   SELECT fee_code, std_fee_code, mapping_version FROM t_claim_item LIMIT 1;
--   SELECT hospital_code, std_hospital_code, std_diagnosis_code, mapping_version
--     FROM t_claim_case LIMIT 1;
--
-- 写路径（解析回填往返）：
--   UPDATE t_claim_item SET std_fee_code='FEE_DRUG', mapping_version='V1'
--    WHERE id = <probe_id> AND std_fee_code = '';
--   SELECT std_fee_code FROM t_claim_item WHERE id = <probe_id>;
--   UPDATE t_claim_item SET std_fee_code='', mapping_version='' WHERE id = <probe_id>;
