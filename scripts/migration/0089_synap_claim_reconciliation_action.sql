-- ============================================================
-- 0089_synap_claim_reconciliation_action.sql
--
-- 一句话：给资金对账补一张**处置流水**（t_reconciliation_action），
--       让「差异处置」和「额度投影重建」从"只打日志"变成"库内可查、可追溯"。
--
-- 背景（为什么这件事不能拖到 V2）
-- ----------------------------------------------------------------
-- §11.7 的资金对账到 2026-11-01 已经能产出 6 类差异
-- （AMOUNT_MISMATCH / STATUS_MISMATCH / MISSING_LOCAL / QUOTA_BREAK /
--   PROJECTION_DRIFT / STAGE_REGRESSION / CASE_SETTLE_MISMATCH），
-- 但「检出」之后没有「处置」这一环，具体断在两处：
--
--   · 差异不可处置：t_reconciliation_diff 的 resolution 恒为 PENDING，
--     实体 append-only、没有任何写路径。运维把一条差异看完了，
--     **没有办法把它标记成已处理**，于是"待处置队列"永远只增不减 ——
--     一份只增不减的清单，两周后没人再看。
--   · 投影重建不可查：PROJECTION_DRIFT 的处置动作
--     （POST /claim-ops/quota-accounts/{id}/projection/rebuild）已经建了，
--     但它只写应用日志（前缀 `[额度投影重建]`）。"谁在何时重算了哪个账户、
--     改了哪几列、从多少到多少"，落库为零 —— 审计要翻日志文件，
--     而日志有轮转、有截断，不能当审计台账。
--
-- 设计取舍（写在这里，免得后人以为是"忘了给 diff 加 update"）
-- ----------------------------------------------------------------
-- 处置**不回写** t_reconciliation_diff，而是追加到本表：
--   1) diff 是对账结论的证据。允许"更新处置字段"就等于允许改写证据
--      （先判红、后改动、再声称从没红过），与 §11.5 审计不可篡改冲突；
--   2) 追加式还顺带解决了并发：两个人同时处置同一差异，
--      不会互相覆盖成"最后一个人说了算"，两条动作都在，读侧取最新；
--   3) 一张表同时承载「差异处置」与「投影重建」两类动作，
--      运维排障只查一处（都属"对系统状态施加的一次受控动作"）。
-- 代价：查"待处置"要 LEFT JOIN 本表（见 ReconciliationDiffRepository#findPending）。
--
-- 为什么本表带 updated_at/updated_by/deleted（"append-only 表为何有审计列"）
-- ----------------------------------------------------------------
-- SDK 的 INSERT/UPDATE 列清单由**实体元数据**生成
-- （UpsertBuilder.buildMySql → ctx.getTable().getColumns()），
-- 不是由 DB 实际列生成；TenantAbstractEntity 的审计列会被映射。
-- 所以列**必须有**，否则写入即 ERROR 1054。业务侧不提供任何 UPDATE 路径，
-- "append-only" 由**代码不写 update + 不授 UPDATE 权限**保证，不是由缺列保证。
-- 同理 t_reconciliation_report / t_reconciliation_diff（0072）也带这些列。
--
-- 幂等
-- -------------------------------------------------------------
-- CREATE TABLE IF NOT EXISTS + information_schema 断言；
-- 已存在同名异形表会静默通过建表但**被断言拦下**（列/索引缺失即 SIGNAL 失败）。
-- ============================================================

