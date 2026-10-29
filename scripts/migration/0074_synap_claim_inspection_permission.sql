-- ============================================================
-- 0074_synap_claim_inspection_permission.sql
--
-- 背景（2026-10-29 全功能实测确诊）
-- --------------------------------
-- 0059 注册了理赔 6 个权限码，其口径是当时「7 个控制器去重」。此后新增的
-- InspectionController（后督抽检，§7.6 / A9）带了自己的权限码
-- claim:inspection:write（发起/领取/结论三个写端点 @PreAuthorize），权限码却从未注册：
--
--   · 任何账号（含超管）JWT scopes 都不含该码 → 后督抽检整页 403「没有该操作权限」；
--   · 页面菜单（0071）权限挂的是 claim:claims:read，菜单可见、操作全拒——
--     「看得到、点不动」的典型种子侧缺口。
--
-- 修法与 0059 同款：注册权限码 + 绑定超管（role 1）；CLAIM_REVIEWER 等业务角色由
-- 角色管理按需授予（E2E 脚本按 claim:* 前缀自动拾取）。
--
-- 幂等：按 code / (role_id, permission_id) NOT EXISTS 守卫；末尾 SIGNAL 校验码在、绑定在。
-- 后置：需重新登录刷新 JWT scopes（权限随登录下发）。
-- ============================================================

USE bone;

-- ── 1. 注册 claim:inspection:write ─────────────────────────────────────────
INSERT INTO iam_permission (id, code, name, resource_type, resource_path, action, type, sort_order, description)
SELECT 9152001 AS id, 'claim:inspection:write' AS code, '理赔-后督抽检写' AS name, 'claim' AS resource_type,
       'review/inspections' AS resource_path, 'write' AS action, 'OPERATION' AS type, 760 AS sort_order,
       '抽检发起/领取/结论（InspectionController @PreAuthorize；结论即终态不可改写）' AS description
WHERE NOT EXISTS (SELECT 1 FROM iam_permission p WHERE p.code = 'claim:inspection:write');

-- ── 2. 超管（role 1）绑定 ──────────────────────────────────────────────────
INSERT INTO iam_role_permission (id, role_id, permission_id)
SELECT 9152002 AS id, 1 AS role_id, p.id AS permission_id
  FROM iam_permission p
 WHERE p.code = 'claim:inspection:write'
   AND NOT EXISTS (
       SELECT 1 FROM iam_role_permission rp
        WHERE rp.role_id = 1 AND rp.permission_id = p.id
   );

-- ── 3. 校验（SIGNAL 防假成功） ─────────────────────────────────────────────
DROP PROCEDURE IF EXISTS sp_verify_0074;
DELIMITER $$
CREATE PROCEDURE sp_verify_0074()
BEGIN
    DECLARE v_perm INT DEFAULT 0;
    DECLARE v_bind INT DEFAULT 0;

    SELECT COUNT(*) INTO v_perm FROM iam_permission WHERE code = 'claim:inspection:write';
    IF v_perm <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '0074 校验失败：claim:inspection:write 未注册（或重复）';
    END IF;

    SELECT COUNT(*) INTO v_bind
      FROM iam_role_permission rp
      JOIN iam_permission p ON p.id = rp.permission_id
     WHERE rp.role_id = 1 AND p.code = 'claim:inspection:write';
    IF v_bind <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '0074 校验失败：超管未绑定 claim:inspection:write';
    END IF;

    SELECT v_perm AS permission_rows, v_bind AS super_admin_bindings;
END$$
DELIMITER ;

CALL sp_verify_0074();
DROP PROCEDURE IF EXISTS sp_verify_0074;
