-- =============================================================================
-- 0038_runtime_row_policies.sql
--
-- 目的（Runtime 数据面设计 §4.7.1 行级 ACL · v0.48 行策略落地）：
--   上一轮（v0.47）实现行级 ACL owner 维度（created_by 单列过滤）。本轮把
--   v1.1 剩余的行策略（多规则 OR 组合，对标 Supabase RLS「策略即 WHERE」）落地：
--     · meta_entity 新增 row_policies 列（JSON 声明：owner_field + rules）；
--     · t_expense_claim 新增 approver_id 列（单据指定审批人）；
--     · 报销实体声明 owner_or_approver 策略：
--          created_by = :operator OR approver_id = :operator
--       —— 本人 + 审批人均可见，其余非本人 404（RLS 默认拒绝，不泄露行存在性）。
--
-- 声明形态（引擎 RowPolicySpec 解析，when 仅支持「列 = :operator」+ AND/OR）：
--   {"owner_field":"created_by","rules":[{"name":"owner_or_approver","effect":"allow",
--     "when":"created_by = :operator OR approver_id = :operator"}]}
--
-- 安全约束：when 的列名必须同时满足「实体已声明 + 物理表存在」双白名单，
--   否则该规则被引擎忽略（保守退回 owner 单规则）；:operator 参数化绑定。
--
-- 设计
-- ----------------------
-- · row_policies 为 TEXT：null = 默认 owner 语义（created_by 单列，v0.47 行为不变）；
--   空 JSON/解析失败 = 回退 owner 默认（保守最小可见性）。
-- · approver_id 语义：单据创建/提交时指定的审批人（业务字段，非动作副作用——
--   approve/reject 为纯声明式 state_transition，不写该列）。
-- · 审批动作链不受行级 ACL 拦截：updateState 走 updateInternal 不经行级断言
--   （动作面由动作级权限独立管控），审批人可流转他人单据，符合业务语义。
-- · 缓存一致性：RedisCachedRuntimeEntityCatalog 缓存透传 rowPolicies + stateField
--   （顺带修复缓存命中丢 state_field 的既有缺陷），实体发布变更走既有 evict。
-- =============================================================================

USE bone;

-- ── 1. meta_entity 增行策略声明列 ──────────────────────────────────────────
ALTER TABLE meta_entity
  ADD COLUMN row_policies TEXT DEFAULT NULL COMMENT '行级 ACL 策略 JSON（owner_field + rules；null=默认 created_by 单规则，Runtime §4.7.1）';

-- ── 2. t_expense_claim 增审批人列 ───────────────────────────────────────────
-- 审批人语义：单据创建/提交时指定（业务字段）。仅 L1 报销实体需要，其他运行时实体不建。
ALTER TABLE t_expense_claim
  ADD COLUMN approver_id BIGINT DEFAULT NULL COMMENT '审批人（单据指定；行策略 owner_or_approver 用）';

-- ── 3. 报销实体行策略声明 ───────────────────────────────────────────────────
-- 幂等：按 uk（tenant_id+code）更新；重复执行不报错。
UPDATE meta_entity
SET row_policies = '{"owner_field":"created_by","rules":[{"name":"owner_or_approver","effect":"allow","when":"created_by = :operator OR approver_id = :operator"}]}',
    updated_by = 1, updated_at = NOW(3)
WHERE tenant_id = 1001 AND code = 'expense_claim' AND delivery_mode = 1;

-- ── 4. approver_id 字段声明（meta_field，行策略依赖可写字段）──────────────
-- 0037 未声明 approver_id：写路径 filterWritable 会剔除未声明字段，
-- 单据无法指定审批人，行策略 approver 分支将成死规则。此处补齐声明
-- （LONG，物理列 t_expense_claim.approver_id，幂等 ON DUPLICATE KEY）。
INSERT INTO meta_field
    (id, tenant_id, entity_id, module_id, name, code, display_name, type, length, numeric_precision,
     is_required, is_unique, is_pk, is_indexed, default_value, enum_values, validation_rules, comment,
     data_classification, is_pii, sensitivity_level, data_steward, business_term, source_system,
     physical_column, sort_order, created_by, updated_by, created_at, updated_at, deleted, version)
VALUES
    (92112, 1001, 9210, 58, '审批人', 'approver_id', '审批人', 'LONG', NULL, NULL, 0, 0, 0, 1, NULL, NULL, NULL,
     '审批人（单据指定；行策略 owner_or_approver 的 approver 分支依赖此字段）', NULL, 0, NULL, NULL, NULL, NULL, NULL,
     12, 1, 1, NOW(3), NOW(3), 0, 0)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name), type = VALUES(type), length = VALUES(length),
    is_required = VALUES(is_required), is_unique = VALUES(is_unique), default_value = VALUES(default_value),
    enum_values = VALUES(enum_values), validation_rules = VALUES(validation_rules), comment = VALUES(comment),
    physical_column = VALUES(physical_column), sort_order = VALUES(sort_order), updated_at = NOW(3);

-- ── 5. 校验 ────────────────────────────────────────────────────────────────
SELECT
  (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = 'bone' AND TABLE_NAME = 'meta_entity'
       AND COLUMN_NAME = 'row_policies')  AS row_policies_column_ready,
  (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = 'bone' AND TABLE_NAME = 't_expense_claim'
       AND COLUMN_NAME = 'approver_id') AS approver_id_column_ready,
  (SELECT COUNT(*) FROM meta_entity
     WHERE tenant_id = 1001 AND code = 'expense_claim'
       AND row_policies IS NOT NULL) AS expense_claim_policy_ready,
  (SELECT COUNT(*) FROM meta_field
     WHERE tenant_id = 1001 AND entity_id = 9210 AND code = 'approver_id'
       AND deleted = 0) AS approver_field_ready;
