-- ============================================================
-- 0053_seed_masterdata_standards_lineage_menu.sql
--
-- 目的：把后端已上线、前端已落地、但菜单里没有入口的两个能力补进侧边栏：
--   · 数据标准  /masterdata#/data-standards  （DataStandardController，写门禁 masterdata:standards:write）
--   · 数据血缘  /masterdata#/lineage         （LineageController，写门禁 masterdata:governance:write）
--
-- 背景（2026-10-22 前后端契约核对）
-- --------------------------------
-- 1) 诊断发现：DataStandardController / LineageController 已随 3a 设计方案交付并跑通
--    （148 个单测全绿），但 0024 播种菜单树时这两个页面还没有前端实现 ⇒ 侧边栏无入口，
--    能力对用户不可达（「后端有、前端无、菜单无」三缺一）。本次前端已补齐页面与路由
--    （bone-masterdata-app：DataStandardManagement / DataLineage + App.tsx 路由），
--    本脚本补上最后一块：DB 菜单种子。
-- 2) 权限码全部沿用既有登记，不新造：
--   · 菜单可见性用读面码——数据标准挂在 masterdata:entities:read（与字段/分类管理同级），
--     血缘挂在 masterdata:governance:write（与治理看板同级，血缘是治理面数据）；
--   · 按钮权限点用写面码——masterdata:standards:write（数据标准-维护）、
--     masterdata:governance:write（数据血缘-采集）。
--     三者均已在 bone-init.sql / DefaultPermissionCodes / bonePermissionCodes.ts 登记。
--
-- 关键设计（沿用 0024 / 0030 范式）
-- --------------------------------
-- · path 采用 `path#hash` 形态，与 0024 全部叶子一致（shell 按 # 拆分后交 qiankun 子应用）。
-- · 每个有「主数据管理」分组的租户一份：按 iam_tenant 展开 + 平台租户 0（虚拟租户，
--   表里无 id=0 行，须显式 UNION）。0024 之后新建、尚未补播种的租户没有父分组，
--   本脚本内连接分组表跳过它们——为其插菜单只会造孤儿，补齐属 0024 重放范畴。
-- · 号段：911000000000000000 + 租户序号*10000 + 行序号。**不复用 0024 的 910... 号段**——
--   0024 自带校验过程数该号段行数必须恰为 119，混插会破坏其自证。
-- · 幂等：按 (tenant_id, name, path) 判存在；不用 INSERT IGNORE（会吞掉其他约束错误，0020 教训）。
-- · 父节点定位：菜单（type=1）按 name='主数据管理' AND path IS NULL AND type=0 查 0024 播种的
--   分组；按钮（type=2）在菜单插入后按名称回挂（阶段 5）——按钮的父是同行新插入的菜单，
--   无法在同一 INSERT 前解析，故统一走后置 UPDATE，避免「 INSERT 前 JOIN 不到 ⇒ 静默 NULL 父」。
--
-- 前置：0024_seed_iam_menu_tree.sql 已执行（「主数据管理」分组存在）。
-- 执行：mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0053_seed_masterdata_standards_lineage_menu.sql
-- ============================================================

USE bone;

-- ---------- 阶段 0：备份 ----------
CREATE TABLE IF NOT EXISTS bak_iam_menu_0053 AS SELECT * FROM iam_menu;

