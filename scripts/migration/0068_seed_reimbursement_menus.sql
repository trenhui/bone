-- =============================================================================
-- 0068_seed_reimbursement_menus.sql
--
-- 目的（2026-10-28）：报销系统（expense 域）此前无任何菜单入口，功能对用户不可达
--   （「实体/后端有、前端运行时路由有、菜单无」四缺一，与 0064 理赔、0067 工作流设计器同型问题）。
--   本脚本补齐：顶级目录「报销系统」+ 2 个叶子（费用报销 / 报销建模）。
--
-- 路由说明（避免乱搞，重要）
-- -----------------
-- · 本仓库报销域以「元数据运行时实体」形式存在，无独立前端微应用（区别于智能理赔 bone-claim-app 的
--   /claim 微应用）。故菜单指向**已存在且经既有菜单验证过**的元数据运行时路由，绝不臆造 /expense#/* 路由：
--     - 费用报销 → /metadata#/runtime    （与既有「业务数据」同源路由，perm 沿用 metadata:runtime:read）
--     - 报销建模 → /metadata#/entities   （与既有「模型管理」同源路由，perm 沿用 metadata:model:read）
-- · 为什么不直接深链 ?entity=expense_reimbursement：本库当前**未执行 0037**，规范实体
--   expense_reimbursement 尚未落 meta_entity；现有报销记录为 onboarding 示例实体（expense_claim_onb*，按租户），
--   无稳定可深链的规范实体 code。臆造深链会指向不存在的实体 ⇒ 打开即空 ⇒ 反而乱搞。
--   故本脚本只用稳定的运行时/建模路由；待 0037 落库后，可把「费用报销」path 改为
--   /metadata#/runtime?entity=expense_reimbursement（属菜单配置微调，非前端改造）。
--
-- 号段
-- -----------------
-- · iam_menu id：915000000000000000 + 租户序号*10000 + 行序号（不复用 910/911/912/913/914）。
--
-- 幂等
-- -----------------
-- · 菜单按 (tenant_id, name, path) 判存在；不用 INSERT IGNORE（会吞掉约束错误伪装成已存在）。
--
-- 后置：超管 / 租户管理员需重新登录刷新 JWT scopes（权限随登录下发，不用停服）。
--
-- 执行（本地 / 测试库直接应用；生产数据变更属 L3，由 DBA / CI 在目标库运行）：
--   mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0068_seed_reimbursement_menus.sql
-- =============================================================================

USE bone;

-- ---------- 阶段 0：备份 ----------
CREATE TABLE IF NOT EXISTS bak_iam_menu_0068 AS SELECT * FROM iam_menu;

