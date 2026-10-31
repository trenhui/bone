-- ============================================================
-- 0084_org_leader_reporting_line.sql
-- 补齐「部门负责人向谁汇报」——0082 回填留下的结构性空洞。
--
-- 【问题（实测，不是推测）】
--   0082 的回填规则是「每个部门取 id 最小的账号当负责人；其余成员的主管 = 本部门负责人」，
--   并刻意让负责人自己的 manager_id 保持 NULL（注释写的是「负责人没有上级」）。
--   在真实的三层部门树上，这个假设是错的：科室负责人要向事业部负责人汇报。
--
--   后果被 scripts/audit-org-coverage.py 抓出来（该脚本以 wf_instance.started_by 为分母）：
--   租户 1001 的 24 个真实发起人里，10 个是部门负责人，他们在 v3 定义下
--     · expense_reimbursement/mgr  (LINE_MANAGER) → manager_id 为空 → 挂起
--     · expense_reimbursement/dept (DEPT_HEAD)    → 解析到本人（自审批）→ 挂起
--   即 42% 的报销提交会直接卡死。
--
-- 【为什么必须两条一起修】
--   只补 manager_id：DEPT_HEAD 仍然解析到本人，dept 节点照样挂（该节点取的是部门负责人，不是主管）。
--   只改解析器「自审批上溯」：上溯目标就是 manager_id，它为空时上溯无处可去，仍然挂。
--   数据侧（本脚本）+ 解析侧（RecordFieldApproverResolver 的自审批上溯）互为前提。
--
-- 【规则】
--   ① 部门负责人的主管 = 其父部门的负责人（真实组织语义：科长→部长→总经理）。
--   ② 普通成员的主管 = 本部门负责人（0082 只对 tenant 1001 做了，tenant 0 漏了，这里统一补）。
--   ③ 根部门（parent_id 为空/0）的负责人保持 NULL —— 他确实没有上级，这不是数据缺失。
--      引擎对他会 fail-closed 挂起，由管理员 assignTask 特批，这是正确结局。
--
-- 【幂等】全部带 manager_id IS NULL 守卫；无父部门 / 父部门无负责人时自然不匹配，可重复执行。
-- ============================================================

-- ---------- ⓿ 根部门无负责人时，取其直接子部门负责人中 id 最小者 ----------
-- 为什么需要这一步：整棵树只有根（公司）没有负责人，而所有一级事业部都挂在根下。
-- 根缺人 ⇒ 一级事业部负责人的 manager_id 永远补不上 ⇒ 他们发单时 LINE_MANAGER 仍然空。
-- 实测就是卡在这一步：只跑 ① 之后，11053 / 11026 的负责人依旧无主管。
--
-- 规则取 MIN(leader_id) 是确定性的**占位**，不是业务真理（0082 对部门负责人用的也是同一套
-- MIN(id) 约定）；实施时应由 HR 数据覆盖为真实 CEO。
-- 只对根部门生效，且必须有已设负责人的子部门——否则宁可为空，不臆造。
UPDATE iam_dept d
   SET d.leader_id = (
     SELECT MIN(x.leader_id) FROM iam_dept x
      WHERE x.parent_id = d.id AND x.deleted = 0 AND x.leader_id IS NOT NULL
   )
 WHERE d.deleted = 0
   AND d.leader_id IS NULL
   AND (d.parent_id IS NULL OR d.parent_id = 0)
   AND EXISTS (
     SELECT 1 FROM iam_dept x
      WHERE x.parent_id = d.id AND x.deleted = 0 AND x.leader_id IS NOT NULL
   );

-- ---------- ① 部门负责人 → 父部门负责人 ----------
-- 不加租户条件也安全（dept_id / leader_id 都是全局唯一雪花），但仍显式限定同租户，
-- 防止将来出现跨租户引用时静默串线。
UPDATE iam_account a
  JOIN iam_dept d ON d.id = a.dept_id AND d.deleted = 0 AND d.tenant_id = a.tenant_id
  JOIN iam_dept p ON p.id = d.parent_id AND p.deleted = 0 AND p.tenant_id = a.tenant_id
   SET a.manager_id = p.leader_id
 WHERE a.deleted = 0
   AND a.manager_id IS NULL
   AND d.leader_id = a.id          -- 本人确实是本部门负责人
   AND p.leader_id IS NOT NULL     -- 父部门有负责人
   AND p.leader_id <> a.id;        -- 不是自己（根部门负责人会被这一条挡下，保持 NULL）

-- ---------- ② 普通成员 → 本部门负责人（补 tenant 0 的遗漏） ----------
UPDATE iam_account a
  JOIN iam_dept d ON d.id = a.dept_id AND d.deleted = 0 AND d.tenant_id = a.tenant_id
   SET a.manager_id = d.leader_id
 WHERE a.deleted = 0
   AND a.manager_id IS NULL
   AND d.leader_id IS NOT NULL
   AND a.id <> d.leader_id;

-- ============================================================
-- ③ 校验（有输出即需人工核对）
-- ============================================================
SELECT '=== 0084 校验：回填后覆盖率 ===' AS section;
SELECT a.tenant_id,
       COUNT(*)                                                        AS accounts,
       SUM(a.manager_id IS NOT NULL)                                   AS with_manager,
       SUM(a.manager_id IS NULL AND a.dept_id IS NULL)                 AS no_dept_at_all,
       SUM(a.manager_id IS NULL AND a.dept_id IS NOT NULL)             AS has_dept_but_no_manager
  FROM iam_account a
 WHERE a.deleted = 0
 GROUP BY a.tenant_id
 ORDER BY a.tenant_id;

SELECT '=== 0084 校验：仍无主管的部门负责人（应只剩根部门负责人）===' AS section;
SELECT d.tenant_id, d.id AS dept_id, d.name AS dept, IFNULL(d.parent_id, 0) AS parent_id,
       a.id AS leader_id, a.username
  FROM iam_dept d
  JOIN iam_account a ON a.id = d.leader_id AND a.deleted = 0
 WHERE d.deleted = 0
   AND a.manager_id IS NULL
 ORDER BY d.tenant_id, d.id;

SELECT '=== 0084 校验：自环（应为 0 行）===' AS section;
SELECT id, username FROM iam_account WHERE deleted = 0 AND manager_id = id;

SELECT '=== 0084 校验：跨租户串线（应为 0 行）===' AS section;
SELECT a.id, a.username, a.tenant_id AS account_tenant, m.tenant_id AS manager_tenant
  FROM iam_account a JOIN iam_account m ON m.id = a.manager_id
 WHERE a.deleted = 0 AND m.deleted = 0 AND a.tenant_id <> m.tenant_id;

SELECT '=== 0084 校验：汇报链是否成环（应无 2 行以上互指）===' AS section;
SELECT a.id, a.username, a.manager_id, m.username AS manager_name, m.manager_id AS manager_of_manager
  FROM iam_account a JOIN iam_account m ON m.id = a.manager_id AND m.deleted = 0
 WHERE a.deleted = 0 AND m.manager_id = a.id
 ORDER BY a.tenant_id, a.id;
