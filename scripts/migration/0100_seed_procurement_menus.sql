-- ============================================================
-- 0100_seed_procurement_menus.sql
--
-- 目的：播种「采购」顶级菜单目录与 2 个叶子：
--   · 交易控制台 → /procurement#/transactions（bone-procurement-app activeRule=/procurement）
--   · 供应商治理 → /procurement#/suppliers（从 masterdata-app 迁入，行为契约不变）
--
-- 前置：0099_seed_procurement_permissions.sql（6 个权限码已注册）。
-- path 形态 /procurement#/<hash>：pathname 给 qiankun activeRule，hash 由子应用 HashRouter 解析。
-- 图标 ShoppingOutlined 在 shell MENU_ICON_MAP（bone-shell/src/App.tsx:294-334）白名单内。
-- 号段：9200000000000002xx（与权限 920...01xx 区分）；幂等按 (tenant_id, name, path)。
-- 目标租户：平台租户 0 + 所有未删除租户（显式 UNION，遵循 0064 范式）。
-- 执行：mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0100_seed_procurement_menus.sql
-- ============================================================

USE bone;

-- 阶段 1：模板行（目录 type=0 的 path 为 NULL；叶子 type=1）
DROP TABLE IF EXISTS tmp_proc_menu_seed;
CREATE TABLE tmp_proc_menu_seed (
    seq INT NOT NULL PRIMARY KEY,
    node_name VARCHAR(200) NOT NULL,
    node_path VARCHAR(500) NULL,
    node_icon VARCHAR(200) NULL,
    node_order INT NOT NULL,
    node_perm VARCHAR(200) NOT NULL,
    parent_seq INT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO tmp_proc_menu_seed (seq, node_name, node_path, node_icon, node_order, node_perm, parent_seq) VALUES
(1, '采购', NULL, 'ShoppingOutlined', 95, 'procurement:transaction:read', NULL),
(2, '交易控制台', '/procurement#/transactions', 'ShoppingOutlined', 1, 'procurement:transaction:read', 1),
(3, '供应商治理', '/procurement#/suppliers', 'ShoppingOutlined', 2, 'procurement:transaction:read', 1);

-- 阶段 2：目标租户
DROP TABLE IF EXISTS tmp_proc_menu_tenants;
CREATE TABLE tmp_proc_menu_tenants (idx INT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL)
  ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
INSERT INTO tmp_proc_menu_tenants (idx, tenant_id)
SELECT 0, 0 UNION ALL
SELECT ROW_NUMBER() OVER (ORDER BY id), id FROM iam_tenant WHERE deleted = 0 AND id <> 0;

-- 阶段 3：插顶级目录（幂等：租户+名称+NULL path）
INSERT INTO iam_menu (id, tenant_id, name, parent_id, path, icon, order_no, permission, type, created_by, updated_by)
SELECT 920000000000000200 + (t.idx * 100) + s.seq, t.tenant_id, s.node_name, NULL,
       s.node_path, s.node_icon, s.node_order, s.node_perm, 0, NULL, NULL
FROM tmp_proc_menu_seed s
JOIN tmp_proc_menu_tenants t
WHERE s.parent_seq IS NULL
  AND NOT EXISTS (
    SELECT 1 FROM iam_menu m
     WHERE m.tenant_id = t.tenant_id AND m.name = s.node_name
       AND m.path IS NULL AND m.type = 0 AND m.deleted = 0);

-- 阶段 4：插叶子（父节点 = 同租户的「采购」目录）
INSERT INTO iam_menu (id, tenant_id, name, parent_id, path, icon, order_no, permission, type, created_by, updated_by)
SELECT 920000000000000200 + (t.idx * 100) + s.seq, t.tenant_id, s.node_name,
       (SELECT p.id FROM iam_menu p WHERE p.tenant_id = t.tenant_id AND p.name = '采购'
          AND p.path IS NULL AND p.type = 0 AND p.deleted = 0),
       s.node_path, s.node_icon, s.node_order, s.node_perm, 1, NULL, NULL
FROM tmp_proc_menu_seed s
JOIN tmp_proc_menu_tenants t
WHERE s.parent_seq IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM iam_menu m
     WHERE m.tenant_id = t.tenant_id AND m.name = s.node_name
       AND IFNULL(m.path, '\0') = IFNULL(s.node_path, '\0') AND m.deleted = 0);

-- 阶段 5：校验（防「插 0 条但 EXIT 0」假成功）
DROP PROCEDURE IF EXISTS sp_verify_0100;
DELIMITER $$
CREATE PROCEDURE sp_verify_0100()
BEGIN
    DECLARE v_group INT DEFAULT 0;
    DECLARE v_leaves INT DEFAULT 0;
    DECLARE v_orphan INT DEFAULT 0;
    SELECT COUNT(*) INTO v_group FROM iam_menu
     WHERE name = '采购' AND path IS NULL AND type = 0 AND deleted = 0;
    SELECT COUNT(*) INTO v_leaves FROM iam_menu
     WHERE deleted = 0 AND path IN ('/procurement#/transactions', '/procurement#/suppliers');
    SELECT COUNT(*) INTO v_orphan FROM iam_menu
     WHERE deleted = 0 AND path IN ('/procurement#/transactions', '/procurement#/suppliers')
       AND parent_id IS NULL;
    IF v_group = 0 OR v_leaves <> 2 * v_group OR v_orphan <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0100 校验失败：采购目录/叶子数量不匹配或叶子未挂父节点';
    END IF;
    SELECT v_group AS groups, v_leaves AS leaves, v_orphan AS orphans;
END$$
DELIMITER ;
CALL sp_verify_0100();
DROP PROCEDURE IF EXISTS sp_verify_0100;

DROP TABLE IF EXISTS tmp_proc_menu_seed;
DROP TABLE IF EXISTS tmp_proc_menu_tenants;
