-- ============================================================
-- 0066_wf_node_round.sql
-- 节点轮次（node_round）+ 驳回往返计数（reject_rounds）+ 投影操作人（operator_id）
--
-- 为什么：
--   本脚本修的是三个「不会报错」的缺陷——全部表现为流程悄悄走错，日志与告警都看不见。
--
--   ① wf_instance.node_round / wf_task.node_round —— 驳回退回后的完成判据污染
--      rejectToNode 会把实例退回同一节点，于是同一 (instance, node) 出现两批任务。
--      compareAndAdvanceNode 的完成判据按 (instance, node) 全量统计，不区分批次：
--        quorum=3 的 5 人会签：第一轮 3 人通过 → 节点推进 → 后续节点驳回退回 → 5 人重新派单。
--        此时历史 3 张 APPROVED 仍在，COUNT(APPROVED) >= quorum 恒真 ⇒ 本轮只要有一个人点同意，
--        节点立刻推进，另外 4 人从未表态。
--      轮次是整数、由推进语句自增，判据子查询按 node_round 等值过滤。
--      刻意不用 created_at >= node_entered_at 比较时间戳：任务与实例更新可能落在同一毫秒
--      （固定时钟的测试必然如此），时间戳比较会退化，整数轮次不会。
--
--   ② wf_instance.reject_rounds —— 驳回上限的计数口径
--      原先用「REJECTED 任务条数」当往返轮次：一个 5 人会签节点上 3 人驳回就算 3 轮，
--      10 轮上限在 4 次真实往返内被打满，单据被误终止；反之每次仅 1 人驳回时又计数偏小。
--      改为在实例行上原子累加，一次退回 = 一轮。
--
--   ③ wf_status_outbox.operator_id —— 投影操作人
--      引擎把「审批通过」写成意图时已经拿到了真正的决策人，但 enqueue 把它丢掉了；
--      中继只好用 started_by 兜底，于是业务台账上出现「发起人执行了 approve」——
--      与 wf_task 里的真实审批人对不上，且错的方向恰好是最敏感的（像自审自批）。
--      本列随意图行携带真实操作人；对账补投的行没有决策人，留 NULL（执行器落成系统身份，
--      绝不冒用发起人）。
--
-- 兼容性（Expand-only，先加列后使用，可回滚）：
--   - 三列均可加默认值后上线，旧版本代码不读新列，行为不变。
--   - 存量行回填口径刻意保守：
--       node_round     = 0（存量任务的 node_round 默认也是 0 ⇒ 与改造前「全量统计」行为完全一致）
--       node_entered_at = started_at（仅用于「当前节点停留时长」诊断，不参与判据）
--       reject_rounds  = 0（从本次上线起重新计；上限只可能因此放宽一次，不会误终止）
--     不把存量在途实例的 node_round 拆细：迁移不得改变在途单据的推进语义。
--
-- 幂等性：information_schema 探测分支，可重复执行。
-- ============================================================

-- ---------- ① wf_instance.node_round / node_entered_at / reject_rounds ----------
SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE wf_instance ADD COLUMN node_round INT NOT NULL DEFAULT 0 COMMENT ''节点轮次（每次推进自增，会签判据按轮次过滤）'' AFTER started_at',
    'DO 0'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wf_instance' AND COLUMN_NAME = 'node_round'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE wf_instance ADD COLUMN node_entered_at DATETIME(3) NULL COMMENT ''进入当前节点的时刻（SLA / 卡死看门狗依据，不参与推进判据）'' AFTER node_round',
    'DO 0'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wf_instance' AND COLUMN_NAME = 'node_entered_at'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE wf_instance ADD COLUMN reject_rounds INT NOT NULL DEFAULT 0 COMMENT ''累计驳回退回次数（循环防护计数，一次退回=一轮）'' AFTER node_entered_at',
    'DO 0'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wf_instance' AND COLUMN_NAME = 'reject_rounds'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 存量回填：node_entered_at 用 started_at 兜底（只用于停留时长诊断）
UPDATE wf_instance SET node_entered_at = started_at WHERE node_entered_at IS NULL;

-- 回填完成后再收紧为 NOT NULL：先加可空列 + 回填 + 收紧，是「不锁表、不改在途语义」的标准三步。
-- 直接 ADD COLUMN ... NOT NULL 在非空表上会失败（严格模式）或写入零值时间（非严格模式），两者都不该发生。
SET @ddl := (
  SELECT IF(
    (SELECT IS_NULLABLE FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wf_instance'
        AND COLUMN_NAME = 'node_entered_at') = 'YES',
    'ALTER TABLE wf_instance MODIFY COLUMN node_entered_at DATETIME(3) NOT NULL COMMENT ''进入当前节点的时刻（SLA / 卡死看门狗依据，不参与推进判据）''',
    'DO 0'
  )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- ② wf_task.node_round ----------
SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE wf_task ADD COLUMN node_round INT NOT NULL DEFAULT 0 COMMENT ''所属节点轮次（写入时取自 wf_instance.node_round）'' AFTER due_at',
    'DO 0'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wf_task' AND COLUMN_NAME = 'node_round'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 会签判据按 (instance, node, round) 过滤，补齐对应索引，避免判据子查询退化为全表扫
SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'CREATE INDEX idx_wf_task_node_round ON wf_task (tenant_id, instance_id, node_code, node_round)',
    'DO 0'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wf_task' AND INDEX_NAME = 'idx_wf_task_node_round'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- ③ wf_status_outbox.operator_id ----------
SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE wf_status_outbox ADD COLUMN operator_id VARCHAR(64) DEFAULT NULL COMMENT ''真实操作人（决策人）；对账补投的行为 NULL'' AFTER record_id',
    'DO 0'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wf_status_outbox' AND COLUMN_NAME = 'operator_id'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 自检（人工执行，确认三列已就位）----------
-- SHOW COLUMNS FROM wf_instance LIKE 'node_round';
-- SHOW COLUMNS FROM wf_instance LIKE 'node_entered_at';
-- SHOW COLUMNS FROM wf_instance LIKE 'reject_rounds';
-- SHOW COLUMNS FROM wf_task LIKE 'node_round';
-- SHOW COLUMNS FROM wf_status_outbox LIKE 'operator_id';
