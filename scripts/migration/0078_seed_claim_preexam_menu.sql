-- ============================================================
-- 0078_seed_claim_preexam_menu.sql
--
-- 背景
-- --------------------------------
-- 后端 `ClaimCaseController` 提供初审段四个动作：
--   POST /{id}/allocate           材料完整 → 分配初审（7→10）
--   POST /{id}/pre-exam/start     进入初审（10→11 人工 / 13 自动化）
--   POST /{id}/pre-exam/complete  初审完成并回填核定金额（11/13→12）
--   POST /{id}/escalate           转人工核赔（12→40）
-- 而前端在 2026-10-30 之前**没有任何页面调用它们**（全仓 grep 仅命中 claimApi.ts 自身，
-- 且 completePreExam 连方法都没有）⇒ 案件从「已签收」到「已初审」这一段在 UI 上不可操作，
-- 只能手工打接口。作业链路在这里断了一整段，且不会报错，只会表现为「单子推不动」。
--
-- 本次改动
-- --------------------------------
--   · 前端新增页面 `PreExamWorkbenchPage`（`bone-claim-app/src/pages/`），路由 `/claim#/pre-exam`；
--   · 本脚本为已存在「智能理赔」目录的租户补齐对应菜单叶子，并把 order_no 重排为 1..10。
--
-- 为什么必须同提交改菜单
-- --------------------------------
-- `scripts/e2e/ui/claim-single-nav-probe.mjs` 对「IAM 菜单 ↔ routes.tsx」做双向全等断言：
-- 页面有路由而菜单无入口 ⇒「功能不可达」判红；菜单有而路由无 ⇒「幽灵菜单」判红。
-- 只加页面不加菜单，探针必然失败——这正是 2026-10-28 那次「后督抽检只在子应用侧存在」的教训。
--
-- 命名与顺序口径
-- --------------------------------
--   菜单名 = 页面 H1 = shell 顶栏标题 =「初审工作台」（三处同一字符串）
--   顺序 = 作业链路：初审 → 核赔 → 理算 → 材料 → 资金 → 申诉 → 挂起 → 后督 → 理赔申请 → 理赔进度
--   与 `bone-claim-app/src/routes.tsx` 数组顺序逐项对应。
--
-- 号段
-- --------------------------------
-- 沿用 0064/0071 的 `913000000000000000 + 租户序号*10000 + 行序号`（叶子 = 目录 id + seq）。
-- 0071 已占用 seq 1/10..18/101..103；本脚本用 seq 19（= 目录 id + 18）。
--
-- 幂等
-- --------------------------------
--   · 新增按 (tenant_id, path) 判存在，不用 INSERT IGNORE（会吞约束错误伪装成功）；
--   · order_no 直接覆盖为终态值（与当前值相同也是幂等）；
--   · 结尾用 PROCEDURE + SIGNAL 校验，避免「影响 0 行却 EXIT 0」的假成功。
--
-- 执行（本地/测试库直接应用；生产数据变更属 L3，由 DBA / CI 在目标库运行）：
--   mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0078_seed_claim_preexam_menu.sql
-- ============================================================

USE bone;

-- ---------- 阶段 0：备份 ----------
CREATE TABLE IF NOT EXISTS bak_iam_menu_0078 AS SELECT * FROM iam_menu;

