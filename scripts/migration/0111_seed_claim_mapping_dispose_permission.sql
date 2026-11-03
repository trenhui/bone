-- ============================================================
-- 0111_seed_claim_mapping_dispose_permission.sql
--
-- 一句话：注册处置独立权限码 claim:mapping:dispose 并绑定超管（role 1），
--        闭合「未命中队列处置」的授权缺口。
--
-- 背景（为什么必须补）
-- ----------------------------------------------------------------
-- 多保司主数据映射 task 2.3 要求处置动作（misses/{id}/resolve、misses/{id}/ignore）
-- 使用**独立于 claim:policy:write 的权限点**（处置影响映射真源，与配置写分权）。
-- CodeMappingController 两个处置端点已挂 @PreAuthorize("hasAuthority('claim:mapping:dispose')")，
-- 前端 permissions.ts 也定义了 MAPPING_DISPOSE 常量，但该码**从未在任何迁移/种子里注册**：
--   · iam_permission 无此行 ⇒ 任何角色的 scope 里都不可能出现它；
--   · 结果是处置端点对所有人 403——未命中队列只能看不能办，「处置闭环」名存实亡。
--
-- id 段口径：接续 0087 的 claim 域权限码段 9153001..9153004，本码取 9153005；
-- 角色绑定 id 与权限码 id 同值段（同 0087 口径），便于回溯。
--
-- 只绑 role 1（超管）：与 0087 的理赔域授权同口径（理赔域属平台运营面）；
-- 租户如需自行处置，由租户管理员通过 IAM 角色管理自主授码（不为演示租户预绑）。
--
-- 幂等：WHERE NOT EXISTS 防重入，可重复执行。
--
-- 执行（本地/测试库直接应用；生产数据变更属 L3，由 DBA / CI 在目标库运行）：
--   mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0111_seed_claim_mapping_dispose_permission.sql
-- ============================================================

USE bone;

-- ─────────────────────────────────────────────────────────────
-- 一、注册权限码
-- ─────────────────────────────────────────────────────────────
INSERT INTO iam_permission
    (id, code, name, resource_type, resource_path, action, type, sort_order, description)
SELECT * FROM (
    SELECT 9153005 AS id, 'claim:mapping:dispose' AS code, '理赔-映射处置' AS name, 'claim' AS resource_type,
           'mapping-miss' AS resource_path, 'dispose' AS action, 'OPERATION' AS type, 800 AS sort_order,
           '未命中队列处置（补映射/忽略；CodeMappingController 处置端点 @PreAuthorize，独立于 claim:policy:write 分权）' AS description
) AS new_code
WHERE NOT EXISTS (SELECT 1 FROM iam_permission p WHERE p.code = new_code.code);

-- ─────────────────────────────────────────────────────────────
-- 二、超管（role 1）绑定
-- ─────────────────────────────────────────────────────────────
INSERT INTO iam_role_permission (id, role_id, permission_id)
SELECT 9153005 AS id, 1 AS role_id, 9153005 AS permission_id
WHERE NOT EXISTS (
    SELECT 1 FROM iam_role_permission rp
     WHERE rp.role_id = 1 AND rp.permission_id = 9153005
);

-- ─────────────────────────────────────────────────────────────
-- 三、验收：权限码存在 + 超管已绑定，缺一即中止
-- ─────────────────────────────────────────────────────────────
DROP PROCEDURE IF EXISTS sp_verify_0111;
DELIMITER $$
CREATE PROCEDURE sp_verify_0111()
BEGIN
    DECLARE v_perm INT DEFAULT 0;
    DECLARE v_bind INT DEFAULT 0;
    SELECT COUNT(*) INTO v_perm FROM iam_permission WHERE code = 'claim:mapping:dispose';
    SELECT COUNT(*) INTO v_bind FROM iam_role_permission rp
      JOIN iam_permission p ON p.id = rp.permission_id
     WHERE rp.role_id = 1 AND p.code = 'claim:mapping:dispose';
    IF v_perm <> 1 OR v_bind <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0111 校验失败：claim:mapping:dispose 未注册或超管未绑定';
    END IF;
END$$
DELIMITER ;

CALL sp_verify_0111();
DROP PROCEDURE IF EXISTS sp_verify_0111;
