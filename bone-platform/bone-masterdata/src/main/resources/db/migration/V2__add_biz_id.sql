-- Bone MasterData 模块添加 biz_id 字段迁移脚本
-- 为双 ID 模型添加业务 ID 字段

-- 1. 主数据实体表添加 biz_id 字段
ALTER TABLE md_entity ADD COLUMN biz_id VARCHAR(64) NOT NULL DEFAULT '' COMMENT '业务ID（UUID）' AFTER id;
ALTER TABLE md_entity ADD UNIQUE INDEX idx_biz_id (biz_id);

-- 2. 主数据字段表添加 biz_id 字段
ALTER TABLE md_field ADD COLUMN biz_id VARCHAR(64) NOT NULL DEFAULT '' COMMENT '业务ID（UUID）' AFTER id;
ALTER TABLE md_field ADD UNIQUE INDEX idx_biz_id (biz_id);

-- 3. 主数据记录表添加 biz_id 字段
ALTER TABLE md_record ADD COLUMN biz_id VARCHAR(64) NOT NULL DEFAULT '' COMMENT '业务ID（UUID）' AFTER id;
ALTER TABLE md_record ADD UNIQUE INDEX idx_biz_id (biz_id);

-- 4. 数据质量规则表添加 biz_id 字段
ALTER TABLE md_quality_rule ADD COLUMN biz_id VARCHAR(64) NOT NULL DEFAULT '' COMMENT '业务ID（UUID）' AFTER id;
ALTER TABLE md_quality_rule ADD UNIQUE INDEX idx_biz_id (biz_id);

-- 5. 数据质量检查表添加 biz_id 字段
ALTER TABLE md_quality_check ADD COLUMN biz_id VARCHAR(64) NOT NULL DEFAULT '' COMMENT '业务ID（UUID）' AFTER id;
ALTER TABLE md_quality_check ADD UNIQUE INDEX idx_biz_id (biz_id);

-- 6. 数据质量报告表添加 biz_id 字段
ALTER TABLE md_quality_report ADD COLUMN biz_id VARCHAR(64) NOT NULL DEFAULT '' COMMENT '业务ID（UUID）' AFTER id;
ALTER TABLE md_quality_report ADD UNIQUE INDEX idx_biz_id (biz_id);
