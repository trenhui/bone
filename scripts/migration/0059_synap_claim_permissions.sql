-- =============================================================================
-- 0059_synap_claim_permissions.sql
--
-- 目的（2026-10-25 联调收尾）：synap-claim 理赔模块 6 个权限码注册 + 超管（role 1）绑定。
--   控制器 @PreAuthorize 实测口径（7 个控制器去重）：claim:claims:read / claim:claims:write /
--   claim:review:write / claim:appeal:write / claim:decision:write / claim:settlement:write。
--   缺码后果：admin JWT scopes 不含这些码 → 理赔全部写端点 403（读端点亦 403），
--   链路"起了但不可用"。与 0045（runtime 授权修复）同属"种子侧补齐"类变更。
--
-- 设计
-- ----------------------
-- · id 段：iam_permission 现用最大 113 → 新码取 114-119；iam_role_permission 现用最大 307
--   → 新绑定取 308-313（均为各表空位，不与在途 ID 冲突）；
-- · sort_order 700-750：现用最大 630（generator 域），理赔域接在其后；
-- · 只绑 role 1（超管）：与 generator:* 写码同口径——写端点挂了这些码，超管必须持码；
--   租户管理员（role 3）不绑（理赔域属平台运营面，与 sys:* 平台域写码同理）；
-- · 幂等：INSERT 均带 NOT EXISTS 守卫（iam_permission 按 code，iam_role_permission 按
--   (role_id, permission_id) 联合判重），可重复执行。
--
-- 后置：admin/超管需重新登录刷新 JWT scopes（权限随登录下发，不停服即时生效）。
--
-- 执行（本地/测试库直接应用；生产数据变更属 L3，由 DBA / CI 在目标库运行）：
--       mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0059_synap_claim_permissions.sql
-- =============================================================================

USE bone;

-- ── 1. 注册 6 个理赔权限码 ──────────────────────────────────────────────────
INSERT INTO iam_permission (id, code, name, resource_type, resource_path, action, type, sort_order, description)
SELECT * FROM (
    SELECT 114 AS id, 'claim:claims:read' AS code, '理赔-赔案读' AS name, 'claim' AS resource_type,
           'claims' AS resource_path, 'read' AS action, 'OPERATION' AS type, 700 AS sort_order,
           '赔案列表/详情/工作台查看（ClaimCaseController 等 7 控制器读端点）' AS description
    UNION ALL SELECT 115, 'claim:claims:write', '理赔-赔案写', 'claim', 'claims', 'write', 'OPERATION', 710,
           '赔案创建/修改/提交/证据上传（ClaimCaseController、EvidenceController @PreAuthorize）'
    UNION ALL SELECT 116, 'claim:review:write', '理赔-审核写', 'claim', 'review', 'write', 'OPERATION', 720,
           '理算/审核/调整（AdjustmentController、ClaimCaseController 审核端点 @PreAuthorize）'
    UNION ALL SELECT 117, 'claim:appeal:write', '理赔-申诉写', 'claim', 'appeal', 'write', 'OPERATION', 730,
           '申诉申请/处理（AppealController @PreAuthorize）'
    UNION ALL SELECT 118, 'claim:decision:write', '理赔-决策写', 'claim', 'decision', 'write', 'OPERATION', 740,
           '决策图执行/回放（DecisionController @PreAuthorize）'
    UNION ALL SELECT 119, 'claim:settlement:write', '理赔-结算写', 'claim', 'settlement', 'write', 'OPERATION', 750,
           '结算单创建/资金流水（SettlementController @PreAuthorize）'
) AS new_codes
WHERE NOT EXISTS (SELECT 1 FROM iam_permission p WHERE p.code = new_codes.code);

-- ── 2. 超管（role 1）绑定 6 码 ──────────────────────────────────────────────
INSERT INTO iam_role_permission (id, role_id, permission_id)
SELECT * FROM (
    SELECT 308 AS id, 1 AS role_id, 114 AS permission_id
    UNION ALL SELECT 309, 1, 115
    UNION ALL SELECT 310, 1, 116
    UNION ALL SELECT 311, 1, 117
    UNION ALL SELECT 312, 1, 118
    UNION ALL SELECT 313, 1, 119
) AS new_bindings
WHERE NOT EXISTS (
    SELECT 1 FROM iam_role_permission rp
     WHERE rp.role_id = new_bindings.role_id
       AND rp.permission_id = new_bindings.permission_id
);

-- ── 3. 验收 ─────────────────────────────────────────────────────────────────
-- SELECT p.code FROM iam_permission p JOIN iam_role_permission rp ON rp.permission_id = p.id
--  WHERE rp.role_id = 1 AND p.code LIKE 'claim:%' ORDER BY p.sort_order;
-- 预期 6 行。