-- ---------- 阶段 1：种子行 ----------
DROP TABLE IF EXISTS tmp_menu_seed_0068;
CREATE TABLE tmp_menu_seed_0068 (
    seq          INT          NOT NULL COMMENT '行序号',
    node_name    VARCHAR(200) NOT NULL,
    node_path    VARCHAR(500) NULL,
    node_icon    VARCHAR(200) NULL,
    node_order   INT          NOT NULL DEFAULT 0,
    node_perm    VARCHAR(200) NULL,
    node_type    TINYINT      NOT NULL DEFAULT 1 COMMENT '0-目录 1-菜单',
    parent_seq   INT          NULL,
    PRIMARY KEY (seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO tmp_menu_seed_0068
    (seq, node_name, node_path, node_icon, node_order, node_perm, node_type, parent_seq) VALUES
(   1, '报销系统',   NULL,                  'TransactionOutlined',   13, NULL,                    0, NULL),
(  10, '费用报销',   '/metadata#/runtime',  'FileTextOutlined',       1, 'metadata:runtime:read',  1, 1),
(  11, '报销建模',   '/metadata#/entities', 'OrderedListOutlined',    2, 'metadata:model:read',    1, 1);

-- ---------- 阶段 2：目标租户（平台租户 0 + 全部活跃租户） ----------
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

-- ---------- 阶段 3：插入目录（type=0，顶级，parent NULL；幂等按 租户+名称+NULL path） ----------
INSERT INTO iam_menu
    (id, tenant_id, name, parent_id, path, icon, order_no, permission, type, created_by, updated_by)
SELECT
    915000000000000000 + (t.idx * 10000) + s.seq,
    t.tenant_id, s.node_name, NULL, s.node_path, s.node_icon, s.node_order, s.node_perm, s.node_type, NULL, NULL
FROM tmp_menu_seed_0068 s
JOIN tmp_seed_tenant_0068 t
WHERE s.node_type = 0
  AND NOT EXISTS (
    SELECT 1 FROM iam_menu m
    WHERE m.tenant_id = t.tenant_id AND m.name = s.node_name
      AND IFNULL(m.path, '\0') = IFNULL(s.node_path, '\0') AND m.deleted = 0
);

-- ---------- 阶段 4：解析「报销系统」目录 id ----------
DROP TABLE IF EXISTS tmp_menu_group_0068;
CREATE TABLE tmp_menu_group_0068 AS
SELECT t.idx AS idx, p.id AS parent_id
  FROM tmp_seed_tenant_0068 t
  JOIN iam_menu p
    ON p.tenant_id = t.tenant_id
   AND p.name = '报销系统' AND p.path IS NULL AND p.type = 0 AND p.deleted = 0;

-- ---------- 阶段 5：插入叶子菜单（挂目录） ----------
INSERT INTO iam_menu
    (id, tenant_id, name, parent_id, path, icon, order_no, permission, type, created_by, updated_by)
SELECT
    915000000000000000 + (t.idx * 10000) + s.seq,
    t.tenant_id, s.node_name, g.parent_id, s.node_path, s.node_icon, s.node_order, s.node_perm, s.node_type, NULL, NULL
FROM tmp_menu_seed_0068 s
JOIN tmp_seed_tenant_0068 t
JOIN tmp_menu_group_0068 g ON g.idx = t.idx
WHERE s.node_type = 1
  AND NOT EXISTS (
    SELECT 1 FROM iam_menu m
    WHERE m.tenant_id = t.tenant_id AND m.name = s.node_name
      AND IFNULL(m.path, '\0') = IFNULL(s.node_path, '\0') AND m.deleted = 0
  );

-- ---------- 阶段 6：清理 ----------
DROP TABLE IF EXISTS tmp_menu_seed_0068;
DROP TABLE IF EXISTS tmp_seed_tenant_0068;
DROP TABLE IF EXISTS tmp_menu_group_0068;

-- ---------- 阶段 7：校验（SIGNAL 防「影响 0 行却 EXIT 0」的假成功） ----------
DROP PROCEDURE IF EXISTS sp_verify_0068;
DELIMITER $$
CREATE PROCEDURE sp_verify_0068()
BEGIN
    DECLARE v_dir  INT DEFAULT 0;
    DECLARE v_menu INT DEFAULT 0;
    DECLARE v_orphan INT DEFAULT 0;
    DECLARE v_dup  INT DEFAULT 0;

    -- 播种范围 = 活跃租户（含平台 0）的「报销系统」目录数
    SELECT COUNT(DISTINCT tenant_id) INTO v_dir
      FROM iam_menu WHERE name='报销系统' AND path IS NULL AND type=0 AND deleted=0
       AND (tenant_id = 0 OR EXISTS (SELECT 1 FROM iam_tenant t WHERE t.id = iam_menu.tenant_id AND t.deleted = 0));

    -- 叶子按 (name, path) 精确计数（/metadata#/runtime 与既有「业务数据」共享同 path，靠 name 区分）
    SELECT COUNT(*) INTO v_menu
      FROM iam_menu
     WHERE deleted = 0
       AND ((name='费用报销'   AND path='/metadata#/runtime')
         OR (name='报销建模'   AND path='/metadata#/entities'));
    IF v_menu <> v_dir * 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0068 校验失败：报销叶子菜单未按租户×2 全覆盖';
    END IF;

    SELECT COUNT(*) INTO v_orphan
      FROM iam_menu
     WHERE deleted = 0
       AND ((name='费用报销' AND path='/metadata#/runtime')
         OR (name='报销建模' AND path='/metadata#/entities'))
       AND parent_id IS NULL;
    IF v_orphan <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0068 校验失败：存在未挂父节点的报销菜单';
    END IF;

    SELECT COUNT(*) INTO v_dup FROM (
        SELECT tenant_id, name, IFNULL(path,'\0') p, COUNT(*) c FROM iam_menu
         WHERE deleted=0 AND (name='报销系统'
            OR (name='费用报销' AND path='/metadata#/runtime')
            OR (name='报销建模' AND path='/metadata#/entities'))
         GROUP BY tenant_id, name, p HAVING c > 1) d;
    IF v_dup <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0068 校验失败：存在重复菜单行（幂等判定失效）';
    END IF;

    SELECT v_dir AS dir_tenants, v_menu AS leaf_rows, v_orphan AS orphan_rows;
END$$
DELIMITER ;

CALL sp_verify_0068();
DROP PROCEDURE IF EXISTS sp_verify_0068;
