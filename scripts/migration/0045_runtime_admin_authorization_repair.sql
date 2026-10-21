-- =============================================================================
-- 0045_runtime_admin_authorization_repair.sql
--
-- 目的（P1-2 实体级授权接线 · 数据侧修复，2026-10-13 实测缺口）：
--   0034_runtime_permission_grant.sql 设计了「SUPER_ADMIN 持通配 + 报销实体 + 四动作细码」
--   的授权面，但实测本库仅落地了 §3（租户管理员 9211-9215），§2（role_id=1 的
--   9201-9208 八码绑定）从未生效；且 platform.admin 只在 bone-metadata-server
--   SecurityConfig 的匿名/API-Key 主体持有，iam_permission 种子侧缺失。
--   后果（生产 security.enabled=true 时）：admin JWT 的 scopes 只含 adminFallback
--   （此前只有粗码 metadata:runtime:*），RuntimeAccessGuard 的实体级判定
--   （platform.admin / runtime:* / runtime:{entity}:*）全不命中 ⇒ 超管访问一切
--   运行时端点 403。本迁移与代码侧 DefaultPermissionCodes.adminFallback 补齐
--   （runtime:*:read/write + platform.admin）互为两侧，缺一即漂移。
--
-- 设计
-- ----------------------
-- · 为什么补 role 1 绑定而不只靠 adminFallback：adminFallback 只覆盖 isAdmin=1 的
--   账户；「超级管理员」角色分配给的其他账户（isAdmin=0）必须经角色-权限表获得同等
--   授权面，否则同角色不同命（adminFallback 注释范式：码必须在两侧齐全）。
-- · 为什么保留「租户管理员无 confirmPayout」：0034 的有意设计（按动作收回授权的
--   演示不动点），本迁移不碰 role 3，SIGNAL 判据 D 锁定该形状不被误改。
-- · E2E 残留清理：runtime:expense_reimbursement_e2e_20261012121858:confirmPayout 是 2026-10-12
--   E2E 造的临时码（绑在 role 1）。iam_permission 软删（deleted=1），
--   iam_role_permission 无 deleted 列（纯关联表）⇒ 物理删，删除前全量备份。
-- · 幂等：INSERT 均带 NOT EXISTS 守卫；E2E 清理对空结果安全；可重复执行。
--
-- 执行（L3 审批项；由 DBA / CI 在目标库运行）：
--       mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0045_runtime_admin_authorization_repair.sql
-- 前置：0033（细码/通配码已注册）、0034（设计意图与 id 段 9201-9215）。
-- 后置：admin/超管需重新登录刷新 JWT scopes（权限随登录下发，不停服即时生效）。
-- =============================================================================

USE bone;

-- ── 0. 备份（改前状态精确锚定） ─────────────────────────────────────────────
DROP TABLE IF EXISTS bak_iam_permission_0045;
CREATE TABLE bak_iam_permission_0045 AS
SELECT * FROM iam_permission
 WHERE code = 'runtime:expense_reimbursement_e2e_20261012121858:confirmPayout';

DROP TABLE IF EXISTS bak_iam_role_permission_0045;
CREATE TABLE bak_iam_role_permission_0045 AS
SELECT rp.*
  FROM iam_role_permission rp
  JOIN iam_permission p ON p.id = rp.permission_id
 WHERE rp.role_id = 1
   AND (p.code LIKE 'runtime:%' OR p.code = 'platform.admin');

-- ── 1. 注册 platform.admin（SecurityConfig 匿名/API-Key 主体早已持有，种子补齐） ──
--    id=9100：0033 段起点（9101）之前的空位；parent_id=58 与 runtime:* 同父（metadata 域根）。
INSERT INTO iam_permission
    (id, code, name, type, resource_type, resource_path, action, description, parent_id, sort_order, created_by, created_at, updated_at, deleted)
SELECT 9100, 'platform.admin', '平台管理员（数据修复通道）', 'OPERATION', 'metadata', '/api/v1/runtime/**', 'admin',
       '平台管理员：RuntimeAccessGuard 动作级判定 early-return + 行级 ACL 豁免（Runtime §4.5，强制 reason+审计）',
       58, 100, 1, NOW(3), NOW(3), 0
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM iam_permission WHERE id = 9100 OR code = 'platform.admin');

-- ── 2. 恢复 0034 §2：SUPER_ADMIN（role_id=1, tenant 0）持通配 + 实体 + 四细码 ────
--    id 段 9201-9208 与 0034 原设计一致（实测该段无占用，用原 id 保持可追溯）。
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
 WHERE NOT EXISTS (SELECT 1 FROM iam_role_permission rp WHERE rp.id = v.id)
   AND NOT EXISTS (SELECT 1 FROM iam_role_permission rp WHERE rp.role_id = 1 AND rp.permission_id = p.id);

