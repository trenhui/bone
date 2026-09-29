-- ============================================================
-- 0011_generator_tenant_backfill.sql  (studio-generator 租户回填 runbook)
-- 背景：studio-generator 此前把租户写死为 0L（写入侧）/ 1L（查询侧），
--       代码侧已改为「租户一律取自 TenantContext，缺失即失败关闭」并给列表加上租户过滤，
--       但存量 gen_* 行的 tenant_id 仍是 DEFAULT 0 —— 属发布阻塞：
--       真实租户按 tenant_id = X 查询，看不到自己（存在租户 0 名下）的数据。
--
-- 归属反查链路（与 0005 同思路，按 owner 反查租户）：
--   * 有 created_by 的表 → 经 iam_account 反查 tenant_id
--   * gen_column_metadata（无直接 owner）→ 经 table_metadata_id 取 gen_table_metadata.tenant_id
--   * gen_code_generation_history（无 created_by）→ 经 task_id 取 gen_generation_task.tenant_id
--
-- ⚠️ 平台内置数据严禁回填（必须保持 tenant_id = 0）：
--   * gen_code_template 的 12 条内置模板种子（content IS NULL，正文真源是 classpath .ftl）
--   * gen_type_mapping 的 JDBC→Java 映射种子
--   这些是平台级数据，回填到某个租户会导致其它租户看不到内置模板 / 类型映射。
--   本脚本用 created_by IS NOT NULL 作为「用户自建」判据，天然跳过无创建人的种子行。
--
-- 前置：必须备份（阶段 0）。状态：L3/L4（线上数据操作）—— 需架构师审批后，由运维在目标库执行。
-- ============================================================

-- ---------- 阶段 0：备份 ----------
CREATE TABLE IF NOT EXISTS bak_gen_data_source_0011            AS SELECT * FROM gen_data_source;
CREATE TABLE IF NOT EXISTS bak_gen_table_metadata_0011         AS SELECT * FROM gen_table_metadata;
CREATE TABLE IF NOT EXISTS bak_gen_column_metadata_0011        AS SELECT * FROM gen_column_metadata;
CREATE TABLE IF NOT EXISTS bak_gen_code_template_0011          AS SELECT * FROM gen_code_template;
CREATE TABLE IF NOT EXISTS bak_gen_generation_task_0011        AS SELECT * FROM gen_generation_task;
CREATE TABLE IF NOT EXISTS bak_gen_code_generation_history_0011 AS SELECT * FROM gen_code_generation_history;

-- ---------- 阶段 1：核对（执行前先跑，确认影响行数） ----------
-- SELECT 'gen_data_source' t,             COUNT(*) c FROM gen_data_source            WHERE tenant_id = 0
-- UNION ALL SELECT 'gen_table_metadata',  COUNT(*)   FROM gen_table_metadata         WHERE tenant_id = 0
-- UNION ALL SELECT 'gen_column_metadata', COUNT(*)   FROM gen_column_metadata        WHERE tenant_id = 0
-- UNION ALL SELECT 'gen_code_template',   COUNT(*)   FROM gen_code_template          WHERE tenant_id = 0 AND created_by IS NOT NULL
-- UNION ALL SELECT 'gen_generation_task', COUNT(*)   FROM gen_generation_task        WHERE tenant_id = 0
-- UNION ALL SELECT 'gen_code_generation_history', COUNT(*) FROM gen_code_generation_history WHERE tenant_id = 0;
-- 注意：gen_code_template 只统计 created_by 非空者（内置种子不计入，属平台数据）。

-- ---------- 阶段 2：回填 gen_data_source（根，有 created_by） ----------
UPDATE gen_data_source d
SET d.tenant_id = (
        SELECT a.tenant_id FROM iam_account a WHERE a.id = d.created_by LIMIT 1
    )
WHERE d.tenant_id = 0 AND d.created_by IS NOT NULL;

-- ---------- 阶段 3：回填 gen_table_metadata（有 created_by） ----------
UPDATE gen_table_metadata m
SET m.tenant_id = (
        SELECT a.tenant_id FROM iam_account a WHERE a.id = m.created_by LIMIT 1
    )
WHERE m.tenant_id = 0 AND m.created_by IS NOT NULL;

