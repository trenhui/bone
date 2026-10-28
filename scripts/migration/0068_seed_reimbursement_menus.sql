-- =============================================================================
-- 0068_seed_reimbursement_menus.sql
--
-- 目的（2026-10-28，v2）：报销系统（expense 域）无任何菜单入口 ⇒ 业务用户不可达。
--   本脚本补齐「报销系统」目录与其**运行时页面**叶子菜单。
--
-- v2 修订（重要，纠正 v1 的口径错误）
-- -----------------
-- · v1 把「费用报销」挂到 /metadata#/runtime。那是**跨实体数据运营台**（页面自带实体选择器，
--   是给建模/运维用的后台入口，见 RuntimeDataManagement 页头「业务数据 · 跨实体数据运营台」），
--   不是业务用户用的报销单页面。菜单给终端用户，必须挂**该实体自己的运行时页面**：
--       /metadata#/entities/{实体ID}/data   （RuntimeDataManagement 嵌入模式：已锁定实体，无选择器）
-- · 实体 ID 随环境/租户而变 ⇒ 本脚本不写死，按 (tenant_id, code) 从 meta_entity 现算 path；
--   算不出实体的租户**不插菜单**（宁缺不挂死链）。平台租户 0 无实体时回退取 tenant 0 的平台实体。
-- · v1 的「报销建模」叶子与既有「业务建模 → 业务对象」（/metadata#/entities）重复，本脚本删除该叶子，
--   不再复制一份后台入口。
--
-- 权限点口径（为什么菜单不挂 runtime:{entity}:*）
-- -----------------
-- · 菜单 permission 是**页面可达性**闸门，与「数据权限」分层：现有 metadata 域菜单
--   （业务数据 / 审批中心 / 模型设计 / 业务对象）一律挂 metadata:* 码，无一用 runtime:{entity}:*。
--   本脚本沿用该约定挂 metadata:runtime:read。
-- · 记录级读写仍由服务端 runtime:expense_reimbursement:read/write、runtime:expense_category:read/write
--   在 API 层判定（fail-closed）。菜单可见 ≠ 能改数据。
-- · 实测反例：挂 runtime:expense_category:read 时，平台管理员的 JWT scopes 里没有该精确码
--   （只有 runtime:*:read 通配 + 已缓存的角色码集合），`menus/current` 是 `scopes.contains(code)`
--   精确匹配 ⇒ 菜单整条不下发。用页面级码既符合约定，也不依赖通配展开。
--
-- 前置
-- -----------------
-- · 0037_runtime_expense_l1_relations.sql 已执行（提供 expense_reimbursement / expense_category
--   两个 delivery_mode=1 RUNTIME、status=1 PUBLISHED 的运行时实体）。缺实体 ⇒ 该租户跳过并在末尾报告。
--
-- 号段
-- -----------------
-- · iam_menu id：915000000000000000 + 租户序号*10000 + 行序号（独占 915 号段，不复用 910~914）。
-- · 幂等：按主键 ON DUPLICATE KEY UPDATE ⇒ 重跑就地纠正 path/权限/图标（含 v1→v2 的 path 迁移）。
--
-- 后置：菜单按权限码过滤下发，改完需**重新登录**刷新 JWT scopes（不用停服）。
--
-- 执行（本地/测试库直接应用；生产数据变更属 L3，由 DBA / CI 在目标库运行）：
--   mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0068_seed_reimbursement_menus.sql
-- =============================================================================

USE bone;

-- ---------- 阶段 0：备份 ----------
CREATE TABLE IF NOT EXISTS bak_iam_menu_0068 AS SELECT * FROM iam_menu;

