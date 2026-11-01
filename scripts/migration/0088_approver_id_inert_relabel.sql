-- =============================================================================
-- 0088_approver_id_inert_relabel.sql
--
-- 目的（2026-11-01 收口 · 决策 D1）：把 approver_id 的「惰性弃用」语义显式标注。
--
-- 背景：v3 路由（0083）让报销/采购的组织审批改读 iam_account.manager_id /
--       iam_dept.leader_id，approver_id 对 tenant 0 的报销/采购已成惰性字段
--       （不再参与路由）。但 v1/v2 的 33 个在途实例仍按启动时版本把它当
--       「申请人指定审批人」(FIELD / INITIATOR_SELECT) 读取，且 0081 已就该字段
--       语义登记过一次。故决策为「改标签而非删除」：保留列与历史数据，仅强化
--       标签说明其边界（与 ADR-0044 决策 5「expand/contract 保留列」一致）。
--
-- 范围纪律：只改注释（display_name 保持 0081 的「指定审批人」），不动物理列、
--       不动数据、不动流程定义。幂等：仅当 comment 尚未含惰性弃用标记时更新，
--       重复执行命中 0 行。
-- 执行：mysql --default-character-set=utf8mb4 -uroot -p bone < 0088_approver_id_inert_relabel.sql
-- =============================================================================

USE bone;

-- ── 1. 模型模板字段（新实例化源头）：补「组织策略不读」与「v1/v2 在途仍读」说明 ──
UPDATE meta_model_template_field
   SET comment = '流程输入：发起时由申请人指定，仅对流程定义中 strategy=FIELD / INITIATOR_SELECT 的审批节点生效。'
                '组织策略 LINE_MANAGER / DEPT_HEAD / LEVEL_UP 不读取本字段（改读 iam_account.manager_id / iam_dept.leader_id，见 0082/0083/0084）。'
                'v1/v2 在途实例仍按启动版本把它作为「申请人指定审批人」读取——禁止删除本列（expand/contract）。'
                '当前/历史审批人不在单据上——改派、加签、会签后的真实办理人以引擎任务台账 wf_task 为准。',
       updated_at = NOW(3),
       version = version + 1
 WHERE code = 'approver_id'
   AND deleted = 0
   AND comment NOT LIKE '%组织策略 LINE_MANAGER%';

-- ── 2. 已实例化实体字段：同步同一说明（0081 已统一 display_name，此处只补边界） ──
UPDATE meta_field
   SET comment = '流程输入：发起时由申请人指定，仅对流程定义中 strategy=FIELD / INITIATOR_SELECT 的审批节点生效。'
                '组织策略 LINE_MANAGER / DEPT_HEAD / LEVEL_UP 不读取本字段（改读 iam_account.manager_id / iam_dept.leader_id，见 0082/0083/0084）。'
                'v1/v2 在途实例仍按启动版本把它作为「申请人指定审批人」读取——禁止删除本列（expand/contract）。'
                '当前/历史审批人不在单据上——改派、加签、会签后的真实办理人以引擎任务台账 wf_task 为准。',
       updated_at = NOW(3),
       version = version + 1
 WHERE code = 'approver_id'
   AND deleted = 0
   AND comment NOT LIKE '%组织策略 LINE_MANAGER%';

-- ── 3. 校验：不应再有未标注惰性弃用的 approver_id；打印供人工复核 ──────────────
SELECT
  (SELECT COUNT(*) FROM meta_field
    WHERE code = 'approver_id' AND deleted = 0 AND comment NOT LIKE '%组织策略 LINE_MANAGER%') AS field_unlabeled,
  (SELECT COUNT(*) FROM meta_model_template_field
    WHERE code = 'approver_id' AND deleted = 0 AND comment NOT LIKE '%组织策略 LINE_MANAGER%') AS template_unlabeled;
