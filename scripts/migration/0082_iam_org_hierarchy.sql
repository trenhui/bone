-- ============================================================
-- 0082_iam_org_hierarchy.sql
-- IAM 组织层级：账户直属主管 + 部门负责人。
--
-- 【为什么需要这两列】
--   审批流的 LINE_MANAGER / DEPT_HEAD / LEVEL_UP 三个指派策略在引擎里早已实现
--   （bone-workflow-spring-boot-starter 的 RecordFieldApproverResolver），但它们的数据源
--   ——「谁向谁汇报」与「部门向谁负责」——平台一直没有建模，于是这三个策略恒不可用。
--   后果不是报错，而是**静默的质量损失**：报销流程里「直属主管审批」与「部门负责人审批」
--   两个语义不同的节点只能共用单据上的 approver_id，实际解析成同一个人，
--   分级审批退化为「一个人连审两级」。
--
-- 【为什么不是从部门树推导】
--   部门树只表达「谁和谁同部门」，推不出汇报线：同部门内平级同事互相不审批，
--   而跨部门的虚线汇报（项目组、派驻）真实存在。两条线必须独立存储。
--
-- 【两列都可空，且空值是合法状态】
--   创始人/CEO 没有上级；新部门可能暂未指定负责人。引擎拿到空会**挂起**实例并通知管理员
--   （fail-closed），绝不会跳过审批节点。用 '' / 0 之类的哨兵把「没有」伪装成「有」
--   会导致单据静默通过，是审批系统最严重的故障形态。
--
-- 【幂等性】
--   建列走 information_schema 探测；回填全部带 IS NULL 守卫，可重复执行。
-- ============================================================

-- ---------- ① iam_account.manager_id（直属主管） ----------
SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE iam_account ADD COLUMN manager_id BIGINT DEFAULT NULL COMMENT ''直属主管账户ID（审批流 LINE_MANAGER 的数据源，跨聚合只存ID不级联）'' AFTER dept_id',
    'DO 0'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'iam_account' AND COLUMN_NAME = 'manager_id'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE iam_account ADD INDEX idx_iam_account_manager (manager_id)',
    'DO 0'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'iam_account' AND INDEX_NAME = 'idx_iam_account_manager'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- ② iam_dept.leader_id（部门负责人） ----------
SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE iam_dept ADD COLUMN leader_id BIGINT DEFAULT NULL COMMENT ''部门负责人账户ID（审批流 DEPT_HEAD 的数据源，跨聚合只存ID不级联）'' AFTER parent_id',
    'DO 0'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'iam_dept' AND COLUMN_NAME = 'leader_id'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE iam_dept ADD INDEX idx_iam_dept_leader (leader_id)',
    'DO 0'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'iam_dept' AND INDEX_NAME = 'idx_iam_dept_leader'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- ③ 平台演示组织回填（tenant 0）
--
-- 目的：让「直属主管」与「部门负责人」在演示环境里是**两个不同的人**，
--       从而能真实演示分级审批，而不是又变成一个字段换两种叫法。
--
--   admin(1)   ──主管──▶ 9103   （平台冒烟管理员）
--   9105       ──主管──▶ 9104   （平台冒烟审批人）
--   研发一部    ──负责人──▶ 9104
--   9103/9104  ──主管──▶ NULL   （顶层，无上级）
--
-- 只填 NULL 的格子，不覆盖任何已有值的租户配置。
-- ============================================================
UPDATE iam_dept
   SET leader_id = 9104
 WHERE tenant_id = 0 AND deleted = 0
   AND name = '研发一部'
   AND leader_id IS NULL;

UPDATE iam_account
   SET manager_id = 9103
 WHERE id = 1 AND deleted = 0 AND manager_id IS NULL;

UPDATE iam_account
   SET manager_id = 9104
 WHERE id = 9105 AND deleted = 0 AND manager_id IS NULL;

-- admin 与三位冒烟审批人挂到演示部门下（原本 dept_id 全为空，
-- 没有部门就无法解析「部门负责人」，DEPT_HEAD 节点会挂起）
UPDATE iam_account
   SET dept_id = (SELECT id FROM (SELECT id FROM iam_dept WHERE tenant_id = 0 AND deleted = 0 AND name = '研发一部' LIMIT 1) t)
 WHERE tenant_id = 0 AND deleted = 0
   AND id IN (1, 9103, 9104, 9105)
   AND dept_id IS NULL;

