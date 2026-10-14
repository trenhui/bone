-- =============================================================================
-- 0044_ia_business_modeling_button_prefix.sql
--
-- 目的（用户语言优先 · 业务建模子树按钮权限点前缀补刀）：把残留旧前缀的按钮权限点
--       联动到 0039 改名后的菜单词汇 ——
--         · 业务建模-实体-*        → 业务建模-业务对象-*
--         · 业务建模-模型管理-*     → 业务建模-业务对象-*   （兼容 0027 未执行的环境）
--         · 业务建模-运行时数据-*   → 业务建模-业务数据-*
--
-- 背景（代码/数据库实测，2026-10-13）
-- ----------------------
-- · 0039 的**菜单改名段已生效**（iam_menu 实测：建模工作台→模型设计、实体→业务对象、
--   运行时数据→业务数据，tenant 0/1001/... 均已改名），但其**按钮前缀联动段未落地**：
--   tenant 0 实测三个存活按钮仍叫旧前缀 ——
--     id=...118  业务建模-实体-新增        (permission=metadata:model:write, parent=业务对象)
--     id=...119  业务建模-实体-发布        (permission=metadata:publish,     parent=业务对象)
--     id=...121  业务建模-运行时数据-写入  (permission=metadata:runtime:write, parent=业务数据)
--   ⇒ 权限管理页「菜单已叫业务对象、按钮还叫实体」，新旧词汇混排，授权时看不清归属。
-- · 0039 自带校验（sp_verify_0039 的 v_btn_old<>0 → SIGNAL）本应拦住此状态 —— 它没被拦住，
--   说明 0039 要么只跑了菜单段，要么整体未跑而菜单系人工改名。无论哪种，当前库态即漂移事实，
--   本脚本以**改前状态精确锚定**修复，不依赖对 0039 执行史的推断。
-- · 「业务建模-关系管理-维护」（parent=已软删的「关系管理」菜单）自身 deleted=1，已随 0027 退役，
--   **不在本脚本范围**（改一个不可见的退役按钮只会增加无意义 churn）。
--
-- 设计（仿 0039，三处关键取舍）
-- ----------------------
-- ① 备份仅锚定将被改动行（旧前缀按钮）；重跑时改前值不中，天然幂等；
-- ② 三条 REPLACE 覆盖 0027 已执行（实体-*）/ 未执行（模型管理-*）两种前置，与 0039 同口径；
-- ③ 校验双判据 —— 「旧前缀存活行数 = 0」+ 「新前缀按钮按名精确存在且存活」，
--    只用 LIKE 计数会被「0 行旧前缀但也没改成新前缀」的静默零效果蒙混；
--    另加菜单名防线（菜单若被回退成旧名，按钮联动即失去锚点，必须显式失败）。
--
-- 执行（L3 审批项，由 DBA / CI 在目标库运行）：
--   mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0044_ia_business_modeling_button_prefix.sql
-- 前置：0024 种子已执行；0039 菜单改名段已生效（本脚本校验段会验证这一点）。
-- =============================================================================

USE bone;

-- ── 1. 备份：仅将被改动行（旧前缀按钮，含已软删行——REPLACE 不判 deleted， 备份须与改动面一致才能回滚） ──
CREATE TABLE IF NOT EXISTS bak_iam_menu_0044 AS
SELECT *
FROM iam_menu
WHERE name LIKE '业务建模-模型管理-%'
   OR name LIKE '业务建模-实体-%'
   OR name LIKE '业务建模-运行时数据-%';

SELECT COUNT(*) AS backed_up FROM bak_iam_menu_0044;

-- ── 2. 按钮前缀联动（与 0039 同口径；兼容 0027 未执行的环境）────────────────────
-- 实体菜单按钮：业务建模-模型管理-* / 业务建模-实体-* → 业务建模-业务对象-*
UPDATE iam_menu
SET name = REPLACE(name, '业务建模-模型管理-', '业务建模-业务对象-'),
    updated_at = CURRENT_TIMESTAMP(3)
WHERE name LIKE '业务建模-模型管理-%';

UPDATE iam_menu
SET name = REPLACE(name, '业务建模-实体-', '业务建模-业务对象-'),
    updated_at = CURRENT_TIMESTAMP(3)
WHERE name LIKE '业务建模-实体-%';

-- 运行时数据按钮：业务建模-运行时数据-* → 业务建模-业务数据-*
UPDATE iam_menu
SET name = REPLACE(name, '业务建模-运行时数据-', '业务建模-业务数据-'),
    updated_at = CURRENT_TIMESTAMP(3)
