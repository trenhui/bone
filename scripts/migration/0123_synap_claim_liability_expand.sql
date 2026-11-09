-- ============================================================
-- 0123_synap_claim_liability_expand.sql
--
-- 一句话：给 t_adj_liability 增加 1 个聚合列 liability_rule_json（JSON NULL），
--         把「责任要素」从标量升级为 9 个键的可扩展表达 —— M2 ① Expand。
--
-- 为什么是一列而不是九列
-- ----------------------------------------------------------------
-- D5 已拍板（2026-11-09）：九列方案在 G3 自设的可证伪判据下自我否证
-- （「必须 ALTER TABLE 才能落地则 ② 不成立」，九列下新增一类要素仍要
-- ADD COLUMN），且本仓实测零 JSON codec —— 「九列改动面更小」不成立
-- （codec / Schema / 键级回落 / 消费方全都要从零写）。
-- 9 个业务要素是这一个列的 9 个键；键的缺省语义分域（A 必需参数 fail-closed /
-- B 可选维度缺键不施加约束 / 未知键与未知版本拒）见
-- 《总体技术方案》§9.1.1 键分类表（唯一真源，本脚本不复述）。
--
-- 为什么 nullable、无默认值、不做回填
-- ----------------------------------------------------------------
-- 历史行的要素已按旧标量列固化，回填只是把同一语义抄一遍：收益为零，
-- 风险却要逐行承担（任一行抄错就是静默少赔，且不会报错）。
-- 保留 NULL 使「历史行 = NULL ⇒ 读侧按键回落旧标量列」成为结构保证而非纪律。
-- 也不为新键编造默认值：B 类键「缺键 = 不施加该约束」与「给 0」语义相反，
-- 编造 0 等于凭空新增拒赔条件。
--
-- 为什么旧标量列一列都不动
-- ----------------------------------------------------------------
-- 本阶段只加列：不改类型、不建 _rule 影子列、不删旧列、不同时做 contract。
-- 旧代码对新列完全无感 ⇒ 线上读写零行为变更；回滚只需 DROP 本列，无数据损失。
-- 读侧切主在 ③ Dual-read，超集解锁在 ④，均不在本脚本。
--
-- 终态断言（fail-closed）
-- ----------------------------------------------------------------
-- 「SQL 没报错」不等于「做到」：information_schema 强断言两件事——
--   ① 新列存在，且类型为 json、可空；
--   ② 三个可映射旧标量列仍在（防后人把「加列」误写成「改列」）。
-- 最后一行断言与第一条同等重要：本阶段的正确性由「新旧共存」保证，
-- 旧列一旦被动过，回滚窗口就没了。
-- ============================================================

ALTER TABLE `t_adj_liability`
  ADD COLUMN `liability_rule_json` JSON DEFAULT NULL
  COMMENT '责任要素聚合列(9要素键+schemaVersion;NULL=历史行或未配置,读侧按键回落旧标量列)'
  AFTER `liability_limit`;

-- 终态断言。IF 只能在过程上下文使用：临时过程 + CALL + DROP，不留过程体。
DELIMITER $$
DROP PROCEDURE IF EXISTS sp_verify_0123$$
CREATE PROCEDURE sp_verify_0123()
BEGIN
    DECLARE v_new INT DEFAULT 0;
    DECLARE v_old INT DEFAULT 0;

    SELECT COUNT(*) INTO v_new
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 't_adj_liability'
       AND column_name = 'liability_rule_json'
       AND data_type = 'json'
       AND is_nullable = 'YES';
    IF v_new <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0123 assert failed: liability_rule_json missing, not json or not nullable';
    END IF;

    SELECT COUNT(*) INTO v_old
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 't_adj_liability'
       AND column_name IN ('pay_percent', 'liability_deduct', 'liability_limit');
    IF v_old <> 3 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0123 assert failed: legacy scalars must all stay (percent/deduct/limit)';
    END IF;
END$$
DELIMITER ;

CALL sp_verify_0123();
DROP PROCEDURE IF EXISTS sp_verify_0123;
