-- =============================================================================
-- 0048_template_action_entry.sql
--
-- 目的：给状态机动作定义加「入口动作」显式标记（is_entry），修复全环状态机
--       「创建记录死锁」——图推导（from − to）在全环上无解（设备巡检 OPEN↔CLOSED：
--       close: OPEN→CLOSED / reopen: CLOSED→OPEN ⇒ from 集合减 to 集合 = ∅）。
--
-- 症状（2026-10-15 E2E 实证，实体 insp_1792042722480）：
--   ① POST 不带 status → 400 META_RUNTIME_VALIDATION_FAILED（必填 status 无人注入）
--   ② POST 带 status     → 422 META_RUNTIME_FIELD_PROTECTED（G14 保护生效）
--   ⇒ 模板实例化出的巡检实体一条记录都建不了；UI「新建记录」必失败。
--   这正是 0040 要修的问题，只是 0040 只覆盖了有向无环的三套模板
--   （报销 DRAFT / 补货 PENDING / 访客 PENDING 均可图推导），漏掉了巡检这台全环机器。
--
-- 设计（与 RuntimeStatusGuard.entryStatusOf 的优先级一一对应）：
--   · is_entry=1 的动作（按 sort_order 取首个）：其 from_status 即新记录初始状态
--     —— UML 状态图「初始状态伪状态」的等价物，显式优先于推断；
--   · 未标记：回退图推导（from_status 集合 − to_status 集合，无入边者）；
--   · 仍不可推导：fail-closed（必填校验拒绝，绝不采信客户端直写值）。
--   · from_status IS NULL（任意态可触发）**不是**入口候选——入口与任意态是两个
--     正交语义（既有测试 RuntimeStatusGuardTest#entryStatus_nullFromStatus_isNotTreatedAsEntry
--     明确固定了这条边界，本迁移不借道 NULL-from）。
--
-- 数据范围：
--   · 模板层：meta_model_template_action 按图推导补标（9150 submit / 9130 placeOrder /
--     9140 approve）+ 巡检模板（tpl_device_inspection）显式标 close（from_status=OPEN 即初始态）；
--   · 实体层：meta_entity_action_def 同规则补标全部存量实例（含 insp_*/uidesign_* 巡检形态）。
--   · 新实例化路径由代码侧透传（materialize UPSERT 携带 is_entry），无需数据修复。
--
-- 幂等：ADD COLUMN 用 information_schema 守卫；标记语句可重复执行（集合写）。
-- 只增不改：不删行、不改既有转移边、不动 from/to 语义。
--
-- 执行（L3 审批项；由 DBA / CI 在目标库运行）：
--       mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0048_template_action_entry.sql
-- 前置：0040_template_action_state_machine.sql 已落地（meta_model_template_action 有种子）。
-- =============================================================================

USE bone;

-- ── 1. DDL：两张表加 is_entry 列（默认 0 = 未标记，存量数据零影响）────────────
SET @ddl1 = (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'meta_entity_action_def'
        AND COLUMN_NAME = 'is_entry') = 0,
    'ALTER TABLE meta_entity_action_def ADD COLUMN is_entry TINYINT NOT NULL DEFAULT 0 AFTER sort_order',
    'SELECT 1')
);
PREPARE s1 FROM @ddl1; EXECUTE s1; DEALLOCATE PREPARE s1;

SET @ddl2 = (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'meta_model_template_action'
        AND COLUMN_NAME = 'is_entry') = 0,
    'ALTER TABLE meta_model_template_action ADD COLUMN is_entry TINYINT NOT NULL DEFAULT 0 AFTER sort_order',
    'SELECT 1')
);
PREPARE s2 FROM @ddl2; EXECUTE s2; DEALLOCATE PREPARE s2;

-- ── 2. 模板层：图推导补标（from_status 不在同模板 to_status 集合中的最小 sort_order 动作）──
UPDATE meta_model_template_action d
JOIN (
  SELECT tenant_id, template_id, MIN(sort_order) AS so
  FROM meta_model_template_action e
  WHERE e.deleted = 0
    AND e.from_status IS NOT NULL AND e.from_status <> ''
    AND NOT EXISTS (
      SELECT 1 FROM meta_model_template_action t
      WHERE t.tenant_id = e.tenant_id AND t.template_id = e.template_id
        AND t.deleted = 0 AND t.to_status = e.from_status
    )
  GROUP BY tenant_id, template_id
) m ON m.tenant_id = d.tenant_id AND m.template_id = d.template_id AND m.so = d.sort_order
SET d.is_entry = 1
WHERE d.deleted = 0;

