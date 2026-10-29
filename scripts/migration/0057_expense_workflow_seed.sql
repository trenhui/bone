-- =============================================================================
-- 0057_expense_workflow_seed.sql
--
-- 目的（报销审批流接入 bone-workflow-engine · 种子段，L3 审批项）：
--   ① 种报销流程定义 expense_reimbursement v1（wf_process_def）：金额三档 CONDITION 分支 +
--      指派规则（FIELD approver_id + finance 档 ROLE FINANCE_HEAD，union 语义）；
--   ② 种 wf_binding（实体 → 流程定义，运营入口；与 handler 硬编码 DEFINITION_KEY 一致）；
--   ③ submit/approve/reject/withdraw 四动作改逃逸（is_escape=1 + handler_bean，模板层 + 实例层）；
--   ④ 新增 withdraw 动作级细码并授权（RuntimeAccessGuard 对逃逸动作强制要求动作级细码）。
--
-- 分工（勿在本脚本重复造表）：
--   · wf_* 六表 DDL 由 0056_wf_workflow_tables.sql 负责（引擎侧真源，SDK @Sql 收敛后的列型口径：
--     deleted TINYINT(1) / definition_json JSON / outbox 无 created_by/updated_by/version）；
--     本脚本只INSERT/UPDATE，且**不依赖任何唯一键**（该 DDL 无 uk_def_key_version / uk_binding）——
--     一切种子均以 WHERE NOT EXISTS 守卫保证幂等，重复执行不堆行。
--   · 借款单 fieldLockRules 主防线由 0051 §4.2 负责（LOCK-AMT-01，与报销单同口径），本脚本不重复。
--
-- ⚠️ L3 待审批：本脚本涉及权限面与动作形态变更，须架构师审批后由 DBA/CI 执行。
-- ⚠️ 前置：0056_wf_workflow_tables.sql（六表）+ 0034/0037（报销实例链）+ 0051（生产模板）已执行。
-- ⚠️ 发布顺序：先本脚本、后 bone-app-expense 新版本——代码先行而表/种子缺失时 submit 报 422
--   「未找到已发布的流程定义」（引擎刻意不兜底：审批数据落错表比启动失败更危险）。
--
-- 执行（L3 审批项；由 DBA / CI 在目标库运行）：
--       mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0057_expense_workflow_seed.sql
-- 幂等：种子 NOT EXISTS 守卫；UPDATE 天然幂等；iam_permission ON DUPLICATE KEY（code 唯一）。
-- =============================================================================

USE bone;

-- ── 1. 流程定义种子：expense_reimbursement v1 ─────────────────────────────
-- 档位口径与已删除的 ExpenseApprovalRouter 逐条等价（设计文档 §8 行为等价清单）：
--   ≤1000 → mgr（直属主管）；1000<x≤5000 → dept（部门负责人）；>5000 → finance（财务负责人）。
-- 金额缺失（isNull）→ 最高档 finance：fail-safe，绝不静默落最低档（router 旧行为是 NPE 500）。
-- 指派策略：FIELD approver_id（表单审批人字段，R5 保证大额必填）+ finance 档追加 ROLE FINANCE_HEAD
--   （RoleMemberProvider 未接入时该规则解析不出、不阻塞——union 语义，FIELD 命中即可派单；
--    两条都解析不出 ⇒ 引擎挂起 SUSPENDED + 告警，fail-closed 不放行）。
-- v1 全部 APPROVE 节点为 SINGLE（单签）：会签（ALL）与平台状态机「approve 边 SUBMITTED→APPROVED」
--   存在语义冲突（首个会签人通过后单据即离 SUBMITTED，余人无法再审批），故 v1 不启用会签。
--   ★ 2026-10-29 更新：该冲突已在**处理器侧**闭合——决策 Handler 改为「实例终态驱动」：实例已终结才声明按
--   to_status 推进，否则声明 platformStatus=KEEP（单据 status 原地不动）。理由：一次决策 ≠ 流程结束，
--   会签未齐、驳回退回指定节点时实例仍 RUNNING，此刻推进 status 会让余下办理人的动作恒 409（实测死单）。
--   于是 v2 起可安全启用会签（ALL / 比例会签），无需再改平台状态机语义。
--
-- 种子的结构回归：bone-app-expense 的 ExpenseWorkflowDefinitionTest 直接解析本段 JSON 驱动
-- WorkflowDefinitionValidator + 真引擎领域行为（同源零漂移）——改种子前先跑该测试。
INSERT INTO wf_process_def
    (id, tenant_id, def_key, def_name, def_version, status, enabled, definition_json,
     created_by, created_at, updated_by, updated_at, deleted, version)
