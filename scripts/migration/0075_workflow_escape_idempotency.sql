-- =============================================================================
-- 0075_workflow_escape_idempotency.sql
--
-- 目的（工作流引擎设计 §9.2 P0-1 · 幂等闭环）：
--   报销的 submit / approve / reject / withdraw 四个动作由 0057 改成逃逸动作（接工作流引擎）时，
--   只写了 is_escape + handler_bean，没有同步 requires_idempotency——SQL 直改绕过了建模写期的
--   硬不变量（MetaEntityApplicationService#upsertActionDef：逃逸动作必须声明幂等键，不自动补）。
--
--   后果：这四条动作在库里是「逃逸但不要求幂等键」，RuntimeActionApplicationService 因此不校验
--   Idempotency-Key、meta_runtime_action_execution 的唯一键（五元组含 NULL 不冲突）也形同虚设：
--   超时重发 / 连点会把 engine.start / completeTask / withdraw 再跑一遍。引擎侧的 CAS 只兜住
--   「状态已变」，兜不住「同一次点击被重复投递」这一层（0035 注释已点名该缺口）。
--
-- 口径：
--   · 只改「工作流逃逸动作」（按 handler_bean 精确匹配），不碰其它实体的 submit/approve 等
--     同名动作——那些动作没有工作流副作用，强制幂等键只会让它们的调用方无谓 422。
--   · 模板层（meta_model_template_action）是新租户实例化的源头，实例层（meta_entity_action_def）
--     是存量租户的运行期真源，两层都要改，缺一层就出现「新单要幂等键、老单不要」的分叉。
--   · 采购（purchase_order）三动作由 e2e / 建模 API 建时已带 requires_idempotency=1，
--     此处一并兜底覆盖「人工或脚本直接落库」的部署。
--
-- 执行（L3 审批项；由 DBA / CI 在目标库运行）：
--       mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0075_workflow_escape_idempotency.sql
-- 幂等：UPDATE … WHERE … requires_idempotency = 0，重复执行不产生副作用。
-- 影响面：调用方（前端审批中心 / RuntimeDataManagement / e2e）已按 requiresIdempotencyKey 自动补
--       Idempotency-Key（审批中心 key = decision-taskId，运行时数据页按 describe 的
--       requiresIdempotencyKey 生成），故本变更不破坏既有调用。
-- =============================================================================

USE bone;

-- ── 1. 实例层 meta_entity_action_def：工作流逃逸动作 → 必须幂等 ──────────────
UPDATE meta_entity_action_def
   SET requires_idempotency = 1,
       updated_at = NOW(3),
       version = version + 1
 WHERE deleted = 0
   AND handler_bean IN (
       'expenseWorkflowSubmitHandler',
       'expenseWorkflowDecisionHandler',
       'expenseWorkflowWithdrawHandler',
       'purchaseWorkflowSubmitHandler',
       'purchaseWorkflowDecisionHandler'
   )
   AND requires_idempotency = 0;

-- ── 2. 模板层 meta_model_template_action：新实例化源头同步（否则新租户又回到 0） ──
UPDATE meta_model_template_action
   SET requires_idempotency = 1,
       updated_at = NOW(3),
       version = version + 1
 WHERE deleted = 0
   AND handler_bean IN (
       'expenseWorkflowSubmitHandler',
       'expenseWorkflowDecisionHandler',
       'expenseWorkflowWithdrawHandler',
       'purchaseWorkflowSubmitHandler',
       'purchaseWorkflowDecisionHandler'
   )
   AND requires_idempotency = 0;

-- ── 3. 校验：工作流逃逸动作必须 100% 要求幂等键（返回 0 即通过） ─────────────
SELECT 'instance_layer_not_idempotent' AS check_name,
       COUNT(*)                        AS violations
  FROM meta_entity_action_def
 WHERE deleted = 0
   AND is_escape = 1
   AND requires_idempotency = 0
   AND handler_bean LIKE '%Workflow%'
UNION ALL
SELECT 'template_layer_not_idempotent',
       COUNT(*)
  FROM meta_model_template_action
 WHERE deleted = 0
   AND is_escape = 1
   AND requires_idempotency = 0
   AND handler_bean LIKE '%Workflow%';
