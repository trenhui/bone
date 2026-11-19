-- ============================================================
-- 0128_seed_procurement_contract_review_menu.sql
--
-- 目的：给「采购」目录（0099/0100 建立）补叶子「合同审查披露」，指向
--       /procurement#/contract-review（前端披露卡片页，复用 bone-procurement-app HashRouter）。
--
-- 前置：0127_seed_procurement_contract_review_permissions.sql（读码已注册）。
-- 图标：ShoppingOutlined（已在 shell MENU_ICON_MAP 白名单，G37 不退化）。
-- 号段：9300000000000002xx + tenant_idx*100（与 0127 权限 001-002/101-102、0100 菜单 2xx 错开）。
-- 幂等：按 (tenant_id, name, path) NOT EXISTS；重复执行无重复行。
-- 校验：sp_verify_0128 防「插 0 条但 EXIT 0」假成功；无「采购」目录则报错（G32 父子一致）。
-- 执行：mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0128_seed_procurement_contract_review_menu.sql
-- ============================================================

USE bone;

DROP TABLE IF EXISTS tmp_proc_cr_menu_tenants;
CREATE TABLE tmp_proc_cr_menu_tenants (idx INT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL)
  ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
INSERT INTO tmp_proc_cr_menu_tenants (idx, tenant_id)
SELECT 0, 0 UNION ALL
SELECT ROW_NUMBER() OVER (ORDER BY id), id FROM iam_tenant WHERE deleted = 0 AND id <> 0;

INSERT INTO iam_menu (id, tenant_id, name, parent_id, path, icon, order_no, permission, type, created_by, updated_by)
SELECT 930000000000000200 + (t.idx * 100) + 1, t.tenant_id, '合同审查披露',
       (SELECT p.id FROM iam_menu p WHERE p.tenant_id = t.tenant_id AND p.name = '采购'
          AND p.path IS NULL AND p.type = 0 AND p.deleted = 0),
       '/procurement#/contract-review', 'ShoppingOutlined', 3,
       'procurement:contract:review:read', 1, NULL, NULL
FROM tmp_proc_cr_menu_tenants t
WHERE EXISTS (
    SELECT 1 FROM iam_menu p WHERE p.tenant_id = t.tenant_id AND p.name = '采购'
      AND p.path IS NULL AND p.type = 0 AND p.deleted = 0)
  AND NOT EXISTS (
    SELECT 1 FROM iam_menu m
     WHERE m.tenant_id = t.tenant_id AND m.path = '/procurement#/contract-review'
       AND m.deleted = 0);

DROP PROCEDURE IF EXISTS sp_verify_0128;
DELIMITER $$
CREATE PROCEDURE sp_verify_0128()
BEGIN
    DECLARE v_parent INT DEFAULT 0;
    DECLARE v_leaves INT DEFAULT 0;
    DECLARE v_orphan INT DEFAULT 0;
    SELECT COUNT(*) INTO v_parent FROM iam_menu
     WHERE name = '采购' AND path IS NULL AND type = 0 AND deleted = 0;
    SELECT COUNT(*) INTO v_leaves FROM iam_menu
     WHERE deleted = 0 AND path = '/procurement#/contract-review';
    SELECT COUNT(*) INTO v_orphan FROM iam_menu
     WHERE deleted = 0 AND path = '/procurement#/contract-review' AND parent_id IS NULL;
    IF v_parent = 0 OR v_leaves <> v_parent OR v_orphan <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0128 校验失败：合同审查叶子数量与采购目录不匹配或未挂父节点';
    END IF;
    SELECT v_parent AS parent_cnt, v_leaves AS leaf_cnt, v_orphan AS orphan_cnt;
END$$
DELIMITER ;
CALL sp_verify_0128();
DROP PROCEDURE IF EXISTS sp_verify_0128;

DROP TABLE IF EXISTS tmp_proc_cr_menu_tenants;
