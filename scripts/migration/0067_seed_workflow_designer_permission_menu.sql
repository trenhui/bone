-- =============================================================================
-- 0067_seed_workflow_designer_permission_menu.sql
--
-- 目的（2026-10-27）：工作流设计器（v1.5 REST 层 + 前端拖拽画布）已上线代码，但
--   ① `iam_permission` 里没有任何 workflow 域权限码 ⇒ admin 回退路径与菜单过滤双缺；
--   ② `iam_menu` 里没有 `/system#/workflow-designer` 入口 ⇒ 功能对用户不可达
--      （「后端有、前端有、shell 注册有、菜单无」四缺一，与 0064 理赔菜单同型问题）。
--   本脚本一次补齐：2 枚权限码 + 超管/租户管理员绑定 + 菜单叶子 + 1 个按钮权限点。
--
-- 权限码设计
-- -----------------
-- · 为什么是 `workflow:` 而不是 `sys:`：`wf_process_def` 带 tenant_id，流程定义是
--   **租户域资产**（租户设计自己的审批流），而 `sys:*` 在既有约定里是平台域（全局表写，
--   不授租户管理员）。挂错域会让「租户管理员能设计自己的流程」这件事在码上自相矛盾。
-- · 为什么只要两枚：读（总览/草稿/版本/校验/试运行）与写（保存/丢弃/发布/上下线）。
--   后端 WorkflowDesignerAuthorizer 另暴露 PUBLISH / ENABLE 两个动作档位，宿主需要
--   职责分离（SoD：改草稿的人不该直接发上线）时在自己的实现里对这两档单独判权即可，
--   不需要为此再拆权限码（拆码会把「谁配菜单」这种运维细节绑进代码）。
--
-- 号段
-- -----------------
-- · iam_permission id 不硬编码：既有 0059 占 114-119、0057 用 9125 段手工 id、生产为雪花 id，
--   硬编码必撞。这里取 MAX(id) 顺延（0057 的同款做法，其注释已写明血泪原因）。
-- · iam_role_permission 同理，逐条 MAX(id)+1（单条 INSERT 内取一次，多条会拿到同一个值）。
-- · 菜单 id：914000000000000000 + 租户序号*10000 + 行序号（不复用 910/911/912/913）。
--
-- 幂等
-- -----------------
-- · 权限码按 code 判存在；绑定按 (role_id, permission_id) 判重；
-- · 菜单按 (tenant_id, name, path) 判存在；按钮按 (tenant_id, name) 判重。
-- · 全部可重复执行，不用 INSERT IGNORE（它会吞掉约束错误，把「撞主键」伪装成「已存在」）。
--
-- 后置：超管 / 租户管理员需**重新登录**刷新 JWT scopes（权限随登录下发，不用停服）。
--
-- 执行（本地 / 测试库直接应用；生产数据变更属 L3，由 DBA / CI 在目标库运行）：
--   mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0067_seed_workflow_designer_permission_menu.sql
-- =============================================================================

USE bone;

-- ---------- 阶段 0：备份 ----------
CREATE TABLE IF NOT EXISTS bak_iam_menu_0067 AS SELECT * FROM iam_menu;