-- ---------- 阶段 1：种子定义（path 留空，阶段 4 现算） ----------
DROP TABLE IF EXISTS tmp_menu_seed_0068;
CREATE TABLE tmp_menu_seed_0068 (
    seq          INT          NOT NULL COMMENT '行序号',
    node_name    VARCHAR(200) NOT NULL,
    entity_code  VARCHAR(200) NULL COMMENT '运行时实体 code（叶子按它解析实体 ID 拼 path；目录为 NULL）',
    node_icon    VARCHAR(200) NULL,
    node_order   INT          NOT NULL DEFAULT 0,
    node_perm    VARCHAR(200) NULL,
    node_type    TINYINT      NOT NULL DEFAULT 1 COMMENT '0-目录 1-菜单',
    parent_seq   INT          NULL,
    PRIMARY KEY (seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO tmp_menu_seed_0068
    (seq, node_name, entity_code, node_icon, node_order, node_perm, node_type, parent_seq) VALUES
(  1, '报销系统',   NULL,                   'TransactionOutlined',   13, NULL,                 0, NULL),
( 10, '费用报销单', 'expense_reimbursement', 'ReconciliationOutlined', 1, 'metadata:runtime:read', 1, 1),
( 11, '费用类别',   'expense_category',      'ProfileOutlined',        2, 'metadata:runtime:read', 1, 1);

-- ---------- 阶段 2：目标租户（活跃租户，含平台 0） ----------
DROP TABLE IF EXISTS tmp_seed_tenant_0068;
CREATE TABLE tmp_seed_tenant_0068 (
    idx       INT   NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO tmp_seed_tenant_0068 (idx, tenant_id)
SELECT 0, 0
UNION ALL
SELECT ROW_NUMBER() OVER (ORDER BY id), id
  FROM iam_tenant
 WHERE deleted = 0 AND id <> 0;

-- ---------- 阶段 3：解析每个（租户 × 叶子）的运行时 URL ----------
-- · 本租户实体优先；无则回退平台租户 0 的实体（scope=PLATFORM 共享场景）。
-- · 二者皆无 ⇒ entity_id 为 NULL ⇒ 阶段 6 不插该行（不挂死链）。
DROP TABLE IF EXISTS tmp_menu_path_0068;
CREATE TABLE tmp_menu_path_0068 (
    idx         INT          NOT NULL,
    seq         INT          NOT NULL,
    entity_id   BIGINT       NULL,
    node_path   VARCHAR(500) NULL,
    PRIMARY KEY (idx, seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO tmp_menu_path_0068 (idx, seq, entity_id, node_path)
SELECT
    t.idx,
    s.seq,
    e.id,
    CASE WHEN e.id IS NULL THEN NULL ELSE CONCAT('/metadata#/entities/', e.id, '/data') END
FROM tmp_seed_tenant_0068 t
CROSS JOIN tmp_menu_seed_0068 s
LEFT JOIN meta_entity e
       ON e.code = s.entity_code
      AND e.deleted = 0
      AND e.delivery_mode = 1                      -- RUNTIME：发布即得运行时 CRUD 页面
      AND e.status = 1                             -- PUBLISHED
      AND e.tenant_id = COALESCE(
            (SELECT e2.tenant_id FROM meta_entity e2
              WHERE e2.code = s.entity_code AND e2.deleted = 0
                AND e2.delivery_mode = 1 AND e2.status = 1 AND e2.tenant_id = t.tenant_id
              LIMIT 1),
            0)
WHERE s.node_type = 1;

-- ---------- 阶段 3.5：清理本脚本管辖的 915 号段中「已不应存在」的行 ----------
-- 目的：脚本口径变更（v1→v2）或实体消失时，旧行必须退役，否则会留下：
--   ① v1 的「费用报销 → /metadata#/runtime」（后台运营台，不是业务入口）
--   ② v1 的「报销建模 → /metadata#/entities」（与「业务对象」重复）
--   ③ 该租户已无实体的空壳目录 / 死链叶子
-- 只动 915 号段（本脚本独占），不碰 910/911/912/913/914 等他人号段。
DELETE FROM iam_menu
 WHERE id BETWEEN 915000000000000000 AND 915999999999999999
   AND ((name = '费用报销' AND path = '/metadata#/runtime')
     OR (name = '报销建模' AND path = '/metadata#/entities'));

DELETE m
  FROM iam_menu m
  JOIN tmp_seed_tenant_0068 t
    ON m.tenant_id = t.tenant_id
 WHERE m.id BETWEEN 915000000000000000 AND 915999999999999999
   AND m.name IN ('报销系统', '费用报销单', '费用类别')
   AND NOT EXISTS (SELECT 1 FROM tmp_menu_path_0068 p WHERE p.idx = t.idx AND p.entity_id IS NOT NULL);

-- ---------- 阶段 4：插入/纠正目录（type=0，顶级，parent NULL） ----------
-- 只为「至少解析出一个运行时实体」的租户建目录：无实体的租户宁可不显示，也不给空壳目录。
INSERT INTO iam_menu
    (id, tenant_id, name, parent_id, path, icon, order_no, permission, type, created_by, updated_by)
SELECT
    915000000000000000 + (t.idx * 10000) + s.seq,
    t.tenant_id, s.node_name, NULL, NULL, s.node_icon, s.node_order, s.node_perm, s.node_type, NULL, NULL
FROM tmp_menu_seed_0068 s
JOIN tmp_seed_tenant_0068 t
WHERE s.node_type = 0
  AND EXISTS (SELECT 1 FROM tmp_menu_path_0068 p WHERE p.idx = t.idx AND p.entity_id IS NOT NULL)
ON DUPLICATE KEY UPDATE
    name = VALUES(name), path = NULL, icon = VALUES(icon), order_no = VALUES(order_no),
    permission = VALUES(permission), parent_id = NULL, deleted = 0, updated_at = NOW(3);

-- ---------- 阶段 5：解析「报销系统」目录 id ----------
DROP TABLE IF EXISTS tmp_menu_group_0068;
CREATE TABLE tmp_menu_group_0068 AS
SELECT t.idx AS idx, p.id AS parent_id
  FROM tmp_seed_tenant_0068 t
  JOIN iam_menu p
    ON p.tenant_id = t.tenant_id
   AND p.id = 915000000000000000 + (t.idx * 10000) + 1
   AND p.deleted = 0;

-- ---------- 阶段 6：插入/纠正叶子菜单（挂目录，path = 实体运行时页面） ----------
INSERT INTO iam_menu
    (id, tenant_id, name, parent_id, path, icon, order_no, permission, type, created_by, updated_by)
SELECT
    915000000000000000 + (t.idx * 10000) + s.seq,
    t.tenant_id, s.node_name, g.parent_id, p.node_path, s.node_icon, s.node_order, s.node_perm, s.node_type, NULL, NULL
FROM tmp_menu_seed_0068 s
JOIN tmp_seed_tenant_0068 t
JOIN tmp_menu_group_0068 g ON g.idx = t.idx
JOIN tmp_menu_path_0068 p  ON p.idx = t.idx AND p.seq = s.seq
WHERE s.node_type = 1
  AND p.node_path IS NOT NULL
ON DUPLICATE KEY UPDATE
    name = VALUES(name), path = VALUES(path), icon = VALUES(icon), order_no = VALUES(order_no),
    permission = VALUES(permission), parent_id = VALUES(parent_id), deleted = 0, updated_at = NOW(3);

-- ---------- 阶段 7：清理临时表 ----------
DROP TABLE IF EXISTS tmp_menu_seed_0068;
DROP TABLE IF EXISTS tmp_seed_tenant_0068;
DROP TABLE IF EXISTS tmp_menu_path_0068;
DROP TABLE IF EXISTS tmp_menu_group_0068;

-- ---------- 阶段 9：校验（SIGNAL 防「影响 0 行却 EXIT 0」的假成功） ----------
DROP PROCEDURE IF EXISTS sp_verify_0068;
DELIMITER $$
CREATE PROCEDURE sp_verify_0068()
BEGIN
    DECLARE v_expect   INT DEFAULT 0;   -- 有实体的租户数（= 应插目录数）
    DECLARE v_dir      INT DEFAULT 0;
    DECLARE v_leaf     INT DEFAULT 0;
    DECLARE v_badpath  INT DEFAULT 0;   -- path 未指向该租户实体的运行时页面
    DECLARE v_orphan   INT DEFAULT 0;
    DECLARE v_stale    INT DEFAULT 0;   -- v1 遗留（/metadata#/runtime 或 报销建模）

    -- 期望租户数：平台租户 0（若有平台实体）+ 各活跃租户（自有实体或回退平台实体）
    -- 口径必须与阶段 3 的解析逻辑一致，否则会出现「插了菜单却判失败」的假红。
    SELECT
        (CASE WHEN EXISTS (
            SELECT 1 FROM meta_entity e
             WHERE e.tenant_id = 0 AND e.code IN ('expense_reimbursement','expense_category')
               AND e.deleted = 0 AND e.delivery_mode = 1 AND e.status = 1) THEN 1 ELSE 0 END)
      + (SELECT COUNT(*) FROM iam_tenant t
          WHERE t.deleted = 0 AND t.id <> 0
            AND EXISTS (SELECT 1 FROM meta_entity e
                         WHERE e.code IN ('expense_reimbursement','expense_category')
                           AND e.deleted = 0 AND e.delivery_mode = 1 AND e.status = 1
                           AND (e.tenant_id = t.id OR e.tenant_id = 0)))
      INTO v_expect;

    SELECT COUNT(*) INTO v_dir
      FROM iam_menu
     WHERE name = '报销系统' AND type = 0 AND deleted = 0
       AND (tenant_id = 0 OR EXISTS (SELECT 1 FROM iam_tenant t WHERE t.id = iam_menu.tenant_id AND t.deleted = 0));

    IF v_dir <> v_expect OR v_dir = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0068 校验失败：报销系统目录数与有实体的租户数不一致（或为 0）';
    END IF;

    -- 叶子必须挂目录 + path 精确等于该租户实体 id 的运行时页面
    SELECT COUNT(*) INTO v_badpath
      FROM iam_menu m
      JOIN iam_menu p ON p.id = m.parent_id AND p.name = '报销系统' AND p.deleted = 0
     WHERE m.deleted = 0 AND m.name IN ('费用报销单','费用类别')
       AND NOT EXISTS (
            SELECT 1 FROM meta_entity e
             WHERE e.code = CASE m.name WHEN '费用报销单' THEN 'expense_reimbursement' ELSE 'expense_category' END
               AND e.deleted = 0 AND e.delivery_mode = 1 AND e.status = 1
               AND e.tenant_id IN (m.tenant_id, 0)
               AND m.path = CONCAT('/metadata#/entities/', e.id, '/data')
       );
    IF v_badpath <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0068 校验失败：存在 path 未指向本租户实体运行时页面的报销菜单';
    END IF;

    SELECT COUNT(*) INTO v_leaf
      FROM iam_menu m
      JOIN iam_menu p ON p.id = m.parent_id AND p.name = '报销系统' AND p.deleted = 0
     WHERE m.deleted = 0 AND m.name IN ('费用报销单','费用类别')
       AND m.path LIKE '/metadata#/entities/%/data';
    -- 每个目录至少一个运行时叶子（防空壳目录），且叶子总数不得少于目录数
    IF v_leaf < v_dir
       OR (SELECT COUNT(DISTINCT m.parent_id)
             FROM iam_menu m
             JOIN iam_menu p ON p.id = m.parent_id AND p.name = '报销系统' AND p.deleted = 0
            WHERE m.deleted = 0 AND m.name IN ('费用报销单','费用类别')) <> v_dir THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0068 校验失败：报销运行时叶子菜单缺失（存在空壳目录或无叶子的租户）';
    END IF;

    SELECT COUNT(*) INTO v_orphan
      FROM iam_menu
     WHERE deleted = 0 AND name IN ('费用报销单','费用类别')
       AND (parent_id IS NULL
            OR parent_id NOT IN (SELECT id FROM iam_menu WHERE name = '报销系统' AND deleted = 0));
    IF v_orphan <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0068 校验失败：存在未挂到「报销系统」的叶子菜单';
    END IF;

    SELECT COUNT(*) INTO v_stale
      FROM iam_menu
     WHERE deleted = 0
       AND ((name = '报销建模' AND path = '/metadata#/entities')
         OR (name = '费用报销' AND path = '/metadata#/runtime'));
    IF v_stale <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0068 校验失败：v1 遗留菜单未清理（报销建模 / 费用报销→/metadata#/runtime）';
    END IF;

    SELECT v_expect AS expect_tenants, v_dir AS dir_rows, v_leaf AS leaf_rows,
           v_badpath AS bad_path, v_orphan AS orphan_rows, v_stale AS stale_rows;
END$$
DELIMITER ;

CALL sp_verify_0068();
DROP PROCEDURE IF EXISTS sp_verify_0068;
