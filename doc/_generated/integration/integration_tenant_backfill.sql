-- ============================================================
-- bone-integration 租户回填脚本（L4，G-2 / P0* 修复前置）
-- ============================================================
-- 状态：模板 —— 归属规则需业务侧 / DBA 确认后执行
-- 前置：本脚本必须在「5 聚合改 TenantAggregateRoot（代码 B）」上线【之前】执行。
--       原因：B 上线后 SDK 按 tenant_id 自动过滤，tenant_id=0 历史行会被过滤成不可见，
--       导致存量流程 / 执行日志“消失”。故顺序固定为：先回填（本脚本）→ 再上线 B。
-- 幂等：仅更新 tenant_id = 0 或 NULL 的行；可重复执行，已正确归属的行不受影响。
-- 回滚：见文件末尾 integration_tenant_backfill_rollback.sql
-- 修正记录：
--   - 原 `WHERE tenant_id IN (0, NULL)` 已改为 `= 0 OR tenant_id IS NULL`：
--     IN 列表中的 NULL 会被 SQL 静默忽略，导致 NULL 租户行漏更；IS NULL 显式兜底。
--     （int_* 表 tenant_id 为 NOT NULL DEFAULT 0，正常无 NULL；此写法防未来 schema 漂移与手动置空。）
--   - 原 JOIN 表名 `iam_user` 已改为 `iam_account`（据 bone-init.sql DDL 真源，物理表为
--     `iam_account`，历史 `iam_user` 命名已淘汰；`created_by` 关联 `iam_account.id`）。
-- ============================================================

-- 0. 设定业务确认的兜底租户（⚠️ 必须先在 prod 确认一个真实、非 0 的租户 ID 再替换！）
--    若保留 0，则无法按创建者解析的 int_flow / int_connector 在 B 上线后仍不可见。
SET @default_tenant = 0;  -- TODO(DBA): 替换为业务确认的兜底租户 ID（非 0）

-- 步骤 0：执行前先核对待回填行数（应为非 0 才需要跑）
-- SELECT 'int_flow'            t, COUNT(*) c FROM int_flow            WHERE tenant_id = 0 OR tenant_id IS NULL;
-- SELECT 'int_flow_node'       t, COUNT(*) c FROM int_flow_node       WHERE tenant_id = 0 OR tenant_id IS NULL;
-- SELECT 'int_flow_connection' t, COUNT(*) c FROM int_flow_connection  WHERE tenant_id = 0 OR tenant_id IS NULL;
-- SELECT 'int_connector'       t, COUNT(*) c FROM int_connector        WHERE tenant_id = 0 OR tenant_id IS NULL;
-- SELECT 'int_execution_log'   t, COUNT(*) c FROM int_execution_log    WHERE tenant_id = 0 OR tenant_id IS NULL;

-- 步骤 1：int_flow 按创建者(created_by) 关联 iam_account 取 tenant_id
UPDATE int_flow f
SET f.tenant_id = (SELECT a.tenant_id FROM iam_account a WHERE a.id = f.created_by LIMIT 1)
WHERE (f.tenant_id = 0 OR f.tenant_id IS NULL)
  AND EXISTS (SELECT 1 FROM iam_account a WHERE a.id = f.created_by);

-- 步骤 2：无法按创建者解析归属的 int_flow，归集到业务确认的兜底租户
UPDATE int_flow f
SET f.tenant_id = @default_tenant
WHERE f.tenant_id = 0 OR f.tenant_id IS NULL;

-- 步骤 3：级联回填子表（按所属流程的 tenant_id）
UPDATE int_flow_node n
SET n.tenant_id = (SELECT f.tenant_id FROM int_flow f WHERE f.id = n.flow_id)
WHERE n.tenant_id = 0 OR n.tenant_id IS NULL;

UPDATE int_flow_connection c
SET c.tenant_id = (SELECT f.tenant_id FROM int_flow f WHERE f.id = c.flow_id)
WHERE c.tenant_id = 0 OR c.tenant_id IS NULL;

UPDATE int_execution_log l
SET l.tenant_id = (SELECT f.tenant_id FROM int_flow f WHERE f.id = l.flow_id)
WHERE l.tenant_id = 0 OR l.tenant_id IS NULL;

-- 步骤 4：int_connector 无 flow 归属，先按 created_by 解析，失败归集兜底租户
UPDATE int_connector co
SET co.tenant_id = (SELECT a.tenant_id FROM iam_account a WHERE a.id = co.created_by LIMIT 1)
WHERE (co.tenant_id = 0 OR co.tenant_id IS NULL)
  AND EXISTS (SELECT 1 FROM iam_account a WHERE a.id = co.created_by);

UPDATE int_connector co
SET co.tenant_id = @default_tenant
WHERE co.tenant_id = 0 OR co.tenant_id IS NULL;

-- 步骤 5：执行后校验（应全部为 0；若仍有非 0 残留，说明 @default_tenant 未正确设置或关联缺失）
-- SELECT 'int_flow'            t, COUNT(*) c FROM int_flow            WHERE tenant_id = 0 OR tenant_id IS NULL;
-- SELECT 'int_flow_node'       t, COUNT(*) c FROM int_flow_node       WHERE tenant_id = 0 OR tenant_id IS NULL;
-- SELECT 'int_flow_connection' t, COUNT(*) c FROM int_flow_connection  WHERE tenant_id = 0 OR tenant_id IS NULL;
-- SELECT 'int_connector'       t, COUNT(*) c FROM int_connector        WHERE tenant_id = 0 OR tenant_id IS NULL;
-- SELECT 'int_execution_log'   t, COUNT(*) c FROM int_execution_log    WHERE tenant_id = 0 OR tenant_id IS NULL;

-- ============================================================
-- 回滚脚本 integration_tenant_backfill_rollback.sql
-- 仅当回填错误时，将本批次回填行恢复为 0。需业务提供可识别的批次标记（如回填前先备份）：
--   CREATE TABLE int_flow_bak AS SELECT * FROM int_flow WHERE tenant_id != 0 AND <批次条件>;
--   UPDATE int_flow f SET f.tenant_id = 0 WHERE EXISTS (SELECT 1 FROM int_flow_bak b WHERE b.id = f.id);
--   （其余子表同理，按 flow_id 关联回滚）
-- ============================================================
