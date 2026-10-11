-- =============================================================================
-- 0035_runtime_action_execution_phase.sql
--
-- 目的（Runtime 数据面设计 §4.8.1 逃逸半事务 · 本轮 P0-2 落地）：
--   0032 的 meta_runtime_action_execution 只有 success/fail 两态，且只在「整个
--   动作链成功之后」由 RuntimeActionService 收尾写入。于是存在一个资金级盲区：
--   dispatch（外部副作用）成功 → updateState（状态流转）失败 → 零留痕。
--   该记录在库里看起来「从未执行过」，但外部网关可能已真实出金 —— 重试即二次打款。
--
--   本迁移把执行日志从「成功才留痕」升级为「先留痕后执行」的生命周期模型：
--     status=3  pending   begin 行已写，副作用尚未发起（或正在发起）
--     status=1  succeeded 副作用成功 且 状态流转成功（终态，幂等命中）
--     status=2  failed    副作用发起前/发起时失败（终态，可安全重试）
--     status=4  orphaned  副作用成功 但 状态流转失败（终态！重试不得再跑副作用，
--                          只能走对账/恢复通道补状态流转）
--
--   幂等去重查询由 status=1 扩展为 status IN (1,4)：orphaned 行的副作用结果
--   仍是「已执行一次」的真源，命中后返回并显式标记 orphaned，由调用方走人工/对账。
--
-- 设计
-- ----------------------
-- · 不改既有 status 列语义（1/2 保持原义），只新增 3/4 两态 —— 存量数据零迁移。
-- · 新增 handler_bean / is_escape / params_json / pending_at / settled_at：
--   pending 行必须自足（进程在 dispatch 任意时刻崩溃后，对账作业据此复原现场）。
-- · requires_idempotency 动作的 begin 行受既有 uk（五元组唯一键）约束：
--   同键重试走 ON DUPLICATE KEY（见 JdbcRuntimeActionExecutionRepository.begin）。
-- · 无幂等键的逃逸动作（requires_idempotency=0）：uk 五元组含 NULL 不冲突，
--   每次尝试各占一行 —— 其重复副作用风险由 0032 注释与 Runtime §4.8 声明
--   （资金类动作必须开 requires_idempotency），不属于本表保护范围。
--
-- 执行（L3 审批项；由 DBA / CI 在目标库运行）：
--       mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0035_runtime_action_execution_phase.sql
-- 前置：0032_runtime_action_spi.sql（meta_runtime_action_execution 已建表）。
-- =============================================================================

USE bone;

-- ── 1. 执行日志扩展：生命周期态 + 现场自足列 ─────────────────────────────────
ALTER TABLE meta_runtime_action_execution
  MODIFY COLUMN status TINYINT NOT NULL DEFAULT 1
    COMMENT '1=成功(终态), 2=失败(终态), 3=pending(begin已写未定论), 4=orphaned(副作用成功但状态未流转,终态)',
  ADD COLUMN handler_bean VARCHAR(255) DEFAULT NULL COMMENT '逃逸处理器 bean 名（dispatch 前快照）',
  ADD COLUMN is_escape    TINYINT    NOT NULL DEFAULT 0 COMMENT '是否逃逸动作（有无外部副作用）',
  ADD COLUMN params_json  VARCHAR(2048) DEFAULT NULL COMMENT '动作入参快照（对账复原现场用）',
  ADD COLUMN pending_at   DATETIME(3)  DEFAULT NULL COMMENT 'begin 落行时间',
  ADD COLUMN settled_at   DATETIME(3)  DEFAULT NULL COMMENT '终态落定时间';

-- pending 停留时间索引：对账作业按它扫超时 pending（进程崩溃残留）
CREATE INDEX idx_exec_pending ON meta_runtime_action_execution (status, pending_at);

-- ── 2. 校验 ────────────────────────────────────────────────────────────────
SELECT
  (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = 'bone' AND TABLE_NAME = 'meta_runtime_action_execution'
       AND COLUMN_NAME = 'pending_at')  AS pending_at_column_ready,
  (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = 'bone' AND TABLE_NAME = 'meta_runtime_action_execution'
       AND INDEX_NAME = 'idx_exec_pending') AS pending_index_ready;
