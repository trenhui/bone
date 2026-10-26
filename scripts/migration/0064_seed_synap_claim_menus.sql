-- ============================================================
-- 0064_seed_synap_claim_menus.sql
--
-- 目的：为 synap-claim 智能理赔微应用播种完整侧边栏菜单——shell 已注册
--       bone-claim-app（activeRule=/claim，entry :3013）、前端 8 个页面路由齐备、
--       后端 :8087 全链路 35/35 通过，但菜单树里 0 条理赔入口 ⇒ 功能对用户不可达
--       （「后端有、前端有、shell 注册有、菜单无」四缺一，2026-10-26 联调实测确诊）。
--
-- 菜单结构（对照 bone-claim-app 路由真源，逐条对应，不多造）：
--   智能理赔（顶级目录，order 12）
--     ├ 报案申请   /claim#/submit     ClaimSubmitPage
--     ├ 理赔进度   /claim#/progress   ClaimProgressPage
--     ├ 核赔工作台 /claim#/review     ReviewWorkbenchPage（/ 缺省落点）
--     ├ 理算归因   /claim#/adjust     AdjustmentAttributionPage
--     ├ 证据上传   /claim#/evidence   EvidenceUploadPage
--     ├ 资金工作台 /claim#/fund       FundWorkbenchPage
--     ├ 申诉处理   /claim#/appeal     AppealPage
--     └ 暂停案件   /claim#/suspend     SuspendPage
--   按钮权限点（type=2，供角色授权与前端门禁消费）：
--     智能理赔-案件-写入 claim:claims:write / 智能理赔-核赔-处理 claim:review:write /
--     智能理赔-申诉-处理 claim:appeal:write（0059 已注册并绑定超管，权限码不新造）。
--
-- 关键设计（沿用 0053 / 0063 范式）
-- --------------------------------
-- · path 形态 `/<app>#/<hash>`，与 0024 全部叶子一致；claim 的 shell activeRule 为 /claim。
-- · 菜单可见性统一 claim:claims:read（读面；理赔域唯一读码，0059 登记）。
-- · 目录是本脚本新建的顶级节点 ⇒ 阶段 2 租户展开**不依赖既有分组**（与 0053/0063 的
--   「内连接既有分组」不同：没有既有父可挂，故对全部活跃租户 + 平台租户 0 建目录）。
-- · 号段：913000000000000000 + 租户序号*10000 + 行序号；不复用 910(0024)/911(0053)/912(0063)。
-- · 幂等：按 (tenant_id, name, path) 判存在；不用 INSERT IGNORE。
-- · 按钮先插 NULL 父、后置 UPDATE 回挂（同 0053 阶段 5）。
--
-- 前置：0059_synap_claim_permissions.sql（claim:claims:read 等 6 码 + 超管绑定）。
-- 执行（本地/测试库直接应用；生产数据变更属 L3/L4，由 DBA / CI 在目标库运行）：
--       mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0064_seed_synap_claim_menus.sql
-- ============================================================

USE bone;

-- ---------- 阶段 0：备份 ----------
CREATE TABLE IF NOT EXISTS bak_iam_menu_0064 AS SELECT * FROM iam_menu;

