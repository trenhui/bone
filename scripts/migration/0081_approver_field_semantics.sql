-- =============================================================================
-- 0081_approver_field_semantics.sql
--
-- 目的：把「审批人」字段的**语义**收敛为「指定审批人（流程输入）」，并消除 6 处措辞不一致。
--
-- 背景（本次设计复核结论）：
--   审批是 (节点 × 轮次 × 办理人) 的 1:N 事实，真源是引擎任务台账 wf_task——实测同一实例的
--   finance_cosign 节点同时挂 2 条 PENDING（9104/9105 会签）。而单据上只有**一个** approver_id
--   标量，且没有任何代码写它（全仓 grep 0 引用）、改派/加签/转办都只改 wf_task 不回写单据，
--   于是它必然陈旧。它真正的语义只有一个：**申请人发起时指定谁审**（流程定义里
--   strategy=FIELD 的节点按它解析办理人）。
--
--   6 个实体的同一字段此前写着 4 种不同名字（`approver_id` / `审批人` / `审批人账户ID` /
--   `审批人（工作流 FIELD 指派，须等于审批操作人账户 id）`）——措辞分裂本身就是概念混乱的症状。
--   本次统一为「指定审批人」，并在 comment 里写明边界：改派/加签/会签后的真实办理人
--   **不在本字段**，一律查引擎台账；界面上的「当前审批人」列也从引擎派生，不读本字段。
--
-- 影响面：**纯元数据展示措辞**，不动物理列、不动数据、不动流程定义（避免审批链路回归）。
--   存量数据（9104/1/760340491857297408）保持原样：它们是历史输入值，本身就是「申请人指定了谁」。
--
-- 幂等：两条 UPDATE 均以「措辞未达标」为条件，重复执行命中 0 行。
-- 执行：mysql --default-character-set=utf8mb4 -uroot -p bone < 0081_approver_field_semantics.sql
-- =============================================================================

USE bone;

-- ── 1. 实体字段：统一措辞 + 写清边界（按 code 全量收敛，覆盖所有报销/采购类实体） ──────
UPDATE meta_field
   SET display_name = '指定审批人',
       comment      = '流程输入：发起时由申请人指定，仅对流程定义中 strategy=FIELD 的审批节点生效。'
                      '当前/历史审批人不在单据上——改派、加签、会签后的真实办理人以引擎任务台账 wf_task 为准。',
       updated_at   = NOW(3),
       version      = version + 1
 WHERE code = 'approver_id'
   AND deleted = 0
   AND (display_name <> '指定审批人' OR comment IS NULL OR comment NOT LIKE '%wf_task%');

-- ── 2. 模型模板：新实例化的源头同步（否则新租户仍拿到旧措辞） ────────────────────────
UPDATE meta_model_template_field
   SET display_name = '指定审批人',
       comment      = '流程输入：发起时由申请人指定，仅对流程定义中 strategy=FIELD 的审批节点生效。'
                      '当前/历史审批人不在单据上——改派、加签、会签后的真实办理人以引擎任务台账 wf_task 为准。',
       updated_at   = NOW(3),
       version      = version + 1
 WHERE code = 'approver_id'
   AND deleted = 0
   AND (display_name <> '指定审批人' OR comment IS NULL OR comment NOT LIKE '%wf_task%');

-- ── 3. 校验：不应再有措辞分裂；打印结果供人工复核 ────────────────────────────────
SELECT
  (SELECT COUNT(*) FROM meta_field
    WHERE code = 'approver_id' AND deleted = 0 AND display_name <> '指定审批人') AS field_inconsistent,
  (SELECT COUNT(*) FROM meta_model_template_field
    WHERE code = 'approver_id' AND deleted = 0 AND display_name <> '指定审批人') AS template_inconsistent,
  (SELECT COUNT(*) FROM meta_field
    WHERE code = 'approver_id' AND deleted = 0) AS field_total;

SELECT f.entity_id, e.code AS entity_code, f.display_name, f.comment
  FROM meta_field f
  JOIN meta_entity e ON e.id = f.entity_id
 WHERE f.code = 'approver_id' AND f.deleted = 0
 ORDER BY e.code;
