-- =============================================================================
-- 0027_menu_metadata_ia.sql
--
-- 目的（PRD v0.33 · W1b 菜单收口）：收敛「业务建模」分组的信息架构，消除自欺式死链，
--       降低新人上手门槛（详见《元数据工作区前端融合方案》§3.3.1）。
--
-- 背景（代码实测）
-- ----------------------
-- · `模型管理` 命名与实体工作区「实体」心智不一致 ⇒ 重命名为 **实体**；
-- · `关系管理` 的路由 `/metadata#/relations` 在 `bone-metadata-app/src/App.tsx:39`
--   被 `<Route ... element={<ToEntities />}>` 302 到实体列表 ⇒ 点了等于「实体」，
--   是纯噪音。软删除（路由 302 兼容保留，菜单入口移除）；
-- · `运行时数据` `/metadata#/runtime` 仍是真实路由（跨实体批量运营高频），不删，
--   但**降级 order** 到分组末位，文案声明非主路径。
--
-- 设计（仿 0025）
-- ----------------------
-- · 备份 → 精确更新/软删 → 计数校验 + SIGNAL（禁止「影响 0 行却 EXIT 0」误判成功）。
-- · **不改 0024 种子**：0024 是种子模板，本脚本操作的是它播种出来的 `iam_menu` 实时数据，
--   按 `path` 锚定（与名称无关），因此重命名/软删对任意租户都生效。
-- · 幂等：重命名/软删均为「改成目标值」，重复执行无副作用。
--
-- 执行（不在此脚本内执行，属 L3 审批项；由 DBA / CI 在目标库运行）：
--       mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0027_menu_metadata_ia.sql
-- 前置：0024_seed_iam_menu_tree.sql 已执行（iam_menu 已有业务建模分组数据）。
-- =============================================================================

USE bone;

-- ── 1. 备份：保留将被改动/软删的行，便于回滚 ────────────────────────────
CREATE TABLE IF NOT EXISTS bak_iam_menu_0027 AS
SELECT *
FROM iam_menu
WHERE deleted = 0
  AND (
        path IN ('/metadata#/entities', '/metadata#/relations', '/metadata#/runtime')
     OR name LIKE '业务建模-模型管理-%'
     OR name LIKE '业务建模-关系管理-%'
  );

SELECT COUNT(*) AS backed_up FROM bak_iam_menu_0027;

-- ── 2. 重命名：模型管理 → 实体（path 锚定，覆盖所有租户）────────────────
UPDATE iam_menu
SET name      = '实体',
    updated_at = CURRENT_TIMESTAMP(3)
WHERE deleted = 0
  AND path     = '/metadata#/entities'
  AND name     = '模型管理';

-- 同步重命名其下按钮权限点文案（业务建模-模型管理-* → 业务建模-实体-*）
UPDATE iam_menu
SET name      = REPLACE(name, '业务建模-模型管理-', '业务建模-实体-'),
    updated_at = CURRENT_TIMESTAMP(3)
WHERE deleted = 0
  AND name LIKE '业务建模-模型管理-%';

-- ── 3. 软删除：关系管理（路由 302 兼容，菜单入口移除）──────────────────
UPDATE iam_menu
SET deleted   = 1,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE deleted = 0
  AND path     = '/metadata#/relations'
  AND name     = '关系管理';

-- 同步软删其下按钮权限点（业务建模-关系管理-*）
UPDATE iam_menu
SET deleted   = 1,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE deleted = 0
  AND name LIKE '业务建模-关系管理-%';

-- ── 4. 降级：运行时数据 → order 推到末位（仍可见，非主路径）────────────
UPDATE iam_menu
SET order_no  = 99,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE deleted = 0
  AND path     = '/metadata#/runtime'
  AND name     = '运行时数据';

-- ── 5. 校验 ─────────────────────────────────────────────────────────────
DROP PROCEDURE IF EXISTS sp_verify_0027;
DELIMITER $$
CREATE PROCEDURE sp_verify_0027()
BEGIN
    DECLARE v_tenant_ent INT DEFAULT 0;   -- 拥有「实体/模型管理」菜单的租户数（重命名前后同一集合）
    DECLARE v_ent_named  INT DEFAULT 0;   -- 重命名后应为「实体」的行数
    DECLARE v_model_old  INT DEFAULT 0;   -- 残留旧名「模型管理」行数（必须 0）
    DECLARE v_ent_btn    INT DEFAULT 0;   -- 残留「业务建模-模型管理-*」按钮（必须 0）
    DECLARE v_rel_alive  INT DEFAULT 0;   -- 关系管理 仍可见行数（必须 0）
    DECLARE v_rel_btn    INT DEFAULT 0;   -- 关系管理 按钮仍可见（必须 0）
    DECLARE v_rt_low     INT DEFAULT 0;   -- 运行时数据 未降级（order<=3）行数（必须 0）
    DECLARE v_rt_count   INT DEFAULT 0;   -- 运行时数据 应存在行数（基线）

    -- 基线：原本拥有「实体/模型管理」菜单的租户数（path 唯一锚定，与名称无关）
    SELECT COUNT(DISTINCT tenant_id) INTO v_tenant_ent
      FROM iam_menu WHERE path = '/metadata#/entities' AND deleted = 0;

    SELECT COUNT(*) INTO v_ent_named
      FROM iam_menu WHERE name = '实体' AND path = '/metadata#/entities' AND deleted = 0;
    IF v_ent_named <> v_tenant_ent THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0027 校验失败：实体菜单重命名未覆盖全部租户（详见校验输出的 tenant_with_entity vs entity_renamed）';
    END IF;

    SELECT COUNT(*) INTO v_model_old
      FROM iam_menu WHERE name = '模型管理' AND path = '/metadata#/entities' AND deleted = 0;
    IF v_model_old <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0027 校验失败：仍存在未重命名的「模型管理」菜单';
    END IF;

    SELECT COUNT(*) INTO v_ent_btn
      FROM iam_menu WHERE name LIKE '业务建模-模型管理-%' AND deleted = 0;
    IF v_ent_btn <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0027 校验失败：仍存在未重命名的「业务建模-模型管理-*」按钮';
    END IF;

    SELECT COUNT(*) INTO v_rel_alive
      FROM iam_menu WHERE name = '关系管理' AND path = '/metadata#/relations' AND deleted = 0;
    IF v_rel_alive <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0027 校验失败：关系管理菜单未软删（死链菜单仍在）';
    END IF;

    SELECT COUNT(*) INTO v_rel_btn
      FROM iam_menu WHERE name LIKE '业务建模-关系管理-%' AND deleted = 0;
    IF v_rel_btn <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0027 校验失败：关系管理按钮权限点未同步软删';
    END IF;

    SELECT COUNT(DISTINCT tenant_id) INTO v_rt_count
      FROM iam_menu WHERE path = '/metadata#/runtime' AND deleted = 0;
    SELECT COUNT(*) INTO v_rt_low
      FROM iam_menu WHERE path = '/metadata#/runtime' AND deleted = 0 AND order_no <= 3;
    IF v_rt_low <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0027 校验失败：运行时数据菜单未降级（order 仍靠前）';
    END IF;

    SELECT v_tenant_ent AS tenant_with_entity,
           v_ent_named  AS entity_renamed,
           v_model_old  AS model_old_left,
           v_ent_btn    AS ent_btn_left,
           v_rel_alive  AS rel_alive,
           v_rel_btn    AS rel_btn_alive,
           v_rt_count   AS rt_total,
           v_rt_low     AS rt_low_order;
END$$
DELIMITER ;

CALL sp_verify_0027();
DROP PROCEDURE sp_verify_0027;
