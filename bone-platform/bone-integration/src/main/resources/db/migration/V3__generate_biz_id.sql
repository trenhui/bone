-- Bone Integration 模块生成 biz_id 数据迁移脚本
-- 为现有数据生成 UUID 格式的业务 ID

-- 1. 为连接器表生成 biz_id
UPDATE int_connector SET biz_id = REPLACE(UUID(), '-', '');

-- 2. 为流程表生成 biz_id
UPDATE int_flow SET biz_id = REPLACE(UUID(), '-', '');

-- 3. 为流程执行记录表生成 biz_id
UPDATE int_flow_execution SET biz_id = REPLACE(UUID(), '-', '');

-- 4. 为流程节点表生成 biz_id
UPDATE int_flow_node SET biz_id = REPLACE(UUID(), '-', '');

-- 5. 为流程连接表生成 biz_id
UPDATE int_flow_connection SET biz_id = REPLACE(UUID(), '-', '');
