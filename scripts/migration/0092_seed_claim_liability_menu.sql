-- ============================================================
-- 0092_seed_claim_liability_menu.sql
--
-- 一句话：给「智能理赔」目录补第 4 条运营配置面叶子菜单「责任配置」（/claim#/liability），
--        与 bone-claim-app/src/routes.tsx 的 ClaimRoute 第 14 项逐字对齐，闭合 0087 遗留的导航缺口。
--
-- 背景（为什么必须补）
-- ----------------------------------------------------------------
-- routes.tsx 已注册 `{ path: '/liability', name: '责任配置', icon: <TagsOutlined/>, ... }`
-- （bone-claim-app/src/routes.tsx:94），但 0087 只播种了 保单管理 / 计划配置 / 额度管理 三条，
-- 漏了「责任配置」。后果：
--   · 侧栏无入口 ⇒ 用户在智能理赔下找不到「责任配置」；
--   · scripts/e2e/ui/claim-single-nav-probe.mjs 双向全等断言会判红
--     （"页面有路由而菜单无入口 ⇒ 功能不可达"）；
--   · 真实环境该页面完全不可达（菜单是侧栏唯一来源）。
--
-- 口径（与 0087 完全一致）
-- ----------------------------------------------------------------
--   · 目录定位 = 有「智能理赔」目录（type=0、path NULL）的每个租户；
--   · 叶子 id = 目录 id + 23（0064 用 10..17、0071 用 18、0078 用 19、0087 用 20/21/22 ⇒ 本次顺延 23）；
--   · 菜单 permission = claim:policy:read（读码）⇒ 持读权限的角色可见；
--     写动作（新建/编辑责任、发布/下线责任）由 ClaimConfigController 端点上的 claim:policy:write 把关
--     （该码已在 0087 注册，本脚本不重复注册）；
--   · 图标 = TagsOutlined（与 routes.tsx 同值；已登记进 bone-shell MENU_ICON_MAP 白名单，否则会静默降级）；
--   · order_no = 14（接在 11/12/13 之后，保持"作业链路在前、运营配置面在后"的分组）。
--
-- 执行（本地/测试库直接应用；生产数据变更属 L3，由 DBA / CI 在目标库运行）：
--   mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0092_seed_claim_liability_menu.sql
-- ============================================================

USE bone;

