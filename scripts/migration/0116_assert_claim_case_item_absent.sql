-- =============================================================================
-- 0116_assert_claim_case_item_absent.sql
--
-- 目的：把「早期原型表 t_claim_case_item 已被清理」这一终态，固化成**可执行断言**。
--
-- 背景（文档漂移，非缺陷）
-- ------------------------
-- 0058_synap_claim_tables.sql §0 的注释写着：
--   「0047_ui_schema_demo_seed.sql 仅在 meta_entity 目录登记旧 demo 实体名，非物理 DDL」
-- 这句话与事实矛盾。0047 实际建了**三张物理表**：
--   ① bak_0047_uischema  —— meta_entity_ui_schema 的空壳备份表
--   ② t_claim_case       —— 早期原型（26 列，含已废弃的 insured_name 明文 PII 等）
--   ③ t_claim_case_item  —— 早期原型子表（10 列，现行名为 t_claim_item）
-- 而 0058 §0 恰恰 DROP 了 ③（`DROP TABLE IF EXISTS t_claim_case_item;`）——
-- 注释说 0047「非物理 DDL」，却又 DROP 它建的表，自相矛盾。
--
-- 为什么用新迁移修，而不是回改 0058 的注释
-- ----------------------------------------
-- scripts/migrate.py 按 **sha256(文件字节)** 记执行账本：已应用脚本的任何改动
-- （哪怕只改一行注释）都会让所有已应用环境报「校验和不符」并拒绝继续 apply。
-- 业界惯例（Flyway / Liquibase）同样是「已发迁移不回改，用新迁移修」——
-- 回改会让历史执行记录失去可复现性。
--
-- 本脚本做什么
-- ------------
-- 不做任何 DDL 变更，只做**终态断言**：
--   t_claim_case_item 不存在 ⇒ 早期原型子表已清理，通过；
--   仍存在 ⇒ 中止（fail-closed），并打印处置清单。
--
-- 为什么中止而不是「顺手 DROP」
-- --------------------------
-- 0058 的处置决策是「该表 0 行、无任何现行代码引用，故 DROP 可承受」。
-- 但断言时点的库可能已有数据（人工灌过、或别的环境建过）——此时静默 DROP 会丢数据。
-- 所以这里只**判红并交人工**：由人确认是空表后手动 DROP，或补一条显式迁移。
-- 这与 0058 §0.2 对 t_claim_case 的处置逻辑一致（形状不符即中止，交人工 RENAME 备份后重建）。
--
-- 幂等：纯断言，无副作用，可重复执行。
-- =============================================================================

USE bone;

-- ── 终态断言：早期原型子表必须已被清理 ───────────────────────────────────────
-- 注意：SIGNAL 的 MESSAGE_TEXT 有 **128 字符上限**（超长报 ERROR 1648 Data too long
-- for condition item 'MESSAGE_TEXT'），故消息只放"结论 + 处置动作"，
-- 详细的背景与理由写在上方注释里。
DROP PROCEDURE IF EXISTS tmp_assert_0116;

DELIMITER $$
CREATE PROCEDURE tmp_assert_0116()
BEGIN
    DECLARE v_exists INT DEFAULT 0;

    SELECT COUNT(*) INTO v_exists
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 't_claim_case_item';

    IF v_exists > 0 THEN
        SET @msg := CONCAT(
            't_claim_case_item still exists (0047 legacy table). ',
            'Not auto-dropped - may hold data. ',
            'If empty: DROP TABLE t_claim_case_item;'
        );
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = @msg;
    END IF;
END$$
DELIMITER ;

CALL tmp_assert_0116();
DROP PROCEDURE IF EXISTS tmp_assert_0116;

-- ── 说明性记录（供人工查阅，不参与判定） ─────────────────────────────────────
-- 0047 建的另外两张表不做断言，理由各不相同：
--   · t_claim_case —— **现行主表**，由 0058 以 37 列形态重建并做形状断言（§0.2）。
--     它必须存在，且形状必须与 0058 一致；断言它的存在由 0058 负责，此处重复断言会
--     掩盖「若 0058 未执行则本迁移也不该独断」的责任边界。
--   · bak_0047_uischema —— 空壳备份表（`SELECT ... WHERE 1=0`），无数据、无引用，
--     留着无害；清理它是运维动作，不是数据正确性不变量。
