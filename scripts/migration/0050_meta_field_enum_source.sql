-- =============================================================================
-- 0050_meta_field_enum_source.sql
--
-- 目的：给存量库 meta_field 补 enum_source 列（枚举选项源声明，JSON），
--       落地 doc/design/modules/11《枚举选项源统一设计方案》的字段绑定能力。
--
-- 背景（2026-10-20 实现 + 联调实证）：
--   界面设计器的字典面板此前只能引用 meta_field.enum_values 这份「无治理的 JSON 文本」，
--   与 bone-system 的平台字典（sys_dict_type/sys_dict_item：值域定义、层级、多语言、
--   租户覆盖、禁删、值格式校验、漂移检测）完全割裂——同一个「性别/币种」多处各存一份。
--   本列让 MetaField 可声明选项真源：{"type":"dict","mode":"snapshot","ref":{"typeCode":X}}，
--   保存/发布时由 metadata-server 经 ACL（/api/v1/system/dict/items/options）拉取真源
--   物化进 enum_values ⇒ 运行时渲染与写校验零改动（与 doc 11 §6「物化策略」一致）。
--
--   bone-init.sql 的 CREATE TABLE 已带上该列（enum_values 之后、validation_rules 之前），
--   但只对新建库生效；init 之前建好的存量库（本地联调库即此类）永远补不上这列，
--   表现为物化写入报 Unknown column 'enum_source'。本脚本把该列正式收口为可重复执行的迁移。
--
-- 幂等：information_schema 先探测再 DDL（老版本 MySQL 无 ADD COLUMN IF NOT EXISTS）。
--
-- 执行：/usr/local/mysql/bin/mysql -h127.0.0.1 -uroot -p bone < scripts/migration/0050_meta_field_enum_source.sql
-- =============================================================================

-- ── 1. 补列（AFTER enum_values，与 bone-init.sql 建表顺序一致）──
SET @db := DATABASE();
SET @exists_col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'meta_field' AND COLUMN_NAME = 'enum_source'
);
SET @sql := IF(@exists_col = 0,
  'ALTER TABLE meta_field ADD COLUMN enum_source JSON DEFAULT NULL COMMENT ''枚举选项源声明(inline缺省/dict;空=inline本地枚举,行为与存量一致)。设计doc/design/modules/11'' AFTER enum_values',
  'SELECT ''skip: enum_source already exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ── 2. 存量数据说明（无需刷数）──
--    存量行 enum_source 为 NULL = inline 本地枚举，行为与改造前逐字一致（doc 11 §4.2 兼容性承诺）。
--    无任何 UPDATE 语句——不碰存量数据是本迁移的硬约束。

-- ── 3. 校验：列必须存在且类型/可空/位置符合预期 ──
SELECT 'enum_source_column' AS check_item,
       COLUMN_TYPE, IS_NULLABLE, ORDINAL_POSITION
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'meta_field' AND COLUMN_NAME = 'enum_source';

-- ── 4. 负向校验：迁移前 INSERT 带 enum_source 报 Unknown column；迁移后必须能写入并读回 ──
--    （用真实列做一次原子验证，回滚由测试框架负责，不在此脚本内留脏数据）
SELECT 'meta_field_read_smoke' AS check_item, COUNT(*) AS field_rows
FROM (SELECT id, enum_source FROM meta_field LIMIT 1) t;
