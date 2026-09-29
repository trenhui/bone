-- ============================================================
-- 0010_iam_quota_counter_backfill.sql  (配额占用计数回填 runbook)
-- 背景：iam_tenant 新增 allocated_accounts / allocated_roles 占用计数，
--       配额校验由「count → 判断 → 写入」改为「占用计数 + @Version 乐观锁」（杜绝多实例超卖）。
--       但存量 iam_tenant 行的计数器为 DEFAULT 0，与库里已存在的账号/角色数不符。
--
-- 若不回填的后果（发布阻塞）：校验会把已用额度当成 0，maxAccounts / maxRoles 形同虚设——
--       在有人新建或删除账号之前，配额可被无限突破。
--
-- 本脚本只做存量回填（数据搬迁，不改结构）。
-- 前置：必须备份（阶段 0）。状态：L3/L4（线上数据操作）—— 需架构师审批后，由运维在目标库执行。
-- ============================================================

-- ---------- 阶段 0：备份 ----------
CREATE TABLE IF NOT EXISTS bak_iam_tenant_0010 AS SELECT * FROM iam_tenant;

-- ---------- 阶段 1：核对（执行前先跑，对比「当前值」与「实际值」） ----------
-- SELECT t.id,
--        t.max_accounts,
--        t.allocated_accounts AS old_acc,
--        (SELECT COUNT(*) FROM iam_account a WHERE a.tenant_id = t.id AND a.deleted = 0) AS real_acc,
--        t.max_roles,
--        t.allocated_roles AS old_role,
--        (SELECT COUNT(*) FROM iam_role r WHERE r.tenant_id = t.id AND r.deleted = 0) AS real_role
-- FROM iam_tenant t
-- ORDER BY t.id;
-- 预期：old_acc / old_role 全为 0，real_acc / real_role 为真实在用量。

-- ---------- 阶段 2：回填 ----------
-- 口径说明：只统计未软删（deleted = 0）的账号/角色，与配额语义「在用量」一致；
--          已软删的不占位，否则删除后额度不会释放（与代码侧 release 行为一致）。
UPDATE iam_tenant t
SET t.allocated_accounts = (
        SELECT COUNT(*) FROM iam_account a WHERE a.tenant_id = t.id AND a.deleted = 0
    ),
    t.allocated_roles = (
        SELECT COUNT(*) FROM iam_role r WHERE r.tenant_id = t.id AND r.deleted = 0
    );

-- ---------- 阶段 3：校验 ----------
-- SELECT t.id, t.allocated_accounts, t.allocated_roles,
--        (SELECT COUNT(*) FROM iam_account a WHERE a.tenant_id = t.id AND a.deleted = 0) AS real_acc,
--        (SELECT COUNT(*) FROM iam_role    r WHERE r.tenant_id = t.id AND r.deleted = 0) AS real_role
-- FROM iam_tenant t
-- WHERE t.allocated_accounts <> (SELECT COUNT(*) FROM iam_account a WHERE a.tenant_id = t.id AND a.deleted = 0)
--    OR t.allocated_roles    <> (SELECT COUNT(*) FROM iam_role    r WHERE r.tenant_id = t.id AND r.deleted = 0);
-- 期望：0 行（计数与实际在用量完全一致）。

-- ---------- 阶段 4：超额租户自查（可选，但建议跑） ----------
-- SELECT t.id, t.max_accounts, t.allocated_accounts, t.max_roles, t.allocated_roles
-- FROM iam_tenant t
-- WHERE (t.max_accounts IS NOT NULL AND t.allocated_accounts > t.max_accounts)
--    OR (t.max_roles    IS NOT NULL AND t.allocated_roles    > t.max_roles);
-- 若非空：说明存量已超配额（回填前无强校验所致）。由业务侧决定
--   ① 上调 maxAccounts/maxRoles，或 ② 清理账号/角色至配额内——否则这些租户将无法新建账号。
