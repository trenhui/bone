-- ============================================================
-- 0129_seed_procurement_ai_governance_permissions.sql
--
-- 目的：为 procuraAI 决策治理（批次1）8 个 HTTP 端点登记权限码并绑定超管（role 1）。
--   · 写端点（登记 / 状态变更 / 人工终决）：procurement:ai-governance:write
--   · 读端点（清单 / 详情 / 裁决 / 追溯 / override 计数）：procurement:ai-governance:read
--
-- 号段：iam_permission 用 940000000000000001-002；iam_role_permission 用 940000000000000101-102。
--   与 0099 采购权限（920...）、0127 合同审查权限（930...）彻底错开。
-- sort_order 840-850：接在 0127 合同审查（820-830）之后。
-- 幂等：INSERT 带 NOT EXISTS（permission 按 code；role_permission 按 (role_id, permission_id)）；重复执行无重复行。
-- 能力开关：端点本身受 bone.procurement.ai-governance.enabled=true 控制，本码仅登记权限、不打开能力。
-- 执行：mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0129_seed_procurement_ai_governance_permissions.sql
-- ============================================================

USE bone;

INSERT INTO iam_permission (id, code, name, resource_type, resource_path, action, type, sort_order, description)
SELECT * FROM (
    SELECT 940000000000000001 AS id, 'procurement:ai-governance:read' AS code, '采购-AI治理读' AS name,
           'procurement' AS resource_type, 'ai-governance' AS resource_path, 'read' AS action,
           'OPERATION' AS type, 840 AS sort_order, '能力清单/详情/裁决/追溯/override计数查看' AS description
    UNION ALL SELECT 940000000000000002, 'procurement:ai-governance:write', '采购-AI治理写',
           'procurement', 'ai-governance', 'write', 'OPERATION', 850,
           '能力登记/启用停用降级/人工终决'
) AS new_codes
WHERE NOT EXISTS (SELECT 1 FROM iam_permission p WHERE p.code = new_codes.code);

INSERT INTO iam_role_permission (id, role_id, permission_id)
SELECT * FROM (
    SELECT 940000000000000101 AS id, 1 AS role_id, 940000000000000001 AS permission_id
    UNION ALL SELECT 940000000000000102, 1, 940000000000000002
) AS new_bindings
WHERE NOT EXISTS (
    SELECT 1 FROM iam_role_permission rp
     WHERE rp.role_id = new_bindings.role_id
       AND rp.permission_id = new_bindings.permission_id
);
