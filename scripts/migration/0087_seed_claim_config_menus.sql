-- ============================================================
-- 0087_seed_claim_config_menus.sql
--
-- 一句话：给「智能理赔」目录补 3 条运营配置面的叶子菜单（保单管理 / 计划配置 / 额度管理），
--       并注册配套的 4 个权限码。
--
-- 背景（为什么必须同提交改前端 routes.tsx）
-- ----------------------------------------------------------------
-- `scripts/e2e/ui/claim-single-nav-probe.mjs` 对「IAM 菜单 ↔ bone-claim-app/src/routes.tsx」
-- 做**双向全等**断言：
--   · 页面有路由而菜单无入口 ⇒「功能不可达」判红（用户在侧栏找不到）；
--   · 菜单有而路由无页面 ⇒「幽灵菜单」判红（点了没反应）。
-- 且还逐条比对**菜单名与页面名逐字一致**、**菜单顺序与 routes.tsx 声明顺序一致**。
-- 只加页面不加菜单 ⇒ 探针必红，且**产品在真实环境里侧栏完全看不到这三个入口**
-- （菜单是侧栏的唯一来源）。
--
-- 三条菜单的顺序 = order_no 11/12/13，追加在既有 1..10 之后：
--   既有 1..10 是**理赔作业链路**（初审→核赔→理算→材料→资金→申诉→挂起→后督→申请→进度）；
--   新增 11..13 是**运营配置面**（保单→计划→额度）。放在链路之后而非之前，是为了让
--   "日常作业"与"偶发配置"在侧栏上自然分组——配置面改一次影响该保单所有未来赔案，
--   与逐单流转的作业面节奏不同，混排会让两类操作看起来同样高频。
--
-- 权限码口径（为什么读码与写码分开）
-- ----------------------------------------------------------------
-- 菜单的 permission 字段决定**能否看见入口**，端点的 @PreAuthorize 决定**能否操作**，
-- 两者是不同的闸门（0078 已记此口径：菜单用写权限码会让只持读权限的角色看不见入口）。
--   · 菜单 permission = claim:policy:read（读码）⇒ 持读权限的角色能看见页面；
--   · 写动作（启用/停用保单、发布/下线计划、建计划建险种、开户/调额/减人）
--     由端点上的 claim:policy:write / claim:quota:write 把关。
--   · 额度管理页的菜单 permission = claim:quota:read。
--
-- 图标白名单（G37）
-- ----------------------------------------------------------------
-- bone-shell 的 MENU_ICON_MAP 是**白名单**（不是动态取图标名）：未登记的图标名会被
-- `resolveMenuIcon` 静默降级成 DEFAULT_MENU_ICON —— 数据看着对、界面上图标错，且**不报错**。
-- 本脚本用的三个图标均已登记：
--   FileTextOutlined（保单管理）· DeploymentUnitOutlined（计划配置）· ClusterOutlined（额度管理）。
--
-- 执行（本地/测试库直接应用；生产数据变更属 L3，由 DBA / CI 在目标库运行）：
--   mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0087_seed_claim_config_menus.sql
-- ============================================================

USE bone;

-- ─────────────────────────────────────────────────────────────
-- 一、注册 4 个权限码
-- ─────────────────────────────────────────────────────────────
INSERT INTO iam_permission
    (id, code, name, resource_type, resource_path, action, type, sort_order, description)