-- ---------- 阶段 1：种子行 ----------
DROP TABLE IF EXISTS tmp_menu_seed_0064;
CREATE TABLE tmp_menu_seed_0064 (
    seq          INT          NOT NULL COMMENT '行序号，参与 id 计算',
    node_name    VARCHAR(200) NOT NULL,
    node_path    VARCHAR(500) NULL,
    node_icon    VARCHAR(200) NULL,
    node_order   INT          NOT NULL DEFAULT 0,
    node_perm    VARCHAR(200) NULL,
    node_type    TINYINT      NOT NULL DEFAULT 1 COMMENT '0-目录 1-菜单 2-按钮',
    parent_seq   INT          NULL     COMMENT '父行 seq（目录=叶子的父；按钮=所属菜单的父，本表内自洽）',
    PRIMARY KEY (seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO tmp_menu_seed_0064
    (seq, node_name,            node_path,               node_icon,                  node_order, node_perm,             node_type, parent_seq) VALUES
(   1, '智能理赔',              NULL,                    'SafetyCertificateOutlined',     12, NULL,                       0, NULL),
(  10, '报案申请',              '/claim#/submit',        'FileTextOutlined',               1, 'claim:claims:read',         1, 1),
(  11, '理赔进度',              '/claim#/progress',      'OrderedListOutlined',            2, 'claim:claims:read',         1, 1),
(  12, '核赔工作台',            '/claim#/review',        'AuditOutlined',                  3, 'claim:claims:read',         1, 1),
(  13, '理算归因',              '/claim#/adjust',        'ReconciliationOutlined',         4, 'claim:claims:read',         1, 1),
(  14, '证据上传',              '/claim#/evidence',      'CloudUploadOutlined',            5, 'claim:claims:read',         1, 1),
(  15, '资金工作台',            '/claim#/fund',          'TransactionOutlined',            6, 'claim:claims:read',         1, 1),
(  16, '申诉处理',              '/claim#/appeal',        'AlertOutlined',                  7, 'claim:claims:read',         1, 1),
(  17, '暂停案件',              '/claim#/suspend',       'ControlOutlined',                8, 'claim:claims:read',         1, 1),
( 101, '智能理赔-案件-写入',    NULL,                    NULL,                             1, 'claim:claims:write',        2, 12),
( 102, '智能理赔-核赔-处理',    NULL,                    NULL,                             1, 'claim:review:write',        2, 12),
( 103, '智能理赔-申诉-处理',    NULL,                    NULL,                             1, 'claim:appeal:write',        2, 16);

-- ---------- 阶段 2：目标租户（平台租户 0 虚拟存在，须显式 UNION；目录为本脚本新建，无需既有分组） ----------
DROP TABLE IF EXISTS tmp_seed_tenant_0064;
CREATE TABLE tmp_seed_tenant_0064 (
    idx       INT   NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO tmp_seed_tenant_0064 (idx, tenant_id)
SELECT 0, 0
UNION ALL
SELECT ROW_NUMBER() OVER (ORDER BY id), id
  FROM iam_tenant
 WHERE deleted = 0 AND id <> 0;

-- ---------- 阶段 3：插入目录（type=0，顶级，parent NULL；幂等按 租户+名称+NULL path） ----------
INSERT INTO iam_menu
    (id, tenant_id, name, parent_id, path, icon, order_no, permission, type, created_by, updated_by)
SELECT
    913000000000000000 + (t.idx * 10000) + s.seq,
    t.tenant_id, s.node_name, NULL, s.node_path, s.node_icon, s.node_order, s.node_perm, s.node_type, NULL, NULL
FROM tmp_menu_seed_0064 s
JOIN tmp_seed_tenant_0064 t
WHERE s.node_type = 0
  AND NOT EXISTS (
    SELECT 1 FROM iam_menu m
    WHERE m.tenant_id = t.tenant_id AND m.name = s.node_name
      AND IFNULL(m.path, '\0') = IFNULL(s.node_path, '\0') AND m.deleted = 0
);

-- ---------- 阶段 4：解析「智能理赔」目录 id（本脚本阶段 3 播种，跨次执行时已存在亦可命中） ----------
DROP TABLE IF EXISTS tmp_menu_group_0064;
CREATE TABLE tmp_menu_group_0064 AS
SELECT t.idx AS idx, p.id AS parent_id
  FROM tmp_seed_tenant_0064 t
  JOIN iam_menu p
    ON p.tenant_id = t.tenant_id
   AND p.name = '智能理赔' AND p.path IS NULL AND p.type = 0 AND p.deleted = 0;

-- ---------- 阶段 5：插入叶子菜单（挂目录） ----------
INSERT INTO iam_menu
    (id, tenant_id, name, parent_id, path, icon, order_no, permission, type, created_by, updated_by)
SELECT
    913000000000000000 + (t.idx * 10000) + s.seq,
    t.tenant_id, s.node_name, g.parent_id, s.node_path, s.node_icon, s.node_order, s.node_perm, s.node_type, NULL, NULL
FROM tmp_menu_seed_0064 s
JOIN tmp_seed_tenant_0064 t
JOIN tmp_menu_group_0064 g ON g.idx = t.idx
WHERE s.node_type = 1
  AND NOT EXISTS (
    SELECT 1 FROM iam_menu m
    WHERE m.tenant_id = t.tenant_id AND m.name = s.node_name
      AND IFNULL(m.path, '\0') = IFNULL(s.node_path, '\0') AND m.deleted = 0
  );

-- ---------- 阶段 6：插入按钮节点（父暂 NULL） ----------
INSERT INTO iam_menu
    (id, tenant_id, name, parent_id, path, icon, order_no, permission, type, created_by, updated_by)
SELECT
    913000000000000000 + (t.idx * 10000) + s.seq,
    t.tenant_id, s.node_name, NULL, s.node_path, s.node_icon, s.node_order, s.node_perm, s.node_type, NULL, NULL
FROM tmp_menu_seed_0064 s
JOIN tmp_seed_tenant_0064 t
WHERE s.node_type = 2
  AND NOT EXISTS (
    SELECT 1 FROM iam_menu m
    WHERE m.tenant_id = t.tenant_id AND m.name = s.node_name
      AND m.deleted = 0
  );

-- ---------- 阶段 7：回挂按钮（按 名称锚定所属菜单：核赔按钮挂核赔工作台，申诉按钮挂申诉处理） ----------
UPDATE iam_menu b
JOIN iam_menu p
  ON p.tenant_id = b.tenant_id
 AND p.deleted = 0
 AND p.name = CASE b.name WHEN '智能理赔-申诉-处理' THEN '申诉处理' ELSE '核赔工作台' END
SET b.parent_id = p.id
WHERE b.name IN ('智能理赔-案件-写入', '智能理赔-核赔-处理', '智能理赔-申诉-处理')
  AND b.deleted = 0
  AND (b.parent_id IS NULL OR b.parent_id <> p.id);

-- ---------- 阶段 8：清理 ----------
DROP TABLE IF EXISTS tmp_menu_seed_0064;
DROP TABLE IF EXISTS tmp_seed_tenant_0064;
DROP TABLE IF EXISTS tmp_menu_group_0064;

-- ---------- 阶段 9：校验（SIGNAL 防假成功） ----------
DROP PROCEDURE IF EXISTS sp_verify_0064;
DELIMITER $$
CREATE PROCEDURE sp_verify_0064()
BEGIN
    DECLARE v_dir  INT DEFAULT 0;
    DECLARE v_menu INT DEFAULT 0;
    DECLARE v_btn  INT DEFAULT 0;
    DECLARE v_orphan INT DEFAULT 0;
    DECLARE v_dup  INT DEFAULT 0;

    -- 播种范围 = 活跃租户（含平台 0）的「智能理赔」目录数（阶段 8 已删中间表，故按目录反推）
    SELECT COUNT(DISTINCT tenant_id) INTO v_dir
      FROM iam_menu WHERE name='智能理赔' AND path IS NULL AND type=0 AND deleted=0
       AND (tenant_id = 0 OR EXISTS (SELECT 1 FROM iam_tenant t WHERE t.id = iam_menu.tenant_id AND t.deleted = 0));

    SELECT COUNT(*) INTO v_menu FROM iam_menu
     WHERE deleted = 0 AND path LIKE '/claim#/%';
    IF v_menu <> v_dir * 8 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0064 校验失败：理赔叶子菜单未按租户×8 全覆盖';
    END IF;

    SELECT COUNT(*) INTO v_btn FROM iam_menu
     WHERE deleted = 0 AND name IN ('智能理赔-案件-写入','智能理赔-核赔-处理','智能理赔-申诉-处理');
    IF v_btn <> v_dir * 3 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0064 校验失败：按钮权限点未按租户×3 全覆盖';
    END IF;

    SELECT COUNT(*) INTO v_orphan FROM iam_menu
     WHERE deleted = 0
       AND (path LIKE '/claim#/%'
            OR name IN ('智能理赔-案件-写入','智能理赔-核赔-处理','智能理赔-申诉-处理'))
       AND parent_id IS NULL;
    IF v_orphan <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0064 校验失败：存在未挂父节点的理赔菜单/按钮';
    END IF;

    SELECT COUNT(*) INTO v_dup FROM (
        SELECT tenant_id, name, IFNULL(path,'\0') p, COUNT(*) c FROM iam_menu
         WHERE deleted=0 AND (path LIKE '/claim#/%'
               OR name IN ('智能理赔','智能理赔-案件-写入','智能理赔-核赔-处理','智能理赔-申诉-处理'))
         GROUP BY tenant_id, name, p HAVING c > 1) d;
    IF v_dup <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0064 校验失败：存在重复菜单行（幂等判定失效）';
    END IF;

    SELECT v_dir AS dir_tenants, v_menu AS leaf_rows, v_btn AS button_rows, v_orphan AS orphan_rows;
END$$
DELIMITER ;

CALL sp_verify_0064();
DROP PROCEDURE IF EXISTS sp_verify_0064;
