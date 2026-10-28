-- ============================================================
-- 0071_align_claim_menus.sql
--
-- 背景（2026-10-28 用户反馈实测确诊）
-- --------------------------------
-- 截图现象：智能理赔出现「两层菜单」——shell 侧边栏渲染 IAM 菜单 8 条，微应用
-- `bone-claim-app` 内部又自绘一条 Sider 9 条，且两者子项**不完全相同**：
--   IAM 菜单（0064 播种）        子应用自绘导航（改前 App.tsx:44 NAV_ITEMS）
--   报案申请 /submit         ↔  员工报案
--   理赔进度 /progress       ↔  进度追踪
--   核赔工作台 /review       =  核赔工作台
--   理算归因 /adjust         =  理算归因
--   证据上传 /evidence       ↔  材料上传
--   资金工作台 /fund         =  资金工作台
--   申诉处理 /appeal         ↔  申诉复议
--   暂停案件 /suspend        ↔  挂起/恢复
--   （缺）                    ↔  后督抽检 /inspection   ← 页面与路由都在，菜单无入口
--
-- 两层菜单的根因是**同一份导航事实被维护了两处**，必然漂移。修法分两半：
--   · 前端（本脚本之外，同提交）：子应用改为嵌入式单导航——qiankun 内不再渲染侧边栏，
--     全局导航只由 shell 依据 IAM 菜单渲染；路由/名称收敛到 `src/routes.tsx` 唯一真源。
--   · 数据（本脚本）：把 IAM 菜单的**名称、顺序、完整性**对齐到「页面语义 + 作业链路」，
--     并补齐缺失入口「后督抽检」，使 9 个页面 ↔ 9 条菜单一一对应。
--
-- 命名口径（术语取 PRD/工程落地版，不新造词）
-- --------------------------------
--   理赔申请 ← 报案申请   PRD §7.2「理赔申请」；页面 H1 亦为「理赔申请」
--   材料上传 ← 证据上传   PRD §7.1 A2「材料上传」/ 工程落地版 §A2「材料上传」
--   申诉复议 ← 申诉处理   Rulebook R-MAN-04「申诉复议」；子应用页面 Card 亦名「申诉复议」
--   挂起/恢复 ← 暂停案件 工程落地版 §5「挂起（suspend）」+ 挂起唤醒/恢复
--   后督抽检 ← 新增       PRD §7.3「后督巡检」/ 工程落地版 §7.6「后督抽检」（页面已存在）
--
-- 顺序（order_no 1..9）= 内勤作业链路 → 员工自助
--   核赔 → 理算 → 材料 → 资金 → 申诉 → 挂起 → 后督 → 理赔申请 → 理赔进度
--   与 `bone-claim-app/src/routes.tsx` 的数组顺序逐项对应（前端数组即落点顺序）。
--
-- 幂等
-- --------------------------------
-- · 改名按 **path 锚定**（path 是路由身份，改名不改 path），已改过则 0 行更新；
-- · 新增按 (tenant_id, path) 判存在；不用 INSERT IGNORE（会吞约束错误伪装成功）；
-- · 排序按 path 直接覆盖为终态值（与当前值相同也是幂等）。
--
-- 号段
-- --------------------------------
-- · 新增叶子的 id = 所属「智能理赔」目录 id + 17（即 seq 18），沿用 0064 的
--   `913000000000000000 + 租户序号*10000 + 行序号` 结构；已占用 seq 为 1/10..17/101..103。
--
-- 执行（本地/测试库直接应用；生产数据变更属 L3，由 DBA / CI 在目标库运行）：
--   mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0071_align_claim_menus.sql
-- ============================================================

USE bone;

-- ---------- 阶段 0：备份 ----------
CREATE TABLE IF NOT EXISTS bak_iam_menu_0071 AS SELECT * FROM iam_menu;

