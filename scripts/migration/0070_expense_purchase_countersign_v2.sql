-- =============================================================================
-- 0070_expense_purchase_countersign_v2.sql
--
-- 目的（大额会签 · 企业级复杂审批 · 种子段，L3 审批项）：
--   ① 补建第二会签人 demo 账号 mdapprover3（id 9105，复制 mdapprover2 的密码哈希/租户，
--      仅用于冒烟；生产按实际审批矩阵替换 userId）；
--   ② 发布 expense_reimbursement v2：金额三档分支保持不变，大额档（>5000 或金额缺失 fail-safe）
--      升级为「财务负责人 + 分管副总」双人**会签**（node.mode=ALL，两条 USER 指派规则）；
--   ③ 发布 purchase_order v2：常规单级审批保持不变，大额档（>10000）升级为双人会签；
--   ④ 低额/中额档仍为 SINGLE（单人），与 v1 行为等价，保证既有 SoD / 退回 / 撤回场景零回归。
--
-- 为什么用 USER 策略而不是 ROLE：
--   当前运行实例未接入 RoleMemberProvider（全仓无 implements RoleMemberProvider），ROLE 策略解析不出
--   成员会直接挂起（SUSPENDED）。USER 策略把固定账号 id 作为 assigneeId，无需组织目录即可稳定解析，
--   是「谁与谁会签」这一审批矩阵最忠实的落点——矩阵变更 = 改两条 userId，不涉及平台语义。
--   生产部署将下方 userId（9104/9105）替换为真实的财务负责人 / 分管副总账号 id 即可。
--
-- 与 v1 的关系：保留 v1 行，v2 为更高版本；运行时 findLatestPublished 取 v2。wf_binding 按 def_key
--   绑定（与版本无关），无需改动。
--
-- 执行（L3 审批项；由 DBA / CI 在目标库运行）：
--       mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0070_expense_purchase_countersign_v2.sql
-- 幂等：mdapprover3 以 NOT EXISTS 守卫；v2 以 def_version=2 NOT EXISTS 守卫；重复执行不堆行、不报错。
-- =============================================================================

USE bone;

-- ── 0. 第二会签人 mdapprover3（demo，仅冒烟） ─────────────────────────────
-- 复制 mdapprover2 的密码哈希（登录密码同为 Approver#2026）与租户；is_admin=1 仅用于打通审批权限，
--   与 mdapprover2 同口径。生产环境请勿照搬 is_admin，应改挂 runtime 审批细码角色。
INSERT INTO iam_account
    (id, tenant_id, username, password_hash, email, real_name, dept_id,
     status, is_admin, created_at, updated_at, deleted, version)
SELECT 9105, 0, 'mdapprover3', password_hash, 'mdapprover3@bone.demo', '平台冒烟审批人B', NULL,
       1, 1, NOW(3), NOW(3), 0, 0
FROM iam_account WHERE username = 'mdapprover2'
  AND NOT EXISTS (
    SELECT 1 FROM iam_account WHERE username = 'mdapprover3' OR id = 9105
);

-- ── 1. 报销流程定义 v2（大额会签） ─────────────────────────────────────────
-- 档位口径与 v1 完全一致：≤1000 → mgr；1000<x≤5000 → dept；>5000 或金额缺失 → 大额档。
-- 大额档从 SINGLE(财务单人) 升级为 ALL(财务负责人 + 分管副总 会签)：
--   实例在该节点生成两条 wf_task（assigneeId 9104 / 9105），需两人都 approve 才推进；
--   任一人先批 ⇒ 实例仍在 RUNNING、单据 status 保持 SUBMITTED（由决策 Handler 的「实例终态驱动」
--   保证，不会假通过）；两人都批 ⇒ 实例 APPROVED ⇒ 单据 APPROVED。
INSERT INTO wf_process_def
    (id, tenant_id, def_key, def_name, def_version, status, enabled, definition_json,
     created_by, created_at, updated_by, updated_at, deleted, version)
