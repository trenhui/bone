-- ============================================================
-- 0101_seed_procurement_fulfillment_permissions.sql
--
-- 目的：为采购履约 6 个 HTTP 端点登记 2 个权限码并绑定超管（role 1）：
--   · procurement:fulfillment:read   —— 履约视图 / 例外列表
--   · procurement:fulfillment:write  —— 确认 / ASN / 开票 / 补料
--
-- 号段：iam_permission 用 920000000000000007-008；role_permission 用 920000000000000107-108
--   （接在 0099 的 001-006 / 101-106 之后，不冲突）。
-- 幂等：INSERT ... WHERE NOT EXISTS（permission 按 code；role_permission 按 (role_id,permission_id)）。
-- 执行：mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0101_seed_procurement_fulfillment_permissions.sql
-- ============================================================

USE bone;

INSERT INTO iam_permission (id, code, name, resource_type, resource_path, action, type, sort_order, description)
SELECT * FROM (
    SELECT 920000000000000007 AS id, 'procurement:fulfillment:read' AS code, '采购-履约读' AS name,
           'procurement' AS resource_type, 'fulfillment' AS resource_path, 'read' AS action,
           'OPERATION' AS type, 820 AS sort_order, '履约视图/例外列表查看' AS description
    UNION ALL SELECT 920000000000000008, 'procurement:fulfillment:write', '采购-履约写',
           'procurement', 'fulfillment', 'write', 'OPERATION', 830,
           'PO确认/ASN发货/自助开票/例外补料'
) AS new_codes
WHERE NOT EXISTS (SELECT 1 FROM iam_permission p WHERE p.code = new_codes.code);

INSERT INTO iam_role_permission (id, role_id, permission_id)
SELECT * FROM (
    SELECT 920000000000000107 AS id, 1 AS role_id, 920000000000000007 AS permission_id
    UNION ALL SELECT 920000000000000108, 1, 920000000000000008
) AS new_bindings
WHERE NOT EXISTS (
    SELECT 1 FROM iam_role_permission rp
     WHERE rp.role_id = new_bindings.role_id
       AND rp.permission_id = new_bindings.permission_id
);