-- ── 3. role 1 绑定 platform.admin ────────────────────────────────────────────
--    adminFallback 只覆盖 isAdmin 账户；「超级管理员」角色本身也须显式持有，
--    使角色分配的任何账户都走通 guard 的 admin early-return。
INSERT INTO iam_role_permission (id, role_id, permission_id, created_at)
SELECT 9209, 1, p.id, NOW(3)
  FROM iam_permission p
 WHERE p.code = 'platform.admin' AND p.deleted = 0
   AND NOT EXISTS (SELECT 1 FROM iam_role_permission rp WHERE rp.role_id = 1 AND rp.permission_id = p.id);

-- ── 4. E2E 残留清理（先物理删绑定，再软删权限码行） ──────────────────────────
SET @e2e_perm_id = (SELECT id FROM iam_permission
                     WHERE code = 'runtime:expense_reimbursement_e2e_20261012121858:confirmPayout' LIMIT 1);
DELETE FROM iam_role_permission WHERE permission_id = @e2e_perm_id;
UPDATE iam_permission SET deleted = 1, updated_at = NOW(3) WHERE id = @e2e_perm_id AND deleted = 0;

-- ── 5. 校验（SIGNAL 四判据；幂等重跑必须全绿） ──────────────────────────────
DELIMITER $$
CREATE PROCEDURE sp_verify_0045()
BEGIN
    DECLARE v_admin_perm INT DEFAULT 0;
    DECLARE v_role1_grants INT DEFAULT 0;
    DECLARE v_e2e_alive INT DEFAULT 0;
    DECLARE v_role3_grants INT DEFAULT 0;
    DECLARE v_role3_confirm INT DEFAULT 0;

    -- 判据 A：platform.admin 已注册且存活
    SELECT COUNT(*) INTO v_admin_perm FROM iam_permission
     WHERE code = 'platform.admin' AND deleted = 0;
    IF v_admin_perm <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0045 校验失败：platform.admin 未注册或已删（SecurityConfig 判据码必须在种子侧存在）';
    END IF;

    -- 判据 B：role 1 的 runtime 授权面 = 通配 2 + 实体 6 = 8（0034 §2 设计形状）
    SELECT COUNT(*) INTO v_role1_grants FROM iam_role_permission rp
      JOIN iam_permission p ON p.id = rp.permission_id
     WHERE rp.role_id = 1 AND p.deleted = 0 AND p.code LIKE 'runtime:%';
    IF v_role1_grants <> 8 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0045 校验失败：SUPER_ADMIN 的 runtime 绑定数不为 8（0034 §2 未完整恢复）';
    END IF;

    -- 判据 C：E2E 残留码存活数 = 0（软删 + 解绑）
    SELECT COUNT(*) INTO v_e2e_alive FROM iam_permission
     WHERE code = 'runtime:expense_reimbursement_e2e_20261012121858:confirmPayout' AND deleted = 0;
    IF v_e2e_alive <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0045 校验失败：E2E 残留码未清零（软删未生效）';
    END IF;

    -- 判据 D：role 3 绑定形状不变——5 码且不含 confirmPayout（0034 有意设计的不动点，
    --          「按动作收回授权」的演示锚点；本迁移不得顺手补发）
    SELECT COUNT(*) INTO v_role3_grants FROM iam_role_permission rp
      JOIN iam_permission p ON p.id = rp.permission_id
     WHERE rp.role_id = 3 AND p.code LIKE 'runtime:%';
    SELECT COUNT(*) INTO v_role3_confirm FROM iam_role_permission rp
      JOIN iam_permission p ON p.id = rp.permission_id
     WHERE rp.role_id = 3 AND p.code = 'runtime:expense_reimbursement:confirmPayout';
    IF v_role3_grants <> 5 OR v_role3_confirm <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0045 校验失败：租户管理员的 runtime 授权面被改动（应为 5 码且无 confirmPayout，见 0034 设计）';
    END IF;

    SELECT v_admin_perm AS platform_admin_registered,
           v_role1_grants AS super_admin_runtime_grants,
           v_e2e_alive AS e2e_leftover_alive,
           v_role3_grants AS tenant_admin_grants,
           v_role3_confirm AS tenant_admin_confirm_payout;
END$$
DELIMITER ;

CALL sp_verify_0045();
DROP PROCEDURE sp_verify_0045;
