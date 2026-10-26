-- ============================================================
-- 0063_seed_metadata_approval_center_menu.sql
--
-- 目的：把「审批中心」能力补进侧边栏——后端 RuntimeApprovalController（待办/已办/审批概况/
--       改派/催办）与前端 ApprovalCenterPage 均已交付，但路由与菜单双缺，功能对用户不可达。
--
-- 背景（2026-10-26 全模块前后端联调）
-- --------------------------------
-- 1) 真实浏览器点检（浏览器逐菜单巡检）发现：侧边栏遍历全部主菜单也找不到审批入口；
--    随后核对代码：ApprovalCenterPage 自 2026-10-25 提交后**从未被 App.tsx 引用**——
--    无路由、无菜单，属于「后端有、前端有、路由菜单无」的三缺一（同 0053 诊断口径的变体）。
-- 2) 本轮已同步补两处：
--    · bone-metadata-app App.tsx：挂 /approval-center 路由（HashRouter）；
--    · 本脚本：菜单树播种（path 形态 /<app>#/<hash>，与 0024 全部叶子一致）。
-- 3) 权限码全部沿用既有登记，不新造：
--    · 菜单可见性（读面）：metadata:runtime:read（与「业务数据」同级，审批是运行时读面）；
--    · 按钮权限点（写面）：metadata:runtime:write（通过/驳回/改派/催办均为运行时写，
--      RuntimeApprovalController @PreAuthorize 口径一致）。
--
-- 关键设计（沿用 0053 范式）
-- --------------------------------
-- · 父节点 =「业务建模」分组（0024 播种的 type=0 目录，path IS NULL），按租户展开 + 平台
--   租户 0（虚拟租户，须显式 UNION）；无该分组的租户内连接跳过（造孤儿属 0024 重放范畴）。
-- · 号段：912000000000000000 + 租户序号*10000 + 行序号。**不复用 0024 的 910 与 0053 的 911
--   号段**——两者各有自证计数，混插会破坏其校验。
-- · order_no=98：夹在既有叶子（模型设计 1 / 业务对象 2 / 业务数据 99）之间，审批中心排在
--   业务数据之前（先处理待办、再进数据页的动线）。
-- · 幂等：按 (tenant_id, name, path) 判存在；不用 INSERT IGNORE（吞其他约束错误，0020 教训）。
-- · 按钮节点先插 NULL 父、后置 UPDATE 回挂（同 0053 阶段 5，避免 INSERT 前 JOIN 静默 NULL）。
--
-- 前置：0024_seed_iam_menu_tree.sql 已执行（「业务建模」分组存在）。
-- 执行（本地/测试库直接应用；生产数据变更属 L3/L4，由 DBA / CI 在目标库运行）：
--       mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0063_seed_metadata_approval_center_menu.sql
-- ============================================================

USE bone;

-- ---------- 阶段 0：备份 ----------
CREATE TABLE IF NOT EXISTS bak_iam_menu_0063 AS SELECT * FROM iam_menu;