-- ---------- 阶段 1：定位「智能理赔」目录（每租户一条） ----------
DROP TABLE IF EXISTS tmp_claim_dir_0078;
CREATE TABLE tmp_claim_dir_0078 (
    tenant_id BIGINT NOT NULL,
    dir_id    BIGINT NOT NULL,
    PRIMARY KEY (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO tmp_claim_dir_0078 (tenant_id, dir_id)
SELECT tenant_id, MIN(id)
  FROM iam_menu
 WHERE name = '智能理赔' AND path IS NULL AND type = 0 AND deleted = 0
 GROUP BY tenant_id;

-- ---------- 阶段 2：补齐「初审工作台」菜单（幂等：同租户同 path 已存在则跳过） ----------
INSERT INTO iam_menu
    (id, tenant_id, name, parent_id, path, icon, order_no, permission, type, created_by, updated_by)
SELECT d.dir_id + 18, d.tenant_id, '初审工作台', d.dir_id, '/claim#/pre-exam',
       'SolutionOutlined', 1, 'claim:review:write', 1, NULL, NULL
  FROM tmp_claim_dir_0078 d
 WHERE NOT EXISTS (
    SELECT 1 FROM iam_menu m
     WHERE m.tenant_id = d.tenant_id AND m.path = '/claim#/pre-exam' AND m.deleted = 0
 );

-- ---------- 阶段 3：order_no 重排为 1..10（与 routes.tsx 数组顺序一致） ----------
DROP TABLE IF EXISTS tmp_claim_order_0078;
CREATE TABLE tmp_claim_order_0078 (
    node_path VARCHAR(500) NOT NULL,
    order_no  INT          NOT NULL,
    PRIMARY KEY (node_path)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO tmp_claim_order_0078 (node_path, order_no) VALUES
('/claim#/pre-exam',   1),
('/claim#/review',     2),
('/claim#/adjust',     3),
('/claim#/evidence',   4),
('/claim#/fund',       5),
('/claim#/appeal',     6),
('/claim#/suspend',    7),
('/claim#/inspection', 8),
('/claim#/submit',     9),
('/claim#/progress',  10);

UPDATE iam_menu m
  JOIN tmp_claim_order_0078 o ON o.node_path = m.path
   SET m.order_no = o.order_no
 WHERE m.deleted = 0
   AND m.type = 1
   AND m.order_no <> o.order_no;

-- ---------- 阶段 4：清理 ----------
DROP TABLE IF EXISTS tmp_claim_dir_0078;
DROP TABLE IF EXISTS tmp_claim_order_0078;

-- ---------- 阶段 5：校验（SIGNAL 防「影响 0 行却 EXIT 0」的假成功） ----------
DROP PROCEDURE IF EXISTS sp_verify_0078;
DELIMITER $$
CREATE PROCEDURE sp_verify_0078()
BEGIN
    DECLARE v_dir    INT DEFAULT 0;
    DECLARE v_leaf   INT DEFAULT 0;
    DECLARE v_miss   INT DEFAULT 0;
    DECLARE v_order  INT DEFAULT 0;
    DECLARE v_orphan INT DEFAULT 0;
    DECLARE v_dup    INT DEFAULT 0;
    DECLARE v_drift  INT DEFAULT 0;

    -- 播种范围 = 有「智能理赔」目录的租户数
    SELECT COUNT(DISTINCT tenant_id) INTO v_dir
      FROM iam_menu WHERE name = '智能理赔' AND path IS NULL AND type = 0 AND deleted = 0;

    -- 每个租户必须恰好 10 条叶子（0071 的 9 条 + 初审工作台）
    SELECT COUNT(*) INTO v_leaf
      FROM iam_menu WHERE deleted = 0 AND type = 1 AND path LIKE '/claim#/%';
    IF v_leaf <> v_dir * 10 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0078 校验失败：理赔叶子菜单未按租户×10 全覆盖';
    END IF;

    -- 初审工作台必须真实存在（防止 INSERT 被静默跳过）：
    -- 直接由 iam_menu 反查「有目录但缺 pre-exam 叶子」的租户数，不依赖已被清理的临时表
    SELECT COUNT(*) INTO v_miss
      FROM (SELECT DISTINCT tenant_id FROM iam_menu
             WHERE name = '智能理赔' AND path IS NULL AND type = 0 AND deleted = 0) d
     WHERE NOT EXISTS (
            SELECT 1 FROM iam_menu m
             WHERE m.tenant_id = d.tenant_id AND m.path = '/claim#/pre-exam' AND m.deleted = 0);
    IF v_miss > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0078 校验失败：初审工作台菜单缺失';
    END IF;

    -- 名称漂移：path 合法但 name 不在终态集合
    SELECT COUNT(*) INTO v_drift
      FROM iam_menu m
     WHERE m.deleted = 0 AND m.type = 1 AND m.path LIKE '/claim#/%'
       AND NOT EXISTS (
            SELECT 1 FROM (
                SELECT '/claim#/pre-exam' AS p, '初审工作台' AS n
                UNION ALL SELECT '/claim#/review',     '核赔工作台'
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
            SET MESSAGE_TEXT = '0078 校验失败：理赔菜单名称与 routes.tsx 不一致（存在漂移行）';
    END IF;

    -- order_no 必须覆盖 1..10 且无重复
    SELECT COUNT(DISTINCT order_no) INTO v_order
      FROM iam_menu m
      JOIN (SELECT DISTINCT tenant_id FROM iam_menu WHERE name='智能理赔' AND path IS NULL AND type=0 AND deleted=0) d
        ON d.tenant_id = m.tenant_id
     WHERE m.deleted = 0 AND m.type = 1 AND m.path LIKE '/claim#/%'
       AND m.order_no BETWEEN 1 AND 10;
    IF v_order <> 10 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0078 校验失败：理赔菜单 order_no 未覆盖 1..10';
    END IF;

    -- 不许有未挂父节点的叶子
    SELECT COUNT(*) INTO v_orphan
      FROM iam_menu m
     WHERE m.deleted = 0 AND m.type = 1 AND m.path LIKE '/claim#/%'
       AND (m.parent_id IS NULL
            OR NOT EXISTS (SELECT 1 FROM iam_menu p
                            WHERE p.id = m.parent_id AND p.deleted = 0
                              AND p.name = '智能理赔' AND p.type = 0));
    IF v_orphan <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0078 校验失败：存在未挂在「智能理赔」目录下的叶子';
    END IF;

    -- 同租户同 path 不许重复
    SELECT COUNT(*) INTO v_dup FROM (
        SELECT tenant_id, path, COUNT(*) c FROM iam_menu
         WHERE deleted = 0 AND type = 1 AND path LIKE '/claim#/%'
         GROUP BY tenant_id, path HAVING c > 1) x;
    IF v_dup <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0078 校验失败：存在重复理赔菜单行（幂等判定失效）';
    END IF;

    SELECT v_dir AS dir_tenants, v_leaf AS leaf_rows, v_orphan AS orphan_rows, v_dup AS dup_paths;
END$$
DELIMITER ;

CALL sp_verify_0078();
DROP PROCEDURE IF EXISTS sp_verify_0078;
