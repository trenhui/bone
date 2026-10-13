-- =============================================================================
-- 0039_ia_business_modeling_rename.sql
--
-- 目的（用户语言优先 · 业务建模子树改名）：建模工作台→模型设计、实体→业务对象、
--       运行时数据→业务数据。与前端菜单/文案改造（bone-metadata-app）配套。
--
-- 背景（代码实测）
-- ----------------------
-- · 0024 种子：「业务建模」分组下 m_meta_wb(/metadata#/apps)、m_meta_ent、m_meta_rt
--   三个子菜单；按钮权限点 4 个（新增/发布/维护/写入），父级分别是 m_meta_ent、
--   m_meta_rel（已随 0027 软删）、m_meta_rt。建模工作台无按钮子节点。
-- · 0027 已把「模型管理」重命名为「实体」并把按钮前缀改为「业务建模-实体-*」；
--   本脚本在其之上继续改名，并**兼容 0027 未执行**的环境（旧前缀一并 REPLACE）。
--
-- 设计（仿 0027，修正未提交草稿 0036 的三处缺陷）
-- ----------------------
-- ① 备份改为**精确锚定改前状态**（0036 全表 INSERT，重跑会重复插入）；
-- ② 补齐按钮权限点前缀联动（0036 只有菜单行，遗漏「业务建模-实体-新增/发布」
--    「业务建模-运行时数据-写入」，改名后权限管理页会新旧词汇混排）；
-- ③ 校验补 SIGNAL（0036 只有 SELECT，影响 0 行也 EXIT 0，违反迁移规范）。
-- · 双锚定：菜单按 parent_id（业务建模子树）+ path；按钮按 name 前缀。
--   幂等：均为「改成目标值」，重复执行 0 行命中，无副作用。
-- · 不改 0024 种子模板：本脚本操作实时 `iam_menu` 数据，改名对任意租户生效。
--
-- 执行（L3 审批项，由 DBA / CI 在目标库运行）：
--   mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0039_ia_business_modeling_rename.sql
-- 前置：0024_seed_iam_menu_tree.sql 已执行；0027 可选（已执行则按钮旧前缀为
--       「业务建模-实体-*」，未执行则为「业务建模-模型管理-*」，两者均被覆盖）。
-- =============================================================================

USE bone;

-- ── 1. 备份：仅将被改动行（菜单旓名 + 按钮旧前缀）；重跑时改前值不中，天然幂等 ──
CREATE TABLE IF NOT EXISTS bak_iam_menu_0039 AS
SELECT *
FROM iam_menu
WHERE deleted = 0
  AND (
        (parent_id IN (SELECT id FROM (SELECT id FROM iam_menu WHERE name = '业务建模') _p)
         AND name IN ('建模工作台', '实体', '模型管理', '运行时数据'))
     OR name LIKE '业务建模-模型管理-%'
     OR name LIKE '业务建模-实体-%'
     OR name LIKE '业务建模-运行时数据-%'
  );

SELECT COUNT(*) AS backed_up FROM bak_iam_menu_0039;

-- ── 2. 菜单改名（双锚定：业务建模子树 + path；派生表规避 ERROR 1093）────────
UPDATE iam_menu
SET name = '模型设计', updated_at = CURRENT_TIMESTAMP(3)
WHERE deleted = 0
  AND path = '/metadata#/apps'
  AND name = '建模工作台'
  AND parent_id IN (SELECT id FROM (SELECT id FROM iam_menu WHERE name = '业务建模') _p);

-- 兼容 0027 未执行（菜单仍叫「模型管理」）与已执行（已改名「实体」）两种前置
UPDATE iam_menu
SET name = '业务对象', updated_at = CURRENT_TIMESTAMP(3)
WHERE deleted = 0
  AND path = '/metadata#/entities'
  AND name IN ('实体', '模型管理')
  AND parent_id IN (SELECT id FROM (SELECT id FROM iam_menu WHERE name = '业务建模') _p);

UPDATE iam_menu
SET name = '业务数据', updated_at = CURRENT_TIMESTAMP(3)
WHERE deleted = 0
  AND path = '/metadata#/runtime'
  AND name = '运行时数据'
  AND parent_id IN (SELECT id FROM (SELECT id FROM iam_menu WHERE name = '业务建模') _p);

-- ── 3. 按钮权限点前缀联动（覆盖 0027 前后两种旧前缀）────────────────────────
-- 实体菜单按钮：业务建模-模型管理-* / 业务建模-实体-* → 业务建模-业务对象-*
UPDATE iam_menu
SET name = REPLACE(name, '业务建模-模型管理-', '业务建模-业务对象-'),
    updated_at = CURRENT_TIMESTAMP(3)
WHERE deleted = 0 AND name LIKE '业务建模-模型管理-%';

UPDATE iam_menu
SET name = REPLACE(name, '业务建模-实体-', '业务建模-业务对象-'),
    updated_at = CURRENT_TIMESTAMP(3)
WHERE deleted = 0 AND name LIKE '业务建模-实体-%';

-- 运行时数据按钮：业务建模-运行时数据-* → 业务建模-业务数据-*
UPDATE iam_menu
SET name = REPLACE(name, '业务建模-运行时数据-', '业务建模-业务数据-'),
    updated_at = CURRENT_TIMESTAMP(3)
WHERE deleted = 0 AND name LIKE '业务建模-运行时数据-%';

-- ── 4. 校验：计数 + SIGNAL（禁止「影响 0 行却 EXIT 0」误判成功）──────────────
DROP PROCEDURE IF EXISTS sp_verify_0039;
DELIMITER $$
CREATE PROCEDURE sp_verify_0039()
BEGIN
    DECLARE v_wb_old   INT DEFAULT 0;  -- 残留旧名「建模工作台」（必须 0）
    DECLARE v_ent_old  INT DEFAULT 0;  -- 残留旧名「实体/模型管理」（必须 0）
    DECLARE v_rt_old   INT DEFAULT 0;  -- 残留旧名「运行时数据」（必须 0）
    DECLARE v_new_cnt  INT DEFAULT 0;  -- 改名后三菜单行数（必须 = 基线）
    DECLARE v_base_cnt INT DEFAULT 0;  -- 基线：三个 path 在业务建模子树的行数
    DECLARE v_btn_old  INT DEFAULT 0;  -- 残留旧按钮前缀（必须 0）
    DECLARE v_btn_new  INT DEFAULT 0;  -- 新按钮前缀行数（ informational）

    SELECT COUNT(*) INTO v_wb_old FROM iam_menu
     WHERE deleted = 0 AND name = '建模工作台'
       AND parent_id IN (SELECT id FROM (SELECT id FROM iam_menu WHERE name = '业务建模') _p);
    IF v_wb_old <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0039 校验失败：仍存在未改名的「建模工作台」菜单';
    END IF;

    SELECT COUNT(*) INTO v_ent_old FROM iam_menu
     WHERE deleted = 0 AND name IN ('实体', '模型管理')
       AND parent_id IN (SELECT id FROM (SELECT id FROM iam_menu WHERE name = '业务建模') _p);
    IF v_ent_old <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0039 校验失败：仍存在未改名的「实体/模型管理」菜单';
    END IF;

    SELECT COUNT(*) INTO v_rt_old FROM iam_menu
     WHERE deleted = 0 AND name = '运行时数据'
       AND parent_id IN (SELECT id FROM (SELECT id FROM iam_menu WHERE name = '业务建模') _p);
    IF v_rt_old <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0039 校验失败：仍存在未改名的「运行时数据」菜单';
    END IF;

    -- 基线（path 锚定、与名称无关）与改名结果必须相等
    SELECT COUNT(*) INTO v_base_cnt FROM iam_menu
     WHERE deleted = 0
       AND path IN ('/metadata#/apps', '/metadata#/entities', '/metadata#/runtime')
       AND parent_id IN (SELECT id FROM (SELECT id FROM iam_menu WHERE name = '业务建模') _p);
    SELECT COUNT(*) INTO v_new_cnt FROM iam_menu
     WHERE deleted = 0 AND name IN ('模型设计', '业务对象', '业务数据')
       AND parent_id IN (SELECT id FROM (SELECT id FROM iam_menu WHERE name = '业务建模') _p);
    IF v_new_cnt <> v_base_cnt OR v_base_cnt = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0039 校验失败：改名后行数与基线不符（详见 renamed_vs_base），可能前置 0024 未执行或部分租户漏改';
    END IF;

    SELECT COUNT(*) INTO v_btn_old FROM iam_menu
     WHERE deleted = 0
       AND (name LIKE '业务建模-模型管理-%' OR name LIKE '业务建模-实体-%'
         OR name LIKE '业务建模-运行时数据-%');
    IF v_btn_old <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0039 校验失败：仍存在未联动的按钮权限点（旧前缀残留）';
    END IF;

    SELECT COUNT(*) INTO v_btn_new FROM iam_menu
     WHERE deleted = 0
       AND (name LIKE '业务建模-业务对象-%' OR name LIKE '业务建模-业务数据-%');

    SELECT v_new_cnt AS renamed_menus, v_base_cnt AS baseline_menus,
           v_btn_new AS renamed_buttons, v_btn_old AS old_buttons_left;
END$$
DELIMITER ;

CALL sp_verify_0039();
DROP PROCEDURE sp_verify_0039;