-- ============================================================
-- ④ 默认租户组织回填（tenant 1001）—— 按部门自动推导
--
-- 规则（生产环境应由 HR 系统导入真实汇报线，这里只为演示租户给出可用基线）：
--   1) 每个部门取本部门 id 最小的账号作为负责人；
--   2) 该部门的其余成员，直属主管 = 本部门负责人。
--
-- 「取 id 最小」是确定性规则而非业务真理：它保证同一次迁移在任何环境下结果一致，
-- 且不依赖人工逐条指定 60 个部门。它是**占位数据**，实施时应被真实 HR 数据覆盖。
-- ============================================================

-- 4.1 部门负责人
UPDATE iam_dept d
  JOIN (
        SELECT a.dept_id, MIN(a.id) AS leader_id
          FROM iam_account a
         WHERE a.tenant_id = 1001 AND a.deleted = 0 AND a.dept_id IS NOT NULL AND a.status = 1
         GROUP BY a.dept_id
       ) pick ON pick.dept_id = d.id
   SET d.leader_id = pick.leader_id
 WHERE d.tenant_id = 1001 AND d.deleted = 0 AND d.leader_id IS NULL;

-- 4.2 成员的主管 = 所在部门负责人（负责人自己没有上级，保持 NULL ⇒ 引擎在顶层挂起而非放行）
UPDATE iam_account a
  JOIN iam_dept d ON d.id = a.dept_id
   SET a.manager_id = d.leader_id
 WHERE a.tenant_id = 1001 AND a.deleted = 0
   AND a.manager_id IS NULL
   AND d.leader_id IS NOT NULL
   AND a.id <> d.leader_id;

-- ============================================================
-- ⑤ 校验（有输出即异常，请在迁移日志里核对）
-- ============================================================
SELECT '=== 0082 校验：列是否就位 ===' AS section;
SELECT TABLE_NAME AS tbl, COLUMN_NAME AS col, IS_NULLABLE, COLUMN_TYPE
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND ( TABLE_NAME = 'iam_account' AND COLUMN_NAME = 'manager_id'
      OR TABLE_NAME = 'iam_dept'    AND COLUMN_NAME = 'leader_id' );

SELECT '=== 0082 校验：回填覆盖率（tenant 0）===' AS section;
SELECT
  (SELECT COUNT(*) FROM iam_account WHERE tenant_id = 0 AND deleted = 0) AS accounts,
  (SELECT COUNT(*) FROM iam_account WHERE tenant_id = 0 AND deleted = 0 AND manager_id IS NOT NULL) AS with_manager,
  (SELECT COUNT(*) FROM iam_dept   WHERE tenant_id = 0 AND deleted = 0) AS depts,
  (SELECT COUNT(*) FROM iam_dept   WHERE tenant_id = 0 AND deleted = 0 AND leader_id IS NOT NULL) AS with_leader;

SELECT '=== 0082 校验：回填覆盖率（tenant 1001）===' AS section;
SELECT
  (SELECT COUNT(*) FROM iam_account WHERE tenant_id = 1001 AND deleted = 0) AS accounts,
  (SELECT COUNT(*) FROM iam_account WHERE tenant_id = 1001 AND deleted = 0 AND manager_id IS NOT NULL) AS with_manager,
  (SELECT COUNT(*) FROM iam_dept   WHERE tenant_id = 1001 AND deleted = 0) AS depts,
  (SELECT COUNT(*) FROM iam_dept   WHERE tenant_id = 1001 AND deleted = 0 AND leader_id IS NOT NULL) AS with_leader;

SELECT '=== 0082 校验：自环（应为 0 行）===' AS section;
SELECT id, username FROM iam_account WHERE deleted = 0 AND manager_id = id;

SELECT '=== 0082 校验：跨租户串线（应为 0 行）===' AS section;
SELECT a.id, a.username, a.tenant_id AS account_tenant, m.tenant_id AS manager_tenant
  FROM iam_account a JOIN iam_account m ON m.id = a.manager_id
 WHERE a.deleted = 0 AND m.deleted = 0 AND a.tenant_id <> m.tenant_id;

SELECT '=== 0082 校验：部门负责人与部门不同租户（应为 0 行）===' AS section;
SELECT d.id, d.name, d.tenant_id AS dept_tenant, a.tenant_id AS leader_tenant
  FROM iam_dept d JOIN iam_account a ON a.id = d.leader_id
 WHERE d.deleted = 0 AND a.deleted = 0 AND d.tenant_id <> a.tenant_id;
