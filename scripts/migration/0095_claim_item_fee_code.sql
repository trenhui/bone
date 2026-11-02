-- ============================================================
-- 0095_claim_item_fee_code.sql
--
-- 一句话：给费用明细项 t_claim_item 增加 fee_code 列（费用类别编码），
--        作为责任「费用类别规则（手术费 100%、材料费 60% 且限 5000）」的唯一判定维度。
--
-- 背景（为什么必须有这一列）
-- ----------------------------------------------------------------
-- 0094 已建 t_adj_liability_fee_rule（责任 × 费用类别差异化规则），但理算引擎的归集口径只有
-- 「医保范围内 / 自付二 / 总自费」三维，无法把"手术费""材料费"分开。要让类别规则真正生效，
-- 明细必须带一个"这是哪类费用"的事实——而它只能由报案端提供：
--   · 明细已有 category（DRUG/SERVICE/EXAM/MATERIAL 四分类粗目），但粗目无法反推十类收费类别
--     （SERVICE 一项就横跨床位/手术/治疗/护理/诊查/化验），用粗目猜细类别 = 让引擎猜，猜错即错赔；
--   · 故新增 fee_code（取 t_ref_fee_category.fee_code：床位费/检查费/手术费…十类），与 category 并存。
--   缺失时（空串 = 未分类）引擎按责任统一比例算并落诊断，绝不按粗目猜类别。
--
-- 口径
-- ----------------------------------------------------------------
--   · 列口径与 ClaimItem#feeCode / 0094 的 t_ref_fee_category.fee_code 完全一致；
--   · 默认值空串：存量明细（历史案件）统一按"未分类"处理，行为 = 引入类别规则前，无破坏性；
--   · 幂等：information_schema 探测 + PREPARE 动态 DDL（0085/0090/0094 同范式）；
--   · bone-init.sql 同提交在 t_claim_item 补该列（G39 门禁只比表级终态，列是盲区，须手动对齐）。
--
-- 执行（本地/测试库直接应用；生产数据变更属 L3，由 DBA / CI 在目标库运行）：
--   mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0095_claim_item_fee_code.sql
-- ============================================================

USE bone;

-- ─────────────────────────────────────────────────────────────
-- ① t_claim_item.fee_code（幂等 ALTER，0085/0090/0094 范式）
-- ─────────────────────────────────────────────────────────────
SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE t_claim_item ADD COLUMN fee_code VARCHAR(32) NOT NULL DEFAULT '''' COMMENT ''费用类别编码(责任费用规则判定维度; 空=未分类)'' AFTER category',
    'DO 0'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_claim_item' AND COLUMN_NAME = 'fee_code'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ─────────────────────────────────────────────────────────────
-- ② 终态断言（列确实就位；缺列会让理算按"未分类"静默错算，必须 fail-fast）
-- ─────────────────────────────────────────────────────────────
DROP PROCEDURE IF EXISTS sp_verify_0095;
DELIMITER $$
CREATE PROCEDURE sp_verify_0095()
BEGIN
    DECLARE v_col INT DEFAULT 0;

    SELECT COUNT(*) INTO v_col
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_claim_item' AND COLUMN_NAME = 'fee_code';
    IF v_col <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0095 校验失败：t_claim_item.fee_code 列未就位';
    END IF;
END$$
DELIMITER ;

CALL sp_verify_0095();
DROP PROCEDURE IF EXISTS sp_verify_0095;