SELECT * FROM (
    SELECT 9153001 AS id, 'claim:policy:read' AS code, '理赔-保单配置读' AS name, 'claim' AS resource_type,
           'policy' AS resource_path, 'read' AS action, 'OPERATION' AS type, 760 AS sort_order,
           '保单/计划/险种配置查看（ClaimConfigController 读端点 @PreAuthorize）' AS description
    UNION ALL SELECT 9153002, 'claim:policy:write', '理赔-保单配置写', 'claim', 'policy', 'write', 'OPERATION', 770,
           '保单/计划/险种配置维护（启用停用保单责任、发布下线计划、建计划建险种）'
    UNION ALL SELECT 9153003, 'claim:quota:read', '理赔-额度配置读', 'claim', 'quota', 'read', 'OPERATION', 780,
           '额度账户与保全记录查看（QuotaConfigController 读端点 @PreAuthorize）'
    UNION ALL SELECT 9153004, 'claim:quota:write', '理赔-额度配置写', 'claim', 'quota', 'write', 'OPERATION', 790,
           '额度开户/调额/减人/恢复（QuotaConfigController 写端点 @PreAuthorize）'
) AS new_codes
WHERE NOT EXISTS (SELECT 1 FROM iam_permission p WHERE p.code = new_codes.code);

-- ─────────────────────────────────────────────────────────────
-- 二、超管（role 1）绑定 4 码
-- ─────────────────────────────────────────────────────────────
-- 只绑 role 1（超管），与 0059 的理赔域写码同口径（理赔域属平台运营面）。
-- id 段 9153001..9153004 与权限码 id 同值段，便于回溯。
INSERT INTO iam_role_permission (id, role_id, permission_id)
SELECT * FROM (
    SELECT 9153001 AS id, 1 AS role_id, 9153001 AS permission_id
    UNION ALL SELECT 9153002, 1, 9153002
    UNION ALL SELECT 9153003, 1, 9153003
    UNION ALL SELECT 9153004, 1, 9153004
) AS new_bindings
WHERE NOT EXISTS (
    SELECT 1 FROM iam_role_permission rp
     WHERE rp.role_id = new_bindings.role_id
       AND rp.permission_id = new_bindings.permission_id
);

