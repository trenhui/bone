-- =============================================================================
-- 0140_synap_claim_drop_case_report_no_redundant.sql
--
-- 为什么需要：
--   并行开发的另一条分支（comet/claim-id-linkage）在 0139 里给 t_claim_case
--   额外加了一列 report_no 作为「冗余展示键」，其读侧 SQL 直接 SELECT c.report_no。
--   本次落地的方案（0139_synap_claim_report_case_split.sql）不引入该列：报案号
--   的唯一真源是 t_claim_report.report_no，读侧用 LEFT JOIN t_claim_report 取真值。
--
--   两套 0139 都曾在共享活库上被应用过，于是库里留下一条本方案完全不消费的孤儿列。
--   留着它的实际危害不是「多占空间」，而是：
--     1. 它是「报案号有两个真源」的物证。任何人看到它都会认为需要维护它，
--        而本方案的所有写入路径都不会写它 —— 它只会越来越旧、越来越假。
--     2. 若将来有人按它写读路径，会读到一份从不更新的历史快照，且没有任何约束
--        能发现分叉（对比：report_id 有 NOT NULL 可查，report_no 什么都没有）。
--
-- 为什么是「新迁移」而不是回改 0139：
--   migrate.py 按 sha256 记 journal，回改已应用脚本会让全环境报校验和不符。
--   修正一律写新迁移。
--
-- 安全前提（fail-closed，不做任何破坏性自动化）：
--   · 列不存在 → 整条迁移 no-op（幂等，可重复执行）；
--   · 列存在且有值 → 先把有值行清单打出来，再 SIGNAL 中止。存量脏数据不自动删；
--   · 列存在且全空 → DROP COLUMN，并做终态断言确认列已消失。
--
-- 为什么用存储过程而不是 PREPARE + SIGNAL：
--   早期写法把 'SIGNAL SQLSTATE ...' 当字符串交给 PREPARE，MySQL 在执行时返回
--   1295「This command is not supported in the prepared statement protocol yet」。
--   中止行为虽然还在（迁移确实停了、数据确实没丢），但报错信息与意图完全无关，
--   后来的人只会看到一句 1295 而无从判断该改什么。SIGNAL 只能出现在存储过程/
--   触发器体内，故改为「建过程 → CALL → DROP」，这也是本仓 62 个迁移的既有写法。
--
-- 与 bone-init.sql / 模块参考态的关系：
--   两者本来就没有该列（本方案的最终态形状），故无需改动。
-- =============================================================================

-- ---------- §1 守卫：列存在且有值则先打清单再中止 ----------
DROP PROCEDURE IF EXISTS chk_0140_report_no_guard;
DELIMITER $$
CREATE PROCEDURE chk_0140_report_no_guard()
BEGIN
    DECLARE v_has INT DEFAULT 0;
    DECLARE v_valued BIGINT DEFAULT 0;

    SELECT COUNT(*) INTO v_has FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 't_claim_case'
       AND column_name = 'report_no';

    -- 嵌套 IF 而非 AND 短路：列不存在时内层 SELECT 根本不能执行（否则 1054）。
    IF v_has = 1 THEN
        SELECT COUNT(*) INTO v_valued FROM t_claim_case
         WHERE report_no IS NOT NULL AND report_no <> '';

        IF v_valued > 0 THEN
            -- 先输出清单，让执行者知道要迁的是哪些行，而不是只知道「失败了」。
            SELECT id AS claim_id, claim_no, report_no
              FROM t_claim_case
             WHERE report_no IS NOT NULL AND report_no <> ''
             LIMIT 50;

            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
              '0140 ABORT: t_claim_case.report_no has data, migrate it first';
        END IF;
    END IF;
END$$
DELIMITER ;

CALL chk_0140_report_no_guard();
DROP PROCEDURE IF EXISTS chk_0140_report_no_guard;

-- ---------- §2 删除孤儿列（幂等） ----------
-- 重新探测一次而不复用 §1 的局部变量：段与段解耦，单独重跑任何一段都成立。
SET @has_col = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 't_claim_case'
    AND column_name = 'report_no'
);
SET @drop_sql = IF(
  @has_col = 1,
  'ALTER TABLE t_claim_case DROP COLUMN report_no',
  'DO 0'
);
PREPARE drop_stmt FROM @drop_sql;
EXECUTE drop_stmt;
DEALLOCATE PREPARE drop_stmt;

-- ---------- §3 终态断言：列必须已消失 ----------
-- 「执行成功」不等于「达成目标」：§2 走的是动态 SQL + IF，条件写反或 @has_col
-- 取值异常时，语句会以一个无害的 DO 0 通过而列还在。必须独立断言终态。
DROP PROCEDURE IF EXISTS chk_0140_report_no_gone;
DELIMITER $$
CREATE PROCEDURE chk_0140_report_no_gone()
BEGIN
    DECLARE v_still INT DEFAULT 0;

    SELECT COUNT(*) INTO v_still FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 't_claim_case'
       AND column_name = 'report_no';

    IF v_still > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
          '0140 FAIL: t_claim_case.report_no still present after drop';
    END IF;
END$$
DELIMITER ;

CALL chk_0140_report_no_gone();
DROP PROCEDURE IF EXISTS chk_0140_report_no_gone;

SELECT '0140 OK: t_claim_case.report_no 冗余列已清理，报案号回归单一真源' AS result;
