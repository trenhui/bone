-- ============================================================
-- 0130_seed_procurement_business_modules_permissions.sql
--
-- 目的：为 procuraAI 业务模块骨架（批次4：寻源/准入/风险绩效）登记 7 个 HTTP 权限码并绑定超管（role 1）。
--   写端点：
--     procurement:sourcing-project:write  POST   /procurement/sourcing-projects
--     procurement:rfx:approve             POST   /procurement/rfx/{rfxNo}/publish
--     procurement:qualification:write     POST   /procurement/qualifications
--     procurement:onboarding:approve      POST   /procurement/onboardings/{no}/approve
--     procurement:risk-alert:write        POST   /procurement/risk-alerts
--   读端点：
--     procurement:qualification:read      GET    /procurement/qualifications/expiring
--     procurement:performance-score:read  GET    /procurement/performance-scores
--
-- 号段：iam_permission 用 950000000000000001-007；iam_role_permission 用 950000000000000101-107。
--   与 0099 采购权限（920...）、0127 合同审查（930...）、0129 AI 治理（940...）彻底错开。
-- sort_order 860-920：接在 0129 AI 治理（840-850）之后，每码间隔 10。
-- 幂等：INSERT 带 NOT EXISTS（permission 按 code；role_permission 按 (role_id, permission_id)）。
-- AI 红线：权限只登记访问边界，不打开任何自动授标/自动授信；人工终决入口仍需具名登录态操作人。
-- 执行：mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0130_seed_procurement_business_modules_permissions.sql
-- ============================================================

USE bone;

INSERT INTO iam_permission (id, code, name, resource_type, resource_path, action, type, sort_order, description)
SELECT * FROM (
    SELECT 950000000000000001 AS id, 'procurement:sourcing-project:write' AS code, '采购-寻源项目写' AS name,
           'procurement' AS resource_type, 'sourcing-projects' AS resource_path, 'write' AS action,
           'OPERATION' AS type, 860 AS sort_order, '建寻源项目（人工拥有者，AI 不建项目）' AS description
    UNION ALL SELECT 950000000000000002, 'procurement:rfx:approve', '采购-RFx 审批',
           'procurement', 'rfx', 'approve', 'OPERATION', 870, 'RFx 发布/开标审批（截止后不可改）'
    UNION ALL SELECT 950000000000000003, 'procurement:qualification:write', '采购-证照写',
           'procurement', 'qualifications', 'write', 'OPERATION', 880, '证照提交（OCR 须人工复核才生效）'
    UNION ALL SELECT 950000000000000004, 'procurement:qualification:read', '采购-证照读',
           'procurement', 'qualifications', 'read', 'OPERATION', 890, '证照到期巡检（cursor 分页）'
    UNION ALL SELECT 950000000000000005, 'procurement:onboarding:approve', '采购-准入审批',
           'procurement', 'onboardings', 'approve', 'OPERATION', 900, '准入终决（必须具名人工，AI 评分仅建议）'
    UNION ALL SELECT 950000000000000006, 'procurement:risk-alert:write', '采购-风险预警写',
           'procurement', 'risk-alerts', 'write', 'OPERATION', 910, '预警登记（来源幂等，UNKNOWN 转人工）'
    UNION ALL SELECT 950000000000000007, 'procurement:performance-score:read', '采购-绩效读',
           'procurement', 'performance-scores', 'read', 'OPERATION', 920, '绩效只读投影（不自动授信）'
) AS new_codes
WHERE NOT EXISTS (SELECT 1 FROM iam_permission p WHERE p.code = new_codes.code);

INSERT INTO iam_role_permission (id, role_id, permission_id)
SELECT * FROM (
    SELECT 950000000000000101 AS id, 1 AS role_id, 950000000000000001 AS permission_id
    UNION ALL SELECT 950000000000000102, 1, 950000000000000002
    UNION ALL SELECT 950000000000000103, 1, 950000000000000003
    UNION ALL SELECT 950000000000000104, 1, 950000000000000004
    UNION ALL SELECT 950000000000000105, 1, 950000000000000005
    UNION ALL SELECT 950000000000000106, 1, 950000000000000006
    UNION ALL SELECT 950000000000000107, 1, 950000000000000007
) AS new_bindings
WHERE NOT EXISTS (
    SELECT 1 FROM iam_role_permission rp
     WHERE rp.role_id = new_bindings.role_id
       AND rp.permission_id = new_bindings.permission_id
);
