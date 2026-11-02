-- ============================================================
-- 0098_synap_claim_reconciliation_diff_version.sql
--
-- 一句话：给对账差异明细 t_reconciliation_diff 增加乐观锁列 version，
--        让「两个运维同时处置同一条差异」不再静默覆盖当前态。
--
-- 背景（这是被代码注释明确记为待办的一条，不是新需求）
-- ----------------------------------------------------------------
--   · ReconciliationDispositionService 类注释原文：
--     「两个运维同时处置同一条差异时，各自读到 PENDING 都可能通过前置检查（先读后写）。
--       后写者覆盖先写者的处置态……要彻底消除需给差异行加乐观锁列，
--       那要求先改生效通道的表结构（t_reconciliation_diff 现无 version 列），已记为待办。」
--   · 契约 §11.7 留痕边界① 与 §12.2.1「对账差异处置乐观锁」行同样登记了该缺口，
--     并写明落地前提「必须同批，缺一即炸」：① 生效通道加列 ② 实体加 @Version ③ 并发用例。
--   · 本脚本负责①；②③ 在同一次提交的实体与服务里完成。
--
-- 为什么必须是「加列 + @Version」同批，而不是只加注解
-- ----------------------------------------------------------------
--   SDK 的 INSERT 列由实体元数据生成（@Column 清单），UPDATE 走 `version = :old` 条件更新。
--   只给实体加 @Version 而不加列 ⇒ 任何 save 都是「写不存在的列」⇒ 运行期 ERROR 1054。
--   只加列而不加 @Version ⇒ 列恒为 0，乐观锁形同虚设，并发覆盖照旧。
--   两种半成品都比不改更危险（前者炸、后者伪装成已加固），故合为一次提交。
--
-- 为什么默认 0 而不是 NULL
-- ----------------------------------------------------------------
--   存量差异行没有历史版本信息，NULL 会让 SDK 的版本条件更新无法构造（BaseRepository
--   对 @Version 为 null 直接拒绝：「Optimistic lock cannot proceed」）。DEFAULT 0 让存量行
--   等价于「第 0 版」，首次处置即从 0 → 1，与新建行行为一致，零数据迁移。
--
-- 为什么该表此前没有 version，而同目录的 t_domain_event_outbox 有
-- ----------------------------------------------------------------
--   0072 建表时只给需要「多实例争抢同一行」的 Outbox 加了 version；对账差异当时还没有
--   任何写路径（resolution 恒为 PENDING，见 0089 注释），乐观锁自然无从谈起。0089 之后
--   处置写路径落地，缺口才真正暴露——属于「能力先于约束」的典型顺序债。
--
-- 幂等 / 执行
-- ----------------------------------------------------------------
--   · 幂等：information_schema 探测 + PREPARE 动态 DDL（0085/0090/0094/0095/0097 同范式），
--     重复执行影响 0 行，可安全重跑；
--   · 终态断言：列必须就位、NOT NULL、DEFAULT 0 三者同时成立。缺列 ⇒ 写路径 1054；
--     允许 NULL ⇒ 存量行首次 save 被 SDK 拒绝；默认值非 0 ⇒ 无法判断「是否被改过」；
--   · bone-init.sql 同提交补该列（G39 只比表级终态，列是盲区，须手动对齐）；
--   · 模块内 db/migration/V*.sql 属参考态（无 Flyway 依赖、无 runner、CI 不引用），
--     本脚本未在其下同步副本，避免与并行会话编号推进冲突；双源收敛见契约 §12.2.1（L3）。
--
-- 执行（生产数据变更属 L3，由 DBA / CI 在目标库运行）：
--   mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0098_synap_claim_reconciliation_diff_version.sql
-- ============================================================

USE bone;

-- ─────────────────────────────────────────────────────────────
-- ① t_reconciliation_diff.version（乐观锁版本，NOT NULL DEFAULT 0）
--    位置对齐 t_domain_event_outbox：审计列之后、tenant_id 之前。
-- ─────────────────────────────────────────────────────────────
SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE t_reconciliation_diff ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT ''乐观锁版本(SDK @Version 维护; 并发处置后写者须失败)'' AFTER resolved_by',
    'DO 0'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_reconciliation_diff' AND COLUMN_NAME = 'version'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ─────────────────────────────────────────────────────────────
-- ② 终态断言（缺列 ⇒ 1054；可空 ⇒ 存量行首次 save 被 SDK 拒；默认非 0 ⇒ 失去版本基线）
-- ─────────────────────────────────────────────────────────────
DROP PROCEDURE IF EXISTS sp_verify_0098;
DELIMITER $$
CREATE PROCEDURE sp_verify_0098()
BEGIN
    DECLARE v_col INT DEFAULT 0;
    DECLARE v_nullable VARCHAR(8) DEFAULT NULL;
    DECLARE v_default VARCHAR(64) DEFAULT NULL;

    SELECT COUNT(*) INTO v_col
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_reconciliation_diff'
       AND COLUMN_NAME = 'version';
    IF v_col <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0098 校验失败：t_reconciliation_diff.version 未就位（实体 @Version 已在同批启用，缺列会让任何 save 报 ERROR 1054）';
    END IF;

    SELECT IS_NULLABLE, COLUMN_DEFAULT INTO v_nullable, v_default
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_reconciliation_diff'
       AND COLUMN_NAME = 'version';
    IF v_nullable <> 'NO' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0098 校验失败：t_reconciliation_diff.version 必须 NOT NULL（可空会让存量行首次 save 被 SDK 以「@Version 为 null」拒绝）';
    END IF;
    IF v_default IS NULL OR v_default <> '0' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0098 校验失败：t_reconciliation_diff.version 必须 DEFAULT 0（否则存量行与新建行的版本基线不一致）';
    END IF;
END$$
DELIMITER ;

CALL sp_verify_0098();
DROP PROCEDURE IF EXISTS sp_verify_0098;
