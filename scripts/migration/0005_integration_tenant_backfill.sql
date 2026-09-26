-- ============================================================
-- 0005_integration_tenant_backfill.sql  (R2 回填 runbook)
-- 背景：integration 5 个聚合已改 extends TenantAggregateRoot（多租户数据隔离方案 §3 / R2 代码侧），
--       但存量数据 tenant_id 仍为 0（DEFAULT）或 NULL —— 属发布阻塞（跨租户读风险）。
-- 本脚本只做存量回填（数据搬迁，不改结构），按 owner 反查租户：
--   * int_flow / int_connector / int_flow_node 有 created_by（用户ID）→ 经 iam_account 反查 tenant_id
--   * int_flow_connection / int_execution_log 无 created_by → 经 flow_id 取 int_flow.tenant_id
-- 前置：必须备份（见阶段 0）。状态：L3/L4（线上数据操作）—— 需架构师审批后，由运维在目标库执行。
-- ============================================================

-- ---------- 阶段 0：备份 ----------
CREATE TABLE IF NOT EXISTS bak_int_flow_0005          AS SELECT * FROM int_flow;
CREATE TABLE IF NOT EXISTS bak_int_flow_node_0005     AS SELECT * FROM int_flow_node;
CREATE TABLE IF NOT EXISTS bak_int_flow_conn_0005    AS SELECT * FROM int_flow_connection;
CREATE TABLE IF NOT EXISTS bak_int_connector_0005    AS SELECT * FROM int_connector;
CREATE TABLE IF NOT EXISTS bak_int_exec_log_0005     AS SELECT * FROM int_execution_log;

-- ---------- 阶段 1：核对（执行前先跑，确认影响行数） ----------
-- SELECT 'int_flow' t, COUNT(*) c FROM int_flow WHERE tenant_id IN (0, NULL)
-- UNION ALL SELECT 'int_connector', COUNT(*) FROM int_connector WHERE tenant_id IN (0, NULL)
-- UNION ALL SELECT 'int_flow_node', COUNT(*) FROM int_flow_node WHERE tenant_id IN (0, NULL)
-- UNION ALL SELECT 'int_flow_connection', COUNT(*) FROM int_flow_connection WHERE tenant_id IN (0, NULL)
-- UNION ALL SELECT 'int_execution_log', COUNT(*) FROM int_execution_log WHERE tenant_id IN (0, NULL);

-- ---------- 阶段 2：回填 int_flow（根，有 created_by） ----------
UPDATE int_flow f
SET f.tenant_id = (
        SELECT a.tenant_id FROM iam_account a WHERE a.id = f.created_by LIMIT 1
    )
WHERE f.tenant_id IN (0, NULL) AND f.created_by IS NOT NULL;

-- ---------- 阶段 3：回填 int_connector / int_flow_node（有 created_by） ----------
UPDATE int_connector c
SET c.tenant_id = (
        SELECT a.tenant_id FROM iam_account a WHERE a.id = c.created_by LIMIT 1
    )
WHERE c.tenant_id IN (0, NULL) AND c.created_by IS NOT NULL;

UPDATE int_flow_node n
SET n.tenant_id = (
        SELECT a.tenant_id FROM iam_account a WHERE a.id = n.created_by LIMIT 1
    )
WHERE n.tenant_id IN (0, NULL) AND n.created_by IS NOT NULL;

-- ---------- 阶段 4：回填 int_flow_connection / int_execution_log（经父 flow_id） ----------
UPDATE int_flow_connection ec
SET ec.tenant_id = (SELECT f.tenant_id FROM int_flow f WHERE f.id = ec.flow_id LIMIT 1)
WHERE ec.tenant_id IN (0, NULL);

UPDATE int_execution_log el
SET el.tenant_id = (SELECT f.tenant_id FROM int_flow f WHERE f.id = el.flow_id LIMIT 1)
WHERE el.tenant_id IN (0, NULL);

-- ---------- 阶段 5：校验 ----------
-- SELECT 'int_flow' t, COUNT(*) remaining FROM int_flow WHERE tenant_id IN (0, NULL)
-- UNION ALL SELECT 'int_connector', COUNT(*) FROM int_connector WHERE tenant_id IN (0, NULL)
-- UNION ALL SELECT 'int_flow_node', COUNT(*) FROM int_flow_node WHERE tenant_id IN (0, NULL)
-- UNION ALL SELECT 'int_flow_connection', COUNT(*) FROM int_flow_connection WHERE tenant_id IN (0, NULL)
-- UNION ALL SELECT 'int_execution_log', COUNT(*) FROM int_execution_log WHERE tenant_id IN (0, NULL);
-- 期望：全部为 0。剩余 0 值行 = created_by 为空且无对应账户 / 孤儿 flow_id，需业务侧确认归属（平台租户 0 或删除）。