-- ─────────────────────────────────────────────────────────────
-- 三、播种 3 条叶子菜单
-- ─────────────────────────────────────────────────────────────
-- 目录定位：有「智能理赔」目录（type=0、path 为 NULL）的每个租户。
DROP TEMPORARY TABLE IF EXISTS tmp_claim_dir_0087;
CREATE TEMPORARY TABLE tmp_claim_dir_0087 (
    tenant_id BIGINT NOT NULL,
    dir_id    BIGINT NOT NULL,
    PRIMARY KEY (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO tmp_claim_dir_0087 (tenant_id, dir_id)
SELECT tenant_id, MIN(id)
  FROM iam_menu
 WHERE name = '智能理赔' AND path IS NULL AND type = 0 AND deleted = 0
 GROUP BY tenant_id;

-- 叶子 id = 目录 id + seq（既有约定：0064 用 10..17、0071 用 18、0078 用 19 ⇒ 本次顺延 20/21/22）
INSERT INTO iam_menu
    (id, tenant_id, name, parent_id, path, icon, order_no, permission, type, created_by, updated_by)
SELECT d.dir_id + 20, d.tenant_id, '保单管理', d.dir_id, '/claim#/policy',
       'FileTextOutlined', 11, 'claim:policy:read', 1, NULL, NULL
  FROM tmp_claim_dir_0087 d
 WHERE NOT EXISTS (
    SELECT 1 FROM iam_menu m
     WHERE m.tenant_id = d.tenant_id AND m.path = '/claim#/policy' AND m.deleted = 0
 );

INSERT INTO iam_menu
    (id, tenant_id, name, parent_id, path, icon, order_no, permission, type, created_by, updated_by)
SELECT d.dir_id + 21, d.tenant_id, '计划配置', d.dir_id, '/claim#/plan',
       'DeploymentUnitOutlined', 12, 'claim:policy:read', 1, NULL, NULL
  FROM tmp_claim_dir_0087 d
 WHERE NOT EXISTS (
    SELECT 1 FROM iam_menu m
     WHERE m.tenant_id = d.tenant_id AND m.path = '/claim#/plan' AND m.deleted = 0
 );

INSERT INTO iam_menu
    (id, tenant_id, name, parent_id, path, icon, order_no, permission, type, created_by, updated_by)
SELECT d.dir_id + 22, d.tenant_id, '额度管理', d.dir_id, '/claim#/quota',
       'ClusterOutlined', 13, 'claim:quota:read', 1, NULL, NULL
  FROM tmp_claim_dir_0087 d
 WHERE NOT EXISTS (
    SELECT 1 FROM iam_menu m
     WHERE m.tenant_id = d.tenant_id AND m.path = '/claim#/quota' AND m.deleted = 0
 );

DROP TEMPORARY TABLE IF EXISTS tmp_claim_dir_0087;

-- ─────────────────────────────────────────────────────────────
-- 四、验收
-- ─────────────────────────────────────────────────────────────
DROP PROCEDURE IF EXISTS sp_verify_0087;
DELIMITER $$
CREATE PROCEDURE sp_verify_0087()
BEGIN
    DECLARE v_dir    INT DEFAULT 0;
    DECLARE v_leaf   INT DEFAULT 0;
    DECLARE v_miss   INT DEFAULT 0;
    DECLARE v_order  INT DEFAULT 0;
    DECLARE v_orphan INT DEFAULT 0;
    DECLARE v_dup    INT DEFAULT 0;
    DECLARE v_drift  INT DEFAULT 0;
    DECLARE v_perm   INT DEFAULT 0;
    DECLARE v_icon   INT DEFAULT 0;

    -- ① 平台模板租户（0）与默认租户（1001）必须恰好 13 条叶子
    --    【为什么只校验这两个租户】其余有「智能理赔」目录的租户是 E2E 残留租户
    --    （实测只有初审/后督抽检 2 条叶子，此前 0071/0078 也从未给它们补齐全量）。
    --    若按"每个有目录的租户都该有 13 条"来断言，会把**历史遗留的租户覆盖不均**
    --    误判成本脚本失败——而那不是本脚本能修的事（补齐 E2E 租户的全量菜单属另一件事）。
    --    本脚本的职责是"给本就完整的租户补齐这 3 条"，故按"目录完整"的租户来断言。
    SELECT COUNT(DISTINCT tenant_id) INTO v_dir FROM iam_menu
     WHERE name = '智能理赔' AND path IS NULL AND type = 0 AND deleted = 0;

    SELECT COUNT(*) INTO v_leaf FROM iam_menu m
      JOIN (SELECT tenant_id FROM iam_menu
             WHERE name = '智能理赔' AND path IS NULL AND type = 0 AND deleted = 0
               AND tenant_id IN (0, 1001)) d ON d.tenant_id = m.tenant_id
     WHERE m.deleted = 0 AND m.type = 1 AND m.path LIKE '/claim#/%';
    IF v_leaf <> 2 * 13 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0087 校验失败：平台/默认租户的理赔叶子菜单未按 13 条全覆盖';
    END IF;

    -- ② 三条新菜单必须真实存在（防 INSERT 被静默跳过）
    SELECT COUNT(*) INTO v_miss FROM (SELECT DISTINCT tenant_id FROM iam_menu
             WHERE name = '智能理赔' AND path IS NULL AND type = 0 AND deleted = 0
               AND tenant_id IN (0, 1001)) d
     WHERE NOT EXISTS (
            SELECT 1 FROM iam_menu m
             WHERE m.tenant_id = d.tenant_id
               AND m.path = '/claim#/policy' AND m.deleted = 0
       )
        OR NOT EXISTS (
            SELECT 1 FROM iam_menu m
             WHERE m.tenant_id = d.tenant_id
               AND m.path = '/claim#/plan' AND m.deleted = 0
       )
        OR NOT EXISTS (
            SELECT 1 FROM iam_menu m
             WHERE m.tenant_id = d.tenant_id
               AND m.path = '/claim#/quota' AND m.deleted = 0
       );
    IF v_miss > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0087 校验失败：存在缺 保单管理/计划配置/额度管理 菜单的租户';
    END IF;

    -- ③ 顺序必须覆盖 1..13 且无重复
    SELECT COUNT(DISTINCT order_no) INTO v_order FROM iam_menu m
      JOIN (SELECT DISTINCT tenant_id FROM iam_menu
             WHERE name = '智能理赔' AND path IS NULL AND type = 0 AND deleted = 0
               AND tenant_id IN (0, 1001)) d
        ON d.tenant_id = m.tenant_id
     WHERE m.deleted = 0 AND m.type = 1 AND m.path LIKE '/claim#/%'
       AND m.order_no BETWEEN 1 AND 13;
    IF v_order <> 13 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0087 校验失败：理赔菜单 order_no 未覆盖 1..13';
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
            SET MESSAGE_TEXT = '0087 校验失败：存在未挂在「智能理赔」目录下的叶子';
    END IF;

    -- ⑤ 同租户同 path 不许重复
    SELECT COUNT(*) INTO v_dup FROM (
        SELECT tenant_id, path, COUNT(*) c FROM iam_menu
         WHERE deleted = 0 AND type = 1 AND path LIKE '/claim#/%'
           AND tenant_id IN (0, 1001)
         GROUP BY tenant_id, path HAVING c > 1) x;
    IF v_dup > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0087 校验失败：存在重复理赔菜单行（幂等判定失效）';
    END IF;

    -- ⑥ 菜单名必须与前端 routes.tsx 逐字一致（探针按名比对，漂移即"菜单与页面不同名"）
    SELECT COUNT(*) INTO v_drift FROM iam_menu m
     WHERE m.deleted = 0 AND m.type = 1 AND m.path LIKE '/claim#/%'
       AND m.tenant_id IN (0, 1001)
       AND NOT EXISTS (
            SELECT 1 FROM (
                SELECT '/claim#/pre-exam' AS p, '初审工作台' AS n
                UNION ALL SELECT '/claim#/review', '核赔工作台'
                UNION ALL SELECT '/claim#/adjust', '理算归因'
                UNION ALL SELECT '/claim#/evidence', '材料上传'
                UNION ALL SELECT '/claim#/fund', '资金工作台'
                UNION ALL SELECT '/claim#/appeal', '申诉复议'
                UNION ALL SELECT '/claim#/suspend', '挂起/恢复'
                UNION ALL SELECT '/claim#/inspection', '后督抽检'
                UNION ALL SELECT '/claim#/submit', '理赔申请'
                UNION ALL SELECT '/claim#/progress', '理赔进度'
                UNION ALL SELECT '/claim#/policy', '保单管理'
                UNION ALL SELECT '/claim#/plan', '计划配置'
                UNION ALL SELECT '/claim#/quota', '额度管理'
            ) t WHERE t.p = m.path AND t.n = m.name);
    IF v_drift <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0087 校验失败：理赔菜单名称与 routes.tsx 不一致（存在漂移行）';
    END IF;

    -- ⑦ 新菜单的 permission 必须是对应的读码（否则只持读权限的角色看不见入口）
    SELECT COUNT(*) INTO v_perm FROM iam_menu m
     WHERE m.deleted = 0 AND m.type = 1
       AND ((m.path = '/claim#/policy' OR m.path = '/claim#/plan') AND m.permission <> 'claim:policy:read')
        OR (m.path = '/claim#/quota' AND m.permission <> 'claim:quota:read');
    IF v_perm > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0087 校验失败：新菜单 permission 不是对应读权限码（入口会看不见）';
    END IF;

    -- ⑧ 图标必须非空（白名单校验在 G37 脚本里做）
    SELECT COUNT(*) INTO v_icon FROM iam_menu m
     WHERE m.deleted = 0 AND m.type = 1
       AND m.path IN ('/claim#/policy', '/claim#/plan', '/claim#/quota')
       AND (m.icon IS NULL OR m.icon = '');
    IF v_icon > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0087 校验失败：新菜单 icon 为空（会被静默降级成默认图标）';
    END IF;
END$$
DELIMITER ;

CALL sp_verify_0087();
DROP PROCEDURE IF EXISTS sp_verify_0087;
