-- Bone MasterData 模块生成 biz_id 数据迁移脚本
-- 为现有数据生成 UUID 格式的业务 ID

-- 1. 为主数据实体表生成 biz_id
UPDATE md_entity SET biz_id = REPLACE(UUID(), '-', '');

-- 2. 为主数据字段表生成 biz_id
UPDATE md_field SET biz_id = REPLACE(UUID(), '-', '');

-- 3. 为主数据记录表生成 biz_id
UPDATE md_record SET biz_id = REPLACE(UUID(), '-', '');

-- 4. 为数据质量规则表生成 biz_id
UPDATE md_quality_rule SET biz_id = REPLACE(UUID(), '-', '');

-- 5. 为数据质量检查表生成 biz_id
UPDATE md_quality_check SET biz_id = REPLACE(UUID(), '-', '');

-- 6. 为数据质量报告表生成 biz_id
UPDATE md_quality_report SET biz_id = REPLACE(UUID(), '-', '');
