-- =============================================================================
-- 0068_purchase_workflow_seed.sql
--
-- 目的（采购审批流接入 bone-workflow-engine · 种子段，L3 审批项）：
--   ① 种采购流程定义 purchase_order v1（wf_process_def）：合理默认单级审批
--      （提交 → 审批人(approver_id) → 通过/驳回），无金额三档分支（采购审批规则差异全在流程定义配置，
--       如需金额分级/会签，在下方 definition_json 加 CONDITION 节点即可，业务 Handler 不变）；
--   ② 种 wf_binding（实体 → 流程定义，与 PurchaseWorkflowConstants.DEFINITION_KEY 一致）。
--
-- 分工（勿重复造表）：wf_* 六表 DDL 由 0056 负责；本脚本只 INSERT，且以 WHERE NOT EXISTS 守卫幂等。
--
-- ⚠ L3 待审批：流程定义属审批数据，须架构师审批后由 DBA/CI 执行。
-- ⚠ 前置：0056_wf_workflow_tables.sql（六表）已执行。
-- ⚠ 动作改逃逸（submit→purchaseWorkflowSubmitHandler / approve,reject→purchaseWorkflowDecisionHandler）
--    不在本脚本：采购无预置生产模板（元数据由 e2e 动态建模或实例层 meta_entity_action_def 落库），
--    故在 e2e 动态建模路径 / 实例层 UPDATE meta_entity_action_def 设置 handler_bean（见联调脚本与文档）。
--
-- 执行（L3 审批项；由 DBA / CI 在目标库运行）：
--       mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0068_purchase_workflow_seed.sql
-- 幂等：种子 NOT EXISTS 守卫。
-- =============================================================================

USE bone;

-- ── 1. 流程定义种子：purchase_order v1 ───────────────────────────────────
-- 合理默认（待业务确认）：单级审批，指派 FIELD approver_id；reject 由引擎在 APPROVE 节点的 REJECT
--   决策终结流程（与报销同语义，无需单独画 REJECT 边）。
-- 如需金额分级/会签，参考 0057 的 CONDITION 节点 + ROLE 指派，改下方 definition_json 即可。
INSERT INTO wf_process_def
    (id, tenant_id, def_key, def_name, def_version, status, enabled, definition_json,
     created_by, created_at, updated_by, updated_at, deleted, version)
SELECT
    CONCAT('def-purchase-order-', t.tenant_id),
    t.tenant_id,
    'purchase_order',
    '采购订单审批',
    1,
    'PUBLISHED',
    1,
    '{"key":"purchase_order","name":"采购订单审批","version":1,"nodes":[{"code":"start","type":"START","mode":null,"label":"提交"},{"code":"approve","type":"APPROVE","mode":"SINGLE","label":"采购审批"},{"code":"end","type":"END","mode":null,"label":"结束"}],"edges":[{"from":"start","to":"approve","when":null,"label":null},{"from":"approve","to":"end","when":null,"label":null}],"assignments":{"approve":[{"strategy":"FIELD","params":{"fieldCode":"approver_id"}}]}}',
    'migration-0068', NOW(3), 'migration-0068', NOW(3), 0, 0
FROM (
    SELECT 0 AS tenant_id
    UNION
    SELECT DISTINCT tenant_id FROM meta_entity
     WHERE code = 'purchase_order' AND deleted = 0
) t
WHERE NOT EXISTS (
    SELECT 1 FROM wf_process_def d
     WHERE d.tenant_id = t.tenant_id AND d.def_key = 'purchase_order'
       AND d.def_version = 1 AND d.deleted = 0
);

-- ── 2. wf_binding 种子（与 handler 硬编码的 DEFINITION_KEY 一致；对账锚点） ──
INSERT INTO wf_binding
    (id, tenant_id, entity_code, def_key, start_action, enabled,
     created_by, created_at, updated_by, updated_at, deleted, version)
SELECT
    CONCAT('bind-purchase-order-', t.tenant_id),
    t.tenant_id,
    'purchase_order',
    'purchase_order',
    'submit',
    1,
    'migration-0068', NOW(3), 'migration-0068', NOW(3), 0, 0
FROM (
    SELECT 0 AS tenant_id
    UNION
    SELECT DISTINCT tenant_id FROM meta_entity
     WHERE code = 'purchase_order' AND deleted = 0
) t
WHERE NOT EXISTS (
    SELECT 1 FROM wf_binding b
     WHERE b.tenant_id = t.tenant_id AND b.entity_code = 'purchase_order' AND b.deleted = 0
);

-- ── 3. 校验 ───────────────────────────────────────────────────────────────
SELECT
  (SELECT COUNT(*) FROM wf_process_def
     WHERE def_key = 'purchase_order' AND status = 'PUBLISHED' AND deleted = 0) AS published_def_count,
  (SELECT COUNT(*) FROM wf_binding
     WHERE entity_code = 'purchase_order' AND deleted = 0) AS binding_count;