-- ---------- 阶段 1：注册 2 枚权限码（id 顺延 MAX(id)，不硬编码） ----------
DROP TABLE IF EXISTS tmp_perm_0067;
CREATE TABLE tmp_perm_0067 (
    seq           INT          NOT NULL PRIMARY KEY,
    code          VARCHAR(200) NOT NULL,
    name          VARCHAR(200) NOT NULL,
    resource_type VARCHAR(50)  NOT NULL,
    resource_path VARCHAR(500) NOT NULL,
    action        VARCHAR(50)  NOT NULL,
    sort_order    INT          NOT NULL,
    description   VARCHAR(500) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO tmp_perm_0067
    (seq, code,                        name,                 resource_type, resource_path, action,  sort_order, description) VALUES
(   1, 'workflow:definitions:read',  '工作流-定义查看',    'workflow',    'definitions', 'read',  760,
     '定义总览/草稿/版本/校验/试运行（WorkflowDesignerController 读端点；后端由 WorkflowDesignerAuthorizer 判权）'),
(   2, 'workflow:definitions:write', '工作流-定义维护',    'workflow',    'definitions', 'write', 770,
     '保存草稿/丢弃/发布/上下线（写路径审计列 X-Operator-Id 必填；发布 fail-closed 校验）');

SET @perm_base_0067 := (SELECT COALESCE(MAX(id), 0) FROM iam_permission);

INSERT INTO iam_permission
    (id, code, name, resource_type, resource_path, action, type, sort_order, description)
SELECT
    @perm_base_0067 + s.seq, s.code, s.name, s.resource_type, s.resource_path,
    s.action, 'OPERATION', s.sort_order, s.description
FROM tmp_perm_0067 s
WHERE NOT EXISTS (SELECT 1 FROM iam_permission p WHERE p.code = s.code);

-- ---------- 阶段 2：角色绑定（超管 1 + 租户管理员 3；逐条 MAX(id)+1） ----------
INSERT INTO iam_role_permission (id, role_id, permission_id, created_at)
SELECT (SELECT COALESCE(MAX(id), 9000) + 1 FROM iam_role_permission), 1, p.id, NOW(3)
  FROM iam_permission p
 WHERE p.code = 'workflow:definitions:read' AND p.deleted = 0
   AND NOT EXISTS (SELECT 1 FROM iam_role_permission rp WHERE rp.role_id = 1 AND rp.permission_id = p.id);

INSERT INTO iam_role_permission (id, role_id, permission_id, created_at)
SELECT (SELECT COALESCE(MAX(id), 9000) + 1 FROM iam_role_permission), 1, p.id, NOW(3)
  FROM iam_permission p
 WHERE p.code = 'workflow:definitions:write' AND p.deleted = 0
   AND NOT EXISTS (SELECT 1 FROM iam_role_permission rp WHERE rp.role_id = 1 AND rp.permission_id = p.id);

INSERT INTO iam_role_permission (id, role_id, permission_id, created_at)
SELECT (SELECT COALESCE(MAX(id), 9000) + 1 FROM iam_role_permission), 3, p.id, NOW(3)
  FROM iam_permission p
 WHERE p.code = 'workflow:definitions:read' AND p.deleted = 0
   AND NOT EXISTS (SELECT 1 FROM iam_role_permission rp WHERE rp.role_id = 3 AND rp.permission_id = p.id);

INSERT INTO iam_role_permission (id, role_id, permission_id, created_at)
SELECT (SELECT COALESCE(MAX(id), 9000) + 1 FROM iam_role_permission), 3, p.id, NOW(3)
  FROM iam_permission p
 WHERE p.code = 'workflow:definitions:write' AND p.deleted = 0
   AND NOT EXISTS (SELECT 1 FROM iam_role_permission rp WHERE rp.role_id = 3 AND rp.permission_id = p.id);

-- ---------- 阶段 3：菜单种子（叶子 1 条 + 按钮 1 条） ----------
DROP TABLE IF EXISTS tmp_menu_seed_0067;
CREATE TABLE tmp_menu_seed_0067 (
    seq        INT          NOT NULL PRIMARY KEY,
    node_name  VARCHAR(200) NOT NULL,
    node_path  VARCHAR(500) NULL,
    node_icon  VARCHAR(200) NULL,
    node_order INT          NOT NULL DEFAULT 0,
    node_perm  VARCHAR(200) NULL,
    node_type  TINYINT      NOT NULL DEFAULT 1 COMMENT '1-菜单 2-按钮'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO tmp_menu_seed_0067
    (seq, node_name,        node_path,                    node_icon,             node_order, node_perm,                      node_type) VALUES
(   1, '工作流设计器',   '/system#/workflow-designer', 'ApartmentOutlined',            7, 'workflow:definitions:read',   1),
( 101, '工作流设计器-维护', NULL,                       NULL,                           1, 'workflow:definitions:write',  2);

-- 目标租户：平台租户 0 + 全部活跃租户（与 0063 / 0064 口径一致）
DROP TABLE IF EXISTS tmp_seed_tenant_0067;
CREATE TABLE tmp_seed_tenant_0067 (
    idx       INT    NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO tmp_seed_tenant_0067 (idx, tenant_id)
SELECT 0, 0
UNION ALL
SELECT ROW_NUMBER() OVER (ORDER BY id), id
  FROM iam_tenant
 WHERE deleted = 0 AND id <> 0;

-- 父节点：既有的「系统管理」目录（path IS NULL + type=0）。缺失说明 0024 未执行，
-- 此时叶子会挂不上——用孤儿校验兜住，不静默生成顶级菜单（那会让侧边栏顶格出现一个重复入口）。
DROP TABLE IF EXISTS tmp_menu_group_0067;
CREATE TABLE tmp_menu_group_0067 AS
SELECT t.idx AS idx, p.id AS parent_id
  FROM tmp_seed_tenant_0067 t
  JOIN iam_menu p
    ON p.tenant_id = t.tenant_id
   AND p.name = '系统管理' AND p.path IS NULL AND p.type = 0 AND p.deleted = 0;

-- ---------- 阶段 4：插入叶子菜单（挂「系统管理」） ----------
INSERT INTO iam_menu
    (id, tenant_id, name, parent_id, path, icon, order_no, permission, type, created_by, updated_by)
SELECT
    914000000000000000 + (t.idx * 10000) + s.seq,
    t.tenant_id, s.node_name, g.parent_id, s.node_path, s.node_icon, s.node_order,
    s.node_perm, s.node_type, NULL, NULL
FROM tmp_menu_seed_0067 s
JOIN tmp_seed_tenant_0067 t
JOIN tmp_menu_group_0067 g ON g.idx = t.idx
WHERE s.node_type = 1
  AND NOT EXISTS (
    SELECT 1 FROM iam_menu m
    WHERE m.tenant_id = t.tenant_id AND m.name = s.node_name
      AND IFNULL(m.path, '\0') = IFNULL(s.node_path, '\0') AND m.deleted = 0
);

-- ---------- 阶段 5：插入按钮权限点（先 parent NULL，再回挂） ----------
INSERT INTO iam_menu
    (id, tenant_id, name, parent_id, path, icon, order_no, permission, type, created_by, updated_by)
SELECT
    914000000000000000 + (t.idx * 10000) + s.seq,
    t.tenant_id, s.node_name, NULL, s.node_path, s.node_icon, s.node_order,
    s.node_perm, s.node_type, NULL, NULL
FROM tmp_menu_seed_0067 s
JOIN tmp_seed_tenant_0067 t
WHERE s.node_type = 2
  AND NOT EXISTS (
    SELECT 1 FROM iam_menu m
    WHERE m.tenant_id = t.tenant_id AND m.name = s.node_name AND m.deleted = 0
);

UPDATE iam_menu b
JOIN iam_menu p
  ON p.tenant_id = b.tenant_id
 AND p.deleted = 0
 AND p.name = '工作流设计器'
 AND p.path = '/system#/workflow-designer'
SET b.parent_id = p.id
WHERE b.name = '工作流设计器-维护'
  AND b.deleted = 0
  AND (b.parent_id IS NULL OR b.parent_id <> p.id);

-- ---------- 阶段 6：清理中间表 ----------
DROP TABLE IF EXISTS tmp_perm_0067;
DROP TABLE IF EXISTS tmp_menu_seed_0067;
DROP TABLE IF EXISTS tmp_seed_tenant_0067;
DROP TABLE IF EXISTS tmp_menu_group_0067;

-- ---------- 阶段 7：校验（SIGNAL 防「影响 0 行却 EXIT 0」的假成功） ----------
DROP PROCEDURE IF EXISTS sp_verify_0067;
DELIMITER $$
CREATE PROCEDURE sp_verify_0067()
BEGIN
    DECLARE v_perm    INT DEFAULT 0;
    DECLARE v_bind    INT DEFAULT 0;
    DECLARE v_tenants INT DEFAULT 0;
    DECLARE v_menu    INT DEFAULT 0;
    DECLARE v_btn     INT DEFAULT 0;
    DECLARE v_orphan  INT DEFAULT 0;
    DECLARE v_dup     INT DEFAULT 0;

    -- 1. 两枚权限码各恰好 1 条
    SELECT COUNT(*) INTO v_perm
      FROM iam_permission
     WHERE deleted = 0
       AND code IN ('workflow:definitions:read', 'workflow:definitions:write');
    IF v_perm <> 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0067 校验失败：workflow 权限码未注册满 2 条';
    END IF;

    -- 2. 绑定 = 2 码 × 2 角色（超管 1 + 租户管理员 3）
    SELECT COUNT(*) INTO v_bind
      FROM iam_role_permission rp
      JOIN iam_permission p ON p.id = rp.permission_id AND p.deleted = 0
     WHERE p.code IN ('workflow:definitions:read', 'workflow:definitions:write')
       AND rp.role_id IN (1, 3);
    IF v_bind <> 4 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0067 校验失败：超管/租户管理员绑定不足 4 条（2 码 × 2 角色）';
    END IF;

    -- 3. 播种范围 = 平台租户 0 + 全部活跃租户
    SELECT COUNT(*) INTO v_tenants
      FROM (SELECT 0 AS tid UNION ALL
            SELECT id FROM iam_tenant WHERE deleted = 0 AND id <> 0) x;

    -- 4. 叶子菜单按租户全覆盖
    SELECT COUNT(*) INTO v_menu
      FROM iam_menu
     WHERE deleted = 0 AND path = '/system#/workflow-designer';
    IF v_menu <> v_tenants THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0067 校验失败：工作流设计器菜单未按租户全覆盖';
    END IF;

    -- 5. 按钮权限点按租户全覆盖
    SELECT COUNT(*) INTO v_btn
      FROM iam_menu
     WHERE deleted = 0 AND name = '工作流设计器-维护';
    IF v_btn <> v_tenants THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0067 校验失败：工作流设计器按钮点未按租户全覆盖';
    END IF;

    -- 6. 不得有孤儿（挂不上「系统管理」说明 0024 未执行）
    SELECT COUNT(*) INTO v_orphan
      FROM iam_menu
     WHERE deleted = 0
       AND (path = '/system#/workflow-designer' OR name = '工作流设计器-维护')
       AND parent_id IS NULL;
    IF v_orphan <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0067 校验失败：菜单/按钮未挂上父节点（确认 0024 已执行，存在「系统管理」目录）';
    END IF;

    -- 7. 幂等生效：同租户下不得出现重复行
    SELECT COUNT(*) INTO v_dup
      FROM (SELECT tenant_id, name, IFNULL(path, '\0') AS p, COUNT(*) AS c
              FROM iam_menu
             WHERE deleted = 0
               AND (path = '/system#/workflow-designer' OR name = '工作流设计器-维护')
             GROUP BY tenant_id, name, p
            HAVING c > 1) d;
    IF v_dup <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0067 校验失败：存在重复菜单行（幂等判定失效）';
    END IF;

    SELECT v_perm AS perm_rows, v_bind AS binding_rows, v_tenants AS tenant_scope,
           v_menu AS menu_rows, v_btn AS button_rows;
END$$
DELIMITER ;

CALL sp_verify_0067();
DROP PROCEDURE IF EXISTS sp_verify_0067;
