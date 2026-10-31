-- ============================================================
-- 0083_expense_org_routing_v3.sql
-- 报销 / 采购流程 v3：审批人从「申请人自选的表单字段」改为「组织层级实时解析」。
--
-- 【修的是什么】
--   v1/v2 的 mgr（直属主管审批）与 dept（部门负责人审批）两个节点都配
--   strategy=FIELD / fieldCode=approver_id —— 也就是同一个「指定审批人」字段。
--   两个语义不同的节点因此解析成同一个人，分级审批退化为「一个人连续审两级」。
--   实测证据（迁移执行前的 wf_task）：tenant 0 报销实例中 mgr 与 dept 的
--   assignee_id 全部是 9104，且 95 张单据的 approver_id（物理列 ext_str_01）恒为 9104。
--
--   更深一层的问题不是「字段取错」，而是**审批人由申请人自己指定**：
--   提交人挑选谁来审自己，等于把这道控制点交给被控制方（SoD 缺陷）。
--   v3 改为引擎从 IAM 组织层级解析，申请人不再参与决定审批人。
--
-- 【为什么依赖 0082】
--   LINE_MANAGER 读 iam_account.manager_id，DEPT_HEAD 读 iam_dept.leader_id。
--   0082 建列并回填。**本脚本必须在 0082 之后执行**：在组织数据缺失时提前切 v3，
--   所有报销单会挂起（fail-closed，不会静默放行，但会造成业务中断）。
--
-- 【运行期开关（本脚本之外还有一道）】
--   组织目录接入由 bone.workflow.org-directory.enabled 控制（默认 false）。
--   开关未开时 OrgHierarchyProvider bean 不存在，组织类策略一律挂起。
--   即：v3 发布 + 开关打开，两者同时具备才真正按组织层级派单。
--
-- 【为什么不改图、只改指派】
--   v3 的 nodes / edges 与 v2 逐字一致，唯一差异是 assignments。
--   一次只动一个变量：图结构变更（例如把「>1000」从单级部门负责人改为
--   「主管 → 部门负责人」两级串行）是产品决策，应与内控口径一起评审，
--   不该混在「修审批人取数」这次改动里——否则一旦出问题无法判断是取数错了还是流程改了。
--
-- 【回滚（无需发版，分钟级生效）】
--   findLatestPublished 的谓词是 status='PUBLISHED' AND enabled=1 AND deleted=0
--   取 max(def_version)。因此下线 v3 即自动回落到 v2：
--     UPDATE wf_process_def SET enabled = 0
--      WHERE def_key IN ('expense_reimbursement','purchase_order') AND def_version = 3;
--   在途实例按启动时的 def_version 装载，不受影响（wf_process_def 是不可变快照）。
--
-- 【幂等性】
--   version=3 以 NOT EXISTS 守卫，重复执行不堆行、不覆盖已发布内容。
--   **不原地改已发布行**：wf_process_def 每行都是历史单据的审批依据快照。
-- ============================================================

-- ---------- ① 报销流程 v3：mgr → LINE_MANAGER，dept → DEPT_HEAD ----------
INSERT INTO wf_process_def
    (id, tenant_id, def_key, def_name, def_version, status, enabled, definition_json,
     created_by, created_at, updated_by, updated_at, deleted, version)
SELECT
    CONCAT('def-expense-reimbursement-v3-', t.tenant_id),
    t.tenant_id,
    'expense_reimbursement',
    '费用报销审批（组织层级分级）',
    3,
    'PUBLISHED',
    1,
    '{"key":"expense_reimbursement","name":"费用报销审批（组织层级分级）","version":3,"nodes":[{"code":"start","type":"START","mode":null,"label":"提交"},{"code":"cond_amount_null","type":"CONDITION","mode":null,"label":"金额缺失守卫"},{"code":"cond_high","type":"CONDITION","mode":null,"label":"金额>5000"},{"code":"cond_mid","type":"CONDITION","mode":null,"label":"金额>1000"},{"code":"mgr","type":"APPROVE","mode":"SINGLE","label":"直属主管审批"},{"code":"dept","type":"APPROVE","mode":"SINGLE","label":"部门负责人审批"},{"code":"finance_cosign","type":"APPROVE","mode":"ALL","label":"大额会签（财务负责人+分管副总）"},{"code":"end","type":"END","mode":null,"label":"结束"}],"edges":[{"from":"start","to":"cond_amount_null","when":null,"label":null},{"from":"cond_amount_null","to":"finance_cosign","when":{"fieldCode":"amount","operator":"isNull"},"label":"金额缺失→最高档会签"},{"from":"cond_amount_null","to":"cond_high","when":null,"label":null},{"from":"cond_high","to":"finance_cosign","when":{"fieldCode":"amount","operator":"gt","value":5000},"label":"金额>5000"},{"from":"cond_high","to":"cond_mid","when":null,"label":null},{"from":"cond_mid","to":"dept","when":{"fieldCode":"amount","operator":"gt","value":1000},"label":"金额>1000"},{"from":"cond_mid","to":"mgr","when":null,"label":null},{"from":"mgr","to":"end","when":null,"label":null},{"from":"dept","to":"end","when":null,"label":null},{"from":"finance_cosign","to":"end","when":null,"label":null}],"assignments":{"mgr":[{"strategy":"LINE_MANAGER","params":{}}],"dept":[{"strategy":"DEPT_HEAD","params":{}}],"finance_cosign":[{"strategy":"USER","params":{"userId":"9104"}},{"strategy":"USER","params":{"userId":"9105"}}]}}',
    'migration-0083', NOW(3), 'migration-0083', NOW(3), 0, 0
