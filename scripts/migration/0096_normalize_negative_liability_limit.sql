-- ============================================================
-- 0096_normalize_negative_liability_limit.sql
--
-- 一句话：把 t_adj_liability 里 liability_limit < 0 的存量归一为 0（=「不封顶」），
--        与配置期收紧（负数额度直接拒绝）配套。
--
-- 背景（为什么要有这一支）
-- ----------------------------------------------------------------
-- 责任额度上限的「负数」语义历史上有两个来源：
--   ① 早期实现把 limit <= 0 一律解释为「不封顶」（含负数），配置期也放行负数；
--   ② reference-impl S25 的契约其实是「额度 **0** 表示不限制」——负数从未被定义过。
-- 于是负数成了一个「隐式哨兵」：运营手滑写 -100，该责任就静默变成无限额赔付
-- （赔得**更多**，风险方向与免赔/日限额相反），而系统毫无提示、配置面还显示"已配置"。
--
-- 本次配套（同一批次的两端）：
--   · 配置期：LiabilityConfigApplicationService#assertRuleUsable 拒绝负数额度
--     （0 与不填仍合法 —— 那才是「不封顶」的明确写法）；
--   · 存量期（本脚本）：把存量负数归一为 0。
--
-- 为什么归 0 是零行为变更
-- ----------------------------------------------------------------
-- 理算期判据是 liabilityLimit.signum() <= 0 ⇒ 不封顶（LiabilityRule#liabilityUncapped），
-- 负数与 0 在理算期本就同义 ⇒ 归一化后**任何赔案的赔付金额完全不变**。
-- 本脚本只把「不可见的歧义」变成「明确的 0」，不改变任何业务结果。
--
-- 乐观锁
-- ----------------------------------------------------------------
-- 同步 bump version：让任何仍持有旧 version 的应用实例在下次编辑时必然冲突并重读，
-- 不会用陈旧对象把修复结果覆盖回去（数据修复与乐观锁共存的标准做法）。
--
-- 幂等：可重复执行（第二次影响 0 行）；DDL 不涉及，无需 information_schema 探测。
--
-- 执行（生产数据变更属 L3，由 DBA / CI 在目标库运行）：
--   mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0096_normalize_negative_liability_limit.sql
-- ============================================================

USE bone;

-- ─────────────────────────────────────────────────────────────
-- ① 审计留痕：先列出将被归一化的行（执行日志里可核对"改了什么"）
-- ─────────────────────────────────────────────────────────────
SELECT id,
       liability_code,
       liability_limit AS old_liability_limit,
       0                AS new_liability_limit,
       version          AS old_version
  FROM t_adj_liability
 WHERE liability_limit < 0
 ORDER BY id;

-- ─────────────────────────────────────────────────────────────
-- ② 归一化：负数额度 → 0（= 不封顶），并 bump 乐观锁版本
-- ─────────────────────────────────────────────────────────────
UPDATE t_adj_liability
   SET liability_limit = 0,
       version = version + 1
 WHERE liability_limit < 0;

-- ─────────────────────────────────────────────────────────────
-- ③ 终态断言：不得再有负数额度
--    （若有 ⇒ 说明仍有写入路径漏掉了配置期守卫，必须 fail-fast 而不是静默放过）
-- ─────────────────────────────────────────────────────────────
DROP PROCEDURE IF EXISTS sp_verify_0096;
DELIMITER $$
CREATE PROCEDURE sp_verify_0096()
BEGIN
    DECLARE v_neg INT DEFAULT 0;

    SELECT COUNT(*) INTO v_neg
      FROM t_adj_liability
     WHERE liability_limit < 0;

    IF v_neg <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0096 校验失败：t_adj_liability 仍存在 liability_limit < 0 的行（配置期守卫有漏网写入路径）';
    END IF;
END$$
DELIMITER ;

CALL sp_verify_0096();
DROP PROCEDURE IF EXISTS sp_verify_0096;
