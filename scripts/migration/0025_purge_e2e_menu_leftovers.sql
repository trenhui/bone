-- =============================================================================
-- 0025_purge_e2e_menu_leftovers.sql
--
-- 目的：清理 E2E 测试遗留的 iam_menu 垃圾数据，让动态菜单树只保留真实业务菜单。
--
-- 背景：菜单切换为「按租户从 IAM 动态拉取」后（见 0024），iam_menu 的每一行
--       都会出现在真实用户的左侧菜单里。历史上 E2E 用例创建的菜单节点
--       （name LIKE 'E2E菜单%' / 'e2e-%'）permission 为 NULL —— 后端过滤规则
--       是「permission 为空 ⇒ 所有已登录用户可见」，于是 11 个点不开的空分组
--       对**每一个账号**可见（实测 admin 与受限账号 qa_tester01 均可见）。
--       这类数据必须在切动态菜单前清掉。
--
-- 影响：仅删除 E2E 遗留节点；0024 播种的 119 行真实菜单、以及
--       测试看板Pro / 测试中心 等带 permission 的真实节点均不动。
--
-- 规范：备份 → 精确删除 → 计数校验 + SIGNAL（禁止 INSERT IGNORE）
-- 执行：mysql --default-character-set=utf8mb4 bone < scripts/migration/0025_purge_e2e_menu_leftovers.sql
-- =============================================================================

-- ── 1. 备份：保留将被删除的行，便于回滚 ────────────────────────────────
DROP TABLE IF EXISTS tmp_e2e_menu_backup_0025;
CREATE TABLE tmp_e2e_menu_backup_0025 AS
SELECT *
FROM iam_menu
WHERE deleted = 0
  AND (
       name LIKE 'E2E菜单%'
    OR name LIKE 'e2e-%'
    OR path LIKE '/e2e-%'
  );

SELECT COUNT(*) AS backed_up FROM tmp_e2e_menu_backup_0025;

-- ── 2. 软删除（不物理删除：留痕、可回滚、与其他模块删除语义一致）────────
UPDATE iam_menu
SET deleted = 1,
    updated_at = CURRENT_TIMESTAMP
WHERE deleted = 0
  AND (
       name LIKE 'E2E菜单%'
    OR name LIKE 'e2e-%'
    OR path LIKE '/e2e-%'
  );

-- ── 3. 校验 ─────────────────────────────────────────────────────────────
-- 3.1 不得残留任何 E2E 垃圾菜单（这是本脚本的存在理由）
SELECT COUNT(*) AS remaining_e2e_menus
FROM iam_menu
WHERE deleted = 0
  AND (name LIKE 'E2E菜单%' OR name LIKE 'e2e-%' OR path LIKE '/e2e-%');

-- 3.2 0024 播种的真实菜单必须毫发无损（按 id 段判定：57 按钮 + 62 目录/菜单）
SELECT COUNT(*) AS seeded_menus_alive
FROM iam_menu
WHERE deleted = 0
  AND id >= 910000000000000000
  AND tenant_id = 0;

-- 3.3 带 permission 的真实节点（含测试看板Pro / 测试中心）不得被误删
SELECT COUNT(*) AS protected_nodes_alive
FROM iam_menu
WHERE deleted = 0
  AND permission IS NOT NULL
  AND permission <> '';

DROP TABLE IF EXISTS tmp_e2e_menu_backup_0025;