-- ---------- 阶段 1：种子行（与租户无关的模板） ----------
DROP TABLE IF EXISTS tmp_menu_seed_0053;
CREATE TABLE tmp_menu_seed_0053 (
    seq          INT          NOT NULL COMMENT '行序号，参与 id 计算',
    node_name    VARCHAR(200) NOT NULL,
    node_path    VARCHAR(500) NULL,
    node_icon    VARCHAR(200) NULL     COMMENT 'antd 图标组件名，前端按名映射',
    node_order   INT          NOT NULL DEFAULT 0,
    node_perm    VARCHAR(200) NULL     COMMENT '权限标识；NULL = 该租户所有成员可见',
    node_type    TINYINT      NOT NULL DEFAULT 1 COMMENT '0-目录 1-菜单 2-按钮',
    PRIMARY KEY (seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 顺序号接在 0024 主数据分组既有叶子之后（域工作台=1 … 治理看板=11），取 12 / 13
INSERT INTO tmp_menu_seed_0053
    (seq, node_name,      node_path,                        node_icon,              node_order, node_perm,                    node_type) VALUES
(   1, '数据标准',        '/masterdata#/data-standards',     'ProfileOutlined',          12, 'masterdata:entities:read',    1),
(   2, '数据血缘',        '/masterdata#/lineage',             'BranchesOutlined',         13, 'masterdata:governance:write', 1),
-- type=2 按钮权限点：写操作一个点，供「角色授权」与前端 AuthButton 门禁消费（父节点由阶段 5 回挂）
( 101, '主数据管理-数据标准-维护',  NULL,                     NULL,                       1, 'masterdata:standards:write',  2),
( 102, '主数据管理-数据血缘-采集',  NULL,                     NULL,                       1, 'masterdata:governance:write', 2);

-- ---------- 阶段 2：目标租户（平台租户 0 虚拟存在，须显式 UNION） ----------
DROP TABLE IF EXISTS tmp_seed_tenant_0053;
CREATE TABLE tmp_seed_tenant_0053 (
    idx       INT   NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO tmp_seed_tenant_0053 (idx, tenant_id)
SELECT 0, 0
UNION ALL
SELECT ROW_NUMBER() OVER (ORDER BY id), id
  FROM iam_tenant
 WHERE deleted = 0 AND id <> 0;

-- ---------- 阶段 3：解析「主数据管理」分组 id（0024 播种的 type=0 目录节点，path 为 NULL） ----------
DROP TABLE IF EXISTS tmp_menu_group_0053;
CREATE TABLE tmp_menu_group_0053 AS
SELECT t.idx AS idx, p.id AS parent_id
  FROM tmp_seed_tenant_0053 t
  JOIN iam_menu p
    ON p.tenant_id = t.tenant_id
   AND p.name      = '主数据管理'
   AND p.path      IS NULL
   AND p.type      = 0
   AND p.deleted   = 0;

-- ---------- 阶段 4：插入菜单/按钮节点（幂等：按 租户+名称+路径 判存在） ----------
-- 新节点 id：911000000000000000 + idx*10000 + seq。
-- 菜单（type=1）父 = 主数据管理分组；按钮（type=2）父暂 NULL，由阶段 5 回挂到同脚本菜单。
-- ⚠ 内连接分组表：只为「已有主数据管理分组的租户」播种。0024 之后新建、尚未补播种的租户
--   没有父分组，为其插入只会制造孤儿菜单（侧边栏点不开的空项）——那类租户的菜单补齐属
--   0024 重放的范畴，不在本脚本职责内。校验基准同步取「有分组的租户数」而非全租户数。
INSERT INTO iam_menu
    (id, tenant_id, name, parent_id, path, icon, order_no, permission, type, created_by, updated_by)
SELECT
    911000000000000000 + (t.idx * 10000) + s.seq,
    t.tenant_id,
    s.node_name,
    CASE WHEN s.node_type = 1 THEN g.parent_id ELSE NULL END,
    s.node_path, s.node_icon, s.node_order, s.node_perm, s.node_type, NULL, NULL
FROM tmp_menu_seed_0053 s
JOIN tmp_seed_tenant_0053 t
JOIN tmp_menu_group_0053 g ON g.idx = t.idx
WHERE NOT EXISTS (
    SELECT 1 FROM iam_menu m
    WHERE m.tenant_id = t.tenant_id
      AND m.name      = s.node_name
      AND IFNULL(m.path, '\0') = IFNULL(s.node_path, '\0')
      AND m.deleted   = 0
);

-- ---------- 阶段 5：回挂按钮节点到同脚本插入的菜单（按 租户+名称 定位） ----------
-- 按钮无 path，按名称锚定父菜单：数据标准-维护 → 数据标准；数据血缘-采集 → 数据血缘。
-- 重复执行时菜单已存在（阶段 4 NOT EXISTS 跳过插入），此 UPDATE 同样生效 ⇒ 幂等。
UPDATE iam_menu b
JOIN iam_menu p
  ON p.tenant_id = b.tenant_id
 AND p.name      = CASE b.name WHEN '主数据管理-数据标准-维护' THEN '数据标准'
                              WHEN '主数据管理-数据血缘-采集' THEN '数据血缘' END
 AND p.deleted   = 0
SET b.parent_id = p.id
WHERE b.name IN ('主数据管理-数据标准-维护', '主数据管理-数据血缘-采集')
  AND b.deleted = 0
  AND (b.parent_id IS NULL OR b.parent_id <> p.id);

-- ---------- 阶段 6：清理中间表 ----------
DROP TABLE IF EXISTS tmp_menu_seed_0053;
DROP TABLE IF EXISTS tmp_seed_tenant_0053;
DROP TABLE IF EXISTS tmp_menu_group_0053;

-- ---------- 阶段 7：校验（SIGNAL 防「插 0 条但 EXIT 0」的假成功） ----------
DROP PROCEDURE IF EXISTS sp_verify_0053;
DELIMITER $$
CREATE PROCEDURE sp_verify_0053()
BEGIN
    DECLARE v_grouped INT DEFAULT 0;  -- 已有「主数据管理」分组的租户数（本脚本的播种范围）
    DECLARE v_menu   INT DEFAULT 0;   -- 两个新菜单在每个已播种租户各一条 ⇒ v_grouped*2
    DECLARE v_btn    INT DEFAULT 0;   -- 两个按钮节点在每个已播种租户各一条 ⇒ v_grouped*2
    DECLARE v_orphan INT DEFAULT 0;   -- 新节点未挂父的残行（必须 0）
    DECLARE v_dup    INT DEFAULT 0;   -- 同租户同名同 path 的重复行（必须 0）

    -- 播种范围 = 活跃租户的「主数据管理」分组（含平台租户 0 虚拟租户）。
    -- 软删租户（iam_tenant.deleted=1）的残留分组不在范围内——其用户已无法登录，播了也无入口。
    SELECT COUNT(DISTINCT p.tenant_id) INTO v_grouped
      FROM iam_menu p
     WHERE p.name = '主数据管理' AND p.path IS NULL AND p.type = 0 AND p.deleted = 0
       AND (p.tenant_id = 0
            OR EXISTS (SELECT 1 FROM iam_tenant t WHERE t.id = p.tenant_id AND t.deleted = 0));

    SELECT COUNT(*) INTO v_menu
      FROM iam_menu
     WHERE deleted = 0
       AND path IN ('/masterdata#/data-standards', '/masterdata#/lineage');
    IF v_menu <> v_grouped * 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0053 校验失败：新菜单未按「有分组的租户」全覆盖（数据标准/数据血缘应各一份）';
    END IF;

    SELECT COUNT(*) INTO v_btn
      FROM iam_menu
     WHERE deleted = 0
       AND name IN ('主数据管理-数据标准-维护', '主数据管理-数据血缘-采集');
    IF v_btn <> v_grouped * 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0053 校验失败：按钮权限点未按「有分组的租户」全覆盖';
    END IF;

    -- 菜单必须挂在「主数据管理」分组下、按钮必须挂在对应菜单下（parent_id 均非空）
    SELECT COUNT(*) INTO v_orphan
      FROM iam_menu
     WHERE deleted = 0
       AND (path IN ('/masterdata#/data-standards', '/masterdata#/lineage')
            OR name IN ('主数据管理-数据标准-维护', '主数据管理-数据血缘-采集'))
       AND parent_id IS NULL;
    IF v_orphan <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0053 校验失败：存在未挂父节点的新菜单/按钮（检查阶段 3 分组解析与阶段 5 回挂）';
    END IF;

    -- 幂等自证：重复执行不得产生重复行
    SELECT COUNT(*) INTO v_dup
      FROM (SELECT tenant_id, name, IFNULL(path, '\0') AS p, COUNT(*) c
              FROM iam_menu
             WHERE deleted = 0
               AND (path IN ('/masterdata#/data-standards', '/masterdata#/lineage')
                    OR name IN ('主数据管理-数据标准-维护', '主数据管理-数据血缘-采集'))
             GROUP BY tenant_id, name, p
            HAVING c > 1) d;
    IF v_dup <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0053 校验失败：存在重复菜单行（幂等判定失效）';
    END IF;

    SELECT v_grouped AS grouped_tenants, v_menu AS menu_rows, v_btn AS button_rows, v_orphan AS orphan_rows;
END$$
DELIMITER ;

CALL sp_verify_0053();
DROP PROCEDURE IF EXISTS sp_verify_0053;