DROP TEMPORARY TABLE IF EXISTS tmp_claim_dir_0092;
CREATE TEMPORARY TABLE tmp_claim_dir_0092 (
    tenant_id BIGINT NOT NULL,
    dir_id    BIGINT NOT NULL,
    PRIMARY KEY (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO tmp_claim_dir_0092 (tenant_id, dir_id)
SELECT tenant_id, MIN(id)
  FROM iam_menu
 WHERE name = '智能理赔' AND path IS NULL AND type = 0 AND deleted = 0
 GROUP BY tenant_id;

-- 叶子 id = 目录 id + 23；幂等：同租户同 path 已存在则跳过
INSERT INTO iam_menu
    (id, tenant_id, name, parent_id, path, icon, order_no, permission, type, created_by, updated_by)
SELECT d.dir_id + 23, d.tenant_id, '责任配置', d.dir_id, '/claim#/liability',
       'TagsOutlined', 14, 'claim:policy:read', 1, NULL, NULL
  FROM tmp_claim_dir_0092 d
 WHERE NOT EXISTS (
    SELECT 1 FROM iam_menu m
     WHERE m.tenant_id = d.tenant_id AND m.path = '/claim#/liability' AND m.deleted = 0
 );

DROP TEMPORARY TABLE IF EXISTS tmp_claim_dir_0092;

-- ─────────────────────────────────────────────────────────────
-- 验收
-- ─────────────────────────────────────────────────────────────
DROP PROCEDURE IF EXISTS sp_verify_0092;
DELIMITER $$
CREATE PROCEDURE sp_verify_0092()
BEGIN
    DECLARE v_leaf   INT DEFAULT 0;
    DECLARE v_miss   INT DEFAULT 0;
    DECLARE v_order  INT DEFAULT 0;
    DECLARE v_orphan INT DEFAULT 0;
    DECLARE v_dup    INT DEFAULT 0;
    DECLARE v_drift  INT DEFAULT 0;
    DECLARE v_perm   INT DEFAULT 0;
    DECLARE v_icon   INT DEFAULT 0;

    -- ① 平台模板租户(0)与默认租户(1001)必须恰好 14 条叶子（1..14 全覆盖）
    --    【为什么只校验这两个租户】其余有「智能理赔」目录的租户是 E2E 残留租户
    --    （此前 0071/0078 也从未给它们补齐全量），非本脚本职责。
    SELECT COUNT(*) INTO v_leaf FROM iam_menu m
      JOIN (SELECT tenant_id FROM iam_menu
             WHERE name = '智能理赔' AND path IS NULL AND type = 0 AND deleted = 0
               AND tenant_id IN (0, 1001)) d ON d.tenant_id = m.tenant_id
     WHERE m.deleted = 0 AND m.type = 1 AND m.path LIKE '/claim#/%';
    IF v_leaf <> 2 * 14 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0092 校验失败：平台/默认租户的理赔叶子菜单未按 14 条全覆盖';
    END IF;

    -- ② 「责任配置」叶子必须真实存在（防 INSERT 被静默跳过）
    SELECT COUNT(*) INTO v_miss FROM (SELECT DISTINCT tenant_id FROM iam_menu
             WHERE name = '智能理赔' AND path IS NULL AND type = 0 AND deleted = 0
               AND tenant_id IN (0, 1001)) d
     WHERE NOT EXISTS (
            SELECT 1 FROM iam_menu m
             WHERE m.tenant_id = d.tenant_id
               AND m.path = '/claim#/liability' AND m.deleted = 0);
    IF v_miss > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0092 校验失败：存在缺 责任配置 菜单的租户';
    END IF;

    -- ③ 顺序必须覆盖 1..14 且无重复
    SELECT COUNT(DISTINCT order_no) INTO v_order FROM iam_menu m
      JOIN (SELECT DISTINCT tenant_id FROM iam_menu
             WHERE name = '智能理赔' AND path IS NULL AND type = 0 AND deleted = 0
               AND tenant_id IN (0, 1001)) d
        ON d.tenant_id = m.tenant_id
     WHERE m.deleted = 0 AND m.type = 1 AND m.path LIKE '/claim#/%'
       AND m.order_no BETWEEN 1 AND 14;
    IF v_order <> 14 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0092 校验失败：理赔菜单 order_no 未覆盖 1..14';
    END IF;

    -- ④ 不许有未挂父节点的叶子
    SELECT COUNT(*) INTO v_orphan FROM iam_menu m
     WHERE m.deleted = 0 AND m.type = 1 AND m.path LIKE '/claim#/%'
       AND m.tenant_id IN (0, 1001)
       AND (m.parent_id IS NULL
            OR NOT EXISTS (SELECT 1 FROM iam_menu p
                            WHERE p.id = m.parent_id AND p.deleted = 0
                              AND p.name = '智能理赔' AND p.type = 0));
    IF v_orphan > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0092 校验失败：存在未挂在「智能理赔」目录下的叶子';
    END IF;

    -- ⑤ 同租户同 path 不许重复
    SELECT COUNT(*) INTO v_dup FROM (
        SELECT tenant_id, path, COUNT(*) c FROM iam_menu
         WHERE deleted = 0 AND type = 1 AND path LIKE '/claim#/%'
           AND tenant_id IN (0, 1001)
         GROUP BY tenant_id, path HAVING c > 1) x;
    IF v_dup > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0092 校验失败：存在重复理赔菜单行（幂等判定失效）';
    END IF;

    -- ⑥ 菜单名必须与前端 routes.tsx 逐字一致（探针按名比对，漂移即"菜单与页面不同名"）
    SELECT COUNT(*) INTO v_drift FROM iam_menu m
     WHERE m.deleted = 0 AND m.type = 1 AND m.path = '/claim#/liability'
       AND m.tenant_id IN (0, 1001)
       AND m.name <> '责任配置';
    IF v_drift > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0092 校验失败：责任配置菜单名称与 routes.tsx 不一致（存在漂移行）';
    END IF;

    -- ⑦ 新菜单的 permission 必须是对应的读码（否则只持读权限的角色看不见入口）
    SELECT COUNT(*) INTO v_perm FROM iam_menu m
     WHERE m.deleted = 0 AND m.type = 1
       AND m.path = '/claim#/liability'
       AND m.permission <> 'claim:policy:read';
    IF v_perm > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0092 校验失败：责任配置菜单 permission 不是 claim:policy:read（入口会看不见）';
    END IF;

    -- ⑧ 图标必须非空且已在 bone-shell MENU_ICON_MAP 白名单（否则静默降级成默认图标）
    SELECT COUNT(*) INTO v_icon FROM iam_menu m
     WHERE m.deleted = 0 AND m.type = 1
       AND m.path = '/claim#/liability'
       AND (m.icon IS NULL OR m.icon = '' OR m.icon <> 'TagsOutlined');
    IF v_icon > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0092 校验失败：责任配置菜单 icon 缺失或不在白名单（会静默降级）';
    END IF;
END$$
DELIMITER ;

CALL sp_verify_0092();
DROP PROCEDURE IF EXISTS sp_verify_0092;
