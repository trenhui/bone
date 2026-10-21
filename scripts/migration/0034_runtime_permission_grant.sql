-- =============================================================================
-- 0034_runtime_permission_grant.sql
--
-- 目的（低代码 Runtime 数据面设计 §4.6 / §4.7 · Must）：
--   0032 把报销动作的 permission_code 清一色写成旧的粗粒度 metadata:runtime:write，
--   0033 才注册了细码 runtime:expense_reimbursement:{submit|approve|reject|confirmPayout}。
--   若维持旧码，会出现两个"看起来实现了、实际是空转"的洞：
--
--   ① 后端 §4.6 逃逸动作门禁失效。RuntimeAccessGuard#requireAction 的判定是
--      「实体级 write +（动作声明了 permission_code 时必须命中该码或 runtime:{entity}:{action}）」。
--      由于全租户几乎人人都有 metadata:runtime:write，任何具备写权限的人都能触发
--      confirmPayout —— "确认打款"这一资金动作等于没有独立授权面，审核无法追溯
--      「谁被允许放款」。
--   ② 前端按钮权限失真。Describe 的 permission 原样透传给 AuthButton，
--      所有写用户都会看到「确认打款」按钮，点了才 403（如果有），
--      违反"无权限动作不返回"（§4.3 唯一形态）。
--
--   本迁移把四个动作换成细码，并把这套码显式授予角色，让
--   「细粒度 authorization」从一个码表里的字符串变成真正生效的判定。
--
-- 设计
-- ----------------------
-- · 为什么四个动作一律换细码，而不是只换逃逸动作：
--   Describe 是"一次按 status 分组算全量"（§4.3.1），按钮权限 = 动作 permission。
--   混用（部分细码、部分粗码）会让"逃逸动作需单独授权"这条规则出现例外，
--   而例外正是 §4.6 要防的东西。统一细码后，授权面 = 一张可审计的角色-权限表。
-- · 通配 runtime:*:read|write 只授予 SUPER_ADMIN：等价旧的粗粒度语义（全实体 CRUD），
--   形状统一后可被收回；租户管理员只发具体实体码，不发通配 —— 这样
--   "按实体收回"这条能力才有真实的不动点可以演示。
-- · dev 口径：bone-metadata-server 在 security.enabled=false 时走匿名主体，
--   需要 platform.admin 才能在不持有细码的情况下跑通演示（否则 requireAction
--   early-return 分支进不去，本地全链路直接 403）。生产开启鉴权后按角色码判定。
--   （对应 SecurityConfig 匿名主体 / API Key 主体补齐 platform.admin。）
-- · 幂等：UPDATE 按唯一键覆盖；INSERT 用 NOT EXISTS 守卫，可重复执行。
--
-- 执行（L3 审批项；由 DBA / CI 在目标库运行）：
--       mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0034_runtime_permission_grant.sql
-- 前置：0033_runtime_data_safety.sql（权限码已注册）、0032_runtime_action_spi.sql（动作已种）。
-- 后置：被授予者需重新登录刷新 JWT scopes（权限随登录下发，不停服即时生效）。
-- =============================================================================

USE bone;

-- ── 1. 动作 permission_code 换成细码（§4.6 门禁生效的前提） ─────────────────
UPDATE meta_entity_action_def
   SET permission_code = 'runtime:expense_reimbursement:submit',
       updated_at      = NOW(3),
       version         = version + 1
 WHERE entity_code = 'expense_reimbursement' AND action_code = 'submit' AND deleted = 0;

UPDATE meta_entity_action_def
   SET permission_code = 'runtime:expense_reimbursement:approve',
       updated_at      = NOW(3),
       version         = version + 1
 WHERE entity_code = 'expense_reimbursement' AND action_code = 'approve' AND deleted = 0;

UPDATE meta_entity_action_def
   SET permission_code = 'runtime:expense_reimbursement:reject',
       updated_at      = NOW(3),
       version         = version + 1
 WHERE entity_code = 'expense_reimbursement' AND action_code = 'reject' AND deleted = 0;

