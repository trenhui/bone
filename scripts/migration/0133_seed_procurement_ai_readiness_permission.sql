-- ============================================================
-- 0133_seed_procurement_ai_readiness_permission.sql
--
-- 目的：为 procuraAI 就绪度只读端点登记 1 个读权限码并绑定超管（role 1）。
--   端点：GET /api/v1/procurement/ai-readiness
--   权限：procurement:ai-readiness:read
--
-- 号段：iam_permission 用 970000000000000001；iam_role_permission 用 970000000000000101。
--   与 0099 采购（920...）、0127 合同（930...）、0129 治理（940...）、0130 业务模块（950...）、
--   0131 AI 建议（960...）错开。
-- 幂等：INSERT ... WHERE NOT EXISTS；重复执行无重复行。
-- 边界：只登记访问边界；就绪度不等于授标/准入/放款许可，AI 效果达标与评测转正仍属 owner。
-- ============================================================

USE bone;

INSERT INTO iam_permission (id, code, name, resource_type, resource_path, action, type, sort_order, description)
SELECT 970000000000000001 AS id, 'procurement:ai-readiness:read' AS code, '采购-AI就绪度读' AS name,
       'procurement' AS resource_type, 'ai-readiness' AS resource_path, 'read' AS action,
       'OPERATION' AS type, 940 AS sort_order, 'AI 就绪度只读聚合查看（工程护栏 ≠ 授标/放款许可）' AS description
WHERE NOT EXISTS (SELECT 1 FROM iam_permission p WHERE p.code = 'procurement:ai-readiness:read');

INSERT INTO iam_role_permission (id, role_id, permission_id)
SELECT 970000000000000101 AS id, 1 AS role_id, 970000000000000001 AS permission_id
WHERE NOT EXISTS (
    SELECT 1 FROM iam_role_permission rp
     WHERE rp.role_id = 1 AND rp.permission_id = 970000000000000001
);