-- ---------- 阶段 1：种子行（与租户无关的模板） ----------
DROP TABLE IF EXISTS tmp_menu_seed_0063;
CREATE TABLE tmp_menu_seed_0063 (
    seq          INT          NOT NULL COMMENT '行序号，参与 id 计算',
    node_name    VARCHAR(200) NOT NULL,
    node_path    VARCHAR(500) NULL,
    node_icon    VARCHAR(200) NULL     COMMENT 'antd 图标组件名，前端按名映射',
    node_order   INT          NOT NULL DEFAULT 0,
    node_perm    VARCHAR(200) NULL     COMMENT '权限标识；NULL = 该租户所有成员可见',
    node_type    TINYINT      NOT NULL DEFAULT 1 COMMENT '0-目录 1-菜单 2-按钮',
    PRIMARY KEY (seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO tmp_menu_seed_0063
    (seq, node_name,              node_path,                       node_icon,          node_order, node_perm,               node_type) VALUES
(   1, '审批中心',                '/metadata#/approval-center',    'AuditOutlined',         98, 'metadata:runtime:read',   1),
-- type=2 按钮权限点：审批动作一个点，供「角色授权」与前端 AuthButton 门禁消费（父节点由阶段 5 回挂）
( 101, '业务建模-审批中心-审批',  NULL,                            NULL,                    1, 'metadata:runtime:write',  2);

-- ---------- 阶段 2：目标租户（平台租户 0 虚拟存在，须显式 UNION） ----------
DROP TABLE IF EXISTS tmp_seed_tenant_0063;
CREATE TABLE tmp_seed_tenant_0063 (
    idx       INT   NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO tmp_seed_tenant_0063 (idx, tenant_id)
SELECT 0, 0
UNION ALL
SELECT ROW_NUMBER() OVER (ORDER BY id), id
  FROM iam_tenant
 WHERE deleted = 0 AND id <> 0;

-- ---------- 阶段 3：解析「业务建模」分组 id（0024 播种的 type=0 目录节点，path 为 NULL） ----------
DROP TABLE IF EXISTS tmp_menu_group_0063;
CREATE TABLE tmp_menu_group_0063 AS
SELECT t.idx AS idx, p.id AS parent_id
  FROM tmp_seed_tenant_0063 t
  JOIN iam_menu p
    ON p.tenant_id = t.tenant_id
   AND p.name      = '业务建模'
   AND p.path      IS NULL
   AND p.type      = 0
   AND p.deleted   = 0;

-- ---------- 阶段 4：插入菜单/按钮节点（幂等：按 租户+名称+路径 判存在） ----------
-- 新节点 id：912000000000000000 + idx*10000 + seq。
INSERT INTO iam_menu
    (id, tenant_id, name, parent_id, path, icon, order_no, permission, type, created_by, updated_by)
SELECT
    912000000000000000 + (t.idx * 10000) + s.seq,
    t.tenant_id,
    s.node_name,
    CASE WHEN s.node_type = 1 THEN g.parent_id ELSE NULL END,
    s.node_path, s.node_icon, s.node_order, s.node_perm, s.node_type, NULL, NULL
FROM tmp_menu_seed_0063 s
JOIN tmp_seed_tenant_0063 t
JOIN tmp_menu_group_0063 g ON g.idx = t.idx
WHERE NOT EXISTS (
    SELECT 1 FROM iam_menu m
    WHERE m.tenant_id = t.tenant_id
      AND m.name      = s.node_name
      AND IFNULL(m.path, '\0') = IFNULL(s.node_path, '\0')
      AND m.deleted   = 0
);

-- ---------- 阶段 5：回挂按钮节点到同脚本插入的菜单（按 租户+名称 定位） ----------
-- 重复执行时菜单已存在（阶段 4 NOT EXISTS 跳过插入），此 UPDATE 同样生效 ⇒ 幂等。
UPDATE iam_menu b
JOIN iam_menu p
  ON p.tenant_id = b.tenant_id
 AND p.name      = '审批中心'
 AND p.deleted   = 0
SET b.parent_id = p.id
WHERE b.name    = '业务建模-审批中心-审批'
  AND b.deleted = 0
  AND (b.parent_id IS NULL OR b.parent_id <> p.id);

-- ---------- 阶段 6：清理中间表 ----------
DROP TABLE IF EXISTS tmp_menu_seed_0063;
DROP TABLE IF EXISTS tmp_seed_tenant_0063;
DROP TABLE IF EXISTS tmp_menu_group_0063;

-- ---------- 阶段 7：校验（SIGNAL 防「插 0 条但 EXIT 0」的假成功） ----------
DROP PROCEDURE IF EXISTS sp_verify_0063;
DELIMITER $$
CREATE PROCEDURE sp_verify_0063()
BEGIN
    DECLARE v_grouped INT DEFAULT 0;  -- 已有「业务建模」分组的租户数（本脚本的播种范围）
    DECLARE v_menu   INT DEFAULT 0;
    DECLARE v_btn    INT DEFAULT 0;
    DECLARE v_orphan INT DEFAULT 0;
    DECLARE v_dup    INT DEFAULT 0;

    SELECT COUNT(DISTINCT p.tenant_id) INTO v_grouped
      FROM iam_menu p
     WHERE p.name = '业务建模' AND p.path IS NULL AND p.type = 0 AND p.deleted = 0
       AND (p.tenant_id = 0
            OR EXISTS (SELECT 1 FROM iam_tenant t WHERE t.id = p.tenant_id AND t.deleted = 0));

    SELECT COUNT(*) INTO v_menu
      FROM iam_menu
     WHERE deleted = 0 AND path = '/metadata#/approval-center';
    IF v_menu <> v_grouped THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0063 校验失败：审批中心菜单未按「有业务建模分组的租户」全覆盖';
    END IF;

    SELECT COUNT(*) INTO v_btn
      FROM iam_menu
     WHERE deleted = 0 AND name = '业务建模-审批中心-审批';
    IF v_btn <> v_grouped THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0063 校验失败：按钮权限点未按「有业务建模分组的租户」全覆盖';
    END IF;

    SELECT COUNT(*) INTO v_orphan
      FROM iam_menu
     WHERE deleted = 0
       AND (path = '/metadata#/approval-center' OR name = '业务建模-审批中心-审批')
       AND parent_id IS NULL;
    IF v_orphan <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0063 校验失败：存在未挂父节点的新菜单/按钮（检查阶段 3 分组解析与阶段 5 回挂）';
    END IF;

    SELECT COUNT(*) INTO v_dup
      FROM (SELECT tenant_id, name, IFNULL(path, '\0') AS p, COUNT(*) c
              FROM iam_menu
             WHERE deleted = 0
               AND (path = '/metadata#/approval-center' OR name = '业务建模-审批中心-审批')
             GROUP BY tenant_id, name, p
            HAVING c > 1) d;
    IF v_dup <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0063 校验失败：存在重复菜单行（幂等判定失效）';
    END IF;

    SELECT v_grouped AS grouped_tenants, v_menu AS menu_rows, v_btn AS button_rows, v_orphan AS orphan_rows;
END$$
DELIMITER ;

CALL sp_verify_0063();
DROP PROCEDURE IF EXISTS sp_verify_0063;
