-- =============================================================================
-- 0030_menu_single_entry.sql
--
-- 目的：消除「同一扇门两个入口」——把元数据域的**唯一入口**收敛到「业务建模 → 建模工作台」，
--       并清偿 0024 播种时欠下的旧菜单清理（3 条 pre-0024 死链 + E2E 垃圾）。
--
-- 背景（2026-10-09 代码+DB+浏览器三重复核，非推断）
-- ----------------------------------------------------
-- 问题 P0-1「重复入口」（用户截图红框，肉眼可见）
--   `iam_menu` 里同时存在两条指向**同一个落地页**的导航项：
--     · 顶级  「元数据建模」     path=`/metadata`           id=758345112408293376
--     · 分组内「业务建模 → 建模工作台」 path=`/metadata#/apps`   id=910000000000000015
--   Playwright 实测：`goto /metadata` ⇒ location.hash 自动变成 `#/apps`，
--   页面标题「建模工作台」——**与菜单第2 项逐像素同一页**。
--   机制：shell 的 `<Route path="/metadata/*">`（bone-shell/src/App.tsx:1221）渲染 qiankun 容器，
--   metadata-app 用 HashRouter，`App.tsx` 的 `<Route path="/">`（:39）是
--   `<Navigate to="/apps" replace/>` ⇒ 无 hash 的容器根路径必然落到该微应用的默认页。
--   ⇒ 只要该微应用已有任一显式菜单入口，再挂一条「容器根路径」菜单就是纯噪音。
--
-- 问题 P0-2「死链菜单」（DB 里有、但被前端剪枝 ⇒ 用户看不见、**管理员在 IAM 菜单管理里看得见**）
--   `bone-shell/src/App.tsx:267 isKnownMenuPath()` 会剪掉不在
--   {`/`, `/profile`} ∪ MICRO_APP_ROUTE_PREFIXES 里的叶子，所以下列 3 条**当前不出现在侧边栏**：
--     · 平台控制台   `/dashboard`（id=758345112311824384）
--     · 租户运营     `/tenant`   （id=758345112353767424）
--     · 测试看板Pro  `/qa-board` （id=758633904293806080，含子按钮 `新增测试`）
--   但它们仍躺在 `iam_menu` 里被 IAM「菜单管理」页原样渲染 ⇒ 管理员看到的是
--   「4 个点不开 / 点了跳首页的菜单」，且无处删除（不敢确认是不是在用）。
--   实测：`/dashboard` `/tenant` `/qa-board` 三者浏览器落地页**全都是**首页「BONE 平台控制台」
--   （shell `<Route path="*" element={<Navigate to="/" replace/>}/>`，App.tsx:1248）。
--   其中「平台控制台」与「首页仪表盘」`/` 的权限码**完全相同**（`sys:console:read`），
--   是同一功能的两个名字。
--
-- 归属判定（为什么这 4 条是「欠的清理」而不是「在用的功能」）
-- ----------------------------------------------------
-- 这 4 条的 id 全部落在 `7583451123.../ 7586339042...` 雪花号段，而 0024 播种用的是
-- 预留号段 `910000000000000000 + 租户序号*10000 + 行序号`。
-- `0024` 自己的注释已经写明：「开发库 16 行里**只有 3 条真实菜单（平台控制台 / 租户运营 /
-- 元数据建模）**，其余全是 E2E 残留」——即 0024 的意图就是用新树**取代**这 3 条，
-- 只是当时只做了「追加播种」，没做「清理旧行」。本脚本补上这一步。
--
-- 范式（沿用 0025 / 0027）
-- ----------------------------------------------------
--   备份 → 精确软删（不物理删除：留痕可回滚）→ 计数校验 + SIGNAL
--   （禁止「影响 0 行却 EXIT 0」的假成功；禁止 INSERT IGNORE 吞掉约束错误）
--
-- 幂等：全部是「置 deleted=1」，重复执行无副作用。
--
-- 执行（属 L3 数据变更，由 DBA / CI 在目标库运行；本地联调库已执行）：
--   mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0030_menu_single_entry.sql
-- 前置：0024_seed_iam_menu_tree.sql 与 0027_menu_metadata_ia.sql 已执行。
-- 不改 0024（种子，已执行且是对任意租户展开的模板，改它等于改历史）。
-- =============================================================================

USE bone;

-- ── 1. 备份：保留将被软删的行，便于回滚 ────────────────────────────────────
CREATE TABLE IF NOT EXISTS bak_iam_menu_0030 AS
SELECT *
FROM iam_menu
WHERE deleted = 0
  AND (
        path IN ('/metadata', '/dashboard', '/tenant', '/qa-board')
     OR name IN ('测试中心', '新增测试')
     OR (name LIKE 'e2e-%' AND path IS NULL AND permission IS NULL)  -- E2E 垃圾：全员可见 + 无路由
  );

SELECT COUNT(*) AS backed_up FROM bak_iam_menu_0030;

-- ── 2. 软删 P0-1：顶级重复入口「元数据建模」────────────────────────────────
-- 按 path 锚定（与名称无关），覆盖所有租户。
-- 元数据域的唯一入口保留为「业务建模 → 建模工作台」(`/metadata#/apps`)。
UPDATE iam_menu
SET deleted    = 1,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE deleted = 0
  AND path     = '/metadata';

