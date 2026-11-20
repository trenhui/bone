-- ============================================================
-- 0131_seed_procurement_ai_advisory_permissions.sql
--
-- 目的：为 procuraAI 业务 AI 建议骨架（批次5 纯工程）登记 1 个读权限码并绑定超管（role 1）。
--   端点：GET /procurement/ai-advisory/capabilities（能力清单）
--         GET /procurement/ai-advisory/capabilities/{code}/advice（建议态裁决）
--   权限：procurement:ai-advisory:read
--
-- 号段：iam_permission 用 960000000000000001；iam_role_permission 用 960000000000000101。
--   与 0099 采购（920...）、0127 合同（930...）、0129 治理（940...）、0130 业务模块（950...）错开。
-- 幂等：INSERT ... WHERE NOT EXISTS；重复执行无重复行。
-- 边界：本骨架不接真实模型、不声明效果达标；能力码/评测转正/契约冻结属 owner（D-15/D-16/D-17）。
-- ============================================================

USE bone;

INSERT INTO iam_permission (id, code, name, resource_type, resource_path, action, type, sort_order, description)
SELECT 960000000000000001 AS id, 'procurement:ai-advisory:read' AS code, '采购-AI建议读' AS name,
       'procurement' AS resource_type, 'ai-advisory' AS resource_path, 'read' AS action,
       'OPERATION' AS type, 930 AS sort_order, '业务AI建议态/披露/能力清单查看（AI仅建议，人工终决）' AS description
WHERE NOT EXISTS (SELECT 1 FROM iam_permission p WHERE p.code = 'procurement:ai-advisory:read');

INSERT INTO iam_role_permission (id, role_id, permission_id)
SELECT 960000000000000101 AS id, 1 AS role_id, 960000000000000001 AS permission_id
WHERE NOT EXISTS (
    SELECT 1 FROM iam_role_permission rp
     WHERE rp.role_id = 1 AND rp.permission_id = 960000000000000001
);