FROM (
    -- 两个租户都要发：tenant 0 是平台演示租户，其余按是否存在该实体推导。
    SELECT 0 AS tenant_id
    UNION
    SELECT DISTINCT tenant_id FROM meta_entity
     WHERE code = 'expense_reimbursement' AND deleted = 0
) t
WHERE NOT EXISTS (
    SELECT 1 FROM wf_process_def d
     WHERE d.tenant_id = t.tenant_id AND d.def_key = 'expense_reimbursement'
       AND d.def_version = 3
);

-- ---------- ② 采购流程 v3：approve → DEPT_HEAD ----------
-- 采购单级审批同样原为 FIELD/approver_id（申请人自选），一并收敛到部门负责人。
-- 大额会签节点（purchase_cosign）的 USER 指派保持不变：那是审批矩阵，不是组织推导。
INSERT INTO wf_process_def
    (id, tenant_id, def_key, def_name, def_version, status, enabled, definition_json,
     created_by, created_at, updated_by, updated_at, deleted, version)
SELECT
    CONCAT('def-purchase-order-v3-', t.tenant_id),
    t.tenant_id,
    'purchase_order',
    '采购订单审批（组织层级分级）',
    3,
    'PUBLISHED',
    1,
    '{"key":"purchase_order","name":"采购订单审批（组织层级分级）","version":3,"nodes":[{"code":"start","type":"START","mode":null,"label":"提交"},{"code":"cond_high","type":"CONDITION","mode":null,"label":"金额>10000"},{"code":"approve","type":"APPROVE","mode":"SINGLE","label":"采购审批"},{"code":"purchase_cosign","type":"APPROVE","mode":"ALL","label":"大额会签（采购负责人+财务）"},{"code":"end","type":"END","mode":null,"label":"结束"}],"edges":[{"from":"start","to":"cond_high","when":null,"label":null},{"from":"cond_high","to":"purchase_cosign","when":{"fieldCode":"po_amount","operator":"gt","value":10000},"label":"金额>10000"},{"from":"cond_high","to":"approve","when":null,"label":null},{"from":"approve","to":"end","when":null,"label":null},{"from":"purchase_cosign","to":"end","when":null,"label":null}],"assignments":{"approve":[{"strategy":"DEPT_HEAD","params":{}}],"purchase_cosign":[{"strategy":"USER","params":{"userId":"9104"}},{"strategy":"USER","params":{"userId":"9105"}}]}}',
    'migration-0083', NOW(3), 'migration-0083', NOW(3), 0, 0
FROM (
    SELECT 0 AS tenant_id
    UNION
    SELECT DISTINCT tenant_id FROM meta_entity
     WHERE code = 'purchase_order' AND deleted = 0
) t
WHERE NOT EXISTS (
    SELECT 1 FROM wf_process_def d
     WHERE d.tenant_id = t.tenant_id AND d.def_key = 'purchase_order'
       AND d.def_version = 3
);

-- ---------- ③ 校验 ----------
-- 逐条断言而不是只 SELECT 计数：执行者必须能用肉眼判断「切换是否真的生效」。
SELECT
  (SELECT COUNT(*) FROM wf_process_def
     WHERE def_key='expense_reimbursement' AND def_version=3 AND status='PUBLISHED'
       AND enabled=1 AND deleted=0) AS expense_v3_published,
  (SELECT COUNT(*) FROM wf_process_def
     WHERE def_key='expense_reimbursement' AND def_version=3
       AND definition_json LIKE '%"mgr":[{"strategy":"LINE_MANAGER"%'
       AND definition_json LIKE '%"dept":[{"strategy":"DEPT_HEAD"%') AS expense_v3_org_routing,
  (SELECT COUNT(*) FROM wf_process_def
     WHERE def_key='purchase_order' AND def_version=3
       AND definition_json LIKE '%"approve":[{"strategy":"DEPT_HEAD"%') AS purchase_v3_org_routing,
  -- v3 必须仍是每个 key 的最高版本；否则「发布了但没生效」（被更高版本遮蔽）
  (SELECT COUNT(*) FROM wf_process_def d
     WHERE d.def_key IN ('expense_reimbursement','purchase_order') AND d.def_version > 3
       AND d.deleted = 0) AS higher_version_rows;

-- 组织数据就绪度：这些账号若发起单据，v3 会在对应节点挂起（fail-closed，非故障）。
-- 数字为 0 说明该租户尚无人具备审批依据，应先补组织数据再切开关。
SELECT
  (SELECT COUNT(*) FROM iam_account WHERE tenant_id=0 AND deleted=0 AND manager_id IS NULL) AS tenant0_accounts_without_manager,
  (SELECT COUNT(*) FROM iam_dept WHERE tenant_id=0 AND deleted=0 AND leader_id IS NULL) AS tenant0_depts_without_leader,
  (SELECT COUNT(*) FROM iam_account WHERE tenant_id=1001 AND deleted=0 AND manager_id IS NULL) AS tenant1001_accounts_without_manager,
  (SELECT COUNT(*) FROM iam_dept WHERE tenant_id=1001 AND deleted=0 AND leader_id IS NULL) AS tenant1001_depts_without_leader;