-- ---------- 阶段 1：改名（按 path 锚定；仅 type=1 叶子，不动目录与按钮） ----------
DROP TABLE IF EXISTS tmp_claim_rename_0071;
CREATE TABLE tmp_claim_rename_0071 (
    node_path VARCHAR(500) NOT NULL,
    new_name  VARCHAR(200) NOT NULL,
    PRIMARY KEY (node_path)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO tmp_claim_rename_0071 (node_path, new_name) VALUES
('/claim#/submit',   '理赔申请'),
('/claim#/progress', '理赔进度'),
('/claim#/review',   '核赔工作台'),
('/claim#/adjust',   '理算归因'),
('/claim#/evidence', '材料上传'),
('/claim#/fund',     '资金工作台'),
('/claim#/appeal',   '申诉复议'),
('/claim#/suspend',  '挂起/恢复'),
('/claim#/inspection','后督抽检');

UPDATE iam_menu m
  JOIN tmp_claim_rename_0071 r ON r.node_path = m.path
   SET m.name = r.new_name
 WHERE m.deleted = 0
   AND m.type = 1
   AND m.name <> r.new_name;

-- ---------- 阶段 1.5：按钮前缀联动（「申诉处理」→「申诉复议」，按钮名须同步） ----------
-- 按钮命名约定 `<分组>-<菜单>-<动作>`（0024 种子注释）。菜单改名而按钮不改 ⇒
-- 权限管理页新旧词汇混排，管理员授权时看不出按钮归属（check-menu-single-entry 规则 3）。
UPDATE iam_menu
   SET name = REPLACE(name, '智能理赔-申诉-', '智能理赔-申诉复议-')
 WHERE deleted = 0
   AND type = 2
   AND name LIKE '智能理赔-申诉-%';

-- ---------- 阶段 2：补齐缺失入口「后督抽检」（仅对已存在「智能理赔」目录的租户） ----------
DROP TABLE IF EXISTS tmp_claim_dir_0071;
CREATE TABLE tmp_claim_dir_0071 (
    tenant_id BIGINT NOT NULL,
    dir_id    BIGINT NOT NULL,
    PRIMARY KEY (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO tmp_claim_dir_0071 (tenant_id, dir_id)
SELECT tenant_id, MIN(id)
  FROM iam_menu
 WHERE name = '智能理赔' AND path IS NULL AND type = 0 AND deleted = 0
 GROUP BY tenant_id;

INSERT INTO iam_menu
    (id, tenant_id, name, parent_id, path, icon, order_no, permission, type, created_by, updated_by)
SELECT d.dir_id + 17, d.tenant_id, '后督抽检', d.dir_id, '/claim#/inspection',
       'SafetyCertificateOutlined', 7, 'claim:claims:read', 1, NULL, NULL
  FROM tmp_claim_dir_0071 d
 WHERE NOT EXISTS (
    SELECT 1 FROM iam_menu m
     WHERE m.tenant_id = d.tenant_id AND m.path = '/claim#/inspection' AND m.deleted = 0
 );

-- ---------- 阶段 3：排序对齐作业链路（order_no 1..9） ----------
DROP TABLE IF EXISTS tmp_claim_order_0071;
CREATE TABLE tmp_claim_order_0071 (
    node_path VARCHAR(500) NOT NULL,
    order_no  INT          NOT NULL,
    PRIMARY KEY (node_path)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO tmp_claim_order_0071 (node_path, order_no) VALUES
('/claim#/review',     1),
('/claim#/adjust',     2),
('/claim#/evidence',   3),
('/claim#/fund',       4),
('/claim#/appeal',     5),
('/claim#/suspend',    6),
('/claim#/inspection', 7),
('/claim#/submit',     8),
('/claim#/progress',   9);

UPDATE iam_menu m
  JOIN tmp_claim_order_0071 o ON o.node_path = m.path
   SET m.order_no = o.order_no
 WHERE m.deleted = 0
   AND m.type = 1
   AND m.order_no <> o.order_no;

-- ---------- 阶段 4：清理 ----------
DROP TABLE IF EXISTS tmp_claim_rename_0071;
DROP TABLE IF EXISTS tmp_claim_dir_0071;
DROP TABLE IF EXISTS tmp_claim_order_0071;

-- ---------- 阶段 5：校验（SIGNAL 防「影响 0 行却 EXIT 0」的假成功） ----------
DROP PROCEDURE IF EXISTS sp_verify_0071;
DELIMITER $$
CREATE PROCEDURE sp_verify_0071()
BEGIN
    DECLARE v_dir    INT DEFAULT 0;
    DECLARE v_leaf   INT DEFAULT 0;
    DECLARE v_drift  INT DEFAULT 0;
    DECLARE v_order  INT DEFAULT 0;
    DECLARE v_orphan INT DEFAULT 0;
    DECLARE v_dup    INT DEFAULT 0;
    DECLARE v_btn    INT DEFAULT 0;

    -- 播种范围 = 有「智能理赔」目录的租户数
    SELECT COUNT(DISTINCT tenant_id) INTO v_dir
      FROM iam_menu WHERE name = '智能理赔' AND path IS NULL AND type = 0 AND deleted = 0;

    -- 每个租户必须恰好 9 条叶子（8 条 0064 + 新增后督抽检）
    SELECT COUNT(*) INTO v_leaf
      FROM iam_menu WHERE deleted = 0 AND type = 1 AND path LIKE '/claim#/%';
    IF v_leaf <> v_dir * 9 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0071 校验失败：理赔叶子菜单未按租户×9 全覆盖';
    END IF;

    -- 名称漂移：存在「path 合法但 name 不在终态集合」的行
    SELECT COUNT(*) INTO v_drift
      FROM iam_menu m
     WHERE m.deleted = 0 AND m.type = 1 AND m.path LIKE '/claim#/%'
       AND NOT EXISTS (
            SELECT 1 FROM (
                SELECT '/claim#/review' AS p, '核赔工作台' AS n
                UNION ALL SELECT '/claim#/adjust',     '理算归因'
                UNION ALL SELECT '/claim#/evidence',   '材料上传'
                UNION ALL SELECT '/claim#/fund',       '资金工作台'
                UNION ALL SELECT '/claim#/appeal',     '申诉复议'
                UNION ALL SELECT '/claim#/suspend',    '挂起/恢复'
                UNION ALL SELECT '/claim#/inspection', '后督抽检'
                UNION ALL SELECT '/claim#/submit',     '理赔申请'
                UNION ALL SELECT '/claim#/progress',   '理赔进度'
            ) t WHERE t.p = m.path AND t.n = m.name);
    IF v_drift <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0071 校验失败：理赔菜单名称仍与 routes.tsx 不一致（存在漂移行）';
    END IF;

    -- 顺序必须覆盖 1..9 且无重复
    SELECT COUNT(DISTINCT order_no) INTO v_order
      FROM iam_menu m
      JOIN (SELECT DISTINCT tenant_id FROM iam_menu WHERE name='智能理赔' AND path IS NULL AND type=0 AND deleted=0) d
        ON d.tenant_id = m.tenant_id
     WHERE m.deleted = 0 AND m.type = 1 AND m.path LIKE '/claim#/%'
       AND m.order_no BETWEEN 1 AND 9;
    IF v_order <> 9 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0071 校验失败：理赔菜单 order_no 未覆盖 1..9';
    END IF;

    -- 不许有未挂父节点的叶子（父 = 智能理赔目录）
    SELECT COUNT(*) INTO v_orphan
      FROM iam_menu m
     WHERE m.deleted = 0 AND m.type = 1 AND m.path LIKE '/claim#/%'
       AND (m.parent_id IS NULL
            OR NOT EXISTS (SELECT 1 FROM iam_menu p
                            WHERE p.id = m.parent_id AND p.deleted = 0
                              AND p.name = '智能理赔' AND p.type = 0));
    IF v_orphan <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0071 校验失败：存在未挂在「智能理赔」目录下的叶子';
    END IF;

    -- 同租户同 path 不许重复
    SELECT COUNT(*) INTO v_dup FROM (
        SELECT tenant_id, path, COUNT(*) c FROM iam_menu
         WHERE deleted = 0 AND type = 1 AND path LIKE '/claim#/%'
         GROUP BY tenant_id, path HAVING c > 1) x;
    IF v_dup <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0071 校验失败：存在重复理赔菜单行（幂等判定失效）';
    END IF;

    -- 按钮前缀必须联动（旧前缀『智能理赔-申诉-』残留 = 改名只改了一半）
    SELECT COUNT(*) INTO v_btn
      FROM iam_menu WHERE deleted = 0 AND type = 2 AND name LIKE '智能理赔-申诉-%';
    IF v_btn <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0071 校验失败：申诉按钮前缀未随菜单改名联动';
    END IF;

    SELECT v_dir AS dir_tenants, v_leaf AS leaf_rows, v_orphan AS orphan_rows, v_dup AS dup_paths, v_btn AS stale_buttons;
END$$
DELIMITER ;

CALL sp_verify_0071();
DROP PROCEDURE IF EXISTS sp_verify_0071;