WHERE name LIKE '业务建模-运行时数据-%';

-- ── 3. 校验：旧前缀清零 + 新按钮按名存在 + 菜单名防线（禁「影响 0 行却 EXIT 0」） ──
DROP PROCEDURE IF EXISTS sp_verify_0044;
DELIMITER $$
CREATE PROCEDURE sp_verify_0044()
BEGIN
    DECLARE v_btn_old   INT DEFAULT 0;  -- 残留旧前缀按钮（必须 0）
    DECLARE v_new_cnt   INT DEFAULT 0;  -- 新前缀存活按钮数（必须 = 3 × 租户数）
    DECLARE v_tenant    INT DEFAULT 0;  -- 持有业务建模子菜单的租户数（基线，禁硬编码 3）
    DECLARE v_menu_old  INT DEFAULT 0;  -- 菜单被回退成旧名的行数（必须 0）
    DECLARE v_menu_new  INT DEFAULT 0;  -- 改名后三菜单行数（必须 = 3 个 path 的基线）
    DECLARE v_base_cnt  INT DEFAULT 0;  -- 基线：三个 path 的行数（与名称无关）

    -- 租户基线：以「业务建模子树三个 path 的存活行」推导（菜单种子按租户各一份，
    --    2026-10-13 实测 12 个租户；写死 3 会把多租户环境判成失败——0044 首跑即踩此坑）
    SELECT COUNT(DISTINCT tenant_id) INTO v_tenant
      FROM iam_menu
     WHERE deleted = 0
       AND path IN ('/metadata#/apps', '/metadata#/entities', '/metadata#/runtime');

    -- 判据 A：任何旧前缀按钮（不论 deleted）都必须清零
    SELECT COUNT(*) INTO v_btn_old FROM iam_menu
     WHERE name LIKE '业务建模-模型管理-%'
        OR name LIKE '业务建模-实体-%'
        OR name LIKE '业务建模-运行时数据-%';
    IF v_btn_old <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0044 校验失败：仍存在未联动的旧前缀按钮权限点（业务建模-实体-*/模型管理-*/运行时数据-*）';
    END IF;

    -- 判据 B：新前缀按钮按名精确存在且存活（防「旧前缀没了但也没改成新前缀」的零效果）；
    --          期望数 = 3 个按钮 × 租户数（每个租户种子各一份），不硬编码
    SELECT COUNT(*) INTO v_new_cnt FROM iam_menu
     WHERE deleted = 0
       AND name IN ('业务建模-业务对象-新增', '业务建模-业务对象-发布', '业务建模-业务数据-写入');
    IF v_new_cnt <> 3 * v_tenant OR v_tenant = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0044 校验失败：新前缀按钮数与租户基线不符（期望 3×租户数），联动可能未生效或种子缺失';
    END IF;

    -- 判据 C：菜单名防线 —— 0039 菜单改名若被回退，按钮联动即失去锚点，必须显式失败
    SELECT COUNT(*) INTO v_menu_old FROM iam_menu
     WHERE deleted = 0
       AND ((path = '/metadata#/apps' AND name = '建模工作台')
         OR (path = '/metadata#/entities' AND name IN ('实体', '模型管理'))
         OR (path = '/metadata#/runtime' AND name = '运行时数据'));
    IF v_menu_old <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0044 校验失败：业务建模子菜单被回退成旧名（建模工作台/实体/模型管理/运行时数据）——按钮联动的锚点已丢失，请先复核 0039';
    END IF;

    -- 判据 D：改名后三菜单行数 = path 基线（防部分租户漏改）
    SELECT COUNT(*) INTO v_base_cnt FROM iam_menu
     WHERE deleted = 0
       AND path IN ('/metadata#/apps', '/metadata#/entities', '/metadata#/runtime');
    SELECT COUNT(*) INTO v_menu_new FROM iam_menu
     WHERE deleted = 0
       AND name IN ('模型设计', '业务对象', '业务数据')
       AND path IN ('/metadata#/apps', '/metadata#/entities', '/metadata#/runtime');
    IF v_menu_new <> v_base_cnt OR v_base_cnt = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0044 校验失败：改名后菜单行数与基线不符（可能前置 0024 未执行或部分租户漏改）';
    END IF;

    SELECT v_btn_old AS old_prefix_left, v_new_cnt AS new_prefix_buttons,
           v_tenant AS tenants, v_menu_old AS menu_reverted,
           v_menu_new AS renamed_menus, v_base_cnt AS baseline_menus;
END$$
DELIMITER ;

CALL sp_verify_0044();
DROP PROCEDURE sp_verify_0044;
