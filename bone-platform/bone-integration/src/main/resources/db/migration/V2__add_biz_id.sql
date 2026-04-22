-- Bone Integration 模块添加 biz_id 字段迁移脚本
-- 为双 ID 模型添加业务 ID 字段

-- 1. 连接器表添加 biz_id 字段
ALTER TABLE int_connector ADD COLUMN biz_id VARCHAR(64) NOT NULL DEFAULT '' COMMENT '业务ID（UUID）' AFTER id;
ALTER TABLE int_connector ADD UNIQUE INDEX idx_biz_id (biz_id);

-- 2. 流程表添加 biz_id 字段
ALTER TABLE int_flow ADD COLUMN biz_id VARCHAR(64) NOT NULL DEFAULT '' COMMENT '业务ID（UUID）' AFTER id;
ALTER TABLE int_flow ADD UNIQUE INDEX idx_biz_id (biz_id);

-- 3. 流程执行记录表添加 biz_id 字段
ALTER TABLE int_flow_execution ADD COLUMN biz_id VARCHAR(64) NOT NULL DEFAULT '' COMMENT '业务ID（UUID）' AFTER id;
ALTER TABLE int_flow_execution ADD UNIQUE INDEX idx_biz_id (biz_id);

-- 4. 流程节点表添加 biz_id 字段
ALTER TABLE int_flow_node ADD COLUMN biz_id VARCHAR(64) NOT NULL DEFAULT '' COMMENT '业务ID（UUID）' AFTER id;
ALTER TABLE int_flow_node ADD UNIQUE INDEX idx_biz_id (biz_id);

-- 5. 流程连接表添加 biz_id 字段
ALTER TABLE int_flow_connection ADD COLUMN biz_id VARCHAR(64) NOT NULL DEFAULT '' COMMENT '业务ID（UUID）' AFTER id;
ALTER TABLE int_flow_connection ADD UNIQUE INDEX idx_biz_id (biz_id);