-- ─────────────────────────────────────────────────────────────
-- 一、建表
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `t_reconciliation_action` (
  `id`          bigint       NOT NULL COMMENT '处置动作ID(分布式ID)',
  `target_type` varchar(24)  NOT NULL COMMENT '目标类型(DIFF=对账差异/QUOTA_ACCOUNT=额度账户)',
  `target_id`   bigint       NOT NULL COMMENT '目标ID(差异ID 或 额度账户ID)',
  `action_type` varchar(32)  NOT NULL COMMENT '动作类型(DIFF_RESOLVE=差异处置/PROJECTION_REBUILD=按流水重建投影)',
  `resolution`  varchar(16)  NOT NULL DEFAULT '' COMMENT '处置结论(仅 DIFF_RESOLVE: RESOLVED/ADJUSTED/WAIVED)',
  `detail`      varchar(512) NOT NULL DEFAULT '' COMMENT '处置说明/重算逐列 before->after',
  `operator_id` varchar(64)  NOT NULL COMMENT '操作人(取自鉴权身份，不接受请求体自报)',
  `occurred_at` datetime(3)  NOT NULL COMMENT '动作发生时间',
  `tenant_id`   bigint       NOT NULL COMMENT '租户',
  `created_by`  bigint       DEFAULT NULL COMMENT '创建人ID',
  `created_at`  datetime(3)  NOT NULL COMMENT '创建时间',
  `updated_by`  bigint       DEFAULT NULL COMMENT '修改人ID',
  `updated_at`  datetime(3)  NOT NULL COMMENT '更新时间(随创建写一次，业务不再改)',
  `deleted`     tinyint(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除(业务不删，列由框架注入)',
  PRIMARY KEY (`id`),
  KEY `idx_target` (`tenant_id`, `target_type`, `target_id`, `occurred_at`),
  KEY `idx_action` (`tenant_id`, `action_type`, `occurred_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资金对账处置流水(append-only)';

-- ─────────────────────────────────────────────────────────────
-- 二、断言（跑过了 ≠ 做到了：逐列/逐索引探测）
-- ─────────────────────────────────────────────────────────────
DELIMITER $$
DROP PROCEDURE IF EXISTS sp_verify_0088$$
CREATE PROCEDURE sp_verify_0088()
BEGIN
    DECLARE v_missing INT DEFAULT 0;

    -- ① 表必须存在
    SELECT COUNT(*) INTO v_missing
      FROM information_schema.tables
     WHERE table_schema = DATABASE() AND table_name = 't_reconciliation_action';
    IF v_missing = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0088 校验失败：t_reconciliation_action 未建成';
    END IF;

    -- ② 列必须齐全（实体声明了 13 列；少一列 ⇒ 该路径写入 ERROR 1054）
    SELECT COUNT(*) INTO v_missing FROM (
        SELECT 'id' AS c UNION ALL SELECT 'target_type' UNION ALL SELECT 'target_id'
        UNION ALL SELECT 'action_type' UNION ALL SELECT 'resolution' UNION ALL SELECT 'detail'
        UNION ALL SELECT 'operator_id' UNION ALL SELECT 'occurred_at' UNION ALL SELECT 'tenant_id'
        UNION ALL SELECT 'created_by' UNION ALL SELECT 'created_at' UNION ALL SELECT 'updated_by'
        UNION ALL SELECT 'updated_at' UNION ALL SELECT 'deleted'
    ) expected
    WHERE NOT EXISTS (
        SELECT 1 FROM information_schema.columns col
         WHERE col.table_schema = DATABASE()
           AND col.table_name = 't_reconciliation_action'
           AND col.column_name = expected.c
    );
    IF v_missing > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0088 校验失败：t_reconciliation_action 缺列（写路径会 Unknown column）';
    END IF;

    -- ③ 索引必须存在（idx_target 是"某差异的处置历史"与"待处置队列"的唯一入口）
    SELECT COUNT(*) INTO v_missing FROM (
        SELECT 'idx_target' AS k UNION ALL SELECT 'idx_action'
    ) expected
    WHERE NOT EXISTS (
        SELECT 1 FROM information_schema.statistics s
         WHERE s.table_schema = DATABASE()
           AND s.table_name = 't_reconciliation_action'
           AND s.index_name = expected.k
    );
    IF v_missing > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0088 校验失败：t_reconciliation_action 索引缺失';
    END IF;

    -- ④ operator_id 必须 NOT NULL（无操作人的处置流水等于没有留痕）
    SELECT COUNT(*) INTO v_missing
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 't_reconciliation_action'
       AND column_name = 'operator_id'
       AND is_nullable = 'YES';
    IF v_missing > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0088 校验失败：operator_id 可空（处置流水必须留操作人）';
    END IF;
END$$
DELIMITER ;

CALL sp_verify_0088();
DROP PROCEDURE IF EXISTS sp_verify_0088;

-- ─────────────────────────────────────────────────────────────
-- 三、验证（手工执行，非脚本自动断言）
-- ─────────────────────────────────────────────────────────────
-- 写路径探针（列缺失时这里会 ERROR 1054）：
--   INSERT INTO t_reconciliation_action
--     (id, target_type, target_id, action_type, resolution, detail,
--      operator_id, occurred_at, tenant_id, created_at, updated_at, deleted)
--   VALUES (999999003,'DIFF',999999001,'DIFF_RESOLVE','RESOLVED','PROBE',
--      'probe-op',NOW(3),0,NOW(3),NOW(3),0);
-- 读路径探针（待处置队列：没有处置流水的差异）：
--   SELECT d.id FROM t_reconciliation_diff d
--     LEFT JOIN t_reconciliation_action a
--            ON a.target_type = 'DIFF' AND a.target_id = d.id
--           AND a.action_type = 'DIFF_RESOLVE'
--    WHERE a.id IS NULL AND d.deleted = 0 AND d.tenant_id = 0
--    ORDER BY d.created_at DESC LIMIT 50;
