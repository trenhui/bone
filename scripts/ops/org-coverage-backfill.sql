-- =============================================================================
-- org-coverage-backfill.sql  —— 组织数据覆盖率回填模板（D2 决策配套）
--
-- ⚠️ 本文件**不是迁移脚本**，放在 scripts/ops/ 而非 scripts/migration/：
--    不进入迁移清单、不被 CI 自动执行。由 DBA / 运维**按租户参数化**显式运行。
--
-- 用途：把某个**真实生产租户**的组织数据补齐到「可审批」基线，使审批流不再因
--      组织数据缺失而 fail-closed 挂起。对应 ADR-0044 决策 2：覆盖率作为可度量 SLI
--      暴露，未覆盖账号发起单据按设计挂起（非故障）；本模板是「修复路径」之一。
--
-- 决策边界（重要）：
--   · 仅对**真实生产租户**运行；**禁止对 demo 租户 0 跑本模板**——tenant 0 是刻意保持
--     低覆盖的演示基线（2/21 账号有主管、1/29 部门有负责人），拉满它只会污染演示数据。
--   · 本模板是 0084_org_leader_reporting_line.sql 的**单租户参数化版**，逻辑完全一致
--     （负责人主管 = 父部门负责人；普通成员主管 = 本部门负责人；根部门负责人保持 NULL），
--     但限定 @tenant，避免误伤其他租户。
--   · 幂等：以 `manager_id IS NULL` / `leader_id IS NULL` 为守卫，重复执行只补缺失项。
--
-- 用法（先改 @tenant，再跑）：
--   mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < scripts/ops/org-coverage-backfill.sql
-- 跑完用审计脚本核对：python3 scripts/audit-org-coverage.py --tenant <tenant> --min-coverage 80
-- =============================================================================

USE bone;

-- ── 0. 目标租户（务必改！demo 租户 0 不要用本模板） ─────────────────────────
SET @tenant = NULL;   -- ← 改成目标真实租户 id，例如 1001；NULL 直接中止以防误跑

SELECT IF(@tenant IS NULL,
       '❌ 未设置 @tenant：本模板不会执行任何写入（防误跑 demo 租户）',
       CONCAT('▶ 将为租户 ', @tenant, ' 回填组织覆盖率')) AS _guard;

-- 守卫：未设租户或设为 0 时整段不执行
SET @go = IF(@tenant IS NOT NULL AND @tenant <> 0, 1, 0);

-- ── 1. 部门负责人主管 = 父部门负责人（让负责人自己发单能上溯） ───────────────
UPDATE iam_account a
  JOIN iam_dept d ON d.id = a.dept_id AND d.tenant_id = @tenant AND d.deleted = 0
  JOIN iam_dept pd ON pd.id = d.parent_id AND pd.tenant_id = @tenant AND pd.deleted = 0
  JOIN iam_account pl ON pl.id = pd.leader_id AND pl.deleted = 0
   SET a.manager_id = pd.leader_id,
       a.updated_at = NOW(3)
 WHERE a.tenant_id = @tenant AND a.deleted = 0 AND a.status = 1
   AND a.manager_id IS NULL
   AND pd.leader_id IS NOT NULL
   AND pd.leader_id <> a.id            -- 防自环
   AND @go = 1;

-- ── 2. 普通成员主管 = 本部门负责人 ──────────────────────────────────────────
UPDATE iam_account a
  JOIN iam_dept d ON d.id = a.dept_id AND d.tenant_id = @tenant AND d.deleted = 0
  JOIN iam_account l ON l.id = d.leader_id AND l.deleted = 0
   SET a.manager_id = d.leader_id,
       a.updated_at = NOW(3)
 WHERE a.tenant_id = @tenant AND a.deleted = 0 AND a.status = 1
   AND a.manager_id IS NULL
   AND d.leader_id IS NOT NULL
   AND d.leader_id <> a.id            -- 负责人本人不反过来挂自己
   AND @go = 1;

-- ── 3. 未指定负责人的部门 = 父部门负责人（让该部门成员后续可被解析） ───────────
UPDATE iam_dept d
  JOIN iam_dept pd ON pd.id = d.parent_id AND pd.tenant_id = @tenant AND pd.deleted = 0
   SET d.leader_id = pd.leader_id,
       d.updated_at = NOW(3)
 WHERE d.tenant_id = @tenant AND d.deleted = 0
   AND d.leader_id IS NULL
   AND pd.leader_id IS NOT NULL
   AND @go = 1;

-- ── 4. 校验（只读）：回填后覆盖率 ─────────────────────────────────────────
SELECT
  (SELECT COUNT(*) FROM iam_account WHERE tenant_id = @tenant AND deleted = 0 AND status = 1) AS accounts,
  (SELECT COUNT(*) FROM iam_account WHERE tenant_id = @tenant AND deleted = 0 AND status = 1 AND manager_id IS NOT NULL) AS with_manager,
  (SELECT COUNT(*) FROM iam_dept WHERE tenant_id = @tenant AND deleted = 0) AS depts,
  (SELECT COUNT(*) FROM iam_dept WHERE tenant_id = @tenant AND deleted = 0 AND leader_id IS NOT NULL) AS with_leader
 WHERE @go = 1;

-- 残留诊断：仍无主管的账号（通常是根部门负责人，按设计 fail-closed，非缺陷）
SELECT id, username, dept_id
  FROM iam_account
 WHERE tenant_id = @tenant AND deleted = 0 AND status = 1
   AND manager_id IS NULL
   AND @go = 1
 ORDER BY id
 LIMIT 50;
