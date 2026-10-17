-- =============================================================================
-- 0049_bp_outbox_claimed_at.sql
--
-- 目的：给存量库 bp_outbox 补 claimed_at 列 + (status, claimed_at) 索引，
--       修复 Outbox 中继「每 5 秒刷一条 bad SQL grammar」的持续性故障。
--
-- 症状（2026-10-16 全模块联调实证，bone-blueprint 运行日志 /tmp/svc-blueprint.log）：
--   ERROR c.b.b.i.m.o.OrderOutboxRelayPortAdapter
--     : Outbox 查询 PENDING 失败: PreparedStatementCallback; bad SQL grammar
--       [SELECT ..., m.claimed_at, ... FROM bp_outbox m WHERE m.status = ? LIMIT 50 OFFSET 0]
--   根因：Unknown column 'm.claimed_at' in 'field list'
--
--   影响面远不止日志噪音：
--     ① relay 每轮都在抛异常 ⇒ Outbox 里的领域事件**一条都发不出去**，
--        订单 → 支付 → 发货 的异步链路在本地联调环境实际是断的（页面看不出来，
--        因为写入侧成功，只有消费侧静默不动）；
--     ② 5xx/ERROR 持续刷屏 ⇒ 真正的服务端故障被淹没，监控失真。
--
-- 背景：claimed_at 是 ADR-0021（Outbox 平台化）为「多实例卡死自愈」新增的字段。
--   bone-init.sql 的 CREATE TABLE 里已经带上该列与 idx_bp_outbox_status_claimed，
--   但**只留了一条注释形态的升级提示（2026-10-06），没有落地成 migration 脚本** ⇒
--   init 之前建好的存量库永远补不上这列。本脚本把那条注释正式收口为可重复执行的迁移。
--
-- 幂等：用 information_schema 先探测再 DDL，重复执行安全（老版本 MySQL 无 IF NOT EXISTS
--   语法的 ADD COLUMN / ADD INDEX，故统一走探测分支）。
--
-- 执行：mysql -uroot -p bone < scripts/migration/0049_bp_outbox_claimed_at.sql
-- =============================================================================

-- ── 1. 补列（AFTER retry_count，与 bone-init.sql 建表顺序一致）──
SET @db := DATABASE();
SET @exists_col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'bp_outbox' AND COLUMN_NAME = 'claimed_at'
);
SET @sql := IF(@exists_col = 0,
  'ALTER TABLE bp_outbox ADD COLUMN claimed_at DATETIME(3) DEFAULT NULL COMMENT ''抢占时间（CAS 翻转 PENDING→PROCESSING 时写入）。多实例下卡死自愈的判据：PROCESSING 且 claimed_at 早于 stuck-timeout-ms（默认 60s）或为 NULL（字段上线前的存量行）才允许回收为 PENDING'' AFTER retry_count',
  'SELECT ''skip: claimed_at already exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ── 2. 补索引（卡死自愈 reconcileStuck 走 (status, claimed_at) 前缀）──
SET @exists_idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'bp_outbox' AND INDEX_NAME = 'idx_bp_outbox_status_claimed'
);
SET @sql := IF(@exists_idx = 0,
  'ALTER TABLE bp_outbox ADD KEY idx_bp_outbox_status_claimed (status, claimed_at)',
  'SELECT ''skip: idx_bp_outbox_status_claimed already exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ── 3. 存量数据说明（无需刷数）──
--    存量 PROCESSING 行 claimed_at 为 NULL，中继自愈（reconcileStuck）按「遗留」直接
--    回收为 PENDING，语义正确（OrderOutboxRelayPortAdapter 已把 IS NULL 视为遗留）。

-- ── 4. 校验：列与索引必须同时存在，且类型/可空符合预期 ──
SELECT 'claimed_at_column' AS check_item,
       COUNT(*) AS present
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'bp_outbox'
  AND COLUMN_NAME = 'claimed_at'
  AND COLUMN_TYPE = 'datetime(3)' AND IS_NULLABLE = 'YES'
UNION ALL
SELECT 'idx_status_claimed', COUNT(*)
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'bp_outbox'
  AND INDEX_NAME = 'idx_bp_outbox_status_claimed';

-- 负向校验：迁移后这条 SQL 必须能跑通（迁移前报 Unknown column 'm.claimed_at'）
SELECT 'relay_query_smoke' AS check_item, COUNT(*) AS rows_scanned
FROM (SELECT m.id FROM bp_outbox m WHERE m.status = 'PENDING' LIMIT 1) t;
