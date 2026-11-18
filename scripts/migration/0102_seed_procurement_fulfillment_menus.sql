-- ============================================================
-- 0102_seed_procurement_fulfillment_menus.sql
--
-- 目的：在既有「采购」目录下新增 1 个叶子：
--   · 履约协同 → /procurement#/fulfillment（bone-procurement-app activeRule=/procurement）
--
-- 前置：0099（6 个交易权限码）+ 0100（采购目录与 2 个叶子）+ 0101（2 个履约权限码）。
-- 图标 ShoppingOutlined 在 shell MENU_ICON_MAP 白名单内。
-- 号段：9200000000000004xx（0100 已占用 2xx 与 3xx，其中 3xx 的 301-303 为
--   「采购/交易控制台/供应商治理」；本脚本用 4xx 起，403 起）；幂等按 (tenant_id,name,path)。
-- 别名：校验过程用 v_group/v_leaf/v_orphan，避开 MySQL 8 保留字（groups/leaves 等）。
-- 执行：mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0102_seed_procurement_fulfillment_menus.sql
-- ============================================================

USE bone;

DROP TABLE IF EXISTS tmp_proc_fulfillment_menu_tenants;
CREATE TABLE tmp_proc_fulfillment_menu_tenants (idx INT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL)
  ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
INSERT INTO tmp_proc_fulfillment_menu_tenants (idx, tenant_id)
SELECT 0, 0 UNION ALL
SELECT ROW_NUMBER() OVER (ORDER BY id), id FROM iam_tenant WHERE deleted = 0 AND id <> 0;

INSERT INTO iam_menu (id, tenant_id, name, parent_id, path, icon, order_no, permission, type, created_by, updated_by)
SELECT 920000000000000400 + (t.idx * 100) + 3, t.tenant_id, '履约协同',
       (SELECT p.id FROM iam_menu p WHERE p.tenant_id = t.tenant_id AND p.name = '采购'
          AND p.path IS NULL AND p.type = 0 AND p.deleted = 0),
       '/procurement#/fulfillment', 'ShoppingOutlined', 3, 'procurement:fulfillment:read', 1, NULL, NULL
FROM tmp_proc_fulfillment_menu_tenants t
WHERE NOT EXISTS (
    SELECT 1 FROM iam_menu m
     WHERE m.tenant_id = t.tenant_id AND m.name = '履约协同'
       AND m.path = '/procurement#/fulfillment' AND m.deleted = 0);

DROP PROCEDURE IF EXISTS sp_verify_0102;
DELIMITER $$
CREATE PROCEDURE sp_verify_0102()
BEGIN
    DECLARE v_group INT DEFAULT 0;
    DECLARE v_leaf INT DEFAULT 0;
    DECLARE v_orphan INT DEFAULT 0;
    SELECT COUNT(*) INTO v_group FROM iam_menu
     WHERE name = '采购' AND path IS NULL AND type = 0 AND deleted = 0;
    SELECT COUNT(*) INTO v_leaf FROM iam_menu
     WHERE deleted = 0 AND path = '/procurement#/fulfillment' AND permission = 'procurement:fulfillment:read';
    SELECT COUNT(*) INTO v_orphan FROM iam_menu
     WHERE deleted = 0 AND path = '/procurement#/fulfillment' AND parent_id IS NULL;
    IF v_group = 0 OR v_leaf <> v_group OR v_orphan <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0102 校验失败：履约协同叶子数量不匹配或未挂采购目录';
    END IF;
    SELECT v_group AS group_cnt, v_leaf AS leaf_cnt, v_orphan AS orphan_cnt;
END$$
DELIMITER ;
CALL sp_verify_0102();
DROP PROCEDURE IF EXISTS sp_verify_0102;

DROP TABLE IF EXISTS tmp_proc_fulfillment_menu_tenants;
