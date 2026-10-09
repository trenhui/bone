-- =============================================================================
-- 0028_meta_template_enum.sql
--
-- 目的（PRD v0.33 · W1a T1）：为平台模型模板字段表补 ENUM / 校验规则的存储列，
--       使「场景模板」可携带枚举（码表）与质量规则，实例化时透传到租户实体字段。
--
-- 关联：W1a T2（后端 MetaModelTemplateField 领域类扩展 + 实例化透传）、
--       T3（前端 FIELD_TYPES 已加 ENUM、字段向导已支持枚举编辑器）。
--
-- 设计：纯增量 ALTER，新增两列均为 nullable，不破坏既有数据；
--       与 meta_field 的 enum_values / validation_rules 口径一致（VARCHAR(1000) JSON 文本）。
--
-- 执行（不在此脚本内执行，属 L3 审批项；由 DBA / CI 在目标库运行）：
--       mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0028_meta_template_enum.sql
-- =============================================================================

USE bone;

-- ── 1. 备份受影响表结构（仅建结构镜像，便于回滚） ────────────────────────
CREATE TABLE IF NOT EXISTS bak_meta_model_template_field_0028 LIKE meta_model_template_field;

-- ── 2. 增量加列 ─────────────────────────────────────────────────────────
-- ENUM 标准码表（JSON）：[{ "code": "RECEIVED", "label": "已收货" }, ...]
SET @col_enum = (SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_schema = 'bone' AND table_name = 'meta_model_template_field' AND column_name = 'enum_values');
SET @sql_enum = IF(@col_enum = 0,
  'ALTER TABLE meta_model_template_field ADD COLUMN enum_values VARCHAR(1000) DEFAULT NULL COMMENT ''ENUM 标准码表（JSON 文本）'' AFTER comment',
  'SELECT 1');
PREPARE stmt FROM @sql_enum; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 字段级校验 / 质量规则（JSON）：{ "min": 0, "max": 120 }
SET @col_rule = (SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_schema = 'bone' AND table_name = 'meta_model_template_field' AND column_name = 'validation_rules');
SET @sql_rule = IF(@col_rule = 0,
  'ALTER TABLE meta_model_template_field ADD COLUMN validation_rules VARCHAR(1000) DEFAULT NULL COMMENT ''字段级校验/质量规则（JSON 文本）'' AFTER enum_values',
  'SELECT 1');
PREPARE stmt FROM @sql_rule; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ── 3. 校验：两列均存在 ───────────────────────────────────────────────────
SELECT
  (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = 'bone' AND table_name = 'meta_model_template_field' AND column_name = 'enum_values') AS has_enum_values,
  (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = 'bone' AND table_name = 'meta_model_template_field' AND column_name = 'validation_rules') AS has_validation_rules;
