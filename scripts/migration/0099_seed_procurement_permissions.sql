-- ============================================================
-- 0099_seed_procurement_permissions.sql
--
-- 目的：为采购交易 6 个 HTTP 端点登记权限码并绑定超管（role 1）。
--   · RequisitionController 写端点：procurement:requisition:write
--   · PurchaseOrderController 写端点：procurement:purchase-order:write
--   · PurchaseOrderController 读端点：procurement:purchase-order:read
--   · 前端交易控制台与菜单：procurement:transaction:read/write
--
-- 号段：iam_permission 用 920000000000000001-006（与既有 114-119 claim、910... 大为错开，
--   不与在途 ID 冲突）；iam_role_permission 用 920000000000000101-106。
-- sort_order 760-810：接在 claim 域 700-750 之后。
-- 幂等：INSERT 带 NOT EXISTS（permission 按 code；role_permission 按 (role_id, permission_id)）。
-- 执行：mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0099_seed_procurement_permissions.sql
-- ============================================================

USE bone;

INSERT INTO iam_permission (id, code, name, resource_type, resource_path, action, type, sort_order, description)
SELECT * FROM (
    SELECT 920000000000000001 AS id, 'procurement:requisition:read' AS code, '采购-请购单读' AS name,
           'procurement' AS resource_type, 'requisitions' AS resource_path, 'read' AS action,
           'OPERATION' AS type, 760 AS sort_order, '请购单列表/详情查看' AS description
    UNION ALL SELECT 920000000000000002, 'procurement:requisition:write', '采购-请购单写',
           'procurement', 'requisitions', 'write', 'OPERATION', 770,
           '请购单创建/提交/审批/转单/取消'
    UNION ALL SELECT 920000000000000003, 'procurement:purchase-order:read', '采购-订单读',
           'procurement', 'purchase-orders', 'read', 'OPERATION', 780,
           '采购订单详情查看'
    UNION ALL SELECT 920000000000000004, 'procurement:purchase-order:write', '采购-订单写',
           'procurement', 'purchase-orders', 'write', 'OPERATION', 790,
           '采购订单创建/提交/下达'
    UNION ALL SELECT 920000000000000005, 'procurement:transaction:read', '采购-交易控制台读',
           'procurement', 'transaction-console', 'read', 'OPERATION', 800,
           '采购交易控制台菜单可见性'
    UNION ALL SELECT 920000000000000006, 'procurement:transaction:write', '采购-交易控制台写',
           'procurement', 'transaction-console', 'write', 'OPERATION', 810,
           '采购交易控制台建单/推进按钮权限'
) AS new_codes
WHERE NOT EXISTS (SELECT 1 FROM iam_permission p WHERE p.code = new_codes.code);

INSERT INTO iam_role_permission (id, role_id, permission_id)
SELECT * FROM (
    SELECT 920000000000000101 AS id, 1 AS role_id, 920000000000000001 AS permission_id
    UNION ALL SELECT 920000000000000102, 1, 920000000000000002
    UNION ALL SELECT 920000000000000103, 1, 920000000000000003
    UNION ALL SELECT 920000000000000104, 1, 920000000000000004
    UNION ALL SELECT 920000000000000105, 1, 920000000000000005
    UNION ALL SELECT 920000000000000106, 1, 920000000000000006
) AS new_bindings
WHERE NOT EXISTS (
    SELECT 1 FROM iam_role_permission rp
     WHERE rp.role_id = new_bindings.role_id
       AND rp.permission_id = new_bindings.permission_id
);
