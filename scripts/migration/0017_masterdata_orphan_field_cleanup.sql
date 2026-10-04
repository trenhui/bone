-- 0017 — 清理 mdm_field 孤儿行（master_data_entity_id 指向已不存在的 mdm_entity）
--
-- 背景：mdm_field 没有外键约束（HC-006 基线：主数据子表随聚合落盘，删除走应用层级联），
-- 因此实体被删除/重建后，字段行会残留。实测 bone 开发库有 4 条 tenant 0 的孤儿字段
-- （created_at 落在 2026-09-24 ~ 09-25，指向当时临时实体），它们既不展示也不可用，
-- 但会污染按实体统计的字段总数，并让"字段数"类断言出现无法解释的偏差。
--
-- 处置口径：
--   1. 软删除（deleted=1）而非物理删除 —— mdm_field 有 deleted 列，且孤儿行一旦误判
--      （比如实体只是被逻辑删除而字段仍有效）可以一条 UPDATE 还原；
--   2. 先落备份表再改写，保留 id/所属实体/字段编码的完整快照；
--   3. 幂等：重复执行命中 0 行，末尾校验「活跃孤儿数 = 0」；
--   4. 不碰 deleted=1 的历史行，也不碰任何能匹配到实体的行。
--
-- 适用：开发库/测试库直接执行；生产库按 ADR-生产数据库增量迁移策略走 expand/contract，
--       本脚本在生产执行前需先确认这些实体的删除是有意为之（不存在"实体被误删"的回滚需求）。

-- ------------------------------------------------------------------
-- 阶段 0：备份（首次执行才建表；重复执行复用既有备份表）
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bak_mdm_field_orphan_0017 LIKE mdm_field;

INSERT INTO bak_mdm_field_orphan_0017 (id, tenant_id, master_data_entity_id, name, code, type,
length, required, default_value, description, sort_order, source_type, enabled,
reference_set_code, min_value, max_value, created_at, created_by, updated_at, updated_by,
deleted, version)
SELECT f.id, f.tenant_id, f.master_data_entity_id, f.name, f.code, f.type,
       f.length, f.required, f.default_value, f.description, f.sort_order, f.source_type, f.enabled,
       f.reference_set_code, f.min_value, f.max_value, f.created_at, f.created_by, f.updated_at,
       f.updated_by, f.deleted, f.version
FROM mdm_field f
LEFT JOIN mdm_entity e ON e.id = f.master_data_entity_id
WHERE e.id IS NULL
  AND f.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM bak_mdm_field_orphan_0017 b WHERE b.id = f.id);

-- ------------------------------------------------------------------
-- 阶段 1：执行前清单（人工核对用；不满足预期就 ROLLBACK 手工介入）
-- ------------------------------------------------------------------
SELECT 'BEFORE' AS phase,
       COUNT(*) AS orphan_active_rows,
       GROUP_CONCAT(CONCAT(f.code, '@', f.master_data_entity_id) ORDER BY f.id SEPARATOR ', ')
                  AS detail
FROM mdm_field f
LEFT JOIN mdm_entity e ON e.id = f.master_data_entity_id
WHERE e.id IS NULL AND f.deleted = 0;

-- ------------------------------------------------------------------
-- 阶段 2：软删除孤儿字段
-- ------------------------------------------------------------------
UPDATE mdm_field f
LEFT JOIN mdm_entity e ON e.id = f.master_data_entity_id
SET f.deleted = 1,
    f.updated_at = CURRENT_TIMESTAMP(3)
WHERE e.id IS NULL
  AND f.deleted = 0;

-- ------------------------------------------------------------------
-- 阶段 3：校验 —— 活跃孤儿必须为 0；备份行数必须等于被清理行数
-- ------------------------------------------------------------------
SELECT 'AFTER' AS phase,
       (SELECT COUNT(*) FROM mdm_field f
          LEFT JOIN mdm_entity e ON e.id = f.master_data_entity_id
         WHERE e.id IS NULL AND f.deleted = 0) AS orphan_active_rows,
       (SELECT COUNT(*) FROM bak_mdm_field_orphan_0017) AS backup_rows,
       (SELECT COUNT(*) FROM mdm_field WHERE deleted = 0) AS active_field_rows;

-- 还原语句（误判时手工执行）：
--   UPDATE mdm_field f JOIN bak_mdm_field_orphan_0017 b ON b.id = f.id
--      SET f.deleted = 0, f.updated_at = CURRENT_TIMESTAMP(3);
