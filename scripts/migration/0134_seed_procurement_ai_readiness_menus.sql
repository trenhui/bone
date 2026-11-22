-- ============================================================
-- 0134_seed_procurement_ai_readiness_menus.sql
--
-- 目的：给「采购」目录补三个叶子入口，指向 bone-procurement-app 的 HashRouter 页面：
--       /procurement#/ai-readiness   AI 就绪度（只读）
--       /procurement#/human-review   OCR 人工终决（具名、幂等）
--       /procurement#/governance     AI 治理状态（只读）
--
-- 前置：0133 已登记 procurement:ai-readiness:read；qualification:write 与 ai-governance:read 为既有码。
-- 图标：ShoppingOutlined（已在 shell MENU_ICON_MAP 白名单，G37 不退化）。
-- 号段：9710000000000002xx + tenant_idx*100（与 970 权限号段错开）。
-- 幂等：按 (tenant_id, path) NOT EXISTS；重复执行无重复行。
-- 边界：菜单可见只说明入口可达，不代表 AI 可用或效果达标。
-- ============================================================

USE bone;

DROP TABLE IF EXISTS tmp_proc_ai_menu_tenants;
CREATE TABLE tmp_proc_ai_menu_tenants (idx INT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL)
  ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
INSERT INTO tmp_proc_ai_menu_tenants (idx, tenant_id)
SELECT 0, 0 UNION ALL
SELECT ROW_NUMBER() OVER (ORDER BY id), id FROM iam_tenant WHERE deleted = 0 AND id <> 0;

INSERT INTO iam_menu (id, tenant_id, name, parent_id, path, icon, order_no, permission, type, created_by, updated_by)
SELECT 971000000000000200 + (t.idx * 100) + 1, t.tenant_id, 'AI 就绪度',
       (SELECT p.id FROM iam_menu p WHERE p.tenant_id = t.tenant_id AND p.name = '采购'
          AND p.path IS NULL AND p.type = 0 AND p.deleted = 0),
       '/procurement#/ai-readiness', 'ShoppingOutlined', 4,
       'procurement:ai-readiness:read', 1, NULL, NULL
FROM tmp_proc_ai_menu_tenants t
WHERE EXISTS (
    SELECT 1 FROM iam_menu p WHERE p.tenant_id = t.tenant_id AND p.name = '采购'
      AND p.path IS NULL AND p.type = 0 AND p.deleted = 0)
  AND NOT EXISTS (
    SELECT 1 FROM iam_menu m
     WHERE m.tenant_id = t.tenant_id AND m.path = '/procurement#/ai-readiness' AND m.deleted = 0);

INSERT INTO iam_menu (id, tenant_id, name, parent_id, path, icon, order_no, permission, type, created_by, updated_by)
SELECT 971000000000000200 + (t.idx * 100) + 2, t.tenant_id, 'OCR 人工终决',
       (SELECT p.id FROM iam_menu p WHERE p.tenant_id = t.tenant_id AND p.name = '采购'
          AND p.path IS NULL AND p.type = 0 AND p.deleted = 0),
       '/procurement#/human-review', 'ShoppingOutlined', 5,
       'procurement:qualification:write', 1, NULL, NULL
FROM tmp_proc_ai_menu_tenants t
WHERE EXISTS (
    SELECT 1 FROM iam_menu p WHERE p.tenant_id = t.tenant_id AND p.name = '采购'
      AND p.path IS NULL AND p.type = 0 AND p.deleted = 0)
  AND NOT EXISTS (
    SELECT 1 FROM iam_menu m
     WHERE m.tenant_id = t.tenant_id AND m.path = '/procurement#/human-review' AND m.deleted = 0);

INSERT INTO iam_menu (id, tenant_id, name, parent_id, path, icon, order_no, permission, type, created_by, updated_by)
SELECT 971000000000000200 + (t.idx * 100) + 3, t.tenant_id, 'AI 治理状态',
       (SELECT p.id FROM iam_menu p WHERE p.tenant_id = t.tenant_id AND p.name = '采购'
          AND p.path IS NULL AND p.type = 0 AND p.deleted = 0),
       '/procurement#/governance', 'ShoppingOutlined', 6,
       'procurement:ai-governance:read', 1, NULL, NULL
FROM tmp_proc_ai_menu_tenants t
WHERE EXISTS (
    SELECT 1 FROM iam_menu p WHERE p.tenant_id = t.tenant_id AND p.name = '采购'
      AND p.path IS NULL AND p.type = 0 AND p.deleted = 0)
  AND NOT EXISTS (
    SELECT 1 FROM iam_menu m
     WHERE m.tenant_id = t.tenant_id AND m.path = '/procurement#/governance' AND m.deleted = 0);

DROP PROCEDURE IF EXISTS sp_verify_0134;
DELIMITER $$
CREATE PROCEDURE sp_verify_0134()
BEGIN
    DECLARE v_parent INT DEFAULT 0;
    DECLARE v_leaves INT DEFAULT 0;
    DECLARE v_orphan INT DEFAULT 0;
    SELECT COUNT(*) INTO v_parent FROM iam_menu
     WHERE name = '采购' AND path IS NULL AND type = 0 AND deleted = 0;
    SELECT COUNT(*) INTO v_leaves FROM iam_menu
     WHERE deleted = 0 AND path IN ('/procurement#/ai-readiness', '/procurement#/human-review', '/procurement#/governance');
    SELECT COUNT(*) INTO v_orphan FROM iam_menu
     WHERE deleted = 0 AND path IN ('/procurement#/ai-readiness', '/procurement#/human-review', '/procurement#/governance')
       AND parent_id IS NULL;
    IF v_parent = 0 OR v_leaves <> v_parent * 3 OR v_orphan <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0134 校验失败：AI 入口叶子数量与采购目录不匹配或未挂父节点';
    END IF;
    SELECT v_parent AS parent_cnt, v_leaves AS leaf_cnt, v_orphan AS orphan_cnt;
END$$
DELIMITER ;
CALL sp_verify_0134();
DROP PROCEDURE IF EXISTS sp_verify_0134;

DROP TABLE IF EXISTS tmp_proc_ai_menu_tenants;