SELECT
    CONCAT('def-expense-reimbursement-v2-', t.tenant_id),
    t.tenant_id,
    'expense_reimbursement',
    '费用报销审批（含大额会签）',
    2,
    'PUBLISHED',
    1,
    '{"key":"expense_reimbursement","name":"费用报销审批（含大额会签）","version":2,"nodes":[{"code":"start","type":"START","mode":null,"label":"提交"},{"code":"cond_amount_null","type":"CONDITION","mode":null,"label":"金额缺失守卫"},{"code":"cond_high","type":"CONDITION","mode":null,"label":"金额>5000"},{"code":"cond_mid","type":"CONDITION","mode":null,"label":"金额>1000"},{"code":"mgr","type":"APPROVE","mode":"SINGLE","label":"直属主管审批"},{"code":"dept","type":"APPROVE","mode":"SINGLE","label":"部门负责人审批"},{"code":"finance_cosign","type":"APPROVE","mode":"ALL","label":"大额会签（财务负责人+分管副总）"},{"code":"end","type":"END","mode":null,"label":"结束"}],"edges":[{"from":"start","to":"cond_amount_null","when":null,"label":null},{"from":"cond_amount_null","to":"finance_cosign","when":{"fieldCode":"amount","operator":"isNull"},"label":"金额缺失→最高档会签"},{"from":"cond_amount_null","to":"cond_high","when":null,"label":null},{"from":"cond_high","to":"finance_cosign","when":{"fieldCode":"amount","operator":"gt","value":5000},"label":"金额>5000"},{"from":"cond_high","to":"cond_mid","when":null,"label":null},{"from":"cond_mid","to":"dept","when":{"fieldCode":"amount","operator":"gt","value":1000},"label":"金额>1000"},{"from":"cond_mid","to":"mgr","when":null,"label":null},{"from":"mgr","to":"end","when":null,"label":null},{"from":"dept","to":"end","when":null,"label":null},{"from":"finance_cosign","to":"end","when":null,"label":null}],"assignments":{"mgr":[{"strategy":"FIELD","params":{"fieldCode":"approver_id"}}],"dept":[{"strategy":"FIELD","params":{"fieldCode":"approver_id"}}],"finance_cosign":[{"strategy":"USER","params":{"userId":"9104"}},{"strategy":"USER","params":{"userId":"9105"}}]}}',
    'migration-0070', NOW(3), 'migration-0070', NOW(3), 0, 0
FROM (
    SELECT 0 AS tenant_id
    UNION
    SELECT DISTINCT tenant_id FROM meta_entity
     WHERE code = 'expense_reimbursement' AND deleted = 0
) t
WHERE NOT EXISTS (
    SELECT 1 FROM wf_process_def d
     WHERE d.tenant_id = t.tenant_id AND d.def_key = 'expense_reimbursement'
       AND d.def_version = 2 AND d.deleted = 0
);

-- ── 2. 采购流程定义 v2（大额会签） ─────────────────────────────────────────
-- 常规（≤10000）维持 v1 单级审批（FIELD approver_id）；>10000 升级为双人会签（采购负责人 + 财务，
--   此处 demo 复用 9104/9105 两个审批账号；生产替换为实际矩阵账号）。
INSERT INTO wf_process_def
    (id, tenant_id, def_key, def_name, def_version, status, enabled, definition_json,
     created_by, created_at, updated_by, updated_at, deleted, version)
SELECT
    CONCAT('def-purchase-order-v2-', t.tenant_id),
    t.tenant_id,
    'purchase_order',
    '采购订单审批（含大额会签）',
    2,
    'PUBLISHED',
    1,
    '{"key":"purchase_order","name":"采购订单审批（含大额会签）","version":2,"nodes":[{"code":"start","type":"START","mode":null,"label":"提交"},{"code":"cond_high","type":"CONDITION","mode":null,"label":"金额>10000"},{"code":"approve","type":"APPROVE","mode":"SINGLE","label":"采购审批"},{"code":"purchase_cosign","type":"APPROVE","mode":"ALL","label":"大额会签（采购负责人+财务）"},{"code":"end","type":"END","mode":null,"label":"结束"}],"edges":[{"from":"start","to":"cond_high","when":null,"label":null},{"from":"cond_high","to":"purchase_cosign","when":{"fieldCode":"po_amount","operator":"gt","value":10000},"label":"金额>10000"},{"from":"cond_high","to":"approve","when":null,"label":null},{"from":"approve","to":"end","when":null,"label":null},{"from":"purchase_cosign","to":"end","when":null,"label":null}],"assignments":{"approve":[{"strategy":"FIELD","params":{"fieldCode":"approver_id"}}],"purchase_cosign":[{"strategy":"USER","params":{"userId":"9104"}},{"strategy":"USER","params":{"userId":"9105"}}]}}',
    'migration-0070', NOW(3), 'migration-0070', NOW(3), 0, 0
FROM (
    SELECT 0 AS tenant_id
    UNION
    SELECT DISTINCT tenant_id FROM meta_entity
     WHERE code = 'purchase_order' AND deleted = 0
) t
WHERE NOT EXISTS (
    SELECT 1 FROM wf_process_def d
     WHERE d.tenant_id = t.tenant_id AND d.def_key = 'purchase_order'
       AND d.def_version = 2 AND d.deleted = 0
);

-- ── 3. 校验 ───────────────────────────────────────────────────────────────
SELECT
  (SELECT COUNT(*) FROM iam_account WHERE username = 'mdapprover3' AND deleted = 0) AS mdapprover3_count,
  (SELECT COUNT(*) FROM wf_process_def
     WHERE def_key = 'expense_reimbursement' AND def_version = 2 AND status = 'PUBLISHED' AND deleted = 0) AS expense_v2_count,
  (SELECT COUNT(*) FROM wf_process_def
     WHERE def_key = 'purchase_order' AND def_version = 2 AND status = 'PUBLISHED' AND deleted = 0) AS purchase_v2_count,
  (SELECT COUNT(*) FROM wf_process_def
     WHERE def_key = 'expense_reimbursement' AND status = 'PUBLISHED' AND deleted = 0) AS expense_published_total,
  (SELECT COUNT(*) FROM wf_process_def
     WHERE def_key = 'purchase_order' AND status = 'PUBLISHED' AND deleted = 0) AS purchase_published_total;