-- ── 3. 软删 P0-2：0024 欠下的 pre-0024 死链菜单 ───────────────────────────
-- 三条的 path 都不在 shell 的合法落点集合 {/, /profile} ∪ MICRO_APP_ROUTE_PREFIXES 内
-- （bone-shell/src/App.tsx:244-275），点击必被 `<Route path="*">` 兜底送回首页。
UPDATE iam_menu
SET deleted    = 1,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE deleted = 0
  AND path IN ('/dashboard', '/tenant', '/qa-board');

-- 连带软删「测试看板Pro」的按钮权限点子节点（type=2，无 path，按名称锚定）
UPDATE iam_menu
SET deleted    = 1,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE deleted = 0
  AND name = '新增测试'
  AND path IS NULL;

-- 连带软删空分组「测试中心」（无任何子节点留着 ⇒ 侧边栏点不开的空目录）
UPDATE iam_menu
SET deleted    = 1,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE deleted = 0
  AND name = '测试中心'
  AND path IS NULL;

-- ── 4. 软删 E2E 垃圾行（path/permission 皆空 ⇒ 后端「permission 为空即全员可见」
--      规则会让它对每一个账号可见，且无路由可落 ⇒ 0025 的清理范围漏网的那一类）──
UPDATE iam_menu
SET deleted    = 1,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE deleted = 0
  AND path IS NULL
  AND permission IS NULL
  AND type = 1;

-- ── 5. 校验 ──────────────────────────────────────────────────────────────
DROP PROCEDURE IF EXISTS sp_verify_0030;
DELIMITER $$
CREATE PROCEDURE sp_verify_0030()
BEGIN
    DECLARE v_dup_meta      INT DEFAULT 0;  -- 顶级重复入口残留（必须 0）
    DECLARE v_dead_paths    INT DEFAULT 0;  -- 死链 path 残留（必须 0）
    DECLARE v_empty_group   INT DEFAULT 0;  -- 空分组/孤儿按钮残留（必须 0）
    DECLARE v_orphan_leaf   INT DEFAULT 0;  -- path+permission 双空的导航项残留（必须 0）
    DECLARE v_meta_entry    INT DEFAULT 0;  -- 元数据域幸存入口数（必须 >=1，即建模工作台）
    DECLARE v_seeded_alive  INT DEFAULT 0;  -- 0024 播种行必须毫发无损

    -- P0-1：不得再有「容器根路径」形态的元数据菜单
    SELECT COUNT(*) INTO v_dup_meta
      FROM iam_menu WHERE deleted = 0 AND path = '/metadata';
    IF v_dup_meta <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0030 校验失败：顶级重复入口 /metadata 仍可见（元数据域必须只有一个入口）';
    END IF;

    -- P0-2：不得再有落点非法的死链菜单
    SELECT COUNT(*) INTO v_dead_paths
      FROM iam_menu
     WHERE deleted = 0
       AND path IN ('/dashboard', '/tenant', '/qa-board');
    IF v_dead_paths <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0030 校验失败：死链菜单仍可见（/dashboard /tenant /qa-board 均不在 shell 合法落点集合内）';
    END IF;

    SELECT COUNT(*) INTO v_empty_group
      FROM iam_menu
     WHERE deleted = 0
       AND path IS NULL
       AND name IN ('测试中心', '新增测试');
    IF v_empty_group <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0030 校验失败：空分组「测试中心」或其按钮「新增测试」未软删';
    END IF;

    SELECT COUNT(*) INTO v_orphan_leaf
      FROM iam_menu
     WHERE deleted = 0 AND path IS NULL AND permission IS NULL AND type = 1;
    IF v_orphan_leaf <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0030 校验失败：仍存在 path+permission 双空的导航项（全员可见且无处可去）';
    END IF;

    -- 元数据域必须还剩至少一个真实入口（软删过头也是 bug）
    SELECT COUNT(DISTINCT tenant_id) INTO v_meta_entry
      FROM iam_menu
     WHERE deleted = 0
       AND path LIKE '/metadata#/%';
    IF v_meta_entry = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0030 校验失败：软删过头——元数据域已无任何 /metadata#/* 入口';
    END IF;

    -- 0024 播种的 910... 号段必须毫发无损（本次只软删 pre-0024 号段 + 无主行）
    SELECT COUNT(*) INTO v_seeded_alive
      FROM iam_menu
     WHERE deleted = 0
       AND id >= 910000000000000000
       AND id <  910000000000000000 + 900000000000000000;
    IF v_seeded_alive = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0030 校验失败：0024 播种行被误伤';
    END IF;

    SELECT v_dup_meta    AS dup_meta_left,
           v_dead_paths  AS dead_path_left,
           v_empty_group AS empty_group_left,
           v_orphan_leaf AS orphan_leaf_left,
           v_meta_entry  AS metadata_tenants_with_entry,
           v_seeded_alive AS seeded_rows_alive;
END$$
DELIMITER ;

CALL sp_verify_0030();
DROP PROCEDURE sp_verify_0030;