-- ── 3. 模板层：巡检全环形态显式标记（tpl_device_inspection.close，OPEN 即初始态）──
UPDATE meta_model_template_action a
JOIN meta_model_template t ON t.id = a.template_id AND t.deleted = 0
SET a.is_entry = 1
WHERE a.deleted = 0
  AND t.code = 'tpl_device_inspection'
  AND a.action_code = 'close';

-- ── 4. 实体层：图推导补标（全部存量实例，与第 2 步同规则）────────────────────
UPDATE meta_entity_action_def d
JOIN (
  SELECT tenant_id, entity_code, MIN(sort_order) AS so
  FROM meta_entity_action_def e
  WHERE e.deleted = 0
    AND e.from_status IS NOT NULL AND e.from_status <> ''
    AND NOT EXISTS (
      SELECT 1 FROM meta_entity_action_def t
      WHERE t.tenant_id = e.tenant_id AND t.entity_code = e.entity_code
        AND t.deleted = 0 AND t.to_status = e.from_status
    )
  GROUP BY tenant_id, entity_code
) m ON m.tenant_id = d.tenant_id AND m.entity_code = d.entity_code AND m.so = d.sort_order
SET d.is_entry = 1
WHERE d.deleted = 0;

-- ── 5. 实体层：巡检全环形态显式标记（close OPEN→CLOSED 且存在 reopen CLOSED→OPEN）──
-- 注意：必须用 JOIN 派生表——MySQL 1093 禁止 UPDATE 目标表出现在相关子查询里。
UPDATE meta_entity_action_def d
JOIN (
  SELECT DISTINCT c.tenant_id, c.entity_code
  FROM meta_entity_action_def c
  JOIN meta_entity_action_def r
    ON r.tenant_id = c.tenant_id AND r.entity_code = c.entity_code
   AND r.action_code = 'reopen' AND r.deleted = 0
  WHERE c.deleted = 0 AND c.action_code = 'close'
    AND c.from_status = 'OPEN' AND c.to_status = 'CLOSED'
) x ON x.tenant_id = d.tenant_id AND x.entity_code = d.entity_code
SET d.is_entry = 1
WHERE d.deleted = 0 AND d.action_code = 'close'
  AND d.from_status = 'OPEN' AND d.to_status = 'CLOSED';

-- ── 6. 去重保险：每实体至多一个入口动作（sort_order 并列时保留 id 最小者）─────
UPDATE meta_entity_action_def d
JOIN (
  SELECT tenant_id, entity_code, MIN(id) AS keep_id
  FROM meta_entity_action_def
  WHERE is_entry = 1 AND deleted = 0
  GROUP BY tenant_id, entity_code
  HAVING COUNT(*) > 1
) k ON k.tenant_id = d.tenant_id AND k.entity_code = d.entity_code AND d.id <> k.keep_id
SET d.is_entry = 0
WHERE d.is_entry = 1 AND d.deleted = 0;

-- ── 7. 校验：入口动作覆盖率（每个「有动作定义」的实体/模板都必须有且仅有一个入口）──
SELECT 'entity_missing_entry' AS check_item, COUNT(*) AS violations FROM (
  SELECT e.tenant_id, e.entity_code
  FROM meta_entity_action_def e
  WHERE e.deleted = 0
  GROUP BY e.tenant_id, e.entity_code
  HAVING SUM(e.is_entry = 1) <> 1
) x
UNION ALL
SELECT 'template_missing_entry', COUNT(*) FROM (
  SELECT e.tenant_id, e.template_id
  FROM meta_model_template_action e
  WHERE e.deleted = 0
  GROUP BY e.tenant_id, e.template_id
  HAVING SUM(e.is_entry = 1) <> 1
) y
UNION ALL
SELECT 'entry_action_blank_from', COUNT(*) FROM meta_entity_action_def
WHERE is_entry = 1 AND deleted = 0 AND (from_status IS NULL OR from_status = '')
UNION ALL
SELECT 'total_entity_entry_actions', COUNT(*) FROM meta_entity_action_def WHERE is_entry = 1 AND deleted = 0
UNION ALL
SELECT 'total_template_entry_actions', COUNT(*) FROM meta_model_template_action WHERE is_entry = 1 AND deleted = 0;