-- ---------- 阶段 4：回填 gen_column_metadata（经父表 table_metadata_id） ----------
UPDATE gen_column_metadata c
SET c.tenant_id = (
        SELECT m.tenant_id FROM gen_table_metadata m WHERE m.id = c.table_metadata_id LIMIT 1
    )
WHERE c.tenant_id = 0
  AND c.table_metadata_id IN (SELECT m.id FROM gen_table_metadata m WHERE m.tenant_id <> 0);

-- ---------- 阶段 5：回填用户自建模板（内置种子 created_by 为空，自动跳过） ----------
UPDATE gen_code_template t
SET t.tenant_id = (
        SELECT a.tenant_id FROM iam_account a WHERE a.id = t.created_by LIMIT 1
    )
WHERE t.tenant_id = 0 AND t.created_by IS NOT NULL;

-- ---------- 阶段 6：回填 gen_generation_task（有 created_by） ----------
UPDATE gen_generation_task g
SET g.tenant_id = (
        SELECT a.tenant_id FROM iam_account a WHERE a.id = g.created_by LIMIT 1
    )
WHERE g.tenant_id = 0 AND g.created_by IS NOT NULL;

-- ---------- 阶段 7：回填 gen_code_generation_history（经 task_id 取任务租户） ----------
UPDATE gen_code_generation_history h
SET h.tenant_id = (
        SELECT g.tenant_id FROM gen_generation_task g WHERE g.task_id = h.task_id LIMIT 1
    )
WHERE h.tenant_id = 0
  AND h.task_id IN (SELECT g.task_id FROM gen_generation_task g WHERE g.tenant_id <> 0);

-- ---------- 阶段 8：校验 ----------
-- SELECT 'gen_data_source' t,             COUNT(*) remaining FROM gen_data_source            WHERE tenant_id = 0
-- UNION ALL SELECT 'gen_table_metadata',  COUNT(*)           FROM gen_table_metadata         WHERE tenant_id = 0
-- UNION ALL SELECT 'gen_column_metadata', COUNT(*)           FROM gen_column_metadata        WHERE tenant_id = 0
-- UNION ALL SELECT 'gen_code_template',   COUNT(*)           FROM gen_code_template          WHERE tenant_id = 0 AND created_by IS NOT NULL
-- UNION ALL SELECT 'gen_generation_task', COUNT(*)           FROM gen_generation_task        WHERE tenant_id = 0
-- UNION ALL SELECT 'gen_code_generation_history', COUNT(*)   FROM gen_code_generation_history WHERE tenant_id = 0;
--
-- 期望：
--   * gen_code_template 为 0（用户自建模板全部归属到具体租户）
--   * 其余表剩余行 = created_by 为空 / 父记录缺失的孤儿数据，需业务侧确认归属
--     （归到平台租户 0，或清理删除）。
--   * gen_code_template 中 tenant_id = 0 且 created_by IS NULL 的行是内置模板种子，属预期保留。

-- ---------- 阶段 9：回滚（异常时使用） ----------
-- 逐表用阶段 0 备份还原 tenant_id（执行前确认 bak_gen_*_0011 已生成）：
-- UPDATE gen_data_source d             JOIN bak_gen_data_source_0011 b             ON b.id=d.id             SET d.tenant_id=b.tenant_id;
-- UPDATE gen_table_metadata m          JOIN bak_gen_table_metadata_0011 b          ON b.id=m.id             SET m.tenant_id=b.tenant_id;
-- UPDATE gen_column_metadata c         JOIN bak_gen_column_metadata_0011 b         ON b.id=c.id             SET c.tenant_id=b.tenant_id;
-- UPDATE gen_code_template t           JOIN bak_gen_code_template_0011 b           ON b.id=t.id             SET t.tenant_id=b.tenant_id;
-- UPDATE gen_generation_task g         JOIN bak_gen_generation_task_0011 b         ON b.id=g.id             SET g.tenant_id=b.tenant_id;
-- UPDATE gen_code_generation_history h JOIN bak_gen_code_generation_history_0011 b ON b.id=h.id            SET h.tenant_id=b.tenant_id;
-- 注意：回滚会把 tenant_id 还原为回填前的值（含用户自建数据回到 0），回滚后须重启 studio-generator 新版本实例。