SELECT
    CONCAT('def-expense-reimbursement-', t.tenant_id),
    t.tenant_id,
    'expense_reimbursement',
    '费用报销审批',
    1,
    'PUBLISHED',
    1,
    '{"key":"expense_reimbursement","name":"费用报销审批","version":1,"nodes":[{"code":"start","type":"START","mode":null,"label":"提交"},{"code":"cond_amount_null","type":"CONDITION","mode":null,"label":"金额缺失守卫"},{"code":"cond_high","type":"CONDITION","mode":null,"label":"金额>5000"},{"code":"cond_mid","type":"CONDITION","mode":null,"label":"金额>1000"},{"code":"mgr","type":"APPROVE","mode":"SINGLE","label":"直属主管审批"},{"code":"dept","type":"APPROVE","mode":"SINGLE","label":"部门负责人审批"},{"code":"finance","type":"APPROVE","mode":"SINGLE","label":"财务负责人审批"},{"code":"end","type":"END","mode":null,"label":"结束"}],"edges":[{"from":"start","to":"cond_amount_null","when":null,"label":null},{"from":"cond_amount_null","to":"finance","when":{"fieldCode":"amount","operator":"isNull"},"label":"金额缺失→最高档"},{"from":"cond_amount_null","to":"cond_high","when":null,"label":null},{"from":"cond_high","to":"finance","when":{"fieldCode":"amount","operator":"gt","value":5000},"label":"金额>5000"},{"from":"cond_high","to":"cond_mid","when":null,"label":null},{"from":"cond_mid","to":"dept","when":{"fieldCode":"amount","operator":"gt","value":1000},"label":"金额>1000"},{"from":"cond_mid","to":"mgr","when":null,"label":null},{"from":"mgr","to":"end","when":null,"label":null},{"from":"dept","to":"end","when":null,"label":null},{"from":"finance","to":"end","when":null,"label":null}],"assignments":{"mgr":[{"strategy":"FIELD","params":{"fieldCode":"approver_id"}}],"dept":[{"strategy":"FIELD","params":{"fieldCode":"approver_id"}}],"finance":[{"strategy":"FIELD","params":{"fieldCode":"approver_id"}},{"strategy":"ROLE","params":{"roleCode":"FINANCE_HEAD"}}]}}',
    'migration-0057', NOW(3), 'migration-0057', NOW(3), 0, 0
FROM (
    SELECT 0 AS tenant_id
    UNION
    SELECT DISTINCT tenant_id FROM meta_entity
     WHERE code = 'expense_reimbursement' AND deleted = 0  -- meta_entity 列名是 code（0037 同款）
) t
WHERE NOT EXISTS (
    SELECT 1 FROM wf_process_def d
     WHERE d.tenant_id = t.tenant_id AND d.def_key = 'expense_reimbursement'
       AND d.def_version = 1 AND d.deleted = 0
);
-- 注：只覆盖「已实例化报销实体的租户」+ 平台租户 0。新租户在实例化后再跑本脚本（或同批执行）即可；
--     租户无定义行时 submit 报 422「未找到已发布的流程定义」——可诊断，不静默。

-- ── 2. wf_binding 种子（与 handler 硬编码的 DEFINITION_KEY 一致；对账锚点） ──
-- v1 handler 硬编码 def_key，binding 为运营侧真源预告；两者必须一致（漂移对账见设计文档 §15.2）。
INSERT INTO wf_binding
    (id, tenant_id, entity_code, def_key, start_action, enabled,
     created_by, created_at, updated_by, updated_at, deleted, version)
SELECT
    CONCAT('bind-expense-reimbursement-', t.tenant_id),
    t.tenant_id,
    'expense_reimbursement',
    'expense_reimbursement',
    'submit',
    1,
    'migration-0057', NOW(3), 'migration-0057', NOW(3), 0, 0
FROM (
    SELECT 0 AS tenant_id
    UNION
    SELECT DISTINCT tenant_id FROM meta_entity
     WHERE code = 'expense_reimbursement' AND deleted = 0
) t
WHERE NOT EXISTS (
    SELECT 1 FROM wf_binding b
     WHERE b.tenant_id = t.tenant_id AND b.entity_code = 'expense_reimbursement' AND b.deleted = 0
);

-- ── 3. 四动作改逃逸（模板层 + 实例层同步） ─────────────────────────────────
-- submit   → expenseWorkflowSubmitHandler     （engine.start）
-- approve  → expenseWorkflowDecisionHandler   （engine.completeTask APPROVE）
-- reject   → expenseWorkflowDecisionHandler   （engine.completeTask REJECT）
-- withdraw → expenseWorkflowWithdrawHandler   （engine.withdraw）
-- 模板层（新实例化源头）：meta_model_template_action.template_id = 9170（tpl_expense_reimbursement_v2，0051）
UPDATE meta_model_template_action
   SET is_escape = 1,
       handler_bean = CASE action_code
           WHEN 'submit'   THEN 'expenseWorkflowSubmitHandler'
           WHEN 'approve'  THEN 'expenseWorkflowDecisionHandler'
           WHEN 'reject'   THEN 'expenseWorkflowDecisionHandler'
           WHEN 'withdraw' THEN 'expenseWorkflowWithdrawHandler'
       END,
       updated_at = NOW(3),
       version = version + 1
 WHERE template_id = 9170 AND deleted = 0
   AND action_code IN ('submit', 'approve', 'reject', 'withdraw');