UPDATE meta_entity_action_def
   SET permission_code = 'runtime:expense_reimbursement:confirmPayout',
       updated_at      = NOW(3),
       version         = version + 1
 WHERE entity_code = 'expense_reimbursement' AND action_code = 'confirmPayout' AND deleted = 0;

-- ── 2. 授予 SUPER_ADMIN（id=1，tenant 0）：通配 + 报销实体 + 四个动作 ────────
INSERT INTO iam_role_permission (id, role_id, permission_id, created_at)
SELECT v.id, 1, p.id, NOW(3)
  FROM (SELECT 9201 AS id, 'runtime:*:read' AS code
        UNION ALL SELECT 9202, 'runtime:*:write'
        UNION ALL SELECT 9203, 'runtime:expense_reimbursement:read'
        UNION ALL SELECT 9204, 'runtime:expense_reimbursement:write'
        UNION ALL SELECT 9205, 'runtime:expense_reimbursement:submit'
        UNION ALL SELECT 9206, 'runtime:expense_reimbursement:approve'
        UNION ALL SELECT 9207, 'runtime:expense_reimbursement:reject'
        UNION ALL SELECT 9208, 'runtime:expense_reimbursement:confirmPayout') v
  JOIN iam_permission p ON p.code = v.code AND p.deleted = 0
 WHERE NOT EXISTS (SELECT 1 FROM iam_role_permission rp WHERE rp.role_id = 1 AND rp.permission_id = p.id);

-- ── 3. 授予 demo 租户管理员（id=3，tenant 1001）：仅报销实体，不给通配 ───────
--    这条是"实体级授权可按实体收回"的演示不动点：该角色只能动 expense_reimbursement。
INSERT INTO iam_role_permission (id, role_id, permission_id, created_at)
SELECT v.id, 3, p.id, NOW(3)
  FROM (SELECT 9211 AS id, 'runtime:expense_reimbursement:read' AS code
        UNION ALL SELECT 9212, 'runtime:expense_reimbursement:write'
        UNION ALL SELECT 9213, 'runtime:expense_reimbursement:submit'
        UNION ALL SELECT 9214, 'runtime:expense_reimbursement:approve'
        UNION ALL SELECT 9215, 'runtime:expense_reimbursement:reject') v
  JOIN iam_permission p ON p.code = v.code AND p.deleted = 0
 WHERE NOT EXISTS (SELECT 1 FROM iam_role_permission rp WHERE rp.role_id = 3 AND rp.permission_id = p.id);

-- ── 4. 校验 ────────────────────────────────────────────────────────────────
SELECT
  (SELECT COUNT(*) FROM meta_entity_action_def
     WHERE deleted = 0 AND entity_code = 'expense_reimbursement'
       AND permission_code LIKE 'runtime:expense_reimbursement:%')            AS fine_grained_action_count,
  (SELECT COUNT(*) FROM meta_entity_action_def
     WHERE deleted = 0 AND entity_code = 'expense_reimbursement'
       AND permission_code = 'metadata:runtime:write')                AS legacy_coarse_remaining,
  (SELECT COUNT(*) FROM iam_role_permission rp
     JOIN iam_permission p ON p.id = rp.permission_id
     WHERE rp.role_id = 1 AND p.code LIKE 'runtime:%')                AS super_admin_runtime_grants,
  (SELECT COUNT(*) FROM iam_role_permission rp
     JOIN iam_permission p ON p.id = rp.permission_id
     WHERE rp.role_id = 3 AND p.code LIKE 'runtime:%')                AS tenant_admin_runtime_grants,
  (SELECT COUNT(*) FROM iam_role_permission rp
     JOIN iam_permission p ON p.id = rp.permission_id
     WHERE rp.role_id = 3 AND p.code = 'runtime:*:write')             AS tenant_admin_wildcard_count;
