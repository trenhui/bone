-- ============================================================
-- 0127_seed_procurement_contract_review_permissions.sql
--
-- 目的：为合同 AI 审查 4 个 HTTP 端点登记权限码（G40 命名）并绑定超管（role 1）。
--   · 写端点（POST /、POST /{reviewId}/override）：procurement:contract:review:write
--   · 读端点（GET /{reviewId}、GET /{reviewId}/disclosure）：procurement:contract:review:read
--
-- 号段：iam_permission 用 930000000000000001-002；iam_role_permission 用 930000000000000101-102。
--   与 0099 采购权限（920...001-006 / 101-106）、0100 采购菜单（920...2xx）彻底错开。
-- sort_order 820-830：接在 0099 采购交易（760-810）之后。
-- 幂等：INSERT 带 NOT EXISTS（permission 按 code；role_permission 按 (role_id, permission_id)）；重复执行无重复行。
-- 能力开关：端点本身受 bone.procurement.contract-ai-review.enabled=true 控制，本码仅登记权限、不打开能力。
-- 执行：mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0127_seed_procurement_contract_review_permissions.sql
-- ============================================================

USE bone;

INSERT INTO iam_permission (id, code, name, resource_type, resource_path, action, type, sort_order, description)
SELECT * FROM (
    SELECT 930000000000000001 AS id, 'procurement:contract:review:read' AS code, '采购-合同审查读' AS name,
           'procurement' AS resource_type, 'contract-reviews' AS resource_path, 'read' AS action,
           'OPERATION' AS type, 820 AS sort_order, '合同审查报告/披露查看' AS description
    UNION ALL SELECT 930000000000000002, 'procurement:contract:review:write', '采购-合同审查写',
           'procurement', 'contract-reviews', 'write', 'OPERATION', 830,
           '提交审查/人工推翻终决'
) AS new_codes
WHERE NOT EXISTS (SELECT 1 FROM iam_permission p WHERE p.code = new_codes.code);

INSERT INTO iam_role_permission (id, role_id, permission_id)
SELECT * FROM (
    SELECT 930000000000000101 AS id, 1 AS role_id, 930000000000000001 AS permission_id
    UNION ALL SELECT 930000000000000102, 1, 930000000000000002
) AS new_bindings
WHERE NOT EXISTS (
    SELECT 1 FROM iam_role_permission rp
     WHERE rp.role_id = new_bindings.role_id
       AND rp.permission_id = new_bindings.permission_id
);
