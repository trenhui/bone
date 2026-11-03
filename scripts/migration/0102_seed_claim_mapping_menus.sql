-- ============================================================
-- 0102_seed_claim_mapping_menus.sql
--
-- 一句话：给「智能理赔」目录补 2 条运营配置面叶子菜单
--        「承保主体」(/claim#/insurers) 与「主数据映射」(/claim#/mappings)，
--        与 bone-claim-app/src/routes.tsx 的两条路由逐字对齐，闭合多保司映射链路的导航缺口。
--
-- 背景（为什么必须同提交改前端 routes.tsx）
-- ----------------------------------------------------------------
-- scripts/e2e/ui/claim-single-nav-probe.mjs 对「IAM 菜单 ↔ routes.tsx」做双向全等断言：
--   · 页面有路由而菜单无入口 ⇒「功能不可达」（用户在侧栏找不到）；
--   · 菜单有而路由无 ⇒「幽灵菜单」（点了没反应）；
--   · 且逐条比对菜单名与页面名逐字一致、菜单 order_no 顺序与 routes.tsx 声明顺序一致。
-- 只加页面不加菜单 ⇒ 探针必红，且真实环境侧栏完全看不到这两个入口（菜单是侧栏唯一来源）。
-- 故本脚本与 routes.tsx 的改动必须同提交：routes 已去除 hidden、菜单名/顺序/图标逐字对齐。
--
-- 口径（与 0087 / 0092 / 0094 完全一致）
-- ----------------------------------------------------------------
--   · 目录定位 = 有「智能理赔」目录（type=0、path NULL）的每个租户；
--   · 叶子 id = 目录 id + 25 / +26（23 已被责任配置占用、24 已被基础目录库占用）；
--   · 菜单 permission = claim:policy:read（读码，已在 0087 注册）⇒ 持读权限的角色可见；
--     写动作（注册承保主体、发布/停用映射、补未命中）由 CodeMappingController 端点上的
--     claim:policy:write 把关（该码已在 0087 注册，本脚本不重复注册）；
--   · order_no = 16 / 17（接在 11..15 之后：保单11/计划12/额度13/责任14/目录库15）；
--   · 图标 = SafetyCertificateOutlined（承保主体资质）/ LinkOutlined（保司码↔标准码 crosswalk），
--     二者均已登记进 bone-shell MENU_ICON_MAP 白名单（G37 门禁校验，未登记会静默降级成默认图标）。
--
-- 执行（本地/测试库直接应用；生产数据变更属 L3，由 DBA / CI 在目标库运行）：
--   mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0102_seed_claim_mapping_menus.sql
-- ============================================================

USE bone;

DROP TEMPORARY TABLE IF EXISTS tmp_claim_dir_0102;
CREATE TEMPORARY TABLE tmp_claim_dir_0102 (
    tenant_id BIGINT NOT NULL,
    dir_id    BIGINT NOT NULL,
    PRIMARY KEY (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO tmp_claim_dir_0102 (tenant_id, dir_id)
SELECT tenant_id, MIN(id)
  FROM iam_menu
 WHERE name = '智能理赔' AND path IS NULL AND type = 0 AND deleted = 0
 GROUP BY tenant_id;

-- 叶子 id = 目录 id + 25；幂等：同租户同 path 已存在则跳过
INSERT INTO iam_menu
    (id, tenant_id, name, parent_id, path, icon, order_no, permission, type, created_by, updated_by)
SELECT d.dir_id + 25, d.tenant_id, '承保主体', d.dir_id, '/claim#/insurers',
       'SafetyCertificateOutlined', 16, 'claim:policy:read', 1, NULL, NULL
  FROM tmp_claim_dir_0102 d
 WHERE NOT EXISTS (
    SELECT 1 FROM iam_menu m
     WHERE m.tenant_id = d.tenant_id AND m.path = '/claim#/insurers' AND m.deleted = 0
 );

-- 叶子 id = 目录 id + 26；幂等：同租户同 path 已存在则跳过
INSERT INTO iam_menu
    (id, tenant_id, name, parent_id, path, icon, order_no, permission, type, created_by, updated_by)
SELECT d.dir_id + 26, d.tenant_id, '主数据映射', d.dir_id, '/claim#/mappings',
       'LinkOutlined', 17, 'claim:policy:read', 1, NULL, NULL
  FROM tmp_claim_dir_0102 d
 WHERE NOT EXISTS (
    SELECT 1 FROM iam_menu m
     WHERE m.tenant_id = d.tenant_id AND m.path = '/claim#/mappings' AND m.deleted = 0
 );

DROP TEMPORARY TABLE IF EXISTS tmp_claim_dir_0102;

-- ─────────────────────────────────────────────────────────────
-- 验收（只校验本脚本负责的两条新菜单，不断言全局叶子总数——
-- 历史脚本对全局数的口径彼此不一致，断言全局数易误杀；菜单↔路由全等由探针把关）
-- ─────────────────────────────────────────────────────────────
DROP PROCEDURE IF EXISTS sp_verify_0102;
DELIMITER $$
CREATE PROCEDURE sp_verify_0102()
BEGIN
    DECLARE v_insurer  INT DEFAULT 0;
    DECLARE v_mapping  INT DEFAULT 0;
    DECLARE v_drift    INT DEFAULT 0;
    DECLARE v_perm     INT DEFAULT 0;
    DECLARE v_icon     INT DEFAULT 0;
    DECLARE v_dup      INT DEFAULT 0;

    -- ① 两条新菜单必须真实存在（防 INSERT 被静默跳过）
    SELECT COUNT(*) INTO v_insurer FROM iam_menu m
     WHERE m.deleted = 0 AND m.type = 1 AND m.path = '/claim#/insurers'
       AND m.tenant_id IN (0, 1001);
    SELECT COUNT(*) INTO v_mapping FROM iam_menu m
     WHERE m.deleted = 0 AND m.type = 1 AND m.path = '/claim#/mappings'
       AND m.tenant_id IN (0, 1001);
    IF v_insurer < 1 OR v_mapping < 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0102 校验失败：承保主体 / 主数据映射 菜单未播种成功';
    END IF;

    -- ② 菜单名必须与前端 routes.tsx 逐字一致（探针按名比对，漂移即"菜单与页面不同名"）
    SELECT COUNT(*) INTO v_drift FROM iam_menu m
     WHERE m.deleted = 0 AND m.type = 1 AND m.tenant_id IN (0, 1001)
       AND ((m.path = '/claim#/insurers' AND m.name <> '承保主体')
         OR (m.path = '/claim#/mappings' AND m.name <> '主数据映射'));
    IF v_drift > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0102 校验失败：菜单名称与 routes.tsx 不一致（存在漂移行）';
    END IF;

    -- ③ 新菜单的 permission 必须是读码（否则只持读权限的角色看不见入口）
    SELECT COUNT(*) INTO v_perm FROM iam_menu m
     WHERE m.deleted = 0 AND m.type = 1 AND m.tenant_id IN (0, 1001)
       AND ((m.path = '/claim#/insurers' OR m.path = '/claim#/mappings')
            AND m.permission <> 'claim:policy:read');
    IF v_perm > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0102 校验失败：新菜单 permission 不是 claim:policy:read（入口会看不见）';
    END IF;

    -- ④ 图标必须非空且已在 bone-shell MENU_ICON_MAP 白名单（否则静默降级成默认图标）
    SELECT COUNT(*) INTO v_icon FROM iam_menu m
     WHERE m.deleted = 0 AND m.type = 1 AND m.tenant_id IN (0, 1001)
       AND m.path IN ('/claim#/insurers', '/claim#/mappings')
       AND (m.icon IS NULL OR m.icon = '' OR m.icon NOT IN ('SafetyCertificateOutlined', 'LinkOutlined'));
    IF v_icon > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0102 校验失败：新菜单 icon 缺失或不在白名单（会静默降级）';
    END IF;

    -- ⑤ 同租户同 path 不许重复
    SELECT COUNT(*) INTO v_dup FROM (
        SELECT tenant_id, path, COUNT(*) c FROM iam_menu
         WHERE deleted = 0 AND type = 1 AND path IN ('/claim#/insurers', '/claim#/mappings')
           AND tenant_id IN (0, 1001)
         GROUP BY tenant_id, path HAVING c > 1) x;
    IF v_dup > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0102 校验失败：存在重复理赔菜单行（幂等判定失效）';
    END IF;
END$$
DELIMITER ;

CALL sp_verify_0102();
DROP PROCEDURE IF EXISTS sp_verify_0102;
