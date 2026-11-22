-- ============================================================
-- 0136_seed_claim_governance_menus.sql
--
-- 一句话：给「智能理赔」目录补 2 条治理面叶子菜单
--        「AI 治理」(/claim#/ai-governance) 与「运营分析看板」(/claim#/analytics)，
--        与 bone-claim-app/src/routes.tsx 的两条路由逐字对齐。
--
-- 背景（为什么必须同提交改 routes.tsx / claimMenuGroups）
-- ----------------------------------------------------------------
-- 这两页此前是 hidden 路由：只能从「我的工作台」治理卡片 / 命令面板深链进入，
-- 侧栏（IAM 菜单是侧栏唯一来源）看不到。实际使用中治理看板与 AI 守门是高频复核入口，
-- 藏在卡片里等于「功能不可达」。2026-11-22 提升为正式菜单项，接入侧栏「治理」分组。
-- scripts/e2e/ui/claim-single-nav-probe.mjs 对「IAM 菜单 ↔ routes.tsx」做双向全等断言：
--   · 页面有路由而菜单无入口 ⇒「功能不可达」；· 菜单有而路由无 ⇒「幽灵菜单」；
--   · 且菜单名逐字一致、order_no 顺序 = routes.tsx 声明顺序。
-- 故本脚本与 routes.tsx（去 hidden）/ claimMenuGroups.ts（登记治理组）必须同提交。
--
-- 口径（与 0087 / 0092 / 0102 完全一致）
-- ----------------------------------------------------------------
--   · 目录定位 = 有「智能理赔」目录（type=0、path NULL）的每个租户；
--   · 叶子 id = 目录 id + 27 / +28（16/17 已被承保主体/主数据映射占用；28/29 空闲核对过）；
--   · 菜单 permission = claim:claims:read（两页后端读码，App.tsx HIDDEN_ROUTE_REQUIRED 已有）；
--   · order_no = 18 / 19（接在 11..17 之后，与 routes.tsx 声明顺序一致：
--     /ai-governance 在 /mappings 之后、/analytics 紧随其后）；
--   · 图标 = SafetyCertificateOutlined（AI 治理）/ DashboardOutlined（运营看板），
--     二者均已登记进 bone-shell MENU_ICON_MAP 白名单（G37 门禁校验）。
--
-- 执行（本地/测试库直接应用；生产数据变更属 L3，由 DBA / CI 在目标库运行）：
--   mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0136_seed_claim_governance_menus.sql
-- ============================================================

USE bone;

DROP TEMPORARY TABLE IF EXISTS tmp_claim_dir_0136;
CREATE TEMPORARY TABLE tmp_claim_dir_0136 (
    tenant_id BIGINT NOT NULL,
    dir_id    BIGINT NOT NULL,
    PRIMARY KEY (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO tmp_claim_dir_0136 (tenant_id, dir_id)
SELECT tenant_id, MIN(id)
  FROM iam_menu
 WHERE name = '智能理赔' AND path IS NULL AND type = 0 AND deleted = 0
 GROUP BY tenant_id;

-- 叶子 id = 目录 id + 27；幂等：同租户同 path 已存在则跳过
INSERT INTO iam_menu
    (id, tenant_id, name, parent_id, path, icon, order_no, permission, type, created_by, updated_by)
SELECT d.dir_id + 27, d.tenant_id, 'AI 治理', d.dir_id, '/claim#/ai-governance',
       'SafetyCertificateOutlined', 18, 'claim:claims:read', 1, NULL, NULL
  FROM tmp_claim_dir_0136 d
 WHERE NOT EXISTS (
    SELECT 1 FROM iam_menu m
     WHERE m.tenant_id = d.tenant_id AND m.path = '/claim#/ai-governance' AND m.deleted = 0
 );

-- 叶子 id = 目录 id + 28；幂等：同租户同 path 已存在则跳过
INSERT INTO iam_menu
    (id, tenant_id, name, parent_id, path, icon, order_no, permission, type, created_by, updated_by)
SELECT d.dir_id + 28, d.tenant_id, '运营分析看板', d.dir_id, '/claim#/analytics',
       'DashboardOutlined', 19, 'claim:claims:read', 1, NULL, NULL
  FROM tmp_claim_dir_0136 d
 WHERE NOT EXISTS (
    SELECT 1 FROM iam_menu m
     WHERE m.tenant_id = d.tenant_id AND m.path = '/claim#/analytics' AND m.deleted = 0
 );

DROP TEMPORARY TABLE IF EXISTS tmp_claim_dir_0136;

-- ─────────────────────────────────────────────────────────────
-- 验收（只校验本脚本负责的两条新菜单，不断言全局叶子总数——
-- 历史脚本对全局数的口径彼此不一致，断言全局数易误杀；菜单↔路由全等由探针把关）
-- ─────────────────────────────────────────────────────────────
DROP PROCEDURE IF EXISTS sp_verify_0136;
DELIMITER $$
CREATE PROCEDURE sp_verify_0136()
BEGIN
    DECLARE v_gov       INT DEFAULT 0;
    DECLARE v_analytics INT DEFAULT 0;
    DECLARE v_drift     INT DEFAULT 0;
    DECLARE v_perm      INT DEFAULT 0;
    DECLARE v_icon      INT DEFAULT 0;
    DECLARE v_order     INT DEFAULT 0;
    DECLARE v_dup       INT DEFAULT 0;

    -- ① 两条新菜单必须真实存在（防 INSERT 被静默跳过）
    SELECT COUNT(*) INTO v_gov FROM iam_menu m
     WHERE m.deleted = 0 AND m.type = 1 AND m.path = '/claim#/ai-governance'
       AND m.tenant_id IN (0, 1001);
    SELECT COUNT(*) INTO v_analytics FROM iam_menu m
     WHERE m.deleted = 0 AND m.type = 1 AND m.path = '/claim#/analytics'
       AND m.tenant_id IN (0, 1001);
    IF v_gov < 1 OR v_analytics < 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0136 校验失败：AI 治理 / 运营分析看板 菜单未播种成功';
    END IF;

    -- ② 菜单名必须与前端 routes.tsx 逐字一致
    SELECT COUNT(*) INTO v_drift FROM iam_menu m
     WHERE m.deleted = 0 AND m.type = 1 AND m.tenant_id IN (0, 1001)
       AND ((m.path = '/claim#/ai-governance' AND m.name <> 'AI 治理')
         OR (m.path = '/claim#/analytics' AND m.name <> '运营分析看板'));
    IF v_drift > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0136 校验失败：菜单名称与 routes.tsx 不一致（存在漂移行）';
    END IF;

    -- ③ permission 必须是读码（否则只持读权限的角色看不见入口）
    SELECT COUNT(*) INTO v_perm FROM iam_menu m
     WHERE m.deleted = 0 AND m.type = 1 AND m.tenant_id IN (0, 1001)
       AND m.path IN ('/claim#/ai-governance', '/claim#/analytics')
       AND m.permission <> 'claim:claims:read';
    IF v_perm > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0136 校验失败：新菜单 permission 不是 claim:claims:read（入口会看不见）';
    END IF;

    -- ④ 图标必须非空且已在 bone-shell MENU_ICON_MAP 白名单
    SELECT COUNT(*) INTO v_icon FROM iam_menu m
     WHERE m.deleted = 0 AND m.type = 1 AND m.tenant_id IN (0, 1001)
       AND m.path IN ('/claim#/ai-governance', '/claim#/analytics')
       AND (m.icon IS NULL OR m.icon = '' OR m.icon NOT IN ('SafetyCertificateOutlined', 'DashboardOutlined'));
    IF v_icon > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0136 校验失败：新菜单 icon 缺失或不在白名单（会静默降级）';
    END IF;

    -- ⑤ order_no 必须与 routes.tsx 声明顺序一致（治理组接在 mappings=17 之后）
    SELECT COUNT(*) INTO v_order FROM iam_menu m
     WHERE m.deleted = 0 AND m.type = 1 AND m.tenant_id IN (0, 1001)
       AND ((m.path = '/claim#/ai-governance' AND m.order_no <> 18)
         OR (m.path = '/claim#/analytics' AND m.order_no <> 19));
    IF v_order > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0136 校验失败：order_no 与 routes.tsx 声明顺序不一致';
    END IF;

    -- ⑥ 同租户同 path 不许重复
    SELECT COUNT(*) INTO v_dup FROM (
        SELECT tenant_id, path, COUNT(*) c FROM iam_menu
         WHERE deleted = 0 AND type = 1
           AND path IN ('/claim#/ai-governance', '/claim#/analytics')
           AND tenant_id IN (0, 1001)
         GROUP BY tenant_id, path HAVING c > 1) x;
    IF v_dup > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0136 校验失败：存在重复理赔菜单行（幂等判定失效）';
    END IF;
END$$
DELIMITER ;

CALL sp_verify_0136();
DROP PROCEDURE IF EXISTS sp_verify_0136;