-- 实例层（存量已实例化租户）：meta_entity_action_def
UPDATE meta_entity_action_def
   SET is_escape = 1,
       handler_bean = CASE action_code
           WHEN 'submit'   THEN 'expenseWorkflowSubmitHandler'
           WHEN 'approve'  THEN 'expenseWorkflowDecisionHandler'
           WHEN 'reject'   THEN 'expenseWorkflowDecisionHandler'
           WHEN 'withdraw' THEN 'expenseWorkflowWithdrawHandler'
       END,
       updated_at = NOW(3),
       version = version + 1
 WHERE entity_code = 'expense_reimbursement' AND deleted = 0
   AND action_code IN ('submit', 'approve', 'reject', 'withdraw');

-- ── 4. withdraw 动作级细码注册 + 授权（逃逸动作门禁强制） ─────────────────
-- submit/approve/reject 细码已由 0033 注册、0034 授权；withdraw 本次新增。
INSERT INTO iam_permission
    (id, code, name, type, resource_type, resource_path, action, description, parent_id, sort_order, created_by, created_at, updated_at, deleted)
VALUES
    (9125, 'runtime:expense_reimbursement:withdraw', '运行时-报销-撤回', 'OPERATION', 'metadata',
     '/api/v1/runtime/entities/expense_reimbursement/records/*/actions/withdraw', 'execute',
     '费用报销：撤回（SUBMITTED→DRAFT，仅发起人；逃逸动作终结审批流）', 58, 125, 1, NOW(3), NOW(3), 0)
ON DUPLICATE KEY UPDATE
    name = VALUES(name), resource_path = VALUES(resource_path), action = VALUES(action),
    description = VALUES(description), sort_order = VALUES(sort_order), updated_at = NOW(3);

-- 授权 id 不硬编码：iam_role_permission 既有 92xx 段手工 id、又有雪花 id，硬编码会撞（9209 已被 0032 占）；
--   取 MAX(id)+1 派生，NOT EXISTS 保证「角色-权限对」不重（重跑安全）。
-- SUPER_ADMIN（id=1，tenant 0）：补授 withdraw（submit/approve/reject 已由 0034 授）
INSERT INTO iam_role_permission (id, role_id, permission_id, created_at)
SELECT (SELECT COALESCE(MAX(id), 9000) + 1 FROM iam_role_permission), 1, p.id, NOW(3)
  FROM iam_permission p
 WHERE p.code = 'runtime:expense_reimbursement:withdraw' AND p.deleted = 0
   AND NOT EXISTS (SELECT 1 FROM iam_role_permission rp WHERE rp.role_id = 1 AND rp.permission_id = p.id);

-- demo 租户管理员（id=3，tenant 1001）：补授 withdraw
INSERT INTO iam_role_permission (id, role_id, permission_id, created_at)
SELECT (SELECT COALESCE(MAX(id), 9000) + 1 FROM iam_role_permission), 3, p.id, NOW(3)
  FROM iam_permission p
 WHERE p.code = 'runtime:expense_reimbursement:withdraw' AND p.deleted = 0
   AND NOT EXISTS (SELECT 1 FROM iam_role_permission rp WHERE rp.role_id = 3 AND rp.permission_id = p.id);

-- ── 5. 校验 ───────────────────────────────────────────────────────────────
SELECT
  (SELECT COUNT(*) FROM wf_process_def
     WHERE def_key = 'expense_reimbursement' AND status = 'PUBLISHED' AND deleted = 0) AS published_def_count,
  (SELECT COUNT(*) FROM wf_binding
     WHERE entity_code = 'expense_reimbursement' AND deleted = 0) AS binding_count,
  (SELECT COUNT(*) FROM meta_model_template_action
     WHERE template_id = 9170 AND deleted = 0 AND is_escape = 1
       AND action_code IN ('submit','approve','reject','withdraw')) AS template_escape_action_count,
  (SELECT COUNT(*) FROM meta_entity_action_def
     WHERE entity_code = 'expense_reimbursement' AND deleted = 0 AND is_escape = 1
       AND action_code IN ('submit','approve','reject','withdraw')) AS escape_action_count,
  (SELECT COUNT(*) FROM meta_entity_action_def
     WHERE entity_code = 'expense_reimbursement' AND deleted = 0
       AND action_code IN ('submit','approve','reject','withdraw')
       AND handler_bean LIKE 'expenseWorkflow%') AS handler_wired_count,
  (SELECT COUNT(*) FROM iam_role_permission rp
     JOIN iam_permission p ON p.id = rp.permission_id
     WHERE p.code = 'runtime:expense_reimbursement:withdraw') AS withdraw_grant_count;
