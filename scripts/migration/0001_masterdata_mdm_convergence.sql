-- ============================================================
-- 0001_masterdata_mdm_convergence.sql
-- G1 双表收敛：md_entity / md_field / md_record  →  mdm_entity / mdm_field / mdm_record
-- 依据：doc/design/modules/3a §7.1（裁定：收敛到 mdm_*）
--       ADR-生产数据库增量迁移策略（expand/contract）
-- 前置：表结构以 bone-init.sql §8 为准（本脚本只负责数据搬迁，不重复维护 DDL）
-- 执行前：必须备份（见文件末尾「回滚」）
-- ============================================================

-- ---------- 阶段 0：备份 ----------
-- 安全约定：只用 CREATE TABLE IF NOT EXISTS，**绝不可**先 DROP 备份表。
-- 曾踩坑：本脚本重跑时 DROP+CREATE 会把上一次的备份覆盖成空表，导致备份失效。
-- 若需重新备份，请手工 DROP 后执行，或改用带时间戳的备份表名。
CREATE TABLE IF NOT EXISTS bak_md_entity_0001 AS SELECT * FROM md_entity;
CREATE TABLE IF NOT EXISTS bak_md_field_0001 AS SELECT * FROM md_field;
CREATE TABLE IF NOT EXISTS bak_md_record_0001 AS SELECT * FROM md_record;
CREATE TABLE IF NOT EXISTS bak_mdm_qcheck_task_0001 AS SELECT * FROM mdm_qcheck_task;
CREATE TABLE IF NOT EXISTS bak_mdm_qcheck_report_0001 AS SELECT * FROM mdm_qcheck_report;
CREATE TABLE IF NOT EXISTS bak_mdm_qcheck_detail_0001 AS SELECT * FROM mdm_qcheck_detail;

-- ---------- 阶段 1：Migrate（md_entity → mdm_entity） ----------
-- 说明：
--   * md_entity.name        → mdm_entity.entity_name
--   * md_entity 无编码列     → entity_code 由 'ENT_' + id 生成（保证租户内唯一键）
--   * 治理等级默认 L1，两个开关随之推导为 0
--   * md_entity.status 为 VARCHAR(DRAFT/PUBLISHED/DISABLED)，与 mdm_entity.status 新语义一致
DELETE FROM mdm_entity;

INSERT INTO mdm_entity (
  id, tenant_id, meta_entity_id, entity_code, entity_name, description, category,
  domain_code, template_id, template_version, owning_app_id, governance_tier,
  is_versioning, workflow_enabled, status,
  created_by, updated_by, created_at, updated_at, deleted, version
)
SELECT
  e.id,
  e.tenant_id,
  e.meta_entity_id,
  CONCAT('ENT_', e.id),
  e.name,
  e.description,
  e.category,
  NULL,           -- domain_code：存量未标注业务域，后续由治理员补
  NULL,           -- template_id：存量均为租户自建
  NULL,           -- template_version
  NULL,           -- owning_app_id：缺省为平台共享域
  'L1',           -- governance_tier
  0,              -- is_versioning
  0,              -- workflow_enabled
  e.status,
  NULL,           -- created_by（md_entity 无此列）
  NULL,           -- updated_by
  e.created_at,
  e.updated_at,
  0,              -- deleted
  0               -- version
FROM md_entity e;

-- ---------- 阶段 2：Migrate（md_field → mdm_field） ----------
DELETE FROM mdm_field;

INSERT INTO mdm_field (
  id, tenant_id, master_data_entity_id, name, code, type, length, required,
  default_value, description, sort_order, source_type, enabled, reference_set_code,
  created_at, created_by, updated_at, updated_by, deleted, version
)
SELECT
  f.id,
  f.tenant_id,
  f.master_data_entity_id,
  f.name,
  f.code,
  f.type,
  f.length,
  f.required,
  f.default_value,
  f.description,
  f.sort_order,
  'TENANT',       -- source_type：存量均为租户自建字段
  1,              -- enabled
  NULL,           -- reference_set_code
  f.created_at,
  f.created_by,
  f.updated_at,
  f.updated_by,
  0,              -- deleted
  0               -- version
FROM md_field f;

-- ---------- 阶段 3：Migrate（md_record → mdm_record） ----------
-- 说明：
--   * md_record.data            → mdm_record.current_data
--   * md_record 无业务编码       → record_code 由 'REC_' + id 生成
--   * display_name 尝试从 data 的 name 键提取；非 JSON 或缺失时回退为记录编码
--   * is_current：已发布记录为当前有效版本
DELETE FROM mdm_record;

INSERT INTO mdm_record (
  id, tenant_id, mdm_entity_id, record_code, display_name, current_data, status,
  version_number, effective_from, effective_to, is_current, parent_record_id,
  publish_time, created_by, updated_by, created_at, updated_at, deleted, version
)
SELECT
  r.id,
  r.tenant_id,
  r.master_data_entity_id,
  CONCAT('REC_', r.id),
  CASE
    WHEN JSON_VALID(r.data) AND JSON_EXTRACT(r.data, '$.name') IS NOT NULL
      THEN JSON_UNQUOTE(JSON_EXTRACT(r.data, '$.name'))
    ELSE CONCAT('REC_', r.id)
  END,
  r.data,
  r.status,
  1,              -- version_number
  NULL,           -- effective_from
  NULL,           -- effective_to
  CASE WHEN r.status = 'PUBLISHED' THEN 1 ELSE 0 END,
  NULL,           -- parent_record_id
  r.publish_time,
  NULL,           -- created_by
  NULL,           -- updated_by
  r.created_at,
  r.updated_at,
  0,              -- deleted
  0               -- version
FROM md_record r;

-- ---------- 阶段 4：校验（三条语句应返回 0 差异） ----------
SELECT 'md_entity_diff'  AS chk, (SELECT COUNT(*) FROM md_entity)  - (SELECT COUNT(*) FROM mdm_entity)  AS diff
UNION ALL
SELECT 'md_field_diff',   (SELECT COUNT(*) FROM md_field)   - (SELECT COUNT(*) FROM mdm_field)
UNION ALL
SELECT 'md_record_diff',  (SELECT COUNT(*) FROM md_record)  - (SELECT COUNT(*) FROM mdm_record);

-- ============================================================
-- 回滚（应急时执行，顺序不可颠倒）：
--   DELETE FROM mdm_record; INSERT INTO mdm_record SELECT * FROM bak_md_record_0001;  -- 仅当列名一致时可用
--   更稳妥做法：直接从 *_0001 备份表逐个字段回插，
--   或恢复执行前的数据库快照（推荐）。
-- Contract 阶段（确认无引用后再执行，不在本脚本内）：
--   DROP TABLE md_entity; DROP TABLE md_field; DROP TABLE md_record;
-- ============================================================
